<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'

import {
  assignRolePermissions,
  createRole,
  deleteRole,
  fetchPermissions,
  fetchRolePermissionIds,
  fetchRoles,
  updateRole
} from '@/api/admin'
import type { Permission, Role, RolePayload } from '@/types'

/** 内置超级管理员角色，后端不允许删除 */
const BUILTIN_ROLE_CODE = 'ROLE_ADMIN'

const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RolePayload>({ roleCode: '', roleName: '', description: '' })
const saving = ref(false)

const rules: FormRules<RolePayload> = {
  roleCode: [{ required: true, message: '请输入角色标识（如 ROLE_EDITOR）', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const permissionDrawerVisible = ref(false)
const permissionTarget = ref<Role | null>(null)
const selectedPermissionIds = ref<number[]>([])
const permissionsLoading = ref(false)
const savingPermissions = ref(false)

async function load() {
  loading.value = true
  try {
    roles.value = await fetchRoles()
  } catch (e) {
    ElMessage.error((e as Error).message)
    roles.value = []
  } finally {
    loading.value = false
  }
}

async function loadPermissions() {
  try {
    permissions.value = await fetchPermissions()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => {
  load()
  loadPermissions()
})

function openCreate() {
  editingId.value = null
  form.roleCode = ''
  form.roleName = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(role: Role) {
  editingId.value = role.id
  form.roleCode = role.roleCode
  form.roleName = role.roleName
  form.description = role.description ?? ''
  dialogVisible.value = true
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload: RolePayload = {
      roleCode: form.roleCode.trim(),
      roleName: form.roleName.trim(),
      description: form.description ?? ''
    }
    if (editingId.value === null) {
      await createRole(payload)
      ElMessage.success('角色新增成功')
    } else {
      await updateRole(editingId.value, payload)
      ElMessage.success('角色修改成功')
    }
    dialogVisible.value = false
    editingId.value = null
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(role: Role) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${role.roleName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteRole(role.id)
    ElMessage.success(`角色 ${role.roleName} 已删除`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function openPermissionDrawer(role: Role) {
  permissionTarget.value = role
  selectedPermissionIds.value = []
  permissionDrawerVisible.value = true
  permissionsLoading.value = true
  try {
    selectedPermissionIds.value = await fetchRolePermissionIds(role.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    permissionsLoading.value = false
  }
}

async function savePermissions() {
  if (!permissionTarget.value) return
  savingPermissions.value = true
  try {
    await assignRolePermissions(permissionTarget.value.id, [...selectedPermissionIds.value])
    ElMessage.success(`角色 ${permissionTarget.value.roleName} 的权限已更新`)
    permissionDrawerVisible.value = false
    permissionTarget.value = null
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    savingPermissions.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roles">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="角色编码" min-width="160">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.roleCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column label="描述" min-width="180">
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button type="primary" link @click="openPermissionDrawer(row)">分配权限</el-button>
          <el-tooltip
            v-if="row.roleCode === BUILTIN_ROLE_CODE"
            content="内置超级管理员角色不允许删除"
            placement="top"
          >
            <el-button type="danger" link disabled>删除</el-button>
          </el-tooltip>
          <el-button v-else type="danger" link @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增角色' : `编辑角色 #${editingId}`"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="角色标识" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 ROLE_EDITOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="permissionDrawerVisible"
      :title="permissionTarget ? `为「${permissionTarget.roleName}」分配权限` : '分配权限'"
      size="420px"
    >
      <p class="hint">勾选后保存将整体覆盖该角色现有权限。</p>
      <div v-loading="permissionsLoading" class="perm-list">
        <el-checkbox-group v-model="selectedPermissionIds">
          <el-checkbox
            v-for="permission in permissions"
            :key="permission.id"
            :value="permission.id"
            class="perm-item"
          >
            <span>{{ permission.permCode }} · {{ permission.permName }}</span>
            <small v-if="permission.description">{{ permission.description }}</small>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty v-if="!permissionsLoading && permissions.length === 0" description="暂无权限数据" />
      </div>
      <template #footer>
        <el-button @click="permissionDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPermissions" @click="savePermissions">
          保存
        </el-button>
      </template>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 14px;
}

.hint {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.perm-list {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}

:deep(.perm-item) {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  padding: 6px 0;
}

:deep(.perm-item .el-checkbox__label) {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

:deep(.perm-item small) {
  color: var(--text-muted);
}
</style>
