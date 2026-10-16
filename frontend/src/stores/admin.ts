import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { adminLogin, adminLogout, fetchAdminProfile } from '@/api/admin'
import type { AuthUser, LoginPayload } from '@/types'
import { clearAdminTokens, getAdminAccessToken, saveAdminTokens } from '@/utils/token'

/** 后台管理登录态：独立令牌 + 必须拥有 ROLE_ADMIN */
export const useAdminStore = defineStore('admin', () => {
  const accessToken = ref(getAdminAccessToken())
  const profile = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => Boolean(accessToken.value))
  const username = computed(() => profile.value?.username ?? '')
  const authorities = computed(() => profile.value?.authorities ?? [])

  function hasRoleAdmin(user: AuthUser) {
    return (user.authorities ?? []).includes('ROLE_ADMIN')
  }

  async function login(payload: LoginPayload) {
    const tokens = await adminLogin(payload)
    saveAdminTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
    accessToken.value = tokens.accessToken
    try {
      const me = await fetchAdminProfile()
      if (!hasRoleAdmin(me)) {
        throw new Error('该账号不是管理员，无法进入后台管理')
      }
      profile.value = me
    } catch (e) {
      // 登录成功但身份校验失败时，不能留下半登录状态
      clear()
      throw e
    }
  }

  async function loadProfile() {
    if (!accessToken.value || profile.value) return
    const me = await fetchAdminProfile()
    if (!hasRoleAdmin(me)) {
      clear()
      throw new Error('该账号不是管理员，无法进入后台管理')
    }
    profile.value = me
  }

  async function logout() {
    try {
      await adminLogout()
    } catch {
      // 令牌已失效时忽略服务端返回，本地清理即可
    }
    clear()
  }

  function clear() {
    clearAdminTokens()
    accessToken.value = ''
    profile.value = null
  }

  return { accessToken, profile, isLoggedIn, username, authorities, login, logout, loadProfile, clear }
})
