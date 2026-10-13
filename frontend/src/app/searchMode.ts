/**
 * 검색 방식 (OWN-07, T122). 글자로 찾기(keyword)는 낱말이 들어간 글을 최신순으로,
 * 의미로 찾기(semantic)는 검색어와 뜻이 가까운 글을 가까운 순으로 찾는다. 주소의 ?mode=에 둔다.
 */
export type SearchMode = 'keyword' | 'semantic'

/** 주소의 mode 값. semantic이 아니면 글자로 찾기 */
export function searchMode(value: string | null): SearchMode {
  return value === 'semantic' ? 'semantic' : 'keyword'
}

/** 결과 주소의 쿼리. 글자로 찾기는 mode를 붙이지 않아 예전 주소와 같다 */
export function searchQuery(base: Record<string, string>, mode: SearchMode): URLSearchParams {
  const query = new URLSearchParams(base)
  if (mode === 'semantic') {
    query.set('mode', 'semantic')
  }
  return query
}

/** 가까움 점수: 서버의 코사인 유사도(0~1, 소수 둘째 자리)를 그대로 "0.82"처럼 */
export function similarityText(similarity: number): string {
  return similarity.toFixed(2)
}
