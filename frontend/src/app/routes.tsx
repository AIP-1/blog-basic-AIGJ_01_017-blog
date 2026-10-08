import { Route, Routes } from 'react-router'
import LoginPage from '../pages/auth/LoginPage'
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
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

/** 블로그 주소({address}.blog.com)의 화면 */
export function BlogRoutes({ address }: { address: string }) {
  return (
    <Routes>
      <Route path="/" element={<BlogMainPage address={address} />} />
      <Route path="/:postId" element={<PostPage />} />
      <Route path="/manage/*" element={<ManagePage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
