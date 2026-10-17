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

/** 左侧品牌面板的能力清单 */
const highlights = [
  { title: '统一认证', desc: '认证中心 RS256 私钥签发 JWT，网关公钥离线验签' },
  { title: '角色与权限码', desc: '菜单、按钮、路由三层按角色 / 权限码动态渲染' },
  { title: '微服务链路', desc: 'Gateway · Auth · User · Chat · RAG 统一接入' }
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
    <section class="brand-panel">
      <div class="brand-top">
        <div class="logo">C</div>
        <div class="brand-name">
          <strong>Cloud Console</strong>
          <span>Spring Cloud 微服务控制台</span>
        </div>
      </div>

      <div class="brand-main">
        <h1>统一身份与权限控制台</h1>
        <p class="lede">
          基于 Spring Cloud + Spring Security + OAuth2 JWT 构建，覆盖认证、鉴权、用户管理、
          实时聊天与 RAG 知识库问答。
        </p>

        <ul class="highlights">
          <li v-for="item in highlights" :key="item.title">
            <span class="mark" />
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.desc }}</p>
            </div>
          </li>
        </ul>
      </div>

      <footer class="brand-foot">
        <span class="dot" />
        <span>网关 :8080 · 认证 :9000 · 用户 :8081 · 聊天 :8082 · RAG :8083</span>
      </footer>
    </section>

    <section class="form-panel">
      <div class="form-box">
        <header class="form-head">
          <h2>登录</h2>
          <p>请使用系统账号登录，不同账号进入的界面与权限不同</p>
        </header>

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

        <div class="quick">
          <p class="quick-title">快捷填充</p>
          <button v-for="account in accounts" :key="account.username" class="chip" type="button" @click="fill(account)">
            <span class="chip-label">{{ account.label }}</span>
            <span class="chip-desc">{{ account.desc }}</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  min-height: 100vh;
  background: var(--surface);
}

/* ---------- 左侧品牌面板 ---------- */
.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 44px 52px;
  background:
    radial-gradient(circle at 12% 8%, rgb(99 102 241 / 30%), transparent 46%),
    radial-gradient(circle at 88% 92%, rgb(34 211 238 / 18%), transparent 44%),
    var(--sidebar);
  color: #fff;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

.brand-name strong {
  display: block;
  font-size: 16px;
  letter-spacing: 0.2px;
}

.brand-name span {
  color: var(--sidebar-text);
  font-size: 12px;
}

.brand-main {
  max-width: 460px;
}

.brand-main h1 {
  margin: 0 0 14px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.24;
  letter-spacing: -0.5px;
}

.lede {
  margin: 0 0 30px;
  color: var(--sidebar-text);
  font-size: 14px;
  line-height: 1.75;
}

.highlights {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.highlights li {
  display: flex;
  gap: 12px;
}

.mark {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 999px;
  background: #22d3ee;
  box-shadow: 0 0 0 4px rgb(34 211 238 / 16%);
}

.highlights strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.highlights p {
  margin: 3px 0 0;
  color: var(--sidebar-muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.brand-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--sidebar-muted);
  font-size: 12px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #34d399;
  box-shadow: 0 0 0 4px rgb(52 211 153 / 16%);
}

/* ---------- 右侧登录表单 ---------- */
.form-panel {
  display: grid;
  place-items: center;
  padding: 48px 40px;
}

.form-box {
  width: 100%;
  max-width: 380px;
}

.form-head h2 {
  margin: 0 0 6px;
  color: var(--text);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.form-head p {
  margin: 0 0 28px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.submit {
  width: 100%;
  height: 40px;
  margin-top: 4px;
}

.quick {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--border);
}

.quick-title {
  margin: 0 0 10px;
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--text-regular);
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.chip:hover {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.chip-label {
  font-size: 13px;
  font-weight: 500;
}

.chip-desc {
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 880px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .brand-panel {
    gap: 28px;
    padding: 32px 28px;
  }

  .brand-main h1 {
    font-size: 26px;
  }

  .highlights,
  .brand-foot {
    display: none;
  }

  .form-panel {
    align-items: start;
    padding: 32px 24px 48px;
  }
}
</style>
