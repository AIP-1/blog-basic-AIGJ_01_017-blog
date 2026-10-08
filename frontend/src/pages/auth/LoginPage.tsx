import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Me, SuspensionDetail } from '../../api/types'
import { formatDateTime } from '../../app/format'
import { safeRedirect } from '../../app/host'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

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
          {error && <p className="err">{error}</p>}
          {suspension && (
            <div className="box danger" role="alert">
              <b>이용이 정지된 계정입니다</b>
              {suspension.reasonMessage && <span>사유: {suspension.reasonMessage}</span>}
              <span className="num">
                {suspension.suspendedUntil ? `정지 기한: ${formatDateTime(suspension.suspendedUntil)}까지` : '영구 정지'}
              </span>
            </div>
          )}
          <button className="btn primary" type="submit" disabled={submitting}>로그인</button>
          <div className="row between small"><span /><Link to="/signup">회원가입</Link></div>
        </form>
      </main>
    </div>
  )
}
