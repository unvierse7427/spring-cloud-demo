import { createRouter, createWebHistory } from 'vue-router'

import { useAdminStore } from '@/stores/admin'
import { useAuthStore } from '@/stores/auth'
import { getAccessToken, getAdminAccessToken } from '@/utils/token'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    desc?: string
    /** 无需登录即可访问 */
    public?: boolean
    /** 不套用控制台外壳（登录页、后台管理端） */
    plain?: boolean
    /** 访问该路由所需的权限码，拥有任意一个即可 */
    perms?: string[]
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true }
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { title: '无访问权限', desc: '当前账号未被授予该页面的权限' }
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '总览', desc: '微服务架构与当前登录身份' }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/views/UserListView.vue'),
      meta: { title: '用户管理', desc: '通过网关调用 cloud-user-service 接口', perms: ['user:list'] }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { title: '聊天室', desc: '基于 Netty WebSocket 的实时群聊' }
    },
    {
      path: '/knowledge',
      name: 'knowledge',
      component: () => import('@/views/KnowledgeView.vue'),
      meta: { title: '知识库', desc: '上传文档构建知识库，检索增强问答并查看引用来源' }
    },
    {
      path: '/ocr',
      name: 'ocr',
      component: () => import('@/views/OcrView.vue'),
      meta: { title: '图片识别', desc: '上传图片提取文字，图片不会被保存' }
    },
    {
      // 后台管理端有独立的登录入口与独立令牌，与前台登录互不影响
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: { title: '后台登录', public: true, plain: true }
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      redirect: '/admin/users',
      meta: { plain: true },
      children: [
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/AdminUserView.vue'),
          meta: { title: '用户与状态', desc: '用户列表、启用禁用与角色分配' }
        },
        {
          path: 'roles',
          name: 'admin-roles',
          component: () => import('@/views/admin/AdminRoleView.vue'),
          meta: { title: '角色与权限', desc: '角色维护与角色权限分配' }
        },
        {
          path: 'permissions',
          name: 'admin-permissions',
          component: () => import('@/views/admin/AdminPermissionView.vue'),
          meta: { title: '权限清单', desc: '系统内置权限码一览' }
        },
        {
          path: 'online',
          name: 'admin-online',
          component: () => import('@/views/admin/AdminOnlineView.vue'),
          meta: { title: '在线用户', desc: '来自 Redis 的用户在线状态' }
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/AdminLogView.vue'),
          meta: { title: '日志审计', desc: '登录日志与接口调用日志' }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  // 后台管理端使用独立令牌，必须单独登录且必须是管理员
  if (to.path.startsWith('/admin')) {
    const admin = useAdminStore()
    const adminLoggedIn = Boolean(getAdminAccessToken())
    if (to.path === '/admin/login') {
      return adminLoggedIn ? { path: '/admin/users' } : true
    }
    if (!adminLoggedIn) {
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
    if (!admin.profile) {
      try {
        await admin.loadProfile()
      } catch {
        return { path: '/admin/login' }
      }
    }
    return true
  }

  const auth = useAuthStore()
  const loggedIn = Boolean(getAccessToken())

  if (to.meta.public) {
    return loggedIn && to.path === '/login' ? { path: '/' } : true
  }
  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 权限判断依赖 /auth/me 返回的 authorities，刷新后需要先补一次身份
  if (!auth.profile) {
    try {
      await auth.loadProfile()
    } catch {
      auth.clear()
      return { path: '/login' }
    }
  }

  const perms = to.meta.perms
  if (perms && perms.length > 0 && !perms.some((code) => auth.hasPermission(code))) {
    return { path: '/403' }
  }
  return true
})

router.afterEach((to) => {
  const site = to.path.startsWith('/admin') ? '后台管理' : 'Cloud Console'
  document.title = `${String(to.meta.title ?? '首页')} · ${site}`
})

export default router
