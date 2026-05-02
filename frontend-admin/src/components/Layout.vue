<template>
  <el-container class="layout" :class="{ 'dashboard-layout': isDashboard }">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <svg class="logo-svg" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2L4 6v6c0 5.25 3.5 10.15 8 11.35C16.5 22.15 20 17.25 20 12V6l-8-4z"
                fill="#3b82f6" opacity="0.9"/>
          <path d="M9 12l2 2 4-4" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span class="logo-text">安全教育管理</span>
      </div>

      <div class="menu-group-label">数据概览</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1a2332"
        text-color="#a0aec0"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据大屏</span>
        </el-menu-item>

        <div class="menu-group-label">运营管理</div>
        <el-menu-item index="/checkins">
          <el-icon><Checked /></el-icon>
          <span>打卡记录</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/redeem">
          <el-icon><Medal /></el-icon>
          <span>核销 &amp; 兑换</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><Picture /></el-icon>
          <span>打卡审核</span>
        </el-menu-item>
        <el-menu-item index="/verify">
          <el-icon><UserFilled /></el-icon>
          <span>实名审核</span>
        </el-menu-item>

        <div class="menu-group-label">内容与配置</div>
        <el-menu-item index="/articles">
          <el-icon><Document /></el-icon>
          <span>内容文章</span>
        </el-menu-item>
        <el-menu-item index="/orgs">
          <el-icon><OfficeBuilding /></el-icon>
          <span>机构管理</span>
        </el-menu-item>
        <el-menu-item index="/gifts">
          <el-icon><Present /></el-icon>
          <span>礼品配置</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主体 -->
    <el-container class="main-container">
      <el-header v-if="!isDashboard" class="header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <span class="admin-tag">超级管理员</span>
          <span class="admin-name">{{ adminName }}</span>
          <el-button link type="danger" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main-content" :class="{ 'dashboard-main': isDashboard }">
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
const adminName  = computed(() => localStorage.getItem('adminName') || '管理员')
const isDashboard = computed(() => route.path.startsWith('/dashboard'))

const titleMap = {
  dashboard: '数据大屏',
  checkins:  '打卡记录',
  users:     '用户管理',
  redeem:    '核销 & 兑换',
  articles:  '内容文章管理',
  orgs:      '机构管理',
  gifts:     '礼品配置',
  review:    '打卡审核',
  verify:    '实名审核',
  config:    '系统配置',
}
const pageTitle = computed(() => {
  const seg = route.path.split('/')[1]
  return titleMap[seg] || '管理后台'
})

function logout() {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminName')
  router.replace('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.dashboard-layout { background: #030917; }

.sidebar {
  background: #1a2332;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
}

.logo-svg { width: 28px; height: 28px; flex-shrink: 0; }

.menu-group-label {
  font-size: 11px;
  color: #4a5568;
  text-transform: uppercase;
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
  background: rgba(59,130,246,0.25) !important;
  color: #fff !important;
}
.el-menu :deep(.el-menu-item:hover) {
  background: rgba(255,255,255,0.07) !important;
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
.page-title { font-size: 16px; font-weight: 600; color: #1a2332; }

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-tag {
  background: #eff6ff;
  color: #3b82f6;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 20px;
  border: 1px solid #bfdbfe;
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

.dashboard-main {
  height: 100vh;
  padding: 0;
  background: #030917;
  overflow: hidden;
}

:global(:fullscreen .sidebar) {
  display: none !important;
}

:global(:fullscreen .main-container) {
  width: 100vw;
}
</style>
