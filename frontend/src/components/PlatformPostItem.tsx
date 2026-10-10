import type { PostSummary } from '../api/types'
import { formatDateTime } from '../app/format'
import { blogUrl } from '../app/host'

/**
 * 플랫폼 주소(홈 최신 글, 전체 검색)의 글 한 줄. 글마다 블로그가 달라서 제목과 블로그 이름은
 * 그 블로그 주소({address}.blog.com)로 가는 a다(블로그 안 목록의 PostItem은 같은 호스트라 Link).
 */
export default function PlatformPostItem({ post }: { post: PostSummary }) {
  return (
    <article className={post.thumbnailUrl ? 'post-item has-thumb' : 'post-item'}>
      <div className="stack" style={{ gap: 2 }}>
        <h3><a href={blogUrl(post.blog.address, `/${post.id}`)}>{post.title}</a></h3>
        {post.summary && <p>{post.summary}</p>}
        <div className="meta">
          <a href={blogUrl(post.blog.address)}>{post.blog.name}</a>
          <span>{formatDateTime(post.publishedAt)}</span>
          <span>공감 {post.likeCount}</span>
          <span>댓글 {post.commentCount}</span>
        </div>
      </div>
      {/* 썸네일은 플랫폼 주소에서도 같은 서버의 /uploads라 그대로 연다 */}
      {post.thumbnailUrl && <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" />}
    </article>
  )
}
