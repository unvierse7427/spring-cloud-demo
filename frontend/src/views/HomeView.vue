<script setup lang="ts">
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
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>当前登录身份</span>
          <el-tag :type="auth.isAdmin ? 'danger' : 'info'" effect="dark" size="small">
            {{ auth.isAdmin ? '管理员' : '普通用户' }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ auth.username || '—' }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ auth.profile?.userId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="role in auth.roles" :key="role" class="tag">{{ role }}</el-tag>
          <span v-if="auth.roles.length === 0">—</span>
        </el-descriptions-item>
        <el-descriptions-item label="权限码">
          <el-tag v-for="code in auth.permissionCodes" :key="code" type="info" class="tag">{{ code }}</el-tag>
          <span v-if="auth.permissionCodes.length === 0">—</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert class="tip" type="info" :closable="false" show-icon>
        <template #title>
          左侧菜单与页面内的操作按钮都会依据上面的角色 / 权限码动态渲染；缺少权限的路由会被拦截到 403 页面。
          用 <strong>zhangsan / user123</strong> 登录可对比差异。
        </template>
      </el-alert>
    </el-card>

    <el-row :gutter="16">
      <el-col v-for="item in services" :key="item.name" :xs="24" :sm="12" :lg="8">
        <el-card class="service" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="service-name">{{ item.name }}</span>
              <span class="port">:{{ item.port }}</span>
            </div>
          </template>
          <p class="role">{{ item.role }}</p>
          <el-tag size="small" type="primary" effect="plain">{{ item.tech }}</el-tag>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>请求链路</template>
      <el-timeline>
        <el-timeline-item
          v-for="item in flows"
          :key="item.step"
          :timestamp="item.step"
          placement="top"
          type="primary"
          hollow
        >
          <strong>{{ item.title }}</strong>
          <p class="detail">{{ item.detail }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tag {
  margin-right: 6px;
}

.tip {
  margin-top: 14px;
}

.service {
  height: 100%;
}

.service-name {
  font-weight: 650;
}

.port {
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
}

.role {
  min-height: 42px;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 13px;
}

.detail {
  color: var(--text-muted);
  font-size: 13px;
}
</style>
