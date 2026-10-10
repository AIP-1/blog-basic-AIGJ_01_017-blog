import { useEffect, useState } from 'react'
import { api, redirectToLogin } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { CursorResponse } from '../../api/types'
import { formatDateTime } from '../../app/format'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/** 알림 하나 (GET /api/me/notifications). link는 갈 화면의 전체 주소다(블로그 주소는 호스트가 달라서) */
interface NotificationItem {
  id: number
  type: 'COMMENT' | 'REPLY' | 'LIKE' | 'SUBSCRIBE' | 'SANCTION'
  message: string
  link: string
  read: boolean
  createdAt: string
}

/**
 * 알림 (SUB-04, 목업 notifications). 머리글의 "알림 n"으로 온다. 20개씩 더보기, 누르면 읽음 처리 후 그 화면으로 간다.
 * 대상이 지워졌거나 볼 수 없게 된 알림은 서버가 빼고 준다.
 */
export default function NotificationsPage() {
  const me = useMe()
  const [items, setItems] = useState<NotificationItem[]>([])
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
    api<CursorResponse<NotificationItem>>('/api/me/notifications')
      .then((page) => {
        if (active) {
          setItems(page.content)
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
    const page = await api<CursorResponse<NotificationItem>>(
      `/api/me/notifications?cursor=${encodeURIComponent(nextCursor)}`)
    setItems((previous) => [...previous, ...page.content])
    setNextCursor(page.nextCursor)
  }

  /** 읽음으로 바꾼 뒤 그 화면으로. 블로그 주소는 다른 호스트라 페이지를 새로 연다 */
  async function open(item: NotificationItem) {
    if (!item.read) {
      await api(`/api/me/notifications/${item.id}/read`, { method: 'PUT' }).catch(() => undefined)
    }
    window.location.assign(item.link)
  }

  async function readAll() {
    try {
      await api('/api/me/notifications/read-all', { method: 'PUT' })
      setItems((previous) => previous.map((item) => ({ ...item, read: true })))
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  const unread = items.filter((item) => !item.read).length

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <section className="section" style={{ maxWidth: 760 }}>
          <div className="row between">
            <h2>알림</h2>
            <button className="btn" type="button" onClick={readAll} disabled={unread === 0}>모두 읽음</button>
          </div>
          {error && <p className="err">{error}</p>}
          {loaded && items.length === 0 && <p className="muted">알림이 없습니다.</p>}
          <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {items.map((item) => (
              <li key={item.id}>
                <button type="button" className="btn ghost" onClick={() => open(item)}
                        style={{ width: '100%', justifyContent: 'space-between', textAlign: 'left',
                          fontWeight: item.read ? 'normal' : 'bold' }}>
                  <span>{item.read ? '' : '● '}{item.message}</span>
                  <span className="small muted num">{formatDateTime(item.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
          {nextCursor && (
            <button className="btn" type="button" style={{ justifySelf: 'center' }} onClick={loadMore}>더보기</button>
          )}
        </section>
      </main>
    </div>
  )
}
