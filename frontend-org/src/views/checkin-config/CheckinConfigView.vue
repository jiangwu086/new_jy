<template>
  <div>
    <el-alert class="tip" type="info" show-icon :closable="false">
      <template #title>
        每个字段：留空 = 沿用平台默认值；填值 = 仅本机构生效。
        修改后立即对本机构所有打卡点生效。
      </template>
    </el-alert>

    <el-card v-loading="loading" shadow="never">
      <el-form :model="form" label-width="180px" style="max-width:680px">
        <el-form-item label="单次打卡积分">
          <el-input v-model="form.points_per_checkin" placeholder="如 10" clearable
                    style="width:200px">
            <template #append>分</template>
          </el-input>
          <span class="default-hint" v-if="!form.points_per_checkin">
            未填，当前生效：平台默认 {{ defaults.points_per_checkin }} 分
          </span>
        </el-form-item>

        <el-form-item label="周期内打卡次数上限">
          <el-input v-model="form.max_checkins_per_period" placeholder="如 5" clearable
                    style="width:200px">
            <template #append>次</template>
          </el-input>
          <span class="default-hint" v-if="!form.max_checkins_per_period">
            未填，当前生效：平台默认 {{ defaults.max_checkins_per_period }} 次
          </span>
        </el-form-item>

        <el-form-item label="周期类型">
          <el-radio-group v-model="form.current_period_type">
            <el-radio value="">沿用平台默认（{{ defaults.current_period_type }}）</el-radio>
            <el-radio value="MONTH">按月</el-radio>
            <el-radio value="QUARTER">按季度</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="">
          <el-button type="primary" :loading="saving" @click="save"
                     style="background:#10b981;border-color:#10b981">保存</el-button>
          <el-button @click="resetAll">全部恢复平台默认</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <h4 style="margin:0 0 12px;color:#1a2332">当前生效配置</h4>
      <el-table :data="effective" border size="small" style="max-width:680px">
        <el-table-column prop="key"      label="配置项"   min-width="180" />
        <el-table-column prop="orgValue" label="机构值"   width="120">
          <template #default="{ row }">
            <span v-if="row.orgValue">{{ row.orgValue }}</span>
            <span v-else style="color:#9ca3af">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="globalValue" label="平台默认" width="120" />
        <el-table-column prop="effective"   label="实际生效" width="120">
          <template #default="{ row }">
            <el-tag :type="row.orgValue ? 'success' : 'info'" size="small">
              {{ row.effective }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCheckinConfig, updateCheckinConfig } from '@/api/index.js'

const loading = ref(false)
const saving  = ref(false)
const effective = ref([])

const form = reactive({
  points_per_checkin: '',
  max_checkins_per_period: '',
  current_period_type: ''
})
const defaults = reactive({
  points_per_checkin: '10',
  max_checkins_per_period: '5',
  current_period_type: 'MONTH'
})

async function load() {
  loading.value = true
  try {
    const res = await getCheckinConfig()
    const list = res.data || []
    effective.value = list
    list.forEach(r => {
      if (r.key in form)     form[r.key]     = r.orgValue || ''
      if (r.key in defaults) defaults[r.key] = r.globalValue || defaults[r.key]
    })
  } finally { loading.value = false }
}

async function save() {
  saving.value = true
  try {
    // 直接发送 form 即可：空字符串等于「清空，回归平台默认」
    await updateCheckinConfig({ ...form })
    ElMessage.success('保存成功')
    load()
  } finally { saving.value = false }
}

function resetAll() {
  form.points_per_checkin      = ''
  form.max_checkins_per_period = ''
  form.current_period_type     = ''
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 16px; }
.default-hint { margin-left: 12px; color: #909399; font-size: 12px; }
</style>
