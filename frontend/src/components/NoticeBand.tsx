import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { NoticeSummary } from '../api/types'

/** 홈 상단의 최신 공지 1개 (ADMIN-06). 공지가 없거나 못 받으면 띠를 그리지 않는다 */
export default function NoticeBand() {
  const [notice, setNotice] = useState<NoticeSummary | null>(null)

  useEffect(() => {
    api<NoticeSummary | undefined>('/api/notices/latest', { allowAnonymous: true })
      .then((latest) => setNotice(latest ?? null))
      .catch(() => setNotice(null))
  }, [])

  if (!notice) {
    return null
  }
  return (
    <div className="box row" role="note" aria-label="공지" style={{ marginBottom: 16 }}>
      <span className="chip brand">공지</span>
      <Link to={`/notices/${notice.id}`} style={{ flex: 1 }}>{notice.title}</Link>
      <Link className="small" to="/notices">공지 전체</Link>
    </div>
  )
}
