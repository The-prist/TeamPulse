#!/usr/bin/env bash
# 本地等价于 CI 的 ui-smoke job：假定全栈已跑起来（compose 或线上），依次跑
#   1) 接口矩阵（6 角色 × 83 接口）
#   2) 浏览器冒烟（6 角色 × 各自页面）
#
# 用法：
#   bash scripts/ci/run-ui-tests.sh                      # 打 http://127.0.0.1
#   BASE=https://your.domain bash scripts/ci/run-ui-tests.sh
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

BASE="${BASE:-http://127.0.0.1}"
fail=0

echo "=============================================================="
echo "1/2 接口矩阵   BASE=$BASE"
echo "=============================================================="
if node scripts/audit/button-e2e.mjs --base="$BASE"; then
  echo "✓ 接口矩阵通过"
else
  echo "✗ 接口矩阵失败"
  fail=1
fi

echo
echo "=============================================================="
echo "2/2 浏览器冒烟 BASE=$BASE"
echo "=============================================================="
if [ ! -d tests/ui/node_modules ]; then
  echo "首次运行：安装 playwright-core（不下载浏览器，复用系统 Edge）"
  ( cd tests/ui && npm install --no-audit --no-fund ) || {
    echo "npm 不可用，请手动解压 playwright-core 到 tests/ui/node_modules/（见 tests/ui/README.md）"
  }
fi
if ( cd tests/ui && BASE="$BASE" node smoke.mjs ); then
  echo "✓ 浏览器冒烟通过"
else
  echo "✗ 浏览器冒烟失败"
  fail=1
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "全部通过 ✓"
else
  echo "存在失败项 ✗"
fi
exit "$fail"
