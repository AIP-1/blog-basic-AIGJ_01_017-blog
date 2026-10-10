import { Route, Routes } from 'react-router'
import AdminPage from '../pages/admin/AdminPage'
import LoginPage from '../pages/auth/LoginPage'
import SignupPage from '../pages/auth/SignupPage'
import BlogCreatePage from '../pages/blog/BlogCreatePage'
import BlogMainPage from '../pages/blog/BlogMainPage'
import GuestbookPage from '../pages/blog/GuestbookPage'
import NotFoundPage from '../pages/error/NotFoundPage'
import FeedPage from '../pages/feed/FeedPage'
import MyPage from '../pages/me/MyPage'
import NotificationsPage from '../pages/me/NotificationsPage'
import WithdrawPage from '../pages/me/WithdrawPage'
import NoticePage from '../pages/notice/NoticePage'
import NoticesPage from '../pages/notice/NoticesPage'
import HomePage from '../pages/home/HomePage'
import ManagePage from '../pages/manage/ManagePage'
import PostPage from '../pages/post/PostPage'
import BlogSearchPage from '../pages/search/BlogSearchPage'
import GlobalSearchPage from '../pages/search/GlobalSearchPage'

/** 플랫폼 주소(blog.com)의 화면 */
export function PlatformRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/blogs/new" element={<BlogCreatePage />} />
      <Route path="/me" element={<MyPage />} />
      <Route path="/me/notifications" element={<NotificationsPage />} />
      <Route path="/me/withdraw" element={<WithdrawPage />} />
      <Route path="/feed" element={<FeedPage />} />
      <Route path="/search" element={<GlobalSearchPage />} />
      <Route path="/notices" element={<NoticesPage />} />
      <Route path="/notices/:noticeId" element={<NoticePage />} />
      <Route path="/admin/*" element={<AdminPage />} />
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
      <Route path="/tag/:tagName" element={<BlogMainPage />} />
      <Route path="/search" element={<BlogSearchPage />} />
      <Route path="/guestbook" element={<GuestbookPage />} />
      <Route path="/:postId" element={<PostPage />} />
      <Route path="/manage/*" element={<ManagePage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
