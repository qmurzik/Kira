#!/usr/bin/env python3
"""Тестовый FTP-сервер для интеграционных тестов TwinFTP (pyftpdlib).

Запуск: python3 test_ftp_server.py --port 2121 --root /tmp/ftproot [--no-mlsd] [--no-epsv] [--latin1]

Пользователь: twin / "пароль 123" (с пробелом и кириллицей — проверка кодирования PASS).
"""
import argparse
import os
import sys

from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=2121)
    ap.add_argument("--root", required=True)
    ap.add_argument("--no-mlsd", action="store_true")
    ap.add_argument("--no-epsv", action="store_true")
    ap.add_argument("--cp1251", action="store_true", help="имена файлов в Windows-1251, без UTF8 в FEAT")
    ap.add_argument("--masquerade", default=None, help="адрес в ответе PASV (проверка NAT-обхода)")
    args = ap.parse_args()

    os.makedirs(args.root, exist_ok=True)
    authorizer = DummyAuthorizer()
    authorizer.add_user("twin", "пароль 123", args.root, perm="elradfmwMT")
    authorizer.add_anonymous(args.root, perm="elr")

    class Handler(FTPHandler):
        pass

    Handler.authorizer = authorizer
    Handler.banner = "TwinFTP test server ready"
    Handler.passive_ports = range(30000, 30200)
    Handler.permit_foreign_addresses = False
    if args.masquerade:
        Handler.masquerade_address = args.masquerade
    proto = dict(FTPHandler.proto_cmds)
    if args.no_mlsd:
        proto.pop("MLSD", None)
        proto.pop("MLST", None)
    if args.no_epsv:
        proto.pop("EPSV", None)
    Handler.proto_cmds = proto
    if args.cp1251:
        Handler.encoding = "cp1251"
        proto.pop("OPTS", None)
        proto.pop("FEAT", None)

    server = FTPServer((args.host, args.port), Handler)
    server.max_cons = 64
    print(f"FTP server on {args.host}:{args.port} root={args.root}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    sys.exit(main())
