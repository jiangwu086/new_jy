<template>
  <div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
      description="在此配置本机构专属礼品。用户在小程序中可兑换平台礼品和本机构礼品。" />

    <div class="toolbar">
      <el-button type="primary" @click="openDialog(null)"
                 style="background:#10b981;border-color:#10b981">
        <el-icon><Plus /></el-icon> 新增礼品
      </el-button>
    </div>

    <el-card shadow="never" style="margin-top:16px">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="礼品" min-width="200">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px">
              <el-image v-if="row.imageUrl" :src="row.imageUrl"
                        style="width:40px;height:40px;border-radius:6px" fit="cover"/>
              <div v-else class="img-ph">礼</div>
              <div>
                <div>{{ row.name }}</div>
                <div style="font-size:12px;color:#9ca3af">{{ row.description }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所需积分" width="100" align="center">
          <template #default="{ row }">
            <span style="color:#10b981;font-weight:700">{{ row.pointsCost }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="90" align="center">
          <template #default="{ row }">
            <span :style="row.stock === -1 ? 'color:#9ca3af' : (row.stock < 10 ? 'color:#ef4444' : '')">
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
      <div class="empty-tip" v-if="!loading && list.length === 0">
        暂无礼品，点击右上角「新增礼品」开始配置
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editRow ? '编辑礼品' : '新增礼品'" width="500px">
      <el-form :model="form" label-width="90px" ref="formRef">
        <el-form-item label="礼品名称" prop="name" :rules="[{required:true,message:'必填'}]">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="所需积分" prop="pointsCost" :rules="[{required:true,message:'必填'}]">
          <el-input-number v-model="form.pointsCost" :min="1" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="-1" />
          <span style="margin-left:8px;color:#9ca3af;font-size:12px">-1 = 不限库存</span>
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
        <el-button type="primary" :loading="saving" @click="save"
                   style="background:#10b981;border-color:#10b981">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listGifts, createGift, updateGift, deleteGift } from '@/api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading       = ref(false)
const list          = ref([])
const dialogVisible = ref(false)
const saving        = ref(false)
const editRow       = ref(null)
const formRef       = ref()
const form = reactive({
  name:'', description:'', imageUrl:'', pointsCost:10,
  stock:-1, sortOrder:0, status:1
})

async function load() {
  loading.value = true
  try {
    const res = await listGifts()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  editRow.value = row
  if (row) {
    Object.assign(form, { name:row.name, description:row.description||'',
      imageUrl:row.imageUrl||'', pointsCost:row.pointsCost,
      stock:row.stock??-1, sortOrder:row.sortOrder||0, status:row.status })
  } else {
    Object.assign(form, { name:'', description:'', imageUrl:'', pointsCost:10,
      stock:-1, sortOrder:0, status:1 })
  }
  dialogVisible.value = true
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
.toolbar { display:flex; justify-content:flex-end; }
.img-ph { width:40px; height:40px; border-radius:6px; background:#ecfdf5; color:#10b981; display:flex; align-items:center; justify-content:center; font-weight:600; flex-shrink:0; }
.empty-tip { text-align:center; color:#9ca3af; padding:40px 0; font-size:14px; }
</style>
