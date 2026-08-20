import { useEffect, useState } from 'react'
import { getArticles } from '../../api/articles'
import { getSources } from '../../api/sources'
import type { ArticlePageResponse } from '../../types/articles'
import type { SourceResponse } from '../../types/sources'
import { ArticleListItem } from './ArticleListItem'
import { FeedToolbar } from './FeedToolbar'
import { PaginationControls } from './PaginationControls'

const PAGE_SIZE = 20

type LoadStatus = 'loading' | 'success' | 'error'

export function ArticleFeed() {
  const [page, setPage] = useState(0)
  const [keywordInput, setKeywordInput] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [selectedSourceId, setSelectedSourceId] = useState<number | null>(null)
  const [articlePage, setArticlePage] = useState<ArticlePageResponse | null>(null)
  const [status, setStatus] = useState<LoadStatus>('loading')
  const [sources, setSources] = useState<SourceResponse[]>([])
  const [sourcesLoading, setSourcesLoading] = useState(true)
  const [sourcesError, setSourcesError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    getSources(controller.signal)
      .then((response) => {
        setSources(response.filter((source) => source.enabled))
        setSourcesError(false)
        setSourcesLoading(false)
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setSourcesError(true)
        setSourcesLoading(false)
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setStatus('loading')
    setArticlePage(null)

    getArticles(
      {
        page,
        size: PAGE_SIZE,
        sourceId: selectedSourceId ?? undefined,
        keyword: submittedKeyword || undefined,
      },
      controller.signal,
    )
      .then((response) => {
        setArticlePage(response)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setStatus('error')
      })

    return () => controller.abort()
  }, [page, selectedSourceId, submittedKeyword])

  const submitSearch = () => {
    setPage(0)
    setSubmittedKeyword(keywordInput.trim())
  }

  const changeSource = (sourceId: number | null) => {
    setPage(0)
    setSelectedSourceId(sourceId)
  }

  return (
    <section className="article-feed" aria-label="资讯列表">
      <FeedToolbar
        keywordInput={keywordInput}
        onKeywordInputChange={setKeywordInput}
        onSearch={submitSearch}
        sources={sources}
        selectedSourceId={selectedSourceId}
        onSourceChange={changeSource}
        sourcesLoading={sourcesLoading}
        sourcesError={sourcesError}
      />

      <div className="article-feed__results" aria-live="polite" aria-busy={status === 'loading'}>
        {status === 'loading' && <p className="status-message">正在加载资讯…</p>}

        {status === 'error' && (
          <p className="status-message status-message--error">资讯加载失败，请稍后重试。</p>
        )}

        {status === 'success' && articlePage?.content.length === 0 && (
          <p className="status-message">没有找到符合条件的资讯。</p>
        )}

        {status === 'success' && articlePage && articlePage.content.length > 0 && (
          <>
            <ul className="article-feed__list">
              {articlePage.content.map((article) => (
                <ArticleListItem article={article} key={article.id} />
              ))}
            </ul>
            <PaginationControls
              page={articlePage.page}
              totalPages={articlePage.totalPages}
              first={articlePage.first}
              last={articlePage.last}
              onPrevious={() => setPage((currentPage) => Math.max(0, currentPage - 1))}
              onNext={() => setPage((currentPage) => currentPage + 1)}
            />
          </>
        )}
      </div>
    </section>
  )
}
