export const PAGE_GROUP = 10

export interface PageGroup {
  pages: number[]
  /** 이전 묶음의 마지막 페이지. 없으면 null */
  prev: number | null
  /** 다음 묶음의 첫 페이지. 없으면 null */
  next: number | null
}

/** 페이지 번호를 10개씩 묶어 보여 준다 (spec 목록과 페이지). current는 1부터. */
export function pageGroup(current: number, totalPages: number): PageGroup {
  if (totalPages < 1) {
    return { pages: [], prev: null, next: null }
  }
  const start = Math.floor((current - 1) / PAGE_GROUP) * PAGE_GROUP + 1
  const end = Math.min(start + PAGE_GROUP - 1, totalPages)
  const pages = Array.from({ length: Math.max(end - start + 1, 0) }, (_, i) => start + i)
  return { pages, prev: start > 1 ? start - 1 : null, next: end < totalPages ? end + 1 : null }
}
