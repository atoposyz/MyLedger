"""Single-user opaque backup API. Bind loopback; Caddy terminates public HTTPS.

No application ledger or password handling. Python 3.11+, standard library only.
"""
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import sqlite3
import struct
import tempfile
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import uuid4

MAX_BYTES = 20 * 1024 * 1024 + 56
ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


class BackupStore:
    def __init__(self, directory, retain=7):
        if not 1 <= retain <= 100:
            raise ValueError("retain must be 1..100")
        self.directory = Path(directory).resolve()
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.retain = retain
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.directory / "index.sqlite3", check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS backups (seq INTEGER PRIMARY KEY, id TEXT UNIQUE NOT NULL, created TEXT NOT NULL, size INTEGER NOT NULL, sha TEXT NOT NULL)")
        self.db.commit()
        self._cleanup()

    def _cleanup(self):
        known = {row[0] for row in self.db.execute("SELECT id FROM backups")}
        for file in self.directory.iterdir():
            if (file.suffix == ".enc" and ID.fullmatch(file.stem) and file.stem not in known) or file.name.startswith("upload-"):
                file.unlink(missing_ok=True)

    @staticmethod
    def valid(data):
        return 56 <= len(data) <= MAX_BYTES and data[:8] == b"MYLENC01" and struct.unpack(">I", data[8:12])[0] == 600000

    def put(self, data):
        if not self.valid(data):
            raise ValueError("Only portable MYLENC01 encrypted backups are accepted")
        with self.lock:
            backup_id = str(uuid4())
            created = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            digest = hashlib.sha256(data).hexdigest()
            target = self.directory / (backup_id + ".enc")
            temp = None
            try:
                with tempfile.NamedTemporaryFile(prefix="upload-", dir=self.directory, delete=False) as output:
                    temp = Path(output.name)
                    output.write(data)
                    output.flush()
                    os.fsync(output.fileno())
                os.replace(temp, target)
                with self.db:
                    self.db.execute("INSERT INTO backups (id,created,size,sha) VALUES (?,?,?,?)", (backup_id, created, len(data), digest))
            except Exception:
                self.db.rollback()
                target.unlink(missing_ok=True)
                if temp:
                    temp.unlink(missing_ok=True)
                raise
            # Prune only after the newly uploaded ciphertext and metadata committed.
            expired = [row[0] for row in self.db.execute("SELECT id FROM backups ORDER BY seq DESC LIMIT -1 OFFSET ?", (self.retain,))]
            with self.db:
                self.db.executemany("DELETE FROM backups WHERE id=?", ((value,) for value in expired))
            self._cleanup()
            return dict(id=backup_id, createdAt=created, sizeBytes=len(data), sha256=digest)

    def list(self):
        with self.lock:
            return [dict(id=row[0], createdAt=row[1], sizeBytes=row[2], sha256=row[3]) for row in self.db.execute("SELECT id,created,size,sha FROM backups ORDER BY seq DESC")]

    def get(self, backup_id):
        with self.lock:
            row = self.db.execute("SELECT size,sha FROM backups WHERE id=?", (backup_id,)).fetchone()
            if row is None:
                return None
            file = self.directory / (backup_id + ".enc")
            if file.stat().st_size != row[0]:
                raise ValueError("Stored ciphertext changed")
            data = file.read_bytes()
            if hashlib.sha256(data).hexdigest() != row[1]:
                raise ValueError("Stored ciphertext changed")
            return data

    def delete(self, backup_id):
        with self.lock:
            with self.db:
                count = self.db.execute("DELETE FROM backups WHERE id=?", (backup_id,)).rowcount
            if count:
                (self.directory / (backup_id + ".enc")).unlink(missing_ok=True)
            return bool(count)

    def close(self):
        self.db.close()


class BackupServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, store, token):
        if not 32 <= len(token) <= 4096 or any(ord(c) < 33 or ord(c) > 126 for c in token):
            raise ValueError("A random access token of at least 32 printable characters is required")
        self.store, self.token = store, token.encode("ascii")
        self.slots = threading.BoundedSemaphore(8)
        super().__init__(address, BackupHandler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()


class BackupHandler(BaseHTTPRequestHandler):
    server_version = "MyLedgerBackup/1"
    def setup(self):
        super().setup()
        self.connection.settimeout(30)

    def log_message(self, *_):
        pass  # No tokens, body, URLs, or ledger metadata in access logs.

    def reply(self, status, payload, mime="application/json"):
        body = json.dumps(payload, separators=(",", ":")).encode() if mime == "application/json" else payload
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def dispatch(self):
        try:
            supplied = self.headers.get("Authorization", "").encode("utf-8")
            if not hmac.compare_digest(supplied, b"Bearer " + self.server.token):
                self.reply(401, {"error": "unauthorized"})
                return
            path = self.path
            match = re.fullmatch(r"/api/backups/(" + ID.pattern + ")", path)
            if self.command == "PUT" and path == "/api/backup":
                if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Type", "").split(";")[0] != "application/octet-stream":
                    self.reply(400, {"error": "content_type_or_framing"})
                    return
                try:
                    length = int(self.headers.get("Content-Length", ""))
                except ValueError:
                    self.reply(411, {"error": "length_required"})
                    return
                if not 56 <= length <= MAX_BYTES:
                    self.reply(413, {"error": "size_limit"})
                    return
                data = self.rfile.read(length)
                if len(data) != length or not BackupStore.valid(data):
                    self.reply(400, {"error": "encrypted_backup_required"})
                    return
                self.reply(201, self.server.store.put(data))
            elif self.command == "GET" and path == "/api/backups":
                self.reply(200, {"backups": self.server.store.list()})
            elif self.command == "GET" and match:
                data = self.server.store.get(match.group(1))
                self.reply(404, {"error": "not_found"}) if data is None else self.reply(200, data, "application/octet-stream")
            elif self.command == "DELETE" and match:
                found = self.server.store.delete(match.group(1))
                self.reply(200 if found else 404, {"deleted": found})
            else:
                self.reply(404, {"error": "not_found"})
        except (OSError, ValueError, sqlite3.Error):
            try:
                self.reply(500, {"error": "backup_operation_failed"})
            except OSError:
                pass

    do_GET = do_PUT = do_DELETE = dispatch


def main():
    token = os.environ.get("MYLEDGER_BACKUP_TOKEN", "")
    store = BackupStore(os.environ.get("MYLEDGER_BACKUP_DIR", "./data"), int(os.environ.get("MYLEDGER_RETAIN", "7")))
    try:
        # Public TLS/authenticated traffic must come through the reverse proxy.
        with BackupServer(("127.0.0.1", int(os.environ.get("MYLEDGER_PORT", "8787"))), store, token) as server:
            print("MyLedger backup API on 127.0.0.1:%d; use an HTTPS reverse proxy" % server.server_port, flush=True)
            server.serve_forever()
    finally:
        store.close()


if __name__ == "__main__":
    main()
