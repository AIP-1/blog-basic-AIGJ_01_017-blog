import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages, retryAfterSeconds, waitText } from '../../api/errors'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 비밀번호 재설정 (OWN-02, T088). 두 단계다.
 * - /password-reset: 가입한 이메일로 재설정 메일 받기. 가입하지 않은 이메일이어도 같은 문구를 보인다.
 * - /password-reset?token=…: 메일의 링크로 열어 새 비밀번호를 정한다(30분 안에, 한 번만).
 * 로그인이 여러 번 틀려 막혔을 때 오는 안내 메일의 링크도 이 화면으로 온다.
 */
export default function PasswordResetPage() {
  const me = useMe()
  const [params] = useSearchParams()
  const token = params.get('token')

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page narrow">
        <h2 style={{ fontSize: 22 }}>비밀번호 재설정</h2>
        {token ? <NewPasswordForm token={token} /> : <RequestForm />}
      </main>
    </div>
  )
}

function RequestForm() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api('/api/auth/password-reset', { method: 'POST', body: { email: email.trim() } })
      setSent(true)
    } catch (caught) {
      const seconds = retryAfterSeconds(caught)
      setError(seconds !== null
        ? `메일은 1분에 한 번 받을 수 있습니다. ${waitText(seconds)} 뒤에 다시 시도해 주세요.`
        : errorMessage(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
      <p className="muted small">가입한 이메일을 넣으면 비밀번호를 새로 정하는 링크를 보내 드립니다.</p>
      <label className="field">
        <span className="label">가입한 이메일</span>
        <input type="email" value={email} autoComplete="email" required
               onChange={(event) => { setEmail(event.target.value); setSent(false) }} />
      </label>
      {error && <p className="err">{error}</p>}
      {sent && <p className="ok">메일을 보냈습니다. 30분 안에 링크를 눌러 주세요.</p>}
      <button className="btn primary" type="submit" disabled={submitting}>재설정 메일 받기</button>
      <div className="row between small"><Link to="/login">로그인으로</Link><span /></div>
    </form>
  )
}

function NewPasswordForm({ token }: { token: string }) {
  const [newPassword, setNewPassword] = useState('')
  const [done, setDone] = useState(false)
  const [invalidLink, setInvalidLink] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setFieldError(null)
    setError(null)
    try {
      await api('/api/auth/password-reset', { method: 'PUT', body: { token, newPassword } })
      setDone(true)
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'RESET_TOKEN_INVALID') {
        setInvalidLink(true)
      } else if (fieldMessages(caught).newPassword) {
        setFieldError(fieldMessages(caught).newPassword)
      } else {
        setError(errorMessage(caught))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div className="stack" style={{ gap: 14 }}>
        <p className="ok">비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요.</p>
        <Link className="btn primary" to="/login">로그인하기</Link>
      </div>
    )
  }
  if (invalidLink) {
    return (
      <div className="stack" style={{ gap: 14 }}>
        <p className="err">링크가 만료되었거나 이미 사용되었습니다. 메일을 다시 받아 주세요.</p>
        <Link className="btn" to="/password-reset">재설정 메일 다시 받기</Link>
      </div>
    )
  }
  return (
    <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
      <label className="field">
        <span className="label">새 비밀번호</span>
        <input type="password" value={newPassword} autoComplete="new-password" required
               onChange={(event) => setNewPassword(event.target.value)} />
        {fieldError ? <span className="err">{fieldError}</span> : <span className="hint">8자 이상, 영문과 숫자를 함께</span>}
      </label>
      {error && <p className="err">{error}</p>}
      <button className="btn primary" type="submit" disabled={submitting}>변경하기</button>
    </form>
  )
}
