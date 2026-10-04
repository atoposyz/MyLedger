# MyLedger 单用户加密备份服务

Python 3.11+，仅使用标准库。只接收 `MYLENC01` 密码加密文件，不处理账目、密码或 AI key。服务默认绑定 `127.0.0.1:8787`，公网 HTTPS 由 Caddy 提供，不直接暴露 Python 后端端口。

## API v1

所有接口需要 `Authorization: Bearer <备份访问令牌>`。令牌与备份密码、AI API key 三者独立。

| 接口 | 请求与响应 |
| --- | --- |
| `PUT /api/backup` | `application/octet-stream`，Content-Length；201 返回单份元数据 |
| `GET /api/backups` | 200 `{"backups":[...]}`，按创建顺序倒序 |
| `GET /api/backups/{id}` | 200 原始密文，或 404 |
| `DELETE /api/backups/{id}` | 200 `{"deleted":true}`，或 404 |

元数据：`id`（服务生成 UUID）、`createdAt`（UTC ISO-8601）、`sizeBytes`、`sha256`。不包含备注、分类、金额或账目数量。上传上限为 20 MiB + 56 字节；只检查加密格式头和 KDF 参数，服务不能解密或证明密码正确。客户端下载验证 SHA-256 后仍必须通过密码/GCM 校验与内层完整备份校验。

默认保留最近 7 份，可用 `MYLEDGER_RETAIN=1..100` 调整。SQLite 仅存备份文件元数据，`.enc` 存密文；新文件与索引成功提交后才清理过期文件。重启清理已知格式的孤立上传文件，不删除目录中的其他文件。服务只运行一个实例，不提供用户系统、同步或 AI 代理。

## 部署（Debian / Ubuntu 示例）

1. 准备一台服务器和域名，例如 `backup.example.com`。DNS A/AAAA 指向服务器，开放 80/443，8787 保持私有。确认 AAAA 与 IPv6 的实际可达性一致。
2. 安装 Python 3.11+、Git、Caddy。Caddy 安装见[官方说明](https://caddyserver.com/docs/install)。

```sh
sudo useradd --system --home /opt/myledger --shell /usr/sbin/nologin myledger
sudo git clone https://github.com/atoposyz/MyLedger.git /opt/myledger
sudo install -d -o myledger -g myledger -m 700 /var/lib/myledger-backups
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'
```

3. 将生成的随机令牌保存到服务器 `/etc/myledger-backup.env`（仅 root 可读），并自行保管，用于随后填写手机。下面是内容模板，不要使用占位符做正式令牌：

```text
MYLEDGER_BACKUP_TOKEN=替换为刚生成的随机令牌
MYLEDGER_BACKUP_DIR=/var/lib/myledger-backups
MYLEDGER_RETAIN=7
MYLEDGER_PORT=8787
```

```sh
sudo chmod 600 /etc/myledger-backup.env
sudo cp /opt/myledger/server/myledger-backup.service.example /etc/systemd/system/myledger-backup.service
sudo systemctl daemon-reload
sudo systemctl enable --now myledger-backup
sudo systemctl status myledger-backup
```

4. 把 `Caddyfile.example` 的域名换成自己的域名，合并到 `/etc/caddy/Caddyfile`。不要覆盖已运行站点的配置。确认后运行：

```sh
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Caddy 会为可达的域名申请/续期证书，见 [HTTPS 说明](https://caddyserver.com/docs/quick-starts/https)。Python 的 `http.server` 不作为公网服务器直接运行，参见 [Python 文档](https://docs.python.org/3/library/http.server.html#security-considerations)。

5. 手机“设置 → 连接配置”：服务器地址填写 `https://backup.example.com`，不要加 `/api/backup`；备份访问令牌填第 2 步生成的值。保存后到“服务器备份”刷新，应显示暂无备份。随后设置另一份自己能记住的备份密码进行上传。

## 验证与排错

```sh
python3 -m unittest discover -s server -p 'test_*.py' -v
```

401/403：令牌不一致；404：基础地址或备份 ID 错误；连接失败：DNS、防火墙、证书或后端未启动。应用拒绝 HTTP 和重定向，需填写最终 HTTPS 地址。不得关闭证书验证。关闭 Caddy 请求正文/Authorization 日志；本服务不记录令牌、请求体或业务信息。

备份服务访问令牌丢失可在服务器换令牌并重启服务，再更新手机；此操作不改变已有文件的备份密码。密码丢失无法解密。更换服务器地址时必须重新填写令牌，应用不会把旧令牌发给新地址。服务器存储目录须持久保留，服务器备份也不能替代外部独立副本。
