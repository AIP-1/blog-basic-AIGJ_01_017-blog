import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { blogUrl } from '../app/host'
import { notificationLabel } from '../app/notifications'
import type { MeState } from '../app/useMe'
import { useUnreadCount } from '../app/useUnreadCount'
import { writeUrl } from '../app/writeLink'
import LogoutButton from './LogoutButton'
import SuspensionNotice from './SuspensionNotice'

/**
 * 플랫폼 주소(blog.com)의 머리글. 비회원이면 로그인·회원가입, 회원이면 글쓰기, 내 블로그(없으면 블로그 만들기),
 * 마이페이지(내 정보, 내 블로그 목록과 블로그 만들기 n/5). 서비스 관리자에게는 서비스 관리(/admin, ADMIN-01).
 * 로그인한 채로 정지된 회원이면 머리글 아래에 정지 사유를 띄운다(ADMIN-02).
 * 검색창은 전체 검색(/search, SRCH-02)으로 간다. 블로그 머리글의 검색창은 그 블로그 안 검색이다.
 * 글쓰기는 대표 블로그의 글쓰기로, 블로그가 없으면 개설 안내로 간다 (AUTH-04).
 */
export default function PlatformHeader({ me }: { me: MeState }) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const unread = useUnreadCount(me)

  function search(event: FormEvent) {
    event.preventDefault()
    if (q.trim()) {
      navigate(`/search?${new URLSearchParams({ q: q.trim(), type: 'post' })}`)
      setQ('')
    }
  }

  return (
    <>
    <header className="site-head">
      <Link className="logo" to="/">blog</Link>
      <nav className="nav-links">
        <Link to="/">홈</Link>
        {me.status === 'member' && <Link to="/feed">구독 피드</Link>}
      </nav>
      <span className="grow" />
      <form className="search-box" role="search" onSubmit={search}>
        <input type="search" value={q} maxLength={100} placeholder="글·블로그 검색" aria-label="전체 검색"
               onChange={(event) => setQ(event.target.value)} />
        <button className="btn" type="submit">검색</button>
      </form>
      {me.status === 'anonymous' && (
        <div className="row">
          <Link className="btn" to="/login">로그인</Link>
          <Link className="btn primary" to="/signup">회원가입</Link>
        </div>
      )}
      {me.status === 'member' && (
        <div className="row">
          <a className="btn primary" href={writeUrl(me.me)}>글쓰기</a>
          {me.me.primaryBlog
            ? <a className="btn" href={blogUrl(me.me.primaryBlog.address)}>내 블로그</a>
            : <Link className="btn" to="/blogs/new">블로그 만들기</Link>}
          <Link className="btn" to="/me/notifications">{notificationLabel(unread)}</Link>
          <Link className="btn" to="/me" title={me.me.nickname}>마이페이지</Link>
          {me.me.role === 'ADMIN' && <Link className="btn" to="/admin">서비스 관리</Link>}
          <LogoutButton />
        </div>
      )}
    </header>
    {me.status === 'anonymous' && me.suspension && (
      <div className="page"><SuspensionNotice suspension={me.suspension} /></div>
    )}
    </>
  )
}
