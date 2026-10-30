import { useEffect, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { API_BASE_URL } from "../utils/api";
import "./Dashboard.css";

function formatDate(dateString) {
  if (!dateString) return "—";
  const date = new Date(dateString.includes("T") ? dateString : `${dateString}T00:00:00`);
  if (Number.isNaN(date.getTime())) return dateString;
  return date.toLocaleDateString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

function formatDateTime(instantString) {
  if (!instantString) return "—";
  const date = new Date(instantString);
  if (Number.isNaN(date.getTime())) return instantString;
  return date.toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function Dashboard() {
  const { user, isLoggedIn, isAdmin, getAuthHeaders } = useAuth();

  // Active Tab
  const [activeTab, setActiveTab] = useState("certificates"); // "certificates" | "activity" | "issuers"

  // Summary Metrics
  const [metrics, setMetrics] = useState({
    totalCertificates: 0,
    validCertificates: 0,
    revokedCertificates: 0,
    verificationAttempts: 0,
    tamperingDetections: 0,
    totalIssuers: 0,
  });
  const [metricsLoading, setMetricsLoading] = useState(true);

  // Certificates Table State
  const [certificates, setCertificates] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [tableLoading, setTableLoading] = useState(true);
  const [tableError, setTableError] = useState("");

  // Audit Logs State
  const [auditLogs, setAuditLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);

  // Issuers State
  const [issuers, setIssuers] = useState([]);
  const [issuersLoading, setIssuersLoading] = useState(false);
  const [showAddIssuerModal, setShowAddIssuerModal] = useState(false);
  const [issuerForm, setIssuerForm] = useState({
    name: "",
    email: "",
    password: "",
    institution: "",
  });
  const [issuerFormError, setIssuerFormError] = useState("");
  const [issuerFormSubmitting, setIssuerFormSubmitting] = useState(false);

  // Revocation Modal State
  const [revokeTarget, setRevokeTarget] = useState(null);
  const [revokeLoading, setRevokeLoading] = useState(false);
  const [revokeError, setRevokeError] = useState("");

  // Detail View Modal State
  const [detailTarget, setDetailTarget] = useState(null);

  // Success Notification
  const [toastMessage, setToastMessage] = useState("");

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(""), 4000);
  };

  // Fetch Metrics
  const fetchMetrics = useCallback(async () => {
    try {
      setMetricsLoading(true);
      const res = await fetch(`${API_BASE_URL}/admin/metrics`, {
        headers: getAuthHeaders(),
      });
      if (res.ok) {
        const data = await res.json();
        setMetrics(data);
      }
    } catch (err) {
      console.error("Failed to fetch admin metrics:", err);
    } finally {
      setMetricsLoading(false);
    }
  }, [getAuthHeaders]);

  // Fetch Paginated Certificates
  const fetchCertificates = useCallback(async () => {
    try {
      setTableLoading(true);
      setTableError("");
      const params = new URLSearchParams({
        page: String(page),
        size: "10",
        status: statusFilter,
      });
      if (search.trim()) {
        params.append("search", search.trim());
      }

      const res = await fetch(`${API_BASE_URL}/admin/certificates?${params.toString()}`, {
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(`Failed to load certificates (HTTP ${res.status})`);
      }

      const data = await res.json();
      setCertificates(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      setTableError(err.message || "Failed to load certificate registry records.");
    } finally {
      setTableLoading(false);
    }
  }, [page, search, statusFilter, getAuthHeaders]);

  // Fetch Audit Logs
  const fetchAuditLogs = useCallback(async () => {
    try {
      setLogsLoading(true);
      const res = await fetch(`${API_BASE_URL}/admin/audit-logs?limit=30`, {
        headers: getAuthHeaders(),
      });
      if (res.ok) {
        const data = await res.json();
        setAuditLogs(data || []);
      }
    } catch (err) {
      console.error("Failed to load audit logs:", err);
    } finally {
      setLogsLoading(false);
    }
  }, [getAuthHeaders]);

  // Fetch Issuers
  const fetchIssuers = useCallback(async () => {
    try {
      setIssuersLoading(true);
      const res = await fetch(`${API_BASE_URL}/admin/issuers`, {
        headers: getAuthHeaders(),
      });
      if (res.ok) {
        const data = await res.json();
        setIssuers(data || []);
      }
    } catch (err) {
      console.error("Failed to load issuers:", err);
    } finally {
      setIssuersLoading(false);
    }
  }, [getAuthHeaders]);

  // Initial load
  useEffect(() => {
    if (isLoggedIn && isAdmin) {
      fetchMetrics();
      fetchCertificates();
    }
  }, [isLoggedIn, isAdmin, fetchMetrics, fetchCertificates]);

  // Tab switch effect
  useEffect(() => {
    if (activeTab === "activity") {
      fetchAuditLogs();
    } else if (activeTab === "issuers") {
      fetchIssuers();
    }
  }, [activeTab, fetchAuditLogs, fetchIssuers]);

  // Handle Search Submit
  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    fetchCertificates();
  };

  // Handle Revoke Action
  const handleConfirmRevoke = async () => {
    if (!revokeTarget) return;
    try {
      setRevokeLoading(true);
      setRevokeError("");

      const res = await fetch(`${API_BASE_URL}/certificates/revoke/${revokeTarget.certificateId}`, {
        method: "POST",
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || `Revocation failed with status ${res.status}`);
      }

      showToast(`Certificate ${revokeTarget.certificateId} was revoked successfully on blockchain.`);
      setRevokeTarget(null);
      fetchMetrics();
      fetchCertificates();
    } catch (err) {
      setRevokeError(err.message || "Failed to execute on-chain revocation.");
    } finally {
      setRevokeLoading(false);
    }
  };

  // Handle Add Issuer
  const handleCreateIssuer = async (e) => {
    e.preventDefault();
    if (!issuerForm.name || !issuerForm.email || !issuerForm.password || !issuerForm.institution) {
      setIssuerFormError("All fields are required.");
      return;
    }
    if (issuerForm.password.length < 8) {
      setIssuerFormError("Password must be at least 8 characters.");
      return;
    }

    try {
      setIssuerFormSubmitting(true);
      setIssuerFormError("");

      const res = await fetch(`${API_BASE_URL}/admin/issuers`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...getAuthHeaders(),
        },
        body: JSON.stringify(issuerForm),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to register issuer account.");
      }

      showToast(`Issuer account for ${issuerForm.name} created successfully.`);
      setShowAddIssuerModal(false);
      setIssuerForm({ name: "", email: "", password: "", institution: "" });
      fetchIssuers();
      fetchMetrics();
    } catch (err) {
      setIssuerFormError(err.message || "Could not create issuer account.");
    } finally {
      setIssuerFormSubmitting(false);
    }
  };

  // Protected route check
  if (!isLoggedIn) {
    return (
      <div className="dashboard-page auth-restricted">
        <div className="restricted-card glass-card">
          <div className="restricted-icon">🔒</div>
          <h2>Authentication Required</h2>
          <p>Please log in with administrator credentials to access the CertChain Management Dashboard.</p>
          <Link to="/login" className="btn-primary">Go to Login</Link>
        </div>
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="dashboard-page auth-restricted">
        <div className="restricted-card glass-card">
          <div className="restricted-icon">⚠️</div>
          <h2>Access Denied</h2>
          <p>Your account ({user?.email}) has role <strong>{user?.role}</strong>. Administrative privileges are required to view the administrative console.</p>
          <div className="restricted-actions">
            <Link to="/issue" className="btn-primary">Go to Certificate Issuance</Link>
            <Link to="/verify" className="btn-secondary">Go to Verification</Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      <div className="dashboard-container">

        {/* Toast Notification */}
        {toastMessage && (
          <div className="dashboard-toast" role="alert">
            <span className="toast-icon">✓</span>
            <span>{toastMessage}</span>
          </div>
        )}

        {/* Dashboard Header */}
        <header className="dashboard-header">
          <div className="dashboard-header-text">
            <div className="admin-badge-row">
              <span className="badge-admin">System Administrator</span>
              <span className="admin-user-tag">{user?.name} ({user?.email})</span>
            </div>
            <h1 className="dashboard-title">Administrative Console</h1>
            <p className="dashboard-subtitle">
              Cryptographic integrity oversight, authoritative certificate lifecycle management, and real-time audit intelligence.
            </p>
          </div>
          <div className="dashboard-header-actions">
            <Link to="/issue" className="btn-primary">
              <span aria-hidden="true">+</span> Issue New Certificate
            </Link>
            <Link to="/verify" className="btn-secondary">
              Verify Certificate
            </Link>
          </div>
        </header>

        {/* Summary Metric Cards */}
        <section className="metrics-grid" aria-label="System Metrics">
          <div className="metric-card glass-card metric-total">
            <div className="metric-header">
              <span className="metric-title">Total Registered</span>
              <span className="metric-icon" aria-hidden="true">📜</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.totalCertificates}
            </div>
            <div className="metric-subtext">Cumulative issuance volume</div>
          </div>

          <div className="metric-card glass-card metric-valid">
            <div className="metric-header">
              <span className="metric-title">Active & Valid</span>
              <span className="metric-icon" aria-hidden="true">✓</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.validCertificates}
            </div>
            <div className="metric-subtext">Cryptographically verifiable on-chain</div>
          </div>

          <div className="metric-card glass-card metric-revoked">
            <div className="metric-header">
              <span className="metric-title">Revoked Certificates</span>
              <span className="metric-icon" aria-hidden="true">✕</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.revokedCertificates}
            </div>
            <div className="metric-subtext">Invalidated on smart contract</div>
          </div>

          <div className="metric-card glass-card metric-verify">
            <div className="metric-header">
              <span className="metric-title">Verification Attempts</span>
              <span className="metric-icon" aria-hidden="true">🔍</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.verificationAttempts}
            </div>
            <div className="metric-subtext">Public & in-process PDF checks</div>
          </div>

          <div className={`metric-card glass-card metric-tamper ${metrics.tamperingDetections > 0 ? "has-tamper" : ""}`}>
            <div className="metric-header">
              <span className="metric-title">Tampering Flags</span>
              <span className="metric-icon" aria-hidden="true">⚠️</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.tamperingDetections}
            </div>
            <div className="metric-subtext">
              {metrics.tamperingDetections > 0 ? "Discrepancies caught by engine" : "No tampering recorded"}
            </div>
          </div>

          <div className="metric-card glass-card metric-issuers">
            <div className="metric-header">
              <span className="metric-title">Authorized Issuers</span>
              <span className="metric-icon" aria-hidden="true">🏛️</span>
            </div>
            <div className="metric-value">
              {metricsLoading ? <span className="skeleton-num">...</span> : metrics.totalIssuers}
            </div>
            <div className="metric-subtext">Accredited institutional actors</div>
          </div>
        </section>

        {/* Tab Navigation */}
        <div className="dashboard-tabs-bar" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "certificates"}
            className={`dashboard-tab ${activeTab === "certificates" ? "active" : ""}`}
            onClick={() => setActiveTab("certificates")}
          >
            📋 Certificate Lifecycle Registry ({totalElements})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "activity"}
            className={`dashboard-tab ${activeTab === "activity" ? "active" : ""}`}
            onClick={() => setActiveTab("activity")}
          >
            ⚡ Live Activity & Audit Log
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "issuers"}
            className={`dashboard-tab ${activeTab === "issuers" ? "active" : ""}`}
            onClick={() => setActiveTab("issuers")}
          >
            👥 Issuer Management ({metrics.totalIssuers})
          </button>
        </div>

        {/* ── TAB 1: CERTIFICATES TABLE ── */}
        {activeTab === "certificates" && (
          <section className="dashboard-content-panel glass-card">
            {/* Filter and Search Toolbar */}
            <div className="table-toolbar">
              <form onSubmit={handleSearchSubmit} className="search-box">
                <input
                  type="text"
                  placeholder="Search by Cert ID, Student Name, Roll No..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="search-input"
                />
                <button type="submit" className="search-btn" title="Search">
                  🔍
                </button>
                {search && (
                  <button
                    type="button"
                    className="clear-search-btn"
                    onClick={() => {
                      setSearch("");
                      setPage(0);
                    }}
                  >
                    ✕
                  </button>
                )}
              </form>

              <div className="filter-controls">
                <label className="filter-label">
                  Status:
                  <select
                    value={statusFilter}
                    onChange={(e) => {
                      setStatusFilter(e.target.value);
                      setPage(0);
                    }}
                    className="filter-select"
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="VALID">Valid Only</option>
                    <option value="REVOKED">Revoked Only</option>
                  </select>
                </label>
                <button
                  type="button"
                  onClick={() => {
                    fetchCertificates();
                    fetchMetrics();
                  }}
                  className="refresh-btn"
                  title="Refresh Table"
                >
                  ↻ Refresh
                </button>
              </div>
            </div>

            {tableError && (
              <div className="table-error-banner" role="alert">
                ⚠️ {tableError}
              </div>
            )}

            {/* Table */}
            <div className="table-responsive">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Certificate ID</th>
                    <th>Student Name</th>
                    <th>Roll Number</th>
                    <th>Course & Institution</th>
                    <th>Issue Date</th>
                    <th>Status</th>
                    <th>Blockchain Tx</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {tableLoading ? (
                    <tr>
                      <td colSpan="8" className="table-loading-cell">
                        <div className="spinner" /> Loading certificate records...
                      </td>
                    </tr>
                  ) : certificates.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="table-empty-cell">
                        No certificates found matching the search criteria.
                      </td>
                    </tr>
                  ) : (
                    certificates.map((cert) => {
                      const isValid = cert.status === "VALID";
                      return (
                        <tr key={cert.certificateId} className={`row-status-${cert.status?.toLowerCase()}`}>
                          <td className="cert-id-cell">
                            <code>{cert.certificateId}</code>
                          </td>
                          <td className="student-name-cell">
                            <strong>{cert.studentName}</strong>
                          </td>
                          <td>{cert.studentRollNumber || "—"}</td>
                          <td>
                            <div className="course-text">{cert.course}</div>
                            <div className="institution-subtext">{cert.institution}</div>
                          </td>
                          <td>{formatDate(cert.issueDate)}</td>
                          <td>
                            <span className={`status-badge badge-${cert.status?.toLowerCase()}`}>
                              {isValid ? "✓ VALID" : "✕ REVOKED"}
                            </span>
                          </td>
                          <td className="tx-cell">
                            {cert.transactionHash ? (
                              <code title={cert.transactionHash} className="tx-truncated">
                                {cert.transactionHash.substring(0, 10)}...
                              </code>
                            ) : (
                              <span className="text-muted">Pending</span>
                            )}
                          </td>
                          <td className="actions-cell">
                            <button
                              type="button"
                              className="btn-action btn-view"
                              onClick={() => setDetailTarget(cert)}
                              title="View Certificate Details"
                            >
                              Inspect
                            </button>
                            {isValid && (
                              <button
                                type="button"
                                className="btn-action btn-revoke"
                                onClick={() => {
                                  setRevokeTarget(cert);
                                  setRevokeError("");
                                }}
                                title="Revoke Certificate"
                              >
                                Revoke
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="pagination-bar">
              <span className="pagination-info">
                Showing page <strong>{page + 1}</strong> of <strong>{totalPages || 1}</strong> ({totalElements} total records)
              </span>
              <div className="pagination-buttons">
                <button
                  type="button"
                  className="page-btn"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0 || tableLoading}
                >
                  ← Previous
                </button>
                <button
                  type="button"
                  className="page-btn"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1 || tableLoading}
                >
                  Next →
                </button>
              </div>
            </div>
          </section>
        )}

        {/* ── TAB 2: LIVE ACTIVITY FEED ── */}
        {activeTab === "activity" && (
          <section className="dashboard-content-panel glass-card">
            <div className="panel-header">
              <div>
                <h3 className="panel-title">System Audit & Verification Stream</h3>
                <p className="panel-subtitle">
                  Immutable chronological log of authentication, issuance, revocation, and document verification checks.
                </p>
              </div>
              <button type="button" onClick={fetchAuditLogs} className="refresh-btn">
                ↻ Refresh Stream
              </button>
            </div>

            {logsLoading ? (
              <div className="logs-loading">
                <div className="spinner" /> Loading audit intelligence stream...
              </div>
            ) : auditLogs.length === 0 ? (
              <div className="logs-empty">No audit events recorded yet.</div>
            ) : (
              <div className="audit-feed">
                {auditLogs.map((log) => {
                  let eventBadgeClass = "event-default";
                  if (log.eventType === "LOGIN_SUCCESS") eventBadgeClass = "event-login-success";
                  else if (log.eventType === "LOGIN_FAILED") eventBadgeClass = "event-login-fail";
                  else if (log.eventType === "CERT_ISSUED") eventBadgeClass = "event-issued";
                  else if (log.eventType === "CERT_REVOKED") eventBadgeClass = "event-revoked";
                  else if (log.eventType === "TAMPER_DETECTED") eventBadgeClass = "event-tamper";
                  else if (log.eventType === "VERIFY_ATTEMPT") eventBadgeClass = "event-verify";
                  else if (log.eventType === "ISSUER_CREATED") eventBadgeClass = "event-issuer";

                  return (
                    <div key={log.id} className="audit-card">
                      <div className="audit-card-header">
                        <div className="audit-badge-group">
                          <span className={`event-badge ${eventBadgeClass}`}>
                            {log.eventType}
                          </span>
                          <span className={`outcome-badge outcome-${log.status?.toLowerCase()}`}>
                            {log.status}
                          </span>
                        </div>
                        <span className="audit-timestamp">{formatDateTime(log.timestamp)}</span>
                      </div>
                      <div className="audit-card-body">
                        <div className="audit-actor">
                          <strong>Actor:</strong> <code>{log.actor}</code>
                          {log.ipAddress && <span className="audit-ip"> ({log.ipAddress})</span>}
                        </div>
                        {log.details && Object.keys(log.details).length > 0 && (
                          <div className="audit-details">
                            {Object.entries(log.details).map(([k, v]) => (
                              <span key={k} className="audit-detail-pill">
                                <strong>{k}:</strong> {String(v)}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        )}

        {/* ── TAB 3: ISSUER MANAGEMENT ── */}
        {activeTab === "issuers" && (
          <section className="dashboard-content-panel glass-card">
            <div className="panel-header">
              <div>
                <h3 className="panel-title">Institutional Certificate Issuers</h3>
                <p className="panel-subtitle">
                  Manage accounts authorized to issue authentic academic certificates onto the blockchain.
                </p>
              </div>
              <button
                type="button"
                className="btn-primary"
                onClick={() => {
                  setShowAddIssuerModal(true);
                  setIssuerFormError("");
                }}
              >
                + Register New Issuer
              </button>
            </div>

            {issuersLoading ? (
              <div className="logs-loading">
                <div className="spinner" /> Loading registered issuers...
              </div>
            ) : issuers.length === 0 ? (
              <div className="logs-empty">No institutional issuers registered yet.</div>
            ) : (
              <div className="table-responsive">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Issuer Name</th>
                      <th>Email Address</th>
                      <th>Affiliated Institution</th>
                      <th>Role</th>
                    </tr>
                  </thead>
                  <tbody>
                    {issuers.map((iss) => (
                      <tr key={iss.id || iss.email}>
                        <td><strong>{iss.name}</strong></td>
                        <td><code>{iss.email}</code></td>
                        <td>{iss.institution || "—"}</td>
                        <td><span className="status-badge badge-valid">{iss.role}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}

        {/* ── MODAL: CONFIRM REVOCATION ── */}
        {revokeTarget && (
          <div className="modal-overlay" role="dialog" aria-modal="true">
            <div className="modal-card modal-danger glass-card">
              <div className="modal-header">
                <h3 className="modal-title">⚠️ Confirm Certificate Revocation</h3>
                <button
                  type="button"
                  className="modal-close"
                  onClick={() => setRevokeTarget(null)}
                  disabled={revokeLoading}
                >
                  ✕
                </button>
              </div>
              <div className="modal-body">
                <p className="danger-alert-text">
                  This action writes an irreversible revocation transaction to the smart contract on the blockchain
                  and updates the authoritative MongoDB record. Once revoked, the certificate will fail verification worldwide.
                </p>
                <div className="revoke-summary-box">
                  <div><span>Certificate ID:</span> <strong>{revokeTarget.certificateId}</strong></div>
                  <div><span>Student Name:</span> <strong>{revokeTarget.studentName}</strong></div>
                  <div><span>Course:</span> {revokeTarget.course}</div>
                  <div><span>Institution:</span> {revokeTarget.institution}</div>
                </div>
                {revokeError && <div className="modal-error-banner">⚠️ {revokeError}</div>}
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setRevokeTarget(null)}
                  disabled={revokeLoading}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn-danger"
                  onClick={handleConfirmRevoke}
                  disabled={revokeLoading}
                >
                  {revokeLoading ? "Executing On-Chain Revocation..." : "Confirm & Revoke on Blockchain"}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── MODAL: CERTIFICATE DETAIL INSPECT ── */}
        {detailTarget && (
          <div className="modal-overlay" role="dialog" aria-modal="true">
            <div className="modal-card modal-detail glass-card">
              <div className="modal-header">
                <h3 className="modal-title">Certificate Audit Inspection</h3>
                <button
                  type="button"
                  className="modal-close"
                  onClick={() => setDetailTarget(null)}
                >
                  ✕
                </button>
              </div>
              <div className="modal-body">
                <div className="detail-grid">
                  <div>
                    <span className="detail-label">Certificate ID</span>
                    <strong className="detail-val"><code>{detailTarget.certificateId}</code></strong>
                  </div>
                  <div>
                    <span className="detail-label">Lifecycle Status</span>
                    <span className={`status-badge badge-${detailTarget.status?.toLowerCase()}`}>
                      {detailTarget.status}
                    </span>
                  </div>
                  <div>
                    <span className="detail-label">Student Name</span>
                    <strong className="detail-val">{detailTarget.studentName}</strong>
                  </div>
                  <div>
                    <span className="detail-label">Roll Number</span>
                    <span className="detail-val">{detailTarget.studentRollNumber || "—"}</span>
                  </div>
                  <div>
                    <span className="detail-label">Degree / Course</span>
                    <span className="detail-val">{detailTarget.course}</span>
                  </div>
                  <div>
                    <span className="detail-label">Awarding Institution</span>
                    <span className="detail-val">{detailTarget.institution}</span>
                  </div>
                  <div>
                    <span className="detail-label">Grade / Honors</span>
                    <span className="detail-val">{detailTarget.grade || "—"}</span>
                  </div>
                  <div>
                    <span className="detail-label">Date of Issuance</span>
                    <span className="detail-val">{formatDate(detailTarget.issueDate)}</span>
                  </div>
                </div>

                <div className="hash-block">
                  <span className="detail-label">Canonical SHA-256 Content Hash</span>
                  <code className="code-break">{detailTarget.certificateHash}</code>
                </div>

                <div className="hash-block">
                  <span className="detail-label">Blockchain Transaction Hash</span>
                  <code className="code-break">{detailTarget.transactionHash || "Not recorded"}</code>
                </div>
              </div>
              <div className="modal-footer">
                <Link
                  to={`/verify?id=${encodeURIComponent(detailTarget.certificateId)}`}
                  className="btn-primary"
                  target="_blank"
                >
                  Open Public Verification Page ↗
                </Link>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setDetailTarget(null)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── MODAL: CREATE ISSUER ── */}
        {showAddIssuerModal && (
          <div className="modal-overlay" role="dialog" aria-modal="true">
            <div className="modal-card modal-form glass-card">
              <div className="modal-header">
                <h3 className="modal-title">Register Accredited Issuer</h3>
                <button
                  type="button"
                  className="modal-close"
                  onClick={() => setShowAddIssuerModal(false)}
                >
                  ✕
                </button>
              </div>
              <form onSubmit={handleCreateIssuer}>
                <div className="modal-body">
                  <p className="modal-help-text">
                    Create an institutional issuer account. Issuers can authenticate and issue official certificates.
                  </p>
                  {issuerFormError && <div className="modal-error-banner">⚠️ {issuerFormError}</div>}
                  <div className="form-group">
                    <label>Full Name / Department</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Office of Academic Registrar"
                      value={issuerForm.name}
                      onChange={(e) => setIssuerForm({ ...issuerForm, name: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Official Institutional Email</label>
                    <input
                      type="email"
                      required
                      placeholder="e.g. registrar@iitd.ac.in"
                      value={issuerForm.email}
                      onChange={(e) => setIssuerForm({ ...issuerForm, email: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Initial Password (min 8 chars)</label>
                    <input
                      type="password"
                      required
                      placeholder="••••••••"
                      value={issuerForm.password}
                      onChange={(e) => setIssuerForm({ ...issuerForm, password: e.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label>Institution Name</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. Indian Institute of Technology Delhi"
                      value={issuerForm.institution}
                      onChange={(e) => setIssuerForm({ ...issuerForm, institution: e.target.value })}
                    />
                  </div>
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => setShowAddIssuerModal(false)}
                    disabled={issuerFormSubmitting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={issuerFormSubmitting}
                  >
                    {issuerFormSubmitting ? "Registering..." : "Create Issuer Account"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}

export default Dashboard;
