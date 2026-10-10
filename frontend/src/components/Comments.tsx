import { useEffect, useState } from 'react'
import { ApiError, api, loginUrl } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Comment, CommentList } from '../api/types'
import type { MeState } from '../app/useMe'
import CommentForm from './CommentForm'
import CommentItem from './CommentItem'
import { afterDelete, afterEdit } from './commentList'

/**
 * 글 아래 댓글 (CMT-01, CMT-02, CMT-03, CMT-05). 작성순 20개씩 더보기, 회원은 쓰기·답글(한 단계),
 * 작성자는 고치기, 작성자·블로그 주인은 지우기.
 * 비회원에게는 쓰기 칸 대신 로그인 안내를 보여 주고, 로그인하면 이 글로 돌아온다(spec US3 시나리오 6).
 * 답글이 있는 댓글을 지우면 "삭제된 댓글입니다" 자리로 남는다(서버 규칙과 같게 화면도 바꾼다).
 */
export default function Comments({ postId, me, commentAllowed, onCountChange }: {
  postId: number
  me: MeState
  commentAllowed: boolean
  onCountChange: (count: number) => void
}) {
  const [comments, setComments] = useState<Comment[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [replyingTo, setReplyingTo] = useState<number | null>(null)
  const path = `/api/posts/${postId}/comments`

  useEffect(() => {
    let active = true
    api<CommentList>(`/api/posts/${postId}/comments`, { allowAnonymous: true })
      .then((page) => {
        if (active) {
          setComments(page.content)
          setNextCursor(page.nextCursor)
          setTotalCount(page.totalCount)
        }
      })
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [postId])

  function changeCount(count: number) {
    setTotalCount(count)
    onCountChange(count)
  }

  async function loadMore() {
    if (!nextCursor) {
      return
    }
    const page = await api<CommentList>(`${path}?cursor=${encodeURIComponent(nextCursor)}`, { allowAnonymous: true })
    setComments((previous) => [...previous, ...page.content])
    setNextCursor(page.nextCursor)
    changeCount(page.totalCount)
  }

  /** 새 댓글·답글을 목록에 붙인다. 같은 연타 방지 키의 재시도면 서버가 같은 댓글을 돌려주므로 이미 있으면 붙이지 않는다. */
  function added(created: Comment) {
    if (created.parentId === null) {
      // 다음 묶음이 남아 있으면 새 댓글은 그 끝에 있으므로, 다 불러온 경우에만 바로 붙인다
      if (nextCursor || comments.some((comment) => comment.id === created.id)) {
        return
      }
      setComments([...comments, created])
    } else {
      const parent = comments.find((comment) => comment.id === created.parentId)
      if (!parent || parent.replies.some((reply) => reply.id === created.id)) {
        return
      }
      setComments(comments.map((comment) => (comment.id === created.parentId
        ? { ...comment, replies: [...comment.replies, created] } : comment)))
      setReplyingTo(null)
    }
    changeCount(totalCount + 1)
  }

  /** 고치기 (CMT-03). 실패하면 오류를 던져 고치는 칸이 그 자리에 보이게 한다 */
  async function edit(target: Comment, content: string) {
    const edited = await api<Comment>(`/api/comments/${target.id}`, { method: 'PATCH', body: { content } })
    setComments((previous) => afterEdit(previous, edited))
  }

  async function remove(target: Comment) {
    if (!window.confirm('이 댓글을 삭제할까요?')) {
      return
    }
    try {
      await api(`/api/comments/${target.id}`, { method: 'DELETE' })
      setComments(afterDelete(comments, target))
      changeCount(totalCount - 1)
    } catch (caught) {
      setError(caught instanceof ApiError && caught.status === 404 ? '이미 지워진 댓글입니다.' : errorMessage(caught))
    }
  }

  return (
    <section className="section" id="comments">
      <h2>댓글 <span className="muted num">{totalCount}</span></h2>
      <div>
        {comments.map((comment) => (
          <div key={comment.id}>
            <CommentItem comment={comment} onDelete={remove} onEdit={edit}
                         onReply={me.status === 'member' && commentAllowed && comment.state !== 'DELETED'
                           ? () => setReplyingTo(replyingTo === comment.id ? null : comment.id) : undefined} />
            {comment.replies.map((reply) => (
              <CommentItem key={reply.id} comment={reply} onDelete={remove} onEdit={edit} isReply />
            ))}
            {replyingTo === comment.id && (
              <div className="comment reply">
                <span />
                <CommentForm path={path} parentId={comment.id} onCreated={added} placeholder="답글을 입력하세요"
                             onError={setError} />
              </div>
            )}
          </div>
        ))}
      </div>
      {nextCursor && <button className="btn" type="button" onClick={loadMore} style={{ justifySelf: 'center' }}>
        댓글 더보기</button>}
      {error && <p className="err" role="alert">{error}</p>}
      {!commentAllowed && <p className="small muted">이 글에는 댓글을 쓸 수 없습니다.</p>}
      {commentAllowed && me.status === 'anonymous' && (
        <a className="btn" href={loginUrl()} style={{ justifySelf: 'start' }}>로그인하고 댓글 쓰기</a>
      )}
      {commentAllowed && me.status === 'member' && (
        <CommentForm path={path} parentId={null} onCreated={added} placeholder="댓글을 입력하세요" onError={setError} />
      )}
    </section>
  )
}
