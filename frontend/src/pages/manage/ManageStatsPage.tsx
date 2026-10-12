import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { PopularPostStat, Referrers, StatsPoint } from '../../api/types'
import { type StatsUnit, barHeights, bucketLabel, referrerLabel } from '../../app/stats'

/**
 * 방문 통계 (MNG-03, 목업 manage-stats). 일(최근 14일)·주(12주)·월(12달) 방문자 막대 그래프, 인기 글(누적·최근 7일), 유입 경로(최근 30일).
 * 그래프는 그림 라이브러리 없이 막대 높이(%)로 그린다(계산은 app/stats.ts).
 */
export default function ManageStatsPage() {
  const [unit, setUnit] = useState<StatsUnit>('day')
  const [points, setPoints] = useState<StatsPoint[] | null>(null)
  const [range, setRange] = useState<'all' | '7d'>('all')
  const [popular, setPopular] = useState<PopularPostStat[] | null>(null)
  const [referrers, setReferrers] = useState<Referrers | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<StatsPoint[]>(`/api/manage/stats/visitors?unit=${unit}`).then(setPoints).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [unit])

  useEffect(() => {
    api<PopularPostStat[]>(`/api/manage/stats/popular-posts?range=${range}`).then(setPopular)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [range])

  useEffect(() => {
    api<Referrers>('/api/manage/stats/referrers').then(setReferrers).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  const heights = points ? barHeights(points) : []
  return (
    <main className="page">
      <h1 style={{ fontSize: 20 }}>방문 통계</h1>
      <p className="small muted" style={{ margin: 0 }}>어제까지의 통계는 매일 새벽에 모은 값이고, 오늘은 바로 셉니다. 주인 본인 방문은 세지 않습니다.</p>
      {error && <p className="err" role="alert">{error}</p>}
      <section className="section" aria-label="방문자 그래프">
        <div className="row between">
          <h2>방문자</h2>
          <div className="tabs" role="tablist">
            {([['day', '일'], ['week', '주'], ['month', '월']] as const).map(([code, label]) => (
              <button key={code} type="button" role="tab" aria-selected={unit === code} className={unit === code ? 'on' : undefined}
                      onClick={() => setUnit(code)}>{label}</button>
            ))}
          </div>
        </div>
        {points && (
          <div role="img" aria-label="방문자 막대 그래프"
               style={{ display: 'grid', gridTemplateColumns: `repeat(${points.length}, 1fr)`, gap: 4, alignItems: 'end', height: 180 }}>
            {points.map((point, i) => (
              <div key={point.date} className="stack" style={{ gap: 2, alignItems: 'end', height: '100%', alignContent: 'end' }}
                   title={`${point.date} 방문자 ${point.visitorCount} · 조회 ${point.viewCount}`}>
                <span className="small num muted" style={{ textAlign: 'center' }}>{point.visitorCount || ''}</span>
                <div style={{ height: `${heights[i] * 1.2}px`, background: 'var(--brand)', borderRadius: 3 }} />
                <span className="small num muted" style={{ textAlign: 'center', fontSize: 11 }}>{bucketLabel(point.date, unit)}</span>
              </div>
            ))}
          </div>
        )}
      </section>
      <section className="section" aria-label="인기 글">
        <div className="row between">
          <h2>인기 글</h2>
          <div className="tabs" role="tablist">
            <button type="button" role="tab" aria-selected={range === 'all'} className={range === 'all' ? 'on' : undefined}
                    onClick={() => setRange('all')}>누적</button>
            <button type="button" role="tab" aria-selected={range === '7d'} className={range === '7d' ? 'on' : undefined}
                    onClick={() => setRange('7d')}>최근 7일</button>
          </div>
        </div>
        {popular && popular.length === 0 && <p className="small muted">조회 기록이 없습니다.</p>}
        <ol className="small" style={{ margin: 0, paddingLeft: 22 }}>
          {popular?.map((row) => (
            <li key={row.post.id}><Link to={`/${row.post.id}`}>{row.post.title}</Link> <span className="muted num">{row.viewCount.toLocaleString()}</span></li>
          ))}
        </ol>
      </section>
      <section className="section" aria-label="유입 경로">
        <h2>유입 경로 <span className="small muted">최근 30일</span></h2>
        {referrers && (
          <>
            <div className="row small">
              {referrers.byType.map((row) => <span key={row.type} className="chip">{referrerLabel(row.type)} <b className="num">{row.count}</b></span>)}
            </div>
            {referrers.bySite.length === 0
              ? <p className="small muted">방문 기록이 없습니다.</p>
              : (
                <div className="table-wrap">
                  <table className="manage-table">
                    <thead><tr><th>종류</th><th>사이트</th><th className="num">방문</th></tr></thead>
                    <tbody>
                      {referrers.bySite.map((row) => (
                        <tr key={`${row.type}-${row.host}`}>
                          <td className="small">{referrerLabel(row.type)}</td>
                          <td className="small">{row.host || '—'}</td>
                          <td className="num">{row.count.toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          </>
        )}
      </section>
    </main>
  )
}
