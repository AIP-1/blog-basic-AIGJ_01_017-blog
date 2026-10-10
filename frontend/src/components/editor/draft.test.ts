import { describe, expect, it } from 'vitest'
import { type AutoSaveState, autoSaveNeeded, isEmptyBody } from './draft'

const base: AutoSaveState = {
  status: 'NEW', busy: false, blinded: false, title: '', html: '<p>쓰는 중</p>', snapshot: 'b', lastSaved: 'a',
}

describe('isEmptyBody', () => {
  it('에디터의 빈 문단과 공백은 빈 본문이다', () => {
    expect(isEmptyBody('<p></p>')).toBe(true)
    expect(isEmptyBody('<p>&nbsp; </p>')).toBe(true)
  })

  it('글자나 이미지가 있으면 빈 본문이 아니다', () => {
    expect(isEmptyBody('<p>a</p>')).toBe(false)
    expect(isEmptyBody('<p><img src="/uploads/a.png"></p>')).toBe(false)
  })
})

describe('autoSaveNeeded (POST-08)', () => {
  it('새 글에 무언가 썼고 바뀌었으면 저장한다', () => {
    expect(autoSaveNeeded(base)).toBe(true)
    expect(autoSaveNeeded({ ...base, html: '<p></p>', title: '제목만' })).toBe(true)
  })

  it('새 글인데 아무것도 안 썼으면 저장하지 않는다', () => {
    expect(autoSaveNeeded({ ...base, html: '<p></p>', title: '  ' })).toBe(false)
  })

  it('마지막 저장과 같으면 저장하지 않는다', () => {
    expect(autoSaveNeeded({ ...base, status: 'DRAFT', lastSaved: 'b' })).toBe(false)
  })

  it('임시저장 글은 내용을 다 지워도 바뀐 것이면 저장한다', () => {
    expect(autoSaveNeeded({ ...base, status: 'DRAFT', html: '<p></p>' })).toBe(true)
  })

  it('발행한 글, 불러오는 중, 저장 중, 숨긴 글은 자동 저장하지 않는다', () => {
    expect(autoSaveNeeded({ ...base, status: 'PUBLISHED' })).toBe(false)
    expect(autoSaveNeeded({ ...base, status: 'LOADING' })).toBe(false)
    expect(autoSaveNeeded({ ...base, busy: true })).toBe(false)
    expect(autoSaveNeeded({ ...base, status: 'DRAFT', blinded: true })).toBe(false)
  })
})
