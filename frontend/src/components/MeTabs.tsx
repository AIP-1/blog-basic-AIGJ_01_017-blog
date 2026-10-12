import { NavLink } from 'react-router'

/** 마이페이지의 탭 (목업 mypage·bookmarks·notifications): 내 정보, 저장한 글, 알림 */
export default function MeTabs() {
  return (
    <nav className="tabs" aria-label="마이페이지">
      <NavLink to="/me" end className={({ isActive }) => (isActive ? 'on' : undefined)}>내 정보</NavLink>
      <NavLink to="/me/bookmarks" className={({ isActive }) => (isActive ? 'on' : undefined)}>저장한 글</NavLink>
      <NavLink to="/me/notifications" className={({ isActive }) => (isActive ? 'on' : undefined)}>알림</NavLink>
    </nav>
  )
}
