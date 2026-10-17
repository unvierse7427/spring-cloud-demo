<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function switchAccount() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <el-result icon="warning" title="403 无访问权限" sub-title="当前账号未被授予该页面的权限，请联系管理员分配角色">
        <template #extra>
          <el-space>
            <el-button type="primary" @click="router.replace('/')">返回总览</el-button>
            <el-button @click="switchAccount">切换账号</el-button>
          </el-space>
        </template>
      </el-result>
    </el-card>

    <el-card shadow="never" class="info">
      <template #header>当前账号权限</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ auth.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="role in auth.roles" :key="role" class="tag">{{ role }}</el-tag>
          <span v-if="auth.roles.length === 0">—</span>
        </el-descriptions-item>
        <el-descriptions-item label="权限码">
          <el-tag v-for="code in auth.permissionCodes" :key="code" type="info" class="tag">{{ code }}</el-tag>
          <span v-if="auth.permissionCodes.length === 0">—</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 640px;
  margin: 0 auto;
}

.panel :deep(.el-result) {
  padding: 24px 20px;
}

.panel :deep(.el-result__title p) {
  color: var(--text);
  font-size: 18px;
  font-weight: 650;
}

.tag {
  margin: 0 6px 6px 0;
}
</style>
