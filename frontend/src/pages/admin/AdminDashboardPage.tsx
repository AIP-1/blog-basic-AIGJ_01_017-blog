import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { AdminDashboard } from '../../api/types'
import LogTable from './LogTable'

/** 관리 대시보드 (ADMIN-06): 오늘 가입·새 글, 처리 대기 신고(처리할 대상 수), 최근 조치 5개 */
export default function AdminDashboardPage() {
  const [data, setData] = useState<AdminDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<AdminDashboard>('/api/admin/dashboard').then(setData).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  return (
    <main className="page stack">
      <h1 style={{ fontSize: 20 }}>대시보드</h1>
      {error && <p className="err" role="alert">{error}</p>}
      {data && (
        <>
          <div className="row" style={{ gap: 12 }}>
            <Stat label="오늘 가입" value={data.todaySignups} />
            <Stat label="오늘 새 글" value={data.todayPosts} />
            <Link to="/admin/reports" style={{ textDecoration: 'none', color: 'inherit' }}>
              <Stat label="처리 대기 신고" value={data.pendingReports} />
            </Link>
          </div>
          <section className="section">
            <h2>최근 조치</h2>
            <LogTable logs={data.recentModerations} empty="아직 조치가 없습니다." />
          </section>
        </>
      )}
    </main>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="box" style={{ minWidth: 140 }}>
      <span className="small muted">{label}</span>
      <b className="num" style={{ fontSize: 24 }}>{value.toLocaleString()}</b>
    </div>
  )
}
