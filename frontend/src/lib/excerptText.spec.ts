import { describe, expect, it } from 'vitest'
import { meaningfulExcerpt, stripHtmlText } from './excerptText'

describe('excerptText', () => {
  it('stripHtmlText removes anchor markup', () => {
    const html = '<a href="https://news.google.com/rss/articles/ABC">OpenAI chip</a>'
    expect(stripHtmlText(html)).toBe('OpenAI chip')
  })

  it('meaningfulExcerpt hides duplicate title', () => {
    const title = 'OpenAI chip news'
    expect(meaningfulExcerpt(title, title, 'Summary')).toBeNull()
  })

  it('meaningfulExcerpt hides google redirect blobs', () => {
    expect(
      meaningfulExcerpt('https://news.google.com/rss/articles/CBMiLong', 'Title', 'Summary'),
    ).toBeNull()
  })

  it('meaningfulExcerpt keeps distinct body', () => {
    expect(meaningfulExcerpt('Title\n\nExtra body detail', 'Title', 'Summary')).toBe(
      'Title Extra body detail',
    )
  })
})
