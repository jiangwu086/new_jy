<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openDialog(null)"
                 style="background:#10b981;border-color:#10b981">
        <el-icon><Plus /></el-icon> 新增点位
      </el-button>
    </div>

    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id"      label="ID"   width="70" />
        <el-table-column prop="name"    label="点位名称" min-width="160" />
        <el-table-column prop="address" label="地址"   min-width="200" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '营业中' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkinRadius" label="打卡半径(m)" width="110" align="center" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger"  @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editRow ? '编辑点位' : '新增点位'"
               width="780px" @opened="initMap" @closed="destroyMap">
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="点位名称" prop="name" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="位置" required>
          <div style="width:100%">
            <div style="display:flex;gap:8px;margin-bottom:8px">
              <el-input v-model="form.longitude" placeholder="经度" readonly style="width:160px" />
              <el-input v-model="form.latitude"  placeholder="纬度" readonly style="width:160px" />
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
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                     active-text="营业中" inactive-text="关闭" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save"
                   style="background:#10b981;border-color:#10b981">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { getLocations, createLocation, updateLocation, deleteLocation } from '@/api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// 修正 Leaflet 默认图标在 Vite 打包后无法加载的问题
import iconUrl       from 'leaflet/dist/images/marker-icon.png'
import iconRetinaUrl from 'leaflet/dist/images/marker-icon-2x.png'
import shadowUrl     from 'leaflet/dist/images/marker-shadow.png'
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconUrl, iconRetinaUrl, shadowUrl })

const loading       = ref(false)
const list          = ref([])
const dialogVisible = ref(false)
const saving        = ref(false)
const editRow       = ref(null)
const formRef       = ref()
const mapEl         = ref(null)
const searchKeyword = ref('')
let map    = null
let marker = null

const form = reactive({
  id: null, name: '', address: '', longitude: '', latitude: '',
  checkinRadius: 200, status: 1, districtCode: ''
})

// 默认中心：天安门
const DEFAULT_CENTER = [39.9087, 116.3975]

async function load() {
  loading.value = true
  try {
    const res = await getLocations()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  editRow.value = row
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      address: row.address || '',
      longitude: row.longitude != null ? String(row.longitude) : '',
      latitude:  row.latitude  != null ? String(row.latitude)  : '',
      checkinRadius: row.checkinRadius || 200,
      status: row.status,
      districtCode: row.districtCode || ''
    })
  } else {
    Object.assign(form, {
      id: null, name:'', address:'', longitude:'', latitude:'',
      checkinRadius: 200, status: 1, districtCode: ''
    })
  }
  dialogVisible.value = true
}

async function save() {
  await formRef.value.validate()
  if (!form.longitude || !form.latitude) {
    ElMessage.warning('请在地图上选择位置')
    return
  }
  saving.value = true
  try {
    // 拷贝一份纯对象，避免传递 reactive proxy 时 axios 处理偶发问题
    const payload = { ...form,
      longitude: parseFloat(form.longitude),
      latitude:  parseFloat(form.latitude) }
    if (editRow.value) {
      await updateLocation(editRow.value.id, payload)
    } else {
      await createLocation(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()              // 等列表重新加载完再 reset saving
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  await ElMessageBox.confirm('确认删除该点位？', '提示', { type: 'warning' })
  await deleteLocation(id)
  ElMessage.success('已删除')
  load()
}

// ── 地图选点（Leaflet + OSM）─────────────────────────────────
function initMap() {
  if (map) return
  nextTick(() => {
    if (!mapEl.value) return

    const lat = parseFloat(form.latitude)
    const lng = parseFloat(form.longitude)
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

    // dialog 弹出时容器尺寸还没稳定，强制重算
    setTimeout(() => map && map.invalidateSize(), 100)
  })
}

function updateCoords({ lat, lng }) {
  // 数据库列是 NUMERIC(10,6)，保留 6 位
  form.latitude  = lat.toFixed(6)
  form.longitude = lng.toFixed(6)
}

function destroyMap() {
  if (map) { map.remove(); map = null }
  marker = null
  searchKeyword.value = ''
}

// OSM Nominatim 地名搜索
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
.toolbar  { display: flex; justify-content: flex-end; }
.loc-map  { width: 100%; height: 320px; border-radius: 6px; border: 1px solid #dcdfe6; }
.map-tip  { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
