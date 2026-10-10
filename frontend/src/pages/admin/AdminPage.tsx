import { useEffect } from 'react'
import { NavLink, Route, Routes } from 'react-router'
import { redirectToLogin } from '../../api/client'
import { shouldRedirectToLogin, useMe } from '../../app/useMe'
import ErrorPage from '../../components/ErrorPage'
import PlatformHeader from '../../components/PlatformHeader'
import NotFoundPage from '../error/NotFoundPage'
import AdminDashboardPage from './AdminDashboardPage'
import AdminLogsPage from './AdminLogsPage'
import AdminMembersPage from './AdminMembersPage'
import AdminReportsPage from './AdminReportsPage'

/**
 * 서비스 관리 영역 (/admin/**, ADMIN-01~06). 비회원은 로그인으로, 일반 회원은 403 화면이다.
 * 이 검사는 안내용이고, 실제로는 서버가 /api/admin/**을 관리자만 통과시킨다(SecurityConfig).
 * 블로그 관리 화면(/manage)과 같은 틀: 왼쪽 메뉴, 오른쪽 내용.
 */
export default function AdminPage() {
  const me = useMe()

  useEffect(() => {
    if (shouldRedirectToLogin(me)) {
      redirectToLogin()
    }
  }, [me])

  if (me.status === 'anonymous' && me.suspension) {
    return <div className="app"><PlatformHeader me={me} /></div>
  }
  if (me.status !== 'member') {
    return null
  }
  if (me.me.role !== 'ADMIN') {
    return <ErrorPage status={403} message="서비스 관리자만 들어올 수 있습니다." />
  }
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <div className="manage">
        <nav className="manage-nav" aria-label="서비스 관리 메뉴">
          <div className="sec">서비스 관리</div>
          <NavLink to="/admin" end>대시보드</NavLink>
          <NavLink to="/admin/members">회원</NavLink>
          <NavLink to="/admin/reports">신고</NavLink>
          <NavLink to="/admin/logs">관리 이력·공지</NavLink>
        </nav>
        <Routes>
          <Route index element={<AdminDashboardPage />} />
          <Route path="members" element={<AdminMembersPage />} />
          <Route path="reports" element={<AdminReportsPage />} />
          <Route path="logs" element={<AdminLogsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </div>
    </div>
  )
}
