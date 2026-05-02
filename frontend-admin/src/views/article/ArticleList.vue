<template>
  <div>
    <div class="page-header">
      <h3>内容文章管理</h3>
      <el-button type="primary" :icon="Plus" @click="openDialog()">发布文章</el-button>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-bar">
      <el-form inline :model="query">
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable placeholder="全部" style="width:140px">
            <el-option label="政策说明" :value="1" />
            <el-option label="理赔指南" :value="2" />
            <el-option label="警示案例" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="文章标题" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id"    label="ID"   width="80"  />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="type"  label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="districtCode" label="区划" width="110">
          <template #default="{ row }">
            <span v-if="!row.districtCode || row.districtCode === ''" style="color:#9ca3af">全域</span>
            <span v-else>{{ row.districtCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder"    label="排序" width="80"  />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:flex-end"
        @change="load"
      />
    </el-card>

    <!-- 发布/编辑文章弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑文章' : '发布文章'"
               width="860px" draggable :before-close="onDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" style="width:100%">
                <el-option label="政策说明" :value="1" />
                <el-option label="理赔指南" :value="2" />
                <el-option label="警示案例" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="区划代码">
              <el-input v-model="form.districtCode" placeholder="留空 = 全域可见" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="排序权重">
              <el-input-number v-model="form.sortOrder" :min="0" style="width:100%"/>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">发布</el-radio>
                <el-radio :value="0">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 富文本编辑器 -->
        <el-form-item label="正文" prop="content">
          <div class="editor-wrap" :class="{'editor-error': contentError}">
            <Toolbar :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" class="editor-toolbar"/>
            <Editor
              v-model="form.content"
              :defaultConfig="editorConfig"
              mode="default"
              class="editor-body"
              @onCreated="handleEditorCreated"
            />
          </div>
          <div v-if="contentError" class="editor-error-tip">请输入正文内容</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="onDialogClose">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { getArticles, createArticle, updateArticle, deleteArticle } from '@/api'

// 编辑器实例（shallowRef 避免 Vue 深度响应式代理导致的性能问题）
const editorRef   = shallowRef()
const contentError = ref(false)

const toolbarConfig = {}
const editorConfig  = {
  placeholder: '请输入正文内容（支持图文、粗体、列表等富文本格式）',
  MENU_CONF: {}
}

function handleEditorCreated(editor) {
  editorRef.value = editor
}

// 组件卸载时销毁编辑器
onBeforeUnmount(() => {
  editorRef.value?.destroy()
})

const list    = ref([])
const total   = ref(0)
const loading = ref(false)
const saving  = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const query = reactive({ page: 1, size: 10, type: null, keyword: '' })

const defaultForm = () => ({
  id: null, title: '', type: 1, districtCode: '', content: '', sortOrder: 0, status: 1
})
const form  = ref(defaultForm())
const rules = {
  title: [{ required: true, message: '请输入标题' }],
  type:  [{ required: true, message: '请选择类型' }],
}

const typeLabel = t => ({ 1: '政策说明', 2: '理赔指南', 3: '警示案例' }[t] || '-')
const typeTag   = t => ({ 1: 'primary', 2: 'warning', 3: 'danger' }[t] || '')

async function load() {
  loading.value = true
  try {
    const res = await getArticles(query)
    list.value  = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  form.value = row ? { ...row } : defaultForm()
  contentError.value = false
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function onDialogClose() {
  dialogVisible.value = false
}

async function handleSave() {
  // 手动验证富文本内容
  const htmlContent = form.value.content
  const plainText   = htmlContent?.replace(/<[^>]*>/g, '').trim() || ''
  if (!plainText) {
    contentError.value = true
    ElMessage.warning('请输入正文内容')
    return
  }
  contentError.value = false

  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) await updateArticle(form.value.id, form.value)
    else               await createArticle(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteArticle(id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h3 { font-size: 18px; color: #1a2332; }
.filter-bar { margin-bottom: 0; }
.filter-bar .el-form { margin-bottom: -18px; }

.editor-wrap {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  z-index: 10;
}
.editor-wrap.editor-error {
  border-color: #f56c6c;
}
.editor-toolbar {
  border-bottom: 1px solid #eee;
}
.editor-body {
  height: 320px;
  overflow-y: auto;
}
.editor-error-tip {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
</style>
