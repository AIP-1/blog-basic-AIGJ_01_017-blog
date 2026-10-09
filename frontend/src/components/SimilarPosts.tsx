import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { PostSummary } from '../api/types'
import { blogUrl } from '../app/host'

/**
 * 비슷한 글 (OWN-06). 글 내용의 임베딩이 가까운 글을 서버가 골라 준다(다른 블로그 글 포함, 볼 수 있는 글만).
 * 빈 배열이면(추천이 아직 준비 안 됨, 비슷한 글 없음) 영역을 통째로 숨긴다. 실패해도 글 읽기에 지장이 없게 숨긴다.
 * 다른 블로그 글일 수 있어 링크는 <a href>로 그 블로그 주소를 연다.
 */
export default function SimilarPosts({ postId }: { postId: number }) {
  const [posts, setPosts] = useState<PostSummary[]>([])

  useEffect(() => {
    let active = true
    api<PostSummary[]>(`/api/posts/${postId}/similar?size=5`, { allowAnonymous: true })
      .then((result) => active && setPosts(result))
      .catch(() => active && setPosts([]))
    return () => {
      active = false
    }
  }, [postId])

  if (posts.length === 0) {
    return null
  }
  return (
    <section className="section" aria-label="비슷한 글">
      <h2>비슷한 글</h2>
      <div className="grid-cards">
        {posts.map((post) => (
          <a key={post.id} className="card" href={blogUrl(post.blog.address, `/${post.id}`)}>
            {post.thumbnailUrl ? <img className="thumb" src={post.thumbnailUrl} alt="" loading="lazy" /> : <div className="thumb" />}
            <span className="small">{post.title}</span>
            <span className="small muted">{post.blog.name}</span>
          </a>
        ))}
      </div>
    </section>
  )
}
