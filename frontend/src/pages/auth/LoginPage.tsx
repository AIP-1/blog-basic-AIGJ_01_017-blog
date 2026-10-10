import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Me, SuspensionDetail } from '../../api/types'
import { safeRedirect } from '../../app/host'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'
import SuspensionNotice from '../../components/SuspensionNotice'

/**
 * 로그인 (AUTH-01, AUTH-03, ADMIN-02). 로그인이 필요한 화면에서 오면 redirect에 원래 주소가 붙고,
 * 로그인 뒤 그 주소로 돌아간다(우리 서비스 주소만).
 */
export default function LoginPage() {
  const me = useMe()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [suspension, setSuspension] = useState<SuspensionDetail | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setSuspension(null)
    try {
      await api<Me>('/api/auth/login', { method: 'POST', body: { email: email.trim(), password, rememberMe } })
      const redirect = safeRedirect(params.get('redirect'))
      if (redirect) {
        // 블로그 주소는 다른 호스트라 페이지를 새로 연다. 쿠키가 .{플랫폼}이라 거기서도 로그인 상태다
        window.location.assign(redirect)
      } else {
        navigate('/')
      }
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'MEMBER_SUSPENDED') {
        setSuspension(caught.detail as unknown as SuspensionDetail)
      } else {
        setError(errorMessage(caught))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page narrow">
        <h2 style={{ fontSize: 22 }}>로그인</h2>
        <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
          <label className="field">
            <span className="label">이메일</span>
            <input type="email" value={email} autoComplete="email" required
                   onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label className="field">
            <span className="label">비밀번호</span>
            <input type="password" value={password} autoComplete="current-password" required
                   onChange={(event) => setPassword(event.target.value)} />
          </label>
          <label className="row small">
            <input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} />
            로그인 상태 유지
          </label>
          <span className="hint" style={{ marginTop: -8 }}>
            {rememberMe
              ? '브라우저를 닫아도 14일 동안 로그인이 유지됩니다. 여럿이 쓰는 컴퓨터에서는 고르지 마세요.'
              : '고르지 않으면 브라우저를 닫거나 30분 동안 아무것도 하지 않을 때 로그아웃됩니다.'}
          </span>
          {error && <p className="err">{error}</p>}
          {suspension && <SuspensionNotice suspension={suspension} />}
          <button className="btn primary" type="submit" disabled={submitting}>로그인</button>
          <div className="row between small"><span /><Link to="/signup">회원가입</Link></div>
        </form>
      </main>
    </div>
  )
}
