<template>
  <div v-if="authLoading" class="auth-loading">
    <span class="auth-loading__orb">AI</span>
    <strong>正在确认登录状态...</strong>
  </div>

  <LoginView
    v-else-if="!currentUser"
    :error="loginError"
    :busy="loginBusy"
    :dingtalk-busy="dingtalkBusy"
    @login="login"
    @dingtalk-login="loginWithDingTalk"
  />

  <div v-else :class="['app-shell', { 'app-shell--report': currentView === 'report' }]">
    <div class="header-reveal-zone" aria-hidden="true" @mouseenter="revealHeader"></div>

    <header
      class="app-header"
      :class="{ 'app-header--hidden': headerHidden }"
      @mouseenter="keepHeaderOpen"
      @mouseleave="releaseHeader"
      @focusin="revealHeader"
    >
      <button class="brand" @click="setView(defaultView)">
        <span class="brand-mark brand-logo-wrap">
          <img :src="youzhiLogo" alt="优智科技 Youzhi" />
        </span>
        <span>
          <strong>Youzhi Weekly AI</strong>
          <small>钉钉周报智能汇总</small>
        </span>
      </button>

      <nav class="decor-nav" aria-label="周报导航">
        <button v-if="canViewReports" :class="{ active: currentView === 'dashboard' }" @click="setView('dashboard')">提交概览</button>
        <button v-if="canViewReports" :class="{ active: currentView === 'status' }" @click="setView('status')">未交名单</button>
        <button v-if="canViewReports" :class="{ active: currentView === 'report' }" @click="setView('report')">AI 评价</button>
        <button v-if="canRunJobs" :class="{ active: currentView === 'jobs' }" @click="setView('jobs')">运行状态</button>
        <button v-if="isAdmin" :class="{ active: currentView === 'users' }" @click="setView('users')">用户管理</button>
      </nav>

      <div class="account-area">
        <el-dropdown
          class="account-menu"
          trigger="hover"
          placement="bottom-end"
          @command="handleAccountCommand"
        >
          <button class="user-chip account-menu__trigger" type="button">
            <span class="avatar">{{ userInitial }}</span>
            <span>
              <strong>{{ currentUser.realName || currentUser.username }}</strong>
              <small>{{ roleLabel }} · JWT 已登录</small>
            </span>
            <span class="account-caret" aria-hidden="true">⌄</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu class="account-dropdown">
              <el-dropdown-item command="feedback">提出 bug 或建议</el-dropdown-item>
              <el-dropdown-item divided command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="main-content">
      <section v-if="canViewReports" class="home-hero">
        <div class="hero-copy">
          <el-tag effect="light" round>{{ latestWeek?.week || '等待数据' }}</el-tag>
          <h1>每周一打开浏览器，就能看到最新周报信息。</h1>
          <p>
            后端自动拉取钉钉通讯录和周报，Codex skill 生成管理评价，前端展示提交概览、未提交候选、个人工作总结和团队负责人履职情况。
          </p>
          <div class="hero-actions">
            <el-button v-if="canRunJobs" type="primary" size="large" round :loading="jobBusy" @click="runJob('previous')">
              生成上一周
            </el-button>
            <el-button v-if="canRunJobs" size="large" round :loading="jobBusy" @click="runJob('current')">
              当前周测试
            </el-button>
            <el-button size="large" round @click="refreshAll">刷新页面</el-button>
          </div>
          <div v-if="!canRunJobs" class="hero-permission-note">
            当前账号按授权范围查看周报数据，采集任务由系统管理员或全部周报权限账号执行。
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

      <section v-if="canViewReports && currentView !== 'dashboard'" class="category-strip">
        <button v-for="week in weeks" :key="week.week" :class="{ active: selectedWeek === week.week }" @click="selectWeek(week.week)">
          {{ week.week }}
        </button>
        <span v-if="weeks.length === 0" class="muted">暂无周次数据，请先生成一次。</span>
      </section>

      <SubmissionOverview
        v-if="currentView === 'dashboard' && canViewReports"
        :overview="overview"
        :rows="rows"
        :summary="summary"
        :weeks="weeks"
        :selected-week="selectedWeek"
        @select-week="selectWeek"
        @navigate-missing="showMissingReports"
        @download="downloadCsv"
      />

      <section v-if="currentView === 'status' && canViewReports" class="page-card">
        <div class="page-header">
          <div>
            <h1>提交状态</h1>
            <p>支持按姓名、部门、提交状态和负责人筛选。</p>
          </div>
          <el-button round @click="downloadCsv">下载 CSV</el-button>
        </div>

        <div class="toolbar">
          <el-input v-model="filters.keyword" clearable placeholder="搜索姓名、部门、职务" />
          <el-select v-model="filters.status" clearable placeholder="提交状态">
            <el-option label="已提交" value="已提交" />
            <el-option label="未提交" value="未提交" />
          </el-select>
          <el-select v-model="filters.leader" clearable placeholder="负责人">
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

      <EvaluationView
        v-if="currentView === 'report' && canViewReports"
        :selected-week="selectedWeek"
        :analysis="analysis"
        :overview="overview"
      />

      <section v-if="currentView === 'users' && isAdmin" class="page-card admin-page">
        <div class="admin-hero">
          <div>
            <span class="login-eyebrow">ACCESS CONTROL</span>
            <h1>用户与权限管理</h1>
            <p>系统管理员可以在这里新建账号、分配角色、绑定钉钉身份，并为后续团队范围权限预留配置。</p>
          </div>
          <el-button type="primary" round @click="openCreateUser">新建账号</el-button>
        </div>

        <div class="admin-toolbar">
          <el-input
            v-model="adminKeyword"
            clearable
            placeholder="搜索用户名、姓名、手机号、钉钉 userId"
            @keyup.enter="loadAdminUsers"
            @clear="loadAdminUsers"
          />
          <el-button round :loading="adminLoading" @click="loadAdminUsers">刷新</el-button>
        </div>

        <el-table :data="adminUsers" class="soft-table admin-table" height="560" row-key="id" v-loading="adminLoading">
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号" min-width="170">
            <template #default="{ row }">
              <div class="admin-user-cell">
                <strong>{{ row.realName || row.username }}</strong>
                <small>{{ row.username }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="220">
            <template #default="{ row }">
              <div class="role-chip-list">
                <el-tag v-for="role in row.roles" :key="role" :type="roleTagType(role)" effect="light">
                  {{ roleName(role) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="钉钉绑定" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="ding-binding">
                <span v-if="row.dingUserId">userId：{{ row.dingUserId }}</span>
                <span v-if="row.dingUnionId">unionId：{{ row.dingUnionId }}</span>
                <span v-if="!row.dingUserId && !row.dingUnionId" class="muted">未绑定</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="权限范围" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.deptScopes?.length ? row.deptScopes.join('、') : '暂未配置' }}
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginTime" label="最近登录" min-width="170">
            <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openEditUser(row)">编辑</el-button>
              <el-button size="small" link type="warning" @click="openPasswordReset(row)">重置密码</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新建账号' : '编辑账号'" width="720px">
          <div class="admin-form-grid">
            <label>
              <span>用户名</span>
              <el-input v-model="userForm.username" placeholder="如 hr01 / manager01" />
            </label>
            <label v-if="userDialogMode === 'create'">
              <span>初始密码</span>
              <el-input v-model="userForm.password" type="password" show-password placeholder="可留空，但需绑定钉钉身份" />
            </label>
            <label>
              <span>姓名</span>
              <el-input v-model="userForm.realName" placeholder="真实姓名" />
            </label>
            <label>
              <span>状态</span>
              <el-select v-model="userForm.status">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </label>
            <label>
              <span>手机号</span>
              <el-input v-model="userForm.mobile" placeholder="可选" />
            </label>
            <label>
              <span>邮箱</span>
              <el-input v-model="userForm.email" placeholder="可选" />
            </label>
            <label>
              <span>钉钉 userId</span>
              <el-input v-model="userForm.dingUserId" placeholder="用于钉钉登录绑定" />
            </label>
            <label>
              <span>钉钉 unionId</span>
              <el-input v-model="userForm.dingUnionId" placeholder="可选，跨应用身份" />
            </label>
            <label class="admin-form-grid__full">
              <span>角色</span>
              <el-select v-model="userForm.roles" multiple placeholder="请选择角色">
                <el-option
                  v-for="role in adminRoles"
                  :key="role.roleCode"
                  :label="`${role.roleName}（${role.roleCode}）`"
                  :value="role.roleCode"
                />
              </el-select>
            </label>
            <label class="admin-form-grid__full">
              <span>部门权限范围</span>
              <el-input
                v-model="userForm.deptScopesText"
                type="textarea"
                :rows="4"
                placeholder="一行一个范围：ALL / DEPT:信用业务线 / DEPT:财务 / USER:李晓玲 / USERID:024...。普通文本会自动匹配部门、姓名或 userid。"
              />
            </label>
          </div>
          <template #footer>
            <el-button @click="userDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="userSaving" @click="saveUser">保存</el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px">
          <div class="password-reset-box">
            <p>正在为 <strong>{{ passwordForm.username }}</strong> 重置密码。</p>
            <el-input v-model="passwordForm.password" type="password" show-password placeholder="请输入新密码，至少 6 位" />
          </div>
          <template #footer>
            <el-button @click="passwordDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="passwordSaving" @click="resetPassword">确认重置</el-button>
          </template>
        </el-dialog>
      </section>

      <section v-if="currentView === 'jobs' && canRunJobs" class="page-card">
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

      <section v-if="currentView === 'denied'" class="page-card no-access-card">
        <span class="login-eyebrow">ACCESS LIMITED</span>
        <h1>当前账号暂无周报查看权限</h1>
        <p>
          当前账号没有配置周报可见范围。如需开通，请联系系统管理员分配 REPORT_ALL 角色或配置部门/人员权限范围。
        </p>
        <el-button v-if="isAdmin" type="primary" round @click="setView('users')">进入用户管理</el-button>
      </section>
    </main>

    <el-dialog
      v-model="feedbackDialogVisible"
      class="feedback-dialog"
      width="560px"
      :close-on-click-modal="!feedbackSaving"
      destroy-on-close
    >
      <template #header>
        <div class="feedback-dialog__header">
          <span class="feedback-dialog__mark">钉</span>
          <div>
            <span class="login-eyebrow">FEEDBACK</span>
            <h2>提出 bug 或建议</h2>
            <p>不用填复杂工单，写一句话即可；系统会自动带上账号、周次和页面信息，并通知开发人员。</p>
          </div>
        </div>
      </template>

      <div class="feedback-panel feedback-panel--simple">
        <div class="feedback-target-card feedback-target-card--simple">
          <div>
            <strong>通知开发人员</strong>
            <span>会直接通过钉钉发送给张艺政。</span>
          </div>
          <el-tag effect="light" round>{{ selectedWeek || '当前页面' }}</el-tag>
        </div>

        <div class="feedback-type-grid feedback-type-grid--compact" aria-label="反馈类型">
          <button
            v-for="item in feedbackCategoryOptions"
            :key="item.value"
            type="button"
            :class="['feedback-type-card', { active: feedbackForm.category === item.value }]"
            @click="feedbackForm.category = item.value"
          >
            <span>{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
          </button>
        </div>

        <label class="feedback-simple-input">
          <span>反馈内容</span>
          <el-input
            v-model="feedbackForm.detail"
            type="textarea"
            :rows="5"
            maxlength="1200"
            show-word-limit
            placeholder="直接写：哪个页面、哪条数据、哪里不方便，或者希望增加什么功能。可以只写一句话。"
            @keyup.ctrl.enter="submitFeedback"
          />
        </label>

        <div class="feedback-quick-row">
          <span>自动附带：{{ currentUser.realName || currentUser.username }} · {{ selectedWeek || '未选择周次' }} · {{ currentViewLabel }}</span>
          <button
            type="button"
            :class="['feedback-urgent-toggle', { active: feedbackForm.urgency !== 'NORMAL' }]"
            @click="toggleFeedbackUrgency"
          >
            {{ feedbackForm.urgency === 'NORMAL' ? '普通处理' : '请尽快处理' }}
          </button>
        </div>

        <el-alert
          v-if="feedbackResult"
          :title="feedbackResult.message"
          :type="feedbackResult.delivered ? 'success' : 'warning'"
          show-icon
          :closable="false"
        />

        <div v-if="feedbackResult && !feedbackResult.delivered" class="feedback-copy-box">
          <div>
            <strong>备用方式</strong>
            <span>复制内容后，在钉钉中直接发给开发人员。</span>
          </div>
          <el-button round type="primary" @click="copyFeedbackText">复制内容</el-button>
        </div>
        <el-input
          v-if="feedbackResult && !feedbackResult.delivered"
          class="feedback-copy-text"
          :model-value="feedbackResult.copyText"
          type="textarea"
          :rows="4"
          readonly
        />
      </div>

      <template #footer>
        <div class="feedback-footer">
          <span>Ctrl + Enter 也可提交</span>
          <div>
            <el-button @click="feedbackDialogVisible = false">取消</el-button>
            <el-button class="feedback-submit-btn" type="primary" :loading="feedbackSaving" @click="submitFeedback">通知开发人员</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="changePasswordDialogVisible" title="修改密码" width="440px">
      <div class="password-reset-box">
        <p>建议首次登录后立即修改初始密码，修改后请妥善保存。</p>
        <label>
          <span>当前密码</span>
          <el-input v-model="changePasswordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </label>
        <label>
          <span>新密码</span>
          <el-input v-model="changePasswordForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </label>
        <label>
          <span>确认新密码</span>
          <el-input v-model="changePasswordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" @keyup.enter="changeOwnPassword" />
        </label>
      </div>
      <template #footer>
        <el-button @click="changePasswordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changePasswordSaving" @click="changeOwnPassword">确认修改</el-button>
      </template>
    </el-dialog>

    <button
      v-if="canViewReports"
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
import { changeCurrentPassword } from './api/auth.js'
import {
  fetchWeekAnalysis,
  fetchWeeks,
  fetchWeekSubmissionStatus,
  fetchWeekSummary,
  normalizeSubmissionStatus,
  normalizeWeekAnalysis,
  normalizeWeekOverview,
  normalizeWeekSummary
} from './api/weeks.js'
import youzhiLogo from './assets/youzhi-logo-transparent.png'
import { useAuth } from './composables/useAuth.js'
import LoginView from './features/auth/LoginView.vue'
import EvaluationView from './features/evaluation/EvaluationView.vue'
import SubmissionOverview from './features/overview/SubmissionOverview.vue'
const adminUsers = ref([])
const adminRoles = ref([])
const adminLoading = ref(false)
const adminKeyword = ref('')
const userDialogVisible = ref(false)
const userDialogMode = ref('create')
const userSaving = ref(false)
const userForm = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  mobile: '',
  email: '',
  dingUserId: '',
  dingUnionId: '',
  status: 1,
  roles: ['USER'],
  deptScopesText: ''
})
const passwordDialogVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ id: null, username: '', password: '' })
const changePasswordDialogVisible = ref(false)
const changePasswordSaving = ref(false)
const changePasswordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const feedbackDialogVisible = ref(false)
const feedbackSaving = ref(false)
const feedbackResult = ref(null)
const feedbackForm = reactive({
  category: 'BUG',
  detail: '',
  urgency: 'NORMAL'
})
const feedbackCategoryOptions = [
  { value: 'BUG', label: 'Bug', icon: '!' },
  { value: 'SUGGESTION', label: '建议', icon: '+' },
  { value: 'DATA', label: '数据问题', icon: '#' },
  { value: 'PERMISSION', label: '权限问题', icon: '锁' }
]
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
const reportViews = new Set(['dashboard', 'status', 'report'])
let lastScrollY = 0
const {
  authLoading,
  loginBusy,
  dingtalkBusy,
  loginError,
  currentUser,
  request,
  restoreSession,
  signIn,
  startDingTalkLogin,
  signOut
} = useAuth({ onAuthInvalidated: resetWorkspaceState })
const weekClient = { request }
const latestWeek = computed(() => weeks.value[0] || null)
const overview = computed(() => weeks.value.find(item => item.week === selectedWeek.value) || {})
const isAdmin = computed(() => (currentUser.value?.roles || []).includes('ADMIN'))
const isFullReportAccess = computed(() => {
  const roles = currentUser.value?.roles || []
  return roles.includes('REPORT_ALL')
})
const hasScopedReportAccess = computed(() => (currentUser.value?.deptScopes || [])
  .some(scope => String(scope || '').trim()))
const canViewReports = computed(() => isFullReportAccess.value || hasScopedReportAccess.value)
const canRunJobs = computed(() => isAdmin.value || isFullReportAccess.value)
const defaultView = computed(() => (canViewReports.value ? 'dashboard' : (isAdmin.value ? 'users' : 'denied')))
const roleLabel = computed(() => {
  const roles = currentUser.value?.roles || []
  if (roles.includes('ADMIN')) return '系统管理员'
  if (roles.includes('REPORT_ALL')) return '全部周报权限'
  if (hasScopedReportAccess.value) return '授权范围周报'
  if (roles.includes('HR')) return 'HR'
  if (roles.includes('MANAGER')) return '团队负责人'
  return '普通用户'
})
const currentViewLabel = computed(() => ({
  dashboard: '提交概览',
  status: '未交名单',
  report: 'AI 评价',
  jobs: '运行状态',
  users: '用户管理',
  denied: '无权限提示'
}[currentView.value] || currentView.value))
const userInitial = computed(() => {
  const name = currentUser.value?.realName || currentUser.value?.username || 'U'
  return String(name).slice(0, 2).toUpperCase()
})
const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return rows.value
    .map((row, index) => ({ row, index }))
    .filter(item => {
      const text = `${item.row['姓名'] || ''} ${item.row['部门'] || ''} ${item.row['职务'] || ''}`.toLowerCase()
      return (!keyword || text.includes(keyword))
        && (!filters.status || item.row['提交状态'] === filters.status)
        && (!filters.leader || item.row['是否负责人候选'] === filters.leader)
    })
    .sort((left, right) => {
      const leftMissing = left.row['提交状态'] === '未提交'
      const rightMissing = right.row['提交状态'] === '未提交'
      if (leftMissing !== rightMissing) return leftMissing ? -1 : 1
      return left.index - right.index
    })
    .map(item => item.row)
})

async function setView(view) {
  if (view === 'jobs' && !canRunJobs.value) {
    currentView.value = defaultView.value
    ElMessage.error('当前账号没有运行采集任务的权限')
    return
  }
  if (reportViews.has(view) && !canViewReports.value) {
    currentView.value = defaultView.value
    ElMessage.error('当前账号没有周报查看权限')
    if (currentView.value === 'users') {
      await ensureAdminData()
    }
    return
  }
  if (view === 'users' && !isAdmin.value) {
    ElMessage.error('只有系统管理员可以访问用户管理')
    return
  }
  currentView.value = view
  revealHeader()
  if (view === 'users') {
    await ensureAdminData()
  }
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

async function initAuth() {
  await restoreSession(enterWorkspace)
}
async function login(credentials) {
  const data = await signIn(credentials)
  if (!data) return
  ElMessage.success('登录成功')
  await enterWorkspace()
}

async function loginWithDingTalk() {
  await startDingTalkLogin()
}

async function enterWorkspace() {
  currentView.value = defaultView.value
  if (canViewReports.value) {
    await refreshAll()
  }
  if (currentView.value === 'users') {
    await ensureAdminData()
  }
}

async function ensureAdminData() {
  if (!isAdmin.value) return
  await Promise.all([loadAdminRoles(), loadAdminUsers()])
}

async function loadAdminRoles() {
  if (adminRoles.value.length) return
  adminRoles.value = await request('/api/admin/roles')
}

async function loadAdminUsers() {
  if (!isAdmin.value) return
  adminLoading.value = true
  try {
    const query = adminKeyword.value ? `?keyword=${encodeURIComponent(adminKeyword.value)}` : ''
    adminUsers.value = await request(`/api/admin/users${query}`)
  } finally {
    adminLoading.value = false
  }
}

function openCreateUser() {
  userDialogMode.value = 'create'
  resetUserForm()
  userDialogVisible.value = true
  loadAdminRoles()
}

function openEditUser(row) {
  userDialogMode.value = 'edit'
  Object.assign(userForm, {
    id: row.id,
    username: row.username || '',
    password: '',
    realName: row.realName || '',
    mobile: row.mobile || '',
    email: row.email || '',
    dingUserId: row.dingUserId || '',
    dingUnionId: row.dingUnionId || '',
    status: row.status === 0 ? 0 : 1,
    roles: row.roles?.length ? [...row.roles] : ['USER'],
    deptScopesText: row.deptScopes?.join('\n') || ''
  })
  userDialogVisible.value = true
  loadAdminRoles()
}

function resetUserForm() {
  Object.assign(userForm, {
    id: null,
    username: '',
    password: '',
    realName: '',
    mobile: '',
    email: '',
    dingUserId: '',
    dingUnionId: '',
    status: 1,
    roles: ['USER'],
    deptScopesText: ''
  })
}

async function saveUser() {
  if (!userForm.username.trim()) {
    ElMessage.error('请输入用户名')
    return
  }
  if (userDialogMode.value === 'create' && !userForm.password && !userForm.dingUserId && !userForm.dingUnionId) {
    ElMessage.error('新建用户至少需要设置密码或绑定钉钉身份')
    return
  }
  const body = {
    username: userForm.username.trim(),
    password: userForm.password,
    realName: userForm.realName,
    mobile: userForm.mobile,
    email: userForm.email,
    dingUserId: userForm.dingUserId,
    dingUnionId: userForm.dingUnionId,
    status: userForm.status,
    roles: userForm.roles?.length ? userForm.roles : ['USER'],
    deptScopes: parseDeptScopes(userForm.deptScopesText)
  }
  try {
    userSaving.value = true
    if (userDialogMode.value === 'create') {
      await request('/api/admin/users', { method: 'POST', body: JSON.stringify(body) })
    } else {
      await request(`/api/admin/users/${userForm.id}`, { method: 'PUT', body: JSON.stringify(body) })
    }
    userDialogVisible.value = false
    ElMessage.success('账号已保存')
    await loadAdminUsers()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    userSaving.value = false
  }
}

function openPasswordReset(row) {
  Object.assign(passwordForm, { id: row.id, username: row.username, password: '' })
  passwordDialogVisible.value = true
}

async function resetPassword() {
  if (!passwordForm.password || passwordForm.password.length < 6) {
    ElMessage.error('新密码至少 6 位')
    return
  }
  try {
    passwordSaving.value = true
    await request(`/api/admin/users/${passwordForm.id}/password`, {
      method: 'POST',
      body: JSON.stringify({ password: passwordForm.password })
    })
    passwordDialogVisible.value = false
    ElMessage.success('密码已重置')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    passwordSaving.value = false
  }
}

function openChangePassword() {
  Object.assign(changePasswordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  changePasswordDialogVisible.value = true
}

async function changeOwnPassword() {
  if (!changePasswordForm.oldPassword) {
    ElMessage.error('请输入当前密码')
    return
  }
  if (!changePasswordForm.newPassword || changePasswordForm.newPassword.length < 6) {
    ElMessage.error('新密码至少 6 位')
    return
  }
  if (changePasswordForm.newPassword !== changePasswordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  try {
    changePasswordSaving.value = true
    await changeCurrentPassword({ request }, {
      oldPassword: changePasswordForm.oldPassword,
      newPassword: changePasswordForm.newPassword
    })
    changePasswordDialogVisible.value = false
    ElMessage.success('密码已修改，下次登录请使用新密码')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    changePasswordSaving.value = false
  }
}

function parseDeptScopes(value) {
  return String(value || '')
    .split(/[\n,，;；]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function roleName(roleCode) {
  const role = adminRoles.value.find(item => item.roleCode === roleCode)
  return role?.roleName || roleCode
}

function roleTagType(roleCode) {
  if (roleCode === 'ADMIN') return 'danger'
  if (roleCode === 'REPORT_ALL') return 'primary'
  if (roleCode === 'HR') return 'success'
  if (roleCode === 'MANAGER') return 'warning'
  return 'info'
}

function openFeedbackDialog() {
  feedbackResult.value = null
  feedbackDialogVisible.value = true
}

function resetFeedbackForm() {
  Object.assign(feedbackForm, {
    category: 'BUG',
    detail: '',
    urgency: 'NORMAL'
  })
  feedbackResult.value = null
}

function toggleFeedbackUrgency() {
  feedbackForm.urgency = feedbackForm.urgency === 'NORMAL' ? 'HIGH' : 'NORMAL'
}

function feedbackTitleFromDetail() {
  const firstLine = feedbackForm.detail
    .trim()
    .split('\n')
    .find(line => line.trim())
    ?.trim() || '周报系统反馈'
  const clipped = firstLine.length > 42 ? `${firstLine.slice(0, 42)}...` : firstLine
  const category = feedbackCategoryOptions.find(item => item.value === feedbackForm.category)?.label || '反馈'
  return `${category}：${clipped}`
}

async function submitFeedback() {
  if (!feedbackForm.detail.trim()) {
    ElMessage.error('写一句反馈内容即可提交')
    return
  }
  try {
    feedbackSaving.value = true
    const data = await request('/api/feedback', {
      method: 'POST',
      body: JSON.stringify({
        category: feedbackForm.category,
        title: feedbackTitleFromDetail(),
        detail: feedbackForm.detail.trim(),
        expectation: '',
        urgency: feedbackForm.urgency,
        pageUrl: window.location.href,
        userAgent: navigator.userAgent,
        week: selectedWeek.value,
        view: currentViewLabel.value
      })
    })
    if (data.delivered) {
      ElMessage.success('已通知开发人员')
      feedbackDialogVisible.value = false
      resetFeedbackForm()
    } else {
      feedbackResult.value = {
        ...data,
        message: '已记录反馈，但钉钉消息未直接发送。请复制内容联系开发人员。'
      }
      ElMessage.warning(feedbackResult.value.message)
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    feedbackSaving.value = false
  }
}

async function copyFeedbackText() {
  const text = feedbackResult.value?.copyText || ''
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制，可以在钉钉中发给开发人员')
  } catch (error) {
    ElMessage.error('浏览器没有开放复制权限，请手动选中文本复制')
  }
}

function handleAccountCommand(command) {
  if (command === 'feedback') {
    openFeedbackDialog()
    return
  }
  if (command === 'password') {
    openChangePassword()
    return
  }
  if (command === 'logout') {
    logout()
  }
}

async function logout() {
  await signOut()
  ElMessage.success('已退出登录')
}

function resetWorkspaceState() {
  weeks.value = []
  selectedWeek.value = ''
  summary.value = {}
  analysis.value = {}
  rows.value = []
  latestJob.value = {}
  adminUsers.value = []
  adminRoles.value = []
  adminKeyword.value = ''
  changePasswordDialogVisible.value = false
  feedbackDialogVisible.value = false
  feedbackResult.value = null
  Object.assign(changePasswordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  resetFeedbackForm()
  currentView.value = 'dashboard'
}

async function refreshAll() {
  if (!canViewReports.value) return
  await loadWeeks()
  if (canRunJobs.value) {
    await loadJob()
  } else {
    latestJob.value = {}
    jobBusy.value = false
  }
  if (selectedWeek.value) {
    await loadWeek(selectedWeek.value)
  }
}

async function loadWeeks() {
  if (!canViewReports.value) return
  const data = await fetchWeeks(weekClient)
  weeks.value = Array.isArray(data) ? data.map(normalizeWeekOverview) : []
  if (!selectedWeek.value && weeks.value.length) {
    selectedWeek.value = weeks.value[0].week
  }
}

async function selectWeek(week) {
  if (!canViewReports.value) return
  selectedWeek.value = week
  await loadWeek(week)
}

async function showMissingReports() {
  filters.status = '未提交'
  await setView('status')
}

async function loadWeek(week) {
  if (!week || !canViewReports.value) return
  const [summaryData, analysisData, statusRows] = await Promise.all([
    fetchWeekSummary(weekClient, week),
    fetchWeekAnalysis(weekClient, week),
    fetchWeekSubmissionStatus(weekClient, week)
  ])
  summary.value = normalizeWeekSummary(summaryData)
  analysis.value = normalizeWeekAnalysis(analysisData)
  rows.value = normalizeSubmissionStatus(statusRows)
}

async function loadJob() {
  if (!canRunJobs.value) return
  latestJob.value = await request('/api/jobs/latest')
  jobBusy.value = latestJob.value.status === 'RUNNING'
}

async function runJob(weekMode) {
  if (!canRunJobs.value) {
    ElMessage.error('当前账号没有运行采集任务的权限')
    return
  }
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
  if (!canViewReports.value) {
    ElMessage.error('当前账号没有下载周报数据的权限')
    return
  }
  if (!selectedWeek.value) return
  try {
    const blob = await request(`/api/files/${selectedWeek.value}/submission-status/download`, {
      responseType: 'blob'
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `submission_status_${selectedWeek.value}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.message)
  }
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
