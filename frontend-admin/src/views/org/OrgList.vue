<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog(null)">
        <el-icon><Plus /></el-icon> 新增机构
      </el-button>
    </div>

    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id"           label="ID"    width="70" />
        <el-table-column prop="name"         label="机构名称"  min-width="180" />
        <el-table-column prop="code"         label="机构编号"  width="140" />
        <el-table-column prop="contactName"  label="联系人"   width="100" />
        <el-table-column prop="contactPhone" label="联系电话"  width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary"  @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning"  @click="openLocations(row)">管理地点</el-button>
            <el-button link type="success"  @click="openAdminDialog(row)">创建管理员</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑机构 -->
    <el-dialog v-model="dialogVisible" :title="editRow ? '编辑机构' : '新增机构'" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef">
        <el-form-item label="机构名称" prop="name" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="机构编号" prop="code" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="form.code" placeholder="如 MT-JN-001" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="机构地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="禁用"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 创建机构管理员 -->
    <el-dialog v-model="adminDialogVisible" title="创建机构管理员" width="440px">
      <el-alert type="info" :closable="false" style="margin-bottom:16px"
                :description="`为「${currentOrg?.name}」创建一个独立的机构端登录账号`" show-icon />
      <el-form :model="adminForm" label-width="90px" ref="adminFormRef">
        <el-form-item label="登录账号" prop="username" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="adminForm.username" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="adminForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="adminForm.realName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adminDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="adminSaving" @click="createOrgAdmin">创建</el-button>
      </template>
    </el-dialog>

    <!-- 地点管理抽屉 -->
    <el-drawer
      v-model="locDrawerVisible"
      :title="`教育地点 — ${currentOrg?.name}`"
      size="840px"
      direction="rtl"
      destroy-on-close
    >
      <div style="display:flex;justify-content:flex-end;margin-bottom:16px">
        <el-button type="primary" :icon="Plus" @click="openLocDialog()">新增地点</el-button>
      </div>

      <el-table :data="locList" v-loading="locLoading" border stripe size="small">
        <el-table-column prop="id"            label="ID"     width="64" />
        <el-table-column prop="name"          label="地点名称" min-width="150" />
        <el-table-column prop="address"       label="详细地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="districtCode"  label="区划"   width="100" />
        <el-table-column label="坐标" width="160">
          <template #default="{ row }">
            <span v-if="row.longitude && row.latitude" style="font-size:12px;color:#666">
              {{ Number(row.longitude).toFixed(4) }},{{ Number(row.latitude).toFixed(4) }}
            </span>
            <el-tag v-else type="warning" size="small">未设置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkinRadius" label="半径(m)" width="80" align="center"/>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'danger'" size="small">
              {{ row.status===1?'开放':'关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="165" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLocDialog(row)">编辑</el-button>
            <el-button link type="success" @click="showQrCode(row)">二维码</el-button>
            <el-popconfirm title="确认关闭该地点？" @confirm="handleLocDelete(row.id)">
              <template #reference>
                <el-button link type="danger">关闭</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!locLoading && locList.length===0"
                description="该机构暂无教育地点，点击「新增地点」添加"
                style="margin-top:40px"/>
    </el-drawer>

    <!-- 二维码弹窗 -->
    <el-dialog v-model="qrVisible" :title="'点位二维码 — '+qrLocation.name"
               width="340px" align-center>
      <div style="text-align:center;padding:16px 0">
        <div v-if="qrLoading" style="padding:60px 0;color:#999">生成中...</div>
        <div v-else-if="qrError" style="color:#ef4444;padding:20px">{{ qrError }}</div>
        <img v-else :src="qrUrl" style="width:240px;height:240px;border-radius:8px"/>
        <div style="margin-top:12px;color:#666;font-size:13px">骑手扫描后直达打卡页</div>
      </div>
      <template #footer>
        <el-button type="primary" :disabled="!qrUrl" @click="downloadQr">下载图片</el-button>
        <el-button @click="qrVisible=false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑地点弹窗 -->
    <el-dialog v-model="locDialogVisible" :title="locForm.id?'编辑地点':'新增地点'"
               width="600px" draggable :before-close="closeLocDialog">
      <el-form ref="locFormRef" :model="locForm" :rules="locRules" label-width="110px">
        <el-form-item label="地点名称" prop="name">
          <el-input v-model="locForm.name" placeholder="如：历下区安全教育站" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="locForm.address" placeholder="街道/楼宇名称" />
        </el-form-item>
        <el-form-item label="区划代码">
          <el-input v-model="locForm.districtCode" placeholder="如：370102" />
        </el-form-item>

        <!-- 地图选点 -->
        <el-form-item label="经纬度坐标">
          <div style="width:100%">
            <div style="display:flex;gap:8px;margin-bottom:8px">
              <el-input v-model="locForm.longitude" placeholder="经度（点击地图自动填入）" style="flex:1" readonly>
                <template #prefix><span style="font-size:11px;color:#9ca3af">经</span></template>
              </el-input>
              <el-input v-model="locForm.latitude" placeholder="纬度（点击地图自动填入）" style="flex:1" readonly>
                <template #prefix><span style="font-size:11px;color:#9ca3af">纬</span></template>
              </el-input>
              <el-button v-if="locForm.longitude" type="danger" plain size="small"
                         @click="clearCoords">清除</el-button>
            </div>
            <!-- 地图搜索 -->
            <div style="display:flex;gap:8px;margin-bottom:8px">
              <el-input v-model="mapSearch" placeholder="输入地址搜索定位（如：济南市历下区）"
                        style="flex:1" @keyup.enter="doSearch" />
              <el-button @click="doSearch" :loading="searching">搜索</el-button>
            </div>
            <!-- Leaflet 地图容器 -->
            <div ref="mapEl" class="map-container"></div>
            <div style="font-size:12px;color:#9ca3af;margin-top:4px">
              点击地图任意位置即可设置坐标；坐标为空时不影响保存
            </div>
          </div>
        </el-form-item>

        <el-form-item label="打卡半径(m)">
          <el-input-number v-model="locForm.checkinRadius" :min="50" :max="2000" :step="50" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="locForm.status">
            <el-radio :value="1">开放</el-radio>
            <el-radio :value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeLocDialog">取消</el-button>
        <el-button type="primary" :loading="locSaving" @click="handleLocSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import {
  listOrgs, createOrg, updateOrg, createOrgAdmin as apiCreateOrgAdmin,
  getLocations, createLocation, updateLocation, deleteLocation
} from '@/api/index.js'
import { ElMessage } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// ── 修复 Leaflet 默认图标路径（Vite 打包后图标 URL 会失效） ──
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon   from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl:       markerIcon,
  shadowUrl:     markerShadow,
})

// ── 机构列表 ──────────────────────────────────────────────────
const loading = ref(false)
const list    = ref([])
const dialogVisible = ref(false)
const saving  = ref(false)
const editRow = ref(null)
const formRef = ref()
const form    = reactive({ name:'', code:'', contactName:'', contactPhone:'', address:'', status:1 })

async function load() {
  loading.value = true
  try { list.value = (await listOrgs()).data || [] }
  finally { loading.value = false }
}

function openDialog(row) {
  editRow.value = row
  Object.assign(form, row
    ? { name:row.name, code:row.code, contactName:row.contactName||'',
        contactPhone:row.contactPhone||'', address:row.address||'', status:row.status }
    : { name:'', code:'', contactName:'', contactPhone:'', address:'', status:1 })
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    editRow.value ? await updateOrg(editRow.value.id, form) : await createOrg(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally { saving.value = false }
}

// ── 机构管理员 ────────────────────────────────────────────────
const adminDialogVisible = ref(false)
const adminSaving   = ref(false)
const adminFormRef  = ref()
const adminForm     = reactive({ username:'', password:'', realName:'' })
const currentOrg    = ref(null)

function openAdminDialog(org) {
  currentOrg.value = org
  Object.assign(adminForm, { username:'', password:'', realName:'' })
  adminDialogVisible.value = true
}

async function createOrgAdmin() {
  await adminFormRef.value.validate()
  adminSaving.value = true
  try {
    await apiCreateOrgAdmin(currentOrg.value.id, adminForm)
    ElMessage.success('账号创建成功，可用机构端登录')
    adminDialogVisible.value = false
  } finally { adminSaving.value = false }
}

// ── 地点管理抽屉 ──────────────────────────────────────────────
const locDrawerVisible = ref(false)
const locList    = ref([])
const locLoading = ref(false)

function openLocations(org) {
  currentOrg.value = org
  locDrawerVisible.value = true
  loadLocations()
}

async function loadLocations() {
  locLoading.value = true
  try { locList.value = (await getLocations(currentOrg.value.id)).data || [] }
  finally { locLoading.value = false }
}

// ── 地点新增/编辑（含地图选点） ───────────────────────────────
const locDialogVisible = ref(false)
const locSaving  = ref(false)
const locFormRef = ref()
const mapEl      = ref(null)
const mapSearch  = ref('')
const searching  = ref(false)

let mapInstance = null
let mapMarker   = null

const defaultLoc = () => ({
  id: null, name: '', address: '', districtCode: '',
  longitude: '', latitude: '', checkinRadius: 200, status: 1
})
const locForm = ref(defaultLoc())
const locRules = {
  name: [{ required: true, message: '请输入地点名称' }],
}

function openLocDialog(row) {
  locForm.value = row
    ? { ...row, longitude: row.longitude ?? '', latitude: row.latitude ?? '' }
    : defaultLoc()
  mapSearch.value = ''
  locDialogVisible.value = true
  // 等 DOM 渲染完再初始化地图
  nextTick(() => { initMap() })
}

function closeLocDialog() {
  destroyMap()
  locDialogVisible.value = false
}

function initMap() {
  if (!mapEl.value) return
  destroyMap()

  // 默认中心：济南（或已有坐标）
  const lat = locForm.value.latitude  ? parseFloat(locForm.value.latitude)  : 36.6512
  const lng = locForm.value.longitude ? parseFloat(locForm.value.longitude) : 117.1201

  mapInstance = L.map(mapEl.value, { zoomControl: true }).setView([lat, lng], 14)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap contributors',
    maxZoom: 19,
  }).addTo(mapInstance)

  // 如果已有坐标，先打一个标记
  if (locForm.value.latitude && locForm.value.longitude) {
    mapMarker = L.marker([lat, lng]).addTo(mapInstance)
  }

  // 点击地图设置坐标
  mapInstance.on('click', (e) => {
    const { lat, lng } = e.latlng
    locForm.value.latitude  = lat.toFixed(6)
    locForm.value.longitude = lng.toFixed(6)
    if (mapMarker) mapMarker.remove()
    mapMarker = L.marker([lat, lng]).addTo(mapInstance)
  })

  // 地图容器可能因 dialog 动画未完成导致尺寸不对，延迟 invalidateSize
  setTimeout(() => mapInstance?.invalidateSize(), 300)
}

function destroyMap() {
  mapMarker?.remove()
  mapMarker = null
  mapInstance?.remove()
  mapInstance = null
}

function clearCoords() {
  locForm.value.longitude = ''
  locForm.value.latitude  = ''
  mapMarker?.remove()
  mapMarker = null
}

async function doSearch() {
  if (!mapSearch.value.trim()) return
  searching.value = true
  try {
    const resp = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(mapSearch.value)}&limit=1`,
      { headers: { 'Accept-Language': 'zh-CN,zh' } }
    )
    const data = await resp.json()
    if (!data.length) { ElMessage.warning('未找到该地址，请换一个关键词'); return }
    const { lat, lon } = data[0]
    mapInstance?.setView([parseFloat(lat), parseFloat(lon)], 16)
  } catch {
    ElMessage.error('地址搜索失败，请手动点击地图选点')
  } finally {
    searching.value = false
  }
}

async function handleLocSave() {
  await locFormRef.value.validate()
  locSaving.value = true
  try {
    const payload = { ...locForm.value, orgId: currentOrg.value.id }
    // 空字符串转 null，避免后端 BigDecimal 转换报错
    payload.longitude = payload.longitude !== '' ? parseFloat(payload.longitude) : null
    payload.latitude  = payload.latitude  !== '' ? parseFloat(payload.latitude)  : null
    payload.id
      ? await updateLocation(payload.id, payload)
      : await createLocation(payload)
    ElMessage.success('保存成功')
    closeLocDialog()
    loadLocations()
  } finally { locSaving.value = false }
}

async function handleLocDelete(id) {
  await deleteLocation(id)
  ElMessage.success('已关闭')
  loadLocations()
}

// ── 二维码 ────────────────────────────────────────────────────
const qrVisible  = ref(false)
const qrLoading  = ref(false)
const qrUrl      = ref('')
const qrError    = ref('')
const qrLocation = ref({ name:'' })

async function showQrCode(row) {
  qrLocation.value = row
  qrVisible.value  = true
  qrLoading.value  = true
  qrUrl.value = ''
  qrError.value = ''
  try {
    const token = localStorage.getItem('adminToken')
    const resp  = await fetch(`/api/v1/admin/locations/${row.id}/qrcode`,
                              { headers: { Authorization: 'Bearer ' + token } })
    if (!resp.ok || resp.headers.get('Content-Type')?.includes('json')) {
      qrError.value = '生成失败：' + await resp.text()
    } else {
      qrUrl.value = URL.createObjectURL(await resp.blob())
    }
  } catch (e) { qrError.value = e.message }
  finally { qrLoading.value = false }
}

function downloadQr() {
  const a = document.createElement('a')
  a.href = qrUrl.value
  a.download = `地点_${qrLocation.value.name}_二维码.png`
  a.click()
}

onMounted(load)
onBeforeUnmount(destroyMap)
</script>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; }
.map-container {
  width: 100%;
  height: 300px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  z-index: 0;
}
/* 确保 Leaflet 弹窗 z-index 不被 Element Plus dialog 遮住 */
:deep(.leaflet-pane) { z-index: 4 !important; }
:deep(.leaflet-top),
:deep(.leaflet-bottom) { z-index: 5 !important; }
</style>
