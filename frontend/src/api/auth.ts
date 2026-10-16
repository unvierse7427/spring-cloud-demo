import request from './request'
import type { AuthUser, LoginPayload, TokenResponse } from '@/types'

/** POST /api/auth/login */
export function login(data: LoginPayload) {
  return request<TokenResponse>({ url: '/auth/login', method: 'post', data })
}

/** POST /api/auth/refresh */
export function refreshAccessToken(refreshToken: string) {
  return request<TokenResponse>({ url: '/auth/refresh', method: 'post', data: { refreshToken } })
}

/** POST /api/auth/logout */
export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

/** GET /api/auth/me */
export function fetchCurrentUser() {
  return request<AuthUser>({ url: '/auth/me', method: 'get' })
}
