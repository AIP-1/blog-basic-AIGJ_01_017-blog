import { useEffect } from 'react'
import { redirectToLogin } from '../../api/client'
import { useMe } from '../../app/useMe'
import ErrorPage from '../../components/ErrorPage'
import PlatformHeader from '../../components/PlatformHeader'

/**
 * 서비스 관리 영역 (T051, ADMIN-01). 비회원은 로그인으로, 일반 회원은 403 화면이다.
 * 이 검사는 안내용이고, 실제로는 서버가 /api/admin/**을 관리자만 통과시킨다(SecurityConfig).
 * 관리 기능(회원 정지, 신고 처리 등)은 뒤 스텝에서 이 화면에 더한다.
 */
export default function AdminPage() {
  const me = useMe()

  useEffect(() => {
    if (me.status === 'anonymous') {
      redirectToLogin()
    }
  }, [me.status])

  if (me.status !== 'member') {
    return null
  }
  if (me.me.role !== 'ADMIN') {
    return <ErrorPage status={403} message="서비스 관리자만 들어올 수 있습니다." />
  }
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <h1 style={{ fontSize: 22 }}>서비스 관리</h1>
        <p className="muted">관리 기능(회원 정지, 게시물 숨김, 신고 처리 등)은 뒤 스텝에서 더합니다.</p>
      </main>
    </div>
  )
}
