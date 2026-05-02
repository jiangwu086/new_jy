<template>
  <div>
    <div class="page-header">
      <h3>系统配置</h3>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="list" border stripe>
        <el-table-column prop="configKey"   label="配置键"   width="260" />
        <el-table-column prop="description" label="说明"     min-width="200" />
        <el-table-column label="当前值" width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.configValue"
              size="small"
              style="width:120px"
              @blur="handleUpdate(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfigs, updateConfig } from '@/api'

const list    = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getConfigs()
    list.value = res.data
  } finally {
    loading.value = false
  }
}

async function handleUpdate(row) {
  await updateConfig(row.configKey, row.configValue)
  ElMessage.success(`已更新 ${row.configKey}`)
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { font-size: 18px; color: #1a2332; }
</style>
