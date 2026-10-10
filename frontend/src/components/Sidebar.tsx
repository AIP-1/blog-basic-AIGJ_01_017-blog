import { useState } from 'react'
import { Link } from 'react-router'
import type { CategoryNode, RecentComment, SidebarModule } from '../api/types'
import type { MeState } from '../app/useMe'
import SubscribeButton from './SubscribeButton'

/**
 * 사이드바 (BLOG-04, BLOG-05). 서버가 준 모듈을 받은 순서대로 그린다(순서와 보일 모듈은 주인이 블로그 설정에서 정한다).
 * 사용자가 쓴 글자(이름, 제목, 댓글)는 React가 글자 그대로 넣는다(HTML로 해석하지 않음).
 * 구독 모듈의 버튼은 로그인 상태(me)가 있어야 그리고, 블로그 주인에게는 그리지 않는다(자기 블로그는 구독할 수 없다).
 */
export default function Sidebar({ modules, me, isOwner = false }: {
  modules: SidebarModule[]
  me?: MeState
  isOwner?: boolean
}) {
  return (
    <aside className="sidebar" aria-label="사이드바">
      {modules.map((module) => <SidebarItem key={module.type} module={module} me={isOwner ? undefined : me} />)}
    </aside>
  )
}

function SidebarItem({ module, me }: { module: SidebarModule; me?: MeState }) {
  switch (module.type) {
    case 'PROFILE':
      return (
        <div className="box">
          <div className="row nowrap">
            {module.data.profileImageUrl
              ? <img className="avatar lg" src={module.data.profileImageUrl} alt="" />
              : <span className="avatar lg" />}
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
    case 'TAG':
      return (
        <div>
          <h4>태그</h4>
          {module.data.length === 0
            ? <p className="small muted">아직 태그가 없습니다.</p>
            : (
              <div className="tag-cloud">
                {module.data.map((tag) => (
                  <Link key={tag.id} className="chip" to={`/tag/${encodeURIComponent(tag.name)}`}>
                    {tag.name} <span className="num">{tag.postCount}</span>
                  </Link>
                ))}
              </div>
            )}
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
    case 'VISITOR':
      return (
        <div>
          <h4>방문자</h4>
          <div className="row small num" style={{ gap: 12 }}>
            <span>오늘 <b>{module.data.today.toLocaleString()}</b></span>
            <span>어제 <b>{module.data.yesterday.toLocaleString()}</b></span>
            <span>누적 <b>{module.data.total.toLocaleString()}</b></span>
          </div>
        </div>
      )
    case 'POPULAR_POST':
      return (
        <div>
          <h4>인기 글</h4>
          {module.data.length === 0
            ? <p className="small muted">아직 글이 없습니다.</p>
            : (
              <ol className="small">
                {module.data.map((post) => (
                  <li key={post.id}>
                    <Link to={`/${post.id}`}>{post.title}</Link> <span className="muted num">{post.viewCount.toLocaleString()}</span>
                  </li>
                ))}
              </ol>
            )}
        </div>
      )
    case 'SUBSCRIBE':
      return <SubscribeModule data={module.data} me={me} />
  }
}

/** 구독 모듈: 구독자 수와 구독 버튼. 누르면 수가 바로 바뀐다(블로그 프로필 상자의 버튼과 같은 SubscribeButton) */
function SubscribeModule({ data, me }: {
  data: { blogId: number; subscriberCount: number; subscribed: boolean }
  me?: MeState
}) {
  const [state, setState] = useState(data)
  return (
    <div>
      <h4>구독</h4>
      <div className="row between">
        <span className="small">구독자 <b className="num">{state.subscriberCount}</b>명</span>
        {me && (
          <SubscribeButton blogId={state.blogId} me={me} subscribed={state.subscribed}
                           onChange={(subscribed, subscriberCount) => setState({ ...state, subscribed, subscriberCount })} />
        )}
      </div>
    </div>
  )
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
