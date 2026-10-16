import request from './request'
import type { User, UserPayload } from '@/types'

/** GET /api/user/list -> cloud-user-service /user/list */
export function fetchUsers(keyword?: string) {
  return request<User[]>({
    url: '/user/list',
    method: 'get',
    params: keyword ? { keyword } : undefined
  })
}

/** GET /api/user/{id} */
export function fetchUser(id: number) {
  return request<User>({ url: `/user/${id}`, method: 'get' })
}

/** POST /api/user */
export function createUser(data: UserPayload) {
  return request<User>({ url: '/user', method: 'post', data })
}

/** DELETE /api/user/{id} */
export function deleteUser(id: number) {
  return request<void>({ url: `/user/${id}`, method: 'delete' })
}
