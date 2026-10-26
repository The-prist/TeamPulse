#!/usr/bin/env pwsh
<#
.SYNOPSIS
    把 TeamPulse 的数据库连接写入 DBeaver 的连接列表。

.DESCRIPTION
    DBeaver 把连接保存在：
        %APPDATA%\DBeaverData\workspace6\General\.dbeaver\data-sources.json
    本脚本会在该文件中新增（或更新）一个名为 "TeamPulse" 的 MySQL 连接。

    重要：DBeaver 运行时会用自己的内存副本覆盖这个文件，所以**必须先完全退出 DBeaver**
    再运行本脚本，然后重新打开 DBeaver。

    密码不在本文件中（DBeaver 把密码加密存在 credentials-config.json），
    首次双击连接时输入一次密码即可，勾选「保存密码」下次就不再问。

.PARAMETER DbHost
    MySQL 主机，默认 127.0.0.1

.PARAMETER DbPort
    端口，默认 3306

.PARAMETER DbName
    数据库名，默认 teampulse

.PARAMETER DbUser
    用户名，默认 root

.PARAMETER Name
    在 DBeaver 里显示的名称，默认 TeamPulse

.PARAMETER Force
    DBeaver 仍在运行时也强制写入（不推荐）

.EXAMPLE
    .\scripts\dbeaver-add-connection.ps1

.EXAMPLE
    .\scripts\dbeaver-add-connection.ps1 -Name "TeamPulse 本地库"
#>
[CmdletBinding()]
param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbName = 'teampulse',
    [string]$DbUser = 'root',
    [string]$Name = 'TeamPulse',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$configDir = Join-Path $env:APPDATA 'DBeaverData\workspace6\General\.dbeaver'
$configFile = Join-Path $configDir 'data-sources.json'

Write-Host "[1/4] 检查 DBeaver 是否在运行" -ForegroundColor Cyan
$proc = Get-Process -Name 'dbeaver' -ErrorAction SilentlyContinue
if ($proc -and -not $Force) {
    Write-Host "  DBeaver 正在运行（PID $($proc.Id)）。" -ForegroundColor Yellow
    Write-Host "  请先完全退出 DBeaver（右下角托盘图标也要退出），然后重新运行本脚本。" -ForegroundColor Yellow
    Write-Host "  原因：DBeaver 退出时会用内存中的旧配置覆盖该文件，刚写入的连接会丢失。" -ForegroundColor DarkGray
    exit 1
}
if (-not $proc) { Write-Host "  DBeaver 未运行，可以安全写入" -ForegroundColor DarkGray }

Write-Host "[2/4] 准备配置文件" -ForegroundColor Cyan
if (-not (Test-Path $configDir)) {
    New-Item -ItemType Directory -Force -Path $configDir | Out-Null
    Write-Host "  已创建目录：$configDir" -ForegroundColor DarkGray
}

# 备份现有配置
if (Test-Path $configFile) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backup = "$configFile.bak-$stamp"
    Copy-Item $configFile $backup -Force
    Write-Host "  已备份：$backup" -ForegroundColor DarkGray
}

Write-Host "[3/4] 写入连接" -ForegroundColor Cyan

# 读取已有内容（文件可能是空的或不存在）
$root = $null
if ((Test-Path $configFile) -and (Get-Item $configFile).Length -gt 0) {
    try {
        $root = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        Write-Host "  现有文件不是合法 JSON，将重建（原文件已备份）" -ForegroundColor Yellow
        $root = $null
    }
}
if (-not $root) {
    $root = [pscustomobject]@{ folders = [pscustomobject]@{}; connections = [pscustomobject]@{} }
}
if (-not $root.PSObject.Properties['connections']) {
    $root | Add-Member -NotePropertyName connections -NotePropertyValue ([pscustomobject]@{}) -Force
}
if (-not $root.PSObject.Properties['folders']) {
    $root | Add-Member -NotePropertyName folders -NotePropertyValue ([pscustomobject]@{}) -Force
}

# 如果已有同名连接，复用它的 id（幂等，重复运行不会产生多条）
$existingId = $null
foreach ($prop in $root.connections.PSObject.Properties) {
    if ($prop.Value.name -eq $Name) { $existingId = $prop.Name; break }
}
$id = if ($existingId) { $existingId } else { "mysql8-$(New-Guid)" }

# MySQL 8 默认认证插件是 caching_sha2_password，DBeaver 不显式打开 allowPublicKeyRetrieval
# 会报 "Public Key Retrieval is not allowed"。这里直接写进连接串与驱动属性，避免踩坑。
$url = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"

$connection = [ordered]@{
    provider      = 'mysql'
    driver        = 'mysql8'
    name          = $Name
    'save-password' = $false
    'read-only'   = $false
    folder        = ''
    configuration = [ordered]@{
        host              = $DbHost
        port              = "$DbPort"
        database          = $DbName
        url               = $url
        configurationType = 'MANUAL'
        type              = 'dev'
        'auth-model'      = 'native'
        user              = $DbUser
        properties        = [ordered]@{
            allowPublicKeyRetrieval = 'true'
            useSSL                  = 'false'
            serverTimezone          = 'Asia/Shanghai'
        }
    }
}

$root.connections | Add-Member -NotePropertyName $id -NotePropertyValue ([pscustomobject]$connection) -Force

$json = $root | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($configFile, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "  已写入连接：" -ForegroundColor Green
Write-Host "    名称     : $Name" -ForegroundColor DarkGray
Write-Host "    地址     : ${DbHost}:${DbPort}" -ForegroundColor DarkGray
Write-Host "    数据库   : $DbName" -ForegroundColor DarkGray
Write-Host "    用户名   : $DbUser" -ForegroundColor DarkGray
Write-Host "    连接串   : $url" -ForegroundColor DarkGray

Write-Host "[4/4] 下一步" -ForegroundColor Cyan
Write-Host "  1) 重新打开 DBeaver，左侧「数据库导航」里应出现连接：$Name"
Write-Host "  2) 双击它 → 首次会提示输入密码（填你自己的 MySQL 密码），可勾选「保存密码」"
Write-Host "  3) 首次连接会自动下载 MySQL 驱动，需要联网，约 10~30 秒"
Write-Host ""
Write-Host "  如果没有出现：说明 DBeaver 版本不认这个配置格式，" -ForegroundColor Yellow
Write-Host "  请用 DBeaver 界面手动新建（见 docs/数据库连接说明.md），或把下面的信息填进去：" -ForegroundColor Yellow
Write-Host "    主机 127.0.0.1 / 端口 3306 / 数据库 teampulse / 用户 root / 密码 <你的密码>" -ForegroundColor White
