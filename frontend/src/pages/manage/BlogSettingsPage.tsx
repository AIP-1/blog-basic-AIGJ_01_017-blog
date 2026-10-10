import { type ChangeEvent, type FormEvent, useState } from 'react'
import { api, uploadFile } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { Blog } from '../../api/types'
import { PLATFORM_DOMAIN } from '../../app/host'
import { DeleteSection, DesignSection, MoveSection, SidebarSection } from './BlogDesignSections'

/**
 * 블로그 설정의 "블로그 정보" (BLOG-02). 주소는 바꿀 수 없어 보여 주기만 한다.
 * 프로필 이미지는 고르는 즉시 올리고(POST /api/images) 미리 보여 준 뒤, 저장을 눌러야 블로그에 반영한다.
 * 아래로 꾸미기(스킨·포인트 색·목록 형태), 사이드바, 블로그 이사, 블로그 삭제가 이어진다(BLOG-05~07, 스텝 19).
 */
export default function BlogSettingsPage({ blog, onSaved }: { blog: Blog; onSaved: (blog: Blog) => void }) {
  const [name, setName] = useState(blog.name)
  const [description, setDescription] = useState(blog.description ?? '')
  /** 새로 올린 사진. 저장하기 전까지는 미리 보기만 한다 */
  const [photo, setPhoto] = useState<{ id: number; thumbnailUrl: string } | null>(null)
  const [uploading, setUploading] = useState(false)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)

  async function choosePhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    setUploading(true)
    setSaved(false)
    setErrors({})
    try {
      setPhoto(await uploadFile<{ id: number; thumbnailUrl: string }>('/api/images', file))
    } catch (error) {
      setErrors({ profileImageId: errorMessage(error) })
    } finally {
      setUploading(false)
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setSaved(false)
    setErrors({})
    try {
      const updated = await api<Blog>('/api/blog', { method: 'PATCH', body: { name: name.trim(), description, profileImageId: photo?.id } })
      onSaved(updated)
      setPhoto(null)
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
        <div className="field">
          <span className="label">프로필 이미지</span>
          <div className="row nowrap">
            {photo || blog.profileImageUrl
              ? <img className="avatar lg" src={photo?.thumbnailUrl ?? blog.profileImageUrl ?? ''} alt="블로그 프로필 이미지" />
              : <span className="avatar lg" />}
            <label className="btn">
              {uploading ? '올리는 중…' : '이미지 바꾸기'}
              <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden disabled={uploading}
                     onChange={choosePhoto} />
            </label>
          </div>
          {photo && <span className="hint">저장을 눌러야 블로그에 반영됩니다.</span>}
          {errors.profileImageId && <p className="err">{errors.profileImageId}</p>}
        </div>
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
          <button className="btn primary" type="submit" disabled={saving || uploading || !name.trim()}>저장</button>
          {saved && <span className="ok">저장했습니다.</span>}
        </div>
      </form>
      <DesignSection blog={blog} onSaved={onSaved} />
      <SidebarSection />
      <MoveSection blog={blog} />
      <DeleteSection blog={blog} />
    </main>
  )
}
