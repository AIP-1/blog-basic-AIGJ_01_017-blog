import { Link } from 'react-router'
import { blogUrl } from '../app/host'
import type { MeState } from '../app/useMe'
import { writeUrl } from '../app/writeLink'
import LogoutButton from './LogoutButton'

/**
 * 플랫폼 주소(blog.com)의 머리글. 비회원이면 로그인·회원가입, 회원이면 글쓰기, 내 블로그(없으면 블로그 만들기),
 * 마이페이지(내 정보, 내 블로그 목록과 블로그 만들기 n/5).
 * 글쓰기는 대표 블로그의 글쓰기로, 블로그가 없으면 개설 안내로 간다 (AUTH-04).
 */
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
          <a className="btn primary" href={writeUrl(me.me)}>글쓰기</a>
          {me.me.primaryBlog
            ? <a className="btn" href={blogUrl(me.me.primaryBlog.address)}>내 블로그</a>
            : <Link className="btn" to="/blogs/new">블로그 만들기</Link>}
          <Link className="btn" to="/me" title={me.me.nickname}>마이페이지</Link>
          <LogoutButton />
        </div>
      )}
    </header>
  )
}
