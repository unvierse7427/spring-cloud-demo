<script setup lang="ts">
import { ElInput, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ComponentPublicInstance } from 'vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { createChatRoom, fetchChatRooms } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import type { ChatMessage, ChatRoom } from '@/types'
import { getAccessToken } from '@/utils/token'

/** 服务端下发的 WebSocket 帧，字段按 type 不同而不同 */
interface WsInbound {
  type: string
  roomId?: number
  onlineCount?: number
  messages?: ChatMessage[]
  message?: ChatMessage
  content?: string
}

type ConnStatus = 'connected' | 'connecting' | 'disconnected'

/** 断线后最多重连的次数 */
const MAX_RECONNECT = 5
/** 心跳间隔（毫秒） */
const HEARTBEAT_INTERVAL = 30000
/** 重连退避间隔（毫秒） */
const RECONNECT_DELAY = 3000

/** 表情面板可选表情 */
const EMOJIS = [
  '😀', '😄', '😁', '😆', '😅', '😂', '🤣', '😊',
  '😇', '🙂', '😉', '😍', '🥰', '😘', '😜', '🤪',
  '🤗', '🤔', '🤨', '😐', '😴', '😢', '😭', '😤',
  '😡', '🥺', '😱', '🤯', '😳', '🥳', '😎', '🤓',
  '🧐', '🤝', '👍', '👎', '👏', '🙏', '🙌', '💪',
  '🔥', '✨', '🎉', '🎁', '❤️', '💔', '💯', '✅',
  '❌', '⭐', '🌟', '☀️', '🌈', '🍀', '☕', '🍺',
  '🍰', '🍎', '⚽', '🚀', '💡', '📌', '🔔', '💬'
]

const auth = useAuthStore()

const rooms = ref<ChatRoom[]>([])
const currentRoom = ref<ChatRoom | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const loading = ref(false)
const connected = ref(false)
const status = ref<ConnStatus>('disconnected')
const scrollRef = ref<ComponentPublicInstance | null>(null)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '' })
const rules: FormRules = {
  name: [
    { required: true, message: '请输入房间名称', trigger: 'blur' },
    { min: 2, max: 32, message: '长度在 2 到 32 个字符', trigger: 'blur' }
  ],
  description: [{ max: 100, message: '最多 100 个字符', trigger: 'blur' }]
}

const statusType = computed<'success' | 'warning' | 'danger'>(() => {
  if (status.value === 'connected') return 'success'
  if (status.value === 'connecting') return 'warning'
  return 'danger'
})

const statusText = computed(() => {
  if (status.value === 'connected') return '已连接'
  if (status.value === 'connecting') return '连接中'
  return '已断开'
})

let socket: WebSocket | null = null
let heartbeatTimer: number | null = null
let reconnectTimer: number | null = null
let reconnectCount = 0
let manualClose = false

onMounted(async () => {
  await loadRooms()
  if (rooms.value.length > 0) {
    selectRoom(rooms.value[0])
  }
})

onBeforeUnmount(() => {
  stopHeartbeat()
  clearReconnect()
  manualClose = true
  closeSocket()
})

async function loadRooms() {
  loading.value = true
  try {
    rooms.value = await fetchChatRooms()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function connect() {
  manualClose = false
  closeSocket()
  status.value = 'connecting'

  const ws = new WebSocket(`${import.meta.env.VITE_WS_BASE_URL}?token=${getAccessToken()}`)
  socket = ws

  ws.onopen = () => {
    connected.value = true
    status.value = 'connected'
    reconnectCount = 0
    startHeartbeat()
    if (currentRoom.value) {
      sendJoin(currentRoom.value.id)
    }
  }

  ws.onmessage = (event: MessageEvent) => handleInbound(event.data)

  ws.onclose = () => {
    connected.value = false
    status.value = 'disconnected'
    stopHeartbeat()
    if (manualClose) return
    scheduleReconnect()
  }

  ws.onerror = () => {
    connected.value = false
    status.value = 'disconnected'
  }
}

function closeSocket() {
  if (!socket) return
  socket.onopen = null
  socket.onmessage = null
  socket.onclose = null
  socket.onerror = null
  socket.close()
  socket = null
}

function handleInbound(raw: unknown) {
  if (typeof raw !== 'string') return
  let data: WsInbound
  try {
    data = JSON.parse(raw) as WsInbound
  } catch {
    return
  }

  switch (data.type) {
    case 'JOINED':
      messages.value = data.messages ?? []
      applyOnlineCount(data.roomId, data.onlineCount)
      scrollToBottom()
      break
    case 'CHAT':
    case 'SYSTEM':
      if (data.message) {
        messages.value.push(data.message)
      }
      applyOnlineCount(data.roomId, data.onlineCount)
      scrollToBottom()
      break
    case 'LEFT':
      applyOnlineCount(data.roomId, data.onlineCount)
      break
    case 'ERROR':
      ElMessage.error(data.content || '聊天服务返回错误')
      break
    case 'PONG':
      break
    default:
      break
  }
}

function applyOnlineCount(roomId: number | undefined, onlineCount: number | undefined) {
  if (typeof roomId !== 'number' || typeof onlineCount !== 'number') return
  const room = rooms.value.find((item) => item.id === roomId)
  if (room) {
    room.onlineCount = onlineCount
  }
  if (currentRoom.value?.id === roomId) {
    currentRoom.value.onlineCount = onlineCount
  }
}

function scheduleReconnect() {
  if (reconnectCount >= MAX_RECONNECT) {
    ElMessage.warning('聊天连接已断开，请手动切换房间重试')
    return
  }
  reconnectCount += 1
  clearReconnect()
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connect()
  }, RECONNECT_DELAY)
}

function clearReconnect() {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'PING' }))
    }
  }, HEARTBEAT_INTERVAL)
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function sendJoin(roomId: number) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'JOIN', roomId }))
  }
}

function selectRoom(room: ChatRoom) {
  if (currentRoom.value?.id === room.id) return

  const previous = currentRoom.value
  if (previous && socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'LEAVE', roomId: previous.id }))
  }

  currentRoom.value = room
  messages.value = []

  if (socket && socket.readyState === WebSocket.OPEN) {
    sendJoin(room.id)
  } else if (!socket || socket.readyState === WebSocket.CLOSED) {
    // 连接不存在或已关闭时（含重连次数耗尽的场景）重新建立连接，onopen 会自动 JOIN 当前房间
    reconnectCount = 0
    connect()
  }
}

function sendMessage() {
  const content = input.value.trim()
  if (!content || !currentRoom.value) return
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    ElMessage.warning('连接尚未就绪，请稍后重试')
    return
  }
  socket.send(JSON.stringify({ type: 'CHAT', roomId: currentRoom.value.id, content }))
  input.value = ''
}

function handleEnter(event: Event | KeyboardEvent) {
  if (event instanceof KeyboardEvent && event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

/** 把表情插入到输入框光标处，并保持焦点与光标位置 */
function insertEmoji(emoji: string) {
  const textarea = inputRef.value?.textarea
  if (!textarea) {
    input.value += emoji
    return
  }

  const start = textarea.selectionStart ?? input.value.length
  const end = textarea.selectionEnd ?? start
  input.value = input.value.slice(0, start) + emoji + input.value.slice(end)

  void nextTick(() => {
    textarea.focus()
    const caret = start + emoji.length
    textarea.setSelectionRange(caret, caret)
  })
}

function scrollToBottom() {
  void nextTick(() => {
    const root = (scrollRef.value?.$el ?? null) as HTMLElement | null
    const wrap = root?.querySelector<HTMLElement>('.el-scrollbar__wrap')
    if (wrap) {
      wrap.scrollTop = wrap.scrollHeight
    }
  })
}

function isSelf(message: ChatMessage) {
  return message.username === auth.username
}

/** 后端返回的时间形如 "2026-10-17 10:20:30"，仅保留 HH:mm */
function formatTime(value: string) {
  const time = value.replace('T', ' ').slice(11, 16)
  return time || value
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
    const room = await createChatRoom({ name: form.name, description: form.description || undefined })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    await loadRooms()
    const target = rooms.value.find((item) => item.id === room.id) ?? room
    currentRoom.value = null
    selectRoom(target)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="chat">
    <el-card class="rooms-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">聊天室</span>
          <span class="card-count">{{ rooms.length }} 个</span>
          <el-button type="primary" size="small" @click="openDialog">
            <el-icon><Plus /></el-icon>
            <span>创建</span>
          </el-button>
        </div>
      </template>

      <el-scrollbar v-loading="loading" class="rooms-scroll">
        <el-empty v-if="rooms.length === 0" description="暂无聊天室" :image-size="64" />
        <div
          v-for="room in rooms"
          :key="room.id"
          class="room-item"
          :class="{ active: currentRoom?.id === room.id }"
          @click="selectRoom(room)"
        >
          <span class="room-initial">{{ room.name.charAt(0) }}</span>
          <div class="room-main">
            <span class="room-name">{{ room.name }}</span>
            <span class="room-owner">{{ room.ownerName }}</span>
          </div>
          <span class="room-online">
            <span class="presence" />
            {{ room.onlineCount }}
          </span>
        </div>
      </el-scrollbar>
    </el-card>

    <el-card class="chat-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="chat-title">
            <span class="chat-room-name">{{ currentRoom ? currentRoom.name : '聊天室' }}</span>
            <el-tag :type="statusType" size="small" effect="light">{{ statusText }}</el-tag>
          </div>
          <span v-if="currentRoom" class="online">在线 {{ currentRoom.onlineCount }} 人</span>
        </div>
      </template>

      <el-empty v-if="!currentRoom" description="请选择或创建一个聊天室" />

      <template v-else>
        <el-scrollbar ref="scrollRef" class="messages">
          <div class="message-list">
            <el-empty v-if="messages.length === 0" description="还没有消息，打个招呼吧" :image-size="64" />
            <template v-for="msg in messages" :key="msg.id">
              <div v-if="msg.msgType === 'SYSTEM'" class="msg-system">{{ msg.content }}</div>
              <div v-else class="msg-row" :class="{ 'msg-self': isSelf(msg) }">
                <div class="msg-meta">
                  <template v-if="!isSelf(msg)">{{ msg.username }} · </template>{{ formatTime(msg.createTime) }}
                </div>
                <div class="bubble" :class="{ 'bubble-self': isSelf(msg) }">{{ msg.content }}</div>
              </div>
            </template>
          </div>
        </el-scrollbar>

        <div class="composer">
          <el-popover trigger="click" placement="top-start" :width="312" :show-arrow="false">
            <template #reference>
              <el-button class="emoji-trigger" text title="插入表情" :disabled="!connected">
                <span class="emoji-trigger-icon">😀</span>
              </el-button>
            </template>
            <div class="emoji-panel">
              <button
                v-for="emoji in EMOJIS"
                :key="emoji"
                type="button"
                class="emoji-item"
                @click="insertEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>
          </el-popover>
          <el-input
            ref="inputRef"
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="回车发送，Shift + 回车换行"
            @keydown.enter="handleEnter"
          />
          <el-button type="primary" :disabled="!connected || !currentRoom" @click="sendMessage">发送</el-button>
        </div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建聊天室" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="房间名称" prop="name">
          <el-input v-model="form.name" maxlength="32" placeholder="2 - 32 个字符" />
        </el-form-item>
        <el-form-item label="房间简介" prop="description">
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
  </div>
</template>

<style scoped>
.chat {
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

/* 左侧房间列表 */
.rooms-card {
  display: flex;
  flex-direction: column;
  flex: 0 0 264px;
  width: 264px;
}

.rooms-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 8px;
}

.rooms-scroll {
  height: 100%;
}

.room-item {
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

.room-item:hover {
  background: var(--surface-hover);
}

.room-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.room-initial {
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

.room-item.active .room-initial {
  background: var(--primary);
  color: #fff;
}

.room-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.room-name {
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-owner {
  color: var(--text-muted);
  font-size: 11.5px;
}

.room-online {
  flex: none;
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.presence {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #34d399;
}

/* 右侧聊天区 */
.chat-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.chat-card :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 0;
}

.chat-title {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chat-room-name {
  color: var(--text);
  font-size: 15px;
  font-weight: 650;
}

.online {
  color: var(--text-faint);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.messages {
  flex: 1;
  min-height: 300px;
  background: var(--surface-sunken);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.msg-system {
  align-self: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-faint);
  font-size: 11.5px;
  text-align: center;
}

.msg-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 72%;
}

.msg-row.msg-self {
  align-self: flex-end;
  align-items: flex-end;
}

.msg-meta {
  margin-bottom: 4px;
  color: var(--text-faint);
  font-size: 11.5px;
}

.bubble {
  padding: 9px 13px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg) var(--r-lg) var(--r-lg) var(--r-xs);
  background: var(--surface);
  color: var(--text-regular);
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble-self {
  border-color: transparent;
  border-radius: var(--r-lg) var(--r-lg) var(--r-xs) var(--r-lg);
  background: var(--primary);
  color: #ffffff;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.composer :deep(.el-textarea) {
  flex: 1;
}

/* 表情选择 */
.emoji-trigger {
  height: 32px;
  padding: 0 6px;
}

.emoji-trigger-icon {
  font-size: 18px;
  line-height: 1;
}

.emoji-panel {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  max-height: 208px;
  overflow-y: auto;
}

.emoji-item {
  padding: 4px 0;
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  font-size: 18px;
  line-height: 1.3;
  cursor: pointer;
}

.emoji-item:hover {
  background: var(--surface-hover);
}

@media (max-width: 960px) {
  .chat {
    flex-direction: column;
    height: auto;
  }

  .rooms-card {
    flex: none;
    width: 100%;
  }

  .rooms-card :deep(.el-card__body) {
    max-height: 240px;
  }

  .chat-card {
    min-height: 460px;
  }
}
</style>
