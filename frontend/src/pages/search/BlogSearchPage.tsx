import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { PageResponse, PostSummary, Sidebar as SidebarData } from '../../api/types'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import BlogHeader from '../../components/BlogHeader'
import ErrorPage from '../../components/ErrorPage'
import Pagination from '../../components/Pagination'
import { PostItem } from '../../components/PostItem'
import Sidebar from '../../components/Sidebar'

/**
 * 블로그 안 검색 결과 (SRCH-01, T048). 검색어와 페이지가 주소(?q=&page=)에 있어 결과 주소를 그대로 나눌 수 있고,
 * 새로고침해도 검색어가 남는다. 서버가 보는 사람이 볼 수 있는 글만 준다.
 */
export default function BlogSearchPage() {
  const me = useMe()
  const [blogState] = useBlog()
  const [params] = useSearchParams()
  const q = params.get('q') ?? ''
  const page = Math.max(Number(params.get('page')) || 1, 1)
  const [result, setResult] = useState<PageResponse<PostSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sidebar, setSidebar] = useState<SidebarData | null>(null)

  useEffect(() => {
    api<SidebarData>('/api/blog/sidebar', { allowAnonymous: true }).then(setSidebar).catch(() => setSidebar(null))
  }, [])

  useEffect(() => {
    let active = true
    if (!q.trim()) {
      return
    }
    const query = new URLSearchParams({ q, page: String(page) })
    api<PageResponse<PostSummary>>(`/api/search?${query}`, { allowAnonymous: true })
      .then((found) => {
        if (active) {
          setResult(found)
          setError(null)
        }
      })
      .catch((caught: unknown) => {
        if (active) {
          setResult(null)
          setError(caught instanceof ApiError && caught.fieldErrors.length > 0
            ? caught.fieldErrors[0].reason : errorMessage(caught))
        }
      })
    return () => {
      active = false
    }
  }, [q, page])

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
          <div className="stack" style={{ gap: 18 }}>
            <h2 style={{ fontSize: 17 }}>
              {q.trim() ? <>'{q}' 검색 결과 <span className="muted num">{result?.totalElements ?? ''}</span></>
                : '검색어를 입력해 주세요'}
            </h2>
            {error && <p className="err">{error}</p>}
            {result && result.content.length === 0 && <p className="muted">검색 결과가 없습니다.</p>}
            {result && result.content.length > 0 && (
              <div className="post-list">
                {result.content.map((post) => <PostItem key={post.id} post={post} owner={blog.viewer.isOwner} />)}
              </div>
            )}
            {result && (
              <Pagination page={result.page} totalPages={result.totalPages}
                          href={(number) => `/search?${new URLSearchParams({ q, page: String(number) })}`} />
            )}
          </div>
          {sidebar && <Sidebar modules={sidebar.modules} />}
        </div>
      </main>
    </div>
  )
}
