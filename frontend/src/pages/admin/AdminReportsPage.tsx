import { type FormEvent, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { PageResponse, ReportGroup, TargetReports } from '../../api/types'
import { type ReasonCode, reasonLabel, resolveChoices, topReason } from '../../app/admin'
import { formatDate, formatDateTime } from '../../app/format'
import Pagination from '../../components/Pagination'
import SanctionFields from '../../components/SanctionFields'
import TargetLink from './TargetLink'

/**
 * 신고 처리 (ADMIN-04). 처리 대기 신고를 대상별로 묶어 신고 수 많은 순. 행을 누르면 그 대상의 신고 목록과 처리 칸.
 * 결과 하나를 고르면 그 대상의 대기 신고가 모두 처리 완료가 되고 관리 이력에 남는다.
 */
export default function AdminReportsPage() {
  const [params] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<PageResponse<ReportGroup> | null>(null)
  const [selected, setSelected] = useState<ReportGroup | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    api<PageResponse<ReportGroup>>(`/api/admin/reports?status=PENDING&page=${page}`)
      .then(setResult)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [page, reloadKey])

  function resolved(text: string) {
    setSelected(null)
    setMessage(text)
    setReloadKey((key) => key + 1)
  }

  return (
    <main className="page stack">
      <h1 style={{ fontSize: 20 }}>신고 처리</h1>
      <p className="small muted">처리 대기 신고를 대상별로 묶어 신고 수가 많은 순으로 보여 줍니다.</p>
      {message && <p className="ok small" role="status">{message}</p>}
      {error && <p className="err" role="alert">{error}</p>}
      {result && result.content.length === 0 && <p className="small muted">처리할 신고가 없습니다.</p>}
      {result && result.content.length > 0 && (
        <div className="table-wrap">
          <table className="manage-table">
            <thead>
              <tr><th>대상</th><th className="num">신고</th><th>사유</th><th>처음 신고</th><th /></tr>
            </thead>
            <tbody>
              {result.content.map((group) => (
                <tr key={`${group.targetType}-${group.targetId}`}>
                  <td><TargetLink target={group.target} /></td>
                  <td className="num">{group.reportCount}</td>
                  <td className="small">
                    {Object.entries(group.reasons).map(([code, count]) => `${reasonLabel(code)} ${count}`).join(', ')}
                  </td>
                  <td className="num small">{formatDate(group.firstReportedAt)}</td>
                  <td>
                    <button className="btn small" type="button"
                            onClick={() => { setSelected(group); setMessage(null) }}>처리</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {result && result.totalPages > 1 && (
        <Pagination page={result.page} totalPages={result.totalPages} href={(number) => `/admin/reports?page=${number}`} />
      )}
      {selected && (
        <ResolvePanel key={`${selected.targetType}-${selected.targetId}`} group={selected} onResolved={resolved}
                      onClose={() => setSelected(null)} />
      )}
    </main>
  )
}

function ResolvePanel({ group, onResolved, onClose }: {
  group: ReportGroup
  onResolved: (message: string) => void
  onClose: () => void
}) {
  const choices = resolveChoices(group.targetType)
  const [reports, setReports] = useState<TargetReports | null>(null)
  const [choice, setChoice] = useState(choices[0].code)
  const [reason, setReason] = useState<ReasonCode>(topReason(group.reasons))
  const [detail, setDetail] = useState('')
  const [period, setPeriod] = useState('7D')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    api<TargetReports>(`/api/admin/reports/${group.targetType}/${group.targetId}`)
      .then(setReports)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [group])

  async function resolve(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    const body = choice === 'REJECT'
      ? { result: choice }
      : { result: choice, reason, reasonDetail: detail.trim() || undefined, period: choice === 'SUSPEND' ? period : undefined }
    try {
      await api(`/api/admin/reports/${group.targetType}/${group.targetId}/resolve`, { method: 'POST', body })
      onResolved(`${choices.find((item) => item.code === choice)?.label}(으)로 처리했습니다. 이 대상의 신고 ${group.reportCount}건이 모두 처리 완료되었습니다.`)
    } catch (caught) {
      const fields = fieldMessages(caught)
      setError(fields.result ?? fields.reasonDetail ?? fields.period ?? errorMessage(caught))
    } finally {
      setSending(false)
    }
  }

  return (
    <section className="box stack" aria-label="신고 처리">
      <div className="row between">
        <h2 style={{ fontSize: 17 }}><TargetLink target={group.target} /></h2>
        <button className="btn ghost small" type="button" onClick={onClose}>닫기</button>
      </div>
      {reports && (
        <ul className="stack small" style={{ gap: 4, margin: 0, paddingLeft: 18 }}>
          {reports.reports.filter((report) => report.status === 'PENDING').map((report) => (
            <li key={report.id}>
              {report.reporter?.nickname ?? '(알 수 없음)'} · {report.reasonMessage}
              {report.description && ` "${report.description}"`} · <span className="num">{formatDateTime(report.createdAt)}</span>
            </li>
          ))}
        </ul>
      )}
      <form className="stack" onSubmit={resolve} style={{ gap: 8 }}>
        <div className="row" role="radiogroup" aria-label="처리 결과">
          {choices.map((item) => (
            <label key={item.code} className="row small nowrap">
              <input type="radio" name="resolve-result" checked={choice === item.code} onChange={() => setChoice(item.code)} />
              {item.label}
            </label>
          ))}
        </div>
        {choice === 'SUSPEND' && (
          <div className="row" role="radiogroup" aria-label="정지 기간">
            {[['7D', '7일'], ['30D', '30일'], ['PERMANENT', '영구']].map(([code, label]) => (
              <label key={code} className="row small nowrap">
                <input type="radio" name="resolve-period" checked={period === code} onChange={() => setPeriod(code)} />
                {label}
              </label>
            ))}
          </div>
        )}
        {choice !== 'REJECT' && (
          <SanctionFields reason={reason} detail={detail} onReason={setReason} onDetail={setDetail} />
        )}
        {error && <p className="err" role="alert">{error}</p>}
        <div className="row">
          <button className={choice === 'REJECT' ? 'btn primary' : 'btn danger'} type="submit"
                  disabled={sending || (choice !== 'REJECT' && reason === 'ETC' && !detail.trim())}>처리</button>
        </div>
      </form>
    </section>
  )
}
