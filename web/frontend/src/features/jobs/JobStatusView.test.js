import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import JobStatusView from './JobStatusView.vue'

describe('job status notification tests', () => {
  it('shows two test actions only to administrators and emits explicit types', async () => {
    const wrapper = mount(JobStatusView, {
      props: { isAdmin: true },
      global: { stubs: elementStubs() }
    })
    const buttons = wrapper.findAll('.notification-test-actions button')

    expect(buttons.map(button => button.text())).toEqual(['测试周日提醒', '测试周一评价'])
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('test-notification')).toEqual([
      ['SUNDAY_REMINDER'],
      ['MONDAY_EVALUATION']
    ])
  })

  it('hides test actions from non-admin job operators', () => {
    const wrapper = mount(JobStatusView, {
      props: { isAdmin: false },
      global: { stubs: elementStubs() }
    })

    expect(wrapper.find('.notification-test-panel').exists()).toBe(false)
  })
})

function elementStubs() {
  return {
    ElButton: { template: '<button><slot /></button>' },
    ElTag: { template: '<span><slot /></span>' }
  }
}
