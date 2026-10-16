import { type ChangeEvent, type FormEvent, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import MeTabs from '../../components/MeTabs'
import { ApiError, api, redirectToLogin, uploadFile } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { Me, MyBlog } from '../../api/types'
import { formatDate } from '../../app/format'
import { blogDisplayAddress, blogUrl } from '../../app/host'
import { SOCIAL_PROVIDERS, providerName, socialAuthorizeUrl, socialErrorMessage } from '../../app/social'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/
/** 한 회원의 활성 블로그 한도 (BLOG-01, 서버 Blog.MAX_ACTIVE_PER_MEMBER와 같은 값) */
const MAX_BLOGS = 5

/**
 * 마이페이지 (AUTH-05, BLOG-08). 플랫폼 주소의 /me에서 프로필 사진·닉네임·비밀번호를 바꾸고,
 * 내 블로그 목록과 대표 블로그, 새 블로그 만들기(활성 5개까지, BLOG-01)로 가는 입구를 둔다(스텝 13 보완).
 * 카카오·구글 계정 연동·해제(OWN-03, T100)와 탈퇴(AUTH-06)로 가는 입구도 여기다.
 */
export default function MyPage() {
  const meState = useMe()
  // 저장한 뒤의 내 정보. 저장 전에는 처음 불러온 값을 쓴다
  const [saved, setSaved] = useState<Me | null>(null)
  const me = saved ?? (meState.status === 'member' ? meState.me : null)

  useEffect(() => {
    if (shouldRedirectToLogin(meState)) {
      redirectToLogin()
    }
  }, [meState])

  return (
    <div className="app">
      <PlatformHeader me={me ? { status: 'member', me } : meState} />
      <main className="page">
        {me && (
          <div className="stack" style={{ gap: 24, maxWidth: 520 }}>
            <MeTabs />
            <h2>내 정보</h2>
            <ProfileSection me={me} onSaved={setSaved} />
            <MyBlogsSection onPrimaryChanged={setSaved} />
            {me.hasPassword
              ? <PasswordSection />
              : <p className="small muted">소셜 계정으로 가입해 비밀번호가 없습니다.</p>}
            <SocialSection me={me} onChanged={setSaved} />
            <section className="section">
              <h2>회원 탈퇴</h2>
              <p className="small muted" style={{ margin: 0 }}>블로그·글·댓글이 모두 삭제되고 되돌릴 수 없습니다.</p>
              <Link className="btn danger" to="/me/withdraw" style={{ justifySelf: 'start' }}>회원 탈퇴</Link>
            </section>
          </div>
        )}
      </main>
    </div>
  )
}

/**
 * 내 블로그 (BLOG-08). 만든 순서로 보여 주고, 대표 블로그를 바꾸고, 5개가 안 되면 새 블로그를 만들러 간다.
 * 대표를 바꾸면 내 정보(primaryBlog)를 다시 받아 머리글의 내 블로그·글쓰기 버튼(AUTH-04)도 새 대표로 가게 한다.
 */
function MyBlogsSection({ onPrimaryChanged }: { onPrimaryChanged: (me: Me) => void }) {
  const [blogs, setBlogs] = useState<MyBlog[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [changing, setChanging] = useState(false)

  useEffect(() => {
    api<MyBlog[]>('/api/me/blogs').then(setBlogs).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  async function makePrimary(blog: MyBlog) {
    setChanging(true)
    setError(null)
    try {
      await api('/api/me/primary-blog', { method: 'PUT', body: { blogId: blog.id } })
      setBlogs((previous) => previous?.map((item) => ({ ...item, isPrimary: item.id === blog.id })) ?? null)
      onPrimaryChanged(await api<Me>('/api/me'))
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setChanging(false)
    }
  }

  const full = blogs !== null && blogs.length >= MAX_BLOGS
  return (
    <section className="section">
      <h3>내 블로그</h3>
      {error && <p className="err" role="alert">{error}</p>}
      {blogs !== null && blogs.length === 0 && <p className="muted">아직 블로그가 없습니다.</p>}
      {blogs !== null && blogs.length > 0 && (
        <ul className="my-blogs">
          {blogs.map((blog) => (
            <li key={blog.id}>
              <div className="stack" style={{ gap: 2 }}>
                <div className="row">
                  <a href={blogUrl(blog.address)}><b>{blog.name}</b></a>
                  {blog.isPrimary && <span className="chip brand">대표</span>}
                </div>
                <span className="small muted">
                  {blogDisplayAddress(blog.address)} · 글 {blog.postCount}
                  {blog.movedTo && ` · ${blog.movedTo}(으)로 이사함`}
                </span>
              </div>
              <div className="row">
                <a className="btn" href={blogUrl(blog.address, '/manage')}>관리</a>
                {!blog.isPrimary && (
                  <button className="btn" type="button" disabled={changing} onClick={() => makePrimary(blog)}>
                    대표로
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {blogs !== null && (full
        ? <p className="small muted">블로그는 {MAX_BLOGS}개까지 만들 수 있습니다.</p>
        : <Link className="btn" to="/blogs/new">블로그 만들기 ({blogs.length}/{MAX_BLOGS})</Link>)}
    </section>
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
          <span>사진 바꾸기</span>
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
      {message && <output className="small">{message}</output>}
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
      {done && <output className="small">비밀번호를 바꿨습니다.</output>}
      <button className="btn primary" type="submit" disabled={!currentPassword || !newPassword}>변경</button>
    </form>
  )
}

/**
 * 소셜 계정 연동 (OWN-03, T100). 제공사마다 하나씩 연결하고 해제한다. 연결은 서버의 authorize?mode=link로 화면을 옮기고,
 * 끝나면 서버가 /me?linked=google 또는 /me?error=…로 돌려보낸다. 로그인 수단이 하나도 남지 않으면 해제는 409다.
 */
function SocialSection({ me, onChanged }: { me: Me; onChanged: (me: Me) => void }) {
  const [params, setParams] = useSearchParams()
  const linked = params.get('linked')
  const [error, setError] = useState<string | null>(socialErrorMessage(params.get('error')))
  const [busy, setBusy] = useState(false)

  async function unlink(path: string) {
    setBusy(true)
    setError(null)
    setParams({}, { replace: true })
    try {
      await api(`/api/me/social/${path}`, { method: 'DELETE' })
      onChanged(await api<Me>('/api/me'))
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'LAST_LOGIN_METHOD'
        ? '로그인 수단이 하나도 남지 않아 해제할 수 없습니다.'
        : errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="section" aria-label="소셜 계정 연동">
      <h3>소셜 계정 연동</h3>
      <p className="small muted" style={{ margin: 0 }}>다른 소셜 계정도 연결해 두면 로그인 수단을 잃었을 때 대비할 수 있어요.</p>
      {SOCIAL_PROVIDERS.map((provider) => {
        const account = me.socialAccounts.find((social) => social.provider === provider.key)
        return (
          <div key={provider.path} className="row between">
            <span>
              <b>{provider.name}</b>{' '}
              <span className="small muted">{account ? `연결됨 ${formatDate(account.linkedAt)}` : '연결 안 됨'}</span>
            </span>
            {account
              ? <button className="btn" type="button" disabled={busy} onClick={() => unlink(provider.path)}>해제</button>
              : <a className="btn" href={socialAuthorizeUrl(provider.path, 'link')}>연결</a>}
          </div>
        )
      })}
      {linked && !error && <p className="ok">{providerName(linked)} 계정을 연결했습니다.</p>}
      {error && <p className="err" role="alert">{error}</p>}
    </section>
  )
}
