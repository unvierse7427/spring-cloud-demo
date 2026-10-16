<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchPermissions } from '@/api/admin'
import type { Permission } from '@/types'

const permissions = ref<Permission[]>([])
const loading = ref(false)

const total = computed(() => permissions.value.length)

onMounted(async () => {
  loading.value = true
  try {
    permissions.value = await fetchPermissions()
  } catch (e) {
    ElMessage.error((e as Error).message)
    permissions.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <p class="hint">共 {{ total }} 项，权限通过角色授予用户</p>

    <el-table v-loading="loading" :data="permissions" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="权限码" min-width="180">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.permCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="permName" label="权限名称" min-width="140" />
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
    </el-table>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: #909399;
  font-size: 13px;
}
</style>
