<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import * as mammoth from 'mammoth'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as XLSX from 'xlsx'

import {
  clearAskHistory,
  createKnowledgeBase,
  deleteDocument,
  deleteKnowledgeBase,
  fetchAskMessages,
  fetchAskSessions,
  fetchDocumentDownloadUrl,
  fetchDocumentPreview,
  fetchDocuments,
  fetchKnowledgeBases,
  fetchRoleOptions,
  fetchSuggestedQuestions,
  reindexDocument,
  streamKnowledgeBaseChat,
  uploadDocuments
} from '@/api/rag'
import type {
  AskResponse,
  DocumentPreview,
  KbDocument,
  KnowledgeBase,
  QaChatMessage,
  QaSessionView,
  RoleOption,
  SourceRef,
  UploadResult
} from '@/types'

/** 文档仍在排队或解析中时，前端按该间隔轮询状态 */
const POLL_INTERVAL = 3000

/** 新会话默认展示的随机问题数量 */
const NEW_SESSION_SUGGESTION_LIMIT = 10

/** 助手回答的打字机速度（毫秒/字） */
const TYPEWRITER_INTERVAL = 24

/** 文档管理表格固定每页 10 条 */
const DOC_PAGE_SIZE = 10

/** 可在浏览器内直接渲染的图片扩展名 */
const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']

/** 可在 iframe 内以纯文本方式渲染的扩展名 */
const TEXT_EXTENSIONS = ['txt', 'md', 'markdown', 'csv', 'json', 'log', 'yml', 'yaml', 'xml']

const bases = ref<KnowledgeBase[]>([])
const current = ref<KnowledgeBase | null>(null)
const documents = ref<KbDocument[]>([])
const activeTab = ref('documents')

const loadingBases = ref(false)
const loadingDocs = ref(false)
const uploading = ref(false)
const asking = ref(false)
const docCurrentPage = ref(1)

/** 文档管理筛选条件 */
const docFilters = reactive({
  keyword: '',
  version: '',
  status: '',
  latest: ''
})

const question = ref('')
const currentSessionId = ref<string | null>(null)
const sessions = ref<QaSessionView[]>([])
const messages = ref<QaChatMessage[]>([])
const loadingSessions = ref(false)
const chatContentRef = ref<HTMLElement | null>(null)

/** 知识库级推荐问题：当前会话没有命中追问建议时作为回退 */
const suggestions = ref<string[]>([])
const loadingSuggestions = ref(false)

/** 当前会话中的最后一条助手消息，用于展示回答后的继续追问建议 */
const lastAssistantMessage = computed(() =>
  [...messages.value].reverse().find((item) => item.role === 'ASSISTANT') ?? null
)

/** 当前回答对应的来源文件，固定展示在回答下方、相关问题上方 */
const latestSourceFiles = computed(() =>
  lastAssistantMessage.value ? messageSourceFiles(lastAssistantMessage.value) : []
)

/** 回答下方展示的问题：优先用当前会话最后一条回答带回的建议，没有时回退到知识库随机问题 */
const displaySuggestions = computed(() => {
  const matched = lastAssistantMessage.value?.suggestions ?? []
  if (matched.length > 0) {
    return matched
  }
  if (messages.value.length > 0) {
    return suggestions.value
  }
  return currentSessionId.value == null ? suggestions.value : []
})

/** 展示区标题，说明问题来源 */
const suggestionLabel = computed(() =>
  (lastAssistantMessage.value?.suggestions?.length ?? 0) > 0
    ? '本次匹配片段的相关问题 · 点击直接提问'
    : messages.value.length === 0 && currentSessionId.value == null
      ? '随机问题 · 点击直接提问'
    : '切片预置的问题 · 点击直接提问'
)

/** 当前聊天区是否已有一轮可展示的问答 */
const hasConversation = computed(() => messages.value.length > 0)

/** 文档管理：按关键字/版本/状态/最新版本进行本地筛选 */
const filteredDocuments = computed(() => {
  const keyword = docFilters.keyword.trim().toLowerCase()
  const version = docFilters.version.trim()
  return documents.value.filter((item) => {
    if (keyword && !item.fileName.toLowerCase().includes(keyword)) {
      return false
    }
    if (version && String(item.version) !== version) {
      return false
    }
    if (docFilters.status && String(item.status) !== docFilters.status) {
      return false
    }
    if (docFilters.latest && String(item.latest) !== docFilters.latest) {
      return false
    }
    return true
  })
})

const pagedDocuments = computed(() => {
  const start = (docCurrentPage.value - 1) * DOC_PAGE_SIZE
  return filteredDocuments.value.slice(start, start + DOC_PAGE_SIZE)
})

/** 批量上传的逐文件结果 */
const uploadResults = ref<UploadResult[]>([])
const uploadResultVisible = ref(false)

/** 可供文档选择的可见角色，以及本次上传选中的角色（为空表示所有用户可见） */
const roleOptions = ref<RoleOption[]>([])
const uploadRoles = ref<string[]>([])

/** el-upload 对每个文件回调一次，先攒起来再合并成一次批量请求 */
const pendingUploads: UploadRequestOptions[] = []
let flushTimer: number | null = null

/** 文档预览 */
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<DocumentPreview | null>(null)
const previewHtml = ref('')
/** pdf：内嵌阅读器；image：图片；frame：纯文本 iframe；word/excel：前端解析；other：无法在线预览 */
const previewKind = ref<'pdf' | 'image' | 'frame' | 'word' | 'excel' | 'other'>('other')

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '' })
const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 32, message: '长度在 2 到 32 个字符', trigger: 'blur' }
  ],
  description: [{ max: 100, message: '最多 100 个字符', trigger: 'blur' }]
}

let pollTimer: number | null = null
let typingTimer: number | null = null
let typingQueue = ''
let typingTarget: QaChatMessage | null = null
let typingResolvers: Array<() => void> = []
let currentTypingMessageId: string | null = null

onMounted(async () => {
  await loadRoleOptions()
  await loadBases()
  if (bases.value.length > 0) {
    await selectBase(bases.value[0])
  }
})

onBeforeUnmount(() => {
  stopPolling()
  stopTypewriter()
})

/** 载入可选角色，供上传文档时限定可见范围 */
async function loadRoleOptions() {
  try {
    roleOptions.value = await fetchRoleOptions()
  } catch {
    roleOptions.value = []
  }
}

/** 把角色码转成中文角色名，未知角色码原样展示 */
function roleLabel(roleCode: string) {
  return roleOptions.value.find((item) => item.roleCode === roleCode)?.roleName ?? roleCode
}

/** 文档的可见角色名列表，为空表示所有用户可见 */
function visibleRoleNames(visibleRoles: string | null) {
  if (!visibleRoles) return []
  return visibleRoles
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean)
    .map(roleLabel)
}

async function loadBases() {
  loadingBases.value = true
  try {
    bases.value = await fetchKnowledgeBases()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loadingBases.value = false
  }
}

async function selectBase(base: KnowledgeBase) {
  current.value = base
  question.value = ''
  currentSessionId.value = null
  sessions.value = []
  messages.value = []
  resetDocFilters()
  stopPolling()
  await loadDocuments()
  await Promise.all([loadSessions(), loadSuggestions()])
}

/** 拉取该知识库切片预生成的推荐问题 */
async function loadSuggestions() {
  const base = current.value
  if (!base) {
    suggestions.value = []
    return
  }
  loadingSuggestions.value = true
  try {
    suggestions.value = await fetchSuggestedQuestions(base.id, NEW_SESSION_SUGGESTION_LIMIT)
  } catch {
    suggestions.value = []
  } finally {
    loadingSuggestions.value = false
  }
}

/** 点击推荐问题：直接以其为问题向模型提问 */
async function askSuggestion(item: string) {
  if (asking.value) return
  question.value = item
  await handleAsk()
}

/** 拉取当前知识库下当前用户的会话列表，默认自动切到最近更新的会话 */
async function loadSessions() {
  const base = current.value
  if (!base) {
    sessions.value = []
    messages.value = []
    currentSessionId.value = null
    return
  }
  loadingSessions.value = true
  try {
    sessions.value = await fetchAskSessions(base.id)
    if (sessions.value.length === 0) {
      currentSessionId.value = null
      messages.value = []
      return
    }
    const currentExists = currentSessionId.value
      ? sessions.value.some((item) => item.id === currentSessionId.value)
      : false
    const targetId = currentExists ? currentSessionId.value : sessions.value[0].id
    await selectSession(targetId!)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loadingSessions.value = false
  }
}

/** 读取指定会话的完整聊天消息 */
async function selectSession(sessionId: string) {
  const base = current.value
  if (!base) return
  currentSessionId.value = sessionId
  messages.value = await fetchAskMessages(base.id, sessionId)
  scrollToBottom()
}

/** 开启新会话：清空当前聊天区并重新随机取一组默认问题 */
async function startNewSession() {
  if (asking.value) return
  currentSessionId.value = null
  messages.value = []
  question.value = ''
  await loadSuggestions()
}

async function handleClearHistory() {
  const base = current.value
  if (!base) return
  try {
    await ElMessageBox.confirm('确定清空当前知识库下的问答历史？', '清空确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await clearAskHistory(base.id)
    sessions.value = []
    messages.value = []
    currentSessionId.value = null
    ElMessage.success('问答历史已清空')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function loadDocuments() {
  const base = current.value
  if (!base) return
  loadingDocs.value = true
  try {
    documents.value = await fetchDocuments(base.id)
    ensureDocPageInRange()
    syncPolling()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loadingDocs.value = false
  }
}

/** 有文档还在处理中就开启轮询，全部结束则停止 */
function syncPolling() {
  const pending = documents.value.some((item) => item.status === 0 || item.status === 1)
  if (pending) {
    startPolling()
  } else {
    stopPolling()
  }
}

function startPolling() {
  if (pollTimer !== null) return
  pollTimer = window.setInterval(() => {
    void refreshDocumentsSilently()
  }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function handleDocFilterChange() {
  docCurrentPage.value = 1
}

function resetDocFilters() {
  docFilters.keyword = ''
  docFilters.version = ''
  docFilters.status = ''
  docFilters.latest = ''
  docCurrentPage.value = 1
}

function ensureDocPageInRange() {
  const maxPage = Math.max(1, Math.ceil(filteredDocuments.value.length / DOC_PAGE_SIZE))
  if (docCurrentPage.value > maxPage) {
    docCurrentPage.value = maxPage
  }
}

function handleDocPageChange(page: number) {
  docCurrentPage.value = page
}

async function refreshDocumentsSilently() {
  const base = current.value
  if (!base) {
    stopPolling()
    return
  }
  try {
    documents.value = await fetchDocuments(base.id)
    ensureDocPageInRange()
    syncPolling()
  } catch {
    stopPolling()
  }
}

function handleUpload(options: UploadRequestOptions) {
  pendingUploads.push(options)
  if (flushTimer !== null) return
  // 同一批选择出来的文件会在同一轮事件循环内回调，合并成一次请求提交
  flushTimer = window.setTimeout(() => {
    flushTimer = null
    void flushUploads()
  }, 0)
}

async function flushUploads() {
  const batch = pendingUploads.splice(0, pendingUploads.length)
  const base = current.value
  if (!base || batch.length === 0) return

  uploading.value = true
  try {
    const results = await uploadDocuments(
      base.id,
      batch.map((item) => item.file as File),
      uploadRoles.value
    )
    results.forEach((item, index) => batch[index]?.onSuccess?.(item))
    showUploadResults(results)
    await loadDocuments()
    await loadBases()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    uploading.value = false
  }
}

/** 弹出逐文件结果，同类结果再各给一条整体提示 */
function showUploadResults(results: UploadResult[]) {
  uploadResults.value = results
  uploadResultVisible.value = true

  const stored = results.filter((item) => item.result === 'NEW' || item.result === 'UPDATED').length
  const duplicated = results.filter((item) => item.result === 'DUPLICATE').length
  const failed = results.filter((item) => item.result === 'FAILED').length
  if (stored > 0) ElMessage.success(`${stored} 个文件已提交，正在后台解析入库`)
  if (duplicated > 0) ElMessage.warning(`${duplicated} 个文件与库中最新版本内容一致，已跳过`)
  if (failed > 0) ElMessage.error(`${failed} 个文件上传失败`)
}

function resultTagType(result: UploadResult['result']) {
  if (result === 'NEW') return 'success'
  if (result === 'UPDATED') return 'primary'
  if (result === 'DUPLICATE') return 'warning'
  return 'danger'
}

function resultText(result: UploadResult['result']) {
  if (result === 'NEW') return '新增入库'
  if (result === 'UPDATED') return '新版本入库'
  if (result === 'DUPLICATE') return '库中已有'
  return '上传失败'
}

async function handleReindex(row: KbDocument) {
  try {
    await reindexDocument(row.id)
    ElMessage.success('已重新提交入库任务')
    await loadDocuments()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function handleDeleteDocument(row: KbDocument) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.fileName}」？其向量数据会一并清除。`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteDocument(row.id)
    ElMessage.success('文档已删除')
    await loadDocuments()
    await loadBases()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function handleDeleteBase(base: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${base.name}」？其下文档与向量会一并清除。`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteKnowledgeBase(base.id)
    ElMessage.success('知识库已删除')
    if (current.value?.id === base.id) {
      current.value = null
      documents.value = []
      sessions.value = []
      messages.value = []
      currentSessionId.value = null
      stopPolling()
    }
    await loadBases()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function handleAsk() {
  const base = current.value
  const text = question.value.trim()
  if (!base || !text) return
  asking.value = true
  const userMessage: QaChatMessage = {
    id: `local-user-${Date.now()}`,
    sessionId: currentSessionId.value ?? '',
    kbId: base.id,
    role: 'USER',
    content: text,
    createTime: new Date().toLocaleString()
  }
  const assistantMessage: QaChatMessage = {
    id: `local-assistant-${Date.now()}`,
    sessionId: currentSessionId.value ?? '',
    kbId: base.id,
    role: 'ASSISTANT',
    content: '',
    sources: [],
    suggestions: [],
    createTime: new Date().toLocaleString()
  }
  messages.value = [...messages.value, userMessage, assistantMessage]
  const userEntry = messages.value[messages.value.length - 2]!
  const assistantEntry = messages.value[messages.value.length - 1]!
  question.value = ''
  scrollToBottom()
  try {
    let streamError = ''
    let finalResponse: AskResponse | null = null
    await streamKnowledgeBaseChat(
      base.id,
      { sessionId: currentSessionId.value ?? undefined, question: text },
      {
        onSession: (sessionId) => {
          currentSessionId.value = sessionId
          userEntry.sessionId = sessionId
          assistantEntry.sessionId = sessionId
        },
        onDelta: (chunk) => {
          enqueueAssistantText(assistantEntry, chunk)
        },
        onDone: (response) => {
          finalResponse = response
        },
        onError: (message) => {
          streamError = message
        }
      }
    )
    if (streamError) {
      throw new Error(streamError)
    }
    await waitForTypewriter()
    const responsePayload = finalResponse as AskResponse | null
    if (responsePayload) {
      currentSessionId.value = responsePayload.sessionId ?? currentSessionId.value
      assistantEntry.content = responsePayload.answer
      assistantEntry.sources = responsePayload.sources ?? []
      assistantEntry.suggestions = responsePayload.suggestions ?? []
    }
    await loadSessions()
    scrollToBottom()
  } catch (e) {
    stopTypewriter()
    messages.value = messages.value.filter((item) => item.id !== userEntry.id && item.id !== assistantEntry.id)
    ElMessage.error((e as Error).message)
  } finally {
    asking.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const node = chatContentRef.value
    if (node) {
      node.scrollTop = node.scrollHeight
    }
  })
}

function enqueueAssistantText(message: QaChatMessage, chunk: string) {
  if (!chunk) return
  typingTarget = message
  currentTypingMessageId = message.id
  typingQueue += chunk
  if (typingTimer !== null) {
    return
  }
  typingTimer = window.setInterval(() => {
    if (!typingTarget || typingQueue.length === 0) {
      stopTypewriter()
      return
    }
    typingTarget.content += typingQueue.charAt(0)
    typingQueue = typingQueue.slice(1)
    scrollToBottom()
    if (typingQueue.length === 0) {
      stopTypewriter()
    }
  }, TYPEWRITER_INTERVAL)
}

function waitForTypewriter() {
  if (typingQueue.length === 0 && typingTimer === null) {
    return Promise.resolve()
  }
  return new Promise<void>((resolve) => {
    typingResolvers.push(resolve)
  })
}

function stopTypewriter() {
  if (typingTimer !== null) {
    window.clearInterval(typingTimer)
    typingTimer = null
  }
  typingQueue = ''
  typingTarget = null
  currentTypingMessageId = null
  const resolvers = typingResolvers
  typingResolvers = []
  resolvers.forEach((resolve) => resolve())
}

function isTypingMessage(message: QaChatMessage) {
  return currentTypingMessageId === message.id
}

/** 回答下方展示来源文件，按 documentId 去重，避免同一文件重复出现多次 */
function messageSourceFiles(message: QaChatMessage) {
  const result: SourceRef[] = []
  const seen = new Set<string>()
  for (const source of message.sources ?? []) {
    const key = source.documentId != null ? `doc-${source.documentId}` : `file-${source.fileName ?? ''}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push(source)
  }
  return result
}

/** 判断文档能用哪种方式预览 */
function detectKind(fileName: string | null, contentType: string | null) {
  const name = (fileName ?? '').toLowerCase()
  const type = (contentType ?? '').toLowerCase()
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.slice(dot + 1) : ''
  if (type.includes('pdf') || ext === 'pdf') return 'pdf' as const
  if (type.startsWith('image/') || IMAGE_EXTENSIONS.includes(ext)) return 'image' as const
  if (
    ext === 'docx' ||
    type.includes('wordprocessingml') ||
    type.includes('msword')
  ) {
    return 'word' as const
  }
  if (
    ext === 'xls' ||
    ext === 'xlsx' ||
    type.includes('spreadsheetml') ||
    type.includes('ms-excel')
  ) {
    return 'excel' as const
  }
  if (type.startsWith('text/') || TEXT_EXTENSIONS.includes(ext)) return 'frame' as const
  return 'other' as const
}

/** 打开预览弹窗：取回原件的内联临时地址，并按类型决定渲染方式 */
async function loadPreview(documentId: number) {
  preview.value = null
  previewHtml.value = ''
  previewKind.value = 'other'
  previewVisible.value = true
  previewLoading.value = true
  try {
    const data = await fetchDocumentPreview(documentId)
    preview.value = data
    previewKind.value = detectKind(data.fileName, data.contentType)
    if (previewKind.value === 'word') {
      previewHtml.value = isLegacyWordFile(data.fileName)
        ? textToHtml(data.parsedText || '暂时无法解析该文档内容，请下载原件后查看。')
        : await renderWordPreview(data.url)
    } else if (previewKind.value === 'excel') {
      previewHtml.value = await renderExcelPreview(data.url)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

/** 文档管理列表里预览原件 */
function openDocumentPreview(row: KbDocument) {
  void loadPreview(row.id)
}

/** 回答来源里预览原件 */
function openSourcePreview(source: SourceRef) {
  if (source.documentId == null) {
    ElMessage.warning('该来源暂不支持预览')
    return
  }
  void loadPreview(source.documentId)
}

/** 下载原件：后端给出的地址按附件返回，交给浏览器直接保存 */
async function saveDocument(documentId: number) {
  try {
    const data = await fetchDocumentDownloadUrl(documentId)
    const link = document.createElement('a')
    link.href = data.url
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

/** 回答来源里下载原件 */
function saveSourceDocument(source: SourceRef) {
  if (source.documentId == null) {
    ElMessage.warning('该来源暂不支持下载')
    return
  }
  void saveDocument(source.documentId)
}

/** 新窗口打开原件（地址 10 分钟内有效） */
function openPreviewInNewTab() {
  if (!preview.value) return
  window.open(preview.value.url, '_blank', 'noopener')
}

async function fetchPreviewBuffer(url: string) {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error('获取文档预览内容失败')
  }
  return response.arrayBuffer()
}

async function renderWordPreview(url: string) {
  const arrayBuffer = await fetchPreviewBuffer(url)
  const result = await mammoth.convertToHtml({ arrayBuffer })
  return result.value || '<p>文档内容为空</p>'
}

async function renderExcelPreview(url: string) {
  const arrayBuffer = await fetchPreviewBuffer(url)
  const workbook = XLSX.read(arrayBuffer, { type: 'array' })
  const sections = workbook.SheetNames.map((sheetName) => {
    const sheet = workbook.Sheets[sheetName]
    const html = XLSX.utils.sheet_to_html(sheet, { editable: false })
    return `
      <section class="sheet-section">
        <h3 class="sheet-title">${sheetName}</h3>
        <div class="sheet-table-wrap">${html}</div>
      </section>
    `
  })
  return sections.length > 0 ? sections.join('') : '<p>工作簿中没有可预览的工作表</p>'
}

function isLegacyWordFile(fileName: string) {
  const lowerName = fileName.toLowerCase()
  return lowerName.endsWith('.doc') && !lowerName.endsWith('.docx')
}

function textToHtml(text: string) {
  return `<div class="doc-text-preview">${escapeHtml(text).replace(/\r?\n/g, '<br />')}</div>`
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function openDialog() {
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await createKnowledgeBase({
      name: form.name,
      description: form.description || undefined
    })
    ElMessage.success('知识库创建成功')
    dialogVisible.value = false
    await loadBases()
    const target = bases.value.find((item) => item.id === created.id) ?? created
    await selectBase(target)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

function statusType(status: number) {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 1) return 'primary'
  return 'warning'
}

/** 历史版本整行置灰，突出当前生效的最新版本 */
function docRowClass({ row }: { row: KbDocument }) {
  return row.latest === 0 ? 'history-row' : ''
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="knowledge">
    <el-card class="base-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">知识库</span>
          <span class="card-count">{{ bases.length }} 个</span>
          <el-button type="primary" size="small" @click="openDialog">
            <el-icon><Plus /></el-icon>
            <span>新建</span>
          </el-button>
        </div>
      </template>

      <el-scrollbar v-loading="loadingBases" class="base-scroll">
        <el-empty v-if="bases.length === 0" description="暂无知识库" :image-size="64" />
        <div
          v-for="base in bases"
          :key="base.id"
          class="base-item"
          :class="{ active: current?.id === base.id }"
          @click="selectBase(base)"
        >
          <span class="base-initial">{{ base.name.charAt(0) }}</span>
          <div class="base-main">
            <span class="base-name">{{ base.name }}</span>
            <span class="base-owner">{{ base.ownerName || '未知' }} · {{ base.documentCount }} 个文档</span>
          </div>
          <el-button text size="small" type="danger" class="base-del" @click.stop="handleDeleteBase(base)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </el-scrollbar>
    </el-card>

    <el-card class="main-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="main-title">
            <span class="main-name">{{ current ? current.name : '知识库' }}</span>
            <el-tag v-if="current" size="small" effect="plain" type="info">{{ current.embeddingModel }}</el-tag>
          </div>
          <span v-if="current" class="main-meta">
            {{ current.documentCount }} 个可见文档 · 创建者 {{ current.ownerName || '未知' }}
          </span>
        </div>
      </template>

      <el-empty v-if="!current" description="请选择或创建一个知识库" />

      <el-tabs v-else v-model="activeTab" class="main-tabs">
        <el-tab-pane label="文档管理" name="documents">
          <div class="doc-pane">
            <div class="toolbar">
            <el-select
              v-model="uploadRoles"
              class="role-select"
              multiple
              clearable
              collapse-tags
              collapse-tags-tooltip
              :disabled="uploading"
              placeholder="可见角色（不选 = 所有用户可见）"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.roleCode"
                :label="role.roleName"
                :value="role.roleCode"
              />
            </el-select>
            <el-upload
              multiple
              :show-file-list="false"
              :http-request="handleUpload"
              :disabled="uploading"
              accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.html,.htm,.json,.xml,.jpg,.jpeg,.png,.bmp,.webp"
            >
              <el-button type="primary" :loading="uploading">
                <el-icon><UploadFilled /></el-icon>
                <span>批量上传</span>
              </el-button>
            </el-upload>
            <el-button :loading="loadingDocs" @click="loadDocuments">
              <el-icon><Refresh /></el-icon>
              <span>刷新</span>
            </el-button>
            </div>

            <div class="doc-filters">
            <el-input
              v-model="docFilters.keyword"
              clearable
              class="doc-filter-name"
              placeholder="按文档名称筛选"
              @input="handleDocFilterChange"
              @clear="handleDocFilterChange"
            />
            <el-input
              v-model="docFilters.version"
              clearable
              class="doc-filter-version"
              placeholder="版本号"
              @input="handleDocFilterChange"
              @clear="handleDocFilterChange"
            />
            <el-select
              v-model="docFilters.status"
              clearable
              class="doc-filter-select"
              placeholder="状态"
              @change="handleDocFilterChange"
              @clear="handleDocFilterChange"
            >
              <el-option label="待处理" value="0" />
              <el-option label="处理中" value="1" />
              <el-option label="已完成" value="2" />
              <el-option label="失败" value="3" />
            </el-select>
            <el-select
              v-model="docFilters.latest"
              clearable
              class="doc-filter-select"
              placeholder="版本类型"
              @change="handleDocFilterChange"
              @clear="handleDocFilterChange"
            >
              <el-option label="最新版本" value="1" />
              <el-option label="历史版本" value="0" />
            </el-select>
            <el-button @click="resetDocFilters">重置筛选</el-button>
            <span class="doc-filter-count">共 {{ filteredDocuments.length }} 条</span>
            </div>

            <p class="hint">
            可一次选择多个文件；同名文件内容一致不会重复入库，内容有变化会生成新版本。未指定可见角色时默认所有用户可见。
            图片（jpg/jpeg/png/bmp/webp，单张不超过 10MB）会自动调用 OCR 识别其中的文字并入库，同样可被智能问答检索。
            </p>

            <div class="doc-table-wrap">
              <el-table
                v-loading="loadingDocs"
                :data="pagedDocuments"
                height="100%"
                empty-text="暂无文档"
                :row-class-name="docRowClass"
              >
                <el-table-column label="文件名" min-width="230" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span>{{ row.fileName }}</span>
                    <el-tag size="small" effect="plain" class="doc-tag">v{{ row.version }}</el-tag>
                    <el-tag v-if="row.latest === 0" size="small" type="info" effect="plain" class="doc-tag">
                      历史版本
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="大小" width="100">
                  <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tooltip :content="row.errorMsg || row.statusText" :disabled="!row.errorMsg">
                      <el-tag :type="statusType(row.status)" size="small">{{ row.statusText }}</el-tag>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column label="可见角色" min-width="150">
                  <template #default="{ row }">
                    <el-tag
                      v-for="name in visibleRoleNames(row.visibleRoles)"
                      :key="name"
                      size="small"
                      effect="plain"
                      class="doc-tag"
                    >
                      {{ name }}
                    </el-tag>
                    <el-tag
                      v-if="visibleRoleNames(row.visibleRoles).length === 0"
                      size="small"
                      type="info"
                      effect="plain"
                    >
                      所有用户
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="chunkCount" label="分片数" width="90" />
                <el-table-column prop="createTime" label="上传时间" width="170" />
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <div class="doc-actions">
                      <el-button text size="small" type="primary" class="doc-action-btn" @click="openDocumentPreview(row)">
                        预览
                      </el-button>
                      <el-button text size="small" type="primary" class="doc-action-btn" @click="saveDocument(row.id)">
                        下载
                      </el-button>
                      <el-button text size="small" class="doc-action-btn" @click="handleReindex(row)">
                        重新入库
                      </el-button>
                      <el-button text size="small" type="danger" class="doc-action-btn" @click="handleDeleteDocument(row)">
                        删除
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div class="doc-pagination">
              <el-pagination
                background
                layout="prev, pager, next"
                :current-page="docCurrentPage"
                :page-size="DOC_PAGE_SIZE"
                :total="filteredDocuments.length"
                @current-change="handleDocPageChange"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="智能问答" name="ask">
          <div class="chat-layout">
            <aside class="history">
              <div class="history-head">
                <div class="history-head-main">
                  <div class="history-title-row">
                    <span class="history-title">历史会话</span>
                    <span class="history-count">{{ sessions.length }} 个</span>
                  </div>
                  <span class="history-subtitle">继续追问已有会话，或开启一轮新对话</span>
                </div>
                <div class="history-head-actions">
                  <el-button
                    size="small"
                    type="primary"
                    class="history-action-btn"
                    :disabled="asking"
                    @click="startNewSession"
                  >
                    <el-icon><Plus /></el-icon>
                    <span>新建会话</span>
                  </el-button>
                  <el-button
                    size="small"
                    plain
                    type="danger"
                    class="history-action-btn"
                    :disabled="sessions.length === 0"
                    @click="handleClearHistory"
                  >
                    <el-icon><Delete /></el-icon>
                    <span>清空</span>
                  </el-button>
                </div>
              </div>
              <div v-loading="loadingSessions" class="history-body">
                <el-empty v-if="sessions.length === 0" description="暂无历史会话" :image-size="56" />
                <div
                  v-for="item in sessions"
                  :key="item.id"
                  class="history-item"
                  :class="{ active: currentSessionId === item.id }"
                  @click="selectSession(item.id)"
                >
                  <div class="history-question">{{ item.title }}</div>
                  <div class="history-preview">{{ item.preview || '暂无预览内容' }}</div>
                  <div class="history-meta">
                    <span>{{ item.updateTime }}</span>
                    <span>{{ item.messageCount }} 条消息</span>
                  </div>
                </div>
              </div>
            </aside>

            <section class="chat-panel">
              <div ref="chatContentRef" class="chat-content">
                <div v-if="!hasConversation" class="ask-empty">
                  <el-empty description="在下方输入问题，开始一个新的会话" :image-size="80" />
                </div>

                <div v-else class="chat-thread">
                  <div
                    v-for="item in messages"
                    :key="item.id"
                    class="message-row"
                    :class="item.role === 'USER' ? 'is-user' : 'is-assistant'"
                  >
                    <div
                      class="message-bubble"
                      :class="item.role === 'USER' ? 'user-bubble' : 'assistant-bubble'"
                    >
                      <div class="message-role">{{ item.role === 'USER' ? '我' : '知识库助手' }}</div>
                      <div class="message-text">
                        {{ item.content || (asking && item.role === 'ASSISTANT' ? '' : '') }}
                        <span
                          v-if="item.role === 'ASSISTANT' && isTypingMessage(item)"
                          class="typewriter-caret"
                          aria-hidden="true"
                        ></span>
                      </div>
                    </div>
                  </div>

                </div>

                <div v-if="latestSourceFiles.length > 0" class="message-sources">
                  <div class="message-sources-title">来源文件</div>
                  <div
                    v-for="source in latestSourceFiles"
                    :key="source.documentId ?? source.fileName ?? source.snippet"
                    class="message-source-item"
                  >
                    <span class="message-source-name">
                      {{ source.fileName || '未命名文件' }}
                      <template v-if="source.version">v{{ source.version }}</template>
                    </span>
                    <div class="message-source-actions">
                      <el-button
                        text
                        size="small"
                        type="primary"
                        :disabled="source.documentId == null"
                        @click="openSourcePreview(source)"
                      >
                        预览
                      </el-button>
                      <el-button
                        text
                        size="small"
                        :disabled="source.documentId == null"
                        @click="saveSourceDocument(source)"
                      >
                        下载
                      </el-button>
                    </div>
                  </div>
                </div>

                <div
                  v-if="displaySuggestions.length > 0"
                  v-loading="loadingSuggestions"
                  class="suggestions"
                  :class="{ 'starter-suggestions': !hasConversation }"
                >
                  <div class="answer-label">{{ suggestionLabel }}</div>
                  <div class="suggestion-list">
                    <button
                      v-for="(item, index) in displaySuggestions"
                      :key="index"
                      type="button"
                      class="suggestion-item"
                      :disabled="asking"
                      @click="askSuggestion(item)"
                    >
                      <el-icon><ChatDotRound /></el-icon>
                      <span class="suggestion-text">{{ item }}</span>
                    </button>
                  </div>
                </div>
              </div>

              <div class="composer">
                <el-input
                  v-model="question"
                  type="textarea"
                  :rows="3"
                  maxlength="500"
                  show-word-limit
                  resize="none"
                  placeholder="发送消息，例如：这份文档的主要结论是什么？"
                  @keydown.ctrl.enter="handleAsk"
                />
                <div class="composer-actions">
                  <span class="ask-tip">Ctrl + Enter 快速提问 · 回答仅基于你有权限查看的最新文档</span>
                  <el-button type="primary" round :loading="asking" :disabled="!question.trim()" @click="handleAsk">
                    <el-icon><Promotion /></el-icon>
                    <span>发送</span>
                  </el-button>
                </div>
              </div>
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建知识库" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="form.name" maxlength="32" placeholder="2 - 32 个字符" />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadResultVisible" title="上传结果" width="620px">
      <el-table :data="uploadResults" max-height="380" empty-text="无上传结果">
        <el-table-column prop="fileName" label="文件" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="resultTagType(row.result)">{{ resultText(row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="uploadResultVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="preview?.fileName || '文档预览'"
      width="74%"
      top="6vh"
      destroy-on-close
    >
      <div v-loading="previewLoading" class="preview-body">
        <template v-if="preview">
          <iframe
            v-if="previewKind === 'pdf' || previewKind === 'frame'"
            :src="preview.url"
            class="preview-frame"
            :title="preview.fileName"
          />
          <div v-else-if="previewKind === 'image'" class="preview-image-wrap">
            <img :src="preview.url" class="preview-image" :alt="preview.fileName" />
          </div>
          <div
            v-else-if="previewKind === 'word' || previewKind === 'excel'"
            class="preview-rich"
            v-html="previewHtml"
          />
          <div v-else class="preview-fallback">
            <el-alert
              type="info"
              show-icon
              :closable="false"
              title="该格式无法在浏览器内直接预览，可下载原件后查看"
            />
          </div>
        </template>
      </div>

      <template #footer>
        <span v-if="preview" class="preview-meta">
          {{ preview.contentType || '未知类型' }} · {{ formatSize(preview.fileSize) }} · 链接 10 分钟内有效
        </span>
        <el-button v-if="preview" @click="openPreviewInNewTab">在新窗口打开</el-button>
        <el-button v-if="preview" type="primary" @click="saveDocument(preview.documentId)">
          下载原件
        </el-button>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge {
  display: flex;
  gap: 18px;
  height: 100%;
  min-height: 520px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title {
  flex: 1;
  min-width: 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 650;
}

.card-count {
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 400;
}

/* 左侧知识库列表 */
.base-card {
  display: flex;
  flex-direction: column;
  flex: 0 0 264px;
  width: 264px;
}

.base-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 8px;
}

.base-scroll {
  height: 100%;
}

.base-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.base-item:hover {
  background: var(--surface-hover);
}

.base-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.base-initial {
  flex: none;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-sm);
  background: var(--surface-sunken);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 650;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.base-item.active .base-initial {
  background: var(--primary);
  color: #fff;
}

.base-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.base-name {
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-owner {
  color: var(--text-muted);
  font-size: 11.5px;
}

/* 删除按钮默认隐藏，悬停或选中时出现，避免列表显得杂乱 */
.base-del {
  flex: none;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.base-item:hover .base-del,
.base-item.active .base-del {
  opacity: 1;
}

/* 右侧主区域 */
.main-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.main-card :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.main-title {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.main-name {
  color: var(--text);
  font-size: 15px;
  font-weight: 650;
}

.main-meta {
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 400;
}

.main-tabs {
  flex: 1;
  min-height: 0;
}

.main-tabs :deep(.el-tabs__content) {
  height: 100%;
}

.main-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.doc-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.doc-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 10px;
}

.doc-filter-name {
  width: 240px;
}

.doc-filter-version {
  width: 140px;
}

.doc-filter-select {
  width: 140px;
}

.doc-filter-count {
  color: var(--text-faint);
  font-size: 12px;
}

.hint {
  margin: 0 0 12px;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.6;
}

.doc-table-wrap {
  flex: 1;
  min-height: 0;
}

.doc-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.role-select {
  width: 260px;
}

.doc-tag {
  margin-left: 6px;
}

.doc-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 8px;
  align-items: center;
}

.doc-action-btn {
  justify-content: flex-start;
  margin: 0;
  padding: 0;
}

.doc-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 历史版本整行置灰 */
:deep(.history-row) {
  color: var(--text-muted);
}

/* 智能问答：左侧历史，右侧聊天区 */
.chat-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  height: 100%;
  min-height: 640px;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  background: linear-gradient(180deg, var(--surface) 0%, var(--surface-sunken) 100%);
  overflow: hidden;
}

.chat-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 24px 18px;
}

.ask-tip {
  color: var(--text-faint);
  font-size: 12px;
}

.ask-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
}

.chat-thread {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
}

.message-row.is-user {
  justify-content: flex-end;
}

.message-row.is-assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(760px, 92%);
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: var(--shadow-sm);
}

.user-bubble {
  border-color: color-mix(in srgb, var(--primary) 30%, var(--border));
  background: color-mix(in srgb, var(--primary) 10%, #fff);
}

.assistant-bubble {
  background: #fff;
}

.message-role {
  margin-bottom: 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 650;
}

.message-text {
  color: var(--text);
  font-size: 13.5px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.typewriter-caret {
  display: inline-block;
  width: 1px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--primary);
  animation: blink-caret 0.9s step-end infinite;
}

@keyframes blink-caret {
  0%,
  45% {
    opacity: 1;
  }
  46%,
  100% {
    opacity: 0;
  }
}

.message-sources {
  width: min(760px, 92%);
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
}

.message-sources-title {
  margin-bottom: 8px;
  color: var(--text);
  font-size: 12px;
  font-weight: 600;
}

.message-source-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
}

.message-source-item + .message-source-item {
  border-top: 1px dashed var(--border);
}

.message-source-name {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.message-source-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.answer-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

/* 回答结束后展示的推荐问题 */
.suggestions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 760px;
}

.starter-suggestions {
  margin-top: 20px;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggestion-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 7px 12px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--text);
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.suggestion-item:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  box-shadow: var(--shadow-sm);
}

.suggestion-item:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.suggestion-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 智能问答历史侧栏 */
.history {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  background: var(--surface-sunken);
}

.history-head {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.history-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.history-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-head-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.history-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}

.history-count {
  color: var(--text-faint);
  font-size: 12px;
  white-space: nowrap;
}

.history-subtitle {
  color: var(--text-faint);
  font-size: 12px;
}

.history-action-btn {
  width: 100%;
  margin: 0;
}

.history-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.history-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.history-item:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-sm);
}

.history-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.history-question {
  color: var(--text);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.6;
  word-break: break-word;
}

.history-preview {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.55;
  word-break: break-word;
}

.history-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  color: var(--text-faint);
  font-size: 11.5px;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 18px;
  border-top: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}

.composer :deep(.el-textarea__inner) {
  min-height: 96px !important;
  padding: 14px 16px;
  border-radius: 20px;
  box-shadow: none;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* 文档预览弹窗 */
.preview-body {
  min-height: 62vh;
}

.preview-frame {
  width: 100%;
  height: 70vh;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.preview-image-wrap {
  display: flex;
  justify-content: center;
  padding: 8px;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  border-radius: var(--r-md);
}

.preview-rich {
  max-height: 70vh;
  padding: 16px 18px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: #fff;
  color: var(--text);
  line-height: 1.8;
}

.preview-rich :deep(p) {
  margin: 0 0 12px;
}

.preview-rich :deep(.doc-text-preview) {
  color: var(--text);
  font-size: 14px;
  line-height: 1.9;
  word-break: break-word;
}

.preview-rich :deep(h1),
.preview-rich :deep(h2),
.preview-rich :deep(h3),
.preview-rich :deep(h4) {
  margin: 0 0 12px;
  color: var(--text);
}

.preview-rich :deep(.sheet-section + .sheet-section) {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed var(--border);
}

.preview-rich :deep(.sheet-title) {
  margin: 0 0 12px;
  font-size: 16px;
}

.preview-rich :deep(.sheet-table-wrap) {
  overflow: auto;
}

.preview-rich :deep(table) {
  min-width: 100%;
  border-collapse: collapse;
}

.preview-rich :deep(th),
.preview-rich :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--border);
  font-size: 13px;
  vertical-align: top;
}

.preview-rich :deep(th) {
  background: var(--surface-sunken);
  font-weight: 600;
}

.preview-fallback {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-meta {
  margin-right: 12px;
  color: var(--text-faint);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .chat-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .history-body {
    max-height: 300px;
  }

  .history-head-actions {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .knowledge {
    flex-direction: column;
    height: auto;
  }

  .base-card {
    flex: none;
    width: 100%;
  }

  .base-card :deep(.el-card__body) {
    max-height: 260px;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .doc-filter-name,
  .doc-filter-version,
  .doc-filter-select {
    width: 100%;
  }

  .role-select {
    width: 100%;
  }

  .chat-content {
    padding: 18px 16px 14px;
  }

  .composer {
    padding: 14px;
  }

  .composer-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .hint {
    margin-bottom: 10px;
  }
}
</style>
