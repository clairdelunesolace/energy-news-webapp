interface PaginationControlsProps {
  page: number
  totalPages: number
  first: boolean
  last: boolean
  onPrevious: () => void
  onNext: () => void
}

export function PaginationControls({
  page,
  totalPages,
  first,
  last,
  onPrevious,
  onNext,
}: PaginationControlsProps) {
  if (totalPages === 0) return null

  return (
    <nav className="pagination" aria-label="资讯分页">
      <button className="button button--secondary" type="button" disabled={first} onClick={onPrevious}>
        上一页
      </button>
      <span className="pagination__status">
        第 {page + 1} / {totalPages} 页
      </span>
      <button className="button button--secondary" type="button" disabled={last} onClick={onNext}>
        下一页
      </button>
    </nav>
  )
}
