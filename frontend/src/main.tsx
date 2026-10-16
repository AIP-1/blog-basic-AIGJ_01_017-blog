import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './app/App'
// 글꼴 Pretendard: 쓰인 글자 조각만 내려받는다(dynamic subset). 운영 CSP가 font-src 'self'라 CDN 대신 함께 빌드한다
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
