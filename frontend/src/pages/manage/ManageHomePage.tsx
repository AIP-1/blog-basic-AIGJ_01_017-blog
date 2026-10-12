import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Blog, ManageSummary } from '../../api/types'
import { blogDisplayAddress } from '../../app/host'

/**
 * 관리 홈 (MNG-03, 목업 manage-home). 오늘·어제·누적 방문자(주인 본인 방문은 세지 않음)와 최근 받은 댓글·최근 글 5개씩.
 * 어제까지는 새벽에 모은 값이고 오늘은 바로 센다.
 */
export default function ManageHomePage({ blog }: { blog: Blog }) {
  const [summary, setSummary] = useState<ManageSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<ManageSummary>('/api/manage/stats').then(setSummary).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  return (
    <main className="page">
      <section className="section">
        <h2>{blog.name}</h2>
        <div className="small muted">{blogDisplayAddress(blog.address)}</div>
        <div className="row small">
          <span>글 <b className="num">{blog.postCount}</b></span>
          <span>구독자 <b className="num">{blog.subscriberCount}</b></span>
        </div>
      </section>
      {error && <p className="err" role="alert">{error}</p>}
      {summary && (
        <>
          <section className="section" aria-label="방문자">
            <div className="row" style={{ gap: 12 }}>
              <Stat label="오늘 방문자" value={summary.today} />
              <Stat label="어제" value={summary.yesterday} />
              <Stat label="누적" value={summary.total} />
            </div>
            <p className="small muted" style={{ margin: 0 }}>주인 본인 방문은 세지 않습니다. <Link to="/manage/stats">방문 통계</Link></p>
          </section>
          <div className="cols" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 20 }}>
            <section className="section">
              <h3>최근 댓글</h3>
              {summary.recentComments.length === 0 && <p className="small muted">받은 댓글이 없습니다.</p>}
              <ul className="small" style={{ paddingLeft: 18, margin: 0 }}>
                {summary.recentComments.map((comment) => (
                  <li key={comment.id}>
                    {comment.state === 'NORMAL'
                      ? <>{comment.author?.nickname} · {comment.content}</>
                      : <span className="muted">{HIDDEN[comment.state]}</span>}
                    {comment.post && <> — <Link to={`/${comment.post.id}#comment-${comment.id}`}>{comment.post.title}</Link></>}
                  </li>
                ))}
              </ul>
              <Link className="small" to="/manage/comments">댓글 관리</Link>
            </section>
            <section className="section">
              <h3>최근 글</h3>
              {summary.recentPosts.length === 0 && <p className="small muted">아직 발행한 글이 없습니다.</p>}
              <ul className="small" style={{ paddingLeft: 18, margin: 0 }}>
                {summary.recentPosts.map((post) => (
                  <li key={post.id}><Link to={`/${post.id}`}>{post.title}</Link> <span className="muted num">조회 {post.viewCount}</span></li>
                ))}
              </ul>
              <Link className="small" to="/manage/posts">글 관리</Link>
            </section>
          </div>
        </>
      )}
      <div className="row">
        <Link className="btn" to="/manage/settings">블로그 설정</Link>
        <Link className="btn" to="/">블로그 보기</Link>
      </div>
    </main>
  )
}

/** 내용이 가려진 댓글의 문구 (댓글 화면과 같음) */
const HIDDEN = { SECRET: '비밀댓글입니다.', BLINDED: '관리자가 숨긴 댓글입니다.', DELETED: '삭제된 댓글입니다.' } as const

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="box" style={{ minWidth: 120 }}>
      <span className="small muted">{label}</span>
      <b className="num" style={{ fontSize: 22 }}>{value.toLocaleString()}</b>
    </div>
  )
}
