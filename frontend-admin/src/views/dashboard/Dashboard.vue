<template>
  <div class="cockpit">
    <div class="screen-grid"></div>
    <header class="cockpit-header">
      <div class="header-wing"></div>
      <div>
        <h1>新就业形态劳动者安全警示服务驾驶舱</h1>
        <p>济南市现场教育点运行监测</p>
      </div>
      <div class="header-meta">
        <strong>{{ clock }}</strong>
        <span>{{ todayText }}</span>
        <button class="fullscreen-btn" @click="toggleFullscreen">{{ isFullscreen ? '退出全屏' : '全屏展示' }}</button>
        <button class="cache-clear-btn" @click="clearServerCache" title="清除 Redis 缓存，让后台最新数据立即在用户端生效">刷新缓存</button>
      </div>
    </header>

    <section class="kpi-row">
      <article class="kpi-card" v-for="item in kpis" :key="item.label">
        <div class="kpi-icon" :class="item.tone">
          <component :is="item.icon" />
        </div>
        <div class="kpi-content">
          <div class="kpi-label">{{ item.label }}</div>
          <div class="kpi-value">
            <AnimatedNumber :value="item.value" :suffix="item.suffix" />
          </div>
          <div class="kpi-sub">{{ item.sub }}</div>
        </div>
      </article>
    </section>

    <main class="cockpit-main">
      <aside class="side-column">
        <section class="panel trend-panel">
          <PanelTitle title="近30日打卡趋势" />
          <div class="chart-legend"><span></span>打卡人次（人次）</div>
          <svg class="line-chart" viewBox="0 0 420 210" preserveAspectRatio="none">
            <defs>
              <linearGradient id="lineFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="rgba(56,189,248,.42)" />
                <stop offset="1" stop-color="rgba(37,99,235,0)" />
              </linearGradient>
            </defs>
            <g class="grid-lines">
              <line v-for="n in 5" :key="n" x1="0" x2="420" :y1="n * 35" :y2="n * 35" />
            </g>
            <polygon :points="trendAreaPoints" fill="url(#lineFill)" />
            <polyline :points="trendLinePoints" class="trend-line" />
            <circle
              v-for="p in trendDots"
              :key="p.key"
              :cx="p.x"
              :cy="p.y"
              r="3.5"
              class="trend-dot"
            />
          </svg>
          <div class="trend-axis">
            <span>{{ firstTrendLabel }}</span>
            <span>{{ middleTrendLabel }}</span>
            <span>{{ lastTrendLabel }}</span>
          </div>
        </section>

        <section class="panel summary-panel">
          <PanelTitle title="打卡数据概览" />
          <div class="summary-grid">
            <div class="summary-item">
              <span>累计有效打卡</span>
              <strong>{{ formatNumber(totalCheckins) }}</strong>
            </div>
            <div class="summary-item">
              <span>今日有效打卡</span>
              <strong>{{ formatNumber(todayCheckins) }}</strong>
            </div>
            <div class="summary-item">
              <span>本月有效打卡</span>
              <strong>{{ formatNumber(monthCheckins) }}</strong>
            </div>
            <div class="summary-item">
              <span>今日新增用户</span>
              <strong>{{ formatNumber(todayNewUsers) }}</strong>
            </div>
          </div>
        </section>
      </aside>

      <section class="center-column">
        <section class="panel map-panel">
          <PanelTitle title="济南市教育点分布" />
          <div class="jinan-map">
            <svg class="jinan-outline" viewBox="0 0 900 520" preserveAspectRatio="none">
              <defs>
                <radialGradient id="hotSpot" cx="50%" cy="50%" r="50%">
                  <stop offset="0" stop-color="rgba(250,204,21,.9)" />
                  <stop offset=".42" stop-color="rgba(34,211,238,.35)" />
                  <stop offset="1" stop-color="rgba(34,211,238,0)" />
                </radialGradient>
              </defs>
              <path class="city-shape" d="M62 310L110 246L208 236L265 170L362 188L430 98L540 130L618 88L730 116L840 202L812 308L738 388L626 360L550 438L436 414L326 458L244 402L146 420Z" />
              <path class="district-line" d="M208 236L326 458M265 170L436 414M430 98L550 438M540 130L626 360M730 116L738 388M110 246L812 308M146 420L840 202" />
              <circle v-for="hot in heatPoints" :key="hot.x + '-' + hot.y" :cx="hot.x" :cy="hot.y" :r="hot.r" fill="url(#hotSpot)" />
            </svg>

            <div
              v-for="district in districts"
              :key="district.name"
              class="district-label"
              :style="{ left: district.x + '%', top: district.y + '%' }"
            >
              {{ district.name }}
            </div>

            <div
              v-for="point in mapPoints"
              :key="point.id"
              class="map-marker"
              :style="{ left: point.x + '%', top: point.y + '%', animationDelay: point.delay + 's' }"
              :title="point.name"
            >
              <span></span>
            </div>

            <div class="map-legend">
              <strong>教育点密度</strong>
              <div class="legend-bar"></div>
              <div><span>低</span><span>高</span></div>
            </div>
          </div>
        </section>

        <section class="panel live-panel">
          <PanelTitle title="实时打卡动态" />
          <div class="live-table">
            <div class="live-head">
              <span>时间</span><span>姓名</span><span>手机号</span><span>所属区县</span><span>教育点名称</span><span>结果</span>
            </div>
            <TransitionGroup name="row" tag="div" class="live-body">
              <div class="live-row" v-for="row in liveRows" :key="row.key">
                <span>{{ row.time }}</span>
                <span>{{ row.name }}</span>
                <span>{{ maskPhone(row.phone) }}</span>
                <span>{{ row.district }}</span>
                <span>{{ row.location }}</span>
                <span class="success">打卡成功</span>
              </div>
            </TransitionGroup>
            <div class="empty-state live-empty" v-if="!liveRows.length">暂无实时打卡记录</div>
          </div>
        </section>
      </section>

      <aside class="side-column">
        <section class="panel rank-panel">
          <PanelTitle title="区县覆盖排名" meta="覆盖人数（人）" />
          <div class="rank-list">
            <div class="rank-item" v-for="(item, idx) in ranking" :key="item.name">
              <b>{{ idx + 1 }}</b>
              <span>{{ item.name }}</span>
              <div class="rank-bar"><i :style="{ width: item.percent + '%' }"></i></div>
              <strong>{{ formatNumber(item.value) }}</strong>
            </div>
            <div class="empty-state" v-if="!ranking.length">暂无区县覆盖数据</div>
          </div>
        </section>

        <section class="panel location-panel">
          <PanelTitle title="教育点列表" :meta="`在营 ${activeLocations} 个`" />
          <div class="location-list">
            <div class="location-item" v-for="item in locationRows" :key="item.id">
              <div class="loc-dot"></div>
              <div>
                <strong>{{ item.name }}</strong>
                <p>{{ item.address || normalizeDistrict(item.district_code || item.districtCode) }}</p>
              </div>
              <time>{{ normalizeDistrict(item.district_code || item.districtCode) }}</time>
            </div>
            <div class="empty-state" v-if="!locationRows.length">暂无教育点数据</div>
          </div>
        </section>
      </aside>
    </main>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { Checked, DataBoard, Location, User } from '@element-plus/icons-vue'
import { getDashboard } from '@/api/index.js'
import { ElMessage } from 'element-plus'
import axios from 'axios'

async function clearServerCache() {
  try {
    const token = localStorage.getItem('adminToken')
    await axios.post('/api/v1/admin/cache/clear', {}, {
      headers: { Authorization: 'Bearer ' + token }
    })
    ElMessage.success('缓存已清除，用户端数据即时生效')
  } catch {
    ElMessage.error('缓存清除失败，请检查网络')
  }
}

const AnimatedNumber = defineComponent({
  props: {
    value: { type: Number, default: 0 },
    suffix: { type: String, default: '' },
  },
  setup(props) {
    const display = ref(0)
    let raf = 0

    function animate() {
      cancelAnimationFrame(raf)
      const start = display.value
      const end = Number(props.value) || 0
      const startedAt = performance.now()
      const duration = 900

      function step(now) {
        const progress = Math.min((now - startedAt) / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        display.value = Math.round(start + (end - start) * eased)
        if (progress < 1) raf = requestAnimationFrame(step)
      }

      raf = requestAnimationFrame(step)
    }

    onMounted(animate)
    watch(() => props.value, animate)
    onUnmounted(() => cancelAnimationFrame(raf))

    return () => h('span', `${formatNumber(display.value)}${props.suffix}`)
  },
})

const PanelTitle = defineComponent({
  props: {
    title: { type: String, required: true },
    meta: { type: String, default: '' },
  },
  setup(props) {
    return () => h('div', { class: 'panel-title' }, [
      h('span', props.title),
      props.meta ? h('em', props.meta) : null,
    ])
  },
})

const loading = ref(true)
const data = ref({})
const liveOffset = ref(0)
const clock = ref('')
const todayText = ref('')
const isFullscreen = ref(false)
let clockTimer = 0
let liveTimer = 0
let refreshTimer = 0

const districtNameMap = {
  lixiang: '历城区',
  licheng: '历城区',
  lixia: '历下区',
  shizhong: '市中区',
  huaiyin: '槐荫区',
  tianqiao: '天桥区',
  changqing: '长清区',
  zhangqiu: '章丘区',
  jiyang: '济阳区',
  laiwu: '莱芜区',
  gangcheng: '钢城区',
  pingyin: '平阴县',
  shanghe: '商河县',
}

const districts = [
  { name: '商河县', x: 69, y: 20 },
  { name: '济阳区', x: 70, y: 48 },
  { name: '章丘区', x: 82, y: 47 },
  { name: '历城区', x: 58, y: 50 },
  { name: '历下区', x: 53, y: 62 },
  { name: '市中区', x: 48, y: 68 },
  { name: '槐荫区', x: 37, y: 60 },
  { name: '天桥区', x: 40, y: 48 },
  { name: '长清区', x: 30, y: 75 },
  { name: '平阴县', x: 16, y: 77 },
]

const trend = computed(() => {
  const rows = data.value.trend30Days?.length ? data.value.trend30Days : data.value.trend7Days
  if (rows?.length) return rows.map(item => ({ dt: item.dt, cnt: Number(item.cnt) || 0 }))
  return []
})

const totalCheckins = computed(() => Number(data.value.totalCheckins) || 0)
const todayCheckins = computed(() => Number(data.value.todayCheckins) || 0)
const monthCheckins = computed(() => Number(data.value.monthCheckins) || 0)
const todayNewUsers = computed(() => Number(data.value.todayNew) || 0)
const activeLocations = computed(() => Number(data.value.activeLocations) || 0)

const kpis = computed(() => [
  {
    label: '累计覆盖人数',
    value: Number(data.value.totalUsers) || 0,
    suffix: '人',
    sub: `今日新增 ${Number(data.value.todayNew) || 0} 人`,
    icon: User,
    tone: 'blue',
  },
  {
    label: '累计打卡人次',
    value: totalCheckins.value,
    suffix: '人次',
    sub: '有效现场打卡总量',
    icon: Checked,
    tone: 'cyan',
  },
  {
    label: '今日打卡人次',
    value: todayCheckins.value,
    suffix: '人次',
    sub: '今日有效现场打卡',
    icon: DataBoard,
    tone: 'aqua',
  },
  {
    label: '本月打卡人次',
    value: monthCheckins.value,
    suffix: '人次',
    sub: '本月有效现场打卡',
    icon: DataBoard,
    tone: 'indigo',
  },
  {
    label: '在营教育点',
    value: activeLocations.value,
    suffix: '个',
    sub: '当前开放点位',
    icon: Location,
    tone: 'orange',
  },
])

const trendMax = computed(() => Math.max(...trend.value.map(item => item.cnt), 1))
const trendLinePoints = computed(() => {
  if (!trend.value.length) return ''
  return trend.value.map((item, idx) => {
  const x = trend.value.length === 1 ? 0 : (idx / (trend.value.length - 1)) * 420
  const y = 190 - (item.cnt / trendMax.value) * 168
  return `${x.toFixed(1)},${y.toFixed(1)}`
}).join(' ')
})
const trendAreaPoints = computed(() => trendLinePoints.value ? `0,205 ${trendLinePoints.value} 420,205` : '')
const trendDots = computed(() => trend.value.map((item, idx) => {
  const x = trend.value.length === 1 ? 0 : (idx / (trend.value.length - 1)) * 420
  const y = 190 - (item.cnt / trendMax.value) * 168
  return { key: `${item.dt}-${idx}`, x, y }
}).filter((_, idx) => idx % Math.ceil(trend.value.length / 10) === 0))
const firstTrendLabel = computed(() => trend.value[0]?.dt || '-')
const middleTrendLabel = computed(() => trend.value[Math.floor(trend.value.length / 2)]?.dt || '-')
const lastTrendLabel = computed(() => trend.value[trend.value.length - 1]?.dt || '-')

const ranking = computed(() => {
  const rows = data.value.districtRanking?.length ? data.value.districtRanking.map(row => ({
      name: normalizeDistrict(row.district_code || row.districtCode || row.name),
      value: Number(row.workers || row.value || row.checkins) || 0,
    })) : []

  if (!rows.length) return []
  const max = Math.max(...rows.map(row => row.value), 1)
  return rows.slice(0, 9).map(row => ({ ...row, percent: Math.max(8, Math.round((row.value / max) * 100)) }))
})

const mapPoints = computed(() => {
  const rows = data.value.mapLocations?.length ? data.value.mapLocations : []
  return rows.map((row, idx) => {
    const lng = Number(row.longitude)
    const lat = Number(row.latitude)
    const hasGeo = Number.isFinite(lng) && Number.isFinite(lat)
    const districtPos = districts.find(d => d.name === normalizeDistrict(row.district_code || row.districtCode))
    return {
      id: row.id || idx,
      name: row.name || `教育点${idx + 1}`,
      x: hasGeo ? clamp(((lng - 116.35) / (117.75 - 116.35)) * 78 + 10, 8, 90) : districtPos?.x || 50,
      y: hasGeo ? clamp((1 - ((lat - 36.1) / (37.35 - 36.1))) * 72 + 12, 10, 86) : districtPos?.y || 50,
      delay: (idx % 8) * 0.25,
    }
  }).slice(0, 80)
})

const heatPoints = computed(() => mapPoints.value.slice(0, 9).map((point, idx) => ({
  x: point.x * 9,
  y: point.y * 5.2,
  r: 36 + (idx % 3) * 10,
})))

const recentRows = computed(() => {
  const rows = data.value.recentCheckins?.length ? data.value.recentCheckins : []

  return rows.map((row, idx) => ({
    key: `${row.id || idx}-${liveOffset.value}`,
    time: formatTime(row.checkin_time),
    name: row.nick_name || row.real_name || row.name || '-',
    phone: row.phone || '',
    district: row.district || inferDistrict(row.location_name),
    location: row.location_name || '-',
  }))
})

const liveRows = computed(() => {
  const rows = recentRows.value
  if (!rows.length) return []
  return [...rows.slice(liveOffset.value % rows.length), ...rows.slice(0, liveOffset.value % rows.length)].slice(0, 5)
})

const locationRows = computed(() => (data.value.mapLocations || []).slice(0, 8))

function normalizeDistrict(code) {
  if (!code) return '未分区'
  const raw = String(code)
  return districtNameMap[raw] || raw
}

function inferDistrict(locationName = '') {
  return districts.find(item => locationName.includes(item.name.slice(0, 2)))?.name || '济南市'
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatTime(value) {
  if (!value) return '--:--:--'
  const raw = String(value)
  if (/^\d{2}:\d{2}/.test(raw)) return raw
  const date = new Date(raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return raw.slice(11, 19) || raw
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

function maskPhone(phone = '') {
  const raw = String(phone)
  if (!raw) return '-'
  if (raw.includes('*')) return raw
  return raw.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function updateClock() {
  const now = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  clock.value = now.toLocaleTimeString('zh-CN', { hour12: false })
  todayText.value = `${now.getFullYear()}年${String(now.getMonth() + 1).padStart(2, '0')}月${String(now.getDate()).padStart(2, '0')}日 ${weekdays[now.getDay()]}`
}

function updateFullscreenState() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

async function toggleFullscreen() {
  if (document.fullscreenElement) {
    await document.exitFullscreen()
    return
  }
  await document.documentElement.requestFullscreen()
}

async function load() {
  loading.value = true
  try {
    const res = await getDashboard()
    data.value = res.data || {}
  } catch (err) {
    console.warn('dashboard data load failed', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  updateClock()
  load()
  document.addEventListener('fullscreenchange', updateFullscreenState)
  clockTimer = window.setInterval(updateClock, 1000)
  liveTimer = window.setInterval(() => {
    liveOffset.value += 1
  }, 3200)
  refreshTimer = window.setInterval(load, 60000)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', updateFullscreenState)
  window.clearInterval(clockTimer)
  window.clearInterval(liveTimer)
  window.clearInterval(refreshTimer)
})
</script>

<style scoped>
.cockpit {
  min-height: calc(100vh - 48px);
  padding: 18px;
  color: #dcecff;
  background:
    radial-gradient(circle at 50% 16%, rgba(37, 99, 235, .26), transparent 34%),
    radial-gradient(circle at 84% 60%, rgba(14, 165, 233, .12), transparent 28%),
    linear-gradient(180deg, #06152c 0%, #061022 48%, #030917 100%);
  position: relative;
  overflow: hidden;
}

.screen-grid {
  position: absolute;
  inset: 0;
  opacity: .22;
  background-image:
    linear-gradient(rgba(59, 130, 246, .16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, .16) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(circle at center, black, transparent 76%);
  pointer-events: none;
}

.cockpit-header {
  height: 74px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: start;
  position: relative;
  z-index: 1;
}

.cockpit-header h1 {
  font-size: 30px;
  line-height: 1;
  letter-spacing: 2px;
  color: #f8fbff;
  text-shadow: 0 0 18px rgba(59, 130, 246, .8);
  white-space: nowrap;
}

.cockpit-header p {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  color: rgba(191, 219, 254, .78);
}

.header-wing {
  width: 100%;
  height: 38px;
  border-top: 2px solid rgba(14, 165, 233, .65);
  transform: skewX(28deg);
  opacity: .7;
}

.header-meta {
  justify-self: end;
  text-align: right;
  color: #dbeafe;
}

.header-meta strong {
  display: block;
  font-size: 20px;
  letter-spacing: 1px;
}

.header-meta span {
  display: block;
  margin-top: 6px;
  color: rgba(219, 234, 254, .72);
  font-size: 13px;
}

.fullscreen-btn {
  display: inline-block;
  margin-top: 8px;
  height: 26px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(56, 189, 248, .55);
  color: #dbeafe;
  background: rgba(37, 99, 235, .16);
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.fullscreen-btn:hover {
  color: #ffffff;
  border-color: rgba(125, 211, 252, .9);
  background: rgba(14, 165, 233, .24);
}

.cache-clear-btn {
  display: inline-block;
  margin-top: 8px;
  margin-left: 8px;
  height: 26px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(52, 211, 153, .55);
  color: #d1fae5;
  background: rgba(16, 185, 129, .16);
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}
.cache-clear-btn:hover {
  color: #ffffff;
  border-color: rgba(52, 211, 153, .9);
  background: rgba(16, 185, 129, .32);
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  position: relative;
  z-index: 1;
}

.kpi-card,
.panel {
  border: 1px solid rgba(56, 189, 248, .34);
  background: linear-gradient(180deg, rgba(9, 33, 70, .88), rgba(5, 18, 40, .92));
  box-shadow: inset 0 0 30px rgba(37, 99, 235, .13), 0 0 20px rgba(2, 132, 199, .12);
}

.kpi-card {
  min-height: 104px;
  padding: 18px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  animation: cardIn .65s ease both;
}

.kpi-card::after,
.panel::after {
  content: "";
  position: absolute;
  left: -35%;
  top: 0;
  width: 32%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(125, 211, 252, .12), transparent);
  animation: sweep 5.8s linear infinite;
}

.kpi-icon {
  width: 58px;
  height: 58px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kpi-icon :deep(svg) {
  width: 34px;
  height: 34px;
}

.kpi-icon.blue { color: #60a5fa; background: rgba(37, 99, 235, .18); }
.kpi-icon.cyan { color: #38bdf8; background: rgba(14, 165, 233, .18); }
.kpi-icon.indigo { color: #818cf8; background: rgba(99, 102, 241, .18); }
.kpi-icon.orange { color: #fb923c; background: rgba(249, 115, 22, .18); }
.kpi-icon.aqua { color: #22d3ee; background: rgba(34, 211, 238, .16); }

.kpi-label {
  font-size: 14px;
  color: rgba(219, 234, 254, .72);
}

.kpi-value {
  margin-top: 8px;
  font-size: 30px;
  line-height: 1;
  font-weight: 800;
  color: #ffffff;
}

.kpi-sub {
  margin-top: 10px;
  font-size: 12px;
  color: #6ee7b7;
}

.cockpit-main {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 24.5% 1fr 25%;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.side-column,
.center-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  position: relative;
  overflow: hidden;
  min-height: 180px;
  padding: 14px;
}

.panel-title {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(56, 189, 248, .22);
  margin: -2px -2px 12px;
  padding: 0 2px 10px;
}

.panel-title span {
  font-size: 18px;
  color: #f8fbff;
  font-weight: 700;
}

.panel-title em {
  font-style: normal;
  font-size: 12px;
  color: rgba(191, 219, 254, .68);
}

.trend-panel {
  height: 320px;
}

.chart-legend {
  font-size: 12px;
  color: rgba(191, 219, 254, .78);
}

.chart-legend span {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 8px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 10px #38bdf8;
}

.line-chart {
  width: 100%;
  height: 220px;
  margin-top: 4px;
}

.grid-lines line {
  stroke: rgba(148, 163, 184, .13);
  stroke-width: 1;
}

.trend-line {
  fill: none;
  stroke: #38bdf8;
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 8px rgba(56, 189, 248, .9));
  stroke-dasharray: 900;
  stroke-dashoffset: 900;
  animation: drawLine 1.4s ease forwards;
}

.trend-dot {
  fill: #93c5fd;
  stroke: #e0f2fe;
  stroke-width: 1;
}

.trend-axis {
  display: flex;
  justify-content: space-between;
  color: rgba(219, 234, 254, .72);
  font-size: 12px;
}

.summary-panel {
  height: 330px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  height: calc(100% - 40px);
}

.summary-item {
  padding: 18px;
  border: 1px solid rgba(56, 189, 248, .24);
  background: rgba(15, 23, 42, .24);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.summary-item span {
  display: block;
  color: rgba(219, 234, 254, .66);
  font-size: 13px;
}

.summary-item strong {
  display: block;
  margin-top: 10px;
  color: #fff;
  font-size: 28px;
}

.map-panel {
  height: 440px;
}

.jinan-map {
  position: relative;
  height: calc(100% - 40px);
  border: 1px solid rgba(59, 130, 246, .2);
  background:
    radial-gradient(circle at 54% 50%, rgba(14, 165, 233, .16), transparent 36%),
    linear-gradient(135deg, rgba(15, 23, 42, .2), rgba(15, 23, 42, .48));
  overflow: hidden;
}

.jinan-map::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(24deg, rgba(59, 130, 246, .08) 1px, transparent 1px),
    linear-gradient(116deg, rgba(59, 130, 246, .07) 1px, transparent 1px);
  background-size: 56px 56px;
  opacity: .7;
}

.jinan-outline {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.city-shape {
  fill: rgba(14, 77, 152, .46);
  stroke: #1d8cff;
  stroke-width: 3;
  filter: drop-shadow(0 0 12px rgba(37, 99, 235, .7));
}

.district-line {
  fill: none;
  stroke: rgba(56, 189, 248, .28);
  stroke-width: 2;
}

.district-label {
  position: absolute;
  color: rgba(226, 232, 240, .9);
  font-size: 13px;
  font-weight: 600;
  text-shadow: 0 0 8px #001b3d;
}

.map-marker {
  position: absolute;
  width: 18px;
  height: 18px;
  transform: translate(-50%, -50%);
}

.map-marker::before,
.map-marker::after {
  content: "";
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  border: 1px solid rgba(56, 189, 248, .55);
  animation: markerWave 2.2s ease-out infinite;
}

.map-marker::after {
  animation-delay: .7s;
}

.map-marker span {
  position: absolute;
  left: 4px;
  top: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #7dd3fc;
  box-shadow: 0 0 14px #38bdf8;
}

.map-legend {
  position: absolute;
  right: 18px;
  bottom: 16px;
  width: 128px;
  padding: 12px;
  border: 1px solid rgba(56, 189, 248, .32);
  background: rgba(4, 13, 31, .68);
}

.map-legend strong {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
}

.legend-bar {
  height: 62px;
  width: 16px;
  background: linear-gradient(180deg, #f97316, #facc15, #22d3ee);
}

.map-legend div:last-child {
  margin-top: -62px;
  margin-left: 28px;
  height: 62px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: rgba(226, 232, 240, .8);
  font-size: 12px;
}

.live-panel {
  height: 210px;
}

.live-table {
  font-size: 13px;
}

.live-head,
.live-row {
  display: grid;
  grid-template-columns: 80px 74px 116px 92px 1fr 86px;
  align-items: center;
  gap: 8px;
}

.live-head {
  height: 34px;
  padding: 0 10px;
  color: #bfdbfe;
  background: rgba(37, 99, 235, .18);
  border: 1px solid rgba(59, 130, 246, .25);
}

.live-body {
  margin-top: 4px;
}

.live-empty {
  height: 118px;
}

.live-row {
  height: 30px;
  padding: 0 10px;
  color: rgba(226, 232, 240, .82);
  border-bottom: 1px solid rgba(59, 130, 246, .12);
}

.success {
  color: #4ade80;
}

.rank-panel {
  height: 320px;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rank-item {
  display: grid;
  grid-template-columns: 24px 62px 1fr 66px;
  align-items: center;
  gap: 10px;
  color: rgba(226, 232, 240, .86);
  font-size: 13px;
}

.rank-item b {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: rgba(37, 99, 235, .42);
  color: #fff;
}

.rank-item:nth-child(1) b {
  background: #b7791f;
}

.rank-bar {
  height: 12px;
  background: rgba(30, 64, 175, .46);
  border-radius: 999px;
  overflow: hidden;
}

.rank-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
  animation: growBar 1s ease both;
}

.rank-item strong {
  text-align: right;
  color: #dbeafe;
  font-weight: 600;
}

.location-panel {
  height: 330px;
}

.location-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.location-item {
  display: grid;
  grid-template-columns: 18px 1fr 58px;
  gap: 10px;
  align-items: center;
  min-height: 34px;
}

.loc-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 12px rgba(56, 189, 248, .8);
}

.location-item strong {
  display: block;
  color: #dbeafe;
  font-size: 13px;
  font-weight: 600;
}

.location-item p {
  margin-top: 4px;
  color: rgba(226, 232, 240, .72);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-item time {
  color: rgba(226, 232, 240, .78);
  font-size: 12px;
  text-align: right;
}

.empty-state {
  min-height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(191, 219, 254, .58);
  font-size: 13px;
  border: 1px dashed rgba(56, 189, 248, .2);
  background: rgba(15, 23, 42, .2);
}

.row-move,
.row-enter-active,
.row-leave-active {
  transition: all .55s ease;
}

.row-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.row-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@keyframes sweep {
  0% { transform: translateX(0); }
  100% { transform: translateX(430%); }
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes drawLine {
  to { stroke-dashoffset: 0; }
}

@keyframes markerWave {
  0% { opacity: .9; transform: scale(.3); }
  100% { opacity: 0; transform: scale(1.45); }
}

@keyframes growBar {
  from { width: 0; }
}

@keyframes pulseGlow {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(56, 189, 248, 0)); }
  50% { filter: drop-shadow(0 0 10px rgba(56, 189, 248, .45)); }
}

@media (max-width: 1360px) {
  .cockpit-main {
    grid-template-columns: 1fr;
  }

  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .map-panel {
    height: 420px;
  }
}
</style>
