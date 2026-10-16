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
    <el-aside width="220px" class="admin-aside">
      <div class="brand">
        <div class="mark">A</div>
        <div class="brand-text">
          <strong>Admin Console</strong>
          <span>用户 · 角色 · 权限 · 日志</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item v-for="item in navs" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-title">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDesc }}</p>
        </div>
        <div class="header-actions">
          <el-tag type="info" effect="plain">ROLE_ADMIN</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-trigger">
              <el-icon><UserFilled /></el-icon>
              {{ admin.username || '未登录' }}
              <el-icon><ArrowDown /></el-icon>
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
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
}

.mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4f46e5, #0ea5e9);
  color: #fff;
  font-weight: 700;
}

.brand-text strong {
  display: block;
  font-size: 14px;
}

.brand-text span {
  color: #909399;
  font-size: 12px;
}

.admin-menu {
  border-right: none;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color);
}

.header-title h1 {
  margin: 0;
  font-size: 18px;
}

.header-title p {
  margin: 2px 0 0;
  color: #909399;
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  outline: none;
}

.admin-main {
  background: var(--el-bg-color-page);
}
</style>
