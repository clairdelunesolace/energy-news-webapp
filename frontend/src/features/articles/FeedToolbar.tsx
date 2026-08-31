import type { WatchlistResponse } from '../../types/watchlists'

interface FeedToolbarProps {
  keywordInput: string
  onKeywordInputChange: (value: string) => void
  onSearch: () => void
  watchlists: WatchlistResponse[]
  selectedKeywordId: number | null
  onKeywordChange: (keywordId: number | null) => void
  keywordsLoading: boolean
  keywordsError: boolean
}

export function FeedToolbar({
  keywordInput,
  onKeywordInputChange,
  onSearch,
  watchlists,
  selectedKeywordId,
  onKeywordChange,
  keywordsLoading,
  keywordsError,
}: FeedToolbarProps) {
  return (
    <form
      className="feed-toolbar"
      role="search"
      onSubmit={(event) => {
        event.preventDefault()
        onSearch()
      }}
    >
      <label className="feed-toolbar__field">
        <span className="feed-toolbar__label">搜索</span>
        <input
          type="search"
          value={keywordInput}
          placeholder="搜索资讯"
          onChange={(event) => onKeywordInputChange(event.target.value)}
        />
      </label>

      <label className="feed-toolbar__field">
        <span className="feed-toolbar__label">关键词</span>
        <select
          value={selectedKeywordId ?? ''}
          disabled={keywordsLoading}
          onChange={(event) => {
            const value = event.target.value
            onKeywordChange(value ? Number(value) : null)
          }}
        >
          <option value="">全部关键词</option>
          {watchlists.map((watchlist) => (
            <optgroup key={watchlist.id} label={watchlist.name}>
              {watchlist.keywords.map((keyword) => (
                <option key={keyword.id} value={keyword.id}>
                  {keyword.keyword}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        {keywordsError && <span className="feed-toolbar__note">关键词列表加载失败</span>}
      </label>

      <button className="button button--primary feed-toolbar__submit" type="submit">
        搜索
      </button>
    </form>
  )
}
