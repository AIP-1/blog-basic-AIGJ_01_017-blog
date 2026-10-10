import { type FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, api, redirectToLogin } from '../../api/client'
import { errorMessage, retryAfterSeconds, waitText } from '../../api/errors'
import type { MyBlog } from '../../api/types'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 회원 탈퇴 (AUTH-06, blog.com/me/withdraw). 무엇이 사라지는지 보여 주고, 비밀번호로 본인 확인과 한 번 더 확인을 받는다.
 * 탈퇴하면 서버가 로그인 쿠키를 지우므로 홈으로 새로 연다(머리글이 비회원으로 그려진다).
 * 비밀번호가 틀려도 로그인 화면으로 보내지 않도록 401을 이 화면에서 받는다(allowAnonymous).
 */
export default function WithdrawPage() {
  const me = useMe()
  const [blogs, setBlogs] = useState<MyBlog[] | null>(null)
  const [password, setPassword] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (shouldRedirectToLogin(me)) {
      redirectToLogin()
    }
  }, [me])

  useEffect(() => {
    if (me.status === 'member') {
      api<MyBlog[]>('/api/me/blogs').then(setBlogs).catch(() => setBlogs([]))
    }
  }, [me.status])

  async function withdraw(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      await api('/api/me', { method: 'DELETE', body: { password }, allowAnonymous: true })
      window.location.href = '/'
    } catch (caught) {
      const wait = retryAfterSeconds(caught)
      if (wait !== null) {
        setError(`비밀번호를 여러 번 틀렸습니다. ${waitText(wait)} 뒤에 다시 시도해 주세요.`)
      } else if (caught instanceof ApiError && caught.status === 401) {
        setError('비밀번호가 맞지 않습니다.')
      } else {
        setError(errorMessage(caught))
      }
      setSending(false)
    }
  }

  if (me.status !== 'member') {
    return <div className="app"><PlatformHeader me={me} /></div>
  }
  const postCount = blogs?.reduce((sum, blog) => sum + blog.postCount, 0) ?? 0
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page narrow">
        <form className="stack" onSubmit={withdraw} style={{ maxWidth: 480 }} aria-label="회원 탈퇴">
          <h1 style={{ fontSize: 22 }}>회원 탈퇴</h1>
          <div className="box danger" role="note">
            <b>탈퇴하면 되돌릴 수 없습니다</b>
            <span className="small">
              {blogs === null ? '블로그와 글, 댓글이 모두 삭제됩니다.'
                : `블로그 ${blogs.length}개와 글 ${postCount}개, 댓글이 모두 삭제됩니다.`}
              {' '}블로그 주소는 다시 쓸 수 없고, 누른 공감과 구독도 사라집니다.
              답글이 달린 댓글은 "삭제된 댓글입니다"로 자리만 남습니다.
            </span>
          </div>
          {me.me.hasPassword
            ? (
              <label className="field">
                <span className="label">비밀번호</span>
                <input type="password" value={password} autoComplete="current-password"
                       onChange={(event) => setPassword(event.target.value)} />
              </label>
            )
            : <p className="small muted">소셜로 가입한 회원의 본인 확인(소셜 재인증)은 소셜 로그인과 함께 열립니다.</p>}
          <label className="row small">
            <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
            위 내용을 확인했고 탈퇴합니다
          </label>
          {error && <p className="err" role="alert">{error}</p>}
          <div className="row">
            <button className="btn danger" type="submit"
                    disabled={sending || !agreed || !me.me.hasPassword || !password}>탈퇴하기</button>
            <Link className="btn" to="/me">취소</Link>
          </div>
        </form>
      </main>
    </div>
  )
}
