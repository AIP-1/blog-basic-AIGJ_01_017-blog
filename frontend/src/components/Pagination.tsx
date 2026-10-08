import { Link } from 'react-router'
import { pageGroup } from './pageGroup'

/** 페이지 번호 10개 묶음과 이전·다음. href(page)로 각 번호의 주소를 만든다. */
export default function Pagination({ page, totalPages, href }: {
  page: number
  totalPages: number
  href: (page: number) => string
}) {
  const group = pageGroup(page, totalPages)
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav className="pager" aria-label="페이지">
      {group.prev !== null && <Link to={href(group.prev)}>이전</Link>}
      {group.pages.map((number) => (
        <Link key={number} to={href(number)} className={number === page ? 'on' : undefined}
              aria-current={number === page ? 'page' : undefined}>
          {number}
        </Link>
      ))}
      {group.next !== null && <Link to={href(group.next)}>다음</Link>}
    </nav>
  )
}
