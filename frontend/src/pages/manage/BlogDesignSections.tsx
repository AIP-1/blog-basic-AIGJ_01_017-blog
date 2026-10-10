import { type DragEvent, type FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { AccentColor, Blog, MyBlog, SidebarModuleItem, Skin } from '../../api/types'
import { ACCENTS, SKINS } from '../../app/blogTheme'
import { platformUrl } from '../../app/host'
import { MODULE_LABELS, moveModule, toggleModule } from './sidebarModules'

/**
 * 꾸미기 (BLOG-05): 스킨, 포인트 색 6색, 메인 글 목록 형태. "적용"을 누르면 PATCH /api/blog로 저장하고 바로 화면에 입힌다.
 */
export function DesignSection({ blog, onSaved }: { blog: Blog; onSaved: (blog: Blog) => void }) {
  const [skin, setSkin] = useState<Skin>(blog.skin)
  const [accentColor, setAccentColor] = useState<AccentColor>(blog.accentColor)
  const [listLayout, setListLayout] = useState(blog.listLayout)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function apply(event: FormEvent) {
    event.preventDefault()
    setMessage(null)
    setError(null)
    try {
      onSaved(await api<Blog>('/api/blog', { method: 'PATCH', body: { skin, accentColor, listLayout } }))
      setMessage('적용했습니다. 블로그의 모든 화면이 이 모양으로 보입니다.')
    } catch (caught) {
      const fields = fieldMessages(caught)
      setError(fields.skin ?? fields.accentColor ?? fields.listLayout ?? errorMessage(caught))
    }
  }

  return (
    <form className="section" onSubmit={apply} style={{ maxWidth: 560 }} aria-label="꾸미기">
      <h2>꾸미기</h2>
      <div className="field">
        <span className="label">스킨</span>
        <div className="row" role="radiogroup" aria-label="스킨">
          {SKINS.map((item) => (
            <label key={item.code} className={skin === item.code ? 'btn on' : 'btn'} title={item.description}>
              <input type="radio" name="skin" hidden checked={skin === item.code} onChange={() => setSkin(item.code)} />
              {item.label}
            </label>
          ))}
        </div>
      </div>
      <div className="field">
        <span className="label">포인트 색</span>
        <div className="row" role="radiogroup" aria-label="포인트 색">
          {(Object.keys(ACCENTS) as AccentColor[]).map((code) => (
            <label key={code} className="row small nowrap" title={ACCENTS[code].label}>
              <input type="radio" name="accent" checked={accentColor === code} onChange={() => setAccentColor(code)} />
              <span aria-hidden="true" style={{
                display: 'inline-block', width: 18, height: 18, borderRadius: 999, background: ACCENTS[code].brand,
                outline: accentColor === code ? '2px solid var(--ink)' : 'none', outlineOffset: 2,
              }} />
              {ACCENTS[code].label}
            </label>
          ))}
        </div>
      </div>
      <div className="field">
        <span className="label">메인 글 목록</span>
        <div className="row" role="radiogroup" aria-label="메인 글 목록 형태">
          {([['LIST', '리스트'], ['THUMBNAIL', '썸네일']] as const).map(([code, label]) => (
            <label key={code} className={listLayout === code ? 'btn on' : 'btn'}>
              <input type="radio" name="layout" hidden checked={listLayout === code} onChange={() => setListLayout(code)} />
              {label}
            </label>
          ))}
        </div>
      </div>
      {error && <p className="err" role="alert">{error}</p>}
      <div className="row">
        <button className="btn primary" type="submit">적용</button>
        {message && <span className="ok small" role="status">{message}</span>}
      </div>
    </form>
  )
}

/**
 * 사이드바 (BLOG-05): 끌어서 순서를 바꾸고 모듈마다 보이기·숨기기. "위로·아래로"로도 옮긴다(마우스 없이).
 * 저장을 눌러야 반영된다(PUT 8개 전체). 판단은 sidebarModules.ts의 순수 함수가 한다.
 */
export function SidebarSection() {
  const [items, setItems] = useState<SidebarModuleItem[] | null>(null)
  const [dragging, setDragging] = useState<number | null>(null)
  const [dirty, setDirty] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<SidebarModuleItem[]>('/api/blog/sidebar/modules').then(setItems).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  function change(next: SidebarModuleItem[]) {
    if (items && next !== items) {
      setItems(next)
      setDirty(true)
      setMessage(null)
    }
  }

  function drop(event: DragEvent, index: number) {
    event.preventDefault()
    if (items && dragging !== null) {
      change(moveModule(items, dragging, index))
    }
    setDragging(null)
  }

  async function save() {
    if (!items) {
      return
    }
    setError(null)
    try {
      await api('/api/blog/sidebar/modules', { method: 'PUT', body: items })
      setDirty(false)
      setMessage('사이드바를 저장했습니다.')
    } catch (caught) {
      setError(fieldMessages(caught).modules ?? errorMessage(caught))
    }
  }

  return (
    <section className="section" style={{ maxWidth: 560 }} aria-label="사이드바">
      <h2>사이드바</h2>
      <p className="small muted">끌어서 순서를 바꾸고, 보일 모듈을 고르세요. 모듈마다 개수는 정해져 있습니다(최근 글·댓글·인기 글 5개).</p>
      {items && (
        <ol className="stack" style={{ gap: 6, listStyle: 'none', padding: 0, margin: 0 }}>
          {items.map((item, index) => (
            <li key={item.moduleType} className="box row between" draggable
                onDragStart={(event) => { event.dataTransfer.effectAllowed = 'move'; setDragging(index) }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => drop(event, index)}
                onDragEnd={() => setDragging(null)}
                style={{ padding: '8px 12px', opacity: dragging === index ? 0.5 : 1, cursor: 'grab' }}>
              <span className="row nowrap">
                <span aria-hidden="true" className="muted">⋮⋮</span>
                <b className="small">{MODULE_LABELS[item.moduleType].name}</b>
                {MODULE_LABELS[item.moduleType].note && (
                  <span className="small muted">{MODULE_LABELS[item.moduleType].note}</span>
                )}
              </span>
              <span className="row nowrap">
                <button className="btn ghost small" type="button" aria-label={`${MODULE_LABELS[item.moduleType].name} 위로`}
                        disabled={index === 0} onClick={() => change(moveModule(items, index, index - 1))}>↑</button>
                <button className="btn ghost small" type="button" aria-label={`${MODULE_LABELS[item.moduleType].name} 아래로`}
                        disabled={index === items.length - 1}
                        onClick={() => change(moveModule(items, index, index + 1))}>↓</button>
                <label className="row small nowrap">
                  <input type="checkbox" checked={item.isVisible} disabled={item.moduleType === 'PROFILE'}
                         onChange={() => change(toggleModule(items, item.moduleType))} />
                  보이기
                </label>
              </span>
            </li>
          ))}
        </ol>
      )}
      {error && <p className="err" role="alert">{error}</p>}
      <div className="row">
        <button className="btn primary" type="button" disabled={!dirty} onClick={save}>사이드바 저장</button>
        {message && <span className="ok small" role="status">{message}</span>}
      </div>
    </section>
  )
}

/**
 * 블로그 이사 (BLOG-06): 이 블로그의 방문자를 내 다른 블로그로 보낸다(301). 글은 글 관리에서 골라 옮긴다.
 * 이사한 뒤에도 주인은 이 관리 화면을 계속 쓴다.
 */
export function MoveSection({ blog }: { blog: Blog }) {
  const [blogs, setBlogs] = useState<MyBlog[] | null>(null)
  const [target, setTarget] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  function load() {
    api<MyBlog[]>('/api/me/blogs').then(setBlogs).catch((caught: unknown) => setError(errorMessage(caught)))
  }

  useEffect(load, [])

  const self = blogs?.find((item) => item.id === blog.id)
  const candidates = blogs?.filter((item) => item.id !== blog.id) ?? []

  async function moveTo(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    try {
      await api('/api/blog/moved-to', { method: 'PUT', body: { targetBlogId: Number(target) } })
      setMessage('이사 대상을 정했습니다. 이 블로그 주소로 오는 방문자는 새 블로그로 갑니다.')
      load()
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'INVALID_MOVE_TARGET'
        ? '그 블로그로는 이사할 수 없습니다(이미 이 블로그로 이사해 오는 블로그면 순환이 됩니다).' : errorMessage(caught))
    }
  }

  async function cancel() {
    setError(null)
    try {
      await api('/api/blog/moved-to', { method: 'DELETE' })
      setMessage('이사 지정을 취소했습니다.')
      load()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <section className="section" style={{ maxWidth: 560 }} aria-label="블로그 이사">
      <h2>블로그 이사</h2>
      <p className="small muted">글을 옮기면 카테고리는 미분류가 되고 태그는 이름으로 다시 연결됩니다. 옛 글 주소는 새 블로그로 이어집니다.
        글은 <Link to="/manage/posts">글 관리</Link>에서 골라 옮깁니다.</p>
      {self?.movedTo && (
        <p className="box small" role="status">
          지금 이 블로그는 <b>{self.movedTo}</b>(으)로 이사 중입니다. 방문자는 그 블로그로 갑니다.
        </p>
      )}
      {blogs && candidates.length === 0 && <p className="small muted">이사할 수 있는 다른 블로그가 없습니다.</p>}
      {candidates.length > 0 && (
        <form className="row" onSubmit={moveTo}>
          <select value={target} aria-label="이사 대상 블로그" style={{ maxWidth: 260 }}
                  onChange={(event) => setTarget(event.target.value)}>
            <option value="">대상 블로그</option>
            {candidates.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.address})</option>)}
          </select>
          <button className="btn" type="submit" disabled={!target}>이 블로그를 대상 블로그로 이사 지정</button>
          {self?.movedTo && <button className="btn" type="button" onClick={cancel}>지정 취소</button>}
        </form>
      )}
      {error && <p className="err" role="alert">{error}</p>}
      {message && <p className="ok small" role="status">{message}</p>}
    </section>
  )
}

/**
 * 블로그 삭제 (BLOG-07): "옮기지 않은 글 N개가 함께 삭제됩니다" 경고, 주소를 다시 입력해 확인. 대표 블로그는 지울 수 없다.
 * 지우면 마이페이지로 간다(이 블로그 주소는 더는 없다).
 */
export function DeleteSection({ blog }: { blog: Blog }) {
  const [preview, setPreview] = useState<{ remainingPostCount: number; isPrimary: boolean } | null>(null)
  const [address, setAddress] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<{ remainingPostCount: number; isPrimary: boolean }>('/api/blog/deletion-preview')
      .then(setPreview)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  async function remove(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api('/api/blog', { method: 'DELETE', body: { confirmAddress: address.trim() } })
      window.location.href = platformUrl('/me')
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'PRIMARY_BLOG'
        ? '대표 블로그는 삭제할 수 없습니다. 마이페이지에서 대표를 바꿔 주세요.'
        : fieldMessages(caught).confirmAddress ?? errorMessage(caught))
    }
  }

  return (
    <form className="section box danger" onSubmit={remove} style={{ maxWidth: 560 }} aria-label="블로그 삭제">
      <h2>블로그 삭제</h2>
      {preview && (
        <p className="small" style={{ margin: 0 }}>
          옮기지 않은 글 <b className="num">{preview.remainingPostCount}</b>개가 함께 삭제됩니다.
          주소 <b>{blog.address}</b>은(는) 다시 쓸 수 없습니다. 대표 블로그는 대표를 바꾼 뒤에 삭제할 수 있습니다.
        </p>
      )}
      {preview?.isPrimary
        ? <p className="small">이 블로그는 대표 블로그입니다. <a href={platformUrl('/me')}>마이페이지</a>에서 대표를 바꿔 주세요.</p>
        : (
          <>
            <input type="text" value={address} placeholder={blog.address} aria-label="확인을 위해 블로그 주소 입력"
                   onChange={(event) => setAddress(event.target.value)} />
            <div className="row">
              <button className="btn danger" type="submit" disabled={address.trim() !== blog.address}>삭제</button>
            </div>
          </>
        )}
      {error && <p className="err" role="alert">{error}</p>}
    </form>
  )
}
