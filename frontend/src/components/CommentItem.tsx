import { type FormEvent, type ReactNode, useState } from 'react'
import { errorMessage, fieldMessages } from '../api/errors'
import type { Comment } from '../api/types'
import { formatDateTime } from '../app/format'
import AuthorName from './AuthorName'

const MAX_LENGTH = 1000

/** 댓글과 방명록은 같은 모양으로 그리고, 가려진 자리의 문구만 다르다 */
export type CommentKind = 'comment' | 'guestbook'

const HIDDEN_LABEL: Record<CommentKind, Record<'SECRET' | 'BLINDED' | 'DELETED', string>> = {
  comment: { SECRET: '비밀댓글입니다.', BLINDED: '관리자가 숨긴 댓글입니다.', DELETED: '삭제된 댓글입니다.' },
  guestbook: { SECRET: '비밀글입니다.', BLINDED: '관리자가 숨긴 글입니다.', DELETED: '삭제된 글입니다.' },
}

/**
 * 댓글·방명록 한 개 (CMT-01~05, CMT-04). 글 상세, 방명록, 관리 화면이 같이 쓴다.
 * 비밀글은 서버가 내용을 빼고 SECRET으로 주므로 화면은 문구만 바꾼다. 작성자 본인에게는 "수정"(viewer.canEdit),
 * 본인·블로그 주인에게는 "삭제"(viewer.canDelete)가 보인다. 권한은 서버가 다시 검사한다.
 */
export default function CommentItem({ comment, kind = 'comment', onDelete, onReply, onEdit, isReply = false, extra }: {
  comment: Comment
  kind?: CommentKind
  onDelete: (comment: Comment) => void
  onReply?: () => void
  /** 고친 내용을 서버에 보내고 고친 댓글을 돌려준다. 실패하면 오류를 던진다 */
  onEdit?: (comment: Comment, content: string) => Promise<void>
  isReply?: boolean
  /** 작성자 줄에 덧붙일 것(관리 화면의 글 제목 등) */
  extra?: ReactNode
}) {
  const [editing, setEditing] = useState(false)
  const hidden = comment.state === 'NORMAL' ? null : HIDDEN_LABEL[kind][comment.state]
  return (
    <div className={isReply ? 'comment reply' : 'comment'} id={`${kind}-${comment.id}`}>
      {!hidden && comment.author?.profileImageUrl
        ? <img className="avatar" src={comment.author.profileImageUrl} alt="" />
        : <span className="avatar" />}
      <div>
        {hidden
          ? <p className="gone small">{hidden}</p>
          : (
            <>
              <div className="row small">
                {comment.author && <AuthorName author={comment.author} bold />}
                {comment.secret && <span className="chip">비밀</span>}
                <span className="muted num">{formatDateTime(comment.createdAt)}</span>
                {comment.updatedAt && <span className="muted small" title={formatDateTime(comment.updatedAt)}>수정됨</span>}
                {extra}
              </div>
              {comment.blind && <p className="err">관리자가 숨긴 댓글입니다. 사유: {comment.blind.reasonMessage}</p>}
              {editing && onEdit
                ? <EditForm comment={comment} onSave={onEdit} onClose={() => setEditing(false)} />
                : <p style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{comment.content}</p>}
            </>
          )}
        {!editing && (
          <div className="row small">
            {onReply && <button className="btn ghost small" type="button" onClick={onReply}>답글</button>}
            {onEdit && comment.viewer.canEdit && (
              <button className="btn ghost small" type="button" onClick={() => setEditing(true)}>수정</button>
            )}
            {comment.viewer.canDelete && (
              <button className="btn ghost small" type="button" onClick={() => onDelete(comment)}>삭제</button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/** 그 자리에서 고치기 (CMT-03). 취소하면 원래 내용으로 돌아간다 */
function EditForm({ comment, onSave, onClose }: {
  comment: Comment
  onSave: (comment: Comment, content: string) => Promise<void>
  onClose: () => void
}) {
  const [content, setContent] = useState(comment.content ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onSave(comment, content.trim())
      onClose()
    } catch (caught) {
      setError(fieldMessages(caught).content ?? errorMessage(caught))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <textarea value={content} maxLength={MAX_LENGTH} aria-label="고칠 내용"
                onChange={(event) => setContent(event.target.value)} />
      {error && <p className="err" role="alert">{error}</p>}
      <div className="row between">
        <span className="small muted num">{content.length}/{MAX_LENGTH}</span>
        <div className="row">
          <button className="btn" type="button" onClick={onClose}>취소</button>
          <button className="btn primary" type="submit"
                  disabled={saving || !content.trim() || content.trim() === comment.content}>저장</button>
        </div>
      </div>
    </form>
  )
}
