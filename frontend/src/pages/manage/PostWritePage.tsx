import { type FormEvent, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError, api, newIdempotencyKey } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { CategoryTree, ManagedPost, PostSaved } from '../../api/types'
import Editor from '../../components/editor/Editor'
import TagInput from '../../components/editor/TagInput'

type Visibility = 'PUBLIC' | 'PRIVATE'

/**
 * 글쓰기·수정 (POST-01, POST-02, POST-03, POST-06). /manage/write는 새 글, /manage/posts/{id}/edit는 수정이다.
 * 제목이 비면 발행하지 않고, 실패해도 입력은 그대로 둔다(spec US2 시나리오 2).
 * 태그(TAG-01)와 이미지(POST-05, 에디터 버튼)는 스텝 7에서 더했다. 임시저장(POST-08)·주제(POST-11)는 뒤 스텝이다.
 */
export default function PostWritePage() {
  const { postId } = useParams()
  const editing = postId !== undefined
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [contentHtml, setContentHtml] = useState('')
  const [loadedHtml, setLoadedHtml] = useState('')
  const [categoryId, setCategoryId] = useState<string>('')
  const [tagNames, setTagNames] = useState<string[]>([])
  const [visibility, setVisibility] = useState<Visibility>('PUBLIC')
  const [categories, setCategories] = useState<CategoryTree | null>(null)
  const [blind, setBlind] = useState<ManagedPost['blind']>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [saving, setSaving] = useState(false)
  // 연타 방지 키는 이 화면에서 한 번 만들어 재시도에도 같은 키를 쓴다. 새 키면 서버가 새 요청으로 본다
  const idempotencyKey = useRef(newIdempotencyKey())

  useEffect(() => {
    api<CategoryTree>('/api/categories').then(setCategories).catch(() => setCategories(null))
  }, [])

  useEffect(() => {
    if (!editing) {
      return
    }
    let active = true
    api<ManagedPost>(`/api/manage/posts/${postId}`)
      .then((post) => {
        if (!active) {
          return
        }
        setTitle(post.title)
        setLoadedHtml(post.contentHtml)
        setContentHtml(post.contentHtml)
        setCategoryId(post.categoryId === null ? '' : String(post.categoryId))
        setTagNames(post.tagNames)
        setVisibility(post.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC')
        setBlind(post.blind)
      })
      .catch((error: unknown) => active && setLoadError(
        error instanceof ApiError && error.status === 404 ? '글을 찾을 수 없습니다.' : errorMessage(error)))
    return () => {
      active = false
    }
  }, [editing, postId])

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!title.trim()) {
      setErrors({ title: '제목을 입력해 주세요.' })
      return
    }
    setSaving(true)
    setErrors({})
    const body = {
      title: title.trim(),
      contentHtml,
      categoryId: categoryId === '' ? null : Number(categoryId),
      tagNames,
      visibility,
      status: 'PUBLISHED',
    }
    try {
      const saved = editing
        ? await api<PostSaved>(`/api/posts/${postId}`, { method: 'PUT', body })
        : await api<PostSaved>('/api/posts', { method: 'POST', body, idempotencyKey: idempotencyKey.current })
      navigate(`/${saved.id}`)
    } catch (error) {
      const fields = fieldMessages(error)
      setErrors(Object.keys(fields).length > 0 ? fields : { form: errorMessage(error) })
      setSaving(false)
    }
  }

  async function remove() {
    if (!window.confirm('이 글을 삭제할까요? 댓글과 공감도 함께 사라집니다.')) {
      return
    }
    try {
      await api(`/api/posts/${postId}`, { method: 'DELETE' })
      navigate('/')
    } catch (error) {
      setErrors({ form: errorMessage(error) })
    }
  }

  if (loadError) {
    return <main className="page"><p className="err">{loadError}</p></main>
  }
  return (
    <main className="page">
      <form className="stack" style={{ gap: 14 }} onSubmit={save} noValidate>
        <div className="row between">
          <h2 style={{ fontSize: 20 }}>{editing ? '글 수정' : '글쓰기'}</h2>
          <div className="row">
            {editing && <button className="btn" type="button" onClick={remove}>삭제</button>}
            <button className="btn primary" type="submit" disabled={saving || blind !== null}>
              {editing ? '수정' : '발행'}
            </button>
          </div>
        </div>
        {blind && (
          <div className="box danger" role="alert">
            <b>관리자가 숨긴 글이라 고칠 수 없습니다</b>
            <span>사유: {blind.reasonMessage}</span>
          </div>
        )}
        {errors.form && <p className="err">{errors.form}</p>}

        <label className="field">
          <span className="label">제목</span>
          <input type="text" value={title} maxLength={200} placeholder="제목을 입력하세요"
                 onChange={(event) => setTitle(event.target.value)} />
          {errors.title && <p className="err">{errors.title}</p>}
        </label>

        <label className="field">
          <span className="label">카테고리</span>
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="">미분류</option>
            {categories?.categories.map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </select>
          {errors.categoryId && <p className="err">{errors.categoryId}</p>}
        </label>

        <div className="field">
          <span className="label">본문</span>
          <Editor initialHtml={loadedHtml} onChange={setContentHtml} />
        </div>

        <div className="field">
          <span className="label">태그</span>
          <TagInput tags={tagNames} onChange={setTagNames} />
          {errors.tagNames && <p className="err">{errors.tagNames}</p>}
        </div>

        <fieldset className="field" style={{ border: 0, padding: 0, margin: 0 }}>
          <legend className="label">공개 범위</legend>
          <div className="row">
            <label className="row small">
              <input type="radio" name="visibility" checked={visibility === 'PUBLIC'}
                     onChange={() => setVisibility('PUBLIC')} /> 공개
            </label>
            <label className="row small">
              <input type="radio" name="visibility" checked={visibility === 'PRIVATE'}
                     onChange={() => setVisibility('PRIVATE')} /> 비공개
            </label>
          </div>
          {errors.visibility && <p className="err">{errors.visibility}</p>}
        </fieldset>
      </form>
    </main>
  )
}
