import { Link } from 'react-router-dom'
import './Footer.css'

function Footer() {
  const year = new Date().getFullYear()

  return (
    <footer className="footer" role="contentinfo">
      <div className="footer-container">
        <div className="footer-top">
          {/* Brand */}
          <div className="footer-brand">
            <Link to="/" className="footer-logo">
              <div className="footer-logo-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"
                        stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                        stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <span>CertChain</span>
            </Link>
            <p className="footer-tagline">
              Tamper-proof certificates powered by the Ethereum blockchain.
            </p>
          </div>

          {/* Links */}
          <div className="footer-links-group">
            <div className="footer-col">
              <h3 className="footer-col-title">Platform</h3>
              <ul>
                <li><Link to="/verify">Verify Certificate</Link></li>
                <li><Link to="/issue">Issue Certificate</Link></li>
                <li><Link to="/dashboard">Dashboard</Link></li>
              </ul>
            </div>
            <div className="footer-col">
              <h3 className="footer-col-title">System</h3>
              <ul>
                <li><Link to="/login">Admin Login</Link></li>
                <li><a href="https://ethereum.org" target="_blank" rel="noreferrer">Ethereum</a></li>
                <li><a href="https://docs.soliditylang.org" target="_blank" rel="noreferrer">Solidity Docs</a></li>
              </ul>
            </div>
          </div>
        </div>

        <div className="footer-bottom">
          <p className="footer-copyright">
            © {year} CertChain &mdash; Final Year Project. Built with React &amp; Ethereum.
          </p>
          <div className="footer-badges">
            <span className="footer-badge">⛓ Ethereum</span>
            <span className="footer-badge">⚛ React</span>
            <span className="footer-badge">🌱 Solidity</span>
          </div>
        </div>
      </div>
    </footer>
  )
}

export default Footer
