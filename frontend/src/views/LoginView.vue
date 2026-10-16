<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const submitting = ref(false)

const accounts = [
  { username: 'admin', password: 'admin123', label: 'admin / admin123', desc: '管理员 · 全部权限' },
  { username: 'zhangsan', password: 'user123', label: 'zhangsan / user123', desc: '普通用户 · 只读' }
]

function fill(account: { username: string; password: string }) {
  form.username = account.username
  form.password = account.password
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    ElMessage.success('登录成功')
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="card" shadow="always">
      <div class="brand">
        <div class="logo">C</div>
        <div>
          <strong>Cloud Console</strong>
          <p>Spring Cloud + Spring Security + OAuth2 JWT</p>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="form"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            @keyup.enter="submit"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-button type="primary" class="submit" :loading="submitting" @click="submit">登录</el-button>
      </el-form>

      <el-divider>
        <span class="divider-text">快捷填充 · 不同账号进入的界面不同</span>
      </el-divider>

      <div class="accounts">
        <el-button v-for="account in accounts" :key="account.username" class="chip" @click="fill(account)">
          <span class="chip-label">{{ account.label }}</span>
          <span class="chip-desc">{{ account.desc }}</span>
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100%;
  padding: 40px 20px;
  background: radial-gradient(circle at 20% 20%, #e5e9ff, transparent 55%),
    radial-gradient(circle at 85% 75%, #dbf5f7, transparent 50%), var(--bg);
}

.card {
  width: 100%;
  max-width: 420px;
  border-radius: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 11px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 19px;
  font-weight: 700;
}

.brand strong {
  display: block;
  font-size: 16px;
}

.brand p {
  color: var(--text-muted);
  font-size: 12px;
}

.form {
  margin-top: 22px;
}

.submit {
  width: 100%;
}

.divider-text {
  color: var(--text-muted);
  font-size: 12px;
}

.accounts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: auto;
  margin-left: 0;
  padding: 8px 12px;
}

.chip-label {
  font-size: 13px;
}

.chip-desc {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
