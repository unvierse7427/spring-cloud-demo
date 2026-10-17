import request from './request'
import type { ChatMessage, ChatRoom } from '@/types'

/** GET /api/chat/rooms -> 房间列表（含在线人数） */
export function fetchChatRooms() {
  return request<ChatRoom[]>({ url: '/chat/rooms', method: 'get' })
}

/** POST /api/chat/rooms -> 创建房间 */
export function createChatRoom(data: { name: string; description?: string }) {
  return request<ChatRoom>({ url: '/chat/rooms', method: 'post', data })
}

/** GET /api/chat/rooms/{roomId}/messages -> 历史消息，按时间正序 */
export function fetchRoomMessages(roomId: number, limit = 50) {
  return request<ChatMessage[]>({
    url: `/chat/rooms/${roomId}/messages`,
    method: 'get',
    params: { limit }
  })
}
