<#
.SYNOPSIS
    从本机把代码打包推送到服务器并部署（不依赖服务器访问 GitHub）。

.DESCRIPTION
    腾讯云等国内机器访问 GitHub 常常间歇性失败（实测 3 次只通 1 次），
    所以服务器上的 git fetch 不可靠。本脚本改为：
      1) 本机打包（排除 .git / node_modules / target / dist / .run-logs 等）
      2) scp 上传到 /tmp
      3) 服务器解包到 /opt/TeamPulse（保留 .env 与 mosquitto passwd）
      4) 调用 deploy-server.sh 完成构建与启动

.PARAMETER Server
    服务器地址，默认 81.70.102.116

.PARAMETER Key
    SSH 私钥路径，默认 .run-logs\ssh\teampulse_deploy

.EXAMPLE
    .\scripts\deploy-from-local.ps1
    .\scripts\deploy-from-local.ps1 -SkipBuild   # 只同步代码，不重建镜像
#>
[CmdletBinding()]
param(
    [string]$Server = '81.70.102.116',
    [string]$User = 'root',
    [string]$Key = '',   # 留空则用 <仓库>\.run-logs\ssh\teampulse_deploy
    [string]$RemoteDir = '/opt/TeamPulse',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
if (-not $Key) { $Key = Join-Path $PSScriptRoot '..\.run-logs\ssh\teampulse_deploy' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$key = (Resolve-Path $Key).Path

if (-not (Test-Path $key)) { throw "找不到私钥: $key" }

Write-Host "`n=== [1/4] 打包 ===" -ForegroundColor Cyan
$tar = Join-Path $env:TEMP ("teampulse-src-{0}.tar.gz" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
Push-Location $root
try {
    # 用 tar 的 --exclude，避免把依赖与运行产物打进去（从 ~300MB 降到几 MB）
    $excludes = @(
        '--exclude=.git', '--exclude=.run-logs', '--exclude=node_modules',
        '--exclude=backend/target', '--exclude=frontend/dist', '--exclude=frontend/.vite',
        '--exclude=.toolchain', '--exclude=.env', '--exclude=deploy/certs',
        '--exclude=deploy/mosquitto/passwd', '--exclude=__pycache__'
    )
    & tar -czf $tar @excludes .
    if ($LASTEXITCODE -ne 0) { throw 'tar 打包失败' }
} finally { Pop-Location }
$sizeMb = [math]::Round((Get-Item $tar).Length / 1MB, 2)
Write-Host "  包大小: $sizeMb MB" -ForegroundColor Green

Write-Host "`n=== [2/4] 上传 ===" -ForegroundColor Cyan
$remoteTar = "/tmp/teampulse-src.tar.gz"
& scp -i $key -o StrictHostKeyChecking=no -o BatchMode=yes $tar "${User}@${Server}:$remoteTar"
if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败' }
Write-Host "  已上传到 $remoteTar" -ForegroundColor Green

Write-Host "`n=== [3/4] 解包（保留 .env 与 passwd）===" -ForegroundColor Cyan
$extract = @"
set -euo pipefail
mkdir -p $RemoteDir
cd $RemoteDir
tar -xzf $remoteTar
echo "解包完成，当前文件数: `$(find . -type f | wc -l)"
"@
$tmpScript = Join-Path $env:TEMP 'tp-extract.sh'
[System.IO.File]::WriteAllText($tmpScript, $extract.Replace("`r`n", "`n"), (New-Object System.Text.UTF8Encoding($false)))
& scp -i $key -o StrictHostKeyChecking=no -o BatchMode=yes $tmpScript "${User}@${Server}:/tmp/tp-extract.sh"
& ssh -i $key -o StrictHostKeyChecking=no -o BatchMode=yes "${User}@${Server}" 'bash /tmp/tp-extract.sh'
if ($LASTEXITCODE -ne 0) { throw '解包失败' }

Write-Host "`n=== [4/4] 构建并重启 ===" -ForegroundColor Cyan
if ($SkipBuild) {
    & ssh -i $key -o StrictHostKeyChecking=no -o BatchMode=yes "${User}@${Server}" "cd $RemoteDir && docker compose up -d && docker compose ps"
} else {
    $deployScript = Join-Path $root 'deploy\scripts\deploy-server.sh'
    $dest = Join-Path $env:TEMP 'deploy-server.sh'
    [System.IO.File]::WriteAllText($dest, [System.IO.File]::ReadAllText($deployScript).Replace("`r`n", "`n"),
        (New-Object System.Text.UTF8Encoding($false)))
    & scp -i $key -o StrictHostKeyChecking=no -o BatchMode=yes $dest "${User}@${Server}:/tmp/deploy-server.sh"
    & ssh -i $key -o StrictHostKeyChecking=no -o BatchMode=yes "${User}@${Server}" 'bash /tmp/deploy-server.sh'
}

Write-Host "`n=== 完成 ===" -ForegroundColor Green
Write-Host "  访问: http://$Server/" -ForegroundColor Green
Remove-Item $tar -Force -ErrorAction SilentlyContinue
