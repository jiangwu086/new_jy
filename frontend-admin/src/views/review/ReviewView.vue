<template>
  <div>
    <div class="page-header">
      <h3>打卡照片审核</h3>
      <el-radio-group v-model="reviewStatus" @change="search">
        <el-radio-button :value="0">待审核 <el-badge :value="pendingCount" :max="99" type="danger" /></el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
      </el-radio-group>
    </div>

    <el-card shadow="never" v-loading="loading">
      <!-- 空状态 -->
      <el-empty v-if="!loading && list.length === 0"
                :description="reviewStatus === 0 ? '暂无待审核记录' : '暂无记录'" />

      <!-- 卡片网格 -->
      <div class="review-grid" v-else>
        <div class="review-card" v-for="item in list" :key="item.id">
          <!-- 照片（点击图片本身即可预览，el-image 自带预览功能） -->
          <div class="photo-wrap">
            <el-image
              v-if="item.photoUrl"
              :src="item.photoUrl"
              fit="cover"
              class="photo-img"
              :preview-src-list="[item.photoUrl]"
              hide-on-click-modal
            >
              <template #error>
                <div class="photo-placeholder">照片加载失败</div>
              </template>
              <template #placeholder>
                <div class="photo-placeholder">加载中…</div>
              </template>
            </el-image>
            <div v-else class="photo-placeholder">无照片</div>
            <div class="status-tag" :class="statusClass(item.reviewStatus)">
              {{ statusLabel(item.reviewStatus) }}
            </div>
          </div>

          <!-- 信息 -->
          <div class="card-body">
            <div class="info-row">
              <span class="label">姓名</span>
              <span>{{ item.userName || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="label">手机号</span>
              <span>{{ item.userPhone }}</span>
            </div>
            <div class="info-row">
              <span class="label">打卡时间</span>
              <span>{{ formatTime(item.checkinTime) }}</span>
            </div>
            <div class="info-row">
              <span class="label">获得积分</span>
              <span style="color:#f59e0b;font-weight:600">+{{ item.pointsEarned }}</span>
            </div>
            <div class="info-row" v-if="item.reviewRemark">
              <span class="label">备注</span>
              <span style="color:#ef4444">{{ item.reviewRemark }}</span>
            </div>
          </div>

          <!-- 操作（仅待审核） -->
          <div class="card-actions" v-if="item.reviewStatus === 0">
            <el-button type="success" size="small" :loading="item._approving"
                       @click="doReview(item, 'approve')">通过</el-button>
            <el-button type="danger" size="small" :loading="item._rejecting"
                       @click="openReject(item)">驳回</el-button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="total > size">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="400px">
      <el-input v-model="rejectRemark" type="textarea" :rows="3"
                placeholder="请填写驳回原因（将通知用户）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPendingReviews, reviewCheckin } from '@/api'

const list         = ref([])
const loading      = ref(false)
const page         = ref(1)
const size         = ref(12)
const total        = ref(0)
const pendingCount = ref(0)
const reviewStatus = ref(0)

const rejectVisible = ref(false)
const rejectRemark  = ref('')
const rejectTarget  = ref(null)
const rejecting     = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getPendingReviews({ reviewStatus: reviewStatus.value, page: page.value, size: size.value })
    list.value  = (res.data.list || []).map(i => ({ ...i, _approving: false, _rejecting: false }))
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  const res = await getPendingReviews({ reviewStatus: 0, page: 1, size: 1 })
  pendingCount.value = res.data.total || 0
}

function search() { page.value = 1; load() }

async function doReview(item, action, remark = '') {
  if (action === 'approve') item._approving = true
  else item._rejecting = true
  try {
    await reviewCheckin(item.id, { action, remark })
    ElMessage.success(action === 'approve' ? '已通过' : '已驳回')
    load()
    loadPendingCount()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    item._approving = false
    item._rejecting = false
  }
}

function openReject(item) {
  rejectTarget.value = item
  rejectRemark.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectRemark.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejecting.value = true
  try {
    await doReview(rejectTarget.value, 'reject', rejectRemark.value)
    rejectVisible.value = false
  } finally {
    rejecting.value = false
  }
}

function statusLabel(s) {
  return s === 0 ? '待审核' : s === 1 ? '已通过' : '已驳回'
}
function statusClass(s) {
  return s === 0 ? 'pending' : s === 1 ? 'approved' : 'rejected'
}
function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

onMounted(() => { load(); loadPendingCount() })
</script>

<style scoped>
.page-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.page-header h3 { font-size: 18px; color: #1a2332; }

.review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.review-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow .2s;
}
.review-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }

.photo-wrap {
  position: relative; height: 180px; background: #f5f7fa; cursor: pointer;
}
.photo-img { width: 100%; height: 100%; }
.photo-placeholder {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: #ccc; font-size: 14px;
}

.status-tag {
  position: absolute; top: 8px; right: 8px;
  padding: 2px 10px; border-radius: 20px; font-size: 12px; font-weight: 600;
}
.status-tag.pending  { background: #fff7ed; color: #f59e0b; }
.status-tag.approved { background: #f0fdf4; color: #16a34a; }
.status-tag.rejected { background: #fef2f2; color: #ef4444; }

.card-body { padding: 12px; }
.info-row  { display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 13px; }
.label     { color: #9ca3af; }

.card-actions {
  display: flex; gap: 8px; padding: 0 12px 12px;
}
.card-actions .el-button { flex: 1; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
