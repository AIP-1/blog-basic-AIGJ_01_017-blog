import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { PostDetail, Sidebar as SidebarData } from '../../api/types'
import { formatDateTime } from '../../app/format'
import { sanitizePostHtml } from '../../app/sanitize'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import AuthorName from '../../components/AuthorName'
import BlogHeader from '../../components/BlogHeader'
import Comments from '../../components/Comments'
import LikeButton from '../../components/LikeButton'
import ErrorPage from '../../components/ErrorPage'
import Sidebar from '../../components/Sidebar'
import SimilarPosts from '../../components/SimilarPosts'

type PostState = { status: 'loading' } | { status: 'ok'; post: PostDetail } | { status: 'notFound' }
  | { status: 'subscribersOnly'; blogName: string } | { status: 'error' }

/**
 * 글 상세 (POST-04, POST-10). 본문은 서버가 정화해 저장했고, 넣기 전에 DOMPurify로 한 번 더 거른다(이중 정화).
 * 볼 수 없는 글이면 404 화면이다. 주인에게만 공개 범위·수정·삭제 칸이 보이지만, 권한은 서버가 다시 검사한다.
 * 본문이 보인 뒤 조회 기록을 남긴다(POST-09). 화면의 조회수는 이번 조회를 세기 전 값이다.
 * 이전·다음 글 아래에 비슷한 글(OWN-06)을 보여 준다.
 */
export default function PostPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const me = useMe()
  const [blogState] = useBlog()
  const [state, setState] = useState<PostState>({ status: 'loading' })
  const [sidebar, setSidebar] = useState<SidebarData | null>(null)
  /** 주인의 공개 범위 바꾸기·삭제가 실패했을 때 글 머리에 보일 문장 */
  const [actionError, setActionError] = useState<string | null>(null)
  // 개발 모드(StrictMode)는 effect를 두 번 부른다. 같은 글에 조회 기록을 두 번 보내지 않게 마지막으로 보낸 글을 기억한다
  const viewedPostId = useRef<number | null>(null)

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

  const shownPostId = state.status === 'ok' ? state.post.id : null
  useEffect(() => {
    if (shownPostId === null || viewedPostId.current === shownPostId) {
      return
    }
    viewedPostId.current = shownPostId
    // 응답은 늘 204다(5분 안에 다시 보면 서버가 세지 않는다). 실패해도 글 읽기에는 지장이 없어 무시한다
    api(`/api/posts/${shownPostId}/views`, { method: 'POST', allowAnonymous: true }).catch(() => undefined)
  }, [shownPostId])

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
    setActionError(null)
    try {
      await api(`/api/posts/${post.id}`, { method: 'DELETE' })
      navigate('/')
    } catch (error) {
      setActionError(errorMessage(error))
    }
  }

  /** 공개 범위 바꾸기 (POST-06, 목업 post-detail 12). 성공(204)하면 화면의 값만 바꾼다 */
  async function changeVisibility(post: PostDetail, visibility: PostDetail['visibility']) {
    setActionError(null)
    try {
      await api(`/api/posts/${post.id}/visibility`, { method: 'PATCH', body: { visibility } })
      setState({ status: 'ok', post: { ...post, visibility } })
    } catch (error) {
      setActionError(errorMessage(error))
    }
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
            : <Article post={state.post} me={me} onDelete={remove} actionError={actionError}
                       onVisibilityChange={(visibility) => changeVisibility(state.post, visibility)}
                       onCommentCount={(count) => setState({ status: 'ok', post: { ...state.post, commentCount: count } })} />}
          {sidebar && <Sidebar modules={sidebar.modules} />}
        </div>
      </main>
    </div>
  )
}

function Article({ post, me, onDelete, onVisibilityChange, actionError, onCommentCount }: {
  post: PostDetail
  me: ReturnType<typeof useMe>
  onDelete: (post: PostDetail) => void
  onVisibilityChange: (visibility: PostDetail['visibility']) => void
  actionError: string | null
  onCommentCount: (count: number) => void
}) {
  return (
    <article className="stack" style={{ gap: 20 }}>
      <header className="post-head">
        <div className="small muted">
          <Link to={`/category/${post.category?.id ?? 0}`}>{post.category?.name ?? '미분류'}</Link>
        </div>
        <h1>{post.title}</h1>
        <div className="row small muted num">
          <AuthorName author={post.author} />
          {post.publishedAt && <span>{formatDateTime(post.publishedAt)}</span>}
          {post.updatedAt && <span>수정 {formatDateTime(post.updatedAt)}</span>}
          <span>조회 {post.viewCount.toLocaleString()}</span>
          {post.viewer.isOwner && post.visibility !== 'PUBLIC' && <span className="chip">비공개</span>}
        </div>
        {post.viewer.isOwner && (
          <div className="row">
            <select value={post.visibility} style={{ width: 'auto' }} aria-label="공개 범위"
                    onChange={(event) => onVisibilityChange(event.target.value as PostDetail['visibility'])}>
              <option value="PUBLIC">공개</option>
              <option value="PRIVATE">비공개</option>
              {/* 구독자 공개는 구독(SUB-01)이 생기면 고를 수 있다. 그 전에 만들어진 글만 그대로 보여 준다 */}
              {post.visibility === 'SUBSCRIBERS' && <option value="SUBSCRIBERS" disabled>구독자 공개</option>}
            </select>
            <Link className="btn" to={`/manage/posts/${post.id}/edit`}>수정</Link>
            <button className="btn" type="button" onClick={() => onDelete(post)}>삭제</button>
          </div>
        )}
        {actionError && <p className="err" role="alert">{actionError}</p>}
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
      <LikeButton key={`like-${post.id}`} postId={post.id} me={me} initialLiked={post.viewer.liked}
                  initialCount={post.likeCount} />
      <nav className="row between small" aria-label="이전·다음 글">
        <span>이전 글 {post.prev ? <Link to={`/${post.prev.id}`}>{post.prev.title}</Link> : <span className="muted">없음</span>}</span>
        <span>다음 글 {post.next ? <Link to={`/${post.next.id}`}>{post.next.title}</Link> : <span className="muted">없음</span>}</span>
      </nav>
      <SimilarPosts key={`similar-${post.id}`} postId={post.id} />
      <Comments key={`comments-${post.id}`} postId={post.id} me={me} commentAllowed={post.commentAllowed}
                onCountChange={onCommentCount} />
    </article>
  )
}
