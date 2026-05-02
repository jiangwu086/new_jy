<template>
  <div class="page">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" @change="load">
        <el-radio-button :value="1">待审核</el-radio-button>
        <el-radio-button :value="2">已通过</el-radio-button>
        <el-radio-button :value="3">已拒绝</el-radio-button>
        <el-radio-button :value="0">全部</el-radio-button>
      </el-radio-group>
      <el-button @click="load" :icon="Refresh">刷新</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="list" v-loading="loading" stripe border style="width:100%">
      <el-table-column prop="userId"   label="用户ID"  width="90"/>
      <el-table-column prop="realName" label="姓名"    width="100"/>
      <el-table-column prop="idCard"   label="身份证"  width="200"/>
      <el-table-column prop="phone"    label="手机号"  width="140"/>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.verifyStatus)" size="small">
            {{ statusLabel(row.verifyStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="verifyRejectReason" label="拒绝原因" min-width="160" show-overflow-tooltip/>
      <el-table-column prop="updateTime" label="提交时间" width="170"/>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.verifyStatus === 1">
            <el-button size="small" type="success" @click="approve(row)">通过</el-button>
            <el-button size="small" type="danger"  @click="openReject(row)">拒绝</el-button>
          </template>
          <span v-else class="done-text">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top:20px; justify-content:flex-end"
      @current-change="load"
    />

    <!-- 拒绝弹窗 -->
    <el-dialog v-model="rejectVisible" title="填写拒绝原因" width="420px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请填写拒绝原因，将展示给用户（例如：身份证号码与姓名不符）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="rejectLoading">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1/admin' })
api.interceptors.request.use(cfg => {
  cfg.headers.Authorization = 'Bearer ' + localStorage.getItem('adminToken')
  return cfg
})

const list          = ref([])
const loading       = ref(false)
const total         = ref(0)
const page          = ref(1)
const size          = ref(20)
const statusFilter  = ref(1)

const rejectVisible = ref(false)
const rejectReason  = ref('')
const rejectLoading = ref(false)
const currentRow    = ref(null)

const statusLabel = s => ({ 0:'未提交', 1:'审核中', 2:'已通过', 3:'已拒绝' }[s] ?? '-')
const statusType  = s => ({ 1:'warning', 2:'success', 3:'danger' }[s] ?? 'info')

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/verifications', {
      params: { status: statusFilter.value, page: page.value, size: size.value }
    })
    list.value  = data.data.list
    total.value = data.data.total
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过【${row.realName}】的实名认证？`, '审核通过', { type: 'success' })
  await api.post(`/verifications/${row.userId}/approve`)
  ElMessage.success('已通过')
  load()
}

function openReject(row) {
  currentRow.value  = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  rejectLoading.value = true
  try {
    await api.post(`/verifications/${currentRow.value.userId}/reject`, {
      reason: rejectReason.value.trim()
    })
    ElMessage.success('已拒绝')
    rejectVisible.value = false
    load()
  } finally {
    rejectLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { padding: 0; }
.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.done-text { color: #999; font-size: 13px; }
</style>
