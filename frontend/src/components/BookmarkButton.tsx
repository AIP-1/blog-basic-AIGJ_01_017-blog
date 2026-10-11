import { useRef, useState } from 'react'
import { api, redirectToLogin } from '../api/client'
import { errorMessage } from '../api/errors'
import type { MeState } from '../app/useMe'

/**
 * 저장 버튼 (SOC-03). 공감 버튼처럼 누르면 바로 바꿔 보이고(낙관적 갱신) 서버 응답으로 맞춘다.
 * 서버는 저장 PUT·취소 DELETE라 연달아 눌러도 한 번이다. 비회원은 로그인 화면으로 갔다가 돌아온다. 저장 수는 보여 주지 않는다.
 */
export default function BookmarkButton({ postId, me, initialBookmarked }: {
  postId: number
  me: MeState
  initialBookmarked: boolean
}) {
  const [saved, setSaved] = useState(initialBookmarked)
  const [error, setError] = useState<string | null>(null)
  const inFlight = useRef(false)

  async function toggle() {
    if (me.status !== 'member') {
      redirectToLogin()
      return
    }
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    const next = !saved
    setSaved(next)
    setError(null)
    try {
      const result = await api<{ bookmarked: boolean }>(`/api/posts/${postId}/bookmark`, { method: next ? 'PUT' : 'DELETE' })
      setSaved(result.bookmarked)
    } catch (caught) {
      setSaved(saved)
      setError(errorMessage(caught))
    } finally {
      inFlight.current = false
    }
  }

  return (
    <span className="row small">
      <button type="button" className={saved ? 'btn on' : 'btn'} aria-pressed={saved} onClick={toggle}>
        {saved ? '★ 저장됨' : '☆ 저장'}
      </button>
      {error && <span className="err">{error}</span>}
    </span>
  )
}
