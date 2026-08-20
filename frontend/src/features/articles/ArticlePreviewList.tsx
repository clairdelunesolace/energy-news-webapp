import { useEffect, useState } from 'react'
import { getArticles } from '../../api/articles'
import type { ArticleResponse } from '../../types/articles'

type LoadStatus = 'loading' | 'success' | 'error'

export function ArticlePreviewList() {
  const [articles, setArticles] = useState<ArticleResponse[]>([])
  const [status, setStatus] = useState<LoadStatus>('loading')

  useEffect(() => {
    const controller = new AbortController()

    getArticles({ page: 0, size: 5 }, controller.signal)
      .then((page) => {
        setArticles(page.content)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setStatus('error')
      })

    return () => controller.abort()
  }, [])

  if (status === 'loading') {
    return <p className="status-message">正在加载最新资讯…</p>
  }

  if (status === 'error') {
    return <p className="status-message status-message--error">加载失败，请稍后重试。</p>
  }

  if (articles.length === 0) {
    return <p className="status-message">暂时没有资讯。</p>
  }

  return (
    <ul className="article-preview-list">
      {articles.map((article) => {
        const translatedTitle = article.translation?.title
        const primaryTitle = translatedTitle ?? article.original.title

        return (
          <li className="article-preview" key={article.id}>
            <span className="article-preview__source">{article.source.name}</span>
            <a className="article-preview__title" href={article.url} target="_blank" rel="noreferrer">
              {primaryTitle}
            </a>
            {translatedTitle && (
              <p className="article-preview__original" lang="en">
                {article.original.title}
              </p>
            )}
          </li>
        )
      })}
    </ul>
  )
}
