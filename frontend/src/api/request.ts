import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'

import type { Result } from '@/types'
import { clearTokens, getAccessToken } from '@/utils/token'

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000
})

/** 自动携带网关要求的 Bearer 令牌 */
instance.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 说明令牌缺失或已过期，清理本地状态并回到登录页 */
instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result>) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      clearTokens()
      redirectToLogin()
    }
    return Promise.reject(new Error(message || error.message || '网络异常，请稍后重试'))
  }
)

function redirectToLogin() {
  const { pathname, search, hash } = window.location
  if (pathname === '/login') return
  const redirect = encodeURIComponent(`${pathname}${search}${hash}`)
  window.location.replace(`/login?redirect=${redirect}`)
}

/**
 * 统一请求入口：拆掉后端 Result 外壳，直接返回业务数据，失败时抛出 Error
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<Result<T>>(config)
  const body = response.data
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export default request
