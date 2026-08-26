import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function AppHeader() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  const handleLogout = async () => {
    if (loggingOut) return
    setLoggingOut(true)

    try {
      await logout()
      navigate('/login', { replace: true })
    } catch {
      // Keep the current session visible if the server did not confirm logout.
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <header className="app-header">
      <div className="app-header__content">
        <Link className="app-brand" to="/" aria-label="储能资讯首页">
          储能资讯
        </Link>
        <div className="app-header__account">
          <span className="app-header__username">{username}</span>
          <button
            className="app-header__logout"
            type="button"
            onClick={handleLogout}
            disabled={loggingOut}
          >
            {loggingOut ? '正在退出…' : '退出登录'}
          </button>
        </div>
      </div>
    </header>
  )
}
