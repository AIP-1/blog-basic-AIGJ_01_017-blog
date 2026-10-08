import { describe, expect, it } from 'vitest'
import { pageGroup } from './pageGroup'

describe('pageGroup', () => {
  it('10개씩 묶는다', () => {
    expect(pageGroup(1, 3)).toEqual({ pages: [1, 2, 3], prev: null, next: null })
    expect(pageGroup(3, 25).pages).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(pageGroup(3, 25).next).toBe(11)
  })

  it('가운데 묶음은 이전·다음이 있다', () => {
    expect(pageGroup(15, 25)).toEqual({
      pages: [11, 12, 13, 14, 15, 16, 17, 18, 19, 20], prev: 10, next: 21,
    })
    expect(pageGroup(21, 25)).toEqual({ pages: [21, 22, 23, 24, 25], prev: 20, next: null })
  })

  it('글이 없으면 번호도 없다', () => {
    expect(pageGroup(1, 0)).toEqual({ pages: [], prev: null, next: null })
  })
})
