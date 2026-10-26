// ============================================================================
// TeamPulse 轻量压测（零依赖，只用 Node 内置能力）
//
// 目的：给出可复现的 QPS 与分位延迟，而不是「感觉很流畅」。
// 用法：
//   node tests/perf/load.mjs --base http://127.0.0.1 \
//     --path "/api/v1/attendance/records?page=1&size=20" \
//     --token <JWT> --concurrency 20 --duration 20
//
// 参数：
//   --base        服务地址（默认 http://127.0.0.1）
//   --path        被测路径，可重复传多次（每个路径独立统计）
//   --token       Bearer Token；不给则用 --user/--password 自动登录取
//   --user/--password  登录账号（默认 admin / admin123）
//   --concurrency 并发数（默认 20）
//   --duration    持续秒数（默认 15）
//   --warmup      预热秒数（默认 3，不计入统计）
//   --json        把结果写到指定文件
//
// 输出：每个路径的 成功数/失败数/RPS/p50/p90/p95/p99/max
// ============================================================================

const argv = process.argv.slice(2)
const arg = (name, def) => {
  const i = argv.indexOf(name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : def
}
const argsAll = (name) => {
  const out = []
  argv.forEach((v, i) => { if (v === name && argv[i + 1]) out.push(argv[i + 1]) })
  return out
}

const BASE = (arg('--base', process.env.BASE_URL || 'http://127.0.0.1')).replace(/\/$/, '')
const PATHS = argsAll('--path')
if (!PATHS.length) PATHS.push('/api/v1/attendance/records?page=1&size=20')
const CONCURRENCY = Number(arg('--concurrency', '20'))
const DURATION = Number(arg('--duration', '15')) * 1000
const WARMUP = Number(arg('--warmup', '3')) * 1000
const JSON_OUT = arg('--json', '')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function getToken() {
  if (arg('--token')) return arg('--token')
  const user = arg('--user', 'admin')
  const password = arg('--password', process.env.ADMIN_PASSWORD || 'admin123')
  const res = await fetch(BASE + '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password }),
  })
  if (res.status !== 200) throw new Error(`登录取 token 失败：HTTP ${res.status}`)
  const json = await res.json()
  return json?.data?.token
}

const pct = (sorted, p) => {
  if (!sorted.length) return 0
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))
  return sorted[idx]
}

async function runPhase(token, durationMs, collect) {
  const stats = new Map(PATHS.map((p) => [p, { ok: 0, fail: 0, lat: [], statuses: {} }]))
  const deadline = Date.now() + durationMs
  let stop = false

  async function worker() {
    while (!stop && Date.now() < deadline) {
      for (const path of PATHS) {
        const t0 = performance.now()
        try {
          const res = await fetch(BASE + path, { headers: { Authorization: 'Bearer ' + token } })
          const dt = performance.now() - t0
          const s = stats.get(path)
          s.statuses[res.status] = (s.statuses[res.status] || 0) + 1
          await res.arrayBuffer()
          if (res.status >= 200 && res.status < 400) s.ok += 1
          else s.fail += 1
          if (collect) s.lat.push(dt)
        } catch {
          const s = stats.get(path)
          s.fail += 1
        }
      }
    }
  }

  const workers = Array.from({ length: CONCURRENCY }, () => worker())
  await Promise.all(workers).finally(() => { stop = true })
  return stats
}

const token = await getToken()
console.log(`目标 ${BASE}`)
console.log(`并发 ${CONCURRENCY}　正式阶段 ${DURATION / 1000}s（另预热 ${WARMUP / 1000}s）`)
console.log(`路径 ${PATHS.join(' , ')}\n`)

if (WARMUP > 0) {
  await runPhase(token, WARMUP, false)
}

const tStart = Date.now()
const stats = await runPhase(token, DURATION, true)
const elapsed = (Date.now() - tStart) / 1000

const report = []
console.log('路径'.padEnd(58) + '成功'.padEnd(8) + '失败'.padEnd(6) + 'RPS'.padEnd(9) + 'p50'.padEnd(9) + 'p95'.padEnd(9) + 'p99'.padEnd(9) + 'max')
for (const path of PATHS) {
  const s = stats.get(path)
  s.lat.sort((a, b) => a - b)
  const rps = (s.ok / elapsed)
  const row = {
    path,
    ok: s.ok,
    fail: s.fail,
    rps: Number(rps.toFixed(1)),
    p50: Number(pct(s.lat, 50).toFixed(1)),
    p90: Number(pct(s.lat, 90).toFixed(1)),
    p95: Number(pct(s.lat, 95).toFixed(1)),
    p99: Number(pct(s.lat, 99).toFixed(1)),
    max: Number((s.lat[s.lat.length - 1] || 0).toFixed(1)),
    statuses: s.statuses,
  }
  report.push(row)
  console.log(
    path.padEnd(58) +
    String(row.ok).padEnd(8) +
    String(row.fail).padEnd(6) +
    String(row.rps).padEnd(9) +
    String(row.p50).padEnd(9) +
    String(row.p95).padEnd(9) +
    String(row.p99).padEnd(9) +
    String(row.max)
  )
}

console.log(`\n统计窗口 ${elapsed.toFixed(1)}s　总请求 ${report.reduce((n, r) => n + r.ok + r.fail, 0)}`)
console.log('状态码分布：')
for (const r of report) {
  console.log(`  ${r.path} -> ${JSON.stringify(r.statuses)}`)
}

if (JSON_OUT) {
  const { writeFileSync } = await import('node:fs')
  writeFileSync(JSON_OUT, JSON.stringify({
    base: BASE, concurrency: CONCURRENCY, durationSec: DURATION / 1000, elapsed, rows: report,
  }, null, 2), 'utf8')
  console.log(`\n结果已写入 ${JSON_OUT}`)
}
