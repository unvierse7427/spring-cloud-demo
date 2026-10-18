import request from './request'
import type {
  AskResponse,
  DocumentPreview,
  KbDocument,
  KnowledgeBase,
  OcrText,
  QaChatMessage,
  QaSessionView,
  RoleOption,
  UploadResult
} from '@/types'
import { clearTokens, getAccessToken } from '@/utils/token'

/** GET /api/rag/kb -> 知识库列表（含文档数量） */
export function fetchKnowledgeBases() {
  return request<KnowledgeBase[]>({ url: '/rag/kb', method: 'get' })
}

/** POST /api/rag/kb -> 新建知识库 */
export function createKnowledgeBase(data: { name: string; description?: string }) {
  return request<KnowledgeBase>({ url: '/rag/kb', method: 'post', data })
}

/** DELETE /api/rag/kb/{kbId} -> 删除知识库（连同文档与向量） */
export function deleteKnowledgeBase(kbId: number) {
  return request<void>({ url: `/rag/kb/${kbId}`, method: 'delete' })
}

/** GET /api/rag/kb/{kbId}/documents -> 知识库下的文档列表 */
export function fetchDocuments(kbId: number) {
  return request<KbDocument[]>({ url: `/rag/kb/${kbId}/documents`, method: 'get' })
}

/** GET /api/rag/kb/roles -> 上传文档时可选的可见角色列表 */
export function fetchRoleOptions() {
  return request<RoleOption[]>({ url: '/rag/kb/roles', method: 'get' })
}

/** POST /api/rag/kb/{kbId}/documents -> 批量上传文档，逐个返回处理结果，向量化在后台异步完成 */
export function uploadDocuments(kbId: number, files: File[], roles: string[] = []) {
  const data = new FormData()
  files.forEach((file) => data.append('files', file))
  roles.forEach((role) => data.append('roles', role))
  return request<UploadResult[]>({
    url: `/rag/kb/${kbId}/documents`,
    method: 'post',
    data,
    timeout: 300000
  })
}

/** POST /api/rag/kb/documents/{documentId}/reindex -> 重新解析并向量化 */
export function reindexDocument(documentId: number) {
  return request<void>({ url: `/rag/kb/documents/${documentId}/reindex`, method: 'post' })
}

/** DELETE /api/rag/kb/documents/{documentId} -> 删除文档 */
export function deleteDocument(documentId: number) {
  return request<void>({ url: `/rag/kb/documents/${documentId}`, method: 'delete' })
}

/** GET /api/rag/kb/documents/{documentId}/preview -> 文档原件的临时预览地址（内联打开，10 分钟有效） */
export function fetchDocumentPreview(documentId: number) {
  return request<DocumentPreview>({ url: `/rag/kb/documents/${documentId}/preview`, method: 'get' })
}

/** GET /api/rag/kb/documents/{documentId}/download -> 文档原件的临时下载地址（附件保存，10 分钟有效） */
export function fetchDocumentDownloadUrl(documentId: number) {
  return request<DocumentPreview>({ url: `/rag/kb/documents/${documentId}/download`, method: 'get' })
}

/** POST /api/rag/kb/{kbId}/ask -> 知识库问答，返回答案与引用来源 */
export function askKnowledgeBase(kbId: number, data: { question: string; topK?: number }) {
  return request<AskResponse>({
    url: `/rag/kb/${kbId}/ask`,
    method: 'post',
    data,
    timeout: 60000
  })
}

/** SSE 事件回调：会话 id、回答增量、最终结果与错误 */
export interface StreamHandlers {
  onSession?: (sessionId: string) => void
  onDelta?: (chunk: string) => void
  onDone?: (response: AskResponse) => void
  onError?: (message: string) => void
}

/** POST /api/rag/kb/{kbId}/ask/stream -> SSE 流式问答，在同一会话里持续追问 */
export async function streamKnowledgeBaseChat(
  kbId: number,
  data: { sessionId?: string; question: string; topK?: number },
  handlers: StreamHandlers
) {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/rag/kb/${kbId}/ask/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: `Bearer ${getAccessToken()}`
    },
    body: JSON.stringify(data)
  })
  if (response.status === 401) {
    clearTokens()
    const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}${window.location.hash}`)
    window.location.replace(`/login?redirect=${redirect}`)
    throw new Error('登录已过期，请重新登录')
  }
  if (!response.ok || !response.body) {
    throw new Error('流式问答请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let index = buffer.search(/\r?\n\r?\n/)
    while (index >= 0) {
      const separator = buffer.slice(index).match(/^\r?\n\r?\n/)?.[0] ?? '\n\n'
      const block = buffer.slice(0, index)
      buffer = buffer.slice(index + separator.length)
      handleSseBlock(block, handlers)
      index = buffer.search(/\r?\n\r?\n/)
    }
  }
  if (buffer.trim()) {
    handleSseBlock(buffer, handlers)
  }
}

function handleSseBlock(block: string, handlers: StreamHandlers) {
  if (!block.trim()) return
  let event = 'message'
  const data: string[] = []
  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trimStart())
    }
  })
  const payload = data.join('\n')
  if (event === 'session') {
    handlers.onSession?.(payload)
    return
  }
  if (event === 'delta') {
    handlers.onDelta?.(payload)
    return
  }
  if (event === 'done') {
    handlers.onDone?.(JSON.parse(payload) as AskResponse)
    return
  }
  if (event === 'error') {
    handlers.onError?.(payload || '流式问答失败')
  }
}

/** GET /api/rag/kb/{kbId}/history -> 当前用户在该知识库下的会话列表 */
export function fetchAskSessions(kbId: number) {
  return request<QaSessionView[]>({ url: `/rag/kb/${kbId}/history`, method: 'get' })
}

/** GET /api/rag/kb/{kbId}/history/{sessionId} -> 指定会话的消息列表 */
export function fetchAskMessages(kbId: number, sessionId: string) {
  return request<QaChatMessage[]>({ url: `/rag/kb/${kbId}/history/${sessionId}`, method: 'get' })
}

/** GET /api/rag/kb/{kbId}/suggestions -> 切片预生成的推荐问题，每次随机返回几条 */
export function fetchSuggestedQuestions(kbId: number, limit = 6) {
  return request<string[]>({ url: `/rag/kb/${kbId}/suggestions`, method: 'get', params: { limit } })
}

/** DELETE /api/rag/kb/{kbId}/history -> 清空当前用户在该知识库下的问答历史 */
export function clearAskHistory(kbId: number) {
  return request<void>({ url: `/rag/kb/${kbId}/history`, method: 'delete' })
}

/** POST /api/rag/ocr -> 识别图片中的文字，图片即用即弃、不会被保存 */
export function recognizeImage(file: File) {
  const data = new FormData()
  data.append('file', file)
  return request<OcrText>({ url: '/rag/ocr', method: 'post', data, timeout: 120000 })
}
