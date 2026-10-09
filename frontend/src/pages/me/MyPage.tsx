import { type ChangeEvent, type FormEvent, useEffect, useState } from 'react'
import { ApiError, api, redirectToLogin, uploadFile } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { Me } from '../../api/types'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

/**
 * 마이페이지 (AUTH-05). 플랫폼 주소의 /me에서 프로필 사진·닉네임·비밀번호를 바꾼다.
 * 소셜 연동(OWN-03), 내 블로그 목록·대표 블로그(BLOG-08), 탈퇴(AUTH-06)는 뒤 스텝이다.
 */
export default function MyPage() {
  const meState = useMe()
  // 저장한 뒤의 내 정보. 저장 전에는 처음 불러온 값을 쓴다
  const [saved, setMe] = useState<Me | null>(null)
  const me = saved ?? (meState.status === 'member' ? meState.me : null)

  useEffect(() => {
    if (meState.status === 'anonymous') {
      redirectToLogin()
    }
  }, [meState.status])

  return (
    <div className="app">
      <PlatformHeader me={me ? { status: 'member', me } : meState} />
      <main className="page">
        {me && (
          <div className="stack" style={{ gap: 24, maxWidth: 520 }}>
            <h2>내 정보</h2>
            <ProfileSection me={me} onSaved={setMe} />
            {me.hasPassword
              ? <PasswordSection />
              : <p className="small muted">소셜 계정으로 가입해 비밀번호가 없습니다.</p>}
          </div>
        )}
      </main>
    </div>
  )
}

/** 프로필 사진과 닉네임. 사진은 고르면 바로 올리고 저장하고, 닉네임은 [저장]을 눌러야 바뀐다. */
function ProfileSection({ me, onSaved }: { me: Me; onSaved: (me: Me) => void }) {
  const [nickname, setNickname] = useState(me.nickname)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [message, setMessage] = useState<string | null>(null)

  async function changePhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    setErrors({})
    setMessage(null)
    try {
      const image = await uploadFile<{ id: number }>('/api/images', file)
      onSaved(await api<Me>('/api/me', { method: 'PATCH', body: { profileImageId: image.id } }))
      setMessage('프로필 사진을 바꿨습니다.')
    } catch (error) {
      setErrors({ photo: errorMessage(error) })
    }
  }

  async function saveNickname(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    setMessage(null)
    try {
      const saved = await api<Me>('/api/me', { method: 'PATCH', body: { nickname: nickname.trim() } })
      onSaved(saved)
      setNickname(saved.nickname)
      setMessage('닉네임을 바꿨습니다.')
    } catch (error) {
      setErrors(error instanceof ApiError && error.code === 'NICKNAME_TAKEN'
        ? { nickname: '이미 쓰고 있는 닉네임입니다.' }
        : { nickname: fieldMessages(error).nickname ?? errorMessage(error) })
    }
  }

  return (
    <section className="section">
      <div className="row nowrap">
        {me.profileImageUrl
          ? <img className="avatar lg" src={me.profileImageUrl} alt="프로필 사진" />
          : <span className="avatar lg" />}
        <label className="btn">
          사진 바꾸기
          <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={changePhoto} />
        </label>
      </div>
      {errors.photo && <p className="err">{errors.photo}</p>}
      <form className="field" onSubmit={saveNickname} noValidate>
        <span className="label">닉네임</span>
        <div className="row nowrap">
          <input type="text" value={nickname} maxLength={20} onChange={(event) => setNickname(event.target.value)} />
          <button className="btn primary" type="submit" disabled={!nickname.trim() || nickname.trim() === me.nickname}>
            저장
          </button>
        </div>
        {errors.nickname && <p className="err">{errors.nickname}</p>}
      </form>
      {message && <p className="small" role="status">{message}</p>}
    </section>
  )
}

function PasswordSection() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [errors, setErrors] = useState<FieldMessages>({})
  const [done, setDone] = useState(false)

  async function change(event: FormEvent) {
    event.preventDefault()
    setDone(false)
    if (!PASSWORD_RULE.test(newPassword)) {
      setErrors({ newPassword: '비밀번호는 8자 이상, 영문과 숫자를 함께 써 주세요.' })
      return
    }
    setErrors({})
    try {
      await api('/api/me/password', { method: 'PUT', body: { currentPassword, newPassword } })
      setCurrentPassword('')
      setNewPassword('')
      setDone(true)
    } catch (error) {
      const fields = fieldMessages(error)
      setErrors(Object.keys(fields).length > 0 ? fields : { form: errorMessage(error) })
    }
  }

  return (
    <form className="section" onSubmit={change} noValidate>
      <h3>비밀번호 변경</h3>
      <label className="field">
        <span className="label">지금 비밀번호</span>
        <input type="password" value={currentPassword} autoComplete="current-password"
               onChange={(event) => setCurrentPassword(event.target.value)} />
        {errors.currentPassword && <p className="err">{errors.currentPassword}</p>}
      </label>
      <label className="field">
        <span className="label">새 비밀번호</span>
        <input type="password" value={newPassword} autoComplete="new-password"
               onChange={(event) => setNewPassword(event.target.value)} />
        <span className="hint">8자 이상, 영문과 숫자를 함께</span>
        {errors.newPassword && <p className="err">{errors.newPassword}</p>}
      </label>
      {errors.form && <p className="err">{errors.form}</p>}
      {done && <p className="small" role="status">비밀번호를 바꿨습니다.</p>}
      <button className="btn primary" type="submit" disabled={!currentPassword || !newPassword}>변경</button>
    </form>
  )
}
