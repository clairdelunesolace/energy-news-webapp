import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getArticle } from '../api/articles'
import { ApiError } from '../api/client'
import { formatArticleDate } from '../features/articles/formatArticleDate'
import type { ArticleResponse } from '../types/articles'

type LoadStatus = 'loading' | 'success' | 'not-found' | 'error'

export function ArticleDetailPage() {
  const { id } = useParams<{ id: string }>()
  const articleId = Number(id)
  const isValidArticleId = Number.isSafeInteger(articleId) && articleId > 0
  const [article, setArticle] = useState<ArticleResponse | null>(null)
  const [status, setStatus] = useState<LoadStatus>('loading')

  useEffect(() => {
    setArticle(null)

    if (!isValidArticleId) {
      setStatus('not-found')
      return
    }

    const controller = new AbortController()
    setStatus('loading')

    getArticle(articleId, controller.signal)
      .then((response) => {
        setArticle(response)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setStatus(error instanceof ApiError && error.status === 404 ? 'not-found' : 'error')
      })

    return () => controller.abort()
  }, [articleId, isValidArticleId])

  return (
    <main className="page-shell">
      <div className="article-detail-page">
        <Link className="article-detail__back" to="/">
          ← 返回资讯列表
        </Link>

        <div className="article-detail__state" aria-live="polite" aria-busy={status === 'loading'}>
          {status === 'loading' && <p className="status-message">正在加载资讯…</p>}
          {status === 'not-found' && <p className="status-message">未找到这篇资讯。</p>}
          {status === 'error' && (
            <p className="status-message status-message--error">资讯加载失败，请稍后重试。</p>
          )}
          {status === 'success' && article && <ArticleDetail article={article} />}
        </div>
      </div>
    </main>
  )
}

function ArticleDetail({ article }: { article: ArticleResponse }) {
  const hasChineseTranslation =
    article.original.language === 'EN' && article.translation !== null
  const translatedTitle = article.translation?.title?.trim() || null
  const translatedDescription = article.translation?.description?.trim() || null
  const translatedContent = article.translation?.content?.trim() || null
  const originalDescription = article.original.description?.trim() || null
  const originalContent = article.original.content?.trim() || null
  const primaryTitle =
    hasChineseTranslation && translatedTitle ? translatedTitle : article.original.title
  const primaryDescription = hasChineseTranslation
    ? translatedDescription
    : originalDescription
  const primaryContent = hasChineseTranslation ? translatedContent : originalContent
  const originalLanguage = article.original.language === 'ZH_CN' ? 'zh-CN' : 'en'
  const primaryTitleLanguage =
    hasChineseTranslation && translatedTitle ? 'zh-CN' : originalLanguage
  const primaryDescriptionLanguage = hasChineseTranslation ? 'zh-CN' : originalLanguage
  const primaryContentLanguage = hasChineseTranslation ? 'zh-CN' : originalLanguage
  const timestamp = article.publishedAt ?? article.collectedAt

  return (
    <article className="article-detail">
      <header className="article-detail__header">
        <div className="article-detail__metadata">
          <span>{article.source.name}</span>
          <span aria-hidden="true">·</span>
          <time dateTime={timestamp}>{formatArticleDate(timestamp)}</time>
        </div>

        <h1 lang={primaryTitleLanguage}>{primaryTitle}</h1>
      </header>

      {primaryDescription && (
        <section className="article-detail__summary" aria-labelledby="article-summary-heading">
          <h2 id="article-summary-heading">摘要</h2>
          <p lang={primaryDescriptionLanguage}>{primaryDescription}</p>
        </section>
      )}

      {primaryContent && (
        <section className="article-detail__body" aria-label="文章正文">
          <ArticleParagraphs content={primaryContent} language={primaryContentLanguage} />
        </section>
      )}

      <a
        className="article-detail__external-link"
        href={article.url}
        target="_blank"
        rel="noopener noreferrer"
      >
        查看原文 ↗
      </a>

      {hasChineseTranslation && (
        <details className="article-detail__original">
          <summary>查看英文原文</summary>
          <div className="article-detail__original-content" lang="en">
            <h2>{article.original.title}</h2>
            {originalDescription && <p>{originalDescription}</p>}
            {originalContent && (
              <ArticleParagraphs content={originalContent} language="en" />
            )}
          </div>
        </details>
      )}
    </article>
  )
}

function ArticleParagraphs({ content, language }: { content: string; language: string }) {
  const paragraphs = content
    .replace(/\r\n?/g, '\n')
    .split(/\n[ \t]*\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)

  return (
    <div className="article-detail__paragraphs" lang={language}>
      {paragraphs.map((paragraph, index) => (
        <p key={index}>{paragraph}</p>
      ))}
    </div>
  )
}
