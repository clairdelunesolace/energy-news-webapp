import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import type { ArticleResponse } from '../src/types/articles.ts'

// Use the existing Vite transformer to test the real TSX card without another test dependency.
const vite = await createServer({
  configFile: false,
  envDir: false,
  server: { middlewareMode: true, watch: null, hmr: false },
})
after(() => vite.close())
const { ArticleListItem } = await vite.ssrLoadModule('/src/features/articles/ArticleListItem.tsx')

function renderArticle(tags: string[], translation: ArticleResponse['translation'] = null) {
  const article: ArticleResponse = {
    id: 41,
    source: { id: 7, name: 'Publisher' },
    url: 'https://example.com/articles/41',
    publishedAt: '2026-09-01T00:00:00Z',
    collectedAt: '2026-09-01T01:00:00Z',
    original: {
      language: 'EN',
      title: 'Microgrid deployment',
      description: 'Battery storage summary',
      content: null,
    },
    translation,
    tags,
    createdAt: '2026-09-01T01:00:00Z',
    updatedAt: '2026-09-01T01:00:00Z',
  }
  return renderToStaticMarkup(
    createElement(MemoryRouter, null, createElement(ArticleListItem, { article })),
  )
}

test('renders one configured keyword chip between title and summary', () => {
  const html = renderArticle(['microgrid'])
  assert.match(html, /<ul class="article-list-item__tags" aria-label="匹配关键词"><li>microgrid<\/li><\/ul>/)
  assert.ok(html.indexOf('</h2>') < html.indexOf('article-list-item__tags'))
  assert.ok(html.indexOf('article-list-item__tags') < html.indexOf('article-list-item__description'))
})

test('renders multiple keyword labels in the API order without changing their text', () => {
  const html = renderArticle(['battery storage', 'BESS', 'microgrid'])
  assert.match(html, /<li>battery storage<\/li><li>BESS<\/li><li>microgrid<\/li>/)
})

test('empty tags render no tag container or placeholder while keeping the article', () => {
  const html = renderArticle([])
  assert.doesNotMatch(html, /article-list-item__tags|匹配关键词/)
  assert.match(html, /Microgrid deployment/)
  assert.match(html, /Battery storage summary/)
})

test('preserves translated and original titles, summary, source, time and navigation', () => {
  const html = renderArticle(['microgrid'], {
    language: 'ZH_CN', title: '微电网部署', description: '电池储能摘要', content: null,
  })
  assert.match(html, /href="\/articles\/41"[^>]*>微电网部署<\/a>/)
  assert.match(html, /电池储能摘要/)
  assert.match(html, /lang="en">Microgrid deployment<\/p>/)
  assert.match(html, /<span>Publisher<\/span>/)
  assert.match(html, /<time dateTime="2026-09-01T00:00:00Z">/)
  assert.match(html, /href="https:\/\/example.com\/articles\/41" target="_blank" rel="noreferrer"/)
  assert.match(html, /查看原文/)
})

test('escapes configured keyword text instead of treating it as HTML', () => {
  const html = renderArticle(['<script>alert(1)</script>'])
  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
  assert.doesNotMatch(html, /<script>/)
})
