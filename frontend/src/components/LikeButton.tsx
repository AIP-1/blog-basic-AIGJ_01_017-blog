import { useRef, useState } from 'react'
import { api, redirectToLogin } from '../api/client'
import { errorMessage } from '../api/errors'
import type { MeState } from '../app/useMe'

/**
 * 공감 버튼 (SOC-01, T048). 누르면 화면부터 바로 바꾸고(즉시 반영), 서버 응답의 실제 값으로 맞춘다.
 * 서버는 켜기 PUT·끄기 DELETE라 몇 번 와도 결과가 같고, 진행 중에는 ref로 다음 클릭을 막는다.
 * 비회원은 로그인 화면으로 갔다가 이 글로 돌아온다(spec US3 시나리오 6).
 */
export default function LikeButton({ postId, me, initialLiked, initialCount }: {
  postId: number
  me: MeState
  initialLiked: boolean
  initialCount: number
}) {
  const [liked, setLiked] = useState(initialLiked)
  const [count, setCount] = useState(initialCount)
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
    const next = !liked
    // 먼저 바꿔 보여 주고(낙관적 갱신), 실패하면 되돌린다
    setLiked(next)
    setCount(count + (next ? 1 : -1))
    setError(null)
    try {
      const result = await api<{ liked: boolean; likeCount: number }>(`/api/posts/${postId}/like`,
        { method: next ? 'PUT' : 'DELETE' })
      setLiked(result.liked)
      setCount(result.likeCount)
    } catch (caught) {
      setLiked(liked)
      setCount(count)
      setError(errorMessage(caught))
    } finally {
      inFlight.current = false
    }
  }

  return (
    <div className="row small">
      <button type="button" className={liked ? 'btn on' : 'btn'} aria-pressed={liked} onClick={toggle}>
        {liked ? '♥' : '♡'} 공감 <span className="num">{count}</span>
      </button>
      {error && <span className="err">{error}</span>}
    </div>
  )
}
