import { useEffect } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import { redirectToLogin } from '../../api/client'
import type { Blog } from '../../api/types'
import { platformUrl } from '../../app/host'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import LogoutButton from '../../components/LogoutButton'
import NotFoundPage from '../error/NotFoundPage'
import BlogSettingsPage from './BlogSettingsPage'
import CategoriesPage from './CategoriesPage'
import ManageHomePage from './ManageHomePage'
import PostWritePage from './PostWritePage'

/**
 * 블로그 관리 화면의 틀 (/manage/**). 비회원은 로그인 화면으로 갔다가 돌아오고, 주인이 아니면 403 안내다.
 * 권한은 서버 API가 다시 검사하므로, 이 화면의 검사는 안내용이다(헌법 원칙 IV).
 */
export default function ManagePage() {
  const me = useMe()
  const [blogState, setBlog] = useBlog()

  useEffect(() => {
    if (me.status === 'anonymous') {
      redirectToLogin()
    }
  }, [me.status])

  if (blogState.status === 'notFound') {
    return <NotFoundPage />
  }
  if (blogState.status === 'error') {
    return <main className="page"><p className="err">{blogState.message}</p></main>
  }
  if (me.status !== 'member' || blogState.status !== 'ok') {
    return null
  }
  const blog = blogState.blog
  if (!blog.viewer.isOwner) {
    return (
      <main className="page narrow">
        <h1 style={{ fontSize: 22 }}>권한이 없습니다</h1>
        <p>이 블로그의 관리 화면은 블로그 주인만 볼 수 있습니다.</p>
        <Link to="/">블로그로 가기</Link>
      </main>
    )
  }

  return (
    <div className="app">
      <header className="site-head">
        <a className="logo" href={platformUrl('/')}>blog</a>
        <Link className="blog-name" to="/">{blog.name}</Link>
        <span className="chip">관리</span>
        <span className="grow" />
        <Link className="btn" to="/">블로그 보기</Link>
        <span className="small">{me.me.nickname}</span>
        <LogoutButton />
      </header>
      {blog.restriction && <RestrictionNotice blog={blog} />}
      <div className="manage">
        <nav className="manage-nav" aria-label="관리 메뉴">
          <div className="sec">블로그 관리</div>
          <NavLink to="/manage" end>관리 홈</NavLink>
          <NavLink to="/manage/write">글쓰기</NavLink>
          <NavLink to="/manage/categories">카테고리</NavLink>
          <div className="sec">설정</div>
          <NavLink to="/manage/settings">블로그 설정</NavLink>
        </nav>
        <Routes>
          <Route index element={<ManageHomePage blog={blog} />} />
          <Route path="write" element={<PostWritePage key="new" />} />
          <Route path="posts/:postId/edit" element={<PostWritePage key="edit" />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="settings" element={<BlogSettingsPage blog={blog} onSaved={setBlog} />} />
          <Route path="*" element={<main className="page"><p className="muted">준비 중인 화면입니다.</p></main>} />
        </Routes>
      </div>
    </div>
  )
}

function RestrictionNotice({ blog }: { blog: Blog }) {
  return (
    <div className="box danger" role="alert" style={{ margin: '12px 20px 0' }}>
      <b>이 블로그는 이용이 제한되었습니다</b>
      {blog.restriction?.reasonMessage && <span>사유: {blog.restriction.reasonMessage}</span>}
    </div>
  )
}
