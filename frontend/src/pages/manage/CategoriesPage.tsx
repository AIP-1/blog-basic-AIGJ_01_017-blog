import { type DragEvent, type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { CategoryNode, CategoryTree, TagCount } from '../../api/types'
import { type OrderRow, flatten, group, moveBefore, moveInto, moveToRootEnd, toOrderItems } from './categoryOrder'

/**
 * 카테고리·태그 관리 (CAT-01, CAT-03, CAT-04, CAT-05, TAG-03, TAG-04, 목업 manage-categories).
 * 카테고리: 추가·이름 변경·삭제(글은 미분류로), 하위 추가(2단계), 비공개 켜기·끄기, 끌어서 놓아 순서·상하위 바꾸기.
 * 끌어서 놓은 결과는 화면에서만 바뀌고 "순서 저장"을 눌러야 한 번에 저장된다(PUT /api/categories/order, 목업 5번).
 * 태그: 글 수(주인이라 비공개 글도 셈), 이름 변경, 삭제(글은 남는다).
 */
export default function CategoriesPage() {
  const [tree, setTree] = useState<CategoryTree | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  /** 끌어서 바꾼, 아직 저장하지 않은 순서. null이면 서버 순서 그대로 */
  const [draft, setDraft] = useState<OrderRow[] | null>(null)
  const [dragging, setDragging] = useState<number | null>(null)

  const load = useCallback(() => {
    api<CategoryTree>('/api/categories')
      .then((loaded) => {
        setTree(loaded)
        setDraft(null)
      })
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

  async function saveOrder() {
    if (!draft) {
      return
    }
    setError(null)
    try {
      await api('/api/categories/order', { method: 'PUT', body: toOrderItems(draft) })
      setMessage('순서를 저장했습니다.')
      load()
    } catch (caught) {
      setError(categoryError(caught))
    }
  }

  if (!tree) {
    return <main className="page">{error && <p className="err" role="alert">{error}</p>}</main>
  }

  const nodes = new Map<number, CategoryNode>()
  tree.categories.forEach((category) => {
    nodes.set(category.id, category)
    category.children.forEach((child) => nodes.set(child.id, child))
  })
  const rows = draft ?? flatten(tree.categories)
  /** 끌던 것을 놓을 때 할 일. 바뀐 것이 있으면 저장 전 순서로 둔다 */
  const drop = (change: (current: OrderRow[], dragId: number) => OrderRow[]) => {
    if (dragging === null) {
      return
    }
    const next = change(rows, dragging)
    if (next !== rows) {
      setDraft(next)
      setMessage(null)
    }
    setDragging(null)
  }
  const dragProps = (id: number) => ({
    draggable: true,
    onDragStart: (event: DragEvent) => {
      event.dataTransfer.effectAllowed = 'move'
      setDragging(id)
    },
    onDragEnd: () => setDragging(null),
    onDragOver: (event: DragEvent) => event.preventDefault(),
    onDrop: (event: DragEvent) => {
      event.preventDefault()
      drop((current, dragId) => moveBefore(current, dragId, id))
    },
  })

  return (
    <main className="page">
      <section className="section" style={{ maxWidth: 560 }}>
        <h2>카테고리</h2>
        {error && <p className="err" role="alert">{error}</p>}
        {message && <p className="ok" role="status">{message}</p>}
        {tree.categories.length === 0 && <p className="small muted">아직 카테고리가 없습니다.</p>}
        {tree.categories.length > 1 && (
          <span className="hint">⋮⋮를 잡고 끌어서 다른 카테고리 위에 놓으면 그 위로, "하위로 넣기"에 놓으면 하위가 됩니다.</span>
        )}
        <div className="stack">
          {group(rows).map((root) => {
            const category = nodes.get(root.id)
            if (!category) {
              return null
            }
            return (
              <div key={root.id} className="stack" style={{ gap: 6 }}>
                <div {...dragProps(root.id)}>
                  <CategoryRow category={category} onChanged={load} onError={setError} dirty={draft !== null} />
                </div>
                {root.children.map((childId) => {
                  const child = nodes.get(childId)
                  return child && (
                    <div key={childId} style={{ marginLeft: 28 }} {...dragProps(childId)}>
                      <CategoryRow category={child} child onChanged={load} onError={setError} dirty={draft !== null}
                                   hiddenByParent={category.isPrivate === true} />
                    </div>
                  )
                })}
                {dragging !== null && dragging !== root.id && (
                  <div className="box small muted" style={{ marginLeft: 28, borderStyle: 'dashed' }}
                       onDragOver={(event) => event.preventDefault()}
                       onDrop={(event) => {
                         event.preventDefault()
                         drop((current, dragId) => moveInto(current, dragId, root.id))
                       }}>
                    여기에 놓으면 "{category.name}"의 하위로 넣기
                  </div>
                )}
              </div>
            )
          })}
          {dragging !== null && (
            <div className="box small muted" style={{ borderStyle: 'dashed' }}
                 onDragOver={(event) => event.preventDefault()}
                 onDrop={(event) => {
                   event.preventDefault()
                   drop(moveToRootEnd)
                 }}>
              여기에 놓으면 최상위 맨 아래로
            </div>
          )}
        </div>
        {draft && (
          <div className="row">
            <button className="btn primary" type="button" onClick={saveOrder}>순서 저장</button>
            <button className="btn" type="button" onClick={() => setDraft(null)}>되돌리기</button>
            <span className="small muted">아직 저장하지 않은 순서입니다.</span>
          </div>
        )}
        <form className="row nowrap" onSubmit={add}>
          <input type="text" value={name} maxLength={30} placeholder="새 카테고리 이름"
                 onChange={(event) => setName(event.target.value)} />
          <button className="btn primary" type="submit" disabled={!name.trim()}>추가</button>
        </form>
        <span className="hint">
          이름은 1~30자. 카테고리를 지우면 그 안의 글은 미분류가 됩니다. 하위 카테고리가 있으면 하위부터 지워 주세요.
          비공개 카테고리와 그 하위의 글은 나만 봅니다.
        </span>
      </section>
      <TagSection />
    </main>
  )
}

/** 태그와 글 수 (TAG-03), 이름 변경·삭제 (TAG-04). 이름을 누르면 블로그의 태그별 글 목록이다. */
function TagSection() {
  const [tags, setTags] = useState<TagCount[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<number | null>(null)
  const [name, setName] = useState('')

  const load = useCallback(() => {
    api<TagCount[]>('/api/tags')
      .then(setTags)
      .catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function rename(event: FormEvent, tag: TagCount) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/tags/${tag.id}`, { method: 'PATCH', body: { name: name.trim() } })
      setEditing(null)
      load()
    } catch (caught) {
      setError(caught instanceof ApiError && caught.code === 'NAME_TAKEN'
        ? '같은 이름의 태그가 이미 있습니다.' : fieldMessages(caught).name ?? errorMessage(caught))
    }
  }

  async function remove(tag: TagCount) {
    if (!window.confirm(`태그 "${tag.name}"을(를) 지울까요? 글 ${tag.postCount}개에서 이 태그만 빠지고 글은 남습니다.`)) {
      return
    }
    setError(null)
    try {
      await api(`/api/tags/${tag.id}`, { method: 'DELETE' })
      load()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  return (
    <section className="section" style={{ maxWidth: 560 }}>
      <h2>태그</h2>
      {error && <p className="err" role="alert">{error}</p>}
      {tags && tags.length === 0 && <p className="small muted">아직 태그가 없습니다. 글쓰기에서 태그를 달면 여기에 보입니다.</p>}
      {tags && tags.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>태그</th><th className="num">글 수</th><th /></tr>
            </thead>
            <tbody>
              {tags.map((tag) => (
                <tr key={tag.id}>
                  <td>
                    {editing === tag.id
                      ? (
                        <form className="row nowrap" onSubmit={(event) => rename(event, tag)}>
                          <input type="text" value={name} maxLength={30} autoFocus aria-label="새 태그 이름"
                                 onChange={(event) => setName(event.target.value)} />
                          <button className="btn primary small" type="submit" disabled={!name.trim()}>저장</button>
                          <button className="btn small" type="button" onClick={() => setEditing(null)}>취소</button>
                        </form>
                      )
                      : <Link to={`/tag/${encodeURIComponent(tag.name)}`}>{tag.name}</Link>}
                  </td>
                  <td className="num">{tag.postCount}</td>
                  <td>
                    {editing !== tag.id && (
                      <span className="row nowrap">
                        <button className="btn ghost small" type="button"
                                onClick={() => { setEditing(tag.id); setName(tag.name) }}>이름 변경</button>
                        <button className="btn ghost small" type="button" onClick={() => remove(tag)}>삭제</button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function CategoryRow({ category, child = false, dirty, hiddenByParent = false, onChanged, onError }: {
  category: CategoryNode
  /** 하위 카테고리면 true. 2단계까지라 [하위 추가]가 없다 */
  child?: boolean
  /** 끌어서 바꾼 순서를 아직 저장하지 않았나. 그동안은 하위 추가를 막는다(저장 전 순서가 섞이지 않게) */
  dirty: boolean
  /** 상위가 비공개라 이 카테고리도 숨는가 */
  hiddenByParent?: boolean
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

  /** 비공개 켜기·끄기 (CAT-05) */
  async function togglePrivate() {
    onError(null)
    try {
      await api(`/api/categories/${category.id}`, { method: 'PATCH', body: { isPrivate: !category.isPrivate } })
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
      <div className="row between box" style={{ cursor: 'grab' }}>
        <span>
          <span className="muted" aria-hidden="true">⋮⋮ </span>
          {child && <span className="muted">└ </span>}
          {category.name} <span className="muted num small">{category.postCount}</span>
          {category.isPrivate && <> <span className="chip">비공개</span></>}
          {!category.isPrivate && hiddenByParent && <> <span className="chip">상위 비공개</span></>}
        </span>
        <div className="row">
          {!child && <button className="btn" type="button" disabled={dirty} onClick={() => setAddingChild(true)}>하위 추가</button>}
          <button className="btn" type="button" onClick={togglePrivate}>{category.isPrivate ? '공개로' : '비공개로'}</button>
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
  if (error instanceof ApiError && error.code === 'CATEGORY_DEPTH') {
    return '카테고리는 2단계까지입니다. 하위가 있는 카테고리는 다른 카테고리의 하위로 넣을 수 없습니다.'
  }
  return fieldMessages(error).name ?? fieldMessages(error).order ?? errorMessage(error)
}
