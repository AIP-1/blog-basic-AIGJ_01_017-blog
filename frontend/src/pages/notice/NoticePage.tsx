import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Notice } from '../../api/types'
import { formatDate } from '../../app/format'
import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'
import NotFoundPage from '../error/NotFoundPage'

/**
 * 공지 상세 (ADMIN-06, blog.com/notices/{id}). 글 상세와 같은 모양(제목·날짜 머리와 구분선, 읽기 좋은 폭의 본문)이다.
 * 내용은 HTML이 아니라 글자라서 그대로 넣고 줄바꿈만 살린다
 * (Thymeleaf의 th:text처럼 글자로 넣으니 태그가 실행되지 않는다).
 */
export default function NoticePage() {
  const me = useMe()
  const { noticeId } = useParams()
  const [notice, setNotice] = useState<Notice | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Notice>(`/api/notices/${noticeId}`, { allowAnonymous: true })
      .then(setNotice)
      .catch((caught: unknown) => {
        if (caught instanceof ApiError && caught.status === 404) {
          setNotFound(true)
        } else {
          setError(errorMessage(caught))
        }
      })
  }, [noticeId])

  if (notFound) {
    return <NotFoundPage />
  }
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page" style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
        <Link className="small" to="/notices">← 공지 목록</Link>
        {error && <p className="err" role="alert">{error}</p>}
        {notice && (
          <article className="stack" style={{ gap: 20 }}>
            <header className="post-head">
              <span className="chip brand" style={{ justifySelf: 'start' }}>공지</span>
              <h1>{notice.title}</h1>
              <div className="row small muted num">
                <span>{formatDate(notice.createdAt)}</span>
                {notice.updatedAt.slice(0, 16) !== notice.createdAt.slice(0, 16) && (
                  <span>수정 {formatDate(notice.updatedAt)}</span>
                )}
              </div>
            </header>
            <div className="prose">
              <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{notice.content}</p>
            </div>
          </article>
        )}
      </main>
    </div>
  )
}
