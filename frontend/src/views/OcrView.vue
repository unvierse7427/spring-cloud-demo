<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { computed, onBeforeUnmount, ref } from 'vue'

import { recognizeImage } from '@/api/rag'
import type { OcrText } from '@/types'

/** 单张图片体积上限，与后端 cloud.rag.ocr.max-image-mb 保持一致 */
const MAX_IMAGE_MB = 10
const MAX_IMAGE_BYTES = MAX_IMAGE_MB * 1024 * 1024

/** 后端允许的图片格式 */
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'bmp', 'webp']

const recognizing = ref(false)
const currentFile = ref<File | null>(null)
/** 本地生成的预览地址，替换或卸载时需要回收，避免内存泄漏 */
const previewUrl = ref('')
const result = ref<OcrText | null>(null)
/** 结果展示方式：识别出表格时可切到 Markdown 视图查看行列结构 */
const viewMode = ref<string>('text')

const scoreText = computed(() =>
  result.value ? `${(result.value.avgScore * 100).toFixed(1)}%` : '-'
)
const elapsedText = computed(() => (result.value ? `${result.value.elapsedMs} ms` : '-'))
/** 识别出表格时把 Markdown 作为默认视图，扁平文本会丢掉行列对齐关系 */
const displayText = computed(() => {
  if (!result.value) return ''
  return viewMode.value === 'markdown' ? result.value.markdown : result.value.text
})

function releasePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

onBeforeUnmount(releasePreview)

function fileExtension(name: string) {
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
}

/** 客户端前置校验，避免把明显不合规的图片发给后端 */
function validate(file: File) {
  if (!ALLOWED_EXTENSIONS.includes(fileExtension(file.name))) {
    ElMessage.warning('仅支持 jpg / jpeg / png / bmp / webp 格式的图片')
    return false
  }
  if (file.size > MAX_IMAGE_BYTES) {
    ElMessage.warning(
      `图片体积 ${(file.size / 1024 / 1024).toFixed(2)}MB 超出上限 ${MAX_IMAGE_MB}MB`
    )
    return false
  }
  return true
}

/** el-upload 回调：只做识别，图片不写存储、不入库、不向量化 */
async function handleUpload(options: UploadRequestOptions) {
  const file = options.file as File
  if (!validate(file)) return

  releasePreview()
  currentFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  result.value = null

  recognizing.value = true
  try {
    result.value = await recognizeImage(file)
    // 识别出表格时默认展示 Markdown，可直接看到行列结构
    viewMode.value = result.value.tableDetected ? 'markdown' : 'text'
    if (!result.value.text) {
      ElMessage.info('未在图片中识别到文字')
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    recognizing.value = false
  }
}

/** 复制当前视图的内容：表格视图下复制的是 Markdown */
async function copyText() {
  const text = displayText.value
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制识别结果')
  } catch {
    ElMessage.warning('当前浏览器不支持自动复制，请手动选中文本复制')
  }
}

function reset() {
  releasePreview()
  currentFile.value = null
  result.value = null
  viewMode.value = 'text'
}
</script>

<template>
  <div class="ocr">
    <el-card class="upload-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">上传图片</span>
          <el-tag size="small" type="info" effect="plain">图片不会被保存</el-tag>
        </div>
      </template>

      <el-upload
        drag
        :show-file-list="false"
        :http-request="handleUpload"
        :disabled="recognizing"
        accept=".jpg,.jpeg,.png,.bmp,.webp"
        class="drop"
      >
        <el-icon class="drop-icon"><UploadFilled /></el-icon>
        <div class="drop-text">将图片拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <p class="hint">
            支持 jpg / jpeg / png / bmp / webp，单张不超过 {{ MAX_IMAGE_MB }}MB。
            识别在服务端完成，图片即用即弃，不写入存储，也不进入知识库。
          </p>
        </template>
      </el-upload>

      <div v-if="previewUrl" class="thumb">
        <img :src="previewUrl" alt="待识别图片预览" />
        <div class="thumb-meta">
          <span class="thumb-name">{{ currentFile?.name }}</span>
          <el-button text size="small" :disabled="recognizing" @click="reset">清除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="result-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">识别结果</span>

          <el-tag v-if="result?.tableDetected" size="small" type="success" effect="plain">
            表格 {{ result.tableRows }} 行 × {{ result.tableCols }} 列
          </el-tag>

          <div v-if="result" class="stats">
            <span class="stat">行数 <b>{{ result.lineCount }}</b></span>
            <span class="stat">平均置信度 <b>{{ scoreText }}</b></span>
            <span class="stat">耗时 <b>{{ elapsedText }}</b></span>
          </div>

          <el-radio-group v-if="result?.tableDetected" v-model="viewMode" size="small">
            <el-radio-button value="markdown">Markdown</el-radio-button>
            <el-radio-button value="text">纯文本</el-radio-button>
          </el-radio-group>

          <el-button text size="small" :disabled="!displayText" @click="copyText">
            <el-icon><CopyDocument /></el-icon>
            <span>复制</span>
          </el-button>
        </div>
      </template>

      <div v-loading="recognizing" class="result-body">
        <el-empty
          v-if="!result"
          :description="recognizing ? '正在识别，请稍候…' : '上传图片后在这里查看识别出的文字'"
          :image-size="72"
        />
        <pre v-else class="result-text">{{ displayText || '未识别到文字' }}</pre>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.ocr {
  display: grid;
  grid-template-columns: minmax(300px, 400px) 1fr;
  gap: 18px;
  height: 100%;
  min-height: 520px;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
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

/* 上传区 */
.upload-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drop :deep(.el-upload-dragger) {
  padding: 26px 16px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-lg);
  background: var(--surface-sunken);
  transition: border-color 0.15s ease, background 0.15s ease;
}

.drop :deep(.el-upload-dragger:hover) {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.drop-icon {
  color: var(--primary);
  font-size: 34px;
}

.drop-text {
  margin-top: 10px;
  color: var(--text-regular);
  font-size: 13px;
}

.drop-text em {
  color: var(--primary);
  font-style: normal;
  font-weight: 600;
}

.hint {
  margin: 10px 0 0;
  color: var(--text-faint);
  font-size: 12px;
  line-height: 1.6;
}

/* 本地缩略图：仅在浏览器内存中，用于确认选中的图片 */
.thumb {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.thumb img {
  width: 100%;
  max-height: 220px;
  object-fit: contain;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-sunken);
}

.thumb-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.thumb-name {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 结果区 */
.result-card :deep(.el-card__body) {
  height: calc(100% - 54px);
}

.stats {
  display: flex;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.stat b {
  color: var(--text);
  font-weight: 600;
}

.result-body {
  height: 100%;
  min-height: 260px;
}

.result-text {
  height: 100%;
  margin: 0;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-sunken);
  color: var(--text);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .ocr {
    grid-template-columns: 1fr;
    min-height: 0;
  }
}
</style>
