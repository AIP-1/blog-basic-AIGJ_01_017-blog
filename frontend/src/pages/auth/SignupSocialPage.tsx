import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { Me } from '../../api/types'
import { providerName, socialAuthorizeUrl, withObjectParticle } from '../../app/social'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 소셜 가입 닉네임 확인 (AUTH-01, T099, blog.com/signup/social?token=…&provider=…).
 * 처음 보는 카카오 계정으로 로그인하면 서버가 여기로 보낸다. 토큰은 10분 유효하고, 제공사 이메일은 저장하지 않는다.
 * 가입하면 로그인되어 일반 가입처럼 블로그 개설 안내로 간다.
 */
export default function SignupSocialPage() {
  const me = useMe()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get('token')
  const provider = params.get('provider')
  const name = providerName(provider)
  const [nickname, setNickname] = useState('')
  const [nicknameOk, setNicknameOk] = useState<boolean | null>(null)
  const [nicknameError, setNicknameError] = useState<string | null>(null)
  const [expired, setExpired] = useState(!token)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function checkNickname() {
    setNicknameOk(null)
    setNicknameError(null)
    if (!nickname.trim()) {
      return
    }
    try {
      const result = await api<{ available: boolean }>(
        `/api/auth/nickname-availability?nickname=${encodeURIComponent(nickname.trim())}`)
      setNicknameOk(result.available)
      if (!result.available) {
        setNicknameError('이미 쓰는 닉네임입니다.')
      }
    } catch (caught) {
      setNicknameError(fieldMessages(caught).nickname ?? errorMessage(caught))
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api<Me>('/api/auth/oauth/signup', { method: 'POST', body: { signupToken: token, nickname: nickname.trim() } })
      navigate('/blogs/new')
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'SOCIAL_SIGNUP_EXPIRED') {
        setExpired(true)
      } else if (caught instanceof ApiError && caught.code === 'NICKNAME_TAKEN') {
        setNicknameError('이미 쓰는 닉네임입니다.')
      } else if (fieldMessages(caught).nickname) {
        setNicknameError(fieldMessages(caught).nickname)
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
        <h2 style={{ fontSize: 22 }}>닉네임을 정해 주세요</h2>
        {expired
          ? (
            <div className="stack" style={{ gap: 14 }}>
              <p className="err">가입 시간이 지났습니다. {name} 로그인을 다시 해 주세요.</p>
              {provider === 'kakao'
                ? <a className="btn primary" href={socialAuthorizeUrl(provider, 'login')}>{name}로 다시 시작</a>
                : <Link className="btn primary" to="/login">로그인으로</Link>}
            </div>
          )
          : (
            <form className="stack" style={{ gap: 14 }} onSubmit={submit}>
              <p className="muted small">
                {name} 계정으로 새로 가입합니다. 이미 이메일로 가입했다면 그 계정으로 로그인한 뒤 마이페이지에서 {withObjectParticle(name)} 연동해 주세요.
              </p>
              <div className="field">
                <label className="label" htmlFor="nickname">닉네임</label>
                <input id="nickname" type="text" maxLength={20} value={nickname} required
                       onChange={(event) => { setNickname(event.target.value); setNicknameOk(null) }}
                       onBlur={checkNickname} />
                {nicknameOk && !nicknameError && <p className="ok">쓸 수 있는 닉네임입니다.</p>}
                {nicknameError && <p className="err">{nicknameError}</p>}
              </div>
              {error && <p className="err">{error}</p>}
              <button className="btn primary" type="submit" disabled={submitting || !nickname.trim()}>가입 완료</button>
            </form>
          )}
      </main>
    </div>
  )
}
