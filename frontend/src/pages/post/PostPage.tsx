import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import type { PostDetail, Sidebar as SidebarData } from '../../api/types'
import { formatDateTime } from '../../app/format'
import { sanitizePostHtml } from '../../app/sanitize'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import BlogHeader from '../../components/BlogHeader'
import Comments from '../../components/Comments'
import ErrorPage from '../../components/ErrorPage'
import Sidebar from '../../components/Sidebar'

type PostState = { status: 'loading' } | { status: 'ok'; post: PostDetail } | { status: 'notFound' }
  | { status: 'subscribersOnly'; blogName: string } | { status: 'error' }

/**
 * 글 상세 (POST-04, POST-10). 본문은 서버가 정화해 저장했고, 넣기 전에 DOMPurify로 한 번 더 거른다(이중 정화).
 * 볼 수 없는 글이면 404 화면이다. 주인에게만 수정·삭제 버튼이 보이지만, 권한은 서버가 다시 검사한다.
 */
export default function PostPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const me = useMe()
  const [blogState] = useBlog()
  const [state, setState] = useState<PostState>({ status: 'loading' })
  const [sidebar, setSidebar] = useState<SidebarData | null>(null)

  useEffect(() => {
    api<SidebarData>('/api/blog/sidebar', { allowAnonymous: true }).then(setSidebar).catch(() => setSidebar(null))
  }, [])

  useEffect(() => {
    let active = true
    api<PostDetail>(`/api/posts/${postId}`, { allowAnonymous: true })
      .then((post) => active && setState({ status: 'ok', post }))
      .catch((error: unknown) => {
        if (!active) {
          return
        }
        if (error instanceof ApiError && error.code === 'SUBSCRIBERS_ONLY') {
          setState({ status: 'subscribersOnly', blogName: String(error.detail?.blogName ?? '') })
        } else if (error instanceof ApiError && error.status === 404) {
          setState({ status: 'notFound' })
        } else {
          setState({ status: 'error' })
        }
      })
    return () => {
      active = false
    }
  }, [postId])

  if (blogState.status === 'notFound' || state.status === 'notFound') {
    return <ErrorPage status={404} />
  }
  if (state.status === 'error' || blogState.status === 'error') {
    return <ErrorPage status={500} />
  }
  if (blogState.status !== 'ok' || state.status === 'loading') {
    return null
  }

  async function remove(post: PostDetail) {
    if (!window.confirm('이 글을 삭제할까요? 댓글과 공감도 함께 사라집니다.')) {
      return
    }
    await api(`/api/posts/${post.id}`, { method: 'DELETE' })
    navigate('/')
  }

  return (
    <div className="app">
      <BlogHeader blog={blogState.blog} me={me} />
      <main className="page">
        <div className="cols">
          {state.status === 'subscribersOnly'
            ? (
              <div className="box" style={{ justifyItems: 'start' }}>
                <b>구독자 공개 글입니다</b>
                <span className="muted">{state.blogName}을(를) 구독하면 읽을 수 있어요. (구독은 뒤 스텝에서 만듭니다)</span>
              </div>
            )
            : <Article post={state.post} me={me} onDelete={remove}
                       onCommentCount={(count) => setState({ status: 'ok', post: { ...state.post, commentCount: count } })} />}
          {sidebar && <Sidebar modules={sidebar.modules} />}
        </div>
      </main>
    </div>
  )
}

function Article({ post, me, onDelete, onCommentCount }: {
  post: PostDetail
  me: ReturnType<typeof useMe>
  onDelete: (post: PostDetail) => void
  onCommentCount: (count: number) => void
}) {
  return (
    <article className="stack" style={{ gap: 20 }}>
      <header className="post-head">
        <div className="small muted">{post.category?.name ?? '미분류'}</div>
        <h1>{post.title}</h1>
        <div className="row small muted num">
          <span>{post.author.nickname}</span>
          {post.publishedAt && <span>{formatDateTime(post.publishedAt)}</span>}
          {post.updatedAt && <span>수정 {formatDateTime(post.updatedAt)}</span>}
          {post.viewer.isOwner && post.visibility !== 'PUBLIC' && <span className="chip">비공개</span>}
        </div>
        {post.viewer.isOwner && (
          <div className="row">
            <Link className="btn" to={`/manage/posts/${post.id}/edit`}>수정</Link>
            <button className="btn" type="button" onClick={() => onDelete(post)}>삭제</button>
          </div>
        )}
      </header>
      {post.blind && (
        <div className="box danger" role="alert">
          <b>관리자가 숨긴 글입니다</b>
          <span>사유: {post.blind.reasonMessage}</span>
          <span className="small">다른 사람에게는 보이지 않습니다. 수정할 수 없고 삭제는 할 수 있습니다.</span>
        </div>
      )}
      <div className="prose" dangerouslySetInnerHTML={{ __html: sanitizePostHtml(post.contentHtml) }} />
      {post.tags.length > 0 && (
        <div className="tag-cloud">
          {post.tags.map((tag) => <Link key={tag} className="chip" to={`/tag/${encodeURIComponent(tag)}`}>#{tag}</Link>)}
        </div>
      )}
      <div className="row small muted"><span>공감 {post.likeCount}</span></div>
      <nav className="row between small" aria-label="이전·다음 글">
        <span>이전 글 {post.prev ? <Link to={`/${post.prev.id}`}>{post.prev.title}</Link> : <span className="muted">없음</span>}</span>
        <span>다음 글 {post.next ? <Link to={`/${post.next.id}`}>{post.next.title}</Link> : <span className="muted">없음</span>}</span>
      </nav>
      <Comments key={post.id} postId={post.id} me={me} commentAllowed={post.commentAllowed}
                onCountChange={onCommentCount} />
    </article>
  )
}
