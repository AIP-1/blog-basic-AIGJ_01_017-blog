import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { NoticeSummary, PageResponse } from '../../api/types'
import { formatDate } from '../../app/format'
import { useMe } from '../../app/useMe'
import Pagination from '../../components/Pagination'
import PlatformHeader from '../../components/PlatformHeader'

/** 공지 목록 (ADMIN-06, blog.com/notices). 10개씩 최신순 */
export default function NoticesPage() {
  const me = useMe()
  const [params] = useSearchParams()
  const page = Number(params.get('page') ?? '1')
  const [result, setResult] = useState<PageResponse<NoticeSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<PageResponse<NoticeSummary>>(`/api/notices?page=${page}`, { allowAnonymous: true })
      .then(setResult)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [page])

  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page" style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
        <h1 style={{ fontSize: 22 }}>공지</h1>
        {error && <p className="err" role="alert">{error}</p>}
        {result && result.content.length === 0 && <p className="muted">공지가 없습니다.</p>}
        {result && result.content.length > 0 && (
          <div className="table-wrap">
            <table className="manage-table">
              <thead><tr><th>제목</th><th className="num">작성일</th></tr></thead>
              <tbody>
                {result.content.map((notice) => (
                  <tr key={notice.id}>
                    <td className="title"><Link to={`/notices/${notice.id}`}>{notice.title}</Link></td>
                    <td className="num small">{formatDate(notice.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {result && result.totalPages > 1 && (
          <Pagination page={result.page} totalPages={result.totalPages} href={(number) => `/notices?page=${number}`} />
        )}
      </main>
    </div>
  )
}
