// 대표 이미지 (POST-07): 본문에 든 이미지 중에서 고른다. 고르지 않으면(null) 서버가 본문 첫 이미지를 쓴다

import type { UploadedImage } from '../../api/types'

const IMAGE_SRC = /<img[^>]*\ssrc="(\/uploads\/[^"]+)"/g

/** 본문에 든 이미지 주소, 본문 순서대로(같은 주소는 한 번). 서버(PostThumbnails)와 같은 규칙이다. */
export function bodyImageUrls(html: string): string[] {
  return [...new Set(Array.from(html.matchAll(IMAGE_SRC), (match) => match[1]))]
}

/**
 * 대표 이미지 후보: 본문에 든 이미지 가운데 번호(id)를 아는 것. 번호는 수정 화면을 열 때 서버가 준 목록과
 * 이 화면에서 올린 이미지에서 안다. 본문에서 지운 이미지는 후보에서 빠진다.
 */
export function thumbnailChoices(html: string, known: Record<string, UploadedImage>): UploadedImage[] {
  return bodyImageUrls(html).flatMap((url) => (known[url] ? [known[url]] : []))
}

/** 고른 이미지가 아직 후보(본문)에 있으면 그 번호, 본문에서 지웠으면 null(첫 이미지로 돌아감). */
export function effectiveThumbnail(selectedId: number | null, choices: UploadedImage[]): number | null {
  return selectedId !== null && choices.some((image) => image.id === selectedId) ? selectedId : null
}
