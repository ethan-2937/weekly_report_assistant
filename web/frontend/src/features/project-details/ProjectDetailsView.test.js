import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectDetailsView from './ProjectDetailsView.vue'

const rows = [
  {
    sequence: 1,
    productLine: '虚构产品线甲',
    customerName: '虚构客户甲',
    projectName: '虚构项目甲',
    investedDays: '2.5',
    travelExpense: '120',
    hospitalityExpense: '0'
  },
  {
    sequence: 2,
    productLine: '虚构产品线乙',
    customerName: '虚构客户乙',
    projectName: '虚构项目乙',
    investedDays: '3',
    travelExpense: '0',
    hospitalityExpense: '80'
  }
]

describe('project details view', () => {
  it('shows seven-column data summaries and emits download', async () => {
    const wrapper = mount(ProjectDetailsView, {
      props: { selectedWeek: '2026-W29', rows },
      global: { stubs: elementStubs() }
    })

    expect(wrapper.text()).toContain('项目明细')
    expect(wrapper.text()).toContain('5.5')
    expect(wrapper.text()).toContain('¥200')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('download')).toHaveLength(1)
  })

  it('renders an empty state for legacy weeks', () => {
    const wrapper = mount(ProjectDetailsView, {
      props: { selectedWeek: '2026-W28', rows: [] },
      global: { stubs: elementStubs() }
    })

    expect(wrapper.text()).toContain('该周暂无可展示的项目明细')
  })

  it('shows a sanitized load failure instead of an empty-state claim', () => {
    const wrapper = mount(ProjectDetailsView, {
      props: { selectedWeek: '2026-W29', rows: [], error: '项目明细暂时不可用' },
      global: { stubs: elementStubs() }
    })

    expect(wrapper.text()).toContain('项目明细暂时不可用')
    expect(wrapper.text()).not.toContain('该周暂无可展示的项目明细')
  })
})

function elementStubs() {
  return {
    BrowserDownloadHint: { template: '<div><slot /></div>' },
    ElAlert: { props: ['title'], template: '<div>{{ title }}</div>' },
    ElButton: { template: '<button><slot /></button>' },
    ElEmpty: { props: ['description'], template: '<div>{{ description }}</div>' },
    ElInput: true,
    ElOption: true,
    ElSelect: true,
    ElTable: { template: '<div><slot /></div>' },
    ElTableColumn: true
  }
}
