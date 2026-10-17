<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import {
  askKnowledgeBase,
  clearAskHistory,
  createKnowledgeBase,
  deleteDocument,
  deleteKnowledgeBase,
  fetchAskHistory,
  fetchDocumentDownloadUrl,
  fetchDocumentPreview,
  fetchDocuments,
  fetchKnowledgeBases,
  fetchRoleOptions,
  reindexDocument,
  uploadDocuments
} from '@/api/rag'
import type {
  AskResponse,
  DocumentPreview,
  KbDocument,
  KnowledgeBase,
  QaHistoryEntry,
  RoleOption,
  SourceRef,
  UploadResult
} from '@/types'

/** 文档仍在排队或解析中时，前端按该间隔轮询状态 */
const POLL_INTERVAL = 3000

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

const question = ref('')
const answer = ref<AskResponse | null>(null)

/** 智能问答历史：当前知识库 + 当前用户，最新在前 */
const history = ref<QaHistoryEntry[]>([])
const loadingHistory = ref(false)

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
/** 从引用来源打开时带上的片段内容，文档列表打开时为空 */
const previewSnippet = ref('')
/** pdf：内嵌阅读器；image：图片；frame：纯文本 iframe；other：无法在线预览 */
const previewKind = ref<'pdf' | 'image' | 'frame' | 'other'>('other')

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

onMounted(async () => {
  await loadRoleOptions()
  await loadBases()
  if (bases.value.length > 0) {
    await selectBase(bases.value[0])
  }
})

onBeforeUnmount(stopPolling)

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
  answer.value = null
  question.value = ''
  stopPolling()
  await loadDocuments()
  await loadHistory()
}

/** 拉取当前知识库下当前用户的问答历史 */
async function loadHistory() {
  const base = current.value
  if (!base) {
    history.value = []
    return
  }
  loadingHistory.value = true
  try {
    history.value = await fetchAskHistory(base.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loadingHistory.value = false
  }
}

/** 点击历史记录，把问题与回答回填到问答区，引用来源可继续点击预览 */
function restoreHistory(item: QaHistoryEntry) {
  question.value = item.question
  answer.value = { answer: item.answer, sources: item.sources ?? [] }
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
    history.value = []
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

async function refreshDocumentsSilently() {
  const base = current.value
  if (!base) {
    stopPolling()
    return
  }
  try {
    documents.value = await fetchDocuments(base.id)
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
      answer.value = null
      history.value = []
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
  try {
    answer.value = await askKnowledgeBase(base.id, { question: text })
    await loadHistory()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    asking.value = false
  }
}

/** 判断文档能用哪种方式预览 */
function detectKind(fileName: string | null, contentType: string | null) {
  const name = (fileName ?? '').toLowerCase()
  const type = (contentType ?? '').toLowerCase()
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.slice(dot + 1) : ''
  if (type.includes('pdf') || ext === 'pdf') return 'pdf' as const
  if (type.startsWith('image/') || IMAGE_EXTENSIONS.includes(ext)) return 'image' as const
  if (type.startsWith('text/') || TEXT_EXTENSIONS.includes(ext)) return 'frame' as const
  return 'other' as const
}

/** 打开预览弹窗：取回原件的内联临时地址，并按类型决定渲染方式 */
async function loadPreview(documentId: number, snippet = '') {
  previewSnippet.value = snippet
  preview.value = null
  previewKind.value = 'other'
  previewVisible.value = true
  previewLoading.value = true
  try {
    const data = await fetchDocumentPreview(documentId)
    preview.value = data
    previewKind.value = detectKind(data.fileName, data.contentType)
  } catch (e) {
    ElMessage.error((e as Error).message)
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

/** 点击引用来源预览原文 */
function openPreview(source: SourceRef) {
  if (!source.documentId) {
    ElMessage.warning('该引用没有关联到具体文档，无法预览')
    return
  }
  void loadPreview(source.documentId, source.snippet)
}

/** 文档管理列表里预览原件 */
function openDocumentPreview(row: KbDocument) {
  void loadPreview(row.id)
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

/** 新窗口打开原件（地址 10 分钟内有效） */
function openPreviewInNewTab() {
  if (!preview.value) return
  window.open(preview.value.url, '_blank', 'noopener')
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

function formatScore(score: number) {
  return typeof score === 'number' ? score.toFixed(3) : '-'
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
            <el-upload multiple :show-file-list="false" :http-request="handleUpload" :disabled="uploading">
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

          <p class="hint">
            可一次选择多个文件；同名文件内容一致不会重复入库，内容有变化会生成新版本。未指定可见角色时默认所有用户可见。
          </p>

          <el-table
            v-loading="loadingDocs"
            :data="documents"
            height="420"
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
            <el-table-column label="操作" width="225" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" type="primary" @click="openDocumentPreview(row)">预览</el-button>
                <el-button text size="small" type="primary" @click="saveDocument(row.id)">下载</el-button>
                <el-button text size="small" @click="handleReindex(row)">重新入库</el-button>
                <el-button text size="small" type="danger" @click="handleDeleteDocument(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="智能问答" name="ask">
          <div class="ask">
            <div class="ask-main">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="向知识库提问，例如：这份文档的主要结论是什么？"
                @keydown.ctrl.enter="handleAsk"
              />
              <div class="ask-actions">
                <span class="ask-tip">Ctrl + Enter 快速提问 · 回答仅基于你有权限查看的最新文档</span>
                <el-button type="primary" :loading="asking" :disabled="!question.trim()" @click="handleAsk">
                  <el-icon><Promotion /></el-icon>
                  <span>提问</span>
                </el-button>
              </div>

              <div v-if="answer" class="answer">
                <div class="answer-block">
                  <div class="answer-label">回答</div>
                  <div class="answer-text">{{ answer.answer }}</div>
                </div>

                <div v-if="answer.sources.length > 0" class="sources">
                  <div class="answer-label">
                    引用来源（{{ answer.sources.length }}）· 仅引用各文档的最新版本，点击可预览原文
                  </div>
                  <el-card
                    v-for="(source, index) in answer.sources"
                    :key="index"
                    shadow="never"
                    class="source-item"
                    :class="{ clickable: !!source.documentId }"
                    @click="openPreview(source)"
                  >
                    <div class="source-head">
                      <div class="source-title">
                        <span class="source-index">{{ index + 1 }}</span>
                        <span class="source-file">{{ source.fileName || '未知文档' }}</span>
                        <el-tag v-if="source.version" size="small" effect="plain">v{{ source.version }}</el-tag>
                      </div>
                      <el-tag size="small" type="info" effect="plain">相似度 {{ formatScore(source.score) }}</el-tag>
                    </div>
                    <div class="source-snippet">{{ source.snippet }}</div>
                  </el-card>
                </div>
              </div>

              <div v-else class="ask-empty">
                <el-empty description="输入问题后点击「提问」" :image-size="72" />
              </div>
            </div>

            <aside class="history">
              <div class="history-head">
                <span class="history-title">历史问答</span>
                <span class="history-count">{{ history.length }} 条</span>
                <el-button
                  text
                  size="small"
                  type="danger"
                  :disabled="history.length === 0"
                  @click="handleClearHistory"
                >
                  <el-icon><Delete /></el-icon>
                  <span>清空</span>
                </el-button>
              </div>
              <div v-loading="loadingHistory" class="history-body">
                <el-empty v-if="history.length === 0" description="暂无历史问答" :image-size="56" />
                <div
                  v-for="item in history"
                  :key="item.id"
                  class="history-item"
                  @click="restoreHistory(item)"
                >
                  <div class="history-question">{{ item.question }}</div>
                  <div class="history-meta">
                    <span>{{ item.createTime }}</span>
                    <span>引用 {{ item.sources?.length || 0 }} 条</span>
                  </div>
                </div>
              </div>
            </aside>
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
          <div v-else class="preview-fallback">
            <el-alert
              type="info"
              show-icon
              :closable="false"
              title="该格式无法在浏览器内直接预览，可下载原件后查看"
            />
            <div v-if="previewSnippet" class="preview-snippet">{{ previewSnippet }}</div>
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.hint {
  margin: 0 0 12px;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.6;
}

.role-select {
  width: 260px;
}

.doc-tag {
  margin-left: 6px;
}

/* 历史版本整行置灰 */
:deep(.history-row) {
  color: var(--text-muted);
}

/* 问答：左侧问答主区 + 右侧历史侧栏 */
.ask {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 264px;
  gap: 18px;
  align-items: start;
}

.ask-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.ask-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ask-tip {
  color: var(--text-faint);
  font-size: 12px;
}

.ask-empty {
  padding: 10px 0;
}

.answer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.answer-label {
  margin-bottom: 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.answer-text {
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface-sunken);
  color: var(--text);
  font-size: 13.5px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.sources {
  display: flex;
  flex-direction: column;
}

.source-item {
  margin-bottom: 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.source-item.clickable {
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.source-item.clickable:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
}

.source-item :deep(.el-card__body) {
  padding: 12px 14px;
}

.source-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.source-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.source-index {
  flex: none;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: var(--r-xs);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11px;
  font-weight: 650;
}

.source-file {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.source-snippet {
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.65;
}

/* 智能问答历史侧栏 */
.history {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface-sunken);
}

.history-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 650;
}

.history-count {
  flex: 1;
  color: var(--text-faint);
  font-size: 12px;
}

.history-body {
  max-height: 480px;
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

.history-question {
  color: var(--text);
  font-size: 12.5px;
  line-height: 1.6;
  word-break: break-word;
}

.history-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  color: var(--text-faint);
  font-size: 11.5px;
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

.preview-fallback {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-snippet {
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--surface-sunken);
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-meta {
  margin-right: 12px;
  color: var(--text-faint);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .ask {
    grid-template-columns: minmax(0, 1fr);
  }

  .history-body {
    max-height: 300px;
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

  .role-select {
    width: 100%;
  }

  .hint {
    margin-bottom: 10px;
  }
}
</style>
