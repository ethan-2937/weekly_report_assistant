import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import PersonReportLink from './PersonReportLink.vue'

describe('person report link tooltip', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('teleports and clamps a left-edge tooltip inside the viewport', async () => {
    const wrapper = mount(PersonReportLink, {
      attachTo: document.body,
      props: {
        name: '示例员工甲',
        candidates: [{ name: '示例员工甲', userId: 'test-user-001' }]
      }
    })
    const button = wrapper.get('button')
    button.element.getBoundingClientRect = () => ({
      left: 0,
      right: 60,
      top: 100,
      bottom: 124,
      width: 60,
      height: 24
    })

    await button.trigger('mouseenter')
    await flushPromises()

    const tooltip = document.body.querySelector('.person-report-link__tooltip')
    expect(tooltip).not.toBeNull()
    expect(tooltip.style.position).toBe('')
    expect(Number.parseFloat(tooltip.style.left)).toBeGreaterThanOrEqual(158)
    expect(button.attributes('aria-describedby')).toBe(tooltip.id)

    await button.trigger('mouseleave')
    expect(document.body.querySelector('.person-report-link__tooltip')).toBeNull()
    wrapper.unmount()
  })
})
