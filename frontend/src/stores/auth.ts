import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fetchCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { AuthUser, LoginPayload, TokenResponse } from '@/types'
import { clearTokens, getAccessToken, saveTokens } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(getAccessToken())
  const profile = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => Boolean(accessToken.value))
  const username = computed(() => profile.value?.username ?? '')
  const authorities = computed(() => profile.value?.authorities ?? [])
  /** JWT 中的角色，如 ROLE_ADMIN */
  const roles = computed(() => authorities.value.filter((code) => code.startsWith('ROLE_')))
  /** JWT 中的权限码，如 user:create */
  const permissionCodes = computed(() => authorities.value.filter((code) => !code.startsWith('ROLE_')))
  const isAdmin = computed(() => authorities.value.includes('ROLE_ADMIN'))

  function applyTokens(tokens: TokenResponse) {
    saveTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
    accessToken.value = tokens.accessToken
  }

  async function login(payload: LoginPayload) {
    applyTokens(await loginApi(payload))
    await loadProfile()
  }

  async function loadProfile() {
    if (!accessToken.value) return
    profile.value = await fetchCurrentUser()
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 令牌已失效时忽略服务端返回，前端本地清理即可
    }
    clear()
  }

  function clear() {
    clearTokens()
    accessToken.value = ''
    profile.value = null
  }

  /** 是否拥有某个角色（如 ROLE_ADMIN） */
  function hasRole(code: string) {
    return authorities.value.includes(code)
  }

  /** 是否拥有某个权限码（如 user:create），管理员视为拥有全部权限 */
  function hasPermission(code: string) {
    return authorities.value.includes(code) || isAdmin.value
  }

  return {
    accessToken,
    profile,
    isLoggedIn,
    username,
    authorities,
    roles,
    permissionCodes,
    isAdmin,
    login,
    logout,
    clear,
    loadProfile,
    hasRole,
    hasPermission
  }
})
