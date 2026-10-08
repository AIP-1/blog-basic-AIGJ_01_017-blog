import { type KeyboardEvent, useState } from 'react'
import { MAX_LENGTH, MAX_TAGS, addTags, splitDraft } from './tagNames'

/**
 * 태그 입력 (TAG-01). 이름을 쓰고 Enter나 쉼표로 하나씩 단다. 쉼표가 든 글을 붙여 넣어도 나눠 단다.
 * 글당 10개, 대소문자만 다른 이름은 하나로 본다. 서버(TagNames)가 같은 규칙으로 다시 정리하므로,
 * 여기서는 사용자가 바로 알아보게 돕는 것뿐이다.
 */
export default function TagInput({ tags, onChange }: { tags: string[]; onChange: (tags: string[]) => void }) {
  const [draft, setDraft] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  function commit(names: string[]) {
    const result = addTags(tags, names)
    setMessage(result.message)
    if (result.tags.length !== tags.length) {
      onChange(result.tags)
    }
  }

  function add() {
    setDraft('')
    commit([draft])
  }

  function onDraftChange(value: string) {
    const { done, rest } = splitDraft(value)
    setDraft(rest)
    if (done.length > 0) {
      commit(done)
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // 한글 조합 중의 Enter는 글자를 끝내는 키라 태그로 넣지 않는다
    if (event.nativeEvent.isComposing) {
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      add()
    } else if (event.key === 'Backspace' && draft === '' && tags.length > 0) {
      onChange(tags.slice(0, -1))
    }
  }

  return (
    <div className="stack">
      <div className="row">
        {tags.map((tag) => (
          <span key={tag} className="chip">
            #{tag}
            <button type="button" className="btn ghost small" aria-label={`${tag} 태그 빼기`}
                    onClick={() => onChange(tags.filter((item) => item !== tag))}>×</button>
          </span>
        ))}
        <input type="text" value={draft} maxLength={(MAX_LENGTH + 1) * MAX_TAGS} placeholder="태그 입력 후 Enter"
               style={{ flex: '1 1 160px', width: 'auto' }}
               onChange={(event) => onDraftChange(event.target.value)} onKeyDown={onKeyDown} onBlur={add} />
      </div>
      <span className="hint">{message ?? `최대 ${MAX_TAGS}개. Enter나 쉼표로 하나씩 단다.`}</span>
    </div>
  )
}
