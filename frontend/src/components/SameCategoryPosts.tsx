import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { PostSummary } from '../api/types'
import { formatDate } from '../app/format'

/**
 * 같은 카테고리의 다른 글 (OWN-05, 목업 post-detail 8번). 같은 블로그의 글이라 Link로 연다.
 * 미분류 글이거나 다른 글이 없으면(서버가 빈 배열) 영역을 숨긴다. 실패해도 글 읽기에 지장이 없게 숨긴다.
 */
export default function SameCategoryPosts({ postId, categoryName }: { postId: number; categoryName: string }) {
  const [posts, setPosts] = useState<PostSummary[]>([])

  useEffect(() => {
    let active = true
    api<PostSummary[]>(`/api/posts/${postId}/same-category?size=5`, { allowAnonymous: true })
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
    <section className="section" aria-label="같은 카테고리의 다른 글">
      <h2>'{categoryName}' 카테고리의 다른 글</h2>
      <ul className="stack" style={{ gap: 4, margin: 0, paddingLeft: 18 }}>
        {posts.map((post) => (
          <li key={post.id}>
            <Link to={`/${post.id}`}>{post.title}</Link>
            <span className="small muted num"> {formatDate(post.publishedAt)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
