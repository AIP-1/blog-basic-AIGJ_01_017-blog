import { useState } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ModerationLogLine } from '../../api/types'
import { actionLabel, releasePath, reasonLabel } from '../../app/admin'
import { formatDateTime } from '../../app/format'
import TargetLink from './TargetLink'

/**
 * 관리 이력 표 (대시보드의 최근 조치, 회원 상세의 제재 이력, 관리 이력 검색). 이력 자체는 조회만 된다.
 * 대상이 지금도 숨김·제한·정지 중이면 그 대상의 가장 최근 줄에 "해제" 버튼을 둔다(onChanged가 있을 때).
 */
export default function LogTable({ logs, empty = '이력이 없습니다.', onChanged }: {
  logs: ModerationLogLine[]
  empty?: string
  onChanged?: () => void
}) {
  const [error, setError] = useState<string | null>(null)

  if (logs.length === 0) {
    return <p className="small muted">{empty}</p>
  }
  // 최신순이라 대상마다 처음 나오는 줄이 가장 최근 조치다
  const latest = new Set<number>()
  const seen = new Set<string>()
  for (const log of logs) {
    const key = `${log.target.type}-${log.target.id}`
    if (!seen.has(key)) {
      seen.add(key)
      latest.add(log.id)
    }
  }

  async function release(log: ModerationLogLine) {
    const path = releasePath(log)
    if (!path) {
      return
    }
    setError(null)
    try {
      await api(path, { method: 'DELETE' })
      onChanged?.()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <div className="table-wrap">
      {error && <p className="err" role="alert">{error}</p>}
      <table className="manage-table">
        <thead>
          <tr><th>시각</th><th>조치</th><th>대상</th><th>사유</th><th>관리자</th>{onChanged && <th />}</tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id}>
              <td className="num small nowrap">{formatDateTime(log.createdAt)}</td>
              <td className="nowrap">{actionLabel(log.action)}</td>
              <td><TargetLink target={log.target} /></td>
              <td className="small">
                {reasonLabel(log.reason)}{log.reasonDetail ? ` — ${log.reasonDetail}` : ''}
              </td>
              <td className="small nowrap">{log.admin.nickname}</td>
              {onChanged && (
                <td>
                  {latest.has(log.id) && log.target.sanctioned && releasePath(log) && (
                    <button className="btn small" type="button" onClick={() => release(log)}>해제</button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
