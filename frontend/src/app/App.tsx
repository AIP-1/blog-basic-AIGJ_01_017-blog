import { BrowserRouter } from 'react-router'
import { parseHost } from './host'
import { BlogRoutes, PlatformRoutes } from './routes'

/** 주소에 따라 플랫폼 화면과 블로그 화면을 나눈다 */
export default function App() {
  const host = parseHost(window.location.hostname)
  return (
    <BrowserRouter>
      {host.kind === 'platform' ? <PlatformRoutes /> : <BlogRoutes />}
    </BrowserRouter>
  )
}
