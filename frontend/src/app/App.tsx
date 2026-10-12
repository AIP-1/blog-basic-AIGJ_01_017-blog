import { BrowserRouter } from 'react-router'
import { blogBase, parseLocation } from './host'
import { BlogRoutes, PlatformRoutes } from './routes'

/**
 * 주소에 따라 플랫폼 화면과 블로그 화면을 나눈다 (스텝 21). 블로그 화면(/@alpha/…)은 라우터 기준 경로(basename)를
 * /@alpha로 둔다. 그래서 블로그 화면 안의 링크(<Link to="/manage">)는 저절로 /@alpha/manage가 된다.
 * 플랫폼 ↔ 블로그, 블로그 ↔ 다른 블로그 이동은 blogUrl·platformUrl로 페이지를 새로 연다.
 */
export default function App() {
  const host = parseLocation(window.location.pathname)
  return host.kind === 'platform'
    ? <BrowserRouter><PlatformRoutes /></BrowserRouter>
    : <BrowserRouter basename={blogBase(host.address)}><BlogRoutes /></BrowserRouter>
}
