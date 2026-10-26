#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""极简 SSH 远程执行器（paramiko）。

用途：在没有 sshpass/plink 的 Windows 上做非交互部署。
支持密码与密钥两种认证；密码只从文件读，不出现在命令行与日志里。

用法：
  python remote.py --host 1.2.3.4 --user root --key path/to/key --script local.sh
  python remote.py --host 1.2.3.4 --user root --password-file path/to/pwd --cmd "uname -a"

退出码：远端命令的退出码；连接失败返回 255。
"""
import argparse
import os
import sys

# paramiko 装在本地目录，避免污染系统 Python；可用 PARAMIKO_PATH 覆盖
sys.path.insert(0, os.environ.get("PARAMIKO_PATH", r"E:\codestorge\.run-logs\pylibs"))

import paramiko  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=22)
    ap.add_argument("--user", default="root")
    ap.add_argument("--key", default="")
    ap.add_argument("--password-file", default="")
    ap.add_argument("--cmd", default="")
    ap.add_argument("--script", default="")
    ap.add_argument("--timeout", type=int, default=600)
    args = ap.parse_args()

    client = paramiko.SSHClient()
    # 首次连接接受主机指纹（这是用户自己的服务器，且我们随后会改用密钥）
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    kwargs = dict(hostname=args.host, port=args.port, username=args.user,
                  timeout=20, banner_timeout=20, auth_timeout=20)
    if args.key:
        kwargs["key_filename"] = args.key
        kwargs["look_for_keys"] = False
        kwargs["allow_agent"] = False
    elif args.password_file:
        kwargs["password"] = open(args.password_file, encoding="utf-8").read().strip()
        kwargs["look_for_keys"] = False
        kwargs["allow_agent"] = False
    else:
        print("需要 --key 或 --password-file", file=sys.stderr)
        return 2

    try:
        client.connect(**kwargs)
    except Exception as exc:  # noqa: BLE001
        print(f"[连接失败] {type(exc).__name__}: {exc}", file=sys.stderr)
        return 255

    if args.script:
        remote_cmd = "bash -s"
        stdin_src = open(args.script, "rb").read()
    else:
        remote_cmd = args.cmd
        stdin_src = None

    stdin, stdout, stderr = client.exec_command(remote_cmd, timeout=args.timeout, get_pty=False)
    if stdin_src is not None:
        stdin.write(stdin_src)
        stdin.flush()
        stdin.channel.shutdown_write()

    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    client.close()

    if out:
        sys.stdout.write(out)
        if not out.endswith("\n"):
            sys.stdout.write("\n")
    if err:
        sys.stderr.write(err if err.endswith("\n") else err + "\n")
    return code


if __name__ == "__main__":
    sys.exit(main())
