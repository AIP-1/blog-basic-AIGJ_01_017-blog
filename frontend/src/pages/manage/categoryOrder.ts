import type { CategoryNode } from '../../api/types'

/**
 * 카테고리 순서·상하위 바꾸기 (CAT-04)의 화면 쪽 계산. 끌어서 놓을 때마다 이 함수들로 새 배열을 만들고,
 * "순서 저장"을 누르면 toOrderItems 결과를 PUT /api/categories/order로 한 번에 보낸다.
 * 배열 순서가 곧 같은 자리 안의 순서다. 2단계 규칙(하위가 있는 카테고리는 하위가 될 수 없음)은 여기서도 지키고,
 * 서버가 다시 검사한다(409 CATEGORY_DEPTH).
 */
export interface OrderRow {
  id: number
  parentId: number | null
}

/** 서버의 트리를 위에서부터 한 줄씩 펼친다(최상위 다음에 그 하위). */
export function flatten(categories: CategoryNode[]): OrderRow[] {
  return categories.flatMap((category) => [
    { id: category.id, parentId: null },
    ...category.children.map((child) => ({ id: child.id, parentId: category.id })),
  ])
}

function hasChildren(rows: OrderRow[], id: number): boolean {
  return rows.some((row) => row.parentId === id)
}

/**
 * dragId를 targetId 바로 위로 옮긴다(target과 같은 자리가 된다). 하위가 있는 카테고리를 하위 자리로 옮기면
 * 3단계가 되므로 바꾸지 않고 그대로 돌려준다. 자기 자신 위로 놓아도 그대로다.
 */
export function moveBefore(rows: OrderRow[], dragId: number, targetId: number): OrderRow[] {
  const target = rows.find((row) => row.id === targetId)
  if (!target || dragId === targetId) {
    return rows
  }
  if (target.parentId !== null && hasChildren(rows, dragId)) {
    return rows
  }
  const rest = rows.filter((row) => row.id !== dragId)
  const index = rest.findIndex((row) => row.id === targetId)
  return [...rest.slice(0, index), { id: dragId, parentId: target.parentId }, ...rest.slice(index)]
}

/** dragId를 parentId의 하위 맨 아래로. 하위가 있거나, 상위가 하위 카테고리거나, 자기 자신이면 그대로. */
export function moveInto(rows: OrderRow[], dragId: number, parentId: number): OrderRow[] {
  const parent = rows.find((row) => row.id === parentId)
  if (!parent || parent.parentId !== null || dragId === parentId || hasChildren(rows, dragId)) {
    return rows
  }
  return [...rows.filter((row) => row.id !== dragId), { id: dragId, parentId }]
}

/** dragId를 최상위 맨 아래로. */
export function moveToRootEnd(rows: OrderRow[], dragId: number): OrderRow[] {
  if (!rows.some((row) => row.id === dragId)) {
    return rows
  }
  return [...rows.filter((row) => row.id !== dragId), { id: dragId, parentId: null }]
}

/** 보낼 본문. 같은 자리 안에서 배열 순서대로 0, 1, 2… */
export function toOrderItems(rows: OrderRow[]): { id: number; parentId: number | null; sortOrder: number }[] {
  const next = new Map<number | null, number>()
  return rows.map((row) => {
    const sortOrder = next.get(row.parentId) ?? 0
    next.set(row.parentId, sortOrder + 1)
    return { id: row.id, parentId: row.parentId, sortOrder }
  })
}

/** 그리기용: 펼친 줄을 다시 [최상위, 그 하위들] 묶음으로. */
export function group(rows: OrderRow[]): { id: number; children: number[] }[] {
  return rows.filter((row) => row.parentId === null).map((root) => ({
    id: root.id,
    children: rows.filter((row) => row.parentId === root.id).map((row) => row.id),
  }))
}
