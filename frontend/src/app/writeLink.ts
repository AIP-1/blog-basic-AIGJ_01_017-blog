// 글쓰기 버튼이 갈 곳 (AUTH-04). 플랫폼 주소와 블로그 주소 어디서 눌러도 같은 곳으로 가도록 전체 주소를 만든다

import type { Me } from '../api/types'
import { blogUrl, platformUrl } from './host'

/** 블로그 개설 화면에 글쓰기에서 왔다고 알리는 값. 개설 화면이 안내 문구를 띄우고, 만든 뒤 바로 글쓰기로 보낸다. */
export const FROM_WRITE = 'write'

/** 대표 블로그가 있으면 그 블로그의 글쓰기, 없으면 블로그 개설(안내 포함). */
export function writeUrl(me: Pick<Me, 'primaryBlog'>, location: Location = window.location): string {
  if (me.primaryBlog) {
    return blogUrl(me.primaryBlog.address, '/manage/write', location)
  }
  return platformUrl(`/blogs/new?from=${FROM_WRITE}`, location)
}
