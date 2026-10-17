import request from './request'
import type {
  AskResponse,
  DocumentPreview,
  KbDocument,
  KnowledgeBase,
  QaHistoryEntry,
  RoleOption,
  UploadResult
} from '@/types'

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

/** GET /api/rag/kb/{kbId}/history -> 当前用户在该知识库下的问答历史（最新在前） */
export function fetchAskHistory(kbId: number) {
  return request<QaHistoryEntry[]>({ url: `/rag/kb/${kbId}/history`, method: 'get' })
}

/** DELETE /api/rag/kb/{kbId}/history -> 清空当前用户在该知识库下的问答历史 */
export function clearAskHistory(kbId: number) {
  return request<void>({ url: `/rag/kb/${kbId}/history`, method: 'delete' })
}
