/**
 * StatusBadge — displays a coloured pill based on certificate status.
 *
 * status: 'valid' | 'revoked' | 'invalid' | 'pending' | 'not_found'
 */
function StatusBadge({ status = 'pending' }) {
  const config = {
    valid:     { label: '✓ Verified',   cls: 'badge--valid'     },
    revoked:   { label: '✕ Revoked',    cls: 'badge--revoked'   },
    invalid:   { label: '✕ Invalid',    cls: 'badge--invalid'   },
    pending:   { label: '⋯ Pending',    cls: 'badge--pending'   },
    not_found: { label: '? Not Found',  cls: 'badge--not-found' },
  }

  const { label, cls } = config[status] ?? config.pending

  return (
    <span className={`status-badge ${cls}`} role="status" aria-label={`Status: ${label}`}>
      {label}
    </span>
  )
}

export default StatusBadge
