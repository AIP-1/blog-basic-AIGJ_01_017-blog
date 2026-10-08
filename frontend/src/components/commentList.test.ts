import { describe, expect, it } from 'vitest'
import type { Comment } from '../api/types'
import { afterDelete } from './commentList'

function comment(id: number, parentId: number | null = null, replies: Comment[] = []): Comment {
  return {
    id, parentId, author: { id: 1, nickname: 'n', profileImageUrl: null, primaryBlogAddress: null },
    content: `c${id}`, secret: false, state: 'NORMAL', createdAt: '2026-10-09T10:00:00+09:00', updatedAt: null,
    viewer: { canEdit: false, canDelete: true }, blind: null, replies,
  }
}

describe('afterDelete', () => {
  it('답글 없는 댓글은 목록에서 빠진다', () => {
    expect(afterDelete([comment(1), comment(2)], comment(1)).map((c) => c.id)).toEqual([2])
  })

  it('답글 있는 댓글은 삭제된 댓글 자리로 남는다', () => {
    const [left] = afterDelete([comment(1, null, [comment(3, 1)])], comment(1))
    expect(left.state).toBe('DELETED')
    expect(left.content).toBeNull()
    expect(left.author).toBeNull()
    expect(left.replies.map((r) => r.id)).toEqual([3])
  })

  it('삭제 자리의 마지막 답글을 지우면 자리도 사라진다', () => {
    const deletedParent = { ...comment(1, null, [comment(3, 1)]), state: 'DELETED' as const }
    expect(afterDelete([deletedParent], comment(3, 1))).toEqual([])
  })

  it('살아 있는 부모의 답글을 지우면 부모는 남는다', () => {
    const [parent] = afterDelete([comment(1, null, [comment(3, 1), comment(4, 1)])], comment(3, 1))
    expect(parent.replies.map((r) => r.id)).toEqual([4])
  })
})
