<template>
  <div v-if="authLoading" class="auth-loading">
    <span class="auth-loading__orb">AI</span>
    <strong>正在确认登录状态...</strong>
  </div>

  <section v-else-if="!currentUser" class="login-shell">
    <div class="login-card">
      <div class="login-brand">
        <span class="brand-mark brand-logo-wrap">
          <img :src="youzhiLogo" alt="优智科技 Youzhi" />
        </span>
        <div>
          <span class="login-eyebrow">Youzhi Weekly AI</span>
          <h1>登录后查看最新周报评价</h1>
          <p>先使用管理员账号进入系统，后续可为 HR、负责人和普通用户配置不同权限。</p>
        </div>
      </div>

      <el-alert
        v-if="loginError"
        :title="loginError"
        type="error"
        show-icon
        :closable="false"
      />

      <div class="login-form">
        <label>
          <span>用户名</span>
          <el-input v-model="loginForm.username" size="large" placeholder="请输入用户名" @keyup.enter="login" />
        </label>
        <label>
          <span>密码</span>
          <el-input
            v-model="loginForm.password"
            size="large"
            type="password"
            show-password
            placeholder="请输入密码"
            @keyup.enter="login"
          />
        </label>
        <el-button type="primary" size="large" round :loading="loginBusy" @click="login">
          用户名密码登录
        </el-button>
        <el-button class="ding-login-btn" size="large" round :loading="dingtalkBusy" @click="loginWithDingTalk">
          钉钉登录
        </el-button>
      </div>

      <div class="login-hint">
        <strong>初始化管理员：</strong>
        <code>admin</code>
        <span>/</span>
        <code>admin123</code>
      </div>
    </div>
  </section>

  <div v-else :class="['app-shell', { 'app-shell--report': currentView === 'report' }]">
    <div class="header-reveal-zone" aria-hidden="true" @mouseenter="revealHeader"></div>

    <header
      class="app-header"
      :class="{ 'app-header--hidden': headerHidden }"
      @mouseenter="keepHeaderOpen"
      @mouseleave="releaseHeader"
      @focusin="revealHeader"
    >
      <button class="brand" @click="setView('dashboard')">
        <span class="brand-mark brand-logo-wrap">
          <img :src="youzhiLogo" alt="优智科技 Youzhi" />
        </span>
        <span>
          <strong>Youzhi Weekly AI</strong>
          <small>钉钉周报智能汇总</small>
        </span>
      </button>

      <nav class="decor-nav" aria-label="周报导航">
        <button :class="{ active: currentView === 'dashboard' }" @click="setView('dashboard')">提交概览</button>
        <button :class="{ active: currentView === 'status' }" @click="setView('status')">未交名单</button>
        <button :class="{ active: currentView === 'report' }" @click="setView('report')">AI 评价</button>
        <button :class="{ active: currentView === 'jobs' }" @click="setView('jobs')">运行状态</button>
      </nav>

      <div class="account-area">
        <div class="user-chip">
          <span class="avatar">{{ userInitial }}</span>
          <span>
            <strong>{{ currentUser.realName || currentUser.username }}</strong>
            <small>{{ roleLabel }} · JWT 已登录</small>
          </span>
        </div>
        <el-button size="small" round @click="logout">退出</el-button>
      </div>
    </header>

    <main class="main-content">
      <section class="home-hero">
        <div class="hero-copy">
          <el-tag effect="light" round>{{ latestWeek?.week || '等待数据' }}</el-tag>
          <h1>每周一打开浏览器，就能看到最新周报信息。</h1>
          <p>
            后端自动拉取钉钉通讯录和周报，Codex skill 生成管理评价，前端展示提交概览、未提交候选、个人工作总结和团队负责人履职情况。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" round :loading="jobBusy" @click="runJob('previous')">
              生成上一周
            </el-button>
            <el-button size="large" round :loading="jobBusy" @click="runJob('current')">
              当前周测试
            </el-button>
            <el-button size="large" round @click="refreshAll">刷新页面</el-button>
          </div>
        </div>

        <div class="hero-showcase" aria-hidden="true">
          <div class="floating-card blue">
            <span>{{ overview.expectedCount }}</span>
            <strong>应交候选</strong>
          </div>
          <div class="floating-card green">
            <span>{{ overview.submittedCount }}</span>
            <strong>已提交</strong>
          </div>
          <div class="floating-card yellow">
            <span>{{ overview.missingCount }}</span>
            <strong>未提交</strong>
          </div>
        </div>
      </section>

      <section class="category-strip">
        <button v-for="week in weeks" :key="week.week" :class="{ active: selectedWeek === week.week }" @click="selectWeek(week.week)">
          {{ week.week }}
        </button>
        <span v-if="weeks.length === 0" class="muted">暂无周次数据，请先生成一次。</span>
      </section>

      <section v-if="currentView === 'dashboard'" class="page-card">
        <div class="page-header">
          <div>
            <h1>提交概览</h1>
            <p>{{ selectedWeek || '未选择周次' }} · 数据生成时间 {{ formatDate(overview.generatedAt) }}</p>
          </div>
          <el-button round :disabled="!selectedWeek" @click="downloadCsv">下载提交表</el-button>
        </div>

        <div class="stat-grid">
          <article class="stat-card">
            <small>应交候选</small>
            <strong>{{ overview.expectedCount }}</strong>
            <span>来自钉钉通讯录授权范围</span>
          </article>
          <article class="stat-card success">
            <small>已提交</small>
            <strong>{{ overview.submittedCount }}</strong>
            <span>按 userid 精准匹配</span>
          </article>
          <article class="stat-card danger">
            <small>未提交候选</small>
            <strong>{{ overview.missingCount }}</strong>
            <span>需结合排除规则复核</span>
          </article>
          <article class="stat-card info">
            <small>负责人候选</small>
            <strong>{{ overview.leaderCandidateCount }}</strong>
            <span>来自钉钉 leader 字段</span>
          </article>
        </div>

        <div class="dashboard-layout">
          <div class="soft-panel">
            <div class="side-title">
              <div>
                <h2>提交摘要</h2>
                <p>由 Python 采集脚本自动生成。</p>
              </div>
            </div>
            <MarkdownReport
              :content="summary.submissionSummary"
              empty-text="暂无提交摘要。"
              variant="compact"
            />
          </div>
        </div>
      </section>

      <section v-if="currentView === 'status'" class="page-card">
        <div class="page-header">
          <div>
            <h1>提交状态</h1>
            <p>支持按姓名、部门、提交状态和负责人候选筛选。</p>
          </div>
          <el-button round @click="downloadCsv">下载 CSV</el-button>
        </div>

        <div class="toolbar">
          <el-input v-model="filters.keyword" clearable placeholder="搜索姓名、部门、职务" />
          <el-select v-model="filters.status" clearable placeholder="提交状态">
            <el-option label="已提交" value="已提交" />
            <el-option label="未提交" value="未提交" />
          </el-select>
          <el-select v-model="filters.leader" clearable placeholder="负责人候选">
            <el-option label="是" value="是" />
            <el-option label="否" value="否" />
          </el-select>
        </div>

        <el-table :data="filteredRows" class="soft-table" height="560" row-key="userid">
          <el-table-column prop="提交状态" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row['提交状态'] === '已提交' ? 'success' : 'danger'" effect="light">
                {{ row['提交状态'] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="姓名" label="姓名" width="130" />
          <el-table-column prop="部门" label="部门" min-width="190" show-overflow-tooltip />
          <el-table-column prop="职务" label="职务" min-width="180" show-overflow-tooltip />
          <el-table-column prop="是否负责人候选" label="负责人" width="100" />
          <el-table-column prop="提交时间" label="提交时间" min-width="170" />
        </el-table>
      </section>

      <section v-if="currentView === 'report'" class="page-card report-page">
        <div class="report-hero-card">
          <div class="report-hero-main">
            <span class="report-eyebrow">WEEKLY AI REVIEW · {{ selectedWeek || '未选择周次' }}</span>
            <h1>AI 周报评价看板</h1>
            <p>
              聚焦虚实盘、时间分配健康度、AI 使用红黑榜、下周计划合格性，以及需要老板拍板的协调事项。
            </p>
          </div>
          <div class="report-status-card">
            <span :class="['status-dot', analysis.isManagerReport ? 'ready' : 'waiting']"></span>
            <strong>{{ analysis.isManagerReport ? '正式评价已生成' : '等待正式评价' }}</strong>
            <small>{{ analysis.source || (analysis.isManagerReport ? 'manager_report.md' : 'analysis_input.md') }}</small>
          </div>
        </div>

        <div class="report-kpi-strip">
          <article>
            <small>提交率</small>
            <strong>{{ submissionRate }}</strong>
            <span>{{ overview.submittedCount || 0 }} / {{ overview.expectedCount || 0 }}</span>
          </article>
          <article>
            <small>未提交候选</small>
            <strong>{{ overview.missingCount || 0 }}</strong>
            <span>优先核对排除规则</span>
          </article>
          <article>
            <small>负责人候选</small>
            <strong>{{ overview.leaderCandidateCount || 0 }}</strong>
            <span>需检查履职材料</span>
          </article>
          <article>
            <small>评价模式</small>
            <strong>{{ analysis.isManagerReport ? '正式' : '预览' }}</strong>
            <span>{{ analysis.isManagerReport ? '可直接阅读' : '等待 Codex 产出' }}</span>
          </article>
        </div>

        <div v-if="!analysis.isManagerReport" class="notice-card">
          <strong>下一步：</strong>
          在服务器 Codex 中运行 skill，让它基于本周 `analysis_input.md` 生成 `output/{{ selectedWeek }}/summary/manager_report.md`，刷新页面后即可展示正式评价。
        </div>
        <MarkdownReport
          :content="analysis.content"
          empty-text="暂无 AI 评价内容。"
          variant="report"
        />
      </section>

      <section v-if="currentView === 'jobs'" class="page-card">
        <div class="page-header">
          <div>
            <h1>运行状态</h1>
            <p>查看最近一次 Web 触发的采集任务。</p>
          </div>
          <div class="header-actions">
            <el-button round :loading="jobBusy" @click="runJob('previous')">补跑上一周</el-button>
            <el-button round @click="loadJob">刷新状态</el-button>
          </div>
        </div>
        <div class="job-card">
          <el-tag :type="jobStatusType(latestJob.status)" effect="light">{{ latestJob.status || 'NO JOB' }}</el-tag>
          <h2>{{ latestJob.weekLabel || latestJob.weekMode || '暂无任务' }}</h2>
          <p>开始：{{ formatDate(latestJob.startedAt) }} · 结束：{{ formatDate(latestJob.finishedAt) }}</p>
          <pre>{{ latestJob.errorMessage || latestJob.stdout || '暂无日志。' }}</pre>
        </div>
      </section>
    </main>

    <button
      class="ai-fab"
      :class="{ active: currentView === 'report', pending: !overview.hasManagerReport }"
      type="button"
      aria-label="查看 AI 评价"
      @click="setView('report')"
    >
      <span class="ai-fab__orb">AI</span>
      <span class="ai-fab__copy">
        <strong>查看 AI 评价</strong>
        <small>{{ overview.hasManagerReport ? '已生成正式评价' : '等待 Codex 生成' }}</small>
      </span>
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import youzhiLogo from './assets/youzhi-logo-transparent.png'
import MarkdownReport from './components/MarkdownReport.vue'

const TOKEN_KEY = 'weekly_report_jwt'

const authLoading = ref(true)
const loginBusy = ref(false)
const dingtalkBusy = ref(false)
const loginError = ref('')
const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const currentUser = ref(null)
const loginForm = reactive({ username: 'admin', password: '' })

const weeks = ref([])
const selectedWeek = ref('')
const currentView = ref('dashboard')
const summary = ref({})
const analysis = ref({})
const rows = ref([])
const latestJob = ref({})
const jobBusy = ref(false)
const headerHidden = ref(false)
const headerHovered = ref(false)
const filters = reactive({ keyword: '', status: '', leader: '' })
let lastScrollY = 0

const latestWeek = computed(() => weeks.value[0] || null)
const overview = computed(() => weeks.value.find(item => item.week === selectedWeek.value) || {})
const roleLabel = computed(() => {
  const roles = currentUser.value?.roles || []
  if (roles.includes('ADMIN')) return '系统管理员'
  if (roles.includes('HR')) return 'HR'
  if (roles.includes('MANAGER')) return '团队负责人'
  return '普通用户'
})
const userInitial = computed(() => {
  const name = currentUser.value?.realName || currentUser.value?.username || 'U'
  return String(name).slice(0, 2).toUpperCase()
})
const submissionRate = computed(() => {
  const expected = Number(overview.value.expectedCount || 0)
  const submitted = Number(overview.value.submittedCount || 0)
  if (!expected) return '-'
  return `${Math.round((submitted / expected) * 100)}%`
})
const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return rows.value.filter(row => {
    const text = `${row['姓名'] || ''} ${row['部门'] || ''} ${row['职务'] || ''}`.toLowerCase()
    return (!keyword || text.includes(keyword))
      && (!filters.status || row['提交状态'] === filters.status)
      && (!filters.leader || row['是否负责人候选'] === filters.leader)
  })
})

function setView(view) {
  currentView.value = view
  revealHeader()
}

function getScrollY() {
  return window.scrollY || document.documentElement.scrollTop || 0
}

function revealHeader() {
  headerHidden.value = false
}

function keepHeaderOpen() {
  headerHovered.value = true
  revealHeader()
}

function releaseHeader() {
  headerHovered.value = false
  if (getScrollY() > 150) {
    headerHidden.value = true
  }
}

function handleHeaderScroll() {
  const currentScrollY = getScrollY()
  const scrollDelta = currentScrollY - lastScrollY

  if (currentScrollY < 90 || scrollDelta < -8) {
    revealHeader()
  } else if (scrollDelta > 10 && currentScrollY > 150 && !headerHovered.value) {
    headerHidden.value = true
  }

  lastScrollY = currentScrollY
}

async function request(path, options = {}) {
  const { skipAuth, ...fetchOptions } = options
  const headers = new Headers(fetchOptions.headers || {})
  if (!skipAuth && token.value) {
    headers.set('Authorization', `Bearer ${token.value}`)
  }
  if (fetchOptions.body && !(fetchOptions.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...fetchOptions, headers })
  const contentType = response.headers.get('content-type') || ''
  const data = contentType.includes('application/json')
    ? await response.json().catch(() => ({}))
    : await response.text().catch(() => '')

  if (response.status === 401 && !skipAuth) {
    clearAuth()
    throw new Error('登录已过期，请重新登录')
  }
  if (!response.ok) {
    throw new Error(data?.error || data || `HTTP ${response.status}`)
  }
  return data
}

async function initAuth() {
  authLoading.value = true
  loginError.value = ''
  readAuthQuery()
  try {
    if (token.value) {
      currentUser.value = await request('/api/auth/me')
      await refreshAll()
    }
  } catch (error) {
    clearAuth(false)
    loginError.value = error.message
  } finally {
    authLoading.value = false
  }
}

function readAuthQuery() {
  const url = new URL(window.location.href)
  const queryToken = url.searchParams.get('token')
  const authError = url.searchParams.get('auth_error')
  if (queryToken) {
    token.value = queryToken
    localStorage.setItem(TOKEN_KEY, queryToken)
  }
  if (authError) {
    loginError.value = authError
  }
  if (queryToken || authError || url.searchParams.get('login')) {
    url.searchParams.delete('token')
    url.searchParams.delete('auth_error')
    url.searchParams.delete('login')
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
  }
}

async function login() {
  if (!loginForm.username || !loginForm.password) {
    loginError.value = '请输入用户名和密码'
    return
  }
  try {
    loginBusy.value = true
    loginError.value = ''
    const data = await request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(loginForm),
      skipAuth: true
    })
    applyLogin(data)
    ElMessage.success('登录成功')
    await refreshAll()
  } catch (error) {
    loginError.value = error.message
  } finally {
    loginBusy.value = false
  }
}

async function loginWithDingTalk() {
  try {
    dingtalkBusy.value = true
    loginError.value = ''
    const data = await request('/api/auth/dingtalk/login-url', { skipAuth: true })
    if (!data.enabled || !data.loginUrl) {
      loginError.value = data.message || '钉钉登录暂未启用'
      return
    }
    window.location.href = data.loginUrl
  } catch (error) {
    loginError.value = error.message
  } finally {
    dingtalkBusy.value = false
  }
}

function applyLogin(data) {
  token.value = data.token
  currentUser.value = data.user
  localStorage.setItem(TOKEN_KEY, data.token)
}

async function logout() {
  try {
    await request('/api/auth/logout', { method: 'POST' })
  } catch (error) {
    // JWT is stateless, so the frontend can always clear local login state.
  }
  clearAuth()
  ElMessage.success('已退出登录')
}

function clearAuth(showLogin = true) {
  token.value = ''
  currentUser.value = null
  localStorage.removeItem(TOKEN_KEY)
  weeks.value = []
  selectedWeek.value = ''
  summary.value = {}
  analysis.value = {}
  rows.value = []
  latestJob.value = {}
  if (showLogin) {
    authLoading.value = false
  }
}

async function refreshAll() {
  await loadWeeks()
  await loadJob()
  if (selectedWeek.value) {
    await loadWeek(selectedWeek.value)
  }
}

async function loadWeeks() {
  weeks.value = await request('/api/weeks')
  if (!selectedWeek.value && weeks.value.length) {
    selectedWeek.value = weeks.value[0].week
  }
}

async function selectWeek(week) {
  selectedWeek.value = week
  await loadWeek(week)
}

async function loadWeek(week) {
  if (!week) return
  const [summaryData, analysisData, statusRows] = await Promise.all([
    request(`/api/weeks/${week}/summary`),
    request(`/api/weeks/${week}/analysis`),
    request(`/api/weeks/${week}/submission-status`)
  ])
  summary.value = summaryData
  analysis.value = analysisData
  rows.value = statusRows
}

async function loadJob() {
  latestJob.value = await request('/api/jobs/latest')
  jobBusy.value = latestJob.value.status === 'RUNNING'
}

async function runJob(weekMode) {
  try {
    jobBusy.value = true
    latestJob.value = await request(`/api/jobs/run?week=${weekMode}`, { method: 'POST' })
    ElMessage.success('采集任务已启动，稍后自动刷新。')
    pollJob()
  } catch (error) {
    jobBusy.value = false
    ElMessage.error(error.message)
  }
}

async function pollJob() {
  for (let i = 0; i < 90; i++) {
    await new Promise(resolve => setTimeout(resolve, 2000))
    await loadJob()
    if (latestJob.value.status && latestJob.value.status !== 'RUNNING') {
      await refreshAll()
      ElMessage[latestJob.value.status === 'SUCCESS' ? 'success' : 'error'](
        latestJob.value.status === 'SUCCESS' ? '采集完成。' : '采集失败，请查看日志。'
      )
      return
    }
  }
  jobBusy.value = false
}

async function downloadCsv() {
  if (!selectedWeek.value) return
  const response = await fetch(`/api/files/${selectedWeek.value}/submission-status/download`, {
    headers: token.value ? { Authorization: `Bearer ${token.value}` } : {}
  })
  if (response.status === 401) {
    clearAuth()
    ElMessage.error('登录已过期，请重新登录')
    return
  }
  if (!response.ok) {
    ElMessage.error(`下载失败：HTTP ${response.status}`)
    return
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `submission_status_${selectedWeek.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function jobStatusType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

onMounted(() => {
  lastScrollY = getScrollY()
  window.addEventListener('scroll', handleHeaderScroll, { passive: true })
  initAuth()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleHeaderScroll)
})
</script>
