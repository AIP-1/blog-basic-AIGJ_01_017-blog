import type { SearchMode } from '../app/searchMode'

/**
 * "글자로 찾기 | 의미로 찾기" 탭 (OWN-07, T122). 전체 검색과 블로그 안 검색이 같이 쓴다.
 * 의미로 찾기를 고르면 아래에 무엇이 다른지 한 줄로 알려 준다.
 */
export default function SearchModeTabs({ mode, onChange }: { mode: SearchMode; onChange: (mode: SearchMode) => void }) {
  return (
    <>
      <div className="tabs" role="tablist" aria-label="검색 방식">
        <button type="button" role="tab" aria-selected={mode === 'keyword'} className={mode === 'keyword' ? 'on' : undefined}
                onClick={() => onChange('keyword')}>글자로 찾기</button>
        <button type="button" role="tab" aria-selected={mode === 'semantic'} className={mode === 'semantic' ? 'on' : undefined}
                onClick={() => onChange('semantic')}>의미로 찾기</button>
      </div>
      {mode === 'semantic' && (
        <p className="small muted">검색어와 뜻이 가까운 글을 가까운 순으로 보여 줍니다. 글에 그 낱말이 없어도 찾아요.</p>
      )}
    </>
  )
}
