<template>
  <div class="login-page">
    <div class="login-card">
      <div class="title">
        <span class="icon">🛡</span>
        <h2>管理后台登录</h2>
        <p>新就业形态劳动者安全警示服务</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名"
                    prefix-icon="User" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码"
                    prefix-icon="Lock" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api'

const router  = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码',   trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    localStorage.setItem('adminToken', res.data.token)
    localStorage.setItem('adminName',  res.data.realName || res.data.username)
    ElMessage.success('登录成功')
    router.replace('/')
  } catch (e) {
    // error handled in request interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a2332 0%, #2d4a7a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px 40px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}

.title {
  text-align: center;
  margin-bottom: 36px;
}

.title .icon { font-size: 40px; }

.title h2 {
  font-size: 22px;
  color: #1a2332;
  margin: 12px 0 6px;
}

.title p { color: #909399; font-size: 13px; }

.submit-btn { width: 100%; margin-top: 8px; height: 48px; font-size: 16px; letter-spacing: 2px; }
</style>
