import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api, newIdempotencyKey } from '../../api/client'
import { type FieldMessages, errorMessage, fieldMessages } from '../../api/errors'
import type { CategoryTree, ManagedPost, ManagedPostSummary, PageResponse, PostSaved, Topic } from '../../api/types'
import Editor from '../../components/editor/Editor'
import TagInput from '../../components/editor/TagInput'
import { AUTO_SAVE_MS, autoSaveNeeded } from '../../components/editor/draft'

type Visibility = 'PUBLIC' | 'PRIVATE'
// LOADING: 고칠 글을 불러오는 중. 불러오기 전에 빈 입력값을 자동 저장해 글을 덮지 않도록 저장하지 않는다
type PostState = 'NEW' | 'LOADING' | ManagedPost['status']

/**
 * 글쓰기·수정 (POST-01, POST-02, POST-03, POST-06). /manage/write는 새 글, /manage/posts/{id}/edit는 수정이다.
 * 제목이 비면 발행하지 않고, 실패해도 입력은 그대로 둔다(spec US2 시나리오 2).
 * 태그(TAG-01)와 이미지(POST-05, 에디터 버튼)는 스텝 7, 주제(POST-11)는 스텝 9에서 더했다.
 *
 * 임시저장(POST-08, 스텝 13): 새 글과 임시저장 글은 "임시저장" 버튼과 1분마다 자동 저장으로 저장한다(제목이 비어도 됨).
 * 처음 저장은 POST /api/posts(DRAFT)로 글 번호를 받고, 그 뒤 저장과 발행은 그 번호로 PUT한다.
 * 저장 요청은 한 번에 하나만 보낸다(자동 저장과 발행이 겹치면 발행이 앞 저장을 기다린다).
 * 발행한 글을 고칠 때는 임시저장으로 되돌릴 수 없으므로 자동 저장도 임시저장 버튼도 없다.
 */
export default function PostWritePage() {
  const { postId } = useParams()
  const editing = postId !== undefined
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [contentHtml, setContentHtml] = useState('')
  const [loadedHtml, setLoadedHtml] = useState('')
  const [categoryId, setCategoryId] = useState<string>('')
  // 주제 code. 빈 문자열은 주제 없음(서버에는 null)
  const [topic, setTopic] = useState<string>('')
  const [topics, setTopics] = useState<Topic[]>([])
  const [tagNames, setTagNames] = useState<string[]>([])
  const [visibility, setVisibility] = useState<Visibility>('PUBLIC')
  const [categories, setCategories] = useState<CategoryTree | null>(null)
  const [blind, setBlind] = useState<ManagedPost['blind']>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [errors, setErrors] = useState<FieldMessages>({})
  const [saving, setSaving] = useState(false)
  // 연타 방지 키는 이 화면에서 한 번 만들어 재시도에도 같은 키를 쓴다. 새 키면 서버가 새 요청으로 본다.
  // 발행과 첫 임시저장은 다른 요청이라 키를 따로 둔다
  const idempotencyKey = useRef(newIdempotencyKey())
  const draftKey = useRef(newIdempotencyKey())
  // 저장된 글 번호. 수정이면 주소의 번호, 새 글이면 첫 임시저장 뒤에 생긴다
  const savedId = useRef<string | null>(postId ?? null)
  const [postState, setPostState] = useState<PostState>(editing ? 'LOADING' : 'NEW')
  // 마지막 임시저장 시각 "13:05" (이 컴퓨터의 시계)
  const [savedAt, setSavedAt] = useState<string | null>(null)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [draftCount, setDraftCount] = useState<number | null>(null)
  // 마지막으로 저장한(또는 불러온) 입력값. 지금 입력값과 같으면 자동 저장을 건너뛴다
  const lastSaved = useRef<string | null>(null)
  // 나가 있는 저장 요청. 다음 저장은 이것이 끝난 뒤에 보낸다
  const inFlight = useRef<Promise<unknown> | null>(null)

  useEffect(() => {
    api<CategoryTree>('/api/categories').then(setCategories).catch(() => setCategories(null))
    api<Topic[]>('/api/topics').then(setTopics).catch(() => setTopics([]))
    loadDraftCount()
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
        setTopic(post.topic ?? '')
        setVisibility(post.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC')
        setBlind(post.blind)
        setPostState(post.status)
        lastSaved.current = snapshotOf({
          title: post.title, contentHtml: post.contentHtml, categoryId: post.categoryId, tagNames: post.tagNames,
          topic: post.topic, visibility: post.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC',
        })
      })
      .catch((error: unknown) => active && setLoadError(
        error instanceof ApiError && error.status === 404 ? '글을 찾을 수 없습니다.' : errorMessage(error)))
    return () => {
      active = false
    }
  }, [editing, postId])

  // 자동 저장 타이머는 화면이 열려 있는 동안 하나만 돈다. 매번 최신 입력값을 보도록 함수를 ref로 넘긴다
  const autoSave = useRef<() => void>(() => {})
  autoSave.current = () => {
    const body = formValues()
    const run = autoSaveNeeded({
      status: postState, busy: inFlight.current !== null, blinded: blind !== null,
      title, html: contentHtml, snapshot: snapshotOf(body), lastSaved: lastSaved.current,
    })
    if (run) {
      void saveDraft()
    }
  }
  useEffect(() => {
    const timer = window.setInterval(() => autoSave.current(), AUTO_SAVE_MS)
    return () => window.clearInterval(timer)
  }, [])

  function loadDraftCount() {
    api<PageResponse<ManagedPostSummary>>('/api/manage/posts?status=DRAFT')
      .then((page) => setDraftCount(page.totalElements))
      .catch(() => setDraftCount(null))
  }

  function formValues() {
    return {
      title: title.trim(),
      contentHtml,
      categoryId: categoryId === '' ? null : Number(categoryId),
      tagNames,
      topic: topic === '' ? null : topic,
      visibility,
    }
  }

  /** 저장 요청을 하나씩 보낸다. 앞 요청이 있으면 끝나기를 기다린다(실패해도). */
  async function serially<T>(request: () => Promise<T>): Promise<T> {
    while (inFlight.current) {
      await inFlight.current.catch(() => undefined)
    }
    const running = request()
    inFlight.current = running
    try {
      return await running
    } finally {
      inFlight.current = null
    }
  }

  /** 임시저장 (버튼과 자동 저장). 처음이면 POST로 글 번호를 받고, 그 뒤로는 PUT. 실패해도 입력은 그대로다. */
  async function saveDraft() {
    const values = formValues()
    const body = { ...values, status: 'DRAFT' }
    setDraftError(null)
    try {
      await serially(async () => {
        if (savedId.current === null) {
          const saved = await api<PostSaved>('/api/posts',
            { method: 'POST', body, idempotencyKey: draftKey.current })
          savedId.current = String(saved.id)
          loadDraftCount()
        } else {
          await api<PostSaved>(`/api/posts/${savedId.current}`, { method: 'PUT', body })
        }
      })
      lastSaved.current = snapshotOf(values)
      setPostState('DRAFT')
      setSavedAt(new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }))
    } catch (error) {
      setDraftError(`임시저장하지 못했습니다. ${errorMessage(error)}`)
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!title.trim()) {
      setErrors({ title: '제목을 입력해 주세요.' })
      return
    }
    setSaving(true)
    setErrors({})
    const body = { ...formValues(), status: 'PUBLISHED' }
    try {
      const saved = await serially(() => savedId.current !== null
        ? api<PostSaved>(`/api/posts/${savedId.current}`, { method: 'PUT', body })
        : api<PostSaved>('/api/posts', { method: 'POST', body, idempotencyKey: idempotencyKey.current }))
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

  const drafting = postState === 'NEW' || postState === 'DRAFT'

  if (loadError) {
    return <main className="page"><p className="err">{loadError}</p></main>
  }
  return (
    <main className="page">
      <form className="stack" style={{ gap: 14 }} onSubmit={save} noValidate>
        <div className="row between">
          <h2 style={{ fontSize: 20 }}>{postState === 'PUBLISHED' ? '글 수정' : '글쓰기'}</h2>
          <div className="row">
            {drafting && (
              <span className="small muted" role="status">
                {savedAt ? `임시저장됨 ${savedAt}` : '1분마다 자동 저장'}
              </span>
            )}
            {drafting && (
              <Link className="btn" to="/manage/posts?status=DRAFT">
                임시저장 목록{draftCount !== null && ` ${draftCount}`}
              </Link>
            )}
            {editing && <button className="btn" type="button" onClick={remove}>삭제</button>}
            {drafting && (
              <button className="btn" type="button" disabled={saving || blind !== null} onClick={() => void saveDraft()}>
                임시저장
              </button>
            )}
            <button className="btn primary" type="submit" disabled={saving || blind !== null}>
              {postState === 'PUBLISHED' ? '수정' : '발행'}
            </button>
          </div>
        </div>
        {draftError && <p className="err" role="alert">{draftError}</p>}
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
            {categories?.categories.flatMap((category) => [
              <option key={category.id} value={category.id}>{category.name}</option>,
              ...category.children.map((child) => (
                <option key={child.id} value={child.id}>{`\u00a0\u00a0└ ${child.name}`}</option>
              )),
            ])}
          </select>
          {errors.categoryId && <p className="err">{errors.categoryId}</p>}
        </label>

        <label className="field">
          <span className="label">주제</span>
          <select value={topic} style={{ maxWidth: 200 }} onChange={(event) => setTopic(event.target.value)}>
            <option value="">주제 없음</option>
            {topics.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
          </select>
          <span className="hint">홈의 주제별 글에 나옵니다. 카테고리와는 따로입니다.</span>
          {errors.topic && <p className="err">{errors.topic}</p>}
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

/** 저장할 입력값을 한 줄로. 마지막 저장과 비교해 바뀐 것이 있는지 본다. */
function snapshotOf(values: {
  title: string; contentHtml: string; categoryId: number | null; tagNames: string[]; topic: string | null;
  visibility: Visibility
}): string {
  return JSON.stringify([values.title.trim(), values.contentHtml, values.categoryId, values.tagNames, values.topic,
    values.visibility])
}
