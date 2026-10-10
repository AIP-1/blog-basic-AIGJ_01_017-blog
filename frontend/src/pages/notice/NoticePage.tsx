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
 * 공지 상세 (ADMIN-06, blog.com/notices/{id}). 내용은 HTML이 아니라 글자라서 그대로 넣고 줄바꿈만 살린다
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
      <main className="page narrow stack">
        <Link className="small" to="/notices">← 공지 목록</Link>
        {error && <p className="err" role="alert">{error}</p>}
        {notice && (
          <article className="stack">
            <h1 style={{ fontSize: 22 }}>{notice.title}</h1>
            <span className="small muted num">{formatDate(notice.createdAt)}</span>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{notice.content}</p>
          </article>
        )}
      </main>
    </div>
  )
}
