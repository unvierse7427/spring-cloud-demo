<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import type { User, UserPayload } from '@/types'

const store = useUserStore()
const auth = useAuthStore()

/** 按钮显隐由 JWT 中的权限码决定，后端仍会做二次校验 */
const canCreate = computed(() => auth.hasPermission('user:create'))
const canDelete = computed(() => auth.hasPermission('user:delete'))

const keyword = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserPayload>({ username: '', nickname: '', email: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

onMounted(() => store.load())

function search() {
  store.load(keyword.value)
}

function reset() {
  keyword.value = ''
  store.load()
}

function openDialog() {
  form.username = ''
  form.nickname = ''
  form.email = ''
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await store.add({ ...form })
    ElMessage.success('新增成功')
    dialogVisible.value = false
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

async function remove(row: User) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await store.remove(row.id)
    ElMessage.success('删除成功')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索用户名 / 昵称" clearable style="width: 220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">
            <el-icon><Search /></el-icon>
            <span>查询</span>
          </el-button>
          <el-button @click="reset">重置</el-button>
          <el-button v-if="canCreate" type="primary" plain @click="openDialog">
            <el-icon><Plus /></el-icon>
            <span>新增用户</span>
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="!canCreate" type="info" :closable="false" show-icon class="tip">
        <template #title>
          当前账号只具备 <strong>user:list</strong> 权限，因此不显示「新增用户」按钮，后端同样会拒绝越权调用。
        </template>
      </el-alert>

      <el-table v-loading="store.loading" :data="store.users" border stripe class="table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-alert v-if="store.error" type="error" :closable="false" show-icon class="tip" :title="store.error" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增用户" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="必填" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可选" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tip {
  margin-bottom: 14px;
}

.table {
  width: 100%;
}

.muted {
  color: var(--text-muted);
}
</style>
