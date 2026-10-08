import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { CursorResponse, PostSummary } from '../../api/types'
import { formatDateTime } from '../../app/format'
import { blogUrl } from '../../app/host'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 플랫폼 홈 (HOME-01). 모든 블로그의 최신 글을 20개씩 더보기로. 서버가 볼 수 있는 글만 준다.
 * 글은 다른 호스트(블로그 주소)라 링크는 <a href>로 새 페이지를 연다. 인기 글·주제별 글은 스텝 8·9.
 */
export default function HomePage() {
  const me = useMe()
  const [posts, setPosts] = useState<PostSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function load(cursor: string | null) {
    try {
      const page = await api<CursorResponse<PostSummary>>(
        `/api/home/latest${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`, { allowAnonymous: true })
      setPosts((previous) => (cursor ? [...previous, ...page.content] : page.content))
      setNextCursor(page.nextCursor)
      setError(null)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setLoaded(true)
    }
  }

  useEffect(() => {
    let active = true
    api<CursorResponse<PostSummary>>('/api/home/latest', { allowAnonymous: true })
      .then((page) => {
        if (active) {
          setPosts(page.content)
          setNextCursor(page.nextCursor)
          setLoaded(true)
        }
      })
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [])

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <section className="section">
          <h2>최신 글</h2>
          {error && <p className="err">{error}</p>}
          {loaded && posts.length === 0 && <p className="muted">아직 글이 없습니다.</p>}
          <div className="post-list">
            {posts.map((post) => (
              <article key={post.id} className={post.thumbnailUrl ? 'post-item has-thumb' : 'post-item'}>
                <div className="stack" style={{ gap: 2 }}>
                  <h3><a href={blogUrl(post.blog.address, `/${post.id}`)}>{post.title}</a></h3>
                  {post.summary && <p>{post.summary}</p>}
                  <div className="meta">
                    <a href={blogUrl(post.blog.address)}>{post.blog.name}</a>
                    <span>{formatDateTime(post.publishedAt)}</span>
                    <span>공감 {post.likeCount}</span>
                    <span>댓글 {post.commentCount}</span>
                  </div>
                </div>
                {/* 썸네일은 플랫폼 주소에서도 같은 서버의 /uploads라 그대로 연다 */}
                {post.thumbnailUrl && <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" />}
              </article>
            ))}
          </div>
          {nextCursor && <button className="btn" type="button" style={{ justifySelf: 'center' }}
                                 onClick={() => load(nextCursor)}>더보기</button>}
        </section>
      </main>
    </div>
  )
}
