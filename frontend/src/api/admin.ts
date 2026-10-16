import adminRequest from './adminRequest'
import type {
  AuthUser,
  LoginLog,
  LoginPayload,
  OnlineUser,
  OperationLog,
  PageResult,
  Permission,
  Role,
  RolePayload,
  TokenResponse,
  User
} from '@/types'

/** POST /api/auth/login —— 后台登录复用认证中心，返回后再校验管理员身份 */
export function adminLogin(data: LoginPayload) {
  return adminRequest<TokenResponse>({ url: '/auth/login', method: 'post', data })
}

/** GET /api/auth/me */
export function fetchAdminProfile() {
  return adminRequest<AuthUser>({ url: '/auth/me', method: 'get' })
}

/** POST /api/auth/logout */
export function adminLogout() {
  return adminRequest<void>({ url: '/auth/logout', method: 'post' })
}

// ------------------------------ 用户与状态 ------------------------------

export interface AdminUserQuery {
  keyword?: string
  status?: number | ''
  page: number
  size: number
}

/** GET /api/admin/user/page */
export function pageUsers(params: AdminUserQuery) {
  return adminRequest<PageResult<User>>({ url: '/admin/user/page', method: 'get', params })
}

/** PUT /api/admin/user/{id}/status */
export function updateUserStatus(id: number, status: number) {
  return adminRequest<void>({ url: `/admin/user/${id}/status`, method: 'put', data: { status } })
}

/** GET /api/admin/user/{id}/roles */
export function fetchUserRoleIds(id: number) {
  return adminRequest<number[]>({ url: `/admin/user/${id}/roles`, method: 'get' })
}

/** PUT /api/admin/user/{id}/roles */
export function assignUserRoles(id: number, ids: number[]) {
  return adminRequest<void>({ url: `/admin/user/${id}/roles`, method: 'put', data: { ids } })
}

// ------------------------------ 角色与权限 ------------------------------

/** GET /api/admin/role/list */
export function fetchRoles() {
  return adminRequest<Role[]>({ url: '/admin/role/list', method: 'get' })
}

/** POST /api/admin/role */
export function createRole(data: RolePayload) {
  return adminRequest<Role>({ url: '/admin/role', method: 'post', data })
}

/** PUT /api/admin/role/{id} */
export function updateRole(id: number, data: RolePayload) {
  return adminRequest<Role>({ url: `/admin/role/${id}`, method: 'put', data })
}

/** DELETE /api/admin/role/{id} */
export function deleteRole(id: number) {
  return adminRequest<void>({ url: `/admin/role/${id}`, method: 'delete' })
}

/** GET /api/admin/role/{id}/permissions */
export function fetchRolePermissionIds(id: number) {
  return adminRequest<number[]>({ url: `/admin/role/${id}/permissions`, method: 'get' })
}

/** PUT /api/admin/role/{id}/permissions */
export function assignRolePermissions(id: number, ids: number[]) {
  return adminRequest<void>({ url: `/admin/role/${id}/permissions`, method: 'put', data: { ids } })
}

/** GET /api/admin/permission/list */
export function fetchPermissions() {
  return adminRequest<Permission[]>({ url: '/admin/permission/list', method: 'get' })
}

// ------------------------------ 日志与在线用户 ------------------------------

export interface LoginLogQuery {
  username?: string
  loginType?: string
  status?: number | ''
  page: number
  size: number
}

/** GET /api/admin/log/login */
export function pageLoginLogs(params: LoginLogQuery) {
  return adminRequest<PageResult<LoginLog>>({ url: '/admin/log/login', method: 'get', params })
}

export interface OperationLogQuery {
  username?: string
  requestUri?: string
  success?: number | ''
  page: number
  size: number
}

/** GET /api/admin/log/operation */
export function pageOperationLogs(params: OperationLogQuery) {
  return adminRequest<PageResult<OperationLog>>({ url: '/admin/log/operation', method: 'get', params })
}

/** GET /api/admin/online */
export function fetchOnlineUsers() {
  return adminRequest<OnlineUser[]>({ url: '/admin/online', method: 'get' })
}
