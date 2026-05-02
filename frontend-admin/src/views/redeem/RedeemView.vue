<template>
  <div>
    <div class="page-header">
      <h3>积分核销 &amp; 兑换记录</h3>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- ── Tab 1: 积分核销 ─────────────────────────────── -->
      <el-tab-pane label="积分核销" name="redeem">
        <el-row :gutter="20">
          <el-col :span="10">
            <el-card header="手动核销" shadow="never">
              <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
                <el-form-item label="用户手机号" prop="phone">
                  <el-input v-model="form.phone" placeholder="输入后按回车查询"
                            @keyup.enter="queryUser" />
                </el-form-item>
                <template v-if="targetUser">
                  <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
                    <el-descriptions-item label="用户ID">{{ targetUser.id }}</el-descriptions-item>
                    <el-descriptions-item label="昵称">{{ targetUser.nickName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="当前积分">
                      <strong style="color:#e6a23c">{{ targetUser.totalPoints }} 分</strong>
                    </el-descriptions-item>
                  </el-descriptions>
                </template>
                <el-form-item label="点位" prop="locationId">
                  <el-select v-model="form.locationId" placeholder="选择核销点位" style="width:100%">
                    <el-option v-for="loc in locations" :key="loc.id" :label="loc.name" :value="loc.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="扣除积分" prop="points">
                  <el-input-number v-model="form.points" :min="1" style="width:100%" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="form.remark" placeholder="兑换物品描述（可选）" />
                </el-form-item>
                <el-button type="success" :loading="saving" style="width:100%"
                           @click="handleRedeem">确认核销</el-button>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="14">
            <el-card header="近期核销记录" shadow="never">
              <el-table :data="offlineRecords" border stripe size="small">
                <el-table-column prop="id"         label="ID"     width="70"  />
                <el-table-column prop="userId"     label="用户ID" width="90"  />
                <el-table-column prop="locationId" label="点位ID" width="90"  />
                <el-table-column prop="pointsUsed" label="积分"   width="80"  />
                <el-table-column prop="remark"     label="备注"   min-width="120" show-overflow-tooltip />
                <el-table-column prop="createTime" label="时间"   width="160" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ── Tab 2: 兑换记录查询 ─────────────────────────── -->
      <el-tab-pane label="兑换记录查询" name="records">
        <el-card shadow="never" style="margin-bottom:16px">
          <el-form :inline="true" :model="filter" @submit.prevent="searchRedeems">
            <el-form-item label="用户ID">
              <el-input v-model="filter.userId" placeholder="用户ID" clearable style="width:120px" />
            </el-form-item>
            <el-form-item label="礼品关键词">
              <el-input v-model="filter.keyword" placeholder="礼品名称" clearable style="width:160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchRedeems">查询</el-button>
              <el-button @click="resetFilter">重置</el-button>
              <el-button type="success" :loading="exportingRedeems" @click="doExportRedeems">导出 Excel</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <el-table :data="redeemsList" border stripe v-loading="loadingRedeems">
            <el-table-column prop="id"         label="ID"       width="80"  />
            <el-table-column prop="userId"     label="用户ID"   width="90"  />
            <el-table-column prop="userPhone"  label="手机号"   width="130" />
            <el-table-column prop="userName"   label="姓名"     width="100" />
            <el-table-column prop="giftName"   label="礼品名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="pointsUsed" label="消耗积分" width="100">
              <template #default="{ row }">
                <el-tag type="warning">{{ row.pointsUsed }} 分</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark"     label="备注"     min-width="120" show-overflow-tooltip />
            <el-table-column prop="createTime" label="兑换时间" width="170">
              <template #default="{ row }">
                {{ row.createTime?.replace('T', ' ')?.substring(0, 16) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="redeemPage"
              v-model:page-size="redeemSize"
              :page-sizes="[10, 20, 50]"
              :total="redeemTotal"
              layout="total, sizes, prev, pager, next"
              @current-change="loadRedeems"
              @size-change="searchRedeems"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLocations, getUsers, doRedeem, getRedeemLogs, getAdminRedeems } from '@/api'

const activeTab  = ref('redeem')
const locations  = ref([])

// ── Tab1: 积分核销 ─────────────────────────────────────────────
const offlineRecords = ref([])
const saving     = ref(false)
const formRef    = ref()
const targetUser = ref(null)
const form = reactive({ phone: '', locationId: null, points: 10, remark: '' })
const rules = {
  phone:      [{ required: true, message: '请输入手机号' }],
  locationId: [{ required: true, message: '请选择点位'  }],
  points:     [{ required: true, message: '请输入积分'  }]
}

async function queryUser() {
  if (!form.phone) return
  const res = await getUsers({ keyword: form.phone, page: 1, size: 1 })
  const users = res.data.records
  if (!users?.length) { ElMessage.warning('未找到该手机号用户'); targetUser.value = null }
  else { targetUser.value = users[0] }
}

async function handleRedeem() {
  await formRef.value.validate()
  if (!targetUser.value) { ElMessage.warning('请先查询用户'); return }
  saving.value = true
  try {
    await doRedeem({ userId: targetUser.value.id, locationId: form.locationId, points: form.points, remark: form.remark })
    ElMessage.success('核销成功')
    targetUser.value.totalPoints -= form.points
    Object.assign(form, { phone: '', points: 10, remark: '' })
    targetUser.value = null
    loadOfflineRecords()
  } finally { saving.value = false }
}

async function loadOfflineRecords() {
  const res = await getRedeemLogs({ limit: 20 })
  offlineRecords.value = res.data
}

// ── Tab2: 兑换记录查询 ─────────────────────────────────────────
const redeemsList    = ref([])
const redeemPage     = ref(1)
const redeemSize     = ref(20)
const redeemTotal    = ref(0)
const loadingRedeems = ref(false)
const filter = reactive({ userId: '', keyword: '' })

async function loadRedeems(page = redeemPage.value) {
  loadingRedeems.value = true
  try {
    const res = await getAdminRedeems({
      userId:  filter.userId  || undefined,
      keyword: filter.keyword || undefined,
      page,
      size: redeemSize.value
    })
    redeemsList.value = res.data.list  || []
    redeemTotal.value = res.data.total || 0
    redeemPage.value  = page
  } finally { loadingRedeems.value = false }
}

function searchRedeems() { loadRedeems(1) }
function resetFilter()   { filter.userId = ''; filter.keyword = ''; loadRedeems(1) }

onMounted(async () => {
  const [locRes] = await Promise.all([getLocations()])
  locations.value = locRes.data
  loadOfflineRecords()
  loadRedeems(1)
})

const exportingRedeems = ref(false)
async function doExportRedeems() {
  exportingRedeems.value = true
  try {
    const token  = localStorage.getItem('adminToken')
    const params = new URLSearchParams({
      ...(filter.userId  ? { userId:  filter.userId  } : {}),
      ...(filter.keyword ? { keyword: filter.keyword } : {}),
    }).toString()
    const url  = `/api/v1/admin/redeems/export${params ? '?' + params : ''}`
    const resp = await fetch(url, { headers: { Authorization: 'Bearer ' + token } })
    const blob = await resp.blob()
    const a    = document.createElement('a')
    a.href     = URL.createObjectURL(blob)
    a.download = '兑换记录.xlsx'
    a.click()
    URL.revokeObjectURL(a.href)
  } finally { exportingRedeems.value = false }
}
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { font-size: 18px; color: #1a2332; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
