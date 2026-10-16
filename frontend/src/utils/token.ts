const ACCESS_TOKEN_KEY = 'cloud_access_token'
const REFRESH_TOKEN_KEY = 'cloud_refresh_token'

/** 后台管理使用独立的令牌，与前台登录互不影响 */
const ADMIN_ACCESS_TOKEN_KEY = 'cloud_admin_access_token'
const ADMIN_REFRESH_TOKEN_KEY = 'cloud_admin_refresh_token'

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
}

export function saveTokens(pair: TokenPair): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, pair.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function getAdminAccessToken(): string {
  return localStorage.getItem(ADMIN_ACCESS_TOKEN_KEY) ?? ''
}

export function getAdminRefreshToken(): string {
  return localStorage.getItem(ADMIN_REFRESH_TOKEN_KEY) ?? ''
}

export function saveAdminTokens(pair: TokenPair): void {
  localStorage.setItem(ADMIN_ACCESS_TOKEN_KEY, pair.accessToken)
  localStorage.setItem(ADMIN_REFRESH_TOKEN_KEY, pair.refreshToken)
}

export function clearAdminTokens(): void {
  localStorage.removeItem(ADMIN_ACCESS_TOKEN_KEY)
  localStorage.removeItem(ADMIN_REFRESH_TOKEN_KEY)
}
