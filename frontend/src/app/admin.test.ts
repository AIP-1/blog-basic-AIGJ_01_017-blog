import { describe, expect, it } from 'vitest'
import type { ModerationTarget } from '../api/types'
import { actionLabel, reasonLabel, releasePath, resolveChoices, targetHref, topReason } from './admin'

const location = { protocol: 'http:', port: '8080', hostname: 'blog.test' } as Location

function target(overrides: Partial<ModerationTarget>): ModerationTarget {
  return {
    type: 'POST', id: 15, label: '글', blogAddress: 'jiwon', postId: 15, memberId: null, exists: true,
    sanctioned: false, ...overrides,
  }
}

describe('targetHref', () => {
  it('글·댓글·블로그는 그 블로그 주소로 간다', () => {
    expect(targetHref(target({}), location)).toEqual({ href: 'http://jiwon.blog.test:8080/15', external: true })
    expect(targetHref(target({ type: 'COMMENT', id: 88 }), location))
      .toEqual({ href: 'http://jiwon.blog.test:8080/15#comment-88', external: true })
    expect(targetHref(target({ type: 'BLOG', id: 3, postId: null }), location))
      .toEqual({ href: 'http://jiwon.blog.test:8080/', external: true })
  })

  it('회원은 관리자 회원 상세, 지워진 대상은 링크가 없다', () => {
    expect(targetHref(target({ type: 'MEMBER', id: 7 }), location)).toEqual({ href: '/admin/members?id=7', external: false })
    expect(targetHref(target({ exists: false }), location)).toBeNull()
  })
})

describe('신고 처리', () => {
  it('블로그 신고는 블라인드, 댓글 신고는 블로그 제한을 고를 수 없다', () => {
    expect(resolveChoices('BLOG').map((choice) => choice.code)).toEqual(['RESTRICT_BLOG', 'SUSPEND', 'REJECT'])
    expect(resolveChoices('COMMENT').map((choice) => choice.code)).toEqual(['BLIND', 'SUSPEND', 'REJECT'])
    expect(resolveChoices('POST')).toHaveLength(4)
  })

  it('가장 많은 사유를 처음 값으로', () => {
    expect(topReason({ COPYRIGHT: 6, ETC: 1 })).toBe('COPYRIGHT')
    expect(topReason({})).toBe('SPAM')
  })

  it('풀 수 있는 조치만 해제 API가 있다', () => {
    const line = (action: string, type: string) => ({ action, target: { type, id: 5 } })
    expect(releasePath(line('BLIND', 'POST'))).toBe('/api/admin/posts/5/blind')
    expect(releasePath(line('BLIND', 'COMMENT'))).toBe('/api/admin/comments/5/blind')
    expect(releasePath(line('RESTRICT_BLOG', 'BLOG'))).toBe('/api/admin/blogs/5/restriction')
    expect(releasePath(line('SUSPEND', 'MEMBER'))).toBe('/api/admin/members/5/suspension')
    expect(releasePath(line('UNBLIND', 'POST'))).toBeNull()
    expect(releasePath(line('REJECT_REPORT', 'BLOG'))).toBeNull()
  })

  it('코드를 화면 이름으로', () => {
    expect(reasonLabel('ABUSE')).toBe('욕설·비방')
    expect(reasonLabel(null)).toBe('—')
    expect(actionLabel('RESTRICT_BLOG')).toBe('블로그 이용 제한')
  })
})
