/** 与后端 com.example.common.result.Result 对应的结构 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 与后端 com.example.user.entity.SysUser 对应的结构 */
export interface User {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  status: number
  createTime: string
  updateTime: string
}

/** 新增用户时提交的字段 */
export type UserPayload = Pick<User, 'username' | 'nickname' | 'email'> & {
  /** 不传时后端使用默认密码 123456 */
  password?: string
}

/** 后端 com.example.common.result.PageResult */
export interface PageResult<T> {
  total: number
  records: T[]
}

/** 后端 com.example.user.entity.SysRole */
export interface Role {
  id: number
  roleCode: string
  roleName: string
  description: string | null
  createTime: string
  updateTime: string
}

/** 新增/修改角色时提交的字段 */
export interface RolePayload {
  roleCode: string
  roleName: string
  description?: string
}

/** 后端 com.example.user.entity.SysPermission */
export interface Permission {
  id: number
  permCode: string
  permName: string
  description: string | null
  createTime: string
}

/** 后端 com.example.common.redis.OnlineUser */
export interface OnlineUser {
  userId: number
  username: string
  nickname: string | null
  clientIp: string | null
  userAgent: string | null
  loginTime: string | null
  lastActiveTime: string | null
}

/** 后端 com.example.common.log.LoginLog */
export interface LoginLog {
  id: number
  userId: number | null
  username: string
  loginType: string
  status: number
  message: string | null
  clientIp: string | null
  userAgent: string | null
  loginTime: string | null
}

/** 后端 com.example.common.log.OperationLog */
export interface OperationLog {
  id: number
  serviceName: string
  userId: number | null
  username: string | null
  requestMethod: string
  requestUri: string
  queryString: string | null
  clientIp: string | null
  userAgent: string | null
  statusCode: number
  durationMs: number
  success: number
  errorMessage: string | null
  createTime: string | null
}

/** 后台管理的分页查询参数 */
export interface PageQuery {
  page: number
  size: number
}

/** 登录请求体 */
export interface LoginPayload {
  username: string
  password: string
}

/** 后端 com.example.auth.dto.TokenResponse */
export interface TokenResponse {
  tokenType: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
}

/** 后端 /auth/me 返回的当前登录用户 */
export interface AuthUser {
  userId: string
  username: string
  authorities: string[] | null
}
