<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名/手机号"
          clearable
          prefix-icon="Search"
          style="width:240px"
          @input="filterList"
        />
        <span class="total-tip">共 <b>{{ filtered.length }}</b> 名员工</span>
      </div>

      <el-table :data="filtered" v-loading="loading" stripe style="margin-top:16px">
        <el-table-column prop="id"           label="用户ID"  width="90"  />
        <el-table-column label="员工" width="160">
          <template #default="{ row }">
            <div>{{ row.nick_name || row.nickName || '—' }}</div>
            <div style="font-size:12px;color:#9ca3af">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="实名认证" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.real_name || row.realName ? 'success' : 'warning'" size="small">
              {{ row.real_name || row.realName ? '已认证' : '未认证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计积分" width="100" align="center">
          <template #default="{ row }">
            <span style="color:#10b981;font-weight:600">{{ row.total_points ?? row.totalPoints ?? 0 }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getWorkers } from '@/api/index.js'

const loading = ref(false)
const list    = ref([])
const keyword = ref('')

const filtered = computed(() => {
  if (!keyword.value) return list.value
  const kw = keyword.value.toLowerCase()
  return list.value.filter(w =>
    (w.nick_name  || w.nickName  || '').toLowerCase().includes(kw) ||
    (w.phone      || '').includes(kw) ||
    (w.real_name  || w.realName  || '').includes(kw)
  )
})

function filterList() { /* computed handles this */ }

async function load() {
  loading.value = true
  try {
    const res = await getWorkers()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 16px; }
.total-tip { font-size: 13px; color: #606266; }
.total-tip b { color: #10b981; }
</style>
