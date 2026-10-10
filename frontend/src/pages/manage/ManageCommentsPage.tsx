import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Comment, PageResponse } from '../../api/types'
import CommentForm from '../../components/CommentForm'
import CommentItem from '../../components/CommentItem'
import Pagination from '../../components/Pagination'

/** 받은 댓글·방명록 한 줄. 댓글이면 달린 글, 방명록이면 null (GET /api/manage/comments) */
type Received = Comment & { post: { id: number; title: string } | null }
type Tab = 'comment' | 'guestbook'

/**
 * 받은 댓글·방명록 관리 (MNG-02, 목업 manage-comments). 남이 쓴 것만 최신순 20개씩, 탭과 페이지는 주소(?type=&page=)에 둔다.
 * 지우기와 답글은 글 상세·방명록과 같은 API를 쓴다. 답글은 한 단계라 답글 줄에서 누르면 그 부모에 단다.
 * 주인이 단 답글은 "받은" 것이 아니라 이 목록에는 나오지 않고, 글 상세·방명록에서 보인다.
 * 작성자 차단(목업 4번, MNG-04)은 백로그다.
 */
export default function ManageCommentsPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('type') === 'guestbook' ? 'guestbook' : 'comment'
  const page = Math.max(Number(params.get('page')) || 1, 1)
  const [result, setResult] = useState<PageResponse<Received> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [replyingTo, setReplyingTo] = useState<number | null>(null)

  const load = useCallback(() => api<PageResponse<Received>>(`/api/manage/comments?type=${tab}&page=${page}`),
    [tab, page])

  useEffect(() => {
    let active = true
    load()
      .then((found) => {
        if (active) {
          setResult(found)
          setError(null)
        }
      })
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [load])

  function changeTab(next: Tab) {
    setReplyingTo(null)
    setMessage(null)
    setParams(next === 'comment' ? {} : { type: next })
  }

  async function remove(target: Comment) {
    if (!window.confirm(tab === 'comment' ? '이 댓글을 삭제할까요?' : '이 방명록 글을 삭제할까요?')) {
      return
    }
    setMessage(null)
    try {
      await api(tab === 'comment' ? `/api/comments/${target.id}` : `/api/guestbook/${target.id}`, { method: 'DELETE' })
      setResult(await load())
      setMessage('삭제했습니다.')
    } catch (caught) {
      setError(caught instanceof ApiError && caught.status === 404 ? '이미 지워진 글입니다.' : errorMessage(caught))
    }
  }

  function replied() {
    setReplyingTo(null)
    setMessage('답글을 달았습니다.')
  }

  return (
    <main className="page">
      <section className="section" style={{ maxWidth: 760 }}>
        <h2>댓글·방명록</h2>
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'comment'} className={tab === 'comment' ? 'on' : undefined}
                  onClick={() => changeTab('comment')}>댓글</button>
          <button type="button" role="tab" aria-selected={tab === 'guestbook'}
                  className={tab === 'guestbook' ? 'on' : undefined} onClick={() => changeTab('guestbook')}>방명록</button>
        </div>
        {message && <p className="ok" role="status">{message}</p>}
        {error && <p className="err" role="alert">{error}</p>}
        {result && result.content.length === 0 && (
          <p className="muted">{tab === 'comment' ? '받은 댓글이 없습니다.' : '받은 방명록이 없습니다.'}</p>
        )}
        <div>
          {result?.content.map((item) => (
            <div key={item.id}>
              <CommentItem comment={item} kind={tab} onDelete={remove}
                           extra={item.post
                             ? <Link to={`/${item.post.id}#comment-${item.id}`}>{item.post.title}</Link>
                             : <Link to="/guestbook">방명록</Link>}
                           onReply={() => setReplyingTo(replyingTo === item.id ? null : item.id)} />
              {replyingTo === item.id && (
                <div className="comment reply">
                  <span />
                  <CommentForm path={item.post ? `/api/posts/${item.post.id}/comments` : '/api/guestbook'}
                               parentId={item.parentId ?? item.id} placeholder="답글을 입력하세요"
                               onCreated={replied} onError={setError} />
                </div>
              )}
            </div>
          ))}
        </div>
        {result && (
          <Pagination page={result.page} totalPages={result.totalPages}
                      href={(number) => `?${new URLSearchParams(tab === 'comment'
                        ? { page: String(number) } : { type: tab, page: String(number) })}`} />
        )}
      </section>
    </main>
  )
}
