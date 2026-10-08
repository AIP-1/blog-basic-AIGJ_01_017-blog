import { type FormEvent, useState } from 'react'
import { api } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { Blog } from '../../api/types'
import { PLATFORM_DOMAIN } from '../../app/host'

/**
 * 블로그 설정의 "블로그 정보" (BLOG-02). 주소는 바꿀 수 없어 보여 주기만 한다.
 * 프로필 이미지(스텝 7), 꾸미기·사이드바·이사·삭제(BLOG-05~07, 백로그)는 기능이 생기면 이 화면에 더한다.
 */
export default function BlogSettingsPage({ blog, onSaved }: { blog: Blog; onSaved: (blog: Blog) => void }) {
  const [name, setName] = useState(blog.name)
  const [description, setDescription] = useState(blog.description ?? '')
  const [errors, setErrors] = useState<FieldMessages>({})
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setSaved(false)
    setErrors({})
    try {
      const updated = await api<Blog>('/api/blog', { method: 'PATCH', body: { name: name.trim(), description } })
      onSaved(updated)
      setSaved(true)
    } catch (error) {
      const fields = fieldMessages(error)
      setErrors(Object.keys(fields).length > 0 ? fields : { form: errorMessage(error) })
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="page">
      <form className="section" onSubmit={save} style={{ maxWidth: 520 }}>
        <h2>블로그 정보</h2>
        <label className="field">
          <span className="label">주소</span>
          <input type="text" value={`${blog.address}.${PLATFORM_DOMAIN}`} disabled />
          <span className="hint">주소는 바꿀 수 없습니다.</span>
        </label>
        <label className="field">
          <span className="label">이름</span>
          <input type="text" value={name} maxLength={50} onChange={(event) => setName(event.target.value)} />
          {errors.name && <p className="err">{errors.name}</p>}
        </label>
        <label className="field">
          <span className="label">소개</span>
          <textarea value={description} maxLength={500} onChange={(event) => setDescription(event.target.value)} />
          {errors.description && <p className="err">{errors.description}</p>}
        </label>
        {errors.form && <p className="err">{errors.form}</p>}
        <div className="row">
          <button className="btn primary" type="submit" disabled={saving || !name.trim()}>저장</button>
          {saved && <span className="ok">저장했습니다.</span>}
        </div>
      </form>
    </main>
  )
}
