import { type KeyboardEvent, useState } from 'react'

const MAX_TAGS = 10
const MAX_LENGTH = 30

/**
 * 태그 입력 (TAG-01). 이름을 쓰고 Enter나 쉼표로 하나씩 단다. 글당 10개, 대소문자만 다른 이름은 하나로 본다.
 * 서버(TagNames)가 같은 규칙으로 다시 정리하므로, 여기서는 사용자가 바로 알아보게 돕는 것뿐이다.
 */
export default function TagInput({ tags, onChange }: { tags: string[]; onChange: (tags: string[]) => void }) {
  const [draft, setDraft] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  function add() {
    const name = draft.trim().replace(/^#+/, '').trim()
    setDraft('')
    if (!name) {
      return
    }
    if (name.length > MAX_LENGTH) {
      setMessage(`태그는 ${MAX_LENGTH}자까지입니다.`)
      return
    }
    if (tags.some((tag) => tag.toLowerCase() === name.toLowerCase())) {
      return
    }
    if (tags.length >= MAX_TAGS) {
      setMessage(`태그는 ${MAX_TAGS}개까지 달 수 있습니다.`)
      return
    }
    setMessage(null)
    onChange([...tags, name])
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // 한글 조합 중의 Enter는 글자를 끝내는 키라 태그로 넣지 않는다
    if (event.nativeEvent.isComposing) {
      return
    }
    if (event.key === 'Enter' || event.key === ',') {
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
        <input type="text" value={draft} maxLength={MAX_LENGTH + 1} placeholder="태그 입력 후 Enter"
               style={{ flex: '1 1 160px', width: 'auto' }}
               onChange={(event) => setDraft(event.target.value)} onKeyDown={onKeyDown} onBlur={add} />
      </div>
      <span className="hint">{message ?? `최대 ${MAX_TAGS}개. Enter나 쉼표로 하나씩 단다.`}</span>
    </div>
  )
}
