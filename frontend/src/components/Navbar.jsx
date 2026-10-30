import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './Navbar.css'

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { isLoggedIn, user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const closeMenu = () => setMenuOpen(false)

  const handleLogout = () => {
    logout()
    closeMenu()
    navigate('/')
  }

  return (
    <nav className="navbar" role="navigation" aria-label="Main navigation">
      <div className="navbar-container">
        {/* Logo */}
        <Link to="/" className="navbar-logo" onClick={closeMenu} aria-label="CertChain Home">
          <div className="logo-icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"
                    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="logo-text">CertChain</span>
        </Link>

        {/* Desktop Links */}
        <div className={`navbar-links ${menuOpen ? 'open' : ''}`} role="list">
          <NavLink to="/" end onClick={closeMenu}>Home</NavLink>
          <NavLink to="/verify" onClick={closeMenu}>Verify</NavLink>
          {isLoggedIn && <NavLink to="/issue" onClick={closeMenu}>Issue</NavLink>}
          {isLoggedIn && isAdmin && <NavLink to="/dashboard" onClick={closeMenu}>Dashboard</NavLink>}
        </div>

        {/* Actions */}
        <div className="navbar-actions">
          {isLoggedIn ? (
            <div className="navbar-user-group">
              <span className="navbar-user-badge">
                {user?.role === 'ADMIN' ? '🛡️ Admin' : '🏛️ Issuer'}: {user?.name?.split(' ')[0] || user?.email}
              </span>
              <button
                type="button"
                id="navbar-logout-btn"
                className="btn-outline-teal"
                onClick={handleLogout}
              >
                Logout
              </button>
            </div>
          ) : (
            <Link to="/login" id="navbar-login-btn" className="btn-outline-teal" onClick={closeMenu}>
              Admin Login
            </Link>
          )}
        </div>

        {/* Hamburger */}
        <button
          id="navbar-hamburger"
          className={`hamburger ${menuOpen ? 'open' : ''}`}
          onClick={() => setMenuOpen(!menuOpen)}
          aria-label="Toggle menu"
          aria-expanded={menuOpen}
        >
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
    </nav>
  )
}

export default Navbar
