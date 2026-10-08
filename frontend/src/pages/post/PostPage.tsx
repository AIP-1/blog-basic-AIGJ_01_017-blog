import { useParams } from 'react-router'

export default function PostPage() {
  const { postId } = useParams()
  return (
    <main>
      <h1>글 {postId}</h1>
      <p>글 상세는 스텝 6에서 만듭니다.</p>
    </main>
  )
}
