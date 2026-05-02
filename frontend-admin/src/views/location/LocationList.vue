<template>
  <div>
    <div class="page-header">
      <h3>打卡点位管理</h3>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增点位</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id"           label="ID"     width="80"  />
        <el-table-column prop="name"         label="点位名称" min-width="160" />
        <el-table-column prop="address"      label="详细地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="districtCode" label="区划代码" width="120" />
        <el-table-column prop="longitude"    label="经度"   width="120" />
        <el-table-column prop="latitude"     label="纬度"   width="120" />
        <el-table-column prop="checkinRadius" label="打卡半径(m)" width="120" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '营业中' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" @click="showQrCode(row)">二维码</el-button>
            <el-popconfirm title="确认关闭该点位？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger">关闭</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 二维码弹窗 -->
    <el-dialog v-model="qrVisible" :title="'点位二维码 — ' + qrLocation.name"
               width="340px" align-center>
      <div style="text-align:center;padding:16px 0">
        <div v-if="qrLoading" style="padding:60px 0;color:#999">生成中...</div>
        <div v-else-if="qrError" style="color:#ef4444;padding:20px">{{ qrError }}</div>
        <img v-else :src="qrUrl" style="width:240px;height:240px;border-radius:8px" />
        <div style="margin-top:12px;color:#666;font-size:13px">
          骑手扫描后直达打卡页
        </div>
      </div>
      <template #footer>
        <el-button type="primary" :disabled="!qrUrl" @click="downloadQr">下载图片</el-button>
        <el-button @click="qrVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑点位' : '新增点位'"
               width="780px" draggable @opened="initMap" @closed="destroyMap">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="点位名称" prop="name">
          <el-input v-model="form.name" placeholder="如：历下区安全教育站" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" placeholder="街道/楼宇名称" />
        </el-form-item>
        <el-form-item label="区划代码" prop="districtCode">
          <el-input v-model="form.districtCode" placeholder="如：370102" />
        </el-form-item>
        <el-form-item label="位置" required>
          <div style="width:100%">
            <div style="display:flex;gap:8px;margin-bottom:8px">
              <el-input v-model="form.longitude" placeholder="经度" readonly style="width:160px" />
              <el-input v-model="form.latitude" placeholder="纬度" readonly style="width:160px" />
              <el-input v-model="searchKeyword" placeholder="搜索地名（按回车）"
                        @keyup.enter="searchLocation" clearable>
                <template #append>
                  <el-button @click="searchLocation">搜索</el-button>
                </template>
              </el-input>
            </div>
            <div ref="mapEl" class="loc-map"></div>
            <div class="map-tip">
              在地图上点击即可选择经纬度，或拖动下方标记微调位置。
              <span v-if="form.longitude && form.latitude" style="color:#999;margin-left:8px">
                已选：{{ form.longitude }}, {{ form.latitude }}
              </span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="打卡半径(m)">
          <el-input-number v-model="form.checkinRadius" :min="50" :max="50000" :step="50" />
          <span style="margin-left:8px;color:#909399;font-size:12px">范围 50 ~ 50000 米</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">营业中</el-radio>
            <el-radio :value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getLocations, createLocation, updateLocation, deleteLocation } from '@/api'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// 修正 Leaflet 默认图标在 Vite 打包后无法加载的问题
import iconUrl       from 'leaflet/dist/images/marker-icon.png'
import iconRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png'
import shadowUrl     from 'leaflet/dist/images/marker-shadow.png'
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconUrl, iconRetinaUrl, shadowUrl })

// ── 二维码 ────────────────────────────────────────────────────
const qrVisible  = ref(false)
const qrLoading  = ref(false)
const qrUrl      = ref('')
const qrError    = ref('')
const qrLocation = ref({ name: '' })

async function showQrCode(row) {
  qrLocation.value = row
  qrVisible.value  = true
  qrLoading.value  = true
  qrUrl.value      = ''
  qrError.value    = ''
  try {
    const token = localStorage.getItem('adminToken')
    const resp  = await fetch(`/api/v1/admin/locations/${row.id}/qrcode`, {
      headers: { Authorization: 'Bearer ' + token }
    })
    if (!resp.ok || resp.headers.get('Content-Type')?.includes('json')) {
      const txt = await resp.text()
      qrError.value = '生成失败：' + txt
    } else {
      const blob = await resp.blob()
      qrUrl.value = URL.createObjectURL(blob)
    }
  } catch (e) {
    qrError.value = e.message
  } finally {
    qrLoading.value = false
  }
}

function downloadQr() {
  const a      = document.createElement('a')
  a.href       = qrUrl.value
  a.download   = `点位_${qrLocation.value.name}_二维码.png`
  a.click()
}

const list    = ref([])
const loading = ref(false)
const saving  = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const defaultForm = () => ({
  id: null, name: '', address: '', districtCode: '',
  longitude: '', latitude: '', checkinRadius: 200, status: 1
})

const form  = ref(defaultForm())
const rules = {
  name:      [{ required: true, message: '请输入点位名称' }],
  longitude: [{ required: true, message: '请输入经度' }],
  latitude:  [{ required: true, message: '请输入纬度' }]
}

async function load() {
  loading.value = true
  try {
    const res = await getLocations()
    list.value = res.data
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  form.value = row ? { ...row } : defaultForm()
  dialogVisible.value = true
  formRef.value?.resetFields()
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await updateLocation(form.value.id, form.value)
    } else {
      await createLocation(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteLocation(id)
  ElMessage.success('已关闭')
  load()
}

// ── 地图选点（Leaflet + OSM）─────────────────────────────────
const mapEl         = ref(null)
const searchKeyword = ref('')
let map      = null
let marker   = null

// 默认中心：天安门，经纬度 [纬, 经]
const DEFAULT_CENTER = [39.9087, 116.3975]

function initMap() {
  if (map) return
  nextTick(() => {
    if (!mapEl.value) return

    // 已有坐标则用，没有就用默认中心
    const lat = parseFloat(form.value.latitude)
    const lng = parseFloat(form.value.longitude)
    const hasCoords = !isNaN(lat) && !isNaN(lng)
    const center = hasCoords ? [lat, lng] : DEFAULT_CENTER
    const zoom   = hasCoords ? 16 : 11

    map = L.map(mapEl.value).setView(center, zoom)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap',
      maxZoom: 19
    }).addTo(map)

    if (hasCoords) {
      marker = L.marker(center, { draggable: true }).addTo(map)
      marker.on('dragend', e => updateCoords(e.target.getLatLng()))
    }

    map.on('click', e => {
      if (!marker) {
        marker = L.marker(e.latlng, { draggable: true }).addTo(map)
        marker.on('dragend', ev => updateCoords(ev.target.getLatLng()))
      } else {
        marker.setLatLng(e.latlng)
      }
      updateCoords(e.latlng)
    })

    // 关键：dialog 弹出时容器尺寸还没稳定，强制让地图重算
    setTimeout(() => map && map.invalidateSize(), 100)
  })
}

function updateCoords({ lat, lng }) {
  // 数据库列是 NUMERIC(10,6)，保留 6 位
  form.value.latitude  = lat.toFixed(6)
  form.value.longitude = lng.toFixed(6)
}

function destroyMap() {
  if (map) { map.remove(); map = null }
  marker = null
  searchKeyword.value = ''
}

// OSM Nominatim 地名搜索（中国大陆精度有限，凑合用）
async function searchLocation() {
  const q = searchKeyword.value.trim()
  if (!q || !map) return
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(q)}`
    const resp = await fetch(url, { headers: { 'Accept-Language': 'zh-CN' } })
    const data = await resp.json()
    if (!data || !data.length) {
      ElMessage.warning('没找到，可以直接在地图上点选')
      return
    }
    const lat = parseFloat(data[0].lat)
    const lng = parseFloat(data[0].lon)
    map.setView([lat, lng], 16)
    if (!marker) {
      marker = L.marker([lat, lng], { draggable: true }).addTo(map)
      marker.on('dragend', e => updateCoords(e.target.getLatLng()))
    } else {
      marker.setLatLng([lat, lng])
    }
    updateCoords({ lat, lng })
  } catch (e) {
    ElMessage.error('搜索失败：' + e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { font-size: 18px; color: #1a2332; }
.loc-map  { width: 100%; height: 320px; border-radius: 6px; border: 1px solid #dcdfe6; }
.map-tip  { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
