import { Route, Routes } from 'react-router-dom'
import { AppHeader } from './components/AppHeader'
import { ArticleDetailPage } from './pages/ArticleDetailPage'
import { HomePage } from './pages/HomePage'

export default function App() {
  return (
    <div className="app-shell">
      <AppHeader />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/articles/:id" element={<ArticleDetailPage />} />
      </Routes>
    </div>
  )
}
