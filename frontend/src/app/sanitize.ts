import DOMPurify from 'dompurify'

/**
 * 글 본문을 화면에 넣기 전 한 번 더 정화한다 (T045, 이중 정화, research R-05).
 * 서버가 저장할 때 이미 허용 목록(HtmlSanitizer)으로 정화했으므로 이것은 보조 장치다.
 * 서버 허용 목록과 같은 태그·속성만 남긴다. 사용자가 쓴 다른 글자(제목, 댓글)는 React가 글자 그대로 넣으므로 정화하지 않는다.
 */
const ALLOWED_TAGS = ['p', 'br', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'strong', 'b', 'em', 'i', 'ul', 'ol', 'li',
  'blockquote', 'pre', 'code', 'a', 'img']
const ALLOWED_ATTR = ['href', 'rel', 'src', 'alt', 'class']

export function sanitizePostHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 링크는 http/https, 이미지는 이 서비스에 올린 파일만 (서버 규칙과 같음)
    ALLOWED_URI_REGEXP: /^(?:https?:\/\/|\/uploads\/)/i,
  })
}
