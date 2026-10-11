import type { ReactNode } from 'react'
import type { BlogCard } from '../api/types'
import { blogUrl } from '../app/host'

/** 인기 블로거·추천 블로그 한 줄: 순위(있으면), 블로그 이름과 주인, 구독자 수. 블로그는 다른 주소라 a로 연다 */
export default function BlogCardItem({ card, showRank = true, children }: {
  card: BlogCard
  showRank?: boolean
  /** 줄 끝에 덧붙일 것(구독 버튼 등) */
  children?: ReactNode
}) {
  const photo = card.blog.profileImageUrl ?? card.owner.profileImageUrl
  return (
    <div className="rank">
      {showRank && <b>{card.rank}</b>}
      {photo ? <img className="avatar" src={photo} alt="" /> : <span className="avatar" />}
      <span className="stack" style={{ gap: 0 }}>
        <a href={blogUrl(card.blog.address)}><b>{card.blog.name}</b></a>
        <span className="small muted">
          {card.owner.nickname} · 구독자 <span className="num">{card.subscriberCount}</span>명
          {card.recentPostTitle && <> · {card.recentPostTitle}</>}
        </span>
      </span>
      {children}
    </div>
  )
}
