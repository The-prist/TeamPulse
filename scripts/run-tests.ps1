# =============================================================================
# TeamPulse 一键自动化测试
#
# 三层：
#   1. 后端单元测试（mvn test，JUnit 5）
#   2. 前端构建校验（vite build，能发现语法/类型/模板错误）
#   3. 接口端到端检查（tests/api/e2e.mjs，需要后端与数据库在运行）
#
# 用法：
#   .\scripts\run-tests.ps1                 # 跑全部（含写入类用例）
#   .\scripts\run-tests.ps1 -ReadOnly       # 跳过写数据（打卡写库、审计写操作）
#   .\scripts\run-tests.ps1 -SkipFrontend   # 只跑后端单测 + 接口检查
#   .\scripts\run-tests.ps1 -BaseUrl http://1.2.3.4   # 打远端环境
#
# 退出码：0 全绿；1 有失败。可直接接 CI。
# =============================================================================
param(
    [switch]$ReadOnly,
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Continue'
$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } elseif ($MyInvocation.MyCommand.Path) { Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path) } else { 'E:\codestorge' }
Set-Location $root

$results = @()
$failed = $false
function Record($layer, $name, $ok, $detail) {
    $script:results += [pscustomobject]@{ 层 = $layer; 项 = $name; 结果 = $(if ($ok) { 'PASS' } else { 'FAIL' }); 说明 = $detail }
    if (-not $ok) { $script:failed = $true }
    $mark = if ($ok) { '✓' } else { '✗' }
    Write-Host ("  {0} [{1}] {2}  {3}" -f $mark, $layer, $name, $detail)
}

Write-Host "`n════════ TeamPulse 自动化测试 ════════" -ForegroundColor Cyan
Write-Host "  项目: $root"
Write-Host "  目标: $BaseUrl"
Write-Host "  模式: $(if ($ReadOnly) { '只读（跳过写数据用例）' } else { '完整（含写数据用例）' })`n"

# ---------------------------------------------------------------- 1. 后端单测
if (-not $SkipBackend) {
    Write-Host "── 1. 后端单元测试 ──" -ForegroundColor Yellow
    $mvn = Join-Path $root '.toolchain\maven\bin\mvn.cmd'
    if (-not (Test-Path $mvn)) { $mvn = 'mvn' }
    $settings = Join-Path $root '.toolchain\settings.xml'
    $env:JAVA_HOME = if (Test-Path 'E:\dev\jdk17') { 'E:\dev\jdk17' } else { Join-Path $root '.toolchain\jdk' }
    $tmp = Join-Path $root '.run-logs\tmp'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $env:TMP = $env:TEMP = $tmp

    $mvnArgs = @('-B', '-ntp', '-f', 'backend/pom.xml', 'test')
    if (Test-Path $settings) { $mvnArgs = @('-B', '-ntp', '-s', $settings, '-f', 'backend/pom.xml', 'test') }
    $out = & $mvn @mvnArgs 2>&1 | Out-String
    $m = [regex]::Match($out, 'Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)')
    $buildOk = $out -match 'BUILD SUCCESS'
    if ($m.Success) {
        $run = [int]$m.Groups[1].Value; $fail = [int]$m.Groups[2].Value; $err = [int]$m.Groups[3].Value
        Record '后端' "单元测试 $run 个用例" ($buildOk -and $fail -eq 0 -and $err -eq 0) "失败 $fail / 错误 $err"
    } else {
        Record '后端' '单元测试' $buildOk '未解析到 Tests run 汇总'
    }
}

# ---------------------------------------------------------------- 2. 前端构建
if (-not $SkipFrontend) {
    Write-Host "`n── 2. 前端构建校验 ──" -ForegroundColor Yellow
    $node = 'C:\Users\12986\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
    if (-not (Test-Path $node)) { $node = 'node' }
    $tmp = Join-Path $root '.run-logs\tmp'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $env:TMP = $env:TEMP = $tmp
    Push-Location (Join-Path $root 'frontend')
    $out = & $node 'node_modules\vite\bin\vite.js' build 2>&1 | Out-String
    Pop-Location
    $ok = ($out -match 'built in') -and -not ($out -match 'error during build|✗ Build failed')
    $mods = [regex]::Match($out, '(\d+) modules transformed')
    Record '前端' 'vite build' $ok $(if ($mods.Success) { "$($mods.Groups[1].Value) 模块" } else { '' })
}

# ---------------------------------------------------------------- 3. 服务在线
Write-Host "`n── 3. 服务可用性 ──" -ForegroundColor Yellow
$alive = $false
try {
    $h = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 8
    $alive = ($h.status -eq 'UP')
    Record '服务' '后端健康检查' $alive "status=$($h.status) db=$($h.components.db.status)"
} catch {
    Record '服务' '后端健康检查' $false $_.Exception.Message
}
if (-not $alive) {
    Write-Host "`n  后端不可用，跳过接口检查。先启动：" -ForegroundColor Red
    Write-Host "    .\scripts\run-local.ps1 -DbPassword <密码>" -ForegroundColor Red
}

# ---------------------------------------------------------------- 4. 接口 E2E
if ($alive) {
    Write-Host "`n── 4. 接口端到端检查 ──" -ForegroundColor Yellow
    $node = 'C:\Users\12986\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
    if (-not (Test-Path $node)) { $node = 'node' }

    # 从数据库取一台启用设备，供真实 HMAC 上报使用
    $mysql = 'E:\dev\mysql\bin\mysql.exe'
    if (-not $DbPassword) { $DbPassword = '123456' }
    if (Test-Path $mysql) {
        $row = & $mysql -h 127.0.0.1 -uroot "--password=$DbPassword" -N -B `
            -e "SELECT device_sn, secret FROM teampulse.iot_device WHERE status <> 'DISABLED' ORDER BY id LIMIT 1;" 2>$null
        if ($row) {
            $parts = ($row | Select-Object -First 1) -split "`t"
            $env:DEVICE_SN = $parts[0].Trim()
            $env:DEVICE_SECRET = $parts[1].Trim()
        }
    }

    $e2eArgs = @('tests/api/e2e.mjs', '--base', $BaseUrl)
    if ($ReadOnly) { $e2eArgs += '--read-only' }
    $json = Join-Path $root '.run-logs\e2e-result.json'
    $env:E2E_JSON = $json
    $e2eOut = & $node @e2eArgs 2>&1 | Out-String
    Write-Host $e2eOut

    $summary = [regex]::Match($e2eOut, '通过\s*(\d+)\s*/\s*(\d+)')
    if ($summary.Success) {
        $p = [int]$summary.Groups[1].Value; $t = [int]$summary.Groups[2].Value
        Record '接口' "端到端断言 $t 项" ($p -eq $t) "通过 $p / $t"
    } else {
        Record '接口' '端到端检查' $false '未能解析汇总'
    }
}

# ---------------------------------------------------------------- 汇总
Write-Host "`n════════ 汇总 ════════" -ForegroundColor Cyan
foreach ($r in $results) {
    $color = if ($r.结果 -eq 'PASS') { 'Green' } else { 'Red' }
    Write-Host ("  {0,-6} {1,-22} {2,-6} {3}" -f $r.层, $r.项, $r.结果, $r.说明) -ForegroundColor $color
}
$totalItems = @($results).Count
$passItems = @($results | Where-Object { $_.结果 -eq 'PASS' }).Count
Write-Host ("  层数 $totalItems 项，通过 $passItems，失败 $($totalItems - $passItems)") -ForegroundColor $(if ($failed) { 'Red' } else { 'Green' })

$report = Join-Path $root '.run-logs\test-report.json'
$results | ConvertTo-Json -Depth 3 | Set-Content $report -Encoding UTF8
Write-Host "  明细: $report"

if ($failed) { exit 1 } else { exit 0 }
