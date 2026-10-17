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

/** 后端 com.example.chat.dto.ChatRoomView 房间视图 */
export interface ChatRoom {
  id: number
  name: string
  description: string | null
  ownerId: number
  ownerName: string
  /** 当前房间在线人数，由 Netty 侧的会话注册表统计 */
  onlineCount: number
  createTime: string
}

/** 后端 com.example.chat.entity.ChatMessage 聊天消息 */
export interface ChatMessage {
  id: number
  roomId: number
  userId: number
  username: string
  content: string
  /** CHAT 用户消息，SYSTEM 系统消息 */
  msgType: 'CHAT' | 'SYSTEM'
  createTime: string
}

/** 后端 com.example.rag.dto.KnowledgeBaseView 知识库列表项 */
export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  embeddingModel: string
  ownerName: string
  documentCount: number
  createTime: string
}

/** 后端 com.example.rag.dto.DocumentView 知识库文档 */
export interface KbDocument {
  id: number
  kbId: number
  fileName: string
  /** 版本号，同名文档首次上传为 1，内容变化后递增 */
  version: number
  /** 1 最新版本（参与检索），0 历史版本（保留存档） */
  latest: number
  /** 可见角色码，逗号分隔，为空表示所有用户可见 */
  visibleRoles: string
  contentType: string | null
  fileSize: number
  /** 0 待处理 1 处理中 2 已完成 3 失败 */
  status: number
  statusText: string
  chunkCount: number
  errorMsg: string | null
  createTime: string
}

/** 后端 com.example.rag.dto.RoleOption 上传文档时可选的可见角色 */
export interface RoleOption {
  roleCode: string
  roleName: string
}

/** 后端 com.example.rag.dto.UploadResult 批量上传时单个文件的处理结果 */
export interface UploadResult {
  fileName: string
  /** NEW 新增 / UPDATED 新版本 / DUPLICATE 库中已有 / FAILED 失败 */
  result: 'NEW' | 'UPDATED' | 'DUPLICATE' | 'FAILED'
  message: string
  documentId: number | null
  version: number | null
}

/** 后端 com.example.rag.dto.SourceRef 回答引用的资料片段 */
export interface SourceRef {
  documentId: number | null
  fileName: string | null
  /** 命中的文档版本号，检索只使用最新版本 */
  version: number | null
  score: number
  snippet: string
}

/** 后端 com.example.rag.dto.DocumentPreview 文档预览信息 */
export interface DocumentPreview {
  documentId: number
  fileName: string
  contentType: string | null
  fileSize: number
  /** MinIO 预签名地址，有效期 10 分钟 */
  url: string
}

/** 后端 com.example.rag.dto.AskResponse 知识库问答响应 */
export interface AskResponse {
  answer: string
  sources: SourceRef[]
}

/** 后端 com.example.rag.dto.QaHistoryEntry 智能问答历史 */
export interface QaHistoryEntry {
  id: string
  kbId: number
  userId: number
  username: string
  question: string
  answer: string
  sources: SourceRef[]
  createTime: string
}
