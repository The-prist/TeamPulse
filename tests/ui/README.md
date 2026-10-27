# 浏览器级冒烟测试（tests/ui）

真开浏览器，把每个角色、每个页面都点一遍，抓三类问题：

1. **整页白屏 / 「页面出现异常」** —— Vue 渲染期抛错（如 template/computed 里引用了未声明的变量）
2. **未捕获 JS 异常**（`pageerror`）
3. **console.error**

## 为什么需要它

接口级测试（`scripts/audit/button-e2e.mjs`）用 fetch 直接打 API，**不渲染 Vue 组件**。
2026-10-27 的日程详情页事故就是典型：接口 200，但组件里 `canDelete` 引用了不存在的
`detail`/`schedule`，渲染时抛 `ReferenceError` → 整页只剩一句"页面出现异常"。
接口测试全绿，页面却打不开。这类回归只能靠浏览器级测试拦住。

## 运行

```bash
cd tests/ui
node smoke.mjs                                  # 打生产域名
BASE=http://81.70.102.116 node smoke.mjs        # 打 IP
ONLY=employee node smoke.mjs                    # 只测一个角色
```

## 依赖

- `playwright-core`（不下载浏览器）
- 复用系统已装的 **Microsoft Edge**（`channel: 'msedge'`）

安装方式（本机没有 npm 时）：直接解压 registry 的 tarball 到 `node_modules/playwright-core`，
它无第三方依赖：

```bash
curl -L https://registry.npmjs.org/playwright-core/-/playwright-core-1.47.2.tgz | tar -xz
mv package node_modules/playwright-core
```

## 判定

| 结果 | 含义 |
|---|---|
| ok | 页面有内容、无「页面出现异常」、无未捕获异常 |
| fail | 白屏 / 出现全局错误提示 / 有 pageerror |
| skip | 账号不可用（如库被清空），不算失败，避免掩盖真正的白屏回归 |

## 期望基线

| 环境 | 期望 |
|---|---|
| 有演示数据 | 6 角色 × 各自页面全绿 |
| 空库（只有 admin） | admin 的 14 个页面 ok；其余 5 角色 skip |
