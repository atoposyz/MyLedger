import http.client
import json
import os
from pathlib import Path
import struct
import tempfile
import threading
import unittest
from backup_server import BackupServer, BackupStore, MAX_BYTES


class BackupApiTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = BackupStore(self.temp.name)
        self.token = "test-token-not-for-deployment-" + "a" * 32
        self.server = BackupServer(("127.0.0.1", 0), self.store, self.token)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.store.close()
        self.temp.cleanup()

    @staticmethod
    def envelope():
        # Server is intentionally unable to decrypt: verify opaque envelope framing.
        return b"MYLENC01" + struct.pack(">I", 600000) + os.urandom(120)

    def request(self, method, path, body=None, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        fields = {"Authorization": "Bearer " + self.token, "Content-Type": "application/octet-stream"}
        if headers:
            fields.update(headers)
        connection.request(method, path, body, fields)
        response = connection.getresponse()
        status, data = response.status, response.read()
        connection.close()
        return status, data

    def put(self, data=None):
        status, body = self.request("PUT", "/api/backup", data or self.envelope())
        self.assertEqual(201, status)
        return json.loads(body)

    def test_authentication_required_on_every_endpoint(self):
        for method, path in [("GET", "/api/backups"), ("PUT", "/api/backup"), ("DELETE", "/api/backups/" + "a" * 36)]:
            status, _ = self.request(method, path, self.envelope() if method == "PUT" else None, {"Authorization": "Bearer wrong"})
            self.assertEqual(401, status)
        self.assertEqual([], self.store.list())

    def test_rejects_plaintext_size_bad_kdf_and_paths(self):
        for data in [b"PK" + b"x" * 100, b"MYLLOC01" + b"x" * 100, b"MYLENC01" + struct.pack(">I", 1) + b"x" * 100]:
            self.assertEqual(400, self.request("PUT", "/api/backup", data)[0])
        self.assertEqual(413, self.request("PUT", "/api/backup", b"x")[0])
        self.assertEqual(413, self.request("PUT", "/api/backup", b"x", {"Content-Length": str(MAX_BYTES + 1)})[0])
        for path in ["/api/backups/../../index.sqlite3", "/api/backups/%2e%2e", "/api/backups?token=secret"]:
            self.assertEqual(404, self.request("GET", path)[0])
        self.assertEqual([], self.store.list())

    def test_round_trip_metadata_retention_and_delete(self):
        data = self.envelope()
        entry = self.put(data)
        self.assertEqual(data, self.request("GET", "/api/backups/" + entry["id"])[1])
        records = [entry] + [self.put() for _ in range(8)]
        status, body = self.request("GET", "/api/backups")
        self.assertEqual(200, status)
        self.assertEqual([row["id"] for row in records[-1:-8:-1]], [row["id"] for row in json.loads(body)["backups"]])
        self.assertEqual(7, len(list(Path(self.temp.name).glob("*.enc"))))
        self.assertEqual(404, self.request("GET", "/api/backups/" + entry["id"])[0])
        chosen = records[-1]["id"]
        self.assertEqual(200, self.request("DELETE", "/api/backups/" + chosen)[0])
        self.assertEqual(404, self.request("DELETE", "/api/backups/" + chosen)[0])
        self.assertEqual(6, len(self.store.list()))

    def test_ciphertext_tampering_detected(self):
        entry = self.put()
        file = Path(self.temp.name) / (entry["id"] + ".enc")
        data = bytearray(file.read_bytes())
        data[-1] ^= 1
        file.write_bytes(data)
        self.assertEqual(500, self.request("GET", "/api/backups/" + entry["id"])[0])

    def test_restart_retains_index_and_cleans_only_orphans(self):
        entry = self.put()
        orphan = Path(self.temp.name) / "00000000-0000-0000-0000-000000000001.enc"
        orphan.write_bytes(self.envelope())
        note = Path(self.temp.name) / "keep.txt"
        note.write_text("not a backup")
        self.store.close()
        self.store = BackupStore(self.temp.name)
        self.server.store = self.store
        self.assertEqual(entry, self.store.list()[0])
        self.assertFalse(orphan.exists())
        self.assertTrue(note.exists())

    def test_rejects_weak_token_and_invalid_retention(self):
        with self.assertRaises(ValueError):
            BackupServer(("127.0.0.1", 0), self.store, "short")
        with self.assertRaises(ValueError):
            BackupStore(self.temp.name, 0)


if __name__ == "__main__":
    unittest.main()
