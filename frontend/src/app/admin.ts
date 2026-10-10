import type { ModerationTarget } from '../api/types'
import { blogUrl } from './host'

/** 제재·신고 사유 (contracts 제재 사유). 화면 이름은 여기서 정한다 */
export const REASONS = [
  { code: 'SPAM', label: '스팸·광고' },
  { code: 'ADULT', label: '음란·유해' },
  { code: 'ABUSE', label: '욕설·비방' },
  { code: 'COPYRIGHT', label: '저작권 침해' },
  { code: 'ETC', label: '기타' },
] as const

export type ReasonCode = (typeof REASONS)[number]['code']

export function reasonLabel(code: string | null): string {
  return REASONS.find((reason) => reason.code === code)?.label ?? '—'
}

const ACTIONS: Record<string, string> = {
  BLIND: '숨김',
  UNBLIND: '숨김 해제',
  SUSPEND: '정지',
  UNSUSPEND: '정지 해제',
  RESTRICT_BLOG: '블로그 이용 제한',
  UNRESTRICT_BLOG: '이용 제한 해제',
  REJECT_REPORT: '신고 기각',
}

export function actionLabel(action: string): string {
  return ACTIONS[action] ?? action
}

const TARGET_TYPES: Record<string, string> = { POST: '글', COMMENT: '댓글', BLOG: '블로그', MEMBER: '회원', REPORT: '신고' }

export function targetTypeLabel(type: string): string {
  return TARGET_TYPES[type] ?? type
}

/**
 * 관리 화면에서 대상을 눌렀을 때 갈 주소. 글·댓글·블로그는 그 블로그 주소(다른 호스트라 a href),
 * 회원은 관리자 회원 상세(같은 호스트라 Link). 지워졌거나 갈 곳이 없으면 null
 */
export function targetHref(target: ModerationTarget, location: Location = window.location):
  { href: string; external: boolean } | null {
  if (!target.exists) {
    return null
  }
  switch (target.type) {
    case 'POST':
      return target.blogAddress && target.postId
        ? { href: blogUrl(target.blogAddress, `/${target.postId}`, location), external: true } : null
    case 'COMMENT':
      return target.blogAddress && target.postId
        ? { href: blogUrl(target.blogAddress, `/${target.postId}#comment-${target.id}`, location), external: true }
        : null
    case 'BLOG':
      return target.blogAddress ? { href: blogUrl(target.blogAddress, '/', location), external: true } : null
    case 'MEMBER':
      return { href: `/admin/members?id=${target.id}`, external: false }
    default:
      return null
  }
}

/** 신고 처리 결과 (ADMIN-04). 대상 종류마다 고를 수 있는 것만 */
export function resolveChoices(targetType: string): { code: string; label: string }[] {
  const all = [
    { code: 'BLIND', label: '블라인드' },
    { code: 'RESTRICT_BLOG', label: '블로그 이용 제한' },
    { code: 'SUSPEND', label: '작성자 정지' },
    { code: 'REJECT', label: '기각' },
  ]
  if (targetType === 'BLOG') {
    return all.filter((choice) => choice.code !== 'BLIND')
  }
  if (targetType === 'COMMENT') {
    return all.filter((choice) => choice.code !== 'RESTRICT_BLOG')
  }
  return all
}

/** 묶음의 사유별 수 중 가장 많은 사유(처리할 때 사유 칸의 처음 값) */
export function topReason(reasons: Record<string, number>): ReasonCode {
  let best: ReasonCode = 'SPAM'
  let count = -1
  for (const reason of REASONS) {
    const value = reasons[reason.code] ?? 0
    if (value > count) {
      best = reason.code
      count = value
    }
  }
  return best
}
