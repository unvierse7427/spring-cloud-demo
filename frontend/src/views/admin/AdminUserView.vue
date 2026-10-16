<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  assignUserRoles,
  fetchRoles,
  fetchUserRoleIds,
  pageUsers,
  updateUserStatus
} from '@/api/admin'
import type { Role, User } from '@/types'

/** 内置管理员账号（id 为 1），后端不允许禁用 */
const ADMIN_USER_ID = 1

const users = ref<User[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', status: '' as number | '', page: 1, size: 10 })
const statusUpdating = ref<number | null>(null)

const roles = ref<Role[]>([])

const roleDrawerVisible = ref(false)
const roleTarget = ref<User | null>(null)
const selectedRoleIds = ref<number[]>([])
const rolesLoading = ref(false)
const savingRoles = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await pageUsers({ ...query })
    users.value = result.records
    total.value = result.total
  } catch (e) {
    ElMessage.error((e as Error).message)
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roles.value = await fetchRoles()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => {
  load()
  loadRoles()
})

function search() {
  query.page = 1
  load()
}

function reset() {
  query.keyword = ''
  query.status = ''
  query.page = 1
  load()
}

function handlePageChange(page: number) {
  query.page = page
  load()
}

function onStatusChange(user: User, value: string | number | boolean) {
  toggleStatus(user, Number(value))
}

async function toggleStatus(user: User, next: number) {
  statusUpdating.value = user.id
  try {
    await updateUserStatus(user.id, next)
    ElMessage.success(`用户 ${user.username} 已${next === 1 ? '启用' : '禁用'}`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    statusUpdating.value = null
  }
}

async function openRoleDrawer(user: User) {
  roleTarget.value = user
  selectedRoleIds.value = []
  roleDrawerVisible.value = true
  rolesLoading.value = true
  try {
    selectedRoleIds.value = await fetchUserRoleIds(user.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    rolesLoading.value = false
  }
}

async function saveRoles() {
  if (!roleTarget.value) return
  savingRoles.value = true
  try {
    await assignUserRoles(roleTarget.value.id, [...selectedRoleIds.value])
    ElMessage.success(`用户 ${roleTarget.value.username} 的角色已更新`)
    roleDrawerVisible.value = false
    roleTarget.value = null
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    savingRoles.value = false
  }
}
</script>

<template>
  <div>
    <el-form :inline="true">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名 / 昵称 / 邮箱"
          clearable
          style="width: 240px"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" style="width: 140px">
          <el-option label="全部状态" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="users" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="邮箱" min-width="180">
        <template #default="{ row }">{{ row.email || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.id === ADMIN_USER_ID"
            content="内置管理员账号不允许禁用"
            placement="top"
          >
            <el-switch :model-value="row.status" :active-value="1" :inactive-value="0" disabled />
          </el-tooltip>
          <el-switch
            v-else
            :model-value="row.status"
            :active-value="1"
            :inactive-value="0"
            :loading="statusUpdating === row.id"
            @change="onStatusChange(row, $event)"
          />
          <el-button type="primary" link @click="openRoleDrawer(row)">分配角色</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="handlePageChange"
    />

    <el-drawer
      v-model="roleDrawerVisible"
      :title="roleTarget ? `为「${roleTarget.username}」分配角色` : '分配角色'"
      size="380px"
    >
      <p class="hint">勾选后保存将整体覆盖该用户现有角色。</p>
      <div v-loading="rolesLoading" class="role-list">
        <el-checkbox-group v-model="selectedRoleIds">
          <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
            {{ role.roleName }}（{{ role.roleCode }}）
          </el-checkbox>
        </el-checkbox-group>
        <el-empty v-if="!rolesLoading && roles.length === 0" description="暂无可分配角色" />
      </div>
      <template #footer>
        <el-button @click="roleDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="saveRoles">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.hint {
  margin: 0 0 12px;
  color: #909399;
  font-size: 12px;
}

.role-list {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}

:deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
}
</style>
