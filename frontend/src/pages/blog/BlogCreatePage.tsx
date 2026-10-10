import { type FormEvent, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError, api, redirectToLogin } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { Blog } from '../../api/types'
import { PLATFORM_DOMAIN, blogUrl } from '../../app/host'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import { FROM_WRITE } from '../../app/writeLink'
import PlatformHeader from '../../components/PlatformHeader'

const REASON_MESSAGES: Record<string, string> = {
  INVALID: '영문 소문자·숫자·하이픈만, 하이픈으로 시작하거나 끝날 수 없습니다. (4~32자)',
  RESERVED: '쓸 수 없는 주소입니다.',
  TAKEN: '이미 쓰는 주소입니다.',
}

/**
 * 블로그 개설 (BLOG-01). 주소와 이름을 따로 받고, 주소는 나중에 바꿀 수 없다고 미리 알린다.
 * 만들면 새 블로그의 관리 화면으로 간다.
 * 블로그가 없는 회원이 글쓰기를 눌러 왔으면(?from=write) 먼저 블로그가 필요하다고 안내하고, 만든 뒤 바로 글쓰기로 보낸다 (AUTH-04).
 */
export default function BlogCreatePage() {
  const me = useMe()
  const [params] = useSearchParams()
  const fromWrite = params.get('from') === FROM_WRITE
  const [address, setAddress] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [addressOk, setAddressOk] = useState(false)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (shouldRedirectToLogin(me)) {
      redirectToLogin()
    }
  }, [me])

  async function checkAddress() {
    setAddressOk(false)
    setErrors((previous) => ({ ...previous, address: '' }))
    if (!address) {
      return
    }
    try {
      const result = await api<{ available: boolean; reason: string | null }>(
        `/api/blogs/address-availability?address=${encodeURIComponent(address)}`)
      setAddressOk(result.available)
      if (!result.available && result.reason) {
        setErrors((previous) => ({ ...previous, address: REASON_MESSAGES[result.reason!] }))
      }
    } catch (error) {
      setErrors((previous) => ({ ...previous, address: errorMessage(error) }))
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setErrors({})
    try {
      const blog = await api<Blog>('/api/blogs', {
        method: 'POST',
        body: { address, name: name.trim(), description: description.trim() || null },
      })
      window.location.assign(blogUrl(blog.address, fromWrite ? '/manage/write' : '/manage'))
    } catch (error) {
      setErrors(createErrors(error))
      setSubmitting(false)
    }
  }

  if (me.status !== 'member') {
    return <div className="app"><PlatformHeader me={me} /></div>
  }
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page narrow">
        <h2 style={{ fontSize: 22 }}>블로그 만들기</h2>
        {fromWrite && (
          <div className="box" role="status">
            <b>글을 쓰려면 먼저 블로그가 필요합니다</b>
            <span className="small">블로그를 만들면 바로 글쓰기 화면으로 이어집니다.</span>
          </div>
        )}
        <form className="stack" style={{ gap: 14 }} onSubmit={submit} noValidate>
          <div className="field">
            <label className="label" htmlFor="address">블로그 주소</label>
            <div className="row nowrap">
              <input id="address" type="text" value={address} maxLength={32} autoCapitalize="none"
                     onChange={(event) => { setAddress(event.target.value); setAddressOk(false) }}
                     onBlur={checkAddress} />
              <span className="muted">.{PLATFORM_DOMAIN}</span>
            </div>
            {addressOk && <p className="ok">쓸 수 있는 주소입니다.</p>}
            {errors.address && <p className="err">{errors.address}</p>}
            <span className="hint">
              영문 소문자·숫자·하이픈 4~32자. <b>개설한 뒤에는 주소를 바꿀 수 없습니다.</b>
            </span>
          </div>
          <label className="field">
            <span className="label">블로그 이름</span>
            <input type="text" value={name} maxLength={50} onChange={(event) => setName(event.target.value)} />
            {errors.name
              ? <p className="err">{errors.name}</p>
              : <span className="hint">1~50자, 나중에 바꿀 수 있습니다</span>}
          </label>
          <label className="field">
            <span className="label">소개 (선택)</span>
            <textarea value={description} maxLength={500} onChange={(event) => setDescription(event.target.value)} />
            {errors.description && <p className="err">{errors.description}</p>}
          </label>
          {errors.form && <p className="err">{errors.form}</p>}
          <button className="btn primary" type="submit" disabled={submitting || !address || !name.trim()}>
            만들기
          </button>
        </form>
      </main>
    </div>
  )
}

function createErrors(error: unknown): FieldMessages {
  if (!(error instanceof ApiError)) {
    return { form: errorMessage(error) }
  }
  switch (error.code) {
    case 'BLOG_ADDRESS_INVALID':
      return { address: fieldMessages(error).address ?? error.message }
    case 'BLOG_ADDRESS_TAKEN':
      return { address: REASON_MESSAGES.TAKEN }
    case 'VALIDATION_FAILED':
      return fieldMessages(error)
    default:
      return { form: error.message }
  }
}
