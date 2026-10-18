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
  { path: '/users', label: '用户管理', icon: 'User', perms: ['user:list'] },
  { path: '/chat', label: '聊天室', icon: 'ChatDotRound' },
  { path: '/knowledge', label: '知识库', icon: 'Collection' },
  { path: '/ocr', label: '图片识别', icon: 'Picture' }
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
    <el-aside width="248px" class="aside">
      <div class="brand">
        <div class="logo">C</div>
        <div class="brand-text">
          <strong>Cloud Console</strong>
          <span>Spring Cloud + Vue 3</span>
        </div>
      </div>

      <p class="nav-label">导航</p>

      <el-menu :default-active="route.path" router class="menu">
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
        <span class="pulse"></span>
        <div class="foot-text">
          <strong>服务已注册</strong>
          <span>网关 :8080 · 认证 :9000 · 用户 :8081</span>
        </div>
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

          <span class="header-divider"></span>

          <el-dropdown @command="handleCommand">
            <span class="user">
              <span class="avatar">{{ (auth.username || '?').slice(0, 1).toUpperCase() }}</span>
              <span class="user-name">{{ auth.username || '未登录' }}</span>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
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
        <div class="main-inner">
          <RouterView />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

/* 左侧导航 */
.aside {
  display: flex;
  flex-direction: column;
  padding: 18px 14px 16px;
  background: var(--sidebar);
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 6px 18px;
  border-bottom: 1px solid rgb(255 255 255 / 7%);
}

.logo {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.brand-text strong {
  color: var(--sidebar-text-strong);
  font-size: 14px;
  font-weight: 600;
}

.brand-text span {
  color: var(--sidebar-muted);
  font-size: 12px;
}

/* 导航分组标题 */
.nav-label {
  padding: 18px 10px 8px;
  color: var(--sidebar-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.menu {
  flex: 1;
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item) {
  height: 40px;
  padding-left: 10px !important;
  margin-bottom: 2px;
  border-radius: var(--r-md);
  color: var(--sidebar-text);
  font-size: 13px;
  font-weight: 500;
  transition: background 0.15s ease, color 0.15s ease;
}

.menu :deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 16px;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-menu-item:focus) {
  background: var(--sidebar-hover);
  color: var(--sidebar-text-strong);
}

.menu :deep(.el-menu-item.is-active) {
  position: relative;
  background: var(--sidebar-active);
  color: var(--sidebar-text-strong);
  font-weight: 600;
}

/* 激活项左侧的强调竖条 */
.menu :deep(.el-menu-item.is-active)::before {
  position: absolute;
  top: 9px;
  bottom: 9px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: #8b8cf7;
  content: '';
}

/* 侧栏底部状态 */
.aside-foot {
  display: flex;
  gap: 9px;
  padding: 12px 10px 0;
  border-top: 1px solid rgb(255 255 255 / 7%);
}

.pulse {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  margin-top: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px rgb(52 211 153 / 18%);
}

.foot-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.45;
}

.foot-text strong {
  color: var(--sidebar-text);
  font-size: 12px;
  font-weight: 600;
}

.foot-text span {
  color: var(--sidebar-muted);
  font-size: 11px;
}

/* 顶部信息条 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.header-title h1 {
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.01em;
  line-height: 1.3;
}

.header-title p {
  margin-top: 1px;
  color: var(--text-muted);
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-divider {
  width: 1px;
  height: 20px;
  margin: 0 4px;
  background: var(--border);
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 999px;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  outline: none;
  transition: background 0.15s ease;
}

.user:hover {
  background: var(--surface-hover);
}

.avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}

.user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-caret {
  color: var(--text-faint);
  font-size: 12px;
}

/* 内容区 */
.main {
  padding: 20px 24px 32px;
  background: var(--bg);
  overflow: auto;
}

.main-inner {
  max-width: 1320px;
  height: 100%;
  margin: 0 auto;
}

/* 聊天室、知识库等需要撑满高度的页面在窄屏下仍可正常收缩 */
@media (max-width: 900px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .main {
    padding: 16px;
  }
}
</style>
