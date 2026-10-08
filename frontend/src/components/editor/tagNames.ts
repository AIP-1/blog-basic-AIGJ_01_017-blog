export const MAX_TAGS = 10
export const MAX_LENGTH = 30

/**
 * 입력한 이름들을 태그 목록에 더한다 (TAG-01). 앞의 #과 공백을 떼고, 빈 이름·대소문자만 다른 이름은 건너뛴다.
 * 넘치거나 너무 긴 이름이 있으면 그 이름은 빼고 안내 문구를 돌려준다. 서버(TagNames)도 같은 규칙으로 다시 정리한다.
 */
export function addTags(tags: string[], rawNames: string[]): { tags: string[]; message: string | null } {
  const result = [...tags]
  let message: string | null = null
  for (const raw of rawNames) {
    const name = raw.trim().replace(/^#+/, '').trim()
    if (!name || result.some((tag) => tag.toLowerCase() === name.toLowerCase())) {
      continue
    }
    if (name.length > MAX_LENGTH) {
      message = `태그는 ${MAX_LENGTH}자까지입니다.`
    } else if (result.length >= MAX_TAGS) {
      message = `태그는 ${MAX_TAGS}개까지 달 수 있습니다.`
    } else {
      result.push(name)
    }
  }
  return { tags: result, message }
}

/** 쉼표가 들어온 입력(붙여 넣기 포함)을 다 쓴 이름들과 아직 쓰는 중인 마지막 조각으로 나눈다. */
export function splitDraft(value: string): { done: string[]; rest: string } {
  const parts = value.split(',')
  return { done: parts.slice(0, -1), rest: parts[parts.length - 1] }
}
