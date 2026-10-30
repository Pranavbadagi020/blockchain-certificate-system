import { Link } from 'react-router-dom'
import './Home.css'

const features = [
  {
    icon: '🔒',
    title: 'Tamper-Proof',
    desc: 'Once recorded on the blockchain, a certificate cannot be changed or deleted — by anyone, including the issuer.',
  },
  {
    icon: '⚡',
    title: 'Instant Verification',
    desc: 'Enter a certificate ID and get a result in seconds. No emails, no phone calls, no waiting.',
  },
  {
    icon: '🌐',
    title: 'Decentralised',
    desc: 'There is no central server that can go offline. The Ethereum network keeps records available forever.',
  },
  {
    icon: '🔍',
    title: 'Fully Transparent',
    desc: "Every issuance is publicly auditable on-chain. Anyone can independently verify the system's integrity.",
  },
]

const steps = [
  {
    number: '01',
    icon: '📋',
    title: 'Institution Issues',
    desc: 'An authorised institution fills in the student details and submits the certificate to the blockchain.',
  },
  {
    number: '02',
    icon: '⛓',
    title: 'Stored on Chain',
    desc: 'The data is cryptographically hashed and permanently recorded as an Ethereum transaction.',
  },
  {
    number: '03',
    icon: '✅',
    title: 'Anyone Verifies',
    desc: 'Employers, universities, or any third party can verify authenticity instantly using the certificate ID.',
  },
]

function Home() {
  return (
    <div className="home">
      {/* ── Hero ── */}
      <section id="hero" className="hero">
        {/* Decorative background */}
        <div className="hero-bg" aria-hidden="true">
          <div className="orb orb-1"></div>
          <div className="orb orb-2"></div>
          <div className="orb orb-3"></div>
          <div className="hero-grid"></div>
        </div>

        <div className="hero-content container">
          <div className="hero-badge">
            <span className="badge-dot" aria-hidden="true"></span>
            Powered by Ethereum Blockchain
          </div>

          <h1 className="hero-title">
            Certificate Verification
            <br />
            <span className="gradient-text">You Can Trust</span>
          </h1>

          <p className="hero-subtitle">
            Issue tamper-proof digital certificates and verify them instantly —
            anywhere in the world. No forgery. No fraud. No doubt.
          </p>

          <div className="hero-actions">
            <Link to="/verify" id="hero-verify-btn" className="btn-primary hero-btn">
              🔍 Verify a Certificate
            </Link>
            <Link to="/issue" id="hero-issue-btn" className="btn-secondary hero-btn">
              📜 Issue Certificate
            </Link>
          </div>

          {/* Stats */}
          <div className="hero-stats" role="list" aria-label="Platform statistics">
            <div className="hero-stat" role="listitem">
              <span className="stat-number">1,200+</span>
              <span className="stat-label">Certificates Issued</span>
            </div>
            <div className="stat-sep" aria-hidden="true"></div>
            <div className="hero-stat" role="listitem">
              <span className="stat-number">850+</span>
              <span className="stat-label">Verifications Done</span>
            </div>
            <div className="stat-sep" aria-hidden="true"></div>
            <div className="hero-stat" role="listitem">
              <span className="stat-number">100%</span>
              <span className="stat-label">Tamper-Proof</span>
            </div>
          </div>
        </div>
      </section>

      {/* ── How It Works ── */}
      <section id="how-it-works" className="how-section">
        <div className="container">
          <div className="section-header">
            <span className="section-tag">How It Works</span>
            <h2>Three Simple Steps</h2>
            <p>
              From issuance to verification, the entire process is transparent,
              secure, and takes only seconds.
            </p>
          </div>

          <div className="steps-grid">
            {steps.map((step, i) => (
              <div className="step-card glass-card" key={i}>
                <div className="step-number">{step.number}</div>
                <div className="step-icon" aria-hidden="true">{step.icon}</div>
                <h3 className="step-title">{step.title}</h3>
                <p className="step-desc">{step.desc}</p>
                {i < steps.length - 1 && (
                  <div className="step-arrow" aria-hidden="true">→</div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Features ── */}
      <section id="features" className="features-section">
        <div className="container">
          <div className="section-header">
            <span className="section-tag">Why CertChain</span>
            <h2>Built for Trust</h2>
            <p>
              Every design decision prioritises security, transparency, and ease of use
              for institutions and verifiers alike.
            </p>
          </div>

          <div className="features-grid">
            {features.map((f, i) => (
              <div className="feature-card glass-card" key={i}>
                <div className="feature-icon" aria-hidden="true">{f.icon}</div>
                <h3 className="feature-title">{f.title}</h3>
                <p className="feature-desc">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA Banner ── */}
      <section id="cta" className="cta-section">
        <div className="container">
          <div className="cta-card glass-card">
            <div className="cta-orb" aria-hidden="true"></div>
            <div className="cta-content">
              <h2>Ready to Get Started?</h2>
              <p>
                Verify an existing certificate in seconds, or issue a new one on
                the blockchain today.
              </p>
              <div className="cta-actions">
                <Link to="/verify" id="cta-verify-btn" className="btn-primary">
                  🔍 Verify Now
                </Link>
                <Link to="/issue" id="cta-issue-btn" className="btn-secondary">
                  📜 Issue Certificate
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}

export default Home
