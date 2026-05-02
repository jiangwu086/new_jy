<template>
  <div>
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent>
        <el-form-item label="点位ID">
          <el-input v-model="filters.locationId" placeholder="点位ID" clearable style="width:120px"/>
        </el-form-item>
        <el-form-item label="周期">
          <el-input v-model="filters.period" placeholder="如 2026-04" clearable style="width:130px"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:100px">
            <el-option label="有效" :value="1"/>
            <el-option label="无效" :value="0"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search"
                     style="background:#10b981;border-color:#10b981">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button type="success" :loading="exporting" @click="doExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="stat-row">共 <b>{{ total }}</b> 条记录</div>

    <el-card shadow="never" style="margin-top:12px">
      <el-table :data="list" v-loading="loading" size="default" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="员工" width="180">
          <template #default="{ row }">
            <div>{{ row.real_name || row.nick_name || row.phone || '—' }}</div>
            <div style="font-size:12px;color:#9ca3af" v-if="row.phone">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="location_name" label="点位" min-width="140" />
        <el-table-column prop="period" label="周期" width="100" align="center"/>
        <el-table-column label="积分" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">+{{ row.points_earned }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '有效' : '无效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkin_time" label="打卡时间" width="170" />
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCheckins } from '@/api/index.js'

const loading = ref(false)
const list    = ref([])
const total   = ref(0)
const page    = ref(1)
const size    = ref(20)

const filters = reactive({ locationId: '', period: '', status: null })

async function load() {
  loading.value = true
  try {
    const params = {
      page: page.value, size: size.value,
      ...(filters.locationId ? { locationId: filters.locationId } : {}),
      ...(filters.period     ? { period:     filters.period }     : {}),
      ...(filters.status !== null && filters.status !== '' ? { status: filters.status } : {}),
    }
    const res = await getCheckins(params)
    list.value  = res.data?.list  || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function reset()  { Object.assign(filters, { locationId:'', period:'', status:null }); page.value=1; load() }

onMounted(load)

const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    const token  = localStorage.getItem('orgToken')
    const url    = '/api/v1/org/checkins/export'
    const resp   = await fetch(url, { headers: { Authorization: 'Bearer ' + token } })
    const blob   = await resp.blob()
    const a      = document.createElement('a')
    a.href       = URL.createObjectURL(blob)
    a.download   = '打卡记录.xlsx'
    a.click()
    URL.revokeObjectURL(a.href)
  } finally { exporting.value = false }
}
</script>

<style scoped>
.filter-card :deep(.el-card__body) { padding: 16px 20px 8px; }
.stat-row { margin-top:12px; font-size:13px; color:#606266; }
.stat-row b { color:#10b981; }
.pagination-row { display:flex; justify-content:flex-end; margin-top:16px; }
</style>
