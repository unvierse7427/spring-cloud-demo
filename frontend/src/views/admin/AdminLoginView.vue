<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const submitting = ref(false)

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await admin.login({ username: form.username.trim(), password: form.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin/users'
    await router.replace(redirect)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <el-card class="login-card">
      <div class="login-head">
        <div class="mark">A</div>
        <div>
          <strong>后台管理端</strong>
          <p>仅管理员账号可登录，与前台登录相互独立</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="管理员账号" prop="username">
          <el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="请输入管理员账号"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="submitting" @click="submit">
          进入后台
        </el-button>
      </el-form>

      <div class="login-foot">
        <span>演示账号：admin / admin123</span>
        <RouterLink to="/login">返回前台登录</RouterLink>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.admin-login {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at 15% 12%, rgb(99 102 241 / 28%), transparent 52%),
    radial-gradient(circle at 85% 82%, rgb(34 211 238 / 16%), transparent 48%),
    var(--sidebar);
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.login-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: var(--r-lg);
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

.login-head strong {
  display: block;
  color: var(--text);
  font-size: 16px;
  font-weight: 650;
}

.login-head p {
  margin: 2px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.submit {
  width: 100%;
  height: 40px;
}

.login-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 12px;
}

.login-foot a {
  color: var(--primary);
  font-weight: 500;
}
</style>
