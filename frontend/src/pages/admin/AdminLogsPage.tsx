import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { ModerationLogLine, Notice, NoticeSummary, PageResponse } from '../../api/types'
import { formatDate } from '../../app/format'
import Pagination from '../../components/Pagination'
import LogTable from './LogTable'

const TARGETS = [
  { code: '', label: '전체' },
  { code: 'POST', label: '글' },
  { code: 'COMMENT', label: '댓글' },
  { code: 'BLOG', label: '블로그' },
  { code: 'MEMBER', label: '회원' },
]

/**
 * 관리 이력·공지 (ADMIN-06). 이력은 대상 종류·번호·관리자 번호·기간으로 검색만 된다(고치거나 지울 수 없다).
 * 아래에서 공지를 쓰고, 고치고, 지운다. 검색 조건은 주소에 둔다.
 */
export default function AdminLogsPage() {
  const [params, setParams] = useSearchParams()
  const [form, setForm] = useState({
    targetId: params.get('targetId') ?? '', adminId: params.get('adminId') ?? '',
    from: params.get('from') ?? '', to: params.get('to') ?? '',
  })
  const targetType = params.get('targetType') ?? ''
  const [result, setResult] = useState<PageResponse<ModerationLogLine> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const query = new URLSearchParams()
    for (const key of ['targetType', 'targetId', 'adminId', 'from', 'to', 'page']) {
      const value = params.get(key)
      if (value) query.set(key, value)
    }
    api<PageResponse<ModerationLogLine>>(`/api/admin/moderation-logs?${query}`)
      .then((page) => {
        setResult(page)
        setError(null)
      })
      .catch((caught: unknown) => {
        const fields = fieldMessages(caught)
        setError(fields.from ?? fields.to ?? fields.targetType ?? errorMessage(caught))
      })
  }, [params, reloadKey])

  function apply(next: Record<string, string>) {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    merged.delete('page')
    setParams(merged)
  }

  function search(event: FormEvent) {
    event.preventDefault()
    apply({ targetId: form.targetId.trim(), adminId: form.adminId.trim(), from: form.from, to: form.to })
  }

  function href(number: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(number))
    return `/admin/logs?${next}`
  }

  return (
    <main className="page stack">
      <h1 style={{ fontSize: 20 }}>관리 이력</h1>
      <div className="tabs" role="tablist">
        {TARGETS.map((item) => (
          <button key={item.code} type="button" className={targetType === item.code ? 'on' : undefined}
                  onClick={() => apply({ targetType: item.code })}>{item.label}</button>
        ))}
      </div>
      <form className="row" onSubmit={search} aria-label="관리 이력 검색">
        <input type="number" min={1} value={form.targetId} placeholder="대상 번호" aria-label="대상 번호"
               style={{ maxWidth: 120 }} onChange={(event) => setForm({ ...form, targetId: event.target.value })} />
        <input type="number" min={1} value={form.adminId} placeholder="관리자 번호" aria-label="관리자 번호"
               style={{ maxWidth: 120 }} onChange={(event) => setForm({ ...form, adminId: event.target.value })} />
        <input type="date" value={form.from} aria-label="시작 날짜" style={{ maxWidth: 160 }}
               onChange={(event) => setForm({ ...form, from: event.target.value })} />
        <span className="small muted">~</span>
        <input type="date" value={form.to} aria-label="끝 날짜" style={{ maxWidth: 160 }}
               onChange={(event) => setForm({ ...form, to: event.target.value })} />
        <button className="btn" type="submit">검색</button>
      </form>
      {error && <p className="err" role="alert">{error}</p>}
      {result && <LogTable logs={result.content} empty="조건에 맞는 이력이 없습니다."
                           onChanged={() => setReloadKey((key) => key + 1)} />}
      {result && result.totalPages > 1 && <Pagination page={result.page} totalPages={result.totalPages} href={href} />}
      <NoticeAdmin />
    </main>
  )
}

/** 공지 쓰기·고치기·지우기 (ADMIN-06). 목록은 누구나 보는 /api/notices를 그대로 쓴다 */
function NoticeAdmin() {
  const [notices, setNotices] = useState<NoticeSummary[]>([])
  const [editing, setEditing] = useState<number | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    api<PageResponse<NoticeSummary>>('/api/notices?page=1&size=50')
      .then((page) => setNotices(page.content))
      .catch((caught: unknown) => setErrors({ form: errorMessage(caught) }))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function startEdit(id: number) {
    const notice = await api<Notice>(`/api/notices/${id}`)
    setEditing(id)
    setTitle(notice.title)
    setContent(notice.content)
    setErrors({})
    setMessage(null)
  }

  function reset() {
    setEditing(null)
    setTitle('')
    setContent('')
    setErrors({})
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setErrors({})
    try {
      if (editing === null) {
        await api('/api/admin/notices', { method: 'POST', body: { title, content } })
        setMessage('공지를 등록했습니다. 홈 상단에 보입니다.')
      } else {
        await api(`/api/admin/notices/${editing}`, { method: 'PUT', body: { title, content } })
        setMessage('공지를 고쳤습니다.')
      }
      reset()
      load()
    } catch (caught) {
      const fields = fieldMessages(caught)
      setErrors(Object.keys(fields).length > 0 ? fields : { form: errorMessage(caught) })
    }
  }

  async function remove(notice: NoticeSummary) {
    if (!window.confirm(`공지 "${notice.title}"을(를) 지울까요?`)) {
      return
    }
    try {
      await api(`/api/admin/notices/${notice.id}`, { method: 'DELETE' })
      if (editing === notice.id) reset()
      setMessage('공지를 지웠습니다.')
      load()
    } catch (caught) {
      setErrors({ form: errorMessage(caught) })
    }
  }

  return (
    <section className="section stack" aria-label="공지">
      <h2>공지</h2>
      {notices.length > 0 && (
        <div className="table-wrap">
          <table className="manage-table">
            <thead><tr><th>제목</th><th>작성일</th><th /></tr></thead>
            <tbody>
              {notices.map((notice) => (
                <tr key={notice.id}>
                  <td className="title"><Link to={`/notices/${notice.id}`}>{notice.title}</Link></td>
                  <td className="num small">{formatDate(notice.createdAt)}</td>
                  <td>
                    <span className="row nowrap">
                      <button className="btn ghost small" type="button" onClick={() => startEdit(notice.id)}>수정</button>
                      <button className="btn ghost small" type="button" onClick={() => remove(notice)}>삭제</button>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <form className="box stack" onSubmit={submit} aria-label={editing === null ? '공지 쓰기' : '공지 고치기'}>
        <b className="small">{editing === null ? '공지 쓰기' : '공지 고치기'}</b>
        <input type="text" value={title} maxLength={200} placeholder="제목" aria-label="공지 제목"
               onChange={(event) => setTitle(event.target.value)} />
        {errors.title && <p className="err">{errors.title}</p>}
        <textarea value={content} rows={5} maxLength={10000} placeholder="내용" aria-label="공지 내용"
                  onChange={(event) => setContent(event.target.value)} />
        {errors.content && <p className="err">{errors.content}</p>}
        {errors.form && <p className="err" role="alert">{errors.form}</p>}
        {message && <p className="ok small" role="status">{message}</p>}
        <div className="row">
          <button className="btn primary" type="submit" disabled={!title.trim() || !content.trim()}>
            {editing === null ? '등록' : '수정'}
          </button>
          {editing !== null && <button className="btn" type="button" onClick={reset}>취소</button>}
        </div>
      </form>
    </section>
  )
}
