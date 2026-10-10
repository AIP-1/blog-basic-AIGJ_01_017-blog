import type { AccentColor, Blog } from '../api/types'

/**
 * 블로그 꾸미기 (BLOG-05)를 화면에 입힌다. 포인트 색은 CSS 변수 --brand 하나(와 옅은 바탕 --brand-soft)를 바꿔
 * 버튼·링크·칩이 한꺼번에 바뀌고, 스킨은 <html data-skin="MAGAZINE">에 맞춘 CSS가 글꼴·간격을 바꾼다.
 * Thymeleaf로 치면 레이아웃의 <html th:attr="data-skin=${blog.skin}">와 <style>:root{--brand:…}</style>를 넣는 것이다.
 */
export const ACCENTS: Record<AccentColor, { label: string; brand: string; soft: string }> = {
  BLUE: { label: '파랑', brand: '#2563c9', soft: '#e3ecfa' },
  GREEN: { label: '초록', brand: '#1f7a5c', soft: '#e3f1ea' },
  ORANGE: { label: '주황', brand: '#c2570c', soft: '#fbecdf' },
  PINK: { label: '분홍', brand: '#c0306e', soft: '#f9e3ed' },
  PURPLE: { label: '보라', brand: '#6d4fc4', soft: '#ece6f8' },
  GRAY: { label: '회색', brand: '#4b5563', soft: '#eceef1' },
}

export const SKINS = [
  { code: 'BASIC', label: '기본', description: '깔끔한 기본 모양' },
  { code: 'MAGAZINE', label: '매거진', description: '큰 제목과 넓은 여백' },
  { code: 'NOTE', label: '노트', description: '좁은 폭과 줄 노트 바탕' },
] as const

/** 블로그 화면에 꾸미기 값을 입힌다. null이면 플랫폼 기본으로 되돌린다 */
export function applyBlogTheme(blog: Pick<Blog, 'skin' | 'accentColor'> | null, root: HTMLElement = document.documentElement) {
  if (!blog) {
    delete root.dataset.skin
    root.style.removeProperty('--brand')
    root.style.removeProperty('--brand-soft')
    return
  }
  const accent = ACCENTS[blog.accentColor] ?? ACCENTS.BLUE
  root.dataset.skin = blog.skin
  root.style.setProperty('--brand', accent.brand)
  root.style.setProperty('--brand-soft', accent.soft)
}
