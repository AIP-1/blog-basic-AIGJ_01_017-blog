import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Blog } from '../api/types'
import { applyBlogTheme } from './blogTheme'

export type BlogState = { status: 'loading' } | { status: 'notFound' } | { status: 'error'; message: string }
  | { status: 'ok'; blog: Blog }

/**
 * 지금 블로그 주소의 블로그 (GET /api/blog). 없거나 볼 수 없으면 notFound다.
 * 받으면 블로그 꾸미기(스킨·포인트 색, BLOG-05)를 화면에 입힌다. 블로그 주소의 모든 화면이 이 훅을 쓴다.
 */
export function useBlog(): [BlogState, (blog: Blog) => void] {
  const [state, setState] = useState<BlogState>({ status: 'loading' })
  useEffect(() => {
    let active = true
    api<Blog>('/api/blog', { allowAnonymous: true })
      .then((blog) => {
        if (active) {
          applyBlogTheme(blog)
          setState({ status: 'ok', blog })
        }
      })
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
  return [state, (blog) => {
    applyBlogTheme(blog)
    setState({ status: 'ok', blog })
  }]
}
