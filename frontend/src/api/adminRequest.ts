import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

import type { Result } from '@/types'
import { clearAdminTokens, getAdminAccessToken } from '@/utils/token'

/**
 * 后台管理专用请求实例。
 * 使用独立令牌（cloud_admin_access_token），与前台登录完全隔离，
 * 401 时也只跳转后台登录页，不影响前台会话。
 */
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000
})

instance.interceptors.request.use((config) => {
  const token = getAdminAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result>) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      clearAdminTokens()
      redirectToAdminLogin()
    }
    return Promise.reject(new Error(message || error.message || '网络异常，请稍后重试'))
  }
)

function redirectToAdminLogin() {
  const { pathname, search } = window.location
  if (pathname === '/admin/login') return
  const redirect = encodeURIComponent(`${pathname}${search}`)
  window.location.replace(`/admin/login?redirect=${redirect}`)
}

export async function adminRequest<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<Result<T>>(config)
  const body = response.data
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export default adminRequest
