<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

interface MenuItem {
  path: string
  label: string
  icon: string
  /** 需要的权限码，拥有任意一个即可见 */
  perms?: string[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 控制台导航：配置了 perms 的菜单只对拥有对应权限码的账号展示 */
const consoleMenus: MenuItem[] = [
  { path: '/', label: '总览', icon: 'HomeFilled' },
  { path: '/users', label: '用户管理', icon: 'User', perms: ['user:list'] }
]

const menus = computed(() =>
  consoleMenus.filter((item) => !item.perms || item.perms.some((code) => auth.hasPermission(code)))
)

const isPlainPage = computed(() => Boolean(route.meta.public || route.meta.plain))
const pageTitle = computed(() => String(route.meta.title ?? '首页'))
const pageDesc = computed(() => String(route.meta.desc ?? ''))

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    await router.replace({ path: '/login' })
    return
  }
  if (command === 'admin') {
    await router.push('/admin/users')
  }
}
</script>

<template>
  <RouterView v-if="isPlainPage" />

  <el-container v-else class="layout">
    <el-aside width="236px" class="aside">
      <div class="brand">
        <div class="logo">C</div>
        <div class="brand-text">
          <strong>Cloud Console</strong>
          <span>Spring Cloud + Vue 3</span>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="menu"
        background-color="transparent"
        text-color="#c8cedd"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>

        <!-- 仅管理员可见：进入后台管理端（后台需要单独登录） -->
        <el-menu-item v-if="auth.isAdmin" index="/admin/users">
          <el-icon><Setting /></el-icon>
          <span>进入后台管理</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-foot">
        <el-icon color="#34d399"><CircleCheckFilled /></el-icon>
        <span>网关 :8080 · 认证 :9000 · 用户 :8081</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header" height="auto">
        <div class="header-title">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDesc }}</p>
        </div>

        <div class="header-right">
          <el-tag v-for="role in auth.roles" :key="role" type="primary" effect="plain" size="small">
            {{ role }}
          </el-tag>

          <el-dropdown @command="handleCommand">
            <span class="user">
              <el-icon><Avatar /></el-icon>
              <span>{{ auth.username || '未登录' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="auth.isAdmin" command="admin">后台管理</el-dropdown-item>
                <el-dropdown-item command="logout" :divided="auth.isAdmin">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  display: flex;
  flex-direction: column;
  padding: 20px 12px 16px;
  background: #171e2e;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 6px 20px;
}

.logo {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.brand-text strong {
  color: #fff;
  font-size: 15px;
}

.brand-text span {
  color: #7f8aa3;
  font-size: 12px;
}

.menu {
  flex: 1;
  border-right: none;
}

.menu :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 2px;
  border-radius: 9px;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(99, 102, 241, 0.28);
  font-weight: 600;
}

.aside-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 6px 0;
  color: #7f8aa3;
  font-size: 12px;
  line-height: 1.5;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.header-title h1 {
  font-size: 19px;
  font-weight: 650;
}

.header-title p {
  color: var(--text-muted);
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  outline: none;
}

.main {
  padding: 20px 24px 32px;
  background: var(--bg);
}
</style>
