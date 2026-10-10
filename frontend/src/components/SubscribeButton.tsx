import { useState } from 'react'
import { api, loginUrl } from '../api/client'
import { errorMessage } from '../api/errors'
import type { MeState } from '../app/useMe'

/**
 * 구독 버튼 (SUB-01, SUB-03). 누르면 PUT, 구독 중이면 DELETE이고, 서버가 돌려준 구독자 수로 바로 바꾼다.
 * PUT·DELETE는 같은 요청을 두 번 보내도 결과가 같아 연타 방지 키가 필요 없다(서버도 한 번으로 센다).
 * 비회원은 로그인 화면으로 갔다가 이 화면으로 돌아온다. 자기 블로그에는 그리지 않는다(부르는 쪽이 정함).
 */
export default function SubscribeButton({ blogId, me, subscribed, onChange }: {
  blogId: number
  me: MeState
  subscribed: boolean
  onChange: (subscribed: boolean, subscriberCount: number) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (me.status === 'anonymous') {
    return <a className="btn primary" href={loginUrl()}>로그인하고 구독하기</a>
  }

  async function toggle() {
    setBusy(true)
    setError(null)
    try {
      const result = await api<{ subscribed: boolean; subscriberCount: number }>(
        `/api/blogs/${blogId}/subscription`, { method: subscribed ? 'DELETE' : 'PUT' })
      onChange(result.subscribed, result.subscriberCount)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="row nowrap">
      <button className={subscribed ? 'btn' : 'btn primary'} type="button" disabled={busy || me.status !== 'member'}
              onClick={toggle} aria-pressed={subscribed}>
        {subscribed ? '구독 중' : '구독하기'}
      </button>
      {error && <span className="err small" role="alert">{error}</span>}
    </span>
  )
}
