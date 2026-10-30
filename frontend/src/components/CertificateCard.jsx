import StatusBadge from './StatusBadge'
import './CertificateCard.css'

/**
 * Renders a full certificate detail card.
 *
 * Props:
 *  - certificate: { id, holderName, course, institution, issueDate, grade, txHash, status }
 *  - compact: boolean — show a narrower version (for tables / lists)
 */
function CertificateCard({ certificate, compact = false }) {
  if (!certificate) return null

  const {
    id         = '—',
    holderName = '—',
    course     = '—',
    institution= '—',
    issueDate  = '—',
    grade      = '—',
    txHash     = '',
    status     = 'valid',
  } = certificate

  const shortHash = txHash
    ? `${txHash.slice(0, 10)}...${txHash.slice(-8)}`
    : '—'

  if (compact) {
    return (
      <div className="cert-card cert-card--compact glass-card">
        <div className="cert-card-compact-top">
          <div>
            <p className="cert-holder">{holderName}</p>
            <p className="cert-course">{course}</p>
          </div>
          <StatusBadge status={status} />
        </div>
        <div className="cert-card-compact-meta">
          <span className="cert-meta-item">🏛 {institution}</span>
          <span className="cert-meta-item">📅 {issueDate}</span>
          <span className="cert-meta-item cert-id">ID: {id}</span>
        </div>
      </div>
    )
  }

  return (
    <div className="cert-card glass-card" role="article" aria-label={`Certificate for ${holderName}`}>
      {/* Header strip */}
      <div className="cert-card-header">
        <div className="cert-card-logo" aria-hidden="true">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"
                  stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                  stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <div className="cert-card-title-group">
          <p className="cert-card-label">Certificate of Completion</p>
          <p className="cert-id-display">ID: {id}</p>
        </div>
        <StatusBadge status={status} />
      </div>

      {/* Body */}
      <div className="cert-card-body">
        <div className="cert-field-grid">
          <div className="cert-field">
            <span className="cert-field-label">Holder Name</span>
            <span className="cert-field-value">{holderName}</span>
          </div>
          <div className="cert-field">
            <span className="cert-field-label">Course / Programme</span>
            <span className="cert-field-value">{course}</span>
          </div>
          <div className="cert-field">
            <span className="cert-field-label">Issuing Institution</span>
            <span className="cert-field-value">{institution}</span>
          </div>
          <div className="cert-field">
            <span className="cert-field-label">Date Issued</span>
            <span className="cert-field-value">{issueDate}</span>
          </div>
          {grade && grade !== '—' && (
            <div className="cert-field">
              <span className="cert-field-label">Grade / Honours</span>
              <span className="cert-field-value cert-grade">{grade}</span>
            </div>
          )}
        </div>
      </div>

      {/* Footer — blockchain info */}
      {txHash && (
        <div className="cert-card-footer">
          <span className="cert-chain-label">⛓ Blockchain Tx Hash</span>
          <code className="cert-hash" title={txHash}>{shortHash}</code>
          <a
            href={`https://sepolia.etherscan.io/tx/${txHash}`}
            target="_blank"
            rel="noreferrer"
            className="cert-etherscan-link"
            aria-label="View on Etherscan"
          >
            View on Etherscan ↗
          </a>
        </div>
      )}
    </div>
  )
}

export default CertificateCard
