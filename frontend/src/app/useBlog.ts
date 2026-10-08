import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Blog } from '../api/types'

export type BlogState = { status: 'loading' } | { status: 'notFound' } | { status: 'error'; message: string }
  | { status: 'ok'; blog: Blog }

/** 지금 블로그 주소의 블로그 (GET /api/blog). 없거나 볼 수 없으면 notFound다. */
export function useBlog(): [BlogState, (blog: Blog) => void] {
  const [state, setState] = useState<BlogState>({ status: 'loading' })
  useEffect(() => {
    let active = true
    api<Blog>('/api/blog', { allowAnonymous: true })
      .then((blog) => active && setState({ status: 'ok', blog }))
      .catch((error: unknown) => {
        if (!active) {
          return
        }
        setState(error instanceof ApiError && error.status === 404
          ? { status: 'notFound' }
          : { status: 'error', message: error instanceof ApiError ? error.message : '블로그를 불러오지 못했습니다.' })
      })
    return () => {
      active = false
    }
  }, [])
  return [state, (blog) => setState({ status: 'ok', blog })]
}
