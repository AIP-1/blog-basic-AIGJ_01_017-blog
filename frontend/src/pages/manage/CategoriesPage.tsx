import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { CategoryNode, CategoryTree } from '../../api/types'

/**
 * 카테고리 관리 (CAT-01). 추가·이름 변경·삭제. 지우면 그 카테고리의 글은 미분류로 옮겨진다.
 * 하위 카테고리(CAT-03, 스텝 9)는 최상위 카테고리의 [하위 추가]로 만든다. 2단계까지라 하위에는 그 버튼이 없다.
 * 드래그로 순서·상하위 바꾸기(CAT-04)와 태그 관리(TAG-04)는 백로그다.
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
            <div key={category.id} className="stack" style={{ gap: 6 }}>
              <CategoryRow category={category} onChanged={load} onError={setError} />
              {category.children.map((child) => (
                <div key={child.id} style={{ marginLeft: 28 }}>
                  <CategoryRow category={child} child onChanged={load} onError={setError} />
                </div>
              ))}
            </div>
          ))}
        </div>
        <form className="row nowrap" onSubmit={add}>
          <input type="text" value={name} maxLength={30} placeholder="새 카테고리 이름"
                 onChange={(event) => setName(event.target.value)} />
          <button className="btn primary" type="submit" disabled={!name.trim()}>추가</button>
        </form>
        <span className="hint">
          이름은 1~30자. 카테고리를 지우면 그 안의 글은 미분류가 됩니다. 하위 카테고리가 있으면 하위부터 지워 주세요.
        </span>
      </section>
    </main>
  )
}

function CategoryRow({ category, child = false, onChanged, onError }: {
  category: CategoryNode
  /** 하위 카테고리면 true. 2단계까지라 [하위 추가]가 없다 */
  child?: boolean
  onChanged: () => void
  onError: (message: string | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name)
  const [addingChild, setAddingChild] = useState(false)
  const [childName, setChildName] = useState('')

  async function addChild(event: FormEvent) {
    event.preventDefault()
    onError(null)
    try {
      await api('/api/categories', { method: 'POST', body: { name: childName.trim(), parentId: category.id } })
      setChildName('')
      setAddingChild(false)
      onChanged()
    } catch (caught) {
      onError(categoryError(caught))
    }
  }

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
    <>
      <div className="row between box">
        <span>
          {child && <span className="muted">└ </span>}
          {category.name} <span className="muted num small">{category.postCount}</span>
        </span>
        <div className="row">
          {!child && <button className="btn" type="button" onClick={() => setAddingChild(true)}>하위 추가</button>}
          <button className="btn" type="button" onClick={() => setEditing(true)}>이름 변경</button>
          <button className="btn" type="button" onClick={remove}>삭제</button>
        </div>
      </div>
      {addingChild && (
        <form className="row nowrap" style={{ marginLeft: 28 }} onSubmit={addChild}>
          <input type="text" value={childName} maxLength={30} autoFocus placeholder={`${category.name}의 하위 카테고리`}
                 onChange={(event) => setChildName(event.target.value)} />
          <button className="btn primary" type="submit" disabled={!childName.trim()}>추가</button>
          <button className="btn" type="button" onClick={() => { setAddingChild(false); setChildName('') }}>취소</button>
        </form>
      )}
    </>
  )
}

function categoryError(error: unknown): string {
  if (error instanceof ApiError && error.code === 'NAME_TAKEN') {
    return '같은 자리에 같은 이름의 카테고리가 이미 있습니다.'
  }
  if (error instanceof ApiError && error.code === 'CATEGORY_HAS_CHILDREN') {
    return '하위 카테고리가 있어 지울 수 없습니다. 하위부터 지워 주세요.'
  }
  return fieldMessages(error).name ?? errorMessage(error)
}
