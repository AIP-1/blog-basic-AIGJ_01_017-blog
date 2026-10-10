import type { SidebarModuleItem, SidebarModuleType } from '../../api/types'

/** 사이드바 모듈 이름 (목업 manage-settings). 블로그 홈 바로가기는 숨길 수 없다 */
export const MODULE_LABELS: Record<SidebarModuleType, { name: string; note?: string }> = {
  PROFILE: { name: '블로그 홈 바로가기', note: '항상 보임' },
  CATEGORY: { name: '카테고리' },
  TAG: { name: '태그' },
  RECENT_POST: { name: '최근 글', note: '5개' },
  RECENT_COMMENT: { name: '최근 댓글', note: '5개' },
  VISITOR: { name: '방문자 수', note: '오늘·어제·누적' },
  POPULAR_POST: { name: '인기 글', note: '누적 조회수 5개' },
  SUBSCRIBE: { name: '구독 버튼과 구독자 수' },
}

/** from 자리의 모듈을 to 자리로 옮긴다(나머지는 한 칸씩 밀린다). 범위 밖이면 그대로 */
export function moveModule(items: SidebarModuleItem[], from: number, to: number): SidebarModuleItem[] {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
    return items
  }
  const next = [...items]
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  return next
}

/** 보이기·숨기기. 블로그 홈 바로가기는 바꾸지 않는다 */
export function toggleModule(items: SidebarModuleItem[], type: SidebarModuleType): SidebarModuleItem[] {
  if (type === 'PROFILE') {
    return items
  }
  return items.map((item) => item.moduleType === type ? { ...item, isVisible: !item.isVisible } : item)
}
