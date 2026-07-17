import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import App from './App.vue'

const auth = vi.hoisted(() => ({
  currentUser: null,
  request: vi.fn().mockResolvedValue([]),
  restoreSession: vi.fn(async callback => callback?.())
}))

vi.mock('./composables/useAuth.js', () => ({
  useAuth: () => ({
    authLoading: ref(false),
    loginBusy: ref(false),
    dingtalkBusy: ref(false),
    loginError: ref(''),
    currentUser: auth.currentUser,
    request: auth.request,
    restoreSession: auth.restoreSession,
    signIn: vi.fn(),
    startDingTalkLogin: vi.fn(),
    signOut: vi.fn()
  })
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() }
}))

describe('application role menus and permission guards', () => {
  it('shows report and job menus to an all-report user without exposing admin', async () => {
    const wrapper = await mountAs({ roles: ['REPORT_ALL'], deptScopes: [] })

    expect(menuLabels(wrapper)).toEqual(['提交概览', '未交名单', '项目明细', 'AI 评价', '运行状态'])
    expect(wrapper.find('.admin-page').exists()).toBe(false)
    expect(wrapper.text()).toContain('全部周报权限')
  })

  it('keeps an admin without report scope inside user management', async () => {
    const wrapper = await mountAs({ roles: ['ADMIN'], deptScopes: [] })

    expect(menuLabels(wrapper)).toEqual(['运行状态', '用户管理'])
    expect(wrapper.find('.home-hero').exists()).toBe(false)
    expect(wrapper.find('.admin-page').exists()).toBe(true)
  })

  it('allows scoped report viewing without job or admin controls', async () => {
    const wrapper = await mountAs({ roles: ['MANAGER'], deptScopes: ['虚构研发部'] })

    expect(menuLabels(wrapper)).toEqual(['提交概览', '未交名单', '项目明细', 'AI 评价'])
    expect(wrapper.text()).toContain('授权范围周报')
    expect(wrapper.find('.home-hero').exists()).toBe(true)
    expect(wrapper.find('.admin-page').exists()).toBe(false)
  })

  it('routes the top missing statistic through the existing report guard', async () => {
    const wrapper = await mountAs({ roles: ['REPORT_ALL'], deptScopes: [] })

    await wrapper.get('[data-testid="pulse-missing-card"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.page-header h1').text()).toBe('提交状态')
    expect(wrapper.findAll('.decor-nav button').find(item => item.text() === '未交名单').classes()).toContain('active')
    expect(wrapper.findAll('.status-row').map(item => item.text())).toEqual(['未提交', '已提交'])
  })

  it('opens project details only for report-scoped users', async () => {
    const wrapper = await mountAs({ roles: ['MANAGER'], deptScopes: ['虚构研发部'] })
    const projectButton = wrapper.findAll('.decor-nav button').find(item => item.text() === '项目明细')

    await projectButton.trigger('click')

    expect(wrapper.get('.project-details-view h1').text()).toBe('项目明细')
    expect(wrapper.text()).toContain('该周暂无可展示的项目明细')
  })
})

async function mountAs(user) {
  auth.currentUser = ref({
    username: 'fictional-user',
    realName: '测试用户',
    ...user
  })
  auth.request.mockImplementation(async path => {
    if (path === '/api/weeks') return [{ week: '2026-W29' }]
    if (path.endsWith('/submission-status')) {
      return [
        { 姓名: '示例员工甲', 提交状态: '已提交' },
        { 姓名: '示例员工乙', 提交状态: '未提交' }
      ]
    }
    return []
  })

  const wrapper = mount(App, {
    global: {
      stubs: elementStubs(),
      directives: { loading: () => {} }
    }
  })
  await flushPromises()
  return wrapper
}

function menuLabels(wrapper) {
  return wrapper.findAll('.decor-nav button').map(item => item.text())
}

function elementStubs() {
  const withSlot = { template: '<div><slot /></div>' }
  return {
    ElAlert: true,
    ElButton: withSlot,
    ElDialog: true,
    ElDropdown: withSlot,
    ElDropdownItem: withSlot,
    ElDropdownMenu: withSlot,
    ElEmpty: { props: ['description'], template: '<div>{{ description }}</div>' },
    ElInput: true,
    ElOption: true,
    ElProgress: true,
    ElSelect: true,
    ElTable: {
      props: { data: { type: Array, default: () => [] } },
      template: '<div class="el-table-stub"><span v-for="(row, index) in data" :key="index" class="status-row">{{ row[\'提交状态\'] }}</span></div>'
    },
    ElTableColumn: true,
    ElTag: withSlot
  }
}
