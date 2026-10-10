import { useEffect, useState } from 'react'
import { ApiError, api, loginUrl } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Comment, CommentList } from '../api/types'
import type { MeState } from '../app/useMe'
import CommentForm from './CommentForm'
import AdminBlindButton from './AdminBlindButton'
import CommentItem from './CommentItem'
import ReportButton from './ReportButton'
import { afterDelete, afterEdit } from './commentList'

/**
 * 글 아래 댓글 (CMT-01, CMT-02, CMT-03, CMT-05, CMT-06 비밀댓글). 작성순 20개씩 더보기, 회원은 쓰기·답글(한 단계),
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
  /** 관리자가 숨기거나 풀면 목록을 다시 받는다 */
  const [reloadKey, setReloadKey] = useState(0)
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
  }, [postId, reloadKey])

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

  /**
   * 댓글마다 신고(ADMIN-04, 남의 보이는 댓글만)와 관리자 숨기기·해제(ADMIN-03). 비밀·삭제 댓글은 내용이 없어 신고하지 않는다
   */
  function moderation(comment: Comment) {
    if (comment.state === 'DELETED' || comment.state === 'SECRET') {
      return null
    }
    const mine = me.status === 'member' && comment.author?.id === me.me.id
    const admin = me.status === 'member' && me.me.role === 'ADMIN'
    return (
      <>
        {comment.state === 'NORMAL' && !mine && !comment.blind && (
          <ReportButton targetType="COMMENT" targetId={comment.id} me={me} small />
        )}
        {admin && (
          <AdminBlindButton kind="comments" id={comment.id} blinded={comment.state === 'BLINDED' || !!comment.blind}
                            onDone={() => setReloadKey((key) => key + 1)} />
        )}
      </>
    )
  }

  return (
    <section className="section" id="comments">
      <h2>댓글 <span className="muted num">{totalCount}</span></h2>
      <div>
        {comments.map((comment) => (
          <div key={comment.id}>
            <CommentItem comment={comment} onDelete={remove} onEdit={edit} actions={moderation(comment)}
                         onReply={me.status === 'member' && commentAllowed && comment.state !== 'DELETED'
                           ? () => setReplyingTo(replyingTo === comment.id ? null : comment.id) : undefined} />
            {comment.replies.map((reply) => (
              <CommentItem key={reply.id} comment={reply} onDelete={remove} onEdit={edit} isReply
                           actions={moderation(reply)} />
            ))}
            {replyingTo === comment.id && (
              <div className="comment reply">
                <span />
                <CommentForm path={path} parentId={comment.id} onCreated={added} placeholder="답글을 입력하세요"
                             allowSecret secretLabel="비밀댓글"
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
        <CommentForm path={path} parentId={null} onCreated={added} placeholder="댓글을 입력하세요" onError={setError}
                     allowSecret secretLabel="비밀댓글" />
      )}
    </section>
  )
}
