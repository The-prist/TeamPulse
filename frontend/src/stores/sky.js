import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { skyApi } from '@/api'
import { BUCKET_LABEL, weatherBucket } from '@/utils/skyWeather'

const REGION_KEY = 'tp_sky_region'

/**
 * 登录页「我所在的城市 + 现在什么天气」。
 *
 * 数据来源两条：
 *   1. 默认：后端按访客真实 IP 定位（/api/v1/public/sky-context）
 *   2. 手动：访客在登录卡上改选省/市/区，选择结果存 localStorage，下次进来沿用
 *
 * 任何一个环节失败（后端不可达 / 第三方超时）都只是拿不到天气，
 * 画面回落到「多云」——登录页不能因为查天气失败而白屏。
 */
export const useSkyStore = defineStore('sky', () => {
  /** 后端返回的天空上下文 */
  const context = ref(null)
  /** 行政区划树（省/市/区） */
  const regions = ref([])
  /** 手动选择的地区，null 表示按 IP 自动定位 */
  const manual = ref(readManual())
  /** 调试用：强制某个天气档位，'auto' 表示跟随真实天气 */
  const override = ref('auto')
  const loading = ref(false)
  const error = ref('')

  const city = computed(() => context.value?.city || '')
  const province = computed(() => context.value?.province || '')
  const district = computed(() => context.value?.district || '')
  const ipCity = computed(() => context.value?.ipCity || '')
  const temperature = computed(() => {
    const t = context.value?.temperature
    return typeof t === 'number' ? Math.round(t) : null
  })
  const cloudCover = computed(() => context.value?.cloudCover ?? 50)
  const weatherCode = computed(() => context.value?.weatherCode ?? 3)
  const source = computed(() => context.value?.source || 'fallback')
  const isManual = computed(() => Boolean(manual.value?.city || manual.value?.district))

  /** 真正生效的天气档位：调试覆盖 > 真实天气 */
  const bucket = computed(() => {
    if (override.value !== 'auto') return override.value
    return weatherBucket(weatherCode.value, cloudCover.value)
  })

  const bucketLabel = computed(() => BUCKET_LABEL[bucket.value] || '多云')
  /** 登录卡上展示的「城市 + 区」 */
  const placeLabel = computed(() => {
    const c = (city.value || '').replace(/市$/, '')
    const d = district.value || ''
    return d ? `${c} · ${d}` : c || '定位中'
  })

  function readManual() {
    try {
      const raw = localStorage.getItem(REGION_KEY)
      if (!raw) return null
      const v = JSON.parse(raw)
      return v && (v.city || v.district) ? v : null
    } catch {
      return null
    }
  }

  function persistManual() {
    try {
      if (manual.value?.city || manual.value?.district) {
        localStorage.setItem(REGION_KEY, JSON.stringify(manual.value))
      } else {
        localStorage.removeItem(REGION_KEY)
      }
    } catch {
      /* 存储被禁用时忽略即可，只影响下次是否记住 */
    }
  }

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const params = {}
      if (manual.value?.province) params.province = manual.value.province
      if (manual.value?.city) params.city = manual.value.city
      if (manual.value?.district) params.district = manual.value.district
      context.value = await skyApi.context(params)
      if (context.value?.source === 'fallback' && isManual.value) {
        error.value = '所选地点查不到天气，已回落到默认城市'
      }
    } catch (e) {
      error.value = '天气获取失败，按多云展示'
    } finally {
      loading.value = false
    }
  }

  async function loadRegions() {
    if (regions.value.length) return regions.value
    try {
      const tree = await skyApi.regions()
      regions.value = toCascader(tree)
    } catch (e) {
      regions.value = []
    }
    return regions.value
  }

  /** 后端给的是 {code,name,children}，转成 el-cascader 需要的 {value,label,children} */
  function toCascader(nodes) {
    if (!Array.isArray(nodes)) return []
    return nodes.map((n) => {
      const item = { value: n.name, label: n.name, code: n.code }
      if (Array.isArray(n.children) && n.children.length) {
        item.children = toCascader(n.children)
      }
      return item
    })
  }

  async function setManual(next) {
    manual.value = next && (next.city || next.district) ? next : null
    persistManual()
    await load()
  }

  async function resetManual() {
    manual.value = null
    persistManual()
    await load()
  }

  return {
    context,
    regions,
    manual,
    override,
    loading,
    error,
    city,
    province,
    district,
    ipCity,
    temperature,
    cloudCover,
    weatherCode,
    source,
    isManual,
    bucket,
    bucketLabel,
    placeLabel,
    load,
    loadRegions,
    setManual,
    resetManual,
  }
})
