<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="48" height="48" rx="12" fill="#ecfdf5"/>
          <path d="M8 20l16-12 16 12v18a3 3 0 01-3 3H11a3 3 0 01-3-3V20z" fill="#10b981" opacity="0.85"/>
          <path d="M18 38V28h12v10" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
      </div>
      <div class="login-title">机构管理端</div>
      <div class="login-sub">新就业形态劳动者安全教育服务</div>

      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="submit">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="机构管理员账号"
            size="large"
            prefix-icon="User"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          @click="submit"
          class="login-btn"
          style="background:#10b981;border-color:#10b981"
        >
          登录
        </el-button>
      </el-form>

      <div class="login-hint">
        平台管理员请使用
        <a href="/admin" target="_blank">管理后台</a>
        登录
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/index.js'
import { ElMessage } from 'element-plus'

const router  = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    localStorage.setItem('orgToken',     res.data.token)
    localStorage.setItem('orgAdminName', res.data.realName || form.username)
    localStorage.setItem('orgName',      res.data.orgName  || '')
    localStorage.setItem('orgId',        res.data.orgId    || '')
    ElMessage.success('登录成功')
    router.replace('/')
  } catch {
    // 错误已由 request.js 拦截弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f1f2e 0%, #134e4a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px 36px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  text-align: center;
}

.login-logo svg { width: 64px; height: 64px; margin-bottom: 16px; }

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #0f1f2e;
  margin-bottom: 6px;
}

.login-sub {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 32px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
}

.login-hint {
  margin-top: 24px;
  font-size: 12px;
  color: #9ca3af;
}
.login-hint a { color: #10b981; text-decoration: none; }
.login-hint a:hover { text-decoration: underline; }
</style>
