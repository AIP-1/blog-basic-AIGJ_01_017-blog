import { Link } from 'react-router'
import type { CategoryNode, RecentComment, SidebarModule } from '../api/types'

/**
 * 사이드바 (BLOG-04). 서버가 준 모듈을 받은 순서대로 그린다.
 * 사용자가 쓴 글자(이름, 제목, 댓글)는 React가 글자 그대로 넣는다(HTML로 해석하지 않음).
 */
export default function Sidebar({ modules }: { modules: SidebarModule[] }) {
  return (
    <aside className="sidebar" aria-label="사이드바">
      {modules.map((module) => <SidebarItem key={module.type} module={module} />)}
    </aside>
  )
}

function SidebarItem({ module }: { module: SidebarModule }) {
  switch (module.type) {
    case 'PROFILE':
      return (
        <div className="box">
          <div className="row nowrap">
            <span className="avatar lg" />
            <div>
              <Link to="/"><b>{module.data.name}</b></Link>
              {module.data.description && <div className="small muted">{module.data.description}</div>}
            </div>
          </div>
        </div>
      )
    case 'CATEGORY':
      return (
        <div>
          <h4>카테고리</h4>
          <ul>
            <li><Link to="/">전체 글 <span className="muted num">({module.data.totalCount})</span></Link></li>
            {module.data.categories.map((category) => <CategoryItem key={category.id} category={category} />)}
            {module.data.uncategorizedCount > 0 && (
              <li>
                <Link to="/category/0">미분류 <span className="muted num">({module.data.uncategorizedCount})</span></Link>
              </li>
            )}
          </ul>
        </div>
      )
    case 'RECENT_POST':
      return (
        <div>
          <h4>최근 글</h4>
          {module.data.length === 0
            ? <p className="small muted">아직 글이 없습니다.</p>
            : <ul>{module.data.map((post) => <li key={post.id}><Link to={`/${post.id}`}>{post.title}</Link></li>)}</ul>}
        </div>
      )
    case 'RECENT_COMMENT':
      return (
        <div>
          <h4>최근 댓글</h4>
          {module.data.length === 0
            ? <p className="small muted">아직 댓글이 없습니다.</p>
            : <ul>{module.data.map((comment) => <CommentItem key={comment.id} comment={comment} />)}</ul>}
        </div>
      )
  }
}

function CategoryItem({ category }: { category: CategoryNode }) {
  return (
    <li>
      <Link to={`/category/${category.id}`}>
        {category.name} <span className="muted num">({category.postCount})</span>
      </Link>
      {category.isPrivate && <span className="chip">비공개</span>}
      {category.children.length > 0 && (
        <ul>{category.children.map((child) => <CategoryItem key={child.id} category={child} />)}</ul>
      )}
    </li>
  )
}

function CommentItem({ comment }: { comment: RecentComment }) {
  const text = comment.state === 'SECRET' ? '비밀댓글입니다'
    : comment.state === 'BLINDED' ? '관리자가 숨긴 댓글입니다'
      : `${comment.content} · ${comment.authorNickname}`
  return (
    <li className="small">
      <Link to={`/${comment.postId}#comment-${comment.id}`}>{text}</Link>
    </li>
  )
}
