import { Link } from 'react-router'
import type { PostSummary } from '../api/types'
import { formatDate } from '../app/format'

/**
 * 썸네일 목록 한 칸 (BLOG-05 목록 형태 "썸네일"). 대표 이미지가 없으면 회색 칸에 제목 첫 글자를 둔다.
 */
export function PostCard({ post }: { post: PostSummary }) {
  return (
    <Link className="card" to={`/${post.id}`}>
      {post.thumbnailUrl
        ? <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" />
        : <span className="thumb placeholder" aria-hidden="true">{post.title.slice(0, 1)}</span>}
      <h3>{post.title}</h3>
      <span className="small muted">{formatDate(post.publishedAt)} · 공감 {post.likeCount} · 댓글 {post.commentCount}</span>
    </Link>
  )
}

/** 블로그 안 목록 한 줄 (블로그 메인, 카테고리·태그별, 검색 결과). 썸네일이 있으면 오른쪽에. 주인에게는 수정 링크. */
export function PostItem({ post, owner }: { post: PostSummary; owner: boolean }) {
  return (
    <article className={post.thumbnailUrl ? 'post-item has-thumb' : 'post-item'}>
      <div className="stack" style={{ gap: 2 }}>
        <div className="row between nowrap">
          <h3><Link to={`/${post.id}`}>{post.title}</Link></h3>
          {owner && <Link className="small" to={`/manage/posts/${post.id}/edit`}>수정</Link>}
        </div>
        {post.summary && <p>{post.summary}</p>}
        <div className="meta">
          <Link to={`/category/${post.category?.id ?? 0}`}>{post.category?.name ?? '미분류'}</Link>
          <span>{formatDate(post.publishedAt)}</span>
          <span>공감 {post.likeCount}</span>
          <span>댓글 {post.commentCount}</span>
        </div>
      </div>
      {post.thumbnailUrl && <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" />}
    </article>
  )
}
