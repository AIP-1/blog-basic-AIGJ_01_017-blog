import { useEffect, useState } from 'react'
import { api, redirectToLogin } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { CursorResponse, SavedPost } from '../../api/types'
import { formatDate } from '../../app/format'
import { blogUrl } from '../../app/host'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import MeTabs from '../../components/MeTabs'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 저장한 글 (SOC-03, blog.com/me/bookmarks). 본인만, 저장 최신순 20개씩 더보기. 볼 수 없게 된 글은 저장할 때의 제목과
 * "볼 수 없는 글" 표시로 남고, 누르면 글로 가지 않고 안내만 한다(이유는 알리지 않는다). 각 줄에서 바로 저장을 취소한다.
 */
export default function BookmarksPage() {
  const me = useMe()
  const [items, setItems] = useState<SavedPost[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
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
    api<CursorResponse<SavedPost>>('/api/me/bookmarks')
      .then((page) => {
        setItems(page.content)
        setNextCursor(page.nextCursor)
        setLoaded(true)
      })
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [member])

  async function loadMore() {
    if (!nextCursor) {
      return
    }
    try {
      const page = await api<CursorResponse<SavedPost>>(`/api/me/bookmarks?cursor=${encodeURIComponent(nextCursor)}`)
      setItems((previous) => [...previous, ...page.content])
      setNextCursor(page.nextCursor)
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  async function remove(postId: number) {
    try {
      await api(`/api/me/bookmarks/${postId}`, { method: 'DELETE' })
      setItems((previous) => previous.filter((item) => item.postId !== postId))
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page" style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
        <MeTabs />
        <section className="section">
          <h2>저장한 글</h2>
          {notice && <p className="box small" role="status">{notice}</p>}
          {error && <p className="err" role="alert">{error}</p>}
          {loaded && items.length === 0 && (
            <p className="muted">저장한 글이 없습니다. 글 아래 저장 버튼으로 나중에 읽을 글을 모아 보세요.</p>
          )}
          <div className="post-list">
            {items.map((item) => (
              <article key={item.postId} className="post-item">
                <div className="stack" style={{ gap: 2 }}>
                  {item.visible
                    ? (
                      <>
                        <h3><a href={blogUrl(item.post.blog.address, `/${item.post.id}`)}>{item.post.title}</a></h3>
                        <div className="meta">
                          <span>{item.post.blog.name}</span>
                          <span>저장 {formatDate(item.savedAt)}</span>
                        </div>
                      </>
                    )
                    : (
                      <>
                        <h3>
                          <button className="btn ghost" type="button" style={{ padding: 0 }}
                                  onClick={() => setNotice('더 이상 볼 수 없는 글입니다.')}>
                            {item.titleSnapshot}
                          </button>
                        </h3>
                        <div className="meta">
                          <span>{item.blogNameSnapshot}</span>
                          <span className="chip">볼 수 없는 글</span>
                          <span>저장 {formatDate(item.savedAt)}</span>
                        </div>
                      </>
                    )}
                </div>
                <button className="btn small" type="button" onClick={() => remove(item.postId)}>저장 취소</button>
              </article>
            ))}
          </div>
          {nextCursor && <button className="btn" type="button" style={{ justifySelf: 'center' }} onClick={loadMore}>더보기</button>}
        </section>
      </main>
    </div>
  )
}
