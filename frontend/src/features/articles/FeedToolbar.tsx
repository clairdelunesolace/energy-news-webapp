import type { SourceResponse } from '../../types/sources'

interface FeedToolbarProps {
  keywordInput: string
  onKeywordInputChange: (value: string) => void
  onSearch: () => void
  sources: SourceResponse[]
  selectedSourceId: number | null
  onSourceChange: (sourceId: number | null) => void
  sourcesLoading: boolean
  sourcesError: boolean
}

export function FeedToolbar({
  keywordInput,
  onKeywordInputChange,
  onSearch,
  sources,
  selectedSourceId,
  onSourceChange,
  sourcesLoading,
  sourcesError,
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
        <span className="feed-toolbar__label">来源</span>
        <select
          value={selectedSourceId ?? ''}
          disabled={sourcesLoading}
          onChange={(event) => {
            const value = event.target.value
            onSourceChange(value ? Number(value) : null)
          }}
        >
          <option value="">全部来源</option>
          {sources.map((source) => (
            <option key={source.id} value={source.id}>
              {source.name}
            </option>
          ))}
        </select>
        {sourcesError && <span className="feed-toolbar__note">来源列表加载失败</span>}
      </label>

      <button className="button button--primary feed-toolbar__submit" type="submit">
        搜索
      </button>
    </form>
  )
}
