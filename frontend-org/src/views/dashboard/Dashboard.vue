<template>
  <div class="dashboard">
    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card" v-for="k in kpis" :key="k.label">
        <div class="kpi-icon" :style="{ background: k.bg }">
          <component :is="k.icon" style="width:22px;height:22px;color:#fff"/>
        </div>
        <div class="kpi-body">
          <div class="kpi-num">{{ loading ? '-' : k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <!-- 最近打卡 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">最近打卡记录</span>
        <router-link to="/checkins" class="view-all">查看全部</router-link>
      </div>
      <el-table :data="recentCheckins" size="small" v-loading="loading" stripe>
        <el-table-column label="员工" width="150">
          <template #default="{ row }">
            <div>{{ row.nick_name || '—' }}</div>
            <div style="font-size:11px;color:#9ca3af">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="location_name" label="打卡点位" min-width="160" />
        <el-table-column prop="period" label="周期" width="100" />
        <el-table-column label="积分" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">+{{ row.points_earned }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkin_time" label="打卡时间" width="160" />
      </el-table>
      <div class="empty-tip" v-if="!loading && recentCheckins.length === 0">
        暂无打卡记录，请先在「我的点位」中添加点位
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboard } from '@/api/index.js'
import { Location, Checked, User, DataBoard } from '@element-plus/icons-vue'

const loading        = ref(true)
const data           = ref({})
const recentCheckins = ref([])

const kpis = computed(() => [
  {
    label: '我的点位',
    value: data.value.totalLocations ?? 0,
    icon:  Location,
    bg:    'linear-gradient(135deg,#10b981,#34d399)',
  },
  {
    label: '今日打卡',
    value: data.value.todayCheckins ?? 0,
    icon:  Checked,
    bg:    'linear-gradient(135deg,#0ea5e9,#38bdf8)',
  },
  {
    label: '本月打卡',
    value: data.value.monthCheckins ?? 0,
    icon:  DataBoard,
    bg:    'linear-gradient(135deg,#6366f1,#818cf8)',
  },
  {
    label: '员工人数',
    value: data.value.totalWorkers ?? 0,
    icon:  User,
    bg:    'linear-gradient(135deg,#f59e0b,#fbbf24)',
  },
])

async function load() {
  loading.value = true
  try {
    const res = await getDashboard()
    data.value           = res.data || {}
    recentCheckins.value = res.data?.recentCheckins || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 20px; }

.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.kpi-icon {
  width: 52px; height: 52px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.kpi-num { font-size: 28px; font-weight: 700; color: #0f1f2e; line-height: 1; margin-bottom: 4px; }
.kpi-label { font-size: 13px; color: #9ca3af; }

.card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.card-title { font-size: 15px; font-weight: 600; color: #0f1f2e; }
.view-all { font-size: 13px; color: #10b981; text-decoration: none; }
.view-all:hover { text-decoration: underline; }

.empty-tip { text-align: center; color: #9ca3af; padding: 32px 0; font-size: 14px; }
</style>
