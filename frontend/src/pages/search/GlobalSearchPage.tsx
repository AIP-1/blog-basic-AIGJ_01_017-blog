import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { FoundBlog, PageResponse, PostSummary } from '../../api/types'
import { blogUrl } from '../../app/host'
import { useMe } from '../../app/useMe'
import AuthorName from '../../components/AuthorName'
import Pagination from '../../components/Pagination'
import PlatformHeader from '../../components/PlatformHeader'
import PlatformPostItem from '../../components/PlatformPostItem'

type Tab = 'post' | 'blog'
type Result = { tab: 'post'; page: PageResponse<PostSummary> } | { tab: 'blog'; page: PageResponse<FoundBlog> }

/**
 * 전체 검색 (SRCH-02, 목업 search). 플랫폼 머리글의 검색창으로 온다. 글·블로그 탭은 같은 API를 type만 바꿔 부르고,
 * 검색어·탭·페이지는 주소(?q=&type=&page=)에 둬서 새로고침·공유해도 같다. 서버가 남에게 보이는 글·블로그만 준다.
 */
export default function GlobalSearchPage() {
  const me = useMe()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const q = params.get('q') ?? ''
  const tab: Tab = params.get('type') === 'blog' ? 'blog' : 'post'
  const page = Math.max(Number(params.get('page')) || 1, 1)
  const [input, setInput] = useState(q)
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    if (!q.trim()) {
      return
    }
    const query = new URLSearchParams({ q, type: tab, page: String(page) })
    const loading = tab === 'post'
      ? api<PageResponse<PostSummary>>(`/api/search?${query}`, { allowAnonymous: true })
        .then((found): Result => ({ tab: 'post', page: found }))
      : api<PageResponse<FoundBlog>>(`/api/search?${query}`, { allowAnonymous: true })
        .then((found): Result => ({ tab: 'blog', page: found }))
    loading
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
  }, [q, tab, page])

  function href(next: { type?: Tab; page?: number }) {
    return `/search?${new URLSearchParams({ q, type: next.type ?? tab, page: String(next.page ?? 1) })}`
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (input.trim()) {
      navigate(`/search?${new URLSearchParams({ q: input.trim(), type: tab })}`)
    }
  }

  const shown = result && result.tab === tab ? result : null

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <section className="section" style={{ maxWidth: 760 }}>
          <h2>검색</h2>
          <form className="row nowrap" role="search" onSubmit={submit}>
            <input type="search" value={input} maxLength={100} placeholder="글과 블로그 검색" aria-label="전체 검색"
                   onChange={(event) => setInput(event.target.value)} />
            <button className="btn primary" type="submit" disabled={!input.trim()}>검색</button>
          </form>
          {q.trim() && (
            <div className="tabs" role="tablist">
              <button type="button" role="tab" aria-selected={tab === 'post'} className={tab === 'post' ? 'on' : undefined}
                      onClick={() => navigate(href({ type: 'post' }))}>글</button>
              <button type="button" role="tab" aria-selected={tab === 'blog'} className={tab === 'blog' ? 'on' : undefined}
                      onClick={() => navigate(href({ type: 'blog' }))}>블로그</button>
            </div>
          )}
          {!q.trim() && <p className="muted">검색어를 입력해 주세요.</p>}
          {error && <p className="err">{error}</p>}
          {shown && (
            <p className="small muted num">{tab === 'post' ? '글' : '블로그'} {shown.page.totalElements}개</p>
          )}
          {shown && shown.page.content.length === 0 && (
            <p className="muted">'{q}'에 맞는 {tab === 'post' ? '글이' : '블로그가'} 없습니다. 다른 검색어로 찾아 보세요.</p>
          )}
          {shown?.tab === 'post' && (
            <div className="post-list">
              {shown.page.content.map((post) => <PlatformPostItem key={post.id} post={post} />)}
            </div>
          )}
          {shown?.tab === 'blog' && (
            <div className="stack">
              {shown.page.content.map((found) => <BlogRow key={found.blog.id} found={found} />)}
            </div>
          )}
          {shown && (
            <Pagination page={shown.page.page} totalPages={shown.page.totalPages}
                        href={(number) => href({ page: number })} />
          )}
        </section>
      </main>
    </div>
  )
}

/** 블로그 한 줄: 프로필 이미지, 이름(그 블로그로), 소개, 주인(대표 블로그 링크), 구독자 수 */
function BlogRow({ found }: { found: FoundBlog }) {
  const { blog } = found
  return (
    <div className="row nowrap" style={{ alignItems: 'flex-start' }}>
      {blog.profileImageUrl
        ? <img className="avatar lg" src={blog.profileImageUrl} alt="" />
        : <span className="avatar lg" />}
      <div className="stack" style={{ gap: 2, minWidth: 0 }}>
        <a href={blogUrl(blog.address)}><b>{blog.name}</b></a>
        {blog.description && <span className="small">{blog.description}</span>}
        <span className="small muted">
          주인 <AuthorName author={found.owner} /> · 구독 <span className="num">{found.subscriberCount}</span>
        </span>
      </div>
    </div>
  )
}
