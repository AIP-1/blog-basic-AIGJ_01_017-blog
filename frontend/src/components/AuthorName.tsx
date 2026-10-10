import type { MemberSummary } from '../api/types'
import { blogUrl } from '../app/host'

/**
 * 글·댓글 작성자 닉네임 (BLOG-08). 대표 블로그가 있으면 그 블로그로 가는 링크다.
 * 대표 블로그는 다른 주소({address}.blog.com)라 react-router의 Link가 아니라 a로 페이지를 바꾼다.
 * 서버는 대표 블로그가 없거나 보는 사람이 볼 수 없으면(삭제·이용 제한 등) primaryBlogAddress를 null로 준다.
 */
export default function AuthorName({ author, bold = false }: { author: MemberSummary; bold?: boolean }) {
  const name = bold ? <b>{author.nickname}</b> : author.nickname
  if (!author.primaryBlogAddress) {
    return <span>{name}</span>
  }
  return <a href={blogUrl(author.primaryBlogAddress)} title={`${author.nickname}의 블로그`}>{name}</a>
}
