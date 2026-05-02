<template>
  <div>
    <div class="toolbar">
      <span class="tips">平台礼品全用户可见；机构礼品仅对应机构用户可见</span>
      <el-button type="primary" @click="openDialog(null)">
        <el-icon><Plus /></el-icon> 新增礼品
      </el-button>
    </div>

    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="礼品" min-width="200">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px">
              <el-image v-if="row.imageUrl" :src="row.imageUrl"
                        style="width:40px;height:40px;border-radius:6px;flex-shrink:0"
                        fit="cover" :preview-src-list="[row.imageUrl]" preview-teleported/>
              <div v-else class="img-placeholder">礼</div>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="130">
          <template #default="{ row }">
            <el-tag v-if="!row.orgId" type="warning" size="small">平台通用</el-tag>
            <el-tag v-else type="info" size="small">机构 #{{ row.orgId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所需积分" width="100" align="center">
          <template #default="{ row }">
            <span style="color:#3b82f6;font-weight:600">{{ row.pointsCost }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="90" align="center">
          <template #default="{ row }">
            <span :style="row.stock === -1 ? 'color:#9ca3af' : ''">
              {{ row.stock === -1 ? '不限' : row.stock }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger"  @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editRow ? '编辑礼品' : '新增礼品'" width="520px">
      <el-form :model="form" label-width="90px" ref="formRef">
        <el-form-item label="礼品名称" prop="name" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <!-- 礼品图片上传 -->
        <el-form-item label="礼品图片">
          <div class="upload-area">
            <!-- 已有图片预览 -->
            <div v-if="form.imageUrl" class="img-preview-box">
              <el-image :src="form.imageUrl"
                        style="width:80px;height:80px;border-radius:8px"
                        fit="cover" :preview-src-list="[form.imageUrl]" preview-teleported/>
              <el-button link type="danger" size="small" class="img-remove-btn"
                         @click="form.imageUrl=''">删除</el-button>
            </div>
            <!-- 上传按钮 -->
            <el-upload v-else
              :show-file-list="false"
              accept="image/*"
              :before-upload="beforeUpload"
              :http-request="doUpload"
              :disabled="uploading"
            >
              <div class="upload-trigger">
                <el-icon v-if="!uploading" :size="24" style="color:#9ca3af"><Plus /></el-icon>
                <el-icon v-else class="spin" :size="24" style="color:#3b82f6">
                  <Loading />
                </el-icon>
                <span style="font-size:12px;color:#9ca3af;margin-top:4px">
                  {{ uploading ? '上传中...' : '点击上传' }}
                </span>
              </div>
            </el-upload>
          </div>
          <div style="font-size:12px;color:#9ca3af;margin-top:4px">支持 JPG/PNG，建议尺寸 400×400</div>
        </el-form-item>

        <el-form-item label="所需积分" prop="pointsCost" :rules="[{required:true,message:'必填'}]">
          <el-input-number v-model="form.pointsCost" :min="1" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="-1" />
          <span style="margin-left:8px;color:#9ca3af;font-size:12px">-1 = 不限库存</span>
        </el-form-item>
        <el-form-item label="归属机构">
          <el-select v-model="form.orgId" placeholder="不选 = 平台通用" clearable style="width:220px">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
          <span style="margin-left:8px;color:#9ca3af;font-size:12px">值越大越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                     active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus, Loading } from '@element-plus/icons-vue'
import { listGifts, createGift, updateGift, deleteGift, listOrgs } from '@/api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const loading   = ref(false)
const list      = ref([])
const orgs      = ref([])
const uploading = ref(false)
const dialogVisible = ref(false)
const saving    = ref(false)
const editRow   = ref(null)
const formRef   = ref()

const form = reactive({
  name:'', description:'', imageUrl:'', pointsCost:10,
  stock:-1, orgId:null, sortOrder:0, status:1
})

async function load() {
  loading.value = true
  try {
    const [giftRes, orgRes] = await Promise.all([listGifts(), listOrgs()])
    list.value = giftRes.data  || []
    orgs.value = orgRes.data   || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  editRow.value = row
  if (row) {
    Object.assign(form, {
      name: row.name, description: row.description||'', imageUrl: row.imageUrl||'',
      pointsCost: row.pointsCost, stock: row.stock ?? -1,
      orgId: row.orgId||null, sortOrder: row.sortOrder||0, status: row.status
    })
  } else {
    Object.assign(form, { name:'', description:'', imageUrl:'', pointsCost:10,
      stock:-1, orgId:null, sortOrder:0, status:1 })
  }
  dialogVisible.value = true
}

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M  = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt5M)  { ElMessage.error('图片大小不能超过 5MB'); return false }
  return true
}

async function doUpload({ file }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const token = localStorage.getItem('adminToken')
    const res = await axios.post('/api/v1/admin/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data', Authorization: 'Bearer ' + token }
    })
    if (res.data?.code === 0) {
      form.imageUrl = res.data.data.url
      ElMessage.success('图片上传成功')
    } else {
      ElMessage.error(res.data?.msg || '上传失败')
    }
  } catch (e) {
    ElMessage.error('上传失败：' + (e.message || '网络错误'))
  } finally {
    uploading.value = false
  }
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editRow.value) { await updateGift(editRow.value.id, form) }
    else               { await createGift(form) }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  await ElMessageBox.confirm('确认删除此礼品？', '提示', { type: 'warning' })
  await deleteGift(id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.tips { font-size:13px; color:#9ca3af; }
.img-placeholder {
  width:40px; height:40px; border-radius:6px;
  background:#eff6ff; color:#3b82f6;
  display:flex; align-items:center; justify-content:center;
  font-size:14px; font-weight:600; flex-shrink:0;
}
.upload-area {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.upload-trigger {
  width: 80px; height: 80px;
  border: 1.5px dashed #dcdfe6;
  border-radius: 8px;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.upload-trigger:hover { border-color: #3b82f6; }
.img-preview-box {
  position: relative;
  display: inline-block;
}
.img-remove-btn {
  position: absolute;
  top: -8px; right: -8px;
  background: #fff;
  border-radius: 50%;
  padding: 0;
  line-height: 1;
}
@keyframes spin { to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
