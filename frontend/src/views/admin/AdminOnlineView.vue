<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchOnlineUsers } from '@/api/admin'
import type { OnlineUser } from '@/types'

/** Redis 中在线状态的 TTL，与后端 cloud.online.timeout 保持一致 */
const ONLINE_TIMEOUT_MINUTES = 30

const users = ref<OnlineUser[]>([])
const loading = ref(false)

const onlineCount = computed(() => users.value.length)

async function load() {
  loading.value = true
  try {
    users.value = await fetchOnlineUsers()
  } catch (e) {
    ElMessage.error((e as Error).message)
    users.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-tag type="info" effect="plain">当前在线 {{ onlineCount }} 人</el-tag>
      <el-button :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>
        <span>刷新</span>
      </el-button>
    </div>

    <p class="hint">
      数据来自 Redis：登录后写入
      <code>auth:online:user:{userId}</code>，超过 {{ ONLINE_TIMEOUT_MINUTES }}
      分钟无接口调用自动过期；登出立即移除。
    </p>

    <el-table v-loading="loading" :data="users">
      <el-table-column prop="userId" label="用户 ID" width="100" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="客户端 IP" min-width="140">
        <template #default="{ row }">{{ row.clientIp || '—' }}</template>
      </el-table-column>
      <el-table-column label="User-Agent" min-width="240">
        <template #default="{ row }">{{ row.userAgent || '—' }}</template>
      </el-table-column>
      <el-table-column label="登录时间" width="180">
        <template #default="{ row }">{{ row.loginTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="最后活跃时间" width="180">
        <template #default="{ row }">{{ row.lastActiveTime || '—' }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.hint {
  margin: 0 0 14px;
  color: var(--text-muted);
  font-size: 12.5px;
}

.hint code {
  padding: 1px 4px;
  border-radius: var(--r-xs);
  background: var(--surface-sunken);
}
</style>
