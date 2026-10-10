import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { CursorResponse, PopularPosts, PostSummary, Topic } from '../../api/types'
import { formatTime } from '../../app/format'
import { blogUrl } from '../../app/host'
import { useMe } from '../../app/useMe'
import NoticeBand from '../../components/NoticeBand'
import PlatformHeader from '../../components/PlatformHeader'
import PlatformPostItem from '../../components/PlatformPostItem'

/**
 * 플랫폼 홈. 인기 글(HOME-02) 10개, 주제별 글(HOME-03) 6개, 모든 블로그의 최신 글(HOME-01) 20개씩 더보기.
 * 서버가 볼 수 있는 글만 준다. 글은 다른 호스트(블로그 주소)라 링크는 <a href>로 새 페이지를 연다.
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
        <NoticeBand />
        <PopularSection />
        <TopicSection />
        <section className="section">
          <h2>최신 글</h2>
          {error && <p className="err">{error}</p>}
          {loaded && posts.length === 0 && <p className="muted">아직 글이 없습니다.</p>}
          <div className="post-list">
            {posts.map((post) => <PlatformPostItem key={post.id} post={post} />)}
          </div>
          {nextCursor && <button className="btn" type="button" style={{ justifySelf: 'center' }}
                                 onClick={() => load(nextCursor)}>더보기</button>}
        </section>
      </main>
    </div>
  )
}

/** 인기 글. 최근 1시간 조회·공감·댓글로 매긴 순위이고, 서버가 5분마다 다시 계산한다. 실패하면 영역을 숨긴다. */
function PopularSection() {
  const [popular, setPopular] = useState<PopularPosts | null>(null)

  useEffect(() => {
    let active = true
    api<PopularPosts>('/api/home/popular', { allowAnonymous: true })
      .then((result) => active && setPopular(result))
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [])

  if (!popular) {
    return null
  }
  return (
    <section className="section">
      <h2>인기 글 <small>최근 1시간 · {formatTime(popular.snapshotAt)} 기준</small></h2>
      {popular.items.length === 0
        ? <p className="muted">최근 1시간 동안 읽힌 글이 없습니다.</p>
        : (
          <div className="rank-list">
            {popular.items.map(({ rank, post }) => (
              <div key={post.id} className="rank">
                <b>{rank}</b>
                <a href={blogUrl(post.blog.address, `/${post.id}`)}>{post.title}</a>
                <a className="small muted" href={blogUrl(post.blog.address)}>{post.blog.name}</a>
              </div>
            ))}
          </div>
        )}
    </section>
  )
}

/** 주제별 글. 주제 탭을 고르면 그 주제의 글 6개(인기 순, 모자라면 최신 글). 처음엔 첫 탭. */
function TopicSection() {
  const [topics, setTopics] = useState<Topic[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [posts, setPosts] = useState<PostSummary[] | null>(null)

  useEffect(() => {
    api<Topic[]>('/api/topics', { allowAnonymous: true })
      .then((result) => {
        setTopics(result)
        setSelected(result[0]?.code ?? null)
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    if (selected === null) {
      return
    }
    let active = true
    api<PostSummary[]>(`/api/home/topics/${selected}`, { allowAnonymous: true })
      .then((result) => active && setPosts(result))
      .catch(() => active && setPosts([]))
    return () => {
      active = false
    }
  }, [selected])

  if (topics.length === 0) {
    return null
  }
  return (
    <section className="section">
      <h2>주제별 글</h2>
      <nav className="tabs" aria-label="주제">
        {topics.map((topic) => (
          <button key={topic.code} type="button" className={topic.code === selected ? 'on' : undefined}
                  aria-pressed={topic.code === selected} onClick={() => setSelected(topic.code)}>
            {topic.name}
          </button>
        ))}
      </nav>
      {posts !== null && posts.length === 0 && <p className="muted">이 주제의 글이 아직 없습니다.</p>}
      <div className="grid-cards">
        {posts?.map((post) => (
          <a key={post.id} className="card" href={blogUrl(post.blog.address, `/${post.id}`)}>
            {post.thumbnailUrl ? <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" /> : <div className="thumb" />}
            <h3>{post.title}</h3>
            <span className="small muted">{post.blog.name} · 공감 {post.likeCount}</span>
          </a>
        ))}
      </div>
    </section>
  )
}
