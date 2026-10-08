// 에디터의 마크다운 입력 (T035a, POST-01). 화면 컴포넌트와 떨어진 순수 함수라 Vitest로 바로 시험한다.

/** 링크로 넣을 수 있는 주소. 서버 정화(HtmlSanitizer)와 같게 http/https 절대 주소만. */
export function isSafeLinkUrl(url: string): boolean {
  return /^https?:\/\/\S+$/i.test(url)
}

/** `[글자](주소)`를 막 다 친 순간을 찾는다. 닫는 괄호를 칠 때 입력 규칙이 이 식을 본다. */
export const MARKDOWN_LINK = /\[([^\]]+)\]\(([^)\s]+)\)$/

/**
 * 붙여넣은 글이 마크다운인가. 그냥 문장을 마크다운으로 해석하면 `*`나 `_`가 엉뚱하게 서식이 되므로,
 * 마크다운에만 있는 모양(줄 앞의 #, -, 1., >, ```, 그리고 **굵게**, [글자](주소))이 있을 때만 그렇다고 본다.
 */
export function looksLikeMarkdown(text: string): boolean {
  const blockSyntax = /^(#{1,6}\s|[-*+]\s|\d+\.\s|>\s?|```)/m
  const inlineSyntax = /\*\*[^*\n]+\*\*|\[[^\]\n]+\]\(https?:\/\/[^)\s]+\)/
  return blockSyntax.test(text) || inlineSyntax.test(text)
}
