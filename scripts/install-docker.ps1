# =============================================================================
# TeamPulse · 本机安装 Docker（Windows 11 家庭版 + WSL2）
#
# 为什么需要这个脚本：
#   1. 家庭版没有 Hyper-V，Docker Desktop 必须用 WSL2 后端；
#   2. 启用 WSL 与安装 Docker Desktop 都需要**管理员权限**，且启用 WSL 后要重启；
#   3. 这些都是系统级改动，本脚本只把步骤固化下来，不替你跳过权限。
#
# 用法（必须用管理员 PowerShell）：
#   1) 开始菜单搜索 "PowerShell" → 右键 → 以管理员身份运行
#   2) cd E:\codestorge
#   3) Set-ExecutionPolicy -Scope Process Bypass -Force
#   4) .\scripts\install-docker.ps1
#   5) 看到「需要重启」后重启电脑；重启后再运行一次本脚本（会自动跳过已完成步骤）
#
# 装完验证：
#   docker version
#   docker compose version
#   docker run --rm hello-world
# =============================================================================

$ErrorActionPreference = 'Stop'

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Step($text) { Write-Host "`n▶ $text" -ForegroundColor Cyan }
function Write-Ok($text)   { Write-Host "  ✓ $text" -ForegroundColor Green }
function Write-Warn2($t)   { Write-Host "  ! $t" -ForegroundColor Yellow }

if (-not (Test-Admin)) {
    Write-Host "`n[需要管理员权限] 请用「以管理员身份运行」的 PowerShell 重新执行：" -ForegroundColor Red
    Write-Host "    cd E:\codestorge" -ForegroundColor Red
    Write-Host "    Set-ExecutionPolicy -Scope Process Bypass -Force" -ForegroundColor Red
    Write-Host "    .\scripts\install-docker.ps1`n" -ForegroundColor Red
    exit 1
}

Write-Host "`n════════ 安装 Docker（WSL2 后端）════════" -ForegroundColor Cyan
Write-Host "  系统: $((Get-CimInstance Win32_OperatingSystem).Caption) Build $((Get-CimInstance Win32_OperatingSystem).BuildNumber)"
Write-Host "  管理员: 是"

# ---------------------------------------------------------------- 1. 启用 WSL
Write-Step "1/5 启用「适用于 Linux 的 Windows 子系统」与「虚拟机平台」"
$needReboot = $false

foreach ($feature in 'Microsoft-Windows-Subsystem-Linux', 'VirtualMachinePlatform') {
    $state = (Get-WindowsOptionalFeature -Online -FeatureName $feature -ErrorAction SilentlyContinue).State
    if ($state -eq 'Enabled') {
        Write-Ok "$feature 已启用"
    } else {
        Write-Host "  正在启用 $feature ..."
        $r = Enable-WindowsOptionalFeature -Online -FeatureName $feature -All -NoRestart
        if ($r.RestartNeeded) { $needReboot = $true }
        Write-Ok "$feature 已提交启用$(if ($r.RestartNeeded) { '（需重启生效）' })"
    }
}

# ---------------------------------------------------------------- 2. WSL 默认版本
Write-Step "2/5 设置 WSL 默认版本为 2"
try {
    & wsl --set-default-version 2 2>&1 | Out-Null
    Write-Ok "默认版本 = 2"
} catch {
    Write-Warn2 "设置失败（重启后会自动生效）: $($_.Exception.Message)"
}

# ---------------------------------------------------------------- 3. 内核 / WSL 组件更新
Write-Step "3/5 更新 WSL 组件（本机功能已启用，但内核可能缺失）"
try {
    Write-Host "  执行 wsl --update（可能需要 1~3 分钟）..."
    & wsl --update 2>&1 | Select-Object -Last 4 | ForEach-Object { "    $_" }
    Write-Ok "wsl --update 完成"
} catch {
    Write-Warn2 "wsl --update 失败，可手动下载内核包：https://aka.ms/wsl2kernel"
}
try {
    $v = & wsl --status 2>&1 | Out-String
    if ($v -match '默认版本|Default Version') { Write-Ok "WSL 状态正常" } else { Write-Warn2 "仍显示未就绪，重启后再跑一次本脚本" }
} catch { }

# ---------------------------------------------------------------- 4. 安装 Docker Desktop
Write-Step "4/5 安装 Docker Desktop"
$dockerCli = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCli) {
    Write-Ok "docker 已存在: $($dockerCli.Source)"
} else {
    Write-Host "  通过 winget 安装（约 600MB，需要几分钟）..."
    & winget install --id Docker.DockerDesktop --exact `
        --accept-source-agreements --accept-package-agreements --disable-interactivity
    if ($LASTEXITCODE -ne 0) {
        Write-Warn2 "winget 返回 $LASTEXITCODE；可能需要在弹窗中允许提权，或用官方安装包手动装："
        Write-Host "    https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe"
    } else {
        Write-Ok "Docker Desktop 安装完成"
    }
}

# ---------------------------------------------------------------- 5. 小结
Write-Step "5/5 下一步"
if ($needReboot) {
    Write-Host "  ⚠ 本次启用了系统功能，**必须重启**后再继续。" -ForegroundColor Yellow
    Write-Host "    重启后操作顺序：" -ForegroundColor Yellow
    Write-Host "      1) 启动 Docker Desktop（首次会提示接受协议、可选登录，可跳过）" -ForegroundColor Yellow
    Write-Host "      2) 等托盘图标变绿 / 出现 'Engine running'" -ForegroundColor Yellow
    Write-Host "      3) 新开终端验证：" -ForegroundColor Yellow
    Write-Host "           docker version" -ForegroundColor Yellow
    Write-Host "           docker compose version" -ForegroundColor Yellow
    Write-Host "           docker run --rm hello-world" -ForegroundColor Yellow
} else {
    Write-Host "  未检测到需要重启，直接启动 Docker Desktop 并验证：" -ForegroundColor Green
    Write-Host "    docker version"
    Write-Host "    docker compose version"
    Write-Host "    docker run --rm hello-world"
}

Write-Host "`n  验证通过后，回到项目根目录跑第一次容器化验证：" -ForegroundColor Cyan
Write-Host "    cp .env.example .env      # 按提示改所有 CHANGE_ME" -ForegroundColor Cyan
Write-Host "    docker compose build      # 先只构建，不启动，暴露镜像问题" -ForegroundColor Cyan
Write-Host "    docker compose up -d" -ForegroundColor Cyan
Write-Host "    curl http://127.0.0.1:8080/actuator/health`n" -ForegroundColor Cyan

Write-Host "提示：Docker Desktop 对个人/小团队免费，但需接受其许可协议；商业大规模使用有订阅要求。" -ForegroundColor DarkGray
