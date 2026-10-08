import { Link } from 'react-router'
import { loginUrl } from '../api/client'
import type { Blog } from '../api/types'
import { platformUrl } from '../app/host'
import type { MeState } from '../app/useMe'
import LogoutButton from './LogoutButton'

/** 블로그 주소({address}.blog.com)의 머리글. 주인에게는 관리 버튼이 보인다. */
export default function BlogHeader({ blog, me }: { blog: Blog; me: MeState }) {
  return (
    <header className="site-head">
      <a className="logo" href={platformUrl('/')} title="플랫폼 홈">blog</a>
      <Link className="blog-name" to="/">{blog.name}</Link>
      <nav className="nav-links"><Link to="/">홈</Link></nav>
      <span className="grow" />
      {me.status === 'anonymous' && <a className="btn" href={loginUrl()}>로그인</a>}
      {me.status === 'member' && (
        <div className="row">
          {blog.viewer.isOwner && <Link className="btn" to="/manage">관리</Link>}
          <span className="small">{me.me.nickname}</span>
          <LogoutButton />
        </div>
      )}
    </header>
  )
}
