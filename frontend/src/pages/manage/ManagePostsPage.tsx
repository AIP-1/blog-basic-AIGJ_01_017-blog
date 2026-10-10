import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { CategoryTree, ManagedPostSummary, PageResponse } from '../../api/types'
import { formatDate, formatDateTime } from '../../app/format'
import Pagination from '../../components/Pagination'

const STATUS_LABEL: Record<ManagedPostSummary['status'], string> = {
  PUBLISHED: '발행',
  DRAFT: '임시저장',
  SCHEDULED: '예약',
}

const VISIBILITY_LABEL: Record<ManagedPostSummary['visibility'], string> = {
  PUBLIC: '공개',
  PRIVATE: '비공개',
  SUBSCRIBERS: '구독자',
}

/**
 * 내 글 관리 (MNG-01). 상태·카테고리로 거르고 제목으로 찾아 20개씩 본다. 여러 글을 골라 공개 범위를 바꾸거나 지운다.
 * 거르기 조건과 페이지는 주소(?status=&categoryId=&q=&page=)에 둔다. 새로고침해도, 링크를 공유해도 같은 목록이다.
 * 다른 블로그로 옮기기(BLOG-06)와 구독자 공개(SUB-01)는 뒤 스텝이다.
 */
export default function ManagePostsPage() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? '1')

  const [posts, setPosts] = useState<PageResponse<ManagedPostSummary> | null>(null)
  const [categories, setCategories] = useState<CategoryTree | null>(null)
  const [selected, setSelected] = useState<number[]>([])
  const [visibility, setVisibility] = useState<'PRIVATE' | 'PUBLIC'>('PRIVATE')
  const [query, setQuery] = useState(q)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    const search = new URLSearchParams()
    if (status) search.set('status', status)
    if (categoryId) search.set('categoryId', categoryId)
    if (q) search.set('q', q)
    search.set('page', String(page))
    return api<PageResponse<ManagedPostSummary>>(`/api/manage/posts?${search}`)
  }, [status, categoryId, q, page])

  useEffect(() => {
    let active = true
    load()
      .then((result) => {
        if (active) {
          setPosts(result)
          setSelected([])
          setError(null)
        }
      })
      .catch((caught: unknown) => active && setError(errorMessage(caught)))
    return () => {
      active = false
    }
  }, [load])

  useEffect(() => {
    api<CategoryTree>('/api/categories').then(setCategories).catch(() => setCategories(null))
  }, [])

  /** 거르기 조건을 바꾸면 1페이지부터. 빈 값은 주소에서 뺀다 */
  function filter(name: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set(name, value)
    } else {
      next.delete(name)
    }
    next.delete('page')
    setParams(next)
  }

  function search(event: FormEvent) {
    event.preventDefault()
    filter('q', query.trim())
  }

  function href(number: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(number))
    return `?${next}`
  }

  function toggle(id: number) {
    setSelected((previous) => (previous.includes(id) ? previous.filter((value) => value !== id) : [...previous, id]))
  }

  async function reload(done: string) {
    setMessage(done)
    setError(null)
    const result = await load()
    setPosts(result)
    setSelected([])
  }

  async function applyVisibility() {
    try {
      const result = await api<{ updatedCount: number }>('/api/manage/posts', {
        method: 'PATCH', body: { postIds: selected, visibility },
      })
      await reload(`글 ${result.updatedCount}개를 ${VISIBILITY_LABEL[visibility]}로 바꿨습니다.`)
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  async function remove() {
    if (!window.confirm(`선택한 글 ${selected.length}개를 삭제할까요? 댓글과 공감도 함께 사라집니다.`)) {
      return
    }
    try {
      const result = await api<{ deletedCount: number }>('/api/manage/posts', {
        method: 'DELETE', body: { postIds: selected },
      })
      await reload(`글 ${result.deletedCount}개를 삭제했습니다.`)
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  const pageIds = posts?.content.map((post) => post.id) ?? []
  const allSelected = pageIds.length > 0 && pageIds.every((id) => selected.includes(id))

  return (
    <main className="page">
      <section className="section">
        <h2>글 관리</h2>
        <div className="row">
          <select value={status} style={{ maxWidth: 140 }} aria-label="상태"
                  onChange={(event) => filter('status', event.target.value)}>
            <option value="">전체 상태</option>
            <option value="PUBLISHED">발행</option>
            <option value="DRAFT">임시저장</option>
          </select>
          <select value={categoryId} style={{ maxWidth: 160 }} aria-label="카테고리"
                  onChange={(event) => filter('categoryId', event.target.value)}>
            <option value="">전체 카테고리</option>
            {categories?.categories.flatMap((category) => [
              <option key={category.id} value={category.id}>{category.name}</option>,
              ...category.children.map((child) => (
                <option key={child.id} value={child.id}>{`  └ ${child.name}`}</option>
              )),
            ])}
            <option value="0">미분류</option>
          </select>
          <form className="row nowrap" onSubmit={search} role="search">
            <input type="search" value={query} placeholder="제목 검색" maxLength={100} style={{ maxWidth: 220 }}
                   onChange={(event) => setQuery(event.target.value)} />
            <button className="btn" type="submit">검색</button>
          </form>
        </div>

        <div className="row">
          {/* 휴대폰에서는 표 머리줄(모두 선택 체크박스)이 숨으므로 여기에 둔다 */}
          <label className="row small mobile-only">
            <input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : pageIds)} /> 모두
          </label>
          <span className="small muted">{selected.length}개 선택</span>
          <select value={visibility} style={{ maxWidth: 160 }} aria-label="바꿀 공개 범위"
                  onChange={(event) => setVisibility(event.target.value as 'PRIVATE' | 'PUBLIC')}>
            <option value="PRIVATE">비공개로</option>
            <option value="PUBLIC">공개로</option>
          </select>
          <button className="btn" type="button" disabled={selected.length === 0} onClick={applyVisibility}>적용</button>
          <button className="btn danger" type="button" disabled={selected.length === 0} onClick={remove}>삭제</button>
        </div>
        {error && <p className="err" role="alert">{error}</p>}
        {message && <p className="small" role="status">{message}</p>}

        {posts && posts.content.length === 0
          ? <p className="muted">조건에 맞는 글이 없습니다.</p>
          : (
            <div className="table-wrap">
              <table className="manage-table">
                <thead>
                  <tr>
                    <th>
                      <input type="checkbox" aria-label="이 페이지 모두 선택" checked={allSelected}
                             onChange={() => setSelected(allSelected ? [] : pageIds)} />
                    </th>
                    <th>제목</th><th>상태</th><th>공개</th><th>카테고리</th><th className="num">날짜</th>
                  </tr>
                </thead>
                <tbody>
                  {posts?.content.map((post) => <PostRow key={post.id} post={post} checked={selected.includes(post.id)}
                                                         onToggle={() => toggle(post.id)} />)}
                </tbody>
              </table>
            </div>
          )}
        {posts && <Pagination page={posts.page} totalPages={posts.totalPages} href={href} />}
      </section>
    </main>
  )
}

function PostRow({ post, checked, onToggle }: { post: ManagedPostSummary; checked: boolean; onToggle: () => void }) {
  const date = post.status === 'PUBLISHED' && post.publishedAt ? formatDate(post.publishedAt) : formatDate(post.updatedAt)
  return (
    <tr>
      <td className="pick"><input type="checkbox" aria-label={`${post.title || '제목 없음'} 선택`} checked={checked} onChange={onToggle} /></td>
      <td className="title">
        {post.status === 'PUBLISHED'
          ? <Link to={`/${post.id}`}>{post.title || '(제목 없음)'}</Link>
          : <Link to={`/manage/posts/${post.id}/edit`}>{post.title || '(제목 없음)'}</Link>}
        {post.blinded && <> <span className="chip danger">숨김: {post.blind?.reasonMessage ?? '관리자 조치'}</span></>}
      </td>
      <td data-label="상태">
        <span className={post.status === 'PUBLISHED' ? 'chip brand' : post.status === 'SCHEDULED' ? 'chip warn' : 'chip'}>
          {STATUS_LABEL[post.status]}
          {post.status === 'SCHEDULED' && post.scheduledAt && ` ${formatDateTime(post.scheduledAt)}`}
        </span>
      </td>
      <td data-label="공개">{VISIBILITY_LABEL[post.visibility]}</td>
      <td data-label="카테고리">
        <Link to={`?categoryId=${post.category?.id ?? 0}`} title="이 카테고리 글만 보기">{post.category?.name ?? '미분류'}</Link>
      </td>
      <td className="num" data-label="날짜">{date}</td>
    </tr>
  )
}
