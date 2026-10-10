import type { ModerationLogLine } from '../../api/types'
import { actionLabel, reasonLabel } from '../../app/admin'
import { formatDateTime } from '../../app/format'
import TargetLink from './TargetLink'

/** 관리 이력 표 (대시보드의 최근 조치, 회원 상세의 제재 이력, 관리 이력 검색). 조회만 된다 */
export default function LogTable({ logs, empty = '이력이 없습니다.' }: { logs: ModerationLogLine[]; empty?: string }) {
  if (logs.length === 0) {
    return <p className="small muted">{empty}</p>
  }
  return (
    <div className="table-wrap">
      <table className="manage-table">
        <thead>
          <tr><th>시각</th><th>조치</th><th>대상</th><th>사유</th><th>관리자</th></tr>
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
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
