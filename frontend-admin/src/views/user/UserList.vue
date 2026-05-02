<template>
  <div>
    <div class="page-header">
      <h3>用户管理</h3>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-form inline :model="query">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="手机号/姓名" clearable
                    style="width:240px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id"          label="ID"       width="90"  />
        <el-table-column prop="phone"       label="手机号"   width="140" />
        <el-table-column prop="nickName"    label="昵称"     width="130" />
        <el-table-column prop="realName"    label="真实姓名" width="120" />
        <el-table-column prop="totalPoints" label="当前积分" width="110" />
        <el-table-column prop="createTime"  label="注册时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewCheckins(row)">打卡记录</el-button>
            <el-button link type="warning" @click="openAdjust(row)">调整积分</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end"
        @change="load"
      />
    </el-card>

    <!-- 打卡记录抽屉 -->
    <el-drawer v-model="drawerVisible" title="打卡记录" size="720px">
      <el-empty v-if="!checkins.length" description="暂无打卡记录" />
      <el-table v-else :data="checkins" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="打卡点" min-width="200">
          <template #default="{ row }">
            <div>{{ row.location_name || `点位 #${row.location_id}` }}</div>
            <div style="font-size:12px;color:#9ca3af" v-if="row.location_address">{{ row.location_address }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="period" label="周期" width="100" align="center"/>
        <el-table-column label="积分" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">+{{ row.points_earned }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="打卡时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.checkin_time) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 调整积分弹窗 -->
    <el-dialog v-model="adjustVisible" title="调整用户积分" width="400px">
      <el-form :model="adjustForm" label-width="80px">
        <el-form-item label="用户">
          <span>{{ currentUser?.phone }} （当前 {{ currentUser?.totalPoints }} 分）</span>
        </el-form-item>
        <el-form-item label="调整量">
          <el-input-number v-model="adjustForm.delta" :step="1" style="width:180px" />
          <span style="margin-left:8px;color:#909399">（正数增加，负数扣减）</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, getUserCheckins, adjustPoints } from '@/api'

const list    = ref([])
const total   = ref(0)
const loading = ref(false)
const saving  = ref(false)
const query   = reactive({ page: 1, size: 10, keyword: '' })

const drawerVisible = ref(false)
const checkins      = ref([])

const adjustVisible = ref(false)
const currentUser   = ref(null)
const adjustForm    = reactive({ delta: 0, remark: '' })

async function load() {
  loading.value = true
  try {
    const res   = await getUsers(query)
    list.value  = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function viewCheckins(row) {
  const res    = await getUserCheckins(row.id)
  checkins.value = res.data || []
  drawerVisible.value = true
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

function openAdjust(row) {
  currentUser.value   = row
  adjustForm.delta    = 0
  adjustForm.remark   = ''
  adjustVisible.value = true
}

async function handleAdjust() {
  saving.value = true
  try {
    await adjustPoints(currentUser.value.id, adjustForm)
    ElMessage.success('积分已调整')
    adjustVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { font-size: 18px; color: #1a2332; }
.filter-bar .el-form { margin-bottom: -18px; }
</style>
