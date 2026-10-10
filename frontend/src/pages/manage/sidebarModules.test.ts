import { describe, expect, it } from 'vitest'
import type { SidebarModuleItem } from '../../api/types'
import { moveModule, toggleModule } from './sidebarModules'

const items: SidebarModuleItem[] = [
  { moduleType: 'PROFILE', isVisible: true },
  { moduleType: 'CATEGORY', isVisible: true },
  { moduleType: 'SUBSCRIBE', isVisible: false },
]

describe('moveModule', () => {
  it('끌어서 놓은 자리로 옮기고 나머지는 밀린다', () => {
    expect(moveModule(items, 2, 1).map((item) => item.moduleType)).toEqual(['PROFILE', 'SUBSCRIBE', 'CATEGORY'])
    expect(moveModule(items, 0, 2).map((item) => item.moduleType)).toEqual(['CATEGORY', 'SUBSCRIBE', 'PROFILE'])
  })

  it('같은 자리나 범위 밖이면 같은 배열', () => {
    expect(moveModule(items, 1, 1)).toBe(items)
    expect(moveModule(items, 0, 5)).toBe(items)
  })
})

describe('toggleModule', () => {
  it('보이기·숨기기를 바꾸고, 블로그 홈 바로가기는 그대로', () => {
    expect(toggleModule(items, 'SUBSCRIBE')[2].isVisible).toBe(true)
    expect(toggleModule(items, 'PROFILE')).toBe(items)
  })
})
