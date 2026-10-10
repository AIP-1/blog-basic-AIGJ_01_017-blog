import { describe, expect, it } from 'vitest'
import { ACCENTS, applyBlogTheme } from './blogTheme'

function fakeRoot() {
  const props = new Map<string, string>()
  return {
    dataset: {} as Record<string, string>,
    style: {
      setProperty: (name: string, value: string) => props.set(name, value),
      removeProperty: (name: string) => props.delete(name),
    },
    props,
  }
}

describe('applyBlogTheme', () => {
  it('스킨은 data-skin, 포인트 색은 --brand 변수로', () => {
    const root = fakeRoot()
    applyBlogTheme({ skin: 'NOTE', accentColor: 'PINK' }, root as unknown as HTMLElement)
    expect(root.dataset.skin).toBe('NOTE')
    expect(root.props.get('--brand')).toBe(ACCENTS.PINK.brand)
    expect(root.props.get('--brand-soft')).toBe(ACCENTS.PINK.soft)
  })

  it('null이면 기본으로 되돌린다', () => {
    const root = fakeRoot()
    applyBlogTheme({ skin: 'MAGAZINE', accentColor: 'GRAY' }, root as unknown as HTMLElement)
    applyBlogTheme(null, root as unknown as HTMLElement)
    expect(root.dataset.skin).toBeUndefined()
    expect(root.props.size).toBe(0)
  })

  it('6색이 모두 있다', () => {
    expect(Object.keys(ACCENTS)).toEqual(['BLUE', 'GREEN', 'ORANGE', 'PINK', 'PURPLE', 'GRAY'])
  })
})
