import { Route, Routes } from 'react-router'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import BlogCreatePage from '../pages/blog/BlogCreatePage'
import BlogMainPage from '../pages/blog/BlogMainPage'
import NotFoundPage from '../pages/error/NotFoundPage'
import HomePage from '../pages/home/HomePage'
import ManagePage from '../pages/manage/ManagePage'
import PostPage from '../pages/post/PostPage'

/** 플랫폼 주소(blog.com)의 화면 */
export function PlatformRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/blogs/new" element={<BlogCreatePage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

/** 블로그 주소({address}.blog.com)의 화면. 블로그는 Host로 정하므로 주소를 따로 넘기지 않는다 */
export function BlogRoutes() {
  return (
    <Routes>
      <Route path="/" element={<BlogMainPage />} />
      <Route path="/category/:categoryId" element={<BlogMainPage />} />
      <Route path="/:postId" element={<PostPage />} />
      <Route path="/manage/*" element={<ManagePage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
