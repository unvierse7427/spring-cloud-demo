<script setup lang="ts">
import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const services = [
  {
    name: 'cloud-gateway',
    port: '8080',
    tech: 'Spring Cloud Gateway',
    role: '统一入口、JWT 统一鉴权、路由转发、跨域处理'
  },
  {
    name: 'cloud-auth-service',
    port: '9000',
    tech: 'Spring Security + OAuth2',
    role: '登录签发 JWT，RS256 私钥签名'
  },
  {
    name: 'cloud-user-service',
    port: '8081',
    tech: 'Spring Boot Web',
    role: '用户业务接口，按权限码做方法级校验'
  },
  {
    name: 'cloud-common',
    port: '—',
    tech: '公共依赖模块',
    role: '统一响应体 Result、JWT 权限转换与失败处理'
  },
  {
    name: 'nacos-server',
    port: '8848',
    tech: 'Spring Cloud Alibaba',
    role: '服务注册与发现'
  }
]

const flows = [
  { step: '01', title: '登录换取令牌', detail: 'POST /api/auth/login，认证中心校验密码后签发 accessToken / refreshToken' },
  { step: '02', title: '携带令牌请求', detail: '前端 axios 拦截器自动加上 Authorization: Bearer <token>' },
  { step: '03', title: '网关统一鉴权', detail: '网关用公钥离线验签，未通过直接返回 401/403，不转发给业务服务' },
  { step: '04', title: '服务响应', detail: '业务服务再次校验令牌并按权限码放行，返回统一 Result' }
]

/** 概览指标全部来自当前登录态，避免写死展示值 */
const stats = computed(() => [
  { label: '已接入服务', value: String(services.length), hint: '含网关与注册中心' },
  { label: '当前角色', value: String(auth.roles.length), hint: auth.isAdmin ? '管理员账号' : '普通账号' },
  { label: '权限码', value: String(auth.permissionCodes.length), hint: '来自 JWT authorities' },
  { label: '网关入口', value: '8080', hint: '所有请求统一经此转发' }
])

const initial = computed(() => (auth.username || 'U').charAt(0).toUpperCase())
</script>

<template>
  <div class="page">
    <section class="hero">
      <div class="hero-id">
        <div class="avatar">{{ initial }}</div>
        <div class="hero-text">
          <div class="hero-line">
            <h2>{{ auth.username || '未登录' }}</h2>
            <el-tag :type="auth.isAdmin ? 'danger' : 'info'" effect="light" size="small" round>
              {{ auth.isAdmin ? '管理员' : '普通用户' }}
            </el-tag>
          </div>
          <p>用户 ID {{ auth.profile?.userId ?? '—' }} · 登录态由 JWT 维持，页面按角色 / 权限码动态渲染</p>
        </div>
      </div>

      <dl class="stats">
        <div v-for="item in stats" :key="item.label" class="stat">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
          <span class="stat-hint">{{ item.hint }}</span>
        </div>
      </dl>
    </section>

    <div class="grid-2">
      <el-card shadow="never" class="block">
        <template #header>
          <div class="block-head">
            <span>我的角色与权限</span>
            <span class="block-note">JWT authorities</span>
          </div>
        </template>

        <div class="meta-row">
          <span class="meta-label">角色</span>
          <div class="meta-values">
            <el-tag v-for="role in auth.roles" :key="role" size="small" effect="light">{{ role }}</el-tag>
            <span v-if="auth.roles.length === 0" class="empty">—</span>
          </div>
        </div>

        <div class="meta-row">
          <span class="meta-label">权限码</span>
          <div class="meta-values">
            <el-tag v-for="code in auth.permissionCodes" :key="code" type="info" size="small" effect="plain">
              {{ code }}
            </el-tag>
            <span v-if="auth.permissionCodes.length === 0" class="empty">—</span>
          </div>
        </div>

        <p class="hint">
          左侧菜单与页面内的操作按钮都会依据上面的角色 / 权限码动态渲染；缺少权限的路由会被拦截到 403 页面。
          用 <strong>zhangsan / user123</strong> 登录可对比差异。
        </p>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>
          <div class="block-head">
            <span>请求链路</span>
            <span class="block-note">一次受保护请求的完整路径</span>
          </div>
        </template>

        <ol class="flow">
          <li v-for="item in flows" :key="item.step">
            <span class="flow-step">{{ item.step }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.detail }}</p>
            </div>
          </li>
        </ol>
      </el-card>
    </div>

    <section class="services">
      <div class="section-head">
        <h3>服务清单</h3>
        <span>共 {{ services.length }} 个模块</span>
      </div>

      <div class="service-grid">
        <article v-for="item in services" :key="item.name" class="service-card">
          <header>
            <span class="service-name">{{ item.name }}</span>
            <span class="port">:{{ item.port }}</span>
          </header>
          <p class="service-role">{{ item.role }}</p>
          <span class="service-tech">{{ item.tech }}</span>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ---------- 顶部身份与指标 ---------- */
.hero {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 24px 26px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.hero-id {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  font-size: 19px;
  font-weight: 700;
}

.hero-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-line h2 {
  margin: 0;
  color: var(--text);
  font-size: 20px;
  font-weight: 650;
}

.hero-text p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin: 0;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--border);
  overflow: hidden;
}

.stat {
  padding: 14px 16px;
  background: var(--surface-sunken);
}

.stat dt {
  color: var(--text-muted);
  font-size: 12px;
}

.stat dd {
  margin: 4px 0 2px;
  color: var(--text);
  font-size: 22px;
  font-weight: 680;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  color: var(--text-faint);
  font-size: 11.5px;
}

/* ---------- 两栏区块 ---------- */
.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  align-items: start;
}

.block :deep(.el-card__body) {
  padding: 18px 20px;
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.block-note {
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 400;
}

.meta-row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}

.meta-label {
  flex: none;
  width: 56px;
  padding-top: 2px;
  color: var(--text-muted);
  font-size: 13px;
}

.meta-values {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty {
  color: var(--text-faint);
  font-size: 13px;
}

.hint {
  margin: 14px 0 0;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--primary-soft);
  color: var(--text-regular);
  font-size: 12.5px;
  line-height: 1.7;
}

/* ---------- 请求链路 ---------- */
.flow {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow li {
  display: flex;
  gap: 12px;
  padding: 9px 0;
}

.flow-step {
  flex: none;
  width: 28px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11.5px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.flow strong {
  display: block;
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
}

.flow p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.65;
}

/* ---------- 服务清单 ---------- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 650;
}

.section-head span {
  color: var(--text-faint);
  font-size: 12px;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.service-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  transition: border-color 0.16s, box-shadow 0.16s, transform 0.16s;
}

.service-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.service-card header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.service-name {
  color: var(--text);
  font-size: 14px;
  font-weight: 620;
}

.port {
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.service-role {
  flex: 1;
  margin: 0;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.65;
}

.service-tech {
  align-self: flex-start;
  padding: 3px 8px;
  border-radius: var(--r-sm);
  background: var(--surface-sunken);
  color: var(--text-muted);
  font-size: 11.5px;
}

@media (max-width: 1080px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .grid-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
