import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError, api, loginUrl } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Comment, PageResponse, Sidebar as SidebarData } from '../../api/types'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import BlogHeader from '../../components/BlogHeader'
import CommentForm from '../../components/CommentForm'
import CommentItem from '../../components/CommentItem'
import { afterEdit } from '../../components/commentList'
import ErrorPage from '../../components/ErrorPage'
import Pagination from '../../components/Pagination'
import Sidebar from '../../components/Sidebar'

const PATH = '/api/guestbook'

/**
 * 방명록 (CMT-04, 목업 guestbook). 블로그 머리글의 "방명록"으로 온다. 최신순 20개씩 페이지 번호이고(?page=),
 * 규칙은 댓글과 같다: 회원만 쓰기, 비밀글은 블로그 주인과 작성자만 내용을 본다, 답글은 한 단계,
 * 작성자는 고치기, 작성자·블로그 주인은 지우기. 쓰거나 지우면 지금 페이지를 다시 받는다(페이지가 밀리기 때문).
 */
export default function GuestbookPage() {
  const me = useMe()
  const [blogState] = useBlog()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const page = Math.max(Number(params.get('page')) || 1, 1)
  const [result, setResult] = useState<PageResponse<Comment> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [replyingTo, setReplyingTo] = useState<number | null>(null)
  const [sidebar, setSidebar] = useState<SidebarData | null>(null)

  const load = useCallback(() => api<PageResponse<Comment>>(`${PATH}?page=${page}`, { allowAnonymous: true }),
    [page])

  useEffect(() => {
    api<SidebarData>('/api/blog/sidebar', { allowAnonymous: true }).then(setSidebar).catch(() => setSidebar(null))
  }, [])

  useEffect(() => {
    let active = true
    load()
      .then((found) => active && setResult(found))
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [load])

  async function reload() {
    setReplyingTo(null)
    setResult(await load())
  }

  /** 새 글은 맨 위(1페이지)에 생긴다. 답글은 지금 페이지의 부모 안에 생긴다 */
  async function added(created: Comment) {
    if (created.parentId === null && page !== 1) {
      setReplyingTo(null)
      navigate('?page=1')
      return
    }
    await reload()
  }

  async function edit(target: Comment, content: string) {
    const edited = await api<Comment>(`${PATH}/${target.id}`, { method: 'PATCH', body: { content } })
    setResult((previous) => previous && { ...previous, content: afterEdit(previous.content, edited) })
  }

  async function remove(target: Comment) {
    if (!window.confirm('이 방명록 글을 삭제할까요?')) {
      return
    }
    try {
      await api(`${PATH}/${target.id}`, { method: 'DELETE' })
      await reload()
    } catch (caught) {
      setError(caught instanceof ApiError && caught.status === 404 ? '이미 지워진 글입니다.' : errorMessage(caught))
    }
  }

  if (blogState.status === 'notFound') {
    return <ErrorPage status={404} />
  }
  if (blogState.status !== 'ok') {
    return null
  }
  const blog = blogState.blog

  return (
    <div className="app">
      <BlogHeader blog={blog} me={me} />
      <main className="page">
        <div className="cols">
          <section className="section">
            <h2>방명록 <span className="muted num">{result?.totalElements ?? ''}</span></h2>
            {me.status === 'anonymous' && (
              <a className="btn" href={loginUrl()} style={{ justifySelf: 'start' }}>로그인하고 방명록 남기기</a>
            )}
            {me.status === 'member' && (
              <CommentForm path={PATH} parentId={null} allowSecret placeholder={`${blog.name}에 한마디 남겨 주세요`}
                           onCreated={added} onError={setError} />
            )}
            {error && <p className="err" role="alert">{error}</p>}
            {result && result.content.length === 0 && <p className="muted">아직 방명록이 없습니다.</p>}
            <div>
              {result?.content.map((entry) => (
                <div key={entry.id}>
                  <CommentItem comment={entry} kind="guestbook" onDelete={remove} onEdit={edit}
                               onReply={me.status === 'member' && entry.state !== 'DELETED'
                                 ? () => setReplyingTo(replyingTo === entry.id ? null : entry.id) : undefined} />
                  {entry.replies.map((reply) => (
                    <CommentItem key={reply.id} comment={reply} kind="guestbook" onDelete={remove} onEdit={edit}
                                 isReply />
                  ))}
                  {replyingTo === entry.id && (
                    <div className="comment reply">
                      <span />
                      <CommentForm path={PATH} parentId={entry.id} allowSecret placeholder="답글을 입력하세요"
                                   onCreated={added} onError={setError} />
                    </div>
                  )}
                </div>
              ))}
            </div>
            {result && <Pagination page={result.page} totalPages={result.totalPages} href={(number) => `?page=${number}`} />}
          </section>
          {sidebar && <Sidebar modules={sidebar.modules} />}
        </div>
      </main>
    </div>
  )
}
