<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <svg class="logo-svg" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"
                fill="#10b981" opacity="0.9"/>
          <path d="M9 22V12h6v10" stroke="#fff" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
        <div class="logo-text-wrap">
          <span class="logo-title">机构管理端</span>
          <span class="logo-org">{{ orgName }}</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="#0f1f2e"
        text-color="#a0aec0"
        active-text-color="#ffffff"
      >
        <div class="menu-group-label">数据概览</div>
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据大屏</span>
        </el-menu-item>

        <div class="menu-group-label">业务管理</div>
        <el-menu-item index="/locations">
          <el-icon><Location /></el-icon>
          <span>我的点位</span>
        </el-menu-item>
        <el-menu-item index="/checkins">
          <el-icon><Checked /></el-icon>
          <span>打卡记录</span>
        </el-menu-item>
        <el-menu-item index="/workers">
          <el-icon><User /></el-icon>
          <span>员工列表</span>
        </el-menu-item>
        <el-menu-item index="/gifts">
          <el-icon><Present /></el-icon>
          <span>礼品配置</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><Picture /></el-icon>
          <span>打卡审核</span>
        </el-menu-item>

        <div class="menu-group-label">机构设置</div>
        <el-menu-item index="/checkin-config">
          <el-icon><Setting /></el-icon>
          <span>打卡规则</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-container">
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <span class="role-tag">机构管理员</span>
          <span class="admin-name">{{ adminName }}</span>
          <el-button link type="danger" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route  = useRoute()
const router = useRouter()

const activeMenu = computed(() => '/' + route.path.split('/')[1])
const adminName  = computed(() => localStorage.getItem('orgAdminName') || '管理员')
const orgName    = computed(() => localStorage.getItem('orgName') || '')

const titleMap = {
  dashboard:        '数据大屏',
  locations:        '我的点位',
  checkins:         '打卡记录',
  workers:          '员工列表',
  gifts:            '礼品配置',
  review:           '打卡审核',
  'checkin-config': '打卡规则',
}
const pageTitle = computed(() => titleMap[route.path.split('/')[1]] || '机构管理端')

function logout() {
  localStorage.removeItem('orgToken')
  localStorage.removeItem('orgAdminName')
  localStorage.removeItem('orgName')
  router.replace('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }

.sidebar {
  background: #0f1f2e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
}

.logo-svg { width: 32px; height: 32px; flex-shrink: 0; }

.logo-text-wrap { display: flex; flex-direction: column; }
.logo-title { font-size: 14px; font-weight: 700; color: #fff; }
.logo-org { font-size: 11px; color: #64748b; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 140px; }

.menu-group-label {
  font-size: 11px;
  color: #334155;
  letter-spacing: 1px;
  padding: 14px 20px 4px;
  font-weight: 600;
}

.el-menu { border-right: none; flex: 1; overflow-y: auto; }
.el-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  border-radius: 8px;
  margin: 2px 10px;
  width: calc(100% - 20px);
}
.el-menu :deep(.el-menu-item.is-active) {
  background: rgba(16,185,129,0.2) !important;
  color: #fff !important;
}
.el-menu :deep(.el-menu-item:hover) {
  background: rgba(255,255,255,0.06) !important;
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
}

.header-left { display: flex; align-items: center; }
.page-title { font-size: 16px; font-weight: 600; color: #0f1f2e; }

.header-right { display: flex; align-items: center; gap: 12px; }

.role-tag {
  background: #f0fdf4;
  color: #16a34a;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 20px;
  border: 1px solid #bbf7d0;
}

.admin-name { color: #606266; font-size: 14px; }

.main-container { flex: 1; overflow: hidden; }
.main-content {
  background: #f4f5f7;
  padding: 24px;
  overflow-y: auto;
  height: calc(100vh - 56px);
  box-sizing: border-box;
}
</style>
