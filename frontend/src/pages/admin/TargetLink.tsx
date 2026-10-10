import { Link } from 'react-router'
import type { ModerationTarget } from '../../api/types'
import { targetHref, targetTypeLabel } from '../../app/admin'

/** 관리 화면의 대상 이름. 블로그 쪽은 다른 주소라 a, 회원 상세는 같은 화면 안이라 Link. 지워졌으면 글자만 */
export default function TargetLink({ target, showType = true }: { target: ModerationTarget; showType?: boolean }) {
  const link = targetHref(target)
  const name = target.label ?? `${targetTypeLabel(target.type)} ${target.id}`
  const text = showType ? `${targetTypeLabel(target.type)} · ${name}` : name
  return (
    <span className="row nowrap small">
      {link === null
        ? <span className="muted">{text}{target.exists ? '' : ' (삭제됨)'}</span>
        : link.external
          ? <a href={link.href} target="_blank" rel="noreferrer">{text}</a>
          : <Link to={link.href}>{text}</Link>}
      {target.sanctioned && <span className="chip danger">조치 중</span>}
    </span>
  )
}
