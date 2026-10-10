import type { SuspensionDetail } from '../api/types'
import { formatDateTime } from '../app/format'

/**
 * 이용 정지 안내 (ADMIN-02). 로그인할 때(LoginPage)와, 이미 로그인한 채로 정지된 회원이 다음 화면을 열 때(useMe) 같은 모양으로 보인다.
 * 서버는 정지 회원의 API 요청에 403 MEMBER_SUSPENDED와 사유·기한을 주고 로그인 쿠키를 지운다.
 */
export default function SuspensionNotice({ suspension }: { suspension: SuspensionDetail }) {
  return (
    <div className="box danger" role="alert">
      <b>이용이 정지된 계정입니다</b>
      {suspension.reasonMessage && <span>사유: {suspension.reasonMessage}</span>}
      <span className="num">
        {suspension.suspendedUntil ? `정지 기한: ${formatDateTime(suspension.suspendedUntil)}까지` : '영구 정지'}
      </span>
    </div>
  )
}
