import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, redirectToLogin } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { BlogCard, CursorResponse, PostSummary } from '../../api/types'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import BlogCardItem from '../../components/BlogCardItem'
import PlatformHeader from '../../components/PlatformHeader'
import PlatformPostItem from '../../components/PlatformPostItem'
import SubscribeButton from '../../components/SubscribeButton'

/**
 * 구독 피드 (SUB-02, 목업 feed). 플랫폼 머리글의 "구독 피드"로 온다. 회원만이고 비회원은 로그인 화면으로 갔다가 돌아온다.
 * 구독한 블로그의 새 글 최신순 20개씩 더보기(커서라 읽는 중에 새 글이 올라와도 겹치거나 빠지지 않는다).
 * 옆에 추천 블로그 5(SUB-06, 목업 2번): 인기 블로거 순위에서 내 블로그와 이미 구독한 블로그를 뺀 것. 구독한 블로그가 없을 때도 보인다.
 */
export default function FeedPage() {
  const me = useMe()
  const [posts, setPosts] = useState<PostSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (shouldRedirectToLogin(me)) {
      redirectToLogin()
    }
  }, [me])

  const member = me.status === 'member'
  useEffect(() => {
    if (!member) {
      return
    }
    let active = true
    api<CursorResponse<PostSummary>>('/api/feed')
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
  }, [member])

  async function loadMore() {
    if (!nextCursor) {
      return
    }
    try {
      const page = await api<CursorResponse<PostSummary>>(`/api/feed?cursor=${encodeURIComponent(nextCursor)}`)
      setPosts((previous) => [...previous, ...page.content])
      setNextCursor(page.nextCursor)
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <section className="section" style={{ maxWidth: 760 }}>
          <h2>구독한 블로그의 새 글</h2>
          {error && <p className="err">{error}</p>}
          {loaded && posts.length === 0 && (
            <p className="muted">
              아직 구독한 블로그가 없거나 새 글이 없습니다. <Link to="/">홈</Link>이나 검색에서 마음에 드는 블로그를 구독해 보세요.
            </p>
          )}
          <div className="post-list">
            {posts.map((post) => <PlatformPostItem key={post.id} post={post} />)}
          </div>
          {nextCursor && (
            <button className="btn" type="button" style={{ justifySelf: 'center' }} onClick={loadMore}>더보기</button>
          )}
        </section>
        {member && <RecommendedBlogs me={me} />}
      </main>
    </div>
  )
}

/** 추천 블로그 5 (SUB-06). 구독하면 그 줄에 "구독 중"이 된다(목록은 다음에 열 때 바뀐다) */
function RecommendedBlogs({ me }: { me: ReturnType<typeof useMe> }) {
  const [cards, setCards] = useState<BlogCard[] | null>(null)

  useEffect(() => {
    api<BlogCard[]>('/api/recommend/blogs').then(setCards).catch(() => setCards([]))
  }, [])

  if (!cards || cards.length === 0) {
    return null
  }
  return (
    <section className="section" style={{ maxWidth: 760 }} aria-label="추천 블로그">
      <h2>추천 블로그</h2>
      <div className="rank-list">
        {cards.map((card) => (
          <BlogCardItem key={card.blog.id} card={card} showRank={false}>
            <SubscribeButton blogId={card.blog.id} me={me} subscribed={false} onChange={() => undefined} />
          </BlogCardItem>
        ))}
      </div>
    </section>
  )
}
