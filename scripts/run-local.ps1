<#
.SYNOPSIS
    本地一键启动 TeamPulse（后端 + 前端），用于查看页面原型。

.DESCRIPTION
    前提：本机已有一个可用的 MySQL 8（脚本会自动建库建表）。
    Redis / MQTT 不是必需的：
      · 没有 Redis  → nonce 去重自动降级为内存实现，打卡链路照常可用
      · 没有 MQTT   → 后端会告警并每 30 秒重试，Web 接口完全不受影响

    脚本会自动寻找 JDK / Maven / Node：
      优先用 PATH 里的，找不到就用项目自带的 .toolchain（Windows 上常用于离线环境）。

.PARAMETER Stop
    停止脚本启动的两个服务（按端口结束进程）。

.PARAMETER DbPassword
    本机 MySQL 的 root 密码。必填且没有默认值——脚本里写死密码
    等于把数据库口令随仓库一起公开（而且提交历史里永远删不掉）。

.EXAMPLE
    .\scripts\run-local.ps1 -DbPassword 你的密码
    .\scripts\run-local.ps1 -Stop
#>
[CmdletBinding()]
param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbName = 'teampulse',
    [string]$DbUser = 'root',
    [Parameter(Mandatory = $true, HelpMessage = '本机 MySQL 密码；没有默认值，请显式传入')]
    [string]$DbPassword,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [string]$NodePath = '',
    [switch]$NoFrontend,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root 'backend'
$FrontendDir = Join-Path $Root 'frontend'
$Toolchain = Join-Path $Root '.toolchain'
$LogDir = Join-Path $Root '.run-logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Find-Tool {
    param([string]$Name, [string]$Fallback)
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($Fallback -and (Test-Path $Fallback)) { return $Fallback }
    return $null
}

function Stop-ByPort {
    param([int]$Port, [string]$Label)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) { Write-Host "  $Label (端口 $Port) 未在运行" -ForegroundColor DarkGray; return }
    # 注意：不能用 $pid 作循环变量——它是 PowerShell 的只读自动变量，
    # 赋值会抛「Cannot overwrite variable PID」，导致 -Stop 与端口释放整体失败。
    foreach ($procId in ($conn.OwningProcess | Select-Object -Unique)) {
        try { Stop-Process -Id $procId -Force -ErrorAction Stop; Write-Host "  已停止 $Label (PID $procId)" -ForegroundColor Yellow } catch { }
    }
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSec, [string]$Label)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { return $true }
        Start-Sleep -Milliseconds 800
    }
    Write-Host "  [超时] $Label 在 $TimeoutSec 秒内没有启动，请查看日志" -ForegroundColor Red
    return $false
}

# ---------------- 停止模式 ----------------
if ($Stop) {
    Write-Host "停止 TeamPulse 本地服务..." -ForegroundColor Cyan
    Stop-ByPort -Port $FrontendPort -Label '前端'
    Stop-ByPort -Port $BackendPort -Label '后端'
    Write-Host "完成。" -ForegroundColor Green
    return
}

# ---------------- 环境检查 ----------------
Write-Host "`n[1/5] 检查工具链" -ForegroundColor Cyan
$java = Find-Tool 'java' (Join-Path $Toolchain 'jdk\bin\java.exe')
$mvn = Find-Tool 'mvn' (Join-Path $Toolchain 'maven\bin\mvn.cmd')

# Node 的查找顺序：显式参数 → 环境变量 NODE_EXE → PATH → .toolchain\node
$node = $null
if ($NodePath -and (Test-Path $NodePath)) { $node = $NodePath }
elseif ($env:NODE_EXE -and (Test-Path $env:NODE_EXE)) { $node = $env:NODE_EXE }
else { $node = Find-Tool 'node' (Join-Path $Toolchain 'node\bin\node.exe') }

if (-not $java) { throw '找不到 java。请安装 JDK 17+，或把 JDK 放到 .toolchain\jdk' }
if (-not $mvn) { throw '找不到 mvn。请安装 Maven 3.9+，或把 Maven 放到 .toolchain\maven' }
if (-not $node) { throw '找不到 node。请安装 Node.js 20+（或把 node 放到 .toolchain\node，或用 -NodePath 指定）' }
Write-Host "  java : $java" -ForegroundColor DarkGray
Write-Host "  mvn  : $mvn" -ForegroundColor DarkGray
Write-Host "  node : $node" -ForegroundColor DarkGray

# JAVA_HOME 必须与找到的 java 一致，否则 Maven 会用错 JDK
$javaHome = Split-Path -Parent (Split-Path -Parent $java)
$env:JAVA_HOME = $javaHome

if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
    Write-Host "`n[警告] 前端依赖未安装，请先执行：cd frontend; npm install" -ForegroundColor Yellow
}

Write-Host "`n[2/5] 检查端口" -ForegroundColor Cyan
# 只清理本次真正要启动的端口：-NoFrontend 时不要动前端（可能是用户自己起的 dev server）
$portsToCheck = @($BackendPort)
if (-not $NoFrontend) { $portsToCheck += $FrontendPort }
foreach ($p in $portsToCheck) {
    $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if ($c) {
        Write-Host "  端口 $p 已被占用，先尝试释放（若为其他程序请换端口）" -ForegroundColor Yellow
        Stop-ByPort -Port $p -Label "端口$p"
        Start-Sleep -Seconds 1
    } else { Write-Host "  端口 $p 空闲" -ForegroundColor DarkGray }
}

# Dev 环境需要临时目录；Windows 上系统临时目录有时不可写，统一指到 target 下
$tmpDir = Join-Path $BackendDir 'target\tmp'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

# ---------------- 启动后端 ----------------
Write-Host "`n[3/5] 启动后端（首次启动会自动建库建表，约 20~40 秒）" -ForegroundColor Cyan
$env:DB_HOST = $DbHost
$env:DB_PORT = "$DbPort"
$env:DB_NAME = $DbName
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:MQTT_ENABLED = 'false'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_PORT = "$BackendPort"

$mvnArgs = @('-B')
$settingsFile = Join-Path $Toolchain 'settings.xml'
if (Test-Path $settingsFile) { $mvnArgs += @('-s', $settingsFile) }
$mvnArgs += @(
    '-f', (Join-Path $BackendDir 'pom.xml'),
    'spring-boot:run',
    "-Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=$tmpDir"
)

$beOut = Join-Path $LogDir 'backend.out.log'
$beErr = Join-Path $LogDir 'backend.err.log'
$backendProc = Start-Process -FilePath $mvn -ArgumentList $mvnArgs -WorkingDirectory $BackendDir `
    -RedirectStandardOutput $beOut -RedirectStandardError $beErr -WindowStyle Hidden -PassThru
Write-Host "  后端进程 PID $($backendProc.Id)，日志：$beOut" -ForegroundColor DarkGray

if (-not (Wait-Port -Port $BackendPort -TimeoutSec 120 -Label '后端')) {
    Write-Host "`n后端日志最后 30 行：" -ForegroundColor Yellow
    Get-Content $beOut -Tail 30 -ErrorAction SilentlyContinue
    Get-Content $beErr -Tail 30 -ErrorAction SilentlyContinue
    throw '后端启动失败'
}

# 探测健康状态（本地无 Redis 时 dev profile 已关闭 redis 探测，应为 UP）
try {
    $health = Invoke-RestMethod "http://127.0.0.1:$BackendPort/actuator/health" -TimeoutSec 10
    Write-Host "  后端健康状态: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "  健康检查暂未通过（不影响页面浏览，可稍后重试）" -ForegroundColor Yellow
}

# ---------------- 启动前端 ----------------
$frontendProc = $null
if (-not $NoFrontend) {
    Write-Host "`n[4/5] 启动前端开发服务器" -ForegroundColor Cyan
    $viteBin = Join-Path $FrontendDir 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path $viteBin)) { throw "找不到 vite：$viteBin，请先 npm install" }

    $feOut = Join-Path $LogDir 'frontend.out.log'
    $feErr = Join-Path $LogDir 'frontend.err.log'
    $frontendProc = Start-Process -FilePath $node `
        -ArgumentList @($viteBin, '--host', '127.0.0.1', '--port', "$FrontendPort") `
        -WorkingDirectory $FrontendDir `
        -RedirectStandardOutput $feOut -RedirectStandardError $feErr -WindowStyle Hidden -PassThru
    Write-Host "  前端进程 PID $($frontendProc.Id)，日志：$feOut" -ForegroundColor DarkGray

    if (-not (Wait-Port -Port $FrontendPort -TimeoutSec 60 -Label '前端')) {
        Get-Content $feErr -Tail 20 -ErrorAction SilentlyContinue
        throw '前端启动失败'
    }
}

# ---------------- 完成 ----------------
Write-Host "`n[5/5] 启动完成" -ForegroundColor Green
Write-Host ("=" * 62) -ForegroundColor DarkGray
Write-Host "  页面入口 : http://127.0.0.1:$FrontendPort/" -ForegroundColor White
Write-Host "  后端接口 : http://127.0.0.1:$BackendPort/api/v1" -ForegroundColor White
Write-Host "  接口文档 : http://127.0.0.1:$BackendPort/swagger-ui.html" -ForegroundColor White
Write-Host "  健康检查 : http://127.0.0.1:$BackendPort/actuator/health" -ForegroundColor White
Write-Host ("=" * 62) -ForegroundColor DarkGray
Write-Host "  登录账号 : admin / admin123" -ForegroundColor Yellow
Write-Host "  首次登录后请立即修改密码；管理员会先看到「选择面板」页。" -ForegroundColor DarkGray
Write-Host "`n  查看演示设备密钥：" -ForegroundColor Cyan
Write-Host "    Select-String -Path '$beOut' -Pattern '设备密钥' -Context 0,0" -ForegroundColor DarkGray
Write-Host "`n  停止服务：" -ForegroundColor Cyan
Write-Host "    .\scripts\run-local.ps1 -Stop" -ForegroundColor DarkGray
Write-Host "`n  模拟一次打卡（另开一个终端）：" -ForegroundColor Cyan
Write-Host "    node device/simulator/http-simulator.mjs --sn TP-ESP32-DEMO --secret <上面的密钥> --employee E1000" -ForegroundColor DarkGray
