import type { Comment } from '../api/types'

/** 지운 뒤의 목록. 답글이 남은 부모는 '삭제된 댓글' 자리로, 마지막 답글이 지워진 삭제 자리는 함께 뺀다. */
export function afterDelete(comments: Comment[], target: Comment): Comment[] {
  if (target.parentId === null) {
    return comments.flatMap((comment) => {
      if (comment.id !== target.id) {
        return [comment]
      }
      return comment.replies.length > 0
        ? [{ ...comment, state: 'DELETED' as const, content: null, author: null,
          viewer: { canEdit: false, canDelete: false } }]
        : []
    })
  }
  return comments.flatMap((comment) => {
    if (comment.id !== target.parentId) {
      return [comment]
    }
    const replies = comment.replies.filter((reply) => reply.id !== target.id)
    return comment.state === 'DELETED' && replies.length === 0 ? [] : [{ ...comment, replies }]
  })
}

/**
 * 고친 뒤의 목록 (CMT-03). 서버가 돌려준 댓글로 같은 id를 바꾼다. 부모를 고쳐도 답글 목록은 그대로 둔다
 * (고치기 응답의 replies는 비어 있다).
 */
export function afterEdit(comments: Comment[], edited: Comment): Comment[] {
  return comments.map((comment) => {
    if (comment.id === edited.id) {
      return { ...edited, replies: comment.replies }
    }
    if (comment.id === edited.parentId) {
      return { ...comment, replies: comment.replies.map((reply) => (reply.id === edited.id ? edited : reply)) }
    }
    return comment
  })
}
