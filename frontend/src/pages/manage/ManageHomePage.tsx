import { Link } from 'react-router'
import type { Blog } from '../../api/types'
import { PLATFORM_DOMAIN } from '../../app/host'

/** 관리 홈. 방문 통계(MNG-03)는 백로그라 지금은 블로그 요약과 바로가기만 있다. */
export default function ManageHomePage({ blog }: { blog: Blog }) {
  return (
    <main className="page">
      <section className="section">
        <h2>{blog.name}</h2>
        <div className="small muted">{blog.address}.{PLATFORM_DOMAIN}</div>
        <div className="row small">
          <span>글 <b className="num">{blog.postCount}</b></span>
          <span>구독자 <b className="num">{blog.subscriberCount}</b></span>
        </div>
      </section>
      <div className="row">
        <Link className="btn" to="/manage/settings">블로그 설정</Link>
        <Link className="btn" to="/">블로그 보기</Link>
      </div>
    </main>
  )
}
