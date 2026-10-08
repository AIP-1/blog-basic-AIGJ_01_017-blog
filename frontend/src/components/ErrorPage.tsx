import { Link } from 'react-router'
import { loginUrl } from '../api/client'
import { parseHost, platformUrl } from '../app/host'

const MESSAGES = {
  401: { title: '로그인이 필요합니다', body: '로그인하면 보던 화면으로 돌아옵니다.' },
  403: { title: '접근 권한이 없습니다', body: '이 화면을 볼 수 있는 권한이 없습니다.' },
  404: { title: '페이지를 찾을 수 없습니다', body: '주소가 바뀌었거나 볼 수 없는 페이지입니다.' },
  500: { title: '잠시 문제가 생겼습니다', body: '잠시 후 다시 시도해 주세요.' },
} as const

/**
 * 오류 화면 (T051, COM-02, 목업 errors). 서버의 내부 정보(스택 등)는 서버가 응답에 담지 않으므로 여기서도 보여 줄 것이 없다.
 * 404는 존재를 숨기는 경우도 포함한다(남의 비공개 글 등, 헌법 원칙 II).
 */
export default function ErrorPage({ status, message }: { status: keyof typeof MESSAGES; message?: string }) {
  const { title, body } = MESSAGES[status]
  return (
    <main className="page narrow" style={{ paddingTop: 60, textAlign: 'center' }}>
      <div className="muted num" style={{ fontSize: 40, fontWeight: 700 }}>{status}</div>
      <h1 style={{ fontSize: 22 }}>{title}</h1>
      <p className="muted">{message ?? body}</p>
      <div className="row" style={{ justifyContent: 'center' }}>
        {status === 401
          ? <a className="btn primary" href={loginUrl()}>로그인</a>
          : <a className="btn" href={platformUrl('/')}>홈으로</a>}
        {status === 403 && parseHost(window.location.hostname).kind === 'blog'
          && <Link className="btn" to="/">이 블로그 처음으로</Link>}
      </div>
    </main>
  )
}
