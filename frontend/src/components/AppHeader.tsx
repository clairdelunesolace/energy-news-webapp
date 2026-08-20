import { Link } from 'react-router-dom'

export function AppHeader() {
  return (
    <header className="app-header">
      <div className="app-header__content">
        <Link className="app-brand" to="/" aria-label="储能资讯首页">
          储能资讯
        </Link>
        <span className="app-header__section">最新资讯</span>
      </div>
    </header>
  )
}
