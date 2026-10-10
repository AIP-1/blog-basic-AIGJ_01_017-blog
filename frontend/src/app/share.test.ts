import { describe, expect, it } from 'vitest'
import { shareLinks } from './share'

describe('shareLinks', () => {
  it('글 주소와 제목을 각 서비스의 공유 주소에 인코딩해 넣는다', () => {
    const [x, facebook] = shareLinks('http://alpha.blog.test/12', '스프링 & JPA')
    expect(x.href).toBe('https://twitter.com/intent/tweet?url=http%3A%2F%2Falpha.blog.test%2F12&text=%EC%8A%A4%ED%94%84%EB%A7%81%20%26%20JPA')
    expect(facebook.href).toBe('https://www.facebook.com/sharer/sharer.php?u=http%3A%2F%2Falpha.blog.test%2F12')
  })
})
