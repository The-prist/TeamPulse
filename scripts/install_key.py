#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把本地公钥装到服务器的 /root/.ssh/authorized_keys（用 SFTP，绕开 shell 引号问题）。

用法：
  python install_key.py --host 1.2.3.4 --password-file pwd.txt --pub local_key.pub
"""
import argparse
import os
import sys

sys.path.insert(0, os.environ.get("PARAMIKO_PATH", r"E:\codestorge\.run-logs\pylibs"))

import paramiko  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=22)
    ap.add_argument("--user", default="root")
    ap.add_argument("--password-file", required=True)
    ap.add_argument("--pub", required=True)
    args = ap.parse_args()

    pub = open(args.pub, encoding="utf-8").read().strip()
    if not pub.startswith("ssh-"):
        print("公钥文件内容不合法", file=sys.stderr)
        return 2

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(hostname=args.host, port=args.port, username=args.user,
                   password=open(args.password_file, encoding="utf-8").read().strip(),
                   look_for_keys=False, allow_agent=False,
                   timeout=20, banner_timeout=20, auth_timeout=20)

    # 1) 准备目录与权限
    client.exec_command("mkdir -p /root/.ssh && chmod 700 /root/.ssh")[1].channel.recv_exit_status()

    # 2) 用 SFTP 追加（先读现有内容，避免重复）
    sftp = client.open_sftp()
    path = "/root/.ssh/authorized_keys"
    existing = ""
    try:
        with sftp.open(path, "r") as fh:
            existing = fh.read().decode("utf-8", "replace")
    except FileNotFoundError:
        existing = ""

    if pub in existing:
        print("公钥已存在，跳过")
    else:
        content = existing
        if content and not content.endswith("\n"):
            content += "\n"
        content += pub + "\n"
        with sftp.open(path, "w") as fh:
            fh.write(content.encode("utf-8"))
        print("公钥已写入")

    sftp.chmod(path, 0o600)
    sftp.close()

    # 3) 校验
    stdin, stdout, stderr = client.exec_command(
        "wc -l < /root/.ssh/authorized_keys; stat -c '%a' /root/.ssh/authorized_keys; "
        "stat -c '%a' /root/.ssh")
    print("authorized_keys 行数 / 权限:", stdout.read().decode().strip().replace("\n", " / "))
    client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
