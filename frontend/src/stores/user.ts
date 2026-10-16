import { defineStore } from 'pinia'
import { ref } from 'vue'

import { createUser, deleteUser, fetchUsers } from '@/api/user'
import type { User, UserPayload } from '@/types'

export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load(keyword?: string) {
    loading.value = true
    error.value = ''
    try {
      users.value = await fetchUsers(keyword)
    } catch (e) {
      error.value = (e as Error).message
      users.value = []
    } finally {
      loading.value = false
    }
  }

  async function add(payload: UserPayload) {
    await createUser(payload)
    await load()
  }

  async function remove(id: number) {
    await deleteUser(id)
    await load()
  }

  return { users, loading, error, load, add, remove }
})
