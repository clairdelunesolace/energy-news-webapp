import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { generateDailyBrief, generateDailyBriefAnalysis, getDailyBrief, getDailyBriefAnalysis } from '../api/dailyBriefs'
import { getWatchlists } from '../api/watchlists'
import type { DailyBriefAnalysisResponse, DailyBriefResponse } from '../types/dailyBriefs'
import type { WatchlistResponse } from '../types/watchlists'
import '../styles/daily-briefs.css'

const DAILY_BRIEF_TIME_ZONE = 'Asia/Shanghai'
const REFRESH_CONFIRMATION = '刷新该日简报会重新选择当天文章，并清除当前 AI 简报。\n刷新后可重新生成 AI 简报。是否继续？'

export function DailyBriefsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [watchlists, setWatchlists] = useState<WatchlistResponse[]>([])
  const [watchlistStatus, setWatchlistStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [reload, setReload] = useState(0)
  const [generating, setGenerating] = useState(false)
  const watchlistId = searchParams.get('watchlistId') ?? ''
  const date = searchParams.get('date') ?? ''
  const validSelection = Number.isSafeInteger(Number(watchlistId)) && Number(watchlistId) > 0 && isDate(date)
  const defaultWatchlist = watchlists.find((watchlist) => watchlist.enabled)
  const selectedWatchlistId = Number.isSafeInteger(Number(watchlistId)) && Number(watchlistId) > 0
    ? watchlistId
    : defaultWatchlist ? String(defaultWatchlist.id) : ''
  const selectedDate = isDate(date) ? date : previousCalendarDateInShanghai()

  useEffect(() => {
    const controller = new AbortController()
    setWatchlistStatus('loading')
    getWatchlists(controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return
        setWatchlists(response)
        setWatchlistStatus('ready')
      })
      .catch(() => {
        if (!controller.signal.aborted) setWatchlistStatus('error')
      })
    return () => controller.abort()
  }, [reload])

  useEffect(() => {
    if (watchlistStatus !== 'ready' || validSelection || !defaultWatchlist) return
    setSearchParams({ watchlistId: String(defaultWatchlist.id), date: selectedDate }, { replace: true })
  }, [defaultWatchlist, selectedDate, setSearchParams, validSelection, watchlistStatus])

  return (
    <main className="page-shell">
      <section className="daily-brief-page" aria-labelledby="daily-brief-heading">
        <header className="feed-page__heading">
          <h1 id="daily-brief-heading">每日情报简报</h1>
          <p>按关注主题与日期查看已生成的简报，聚焦值得关注的核心事件。</p>
        </header>

        {watchlistStatus === 'loading' && <p role="status">正在加载关注主题…</p>}
        {watchlistStatus === 'error' && (
          <div className="status-message status-message--error" role="alert">
            <p>关注主题加载失败，请稍后重试。</p>
            <button className="button button--secondary" onClick={() => setReload((value) => value + 1)}>
              重新加载
            </button>
          </div>
        )}
        {watchlistStatus === 'ready' && watchlists.length === 0 && (
          <p className="status-message">还没有关注主题。<Link to="/watchlists">前往管理关注关键词</Link></p>
        )}
        {watchlistStatus === 'ready' && watchlists.length > 0 && selectedWatchlistId && (
          <BriefSelector
            watchlists={watchlists}
            watchlistId={selectedWatchlistId}
            date={selectedDate}
            disabled={generating}
            onSelect={(selectedId, selectedDate) => {
              setSearchParams({ watchlistId: selectedId, date: selectedDate })
            }}
          />
        )}
        {watchlistStatus === 'ready' && watchlists.length > 0 && !selectedWatchlistId && (
          <p className="status-message daily-brief-state">没有已启用的关注主题。</p>
        )}

        {watchlistStatus === 'ready' && (watchlistId !== '' || date !== '') && !validSelection && (
          <p className="status-message status-message--error daily-brief-state" role="alert">请选择有效的关注主题和日期。</p>
        )}
        {validSelection && (
          <BriefContent key={`${watchlistId}:${date}`} watchlistId={Number(watchlistId)} date={date} onGeneratingChange={setGenerating} />
        )}
      </section>
    </main>
  )
}

function BriefSelector({ watchlists, watchlistId, date, disabled, onSelect }: {
  watchlists: WatchlistResponse[]
  watchlistId: string
  date: string
  disabled: boolean
  onSelect(id: string, date: string): void
}) {
  return (
    <div className="feed-toolbar daily-brief-selector">
      <label className="feed-toolbar__field">
        <span className="feed-toolbar__label">关注主题</span>
        <select value={watchlistId} onChange={(event) => onSelect(event.target.value, date)} disabled={disabled} required>
          {!watchlists.some((item) => String(item.id) === watchlistId) && (
            <option value={watchlistId}>主题 #{watchlistId}</option>
          )}
          {watchlists.map((item) => <option key={item.id} value={item.id}>{item.name}{item.enabled ? '' : '（已停用）'}</option>)}
        </select>
      </label>
      <label className="feed-toolbar__field">
        <span className="feed-toolbar__label">简报日期</span>
        <input type="date" value={date} onChange={(event) => onSelect(watchlistId, event.target.value)} disabled={disabled} required />
      </label>
    </div>
  )
}

function BriefContent({ watchlistId, date, onGeneratingChange }: {
  watchlistId: number
  date: string
  onGeneratingChange(value: boolean): void
}) {
  const [brief, setBrief] = useState<DailyBriefResponse | null>(null)
  const [analysis, setAnalysis] = useState<DailyBriefAnalysisResponse | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'not-found' | 'error'>('loading')
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [analysisLoadFailed, setAnalysisLoadFailed] = useState(false)
  const [operation, setOperation] = useState<'brief' | 'analysis' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const generationRequest = useRef<AbortController | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    getDailyBrief(watchlistId, date, controller.signal)
      .then(async (response) => {
        if (controller.signal.aborted) return
        setBrief(response)
        setStatus('ready')
        setAnalysisLoading(true)
        setAnalysisLoadFailed(false)
        try {
          const existing = await getDailyBriefAnalysis(response.id, controller.signal)
          if (!controller.signal.aborted) setAnalysis(existing)
        } catch {
          if (!controller.signal.aborted) {
            setAnalysisLoadFailed(true)
            setError('已有 AI 简报状态加载失败，请重新选择日期后重试。')
          }
        } finally {
          if (!controller.signal.aborted) setAnalysisLoading(false)
        }
      })
      .catch((loadError: unknown) => {
        if (!controller.signal.aborted) setStatus(loadError instanceof ApiError && loadError.status === 404 ? 'not-found' : 'error')
      })
    return () => {
      controller.abort()
      generationRequest.current?.abort()
      onGeneratingChange(false)
    }
  }, [watchlistId, date, onGeneratingChange])

  const generateBrief = async () => {
    if (status !== 'not-found' || generationRequest.current) return
    const controller = new AbortController()
    generationRequest.current = controller
    setOperation('brief')
    onGeneratingChange(true)
    setError(null)
    try {
      const generated = await generateDailyBrief(watchlistId, date, controller.signal)
      if (!controller.signal.aborted) {
        setBrief(generated)
        setAnalysis(null)
        setAnalysisLoadFailed(false)
        setStatus('ready')
        setNotice('该日简报已生成。')
      }
    } catch (generationError: unknown) {
      if (!controller.signal.aborted) setError(briefGenerationErrorMessage(generationError))
    } finally {
      if (!controller.signal.aborted) {
        generationRequest.current = null
        setOperation(null)
        onGeneratingChange(false)
      }
    }
  }

  const refreshBrief = async () => {
    if (!brief || status !== 'ready' || analysisLoading || analysisLoadFailed || generationRequest.current) return
    if (analysis && !window.confirm(REFRESH_CONFIRMATION)) return
    const controller = new AbortController()
    generationRequest.current = controller
    setOperation('brief')
    onGeneratingChange(true)
    setError(null)
    setNotice(null)
    try {
      const refreshed = await generateDailyBrief(watchlistId, date, controller.signal)
      if (!controller.signal.aborted) {
        setBrief(refreshed)
        setAnalysis(null)
        setAnalysisLoadFailed(false)
        setNotice('该日简报已刷新，AI 简报可重新生成。')
      }
    } catch (generationError: unknown) {
      if (!controller.signal.aborted) setError(briefGenerationErrorMessage(generationError))
    } finally {
      if (!controller.signal.aborted) {
        generationRequest.current = null
        setOperation(null)
        onGeneratingChange(false)
      }
    }
  }

  const generate = async () => {
    if (!brief || brief.itemCount === 0 || analysisLoading || analysisLoadFailed || generationRequest.current) return
    const controller = new AbortController()
    generationRequest.current = controller
    setOperation('analysis')
    onGeneratingChange(true)
    setError(null)
    setNotice(null)
    try {
      const refreshed = await generateDailyBriefAnalysis(brief.id, controller.signal)
      if (!controller.signal.aborted) {
        setAnalysis(refreshed)
        setNotice('AI 简报已更新并保存。')
      }
    } catch (generationError: unknown) {
      if (!controller.signal.aborted) setError(generationErrorMessage(generationError))
    } finally {
      if (!controller.signal.aborted) {
        generationRequest.current = null
        setOperation(null)
        onGeneratingChange(false)
      }
    }
  }

  if (status === 'loading') return <p className="status-message daily-brief-state" role="status">正在加载简报…</p>
  if (status === 'not-found') return (
    <div className="status-message daily-brief-state" aria-busy={operation === 'brief'}>
      <p>该主题在所选日期还没有简报。</p>
      <p className="daily-brief-footnote">将根据数据库中该日期已有的匹配新闻生成简报。</p>
      <button className="button button--primary" type="button" onClick={generateBrief} disabled={operation !== null}>
        {operation === 'brief' ? '正在生成简报...' : '生成该日简报'}
      </button>
      {operation === 'brief' && <p className="daily-brief-notice" role="status">正在生成简报...</p>}
      {error && <p className="daily-brief-error" role="alert">{error}</p>}
    </div>
  )
  if (status === 'error' || !brief) return <p className="status-message status-message--error daily-brief-state" role="alert">简报加载失败，请点击“查看简报”重试。</p>

  return (
    <div className="daily-brief-report">
      <dl className="daily-brief-metadata">
        <div><dt>关注主题</dt><dd>{brief.watchlistName} <span>#{brief.watchlistId}</span></dd></div>
        <div><dt>简报日期</dt><dd>{brief.briefDate}</dd></div>
        <div><dt>候选文章</dt><dd>{brief.candidateCount} <span>篇</span></dd></div>
        <div><dt>入选文章</dt><dd>{brief.itemCount} <span>篇</span></dd></div>
      </dl>
      <div className="daily-brief-snapshot-actions">
        <p className="daily-brief-footnote">简报 #{brief.id} · {brief.zone} · 更新于 <time dateTime={brief.updatedAt}>{formatTime(brief.updatedAt, brief.zone)}</time></p>
        <button className="button button--secondary" type="button" onClick={refreshBrief} disabled={operation !== null || analysisLoading || analysisLoadFailed}>
          {operation === 'brief' ? '正在刷新简报...' : '刷新该日简报'}
        </button>
      </div>
      {notice && <p className="daily-brief-notice" role="status">{notice}</p>}
      {error && <p className="daily-brief-error" role="alert">{error}</p>}

      {brief.itemCount > 0 && (
        <section aria-label="AI 简报" aria-busy={analysisLoading || operation === 'analysis'}>
          <div className="daily-brief-actions">
            <span className="daily-brief-ai-label">AI 生成 · 仅供情报参考</span>
            <button className="button button--primary" type="button" onClick={generate} disabled={operation !== null || analysisLoading || analysisLoadFailed || brief.itemCount === 0}>
              {operation === 'analysis' ? '正在生成 AI 简报…' : analysis ? '重新生成 AI 简报' : '生成 AI 简报'}
            </button>
          </div>
          <div aria-live="polite">
            {operation === 'analysis' && <p className="daily-brief-notice" role="status">正在生成，请稍候。已有简报会保留，成功后自动更新。</p>}
          </div>
          {analysisLoading && <p className="status-message" role="status">正在加载 AI 简报…</p>}
          {!analysisLoading && !analysis && !error && (
            <p className="status-message">尚未生成 AI 简报，点击上方按钮开始分析。</p>
          )}
          {analysis && (
            <>
              <header className="daily-brief-analysis-heading">
                <h2>{analysis.headline}</h2>
                <p className="daily-brief-overview">{analysis.overview}</p>
                <p className="daily-brief-footnote">生成于 <time dateTime={analysis.generatedAt}>{formatTime(analysis.generatedAt, brief.zone)}</time> · {analysis.provider} / {analysis.model}</p>
              </header>
              <h2 className="daily-brief-events-heading">核心事件</h2>
              <ol className="daily-brief-events">
                {analysis.events.map((event) => (
                  <li key={event.rank}>
                    <article className="daily-brief-event">
                      <h3><span className="daily-brief-event__rank">{event.rank}</span>{event.title}</h3>
                      <p className="daily-brief-event__summary">{event.summary}</p>
                      <div className="daily-brief-event__importance"><h4>为什么重要</h4><p>{event.whyItMatters}</p></div>
                      <div className="daily-brief-event__sources">
                        <span>支持文章：</span>
                        {event.supportingArticleIds.map((id) => (
                          <Link key={id} to={`/articles/${id}`} title={brief.items.find((item) => item.articleId === id)?.title}>#{id}</Link>
                        ))}
                      </div>
                    </article>
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      )}

      <SelectedArticles brief={brief} />
    </div>
  )
}

function SelectedArticles({ brief }: { brief: DailyBriefResponse }) {
  return (
    <section className="daily-brief-selected-articles" aria-labelledby="daily-brief-selected-heading">
      <h2 id="daily-brief-selected-heading">入选文章</h2>
      {brief.items.length === 0 ? (
        <p className="status-message">该日期暂无匹配新闻，已生成空简报。</p>
      ) : (
        <ol>
          {brief.items.map((item) => (
            <li key={item.articleId}>
              <article>
                <h3><span>{item.rank}</span><Link to={`/articles/${item.articleId}`}>{item.title}</Link></h3>
                <p>{item.sourceName} · <time dateTime={item.effectiveTime}>{formatTime(item.effectiveTime, brief.zone)}</time></p>
                {item.description && <p>{item.description}</p>}
                {item.matchedKeywords.length > 0 && <p>匹配关键词：{item.matchedKeywords.join(' · ')}</p>}
              </article>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function briefGenerationErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 400: return '关注主题或日期无效，请重新选择。'
      case 401: return '登录状态已过期，请重新登录。'
      case 403: return '操作未完成，请刷新页面后重试。'
      case 404: return '该关注主题已不可用，请重新选择。'
      case 409: return '该关注主题已停用，无法生成简报。'
    }
  }
  return '简报生成失败，请稍后重试。'
}

function generationErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401: return '登录状态已过期，请重新登录。'
      case 403: return '操作未完成，请刷新页面后重试。'
      case 404: return '这份简报已不可用，请重新查询。'
      case 409: return '简报内容已变化或暂无可分析文章，请重新查询。'
      case 503: return 'AI 服务暂不可用或请求较多，请稍后重试。'
      case 504: return 'AI 生成等待超时，请稍后重试。'
    }
  }
  return 'AI 简报生成失败，请稍后重试。已有简报不会被替换。'
}

function isDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value
}

function previousCalendarDateInShanghai(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: DAILY_BRIEF_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const value = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((part) => part.type === type)?.value)
  const shanghaiCalendarDate = new Date(Date.UTC(value('year'), value('month') - 1, value('day')))
  shanghaiCalendarDate.setUTCDate(shanghaiCalendarDate.getUTCDate() - 1)
  return shanghaiCalendarDate.toISOString().slice(0, 10)
}

function formatTime(value: string, zone: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium', timeZone: zone, hour12: false }).format(new Date(value))
}
