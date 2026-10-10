import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { AdminMember, AdminMemberDetail, PageResponse } from '../../api/types'
import type { ReasonCode } from '../../app/admin'
import { formatDate, formatDateTime } from '../../app/format'
import { blogUrl } from '../../app/host'
import Pagination from '../../components/Pagination'
import SanctionFields from '../../components/SanctionFields'
import LogTable from './LogTable'

const STATUSES = [
  { code: '', label: '전체' },
  { code: 'ACTIVE', label: '활동' },
  { code: 'SUSPENDED', label: '정지' },
  { code: 'WITHDRAWN', label: '탈퇴' },
]

const STATUS_LABEL: Record<AdminMember['status'], string> = { ACTIVE: '활동', SUSPENDED: '정지', WITHDRAWN: '탈퇴' }

/**
 * 회원 관리 (ADMIN-02, ADMIN-05). 이메일·닉네임 검색과 상태 필터, 행을 누르면 상세(?id=)에서 정지·해제와
 * 보유 블로그의 이용 제한·해제. 검색 조건은 주소(?q=&status=&page=)에 두어 새로고침해도 그대로다.
 */
export default function AdminMembersPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const status = params.get('status') ?? ''
  const page = Number(params.get('page') ?? '1')
  const selected = params.get('id')
  const [input, setInput] = useState(q)
  const [result, setResult] = useState<PageResponse<AdminMember> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const query = new URLSearchParams({ page: String(page) })
    if (q) query.set('q', q)
    if (status) query.set('status', status)
    api<PageResponse<AdminMember>>(`/api/admin/members?${query}`)
      .then(setResult)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [q, status, page, reloadKey])

  function update(next: Record<string, string>) {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    setParams(merged)
  }

  function search(event: FormEvent) {
    event.preventDefault()
    update({ q: input.trim(), page: '', id: '' })
  }

  function href(number: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(number))
    next.delete('id')
    return `/admin/members?${next}`
  }

  return (
    <main className="page stack">
      <h1 style={{ fontSize: 20 }}>회원</h1>
      <div className="tabs" role="tablist">
        {STATUSES.map((item) => (
          <button key={item.code} type="button" className={status === item.code ? 'on' : undefined}
                  onClick={() => update({ status: item.code, page: '', id: '' })}>{item.label}</button>
        ))}
      </div>
      <form className="row" role="search" onSubmit={search}>
        <input type="search" value={input} placeholder="이메일·닉네임" aria-label="회원 검색" style={{ maxWidth: 280 }}
               onChange={(event) => setInput(event.target.value)} />
        <button className="btn" type="submit">검색</button>
      </form>
      {error && <p className="err" role="alert">{error}</p>}
      {result && result.content.length === 0 && <p className="small muted">회원이 없습니다.</p>}
      {result && result.content.length > 0 && (
        <div className="table-wrap">
          <table className="manage-table">
            <thead>
              <tr><th>닉네임</th><th>이메일</th><th>상태</th><th>가입일</th></tr>
            </thead>
            <tbody>
              {result.content.map((member) => (
                <tr key={member.id} aria-selected={selected === String(member.id)}
                    style={selected === String(member.id) ? { background: 'var(--brand-soft)' } : undefined}>
                  <td>
                    <button className="btn ghost small" type="button" onClick={() => update({ id: String(member.id) })}>
                      {member.nickname}
                    </button>
                    {member.role === 'ADMIN' && <span className="chip brand">관리자</span>}
                  </td>
                  <td className="small">{member.email ?? <span className="muted">(소셜 가입)</span>}</td>
                  <td className="small nowrap">
                    <span className={member.status === 'SUSPENDED' ? 'chip danger' : 'chip'}>
                      {STATUS_LABEL[member.status]}
                    </span>
                    {member.status === 'SUSPENDED' && (
                      <span className="muted"> {member.suspendedUntil ? `~${formatDate(member.suspendedUntil)}` : '영구'}</span>
                    )}
                  </td>
                  <td className="num small">{formatDate(member.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {result && result.totalPages > 1 && <Pagination page={result.page} totalPages={result.totalPages} href={href} />}
      {selected && (
        <MemberDetail key={`${selected}-${reloadKey}`} id={Number(selected)}
                      onChanged={() => setReloadKey((key) => key + 1)} />
      )}
    </main>
  )
}

/** 회원 상세: 가입일, 보유 블로그(이용 제한·해제), 받은 신고 수, 제재 이력, 정지·해제 */
function MemberDetail({ id, onChanged }: { id: number; onChanged: () => void }) {
  const [detail, setDetail] = useState<AdminMemberDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<AdminMemberDetail>(`/api/admin/members/${id}`).then(setDetail).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  if (error) {
    return <p className="err" role="alert">{error}</p>
  }
  if (!detail) {
    return null
  }
  const member = detail.member
  return (
    <section className="box stack" aria-label="회원 상세">
      <h2 style={{ fontSize: 17 }}>{member.nickname} 상세</h2>
      <p className="small">
        가입 {formatDate(member.createdAt)} · 블로그 {detail.blogs.filter((blog) => !blog.deleted).length} ·
        받은 신고 {detail.reportCount}
      </p>
      {detail.suspension && (
        <p className="err">
          정지 중: {detail.suspension.reasonMessage ?? '사유 없음'}
          {' '}({detail.suspension.suspendedUntil ? `${formatDateTime(detail.suspension.suspendedUntil)}까지` : '영구'})
        </p>
      )}
      {detail.blogs.length > 0 && (
        <div className="stack" style={{ gap: 6 }}>
          <b className="small">보유 블로그</b>
          {detail.blogs.map((blog) => (
            <BlogLine key={blog.id} blog={blog} onChanged={() => { load(); onChanged() }} />
          ))}
        </div>
      )}
      {member.role !== 'ADMIN' && member.status !== 'WITHDRAWN' && (
        <SuspendForm memberId={id} suspended={member.status === 'SUSPENDED'} onChanged={() => { load(); onChanged() }} />
      )}
      <b className="small">제재 이력</b>
      <LogTable logs={detail.moderations} empty="제재 이력이 없습니다." />
    </section>
  )
}

function BlogLine({ blog, onChanged }: { blog: AdminMemberDetail['blogs'][number]; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<ReasonCode>('SPAM')
  const [detail, setDetail] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function restrict(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/admin/blogs/${blog.id}/restriction`, {
        method: 'POST', body: { reason, reasonDetail: detail.trim() || undefined },
      })
      setOpen(false)
      onChanged()
    } catch (caught) {
      setError(fieldMessages(caught).reasonDetail ?? errorMessage(caught))
    }
  }

  async function unrestrict() {
    setError(null)
    try {
      await api(`/api/admin/blogs/${blog.id}/restriction`, { method: 'DELETE' })
      onChanged()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <div className="stack" style={{ gap: 4 }}>
      <div className="row small">
        {blog.deleted
          ? <span className="muted">{blog.name} ({blog.address}) · 삭제됨</span>
          : <a href={blogUrl(blog.address)} target="_blank" rel="noreferrer">{blog.name} ({blog.address})</a>}
        {blog.isPrimary && <span className="chip">대표</span>}
        {blog.restricted && <span className="chip danger">이용 제한</span>}
        {!blog.deleted && (blog.restricted
          ? <button className="btn small" type="button" onClick={unrestrict}>제한 해제</button>
          : <button className="btn small danger" type="button" onClick={() => setOpen(!open)}>이용 제한</button>)}
      </div>
      {open && (
        <form className="stack" onSubmit={restrict} aria-label="블로그 이용 제한">
          <SanctionFields reason={reason} detail={detail} onReason={setReason} onDetail={setDetail} />
          <div className="row">
            <button className="btn danger small" type="submit" disabled={reason === 'ETC' && !detail.trim()}>제한하기</button>
            <button className="btn small" type="button" onClick={() => setOpen(false)}>취소</button>
          </div>
        </form>
      )}
      {error && <p className="err" role="alert">{error}</p>}
    </div>
  )
}

function SuspendForm({ memberId, suspended, onChanged }: { memberId: number; suspended: boolean; onChanged: () => void }) {
  const [period, setPeriod] = useState('7D')
  const [reason, setReason] = useState<ReasonCode>('SPAM')
  const [detail, setDetail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  async function suspend(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      await api(`/api/admin/members/${memberId}/suspension`, {
        method: 'POST', body: { period, reason, reasonDetail: detail.trim() || undefined },
      })
      onChanged()
    } catch (caught) {
      const fields = fieldMessages(caught)
      setError(fields.reasonDetail ?? fields.period ?? fields.member ?? errorMessage(caught))
    } finally {
      setSending(false)
    }
  }

  async function unsuspend() {
    setError(null)
    try {
      await api(`/api/admin/members/${memberId}/suspension`, { method: 'DELETE' })
      onChanged()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <form className="stack" onSubmit={suspend} aria-label="회원 정지" style={{ gap: 6 }}>
      <b className="small">{suspended ? '정지 다시 정하기' : '정지'}</b>
      <div className="row" role="radiogroup" aria-label="정지 기간">
        {[['7D', '7일'], ['30D', '30일'], ['PERMANENT', '영구']].map(([code, label]) => (
          <label key={code} className="row small nowrap">
            <input type="radio" name="period" checked={period === code} onChange={() => setPeriod(code)} />
            {label}
          </label>
        ))}
      </div>
      <SanctionFields reason={reason} detail={detail} onReason={setReason} onDetail={setDetail} error={error} />
      <div className="row">
        <button className="btn danger" type="submit" disabled={sending || (reason === 'ETC' && !detail.trim())}>정지</button>
        {suspended && <button className="btn" type="button" onClick={unsuspend}>정지 해제</button>}
      </div>
    </form>
  )
}
