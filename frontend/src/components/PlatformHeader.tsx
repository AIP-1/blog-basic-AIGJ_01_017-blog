import { Link } from 'react-router'
import { blogUrl } from '../app/host'
import type { MeState } from '../app/useMe'
import LogoutButton from './LogoutButton'

/** 플랫폼 주소(blog.com)의 머리글. 비회원이면 로그인·회원가입, 회원이면 내 블로그(없으면 블로그 만들기). */
export default function PlatformHeader({ me }: { me: MeState }) {
  return (
    <header className="site-head">
      <Link className="logo" to="/">blog</Link>
      <nav className="nav-links"><Link to="/">홈</Link></nav>
      <span className="grow" />
      {me.status === 'anonymous' && (
        <div className="row">
          <Link className="btn" to="/login">로그인</Link>
          <Link className="btn primary" to="/signup">회원가입</Link>
        </div>
      )}
      {me.status === 'member' && (
        <div className="row">
          {me.me.primaryBlog
            ? <a className="btn primary" href={blogUrl(me.me.primaryBlog.address)}>내 블로그</a>
            : <Link className="btn primary" to="/blogs/new">블로그 만들기</Link>}
          <Link className="small" to="/me">{me.me.nickname}</Link>
          <LogoutButton />
        </div>
      )}
    </header>
  )
}
