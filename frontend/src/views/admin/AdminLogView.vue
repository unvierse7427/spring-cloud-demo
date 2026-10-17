<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { pageLoginLogs, pageOperationLogs } from '@/api/admin'
import type { LoginLog, OperationLog } from '@/types'

const tab = ref<'login' | 'operation'>('login')

// ------------------------------ 登录日志 ------------------------------

const loginQuery = reactive({
  username: '',
  loginType: '',
  status: '' as number | '',
  page: 1,
  size: 10
})
const loginLogs = ref<LoginLog[]>([])
const loginTotal = ref(0)
const loginLoading = ref(false)

async function loadLoginLogs() {
  loginLoading.value = true
  try {
    const result = await pageLoginLogs({ ...loginQuery })
    loginLogs.value = result.records
    loginTotal.value = result.total
  } catch (e) {
    ElMessage.error((e as Error).message)
    loginLogs.value = []
    loginTotal.value = 0
  } finally {
    loginLoading.value = false
  }
}

function searchLogin() {
  loginQuery.page = 1
  loadLoginLogs()
}

function resetLogin() {
  loginQuery.username = ''
  loginQuery.loginType = ''
  loginQuery.status = ''
  loginQuery.page = 1
  loadLoginLogs()
}

function handleLoginPageChange(page: number) {
  loginQuery.page = page
  loadLoginLogs()
}

// ------------------------------ 接口调用日志 ------------------------------

const opQuery = reactive({
  username: '',
  requestUri: '',
  success: '' as number | '',
  page: 1,
  size: 10
})
const opLogs = ref<OperationLog[]>([])
const opTotal = ref(0)
const opLoading = ref(false)
const opLoaded = ref(false)

async function loadOperationLogs() {
  opLoading.value = true
  try {
    const result = await pageOperationLogs({ ...opQuery })
    opLogs.value = result.records
    opTotal.value = result.total
    opLoaded.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
    opLogs.value = []
    opTotal.value = 0
  } finally {
    opLoading.value = false
  }
}

function searchOperation() {
  opQuery.page = 1
  loadOperationLogs()
}

function resetOperation() {
  opQuery.username = ''
  opQuery.requestUri = ''
  opQuery.success = ''
  opQuery.page = 1
  loadOperationLogs()
}

function handleOperationPageChange(page: number) {
  opQuery.page = page
  loadOperationLogs()
}

onMounted(loadLoginLogs)

function onTabChange(name: string | number) {
  if (name === 'operation' && !opLoaded.value) {
    loadOperationLogs()
  }
}

function loginTypeText(type: string) {
  if (type === 'LOGIN') return '登录'
  if (type === 'LOGOUT') return '退出'
  if (type === 'REFRESH') return '刷新'
  return type
}
</script>

<template>
  <el-tabs v-model="tab" class="log-page" @tab-change="onTabChange">
    <el-tab-pane label="登录日志" name="login">
      <el-form :inline="true">
        <el-form-item label="用户名">
          <el-input
            v-model="loginQuery.username"
            placeholder="用户名"
            clearable
            style="width: 180px"
            @keyup.enter="searchLogin"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="loginQuery.loginType" placeholder="全部类型" style="width: 140px">
            <el-option label="全部类型" value="" />
            <el-option label="登录" value="LOGIN" />
            <el-option label="退出" value="LOGOUT" />
            <el-option label="刷新" value="REFRESH" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="loginQuery.status" placeholder="全部结果" style="width: 140px">
            <el-option label="全部结果" value="" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchLogin">查询</el-button>
          <el-button @click="resetLogin">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loginLoading" :data="loginLogs">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ loginTypeText(row.loginType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消息" min-width="180">
          <template #default="{ row }">{{ row.message || '—' }}</template>
        </el-table-column>
        <el-table-column label="客户端 IP" width="150">
          <template #default="{ row }">{{ row.clientIp || '—' }}</template>
        </el-table-column>
        <el-table-column label="登录时间" width="180">
          <template #default="{ row }">{{ row.loginTime || '—' }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="loginTotal"
        :current-page="loginQuery.page"
        :page-size="loginQuery.size"
        @current-change="handleLoginPageChange"
      />
    </el-tab-pane>

    <el-tab-pane label="接口调用日志" name="operation">
      <el-form :inline="true">
        <el-form-item label="用户名">
          <el-input
            v-model="opQuery.username"
            placeholder="用户名"
            clearable
            style="width: 180px"
            @keyup.enter="searchOperation"
          />
        </el-form-item>
        <el-form-item label="请求路径">
          <el-input
            v-model="opQuery.requestUri"
            placeholder="接口路径，如 /user/list"
            clearable
            style="width: 220px"
            @keyup.enter="searchOperation"
          />
        </el-form-item>
        <el-form-item label="是否成功">
          <el-select v-model="opQuery.success" placeholder="全部结果" style="width: 140px">
            <el-option label="全部结果" value="" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchOperation">查询</el-button>
          <el-button @click="resetOperation">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="opLoading" :data="opLogs">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="serviceName" label="服务名" min-width="140" />
        <el-table-column label="用户" min-width="120">
          <template #default="{ row }">{{ row.username || '匿名' }}</template>
        </el-table-column>
        <el-table-column label="请求方法" width="110">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.requestMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请求路径" min-width="220">
          <template #default="{ row }">
            {{ row.requestUri }}
            <span v-if="row.queryString">?{{ row.queryString }}</span>
          </template>
        </el-table-column>
        <el-table-column label="客户端 IP" width="150">
          <template #default="{ row }">{{ row.clientIp || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态码" width="100">
          <template #default="{ row }">
            <el-tag :type="row.statusCode >= 200 && row.statusCode < 400 ? 'success' : 'danger'">
              {{ row.statusCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时 (ms)" width="110">
          <template #default="{ row }">{{ row.durationMs }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ row.createTime || '—' }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="opTotal"
        :current-page="opQuery.page"
        :page-size="opQuery.size"
        @current-change="handleOperationPageChange"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped>
/* 用面板样式包裹整个标签页容器，与其它后台页面保持一致的纸面质感 */
.log-page {
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .log-page {
    padding: 14px;
  }
}
</style>
