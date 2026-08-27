import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import {
  addKeyword,
  createWatchlist,
  deleteKeyword,
  deleteWatchlist,
  getWatchlists,
  updateKeyword,
  updateWatchlist,
} from '../api/watchlists'
import { ApiError } from '../api/client'
import type { KeywordResponse, WatchlistResponse } from '../types/watchlists'

type LoadStatus = 'loading' | 'success' | 'error'
type PerformMutation = <T>(
  key: string,
  action: () => Promise<T>,
  duplicateMessage: string,
) => Promise<T | null>

export function WatchlistsPage() {
  const [watchlists, setWatchlists] = useState<WatchlistResponse[]>([])
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading')
  const [newWatchlistName, setNewWatchlistName] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [operationError, setOperationError] = useState<string | null>(null)
  const createInputRef = useRef<HTMLInputElement>(null)

  const loadWatchlists = useCallback((signal?: AbortSignal) => {
    setLoadStatus('loading')
    getWatchlists(signal)
      .then((response) => {
        setWatchlists(sortWatchlists(response))
        setLoadStatus('success')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setLoadStatus('error')
      })
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    loadWatchlists(controller.signal)
    return () => controller.abort()
  }, [loadWatchlists])

  const performMutation: PerformMutation = async (key, action, duplicateMessage) => {
    setPending(key)
    setOperationError(null)
    try {
      return await action()
    } catch (error: unknown) {
      setOperationError(
        error instanceof ApiError && error.status === 409
          ? duplicateMessage
          : '操作失败，请稍后重试。',
      )
      return null
    } finally {
      setPending(null)
    }
  }

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    const name = newWatchlistName.trim()
    if (!name) return

    const created = await performMutation(
      'create-watchlist',
      () => createWatchlist(name),
      '该关注主题已存在',
    )
    if (!created) return

    setWatchlists((current) => sortWatchlists([...current, created]))
    setNewWatchlistName('')
  }

  const replaceWatchlist = (updated: WatchlistResponse) => {
    setWatchlists((current) =>
      sortWatchlists(current.map((watchlist) => (watchlist.id === updated.id ? updated : watchlist))),
    )
  }

  const changeKeywords = (
    watchlistId: number,
    change: (keywords: KeywordResponse[]) => KeywordResponse[],
  ) => {
    setWatchlists((current) =>
      current.map((watchlist) =>
        watchlist.id === watchlistId
          ? { ...watchlist, keywords: sortKeywords(change(watchlist.keywords)) }
          : watchlist,
      ),
    )
  }

  return (
    <main className="page-shell">
      <section className="watchlists-page" aria-labelledby="watchlists-heading">
        <header className="watchlists-page__heading">
          <div>
            <h1 id="watchlists-heading">关注关键词</h1>
            <p>管理关注主题与关键词，为后续新闻发现提供兴趣范围。</p>
          </div>
          <form className="watchlist-create-form" onSubmit={handleCreate}>
            <label htmlFor="new-watchlist-name">新建关注主题</label>
            <div className="inline-form__controls">
              <input
                ref={createInputRef}
                id="new-watchlist-name"
                value={newWatchlistName}
                onChange={(event) => setNewWatchlistName(event.target.value)}
                maxLength={100}
                placeholder="例如：数据中心能源"
                disabled={pending !== null}
              />
              <button
                className="button button--primary"
                type="submit"
                disabled={!newWatchlistName.trim() || pending !== null}
              >
                {pending === 'create-watchlist' ? '正在添加…' : '添加主题'}
              </button>
            </div>
          </form>
        </header>

        {operationError && (
          <p className="watchlists-page__operation-error" role="alert">
            {operationError}
          </p>
        )}

        <div aria-live="polite" aria-busy={loadStatus === 'loading'}>
          {loadStatus === 'loading' && <p className="status-message">加载中...</p>}

          {loadStatus === 'error' && (
            <div className="status-message status-message--error">
              <p>关注主题加载失败，请稍后重试。</p>
              <button className="button button--secondary" type="button" onClick={() => loadWatchlists()}>
                重新加载
              </button>
            </div>
          )}

          {loadStatus === 'success' && watchlists.length === 0 && (
            <div className="watchlists-empty">
              <p>还没有关注主题</p>
              <button
                className="button button--secondary"
                type="button"
                onClick={() => createInputRef.current?.focus()}
              >
                添加第一个关注主题
              </button>
            </div>
          )}

          {loadStatus === 'success' && watchlists.length > 0 && (
            <div className="watchlist-list">
              {watchlists.map((watchlist) => (
                <WatchlistSection
                  key={watchlist.id}
                  watchlist={watchlist}
                  pending={pending}
                  performMutation={performMutation}
                  onWatchlistChanged={replaceWatchlist}
                  onWatchlistDeleted={(id) =>
                    setWatchlists((current) => current.filter((item) => item.id !== id))
                  }
                  onKeywordAdded={(keyword) =>
                    changeKeywords(watchlist.id, (keywords) => [...keywords, keyword])
                  }
                  onKeywordChanged={(keyword) =>
                    changeKeywords(watchlist.id, (keywords) =>
                      keywords.map((item) => (item.id === keyword.id ? keyword : item)),
                    )
                  }
                  onKeywordDeleted={(keywordId) =>
                    changeKeywords(watchlist.id, (keywords) =>
                      keywords.filter((keyword) => keyword.id !== keywordId),
                    )
                  }
                />
              ))}
            </div>
          )}
        </div>
      </section>
    </main>
  )
}

interface WatchlistSectionProps {
  watchlist: WatchlistResponse
  pending: string | null
  performMutation: PerformMutation
  onWatchlistChanged(watchlist: WatchlistResponse): void
  onWatchlistDeleted(id: number): void
  onKeywordAdded(keyword: KeywordResponse): void
  onKeywordChanged(keyword: KeywordResponse): void
  onKeywordDeleted(id: number): void
}

function WatchlistSection({
  watchlist,
  pending,
  performMutation,
  onWatchlistChanged,
  onWatchlistDeleted,
  onKeywordAdded,
  onKeywordChanged,
  onKeywordDeleted,
}: WatchlistSectionProps) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(watchlist.name)
  const [newKeyword, setNewKeyword] = useState('')
  const disabled = pending !== null

  const saveName = async (event: FormEvent) => {
    event.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName) return
    const updated = await performMutation(
      `rename-${watchlist.id}`,
      () => updateWatchlist(watchlist.id, { name: trimmedName }),
      '该关注主题已存在',
    )
    if (!updated) return
    onWatchlistChanged(updated)
    setEditing(false)
  }

  const toggleEnabled = async () => {
    const updated = await performMutation(
      `toggle-${watchlist.id}`,
      () => updateWatchlist(watchlist.id, { enabled: !watchlist.enabled }),
      '该关注主题已存在',
    )
    if (updated) onWatchlistChanged(updated)
  }

  const removeWatchlist = async () => {
    const confirmed = window.confirm(`确定删除“${watchlist.name}”及其全部关键词吗？`)
    if (!confirmed) return
    const deleted = await performMutation(
      `delete-${watchlist.id}`,
      async () => {
        await deleteWatchlist(watchlist.id)
        return true
      },
      '该关注主题已存在',
    )
    if (deleted) onWatchlistDeleted(watchlist.id)
  }

  const submitKeyword = async (event: FormEvent) => {
    event.preventDefault()
    const text = newKeyword.trim()
    if (!text) return
    const created = await performMutation(
      `add-keyword-${watchlist.id}`,
      () => addKeyword(watchlist.id, text),
      '该关键词已存在',
    )
    if (!created) return
    onKeywordAdded(created)
    setNewKeyword('')
  }

  return (
    <section className="watchlist-section" aria-labelledby={`watchlist-${watchlist.id}-heading`}>
      <header className="watchlist-section__header">
        {editing ? (
          <form className="watchlist-rename-form" onSubmit={saveName}>
            <label className="visually-hidden" htmlFor={`watchlist-name-${watchlist.id}`}>
              关注主题名称
            </label>
            <input
              id={`watchlist-name-${watchlist.id}`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              disabled={disabled}
              autoFocus
            />
            <button className="button button--primary button--compact" type="submit" disabled={!name.trim() || disabled}>
              保存
            </button>
            <button
              className="text-button"
              type="button"
              onClick={() => {
                setName(watchlist.name)
                setEditing(false)
              }}
              disabled={disabled}
            >
              取消
            </button>
          </form>
        ) : (
          <div className="watchlist-section__title-row">
            <h2 id={`watchlist-${watchlist.id}-heading`}>{watchlist.name}</h2>
            <StatusLabel enabled={watchlist.enabled} />
          </div>
        )}

        {!editing && (
          <div className="watchlist-section__actions">
            <button className="text-button" type="button" onClick={() => setEditing(true)} disabled={disabled}>
              重命名
            </button>
            <button className="text-button" type="button" onClick={toggleEnabled} disabled={disabled}>
              {watchlist.enabled ? '停用' : '启用'}
            </button>
            <button className="text-button text-button--danger" type="button" onClick={removeWatchlist} disabled={disabled}>
              删除主题
            </button>
          </div>
        )}
      </header>

      {watchlist.keywords.length === 0 ? (
        <p className="watchlist-section__no-keywords">还没有关键词</p>
      ) : (
        <ul className="keyword-list">
          {watchlist.keywords.map((keyword) => (
            <KeywordRow
              key={keyword.id}
              keyword={keyword}
              pending={pending}
              performMutation={performMutation}
              onChanged={onKeywordChanged}
              onDeleted={onKeywordDeleted}
            />
          ))}
        </ul>
      )}

      <form className="keyword-create-form" onSubmit={submitKeyword}>
        <label htmlFor={`new-keyword-${watchlist.id}`}>添加关键词</label>
        <div className="inline-form__controls">
          <input
            id={`new-keyword-${watchlist.id}`}
            value={newKeyword}
            onChange={(event) => setNewKeyword(event.target.value)}
            maxLength={200}
            placeholder="输入关键词"
            disabled={disabled}
          />
          <button className="button button--secondary" type="submit" disabled={!newKeyword.trim() || disabled}>
            添加关键词
          </button>
        </div>
      </form>
    </section>
  )
}

interface KeywordRowProps {
  keyword: KeywordResponse
  pending: string | null
  performMutation: PerformMutation
  onChanged(keyword: KeywordResponse): void
  onDeleted(id: number): void
}

function KeywordRow({ keyword, pending, performMutation, onChanged, onDeleted }: KeywordRowProps) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(keyword.keyword)
  const disabled = pending !== null

  const saveKeyword = async (event: FormEvent) => {
    event.preventDefault()
    const trimmedText = text.trim()
    if (!trimmedText) return
    const updated = await performMutation(
      `edit-keyword-${keyword.id}`,
      () => updateKeyword(keyword.id, { keyword: trimmedText }),
      '该关键词已存在',
    )
    if (!updated) return
    onChanged(updated)
    setEditing(false)
  }

  const toggleEnabled = async () => {
    const updated = await performMutation(
      `toggle-keyword-${keyword.id}`,
      () => updateKeyword(keyword.id, { enabled: !keyword.enabled }),
      '该关键词已存在',
    )
    if (updated) onChanged(updated)
  }

  const removeKeyword = async () => {
    const deleted = await performMutation(
      `delete-keyword-${keyword.id}`,
      async () => {
        await deleteKeyword(keyword.id)
        return true
      },
      '该关键词已存在',
    )
    if (deleted) onDeleted(keyword.id)
  }

  return (
    <li className="keyword-row">
      {editing ? (
        <form className="keyword-edit-form" onSubmit={saveKeyword}>
          <label className="visually-hidden" htmlFor={`keyword-${keyword.id}`}>
            编辑关键词
          </label>
          <input
            id={`keyword-${keyword.id}`}
            value={text}
            onChange={(event) => setText(event.target.value)}
            maxLength={200}
            disabled={disabled}
            autoFocus
          />
          <button className="button button--primary button--compact" type="submit" disabled={!text.trim() || disabled}>
            保存
          </button>
          <button
            className="text-button"
            type="button"
            onClick={() => {
              setText(keyword.keyword)
              setEditing(false)
            }}
            disabled={disabled}
          >
            取消
          </button>
        </form>
      ) : (
        <>
          <div className="keyword-row__value">
            <span>{keyword.keyword}</span>
            <StatusLabel enabled={keyword.enabled} />
          </div>
          <div className="keyword-row__actions">
            <button className="text-button" type="button" onClick={() => setEditing(true)} disabled={disabled}>
              编辑
            </button>
            <button className="text-button" type="button" onClick={toggleEnabled} disabled={disabled}>
              {keyword.enabled ? '停用' : '启用'}
            </button>
            <button className="text-button text-button--danger" type="button" onClick={removeKeyword} disabled={disabled}>
              删除
            </button>
          </div>
        </>
      )}
    </li>
  )
}

function StatusLabel({ enabled }: { enabled: boolean }) {
  return (
    <span className={`status-label ${enabled ? 'status-label--enabled' : ''}`}>
      {enabled ? '启用' : '停用'}
    </span>
  )
}

function sortWatchlists(watchlists: WatchlistResponse[]): WatchlistResponse[] {
  return [...watchlists]
    .map((watchlist) => ({ ...watchlist, keywords: sortKeywords(watchlist.keywords) }))
    .sort((left, right) => {
      if (left.enabled !== right.enabled) return left.enabled ? -1 : 1
      const nameOrder = left.name.localeCompare(right.name, 'zh-CN', { sensitivity: 'base' })
      return nameOrder || left.id - right.id
    })
}

function sortKeywords(keywords: KeywordResponse[]): KeywordResponse[] {
  return [...keywords].sort((left, right) => {
    const keywordOrder = left.keyword.localeCompare(right.keyword, 'zh-CN', { sensitivity: 'base' })
    return keywordOrder || left.id - right.id
  })
}
