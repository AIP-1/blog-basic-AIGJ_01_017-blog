import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { CategoryNode, CategoryTree } from '../../api/types'

/**
 * 카테고리 관리 (CAT-01). 추가·이름 변경·삭제. 지우면 그 카테고리의 글은 미분류로 옮겨진다.
 * 하위 카테고리(CAT-03)와 순서 바꾸기(CAT-04)는 스텝 9, 태그 관리는 스텝 7 이후에 더한다.
 */
export default function CategoriesPage() {
  const [tree, setTree] = useState<CategoryTree | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<CategoryTree>('/api/categories')
      .then(setTree)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function add(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api('/api/categories', { method: 'POST', body: { name: name.trim() } })
      setName('')
      load()
    } catch (caught) {
      setError(categoryError(caught))
    }
  }

  return (
    <main className="page">
      <section className="section" style={{ maxWidth: 560 }}>
        <h2>카테고리</h2>
        {error && <p className="err" role="alert">{error}</p>}
        {tree && tree.categories.length === 0 && <p className="small muted">아직 카테고리가 없습니다.</p>}
        <div className="stack">
          {tree?.categories.map((category) => (
            <CategoryRow key={category.id} category={category} onChanged={load} onError={setError} />
          ))}
        </div>
        <form className="row nowrap" onSubmit={add}>
          <input type="text" value={name} maxLength={30} placeholder="새 카테고리 이름"
                 onChange={(event) => setName(event.target.value)} />
          <button className="btn primary" type="submit" disabled={!name.trim()}>추가</button>
        </form>
        <span className="hint">이름은 1~30자. 카테고리를 지우면 그 안의 글은 미분류가 됩니다.</span>
      </section>
    </main>
  )
}

function CategoryRow({ category, onChanged, onError }: {
  category: CategoryNode
  onChanged: () => void
  onError: (message: string | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name)

  async function rename(event: FormEvent) {
    event.preventDefault()
    onError(null)
    try {
      await api(`/api/categories/${category.id}`, { method: 'PATCH', body: { name: name.trim() } })
      setEditing(false)
      onChanged()
    } catch (caught) {
      onError(categoryError(caught))
    }
  }

  async function remove() {
    const message = category.postCount > 0
      ? `"${category.name}"을(를) 삭제하면 글 ${category.postCount}개가 미분류로 옮겨집니다. 삭제할까요?`
      : `"${category.name}"을(를) 삭제할까요?`
    if (!window.confirm(message)) {
      return
    }
    onError(null)
    try {
      await api(`/api/categories/${category.id}`, { method: 'DELETE' })
      onChanged()
    } catch (caught) {
      onError(categoryError(caught))
    }
  }

  if (editing) {
    return (
      <form className="row nowrap box" onSubmit={rename}>
        <input type="text" value={name} maxLength={30} autoFocus onChange={(event) => setName(event.target.value)} />
        <button className="btn primary" type="submit" disabled={!name.trim()}>저장</button>
        <button className="btn" type="button" onClick={() => { setEditing(false); setName(category.name) }}>취소</button>
      </form>
    )
  }
  return (
    <div className="row between box">
      <span>{category.name} <span className="muted num small">{category.postCount}</span></span>
      <div className="row">
        <button className="btn" type="button" onClick={() => setEditing(true)}>이름 변경</button>
        <button className="btn" type="button" onClick={remove}>삭제</button>
      </div>
    </div>
  )
}

function categoryError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'NAME_TAKEN') {
    return '같은 이름의 카테고리가 이미 있습니다.'
  }
  return fieldMessages(error).name ?? errorMessage(error)
}
