import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { MeState } from './useMe'

/** 알림을 읽었다고 머리글에 알리는 브라우저 이벤트 이름. 알림 화면이 읽음·모두 읽음 뒤에 보낸다 */
export const NOTIFICATIONS_CHANGED = 'notifications-changed'

/**
 * 머리글의 읽지 않은 알림 수 (SUB-04). 처음 값은 이미 받은 내 정보(/api/me)의 수를 쓰고,
 * 알림 화면에서 읽으면 GET /api/me/notifications/unread-count로 다시 받는다(새로고침 없이 "알림 2" → "알림").
 */
export function useUnreadCount(me: MeState): number {
  const initial = me.status === 'member' ? me.me.unreadNotificationCount : 0
  const [count, setCount] = useState<number | null>(null)

  useEffect(() => {
    if (me.status !== 'member') {
      return
    }
    const refresh = () => {
      api<{ count: number }>('/api/me/notifications/unread-count')
        .then((result) => setCount(result.count))
        .catch(() => undefined)
    }
    window.addEventListener(NOTIFICATIONS_CHANGED, refresh)
    return () => window.removeEventListener(NOTIFICATIONS_CHANGED, refresh)
  }, [me.status])

  return count ?? initial
}
