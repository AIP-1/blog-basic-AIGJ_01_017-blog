import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { BlogCard, PostSummary, RankedBlogs, RankedPosts } from '../../api/types'
import { formatDateTime, formatTime } from '../../app/format'
import { blogUrl } from '../../app/host'
import { useMe } from '../../app/useMe'
import BlogCardItem from '../../components/BlogCardItem'
import PlatformHeader from '../../components/PlatformHeader'

type Tab = 'posts' | 'bloggers'

/**
 * 랭킹 전체보기 (HOME-05, 목업 ranking). 인기 글·인기 블로거 탭, 100위까지 20개씩 더보기. 홈과 같은 기준·같은 시점이다.
 * 더보기 때 처음 받은 기준 시각(snapshotAt)을 함께 보내고, 그 사이 순위가 새로 계산됐으면 서버가 409와 새 기준 시각을 준다.
 * 그러면 안내를 띄우고 새 기준으로 처음부터 다시 받는다(중복·누락이 없게).
 */
export default function RankingPage() {
  const me = useMe()
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'bloggers' ? 'bloggers' : 'posts'
  const [snapshotAt, setSnapshotAt] = useState<string | null>(null)
  const [posts, setPosts] = useState<{ rank: number; post: PostSummary }[]>([])
  const [bloggers, setBloggers] = useState<BlogCard[]>([])
  const [nextOffset, setNextOffset] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  /** 첫 묶음을 받아 화면 상태로 바꾼다. 받기는 fetchFirst(상태를 건드리지 않는 함수)가 한다 */
  const apply = useCallback((first: First) => {
    if (first.tab === 'posts') {
      setPosts(first.page.items)
    } else {
      setBloggers(first.page.items)
    }
    setSnapshotAt(first.page.snapshotAt)
    setNextOffset(first.page.nextOffset)
    setError(null)
  }, [])

  useEffect(() => {
    let active = true
    fetchFirst(tab)
      .then((first) => active && apply(first))
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [tab, apply])

  function changeTab(next: Tab) {
    setNotice(null)
    setParams(next === 'bloggers' ? { tab: 'bloggers' } : {})
  }

  async function loadMore() {
    if (nextOffset === null || !snapshotAt) {
      return
    }
    const query = new URLSearchParams({ snapshotAt, offset: String(nextOffset) })
    try {
      if (tab === 'posts') {
        const page = await api<RankedPosts>(`/api/ranking/posts?${query}`, { allowAnonymous: true })
        setPosts((previous) => [...previous, ...page.items])
        setNextOffset(page.nextOffset)
      } else {
        const page = await api<RankedBlogs>(`/api/ranking/bloggers?${query}`, { allowAnonymous: true })
        setBloggers((previous) => [...previous, ...page.items])
        setNextOffset(page.nextOffset)
      }
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'RANKING_UPDATED') {
        const next = String(caught.detail?.snapshotAt ?? '')
        setNotice(`순위가 새로 계산됐습니다. ${next ? formatTime(next) + ' 기준으로 ' : ''}처음부터 다시 보여 드릴게요.`)
        apply(await fetchFirst(tab))
      } else {
        setError(errorMessage(caught))
      }
    }
  }

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page" style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
        <h1 style={{ fontSize: 22 }}>랭킹</h1>
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={tab === 'posts'} className={tab === 'posts' ? 'on' : undefined}
                  onClick={() => changeTab('posts')}>인기 글</button>
          <button type="button" role="tab" aria-selected={tab === 'bloggers'} className={tab === 'bloggers' ? 'on' : undefined}
                  onClick={() => changeTab('bloggers')}>인기 블로거</button>
        </div>
        {snapshotAt && (
          <p className="small muted" style={{ margin: 0 }}>
            {formatDateTime(snapshotAt)} 기준 · {tab === 'posts'
              ? '최근 1시간 조회×1 + 공감×3 + 댓글×5, 5분마다 갱신'
              : '최근 7일 블로그 점수(조회·공감·댓글·새 구독자), 1시간마다 갱신'}
          </p>
        )}
        {notice && <p className="box small" role="status">{notice}</p>}
        {error && <p className="err" role="alert">{error}</p>}
        {tab === 'posts' && (
          posts.length === 0
            ? <p className="muted">아직 순위가 없습니다.</p>
            : (
              <div className="table-wrap">
                <table className="manage-table">
                  <thead><tr><th className="num">순위</th><th>글</th><th>블로그</th><th className="num">공감</th></tr></thead>
                  <tbody>
                    {posts.map(({ rank, post }) => (
                      <tr key={post.id}>
                        <td className="num">{rank}</td>
                        <td className="title"><a href={blogUrl(post.blog.address, `/${post.id}`)}>{post.title}</a></td>
                        <td className="small"><a href={blogUrl(post.blog.address)}>{post.blog.name}</a></td>
                        <td className="num">{post.likeCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
        )}
        {tab === 'bloggers' && (
          bloggers.length === 0
            ? <p className="muted">아직 순위가 없습니다.</p>
            : <div className="rank-list">{bloggers.map((card) => <BlogCardItem key={card.blog.id} card={card} />)}</div>
        )}
        {nextOffset !== null && (
          <button className="btn" type="button" style={{ justifySelf: 'center' }} onClick={loadMore}>
            {nextOffset + 1}~{nextOffset + 20}위 더보기
          </button>
        )}
      </main>
    </div>
  )
}

type First = { tab: 'posts'; page: RankedPosts } | { tab: 'bloggers'; page: RankedBlogs }

/** 탭의 첫 묶음(기준 시각 없이 = 지금 순위) */
async function fetchFirst(tab: Tab): Promise<First> {
  return tab === 'posts'
    ? { tab, page: await api<RankedPosts>('/api/ranking/posts', { allowAnonymous: true }) }
    : { tab, page: await api<RankedBlogs>('/api/ranking/bloggers', { allowAnonymous: true }) }
}
