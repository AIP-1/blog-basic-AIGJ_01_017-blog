import { describe, expect, it } from 'vitest'
import type { CategoryNode } from '../../api/types'
import { flatten, group, moveBefore, moveInto, moveToRootEnd, toOrderItems } from './categoryOrder'

function node(id: number, children: CategoryNode[] = []): CategoryNode {
  return { id, name: `c${id}`, postCount: 0, sortOrder: 0, children }
}

// 1 ─ 3     (1의 하위 3)
// 2
const tree = [node(1, [node(3)]), node(2)]

describe('categoryOrder', () => {
  it('트리를 위에서부터 펼치고 다시 묶는다', () => {
    const rows = flatten(tree)
    expect(rows).toEqual([{ id: 1, parentId: null }, { id: 3, parentId: 1 }, { id: 2, parentId: null }])
    expect(group(rows)).toEqual([{ id: 1, children: [3] }, { id: 2, children: [] }])
  })

  it('다른 줄 위에 놓으면 그 줄과 같은 자리의 바로 위로 간다', () => {
    const rows = moveBefore(flatten(tree), 2, 1)
    expect(group(rows)).toEqual([{ id: 2, children: [] }, { id: 1, children: [3] }])
    // 하위를 최상위 줄 위에 놓으면 최상위가 된다
    expect(group(moveBefore(flatten(tree), 3, 2))).toEqual([
      { id: 1, children: [] }, { id: 3, children: [] }, { id: 2, children: [] }])
  })

  it('하위가 있는 카테고리는 하위 자리로 갈 수 없다(3단계)', () => {
    const rows = flatten(tree)
    expect(moveBefore(rows, 1, 3)).toBe(rows)
    expect(moveInto(rows, 1, 2)).toBe(rows)
  })

  it('하위로 넣기와 최상위 맨 아래로 빼기', () => {
    const into = moveInto(flatten(tree), 2, 1)
    expect(group(into)).toEqual([{ id: 1, children: [3, 2] }])
    expect(group(moveToRootEnd(into, 3))).toEqual([{ id: 1, children: [2] }, { id: 3, children: [] }])
  })

  it('보낼 본문은 같은 자리 안에서 0부터 순서를 매긴다', () => {
    expect(toOrderItems(moveInto(flatten(tree), 2, 1))).toEqual([
      { id: 1, parentId: null, sortOrder: 0 },
      { id: 3, parentId: 1, sortOrder: 0 },
      { id: 2, parentId: 1, sortOrder: 1 },
    ])
  })
})
