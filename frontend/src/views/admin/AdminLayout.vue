<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const navs = [
  { path: '/admin/users', label: '用户与状态', icon: 'User' },
  { path: '/admin/roles', label: '角色与权限', icon: 'Avatar' },
  { path: '/admin/permissions', label: '权限清单', icon: 'Key' },
  { path: '/admin/online', label: '在线用户', icon: 'Monitor' },
  { path: '/admin/logs', label: '日志审计', icon: 'Document' }
]

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => String(route.meta.title ?? '后台管理'))
const pageDesc = computed(() => String(route.meta.desc ?? ''))

onMounted(() => {
  admin.loadProfile().catch(() => undefined)
})

async function handleLogout() {
  await admin.logout()
  await router.replace({ path: '/admin/login' })
}

function handleCommand(command: string) {
  if (command === 'logout') {
    handleLogout()
  } else if (command === 'console') {
    router.push('/')
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="248px" class="admin-aside">
      <div class="brand">
        <div class="mark">A</div>
        <div class="brand-text">
          <strong>Admin Console</strong>
          <span>后台管理</span>
        </div>
      </div>

      <p class="nav-label">管理</p>

      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item v-for="item in navs" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <footer class="aside-foot">
        <span class="pulse" />
        <span>仅 ROLE_ADMIN 可访问</span>
      </footer>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-title">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDesc }}</p>
        </div>
        <div class="header-actions">
          <el-tag type="info" effect="plain" size="small">ROLE_ADMIN</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-trigger">
              <span class="avatar">{{ (admin.username || 'A').charAt(0).toUpperCase() }}</span>
              <span class="user-name">{{ admin.username || '未登录' }}</span>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="console">返回前台控制台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出后台</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-main">
        <div class="main-inner">
          <RouterView />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

/* 与前台控制台共用同一套深色导航与冷调画布 */
.admin-aside {
  display: flex;
  flex-direction: column;
  background: var(--sidebar);
  border-right: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 18px 18px 16px;
}

.mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.brand-text strong {
  display: block;
  color: var(--sidebar-text-strong);
  font-size: 14px;
  letter-spacing: 0.2px;
}

.brand-text span {
  color: var(--sidebar-muted);
  font-size: 11.5px;
}

.nav-label {
  margin: 6px 0 4px;
  padding: 0 20px;
  color: var(--sidebar-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.6px;
}

.admin-menu {
  flex: 1;
  padding: 0 10px;
  border-right: none;
  background: transparent;
}

.admin-menu :deep(.el-menu-item) {
  height: 40px;
  margin-bottom: 2px;
  padding-left: 14px !important;
  border-radius: var(--r-md);
  color: var(--sidebar-text);
  font-size: 13px;
  font-weight: 500;
}

.admin-menu :deep(.el-menu-item:hover) {
  background: var(--sidebar-hover);
  color: var(--sidebar-text-strong);
}

.admin-menu :deep(.el-menu-item.is-active) {
  position: relative;
  background: var(--sidebar-active);
  color: var(--sidebar-text-strong);
  font-weight: 600;
}

.admin-menu :deep(.el-menu-item.is-active)::before {
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: #818cf8;
  content: '';
}

.aside-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid rgb(255 255 255 / 8%);
  color: var(--sidebar-muted);
  font-size: 11.5px;
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #34d399;
  box-shadow: 0 0 0 4px rgb(52 211 153 / 16%);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: auto;
  padding: 14px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.header-title h1 {
  margin: 0;
  color: var(--text);
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.2px;
}

.header-title p {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  color: var(--text-regular);
  cursor: pointer;
  outline: none;
  transition: background-color 0.15s ease;
}

.user-trigger:hover {
  background: var(--surface-hover);
}

.avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 650;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
}

.user-caret {
  color: var(--text-faint);
  font-size: 12px;
}

.admin-main {
  padding: 20px 24px 32px;
  background: var(--bg);
  overflow: auto;
}

.main-inner {
  height: 100%;
  max-width: 1320px;
  margin: 0 auto;
}

@media (max-width: 900px) {
  .admin-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .admin-main {
    padding: 16px;
  }
}
</style>
