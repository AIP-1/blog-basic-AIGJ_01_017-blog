import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { loginUrl } from '../api/client'
import type { Blog } from '../api/types'
import { platformUrl } from '../app/host'
import type { MeState } from '../app/useMe'
import { writeUrl } from '../app/writeLink'
import LogoutButton from './LogoutButton'

/**
 * 블로그 주소({address}.blog.com)의 머리글. 주인에게는 이 블로그의 글쓰기·관리 버튼이 보인다.
 * 남의 블로그에서 누르는 글쓰기는 내 대표 블로그의 글쓰기로, 블로그가 없으면 개설 안내로 간다 (AUTH-04).
 */
export default function BlogHeader({ blog, me }: { blog: Blog; me: MeState }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')

  function search(event: FormEvent) {
    event.preventDefault()
    if (q.trim()) {
      navigate(`/search?${new URLSearchParams({ q: q.trim() })}`)
    }
  }

  return (
    <header className="site-head">
      <a className="logo" href={platformUrl('/')} title="플랫폼 홈">blog</a>
      <Link className="blog-name" to="/">{blog.name}</Link>
      <nav className="nav-links"><Link to="/">홈</Link></nav>
      <span className="grow" />
      <form className="search-box" role="search" onSubmit={search}>
        <input type="search" value={q} maxLength={100} placeholder="이 블로그에서 검색" aria-label="블로그 내 검색"
               onChange={(event) => setQ(event.target.value)} />
        <button className="btn" type="submit">검색</button>
      </form>
      {me.status === 'anonymous' && <a className="btn" href={loginUrl()}>로그인</a>}
      {me.status === 'member' && (
        <div className="row">
          {blog.viewer.isOwner
            ? <Link className="btn primary" to="/manage/write">글쓰기</Link>
            : <a className="btn primary" href={writeUrl(me.me)}>글쓰기</a>}
          {blog.viewer.isOwner && <Link className="btn" to="/manage">관리</Link>}
          <span className="small">{me.me.nickname}</span>
          <LogoutButton />
        </div>
      )}
    </header>
  )
}
