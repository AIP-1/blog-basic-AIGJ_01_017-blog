import { describe, expect, it } from 'vitest'
import { addTags, splitDraft } from './tagNames'

describe('addTags', () => {
  it('#과 공백을 떼고 대소문자만 다른 이름은 하나로 본다', () => {
    expect(addTags(['Java'], ['#java', '  여행 ', '', '#'])).toEqual({ tags: ['Java', '여행'], message: null })
  })

  it('/가 든 이름은 빼고 알린다', () => {
    expect(addTags([], ['a/b', '산'])).toEqual({ tags: ['산'], message: '태그에는 /를 쓸 수 없습니다.' })
  })

  it('악센트만 다른 이름도 하나로 본다', () => {
    expect(addTags(['Café'], ['cafe', '여행'])).toEqual({ tags: ['Café', '여행'], message: null })
  })

  it('10개를 넘으면 더하지 않고 알린다', () => {
    const ten = Array.from({ length: 10 }, (_, i) => `t${i}`)
    expect(addTags(ten, ['더'])).toEqual({ tags: ten, message: '태그는 10개까지 달 수 있습니다.' })
  })

  it('30자를 넘는 이름은 빼고 나머지는 더한다', () => {
    expect(addTags([], ['가'.repeat(31), '산'])).toEqual({ tags: ['산'], message: '태그는 30자까지입니다.' })
  })
})

describe('splitDraft', () => {
  it('붙여 넣은 "바다,산,"은 두 이름을 끝내고 남은 조각은 비어 있다', () => {
    expect(splitDraft('바다,산,')).toEqual({ done: ['바다', '산'], rest: '' })
  })

  it('쉼표가 없으면 아직 쓰는 중이다', () => {
    expect(splitDraft('바다')).toEqual({ done: [], rest: '바다' })
  })
})
