import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, api } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages, retryAfterSeconds } from '../../api/errors'
import type { Me } from '../../api/types'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

/**
 * 회원가입 (AUTH-01, OWN-01). 이메일 인증 코드를 확인해야 가입 버튼이 켜진다.
 * 실패해도 입력한 값은 그대로 두고, 어느 칸이 왜 틀렸는지 그 칸 아래에 보여 준다.
 */
export default function SignupPage() {
  const me = useMe()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [verified, setVerified] = useState(false)
  const [nicknameOk, setNicknameOk] = useState<boolean | null>(null)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [submitting, setSubmitting] = useState(false)

  function setError(field: string, message?: string) {
    setErrors((previous) => ({ ...previous, [field]: message ?? '' }))
  }

  function changeEmail(value: string) {
    // 이메일을 바꾸면 인증을 다시 받아야 한다
    setEmail(value)
    setCodeSent(false)
    setVerified(false)
  }

  async function sendCode() {
    setError('email')
    try {
      await api('/api/auth/email-verifications', { method: 'POST', body: { email: email.trim() } })
      setCodeSent(true)
    } catch (error) {
      const seconds = retryAfterSeconds(error)
      setError('email', seconds !== null
        ? `잠시 후 다시 시도해 주세요. (${seconds}초)`
        : fieldMessages(error).email ?? emailMessage(error))
    }
  }

  async function verifyCode() {
    setError('code')
    try {
      await api('/api/auth/email-verifications/verify', { method: 'POST', body: { email: email.trim(), code } })
      setVerified(true)
    } catch (error) {
      setVerified(false)
      setError('code', fieldMessages(error).code ?? codeMessage(error))
    }
  }

  async function checkNickname() {
    setNicknameOk(null)
    setError('nickname')
    if (!nickname.trim()) {
      return
    }
    try {
      const result = await api<{ available: boolean }>(
        `/api/auth/nickname-availability?nickname=${encodeURIComponent(nickname.trim())}`)
      setNicknameOk(result.available)
      if (!result.available) {
        setError('nickname', '이미 쓰고 있는 닉네임입니다.')
      }
    } catch (error) {
      setError('nickname', fieldMessages(error).nickname ?? errorMessage(error))
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!PASSWORD_RULE.test(password)) {
      setError('password', '비밀번호는 8자 이상, 영문과 숫자를 함께 써 주세요.')
      return
    }
    setSubmitting(true)
    setErrors({})
    try {
      await api<Me>('/api/auth/signup', {
        method: 'POST',
        body: { email: email.trim(), code, password, nickname: nickname.trim() },
      })
      navigate('/blogs/new')
    } catch (error) {
      setErrors(signupErrors(error))
      if (error instanceof ApiError && (error.code === 'INVALID_VERIFICATION_CODE' || isExpired(error))) {
        setVerified(false)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page narrow">
        <h2 style={{ fontSize: 22 }}>회원가입</h2>
        <form className="stack" style={{ gap: 14 }} onSubmit={submit} noValidate>
          <div className="field">
            <label className="label" htmlFor="email">이메일</label>
            <div className="row nowrap">
              <input id="email" type="email" value={email} autoComplete="email"
                     onChange={(event) => changeEmail(event.target.value)} />
              <button className="btn" type="button" onClick={sendCode} disabled={!email.trim()}>
                {codeSent ? '다시 받기' : '코드 받기'}
              </button>
            </div>
            {codeSent && !errors.email && <p className="ok">인증 코드를 보냈습니다. 10분 안에 입력해 주세요.</p>}
            {errors.email && <p className="err">{errors.email}</p>}
          </div>

          <div className="field">
            <label className="label" htmlFor="code">인증 코드</label>
            <div className="row nowrap">
              <input id="code" type="text" inputMode="numeric" maxLength={6} value={code} disabled={verified}
                     onChange={(event) => setCode(event.target.value)} />
              <button className="btn" type="button" onClick={verifyCode} disabled={!codeSent || verified || !code}>
                확인
              </button>
            </div>
            {verified && <p className="ok">이메일이 확인되었습니다.</p>}
            {errors.code && <p className="err">{errors.code}</p>}
          </div>

          <div className="field">
            <label className="label" htmlFor="password">비밀번호</label>
            <input id="password" type="password" value={password} autoComplete="new-password"
                   onChange={(event) => setPassword(event.target.value)} />
            {errors.password
              ? <p className="err">{errors.password}</p>
              : <span className="hint">8자 이상, 영문과 숫자를 함께</span>}
          </div>

          <div className="field">
            <label className="label" htmlFor="nickname">닉네임</label>
            <input id="nickname" type="text" maxLength={20} value={nickname}
                   onChange={(event) => { setNickname(event.target.value); setNicknameOk(null) }}
                   onBlur={checkNickname} />
            {nicknameOk && !errors.nickname && <p className="ok">쓸 수 있는 닉네임입니다.</p>}
            {errors.nickname && <p className="err">{errors.nickname}</p>}
          </div>

          {errors.form && <p className="err">{errors.form}</p>}
          <button className="btn primary" type="submit" disabled={!verified || submitting}>가입하기</button>
        </form>
      </main>
    </div>
  )
}

function isExpired(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'VERIFICATION_EXPIRED'
}

function emailMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'EMAIL_TAKEN') {
    return '이미 가입한 이메일입니다. 로그인해 주세요.'
  }
  return errorMessage(error)
}

function codeMessage(error: unknown): string {
  if (isExpired(error)) {
    return '인증 코드가 만료되었습니다. 코드를 다시 받아 주세요.'
  }
  return errorMessage(error)
}

/** 가입 실패를 칸별 문장으로 나눈다. */
function signupErrors(error: unknown): FieldMessages {
  if (!(error instanceof ApiError)) {
    return { form: errorMessage(error) }
  }
  switch (error.code) {
    case 'EMAIL_TAKEN':
      return { email: emailMessage(error) }
    case 'NICKNAME_TAKEN':
      return { nickname: error.message }
    case 'INVALID_VERIFICATION_CODE':
    case 'VERIFICATION_EXPIRED':
      return { code: codeMessage(error) }
    case 'VALIDATION_FAILED':
      return fieldMessages(error)
    default:
      return { form: error.message }
  }
}
