import { useState, useEffect, useRef, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import CertificateModal from "../components/CertificateModal";
import { downloadCertificatePDF } from "../utils/pdfGenerator";
import "./Verify.css";

import { API_BASE_URL as BASE_URL } from "../utils/api";
const API_BASE_URL = `${BASE_URL}/certificates`;

function Verify() {
  const [searchParams] = useSearchParams();
  const [activeTab, setActiveTab] = useState("upload"); // "upload" | "id" | "infosys"

  // ID search state
  const [certificateId, setCertificateId] = useState("");

  // File upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [isDragging, setIsDragging] = useState(false);

  // Infosys verification state
  const [selectedInfosysFile, setSelectedInfosysFile] = useState(null);
  const [infosysDragging, setInfosysDragging] = useState(false);
  const [infosysResult, setInfosysResult] = useState(null);
  const [infosysLoading, setInfosysLoading] = useState(false);
  const [infosysError, setInfosysError] = useState("");
  const [showRawJson, setShowRawJson] = useState(false);
  const infosysFileInputRef = useRef(null);

  // Common verification state
  const [certificate, setCertificate] = useState(null);
  const [verificationResult, setVerificationResult] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);

  // Guard against repeated automatic verification from re-renders
  const lastVerifiedIdRef = useRef(null);
  const fileInputRef = useRef(null);

  // Verification by Certificate ID
  const executeIdVerification = useCallback(async (idToVerify) => {
    const id = (idToVerify || "").trim();

    if (!id) {
      setError("Please enter a valid certificate ID.");
      setCertificate(null);
      setVerificationResult(null);
      return;
    }

    setLoading(true);
    setError("");
    setCertificate(null);
    setVerificationResult(null);

    try {
      const response = await fetch(
        `${API_BASE_URL}/verify/${encodeURIComponent(id)}`,
      );

      const data = await response.json();

      if (response.status === 404) {
        setVerificationResult({
          verificationResult: "NOT_FOUND",
          message: data.message || `Certificate ID ${id} was not found in the institution registry.`,
          blockchainVerified: false,
          blockchainNote: "No matching record exists in the CertChain database or blockchain registry.",
        });
        setCertificate(null);
        return;
      }

      if (!response.ok) {
        throw new Error(data.message || "Unable to verify this certificate.");
      }

      setVerificationResult(data);
      setCertificate(data.certificate || null);
    } catch (err) {
      setError(
        err.message ||
          "Could not connect to the server. Make sure the backend is running.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  // Verification by PDF Upload
  const executePdfUploadVerification = async (fileToUpload) => {
    if (!fileToUpload) {
      setError("Please select a PDF certificate to upload.");
      return;
    }

    if (fileToUpload.size > 10 * 1024 * 1024) {
      setError("The selected file exceeds the 10 MB maximum upload limit.");
      return;
    }

    if (
      fileToUpload.type &&
      fileToUpload.type !== "application/pdf" &&
      !fileToUpload.name.toLowerCase().endsWith(".pdf")
    ) {
      setError("Only PDF documents (.pdf) are supported for certificate verification.");
      return;
    }

    setLoading(true);
    setError("");
    setCertificate(null);
    setVerificationResult(null);

    try {
      const formData = new FormData();
      formData.append("file", fileToUpload);

      const response = await fetch(`${API_BASE_URL}/verify/upload`, {
        method: "POST",
        body: formData,
      });

      const data = await response.json();

      if (!response.ok && response.status !== 400 && response.status !== 404) {
        throw new Error(data.message || "Server error while verifying PDF.");
      }

      setVerificationResult(data);
      setCertificate(data.certificate || null);
    } catch (err) {
      setError(
        err.message ||
          "Could not connect to the verification server. Please ensure the backend is active.",
      );
    } finally {
      setLoading(false);
    }
  };

  // URL Auto-verify when opened with /verify?id=CERT-...
  useEffect(() => {
    const queryId = searchParams.get("id");
    if (!queryId) return;

    const trimmedId = queryId.trim();
    if (!trimmedId) return;

    if (lastVerifiedIdRef.current === trimmedId) return;
    lastVerifiedIdRef.current = trimmedId;

    setActiveTab("id");
    setCertificateId(trimmedId);
    executeIdVerification(trimmedId);
  }, [searchParams, executeIdVerification]);

  const handleIdSubmit = (event) => {
    event?.preventDefault();
    const id = certificateId.trim();
    lastVerifiedIdRef.current = id;
    executeIdVerification(id);
  };

  const handleFileUploadSubmit = (event) => {
    event?.preventDefault();
    executePdfUploadVerification(selectedFile);
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setError("");
      setVerificationResult(null);
      setCertificate(null);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      if (!file.name.toLowerCase().endsWith(".pdf")) {
        setError("Please drop a valid .pdf certificate file.");
        return;
      }
      setSelectedFile(file);
      setError("");
      setVerificationResult(null);
      setCertificate(null);
    }
  };

  const clearIdSearch = () => {
    setCertificateId("");
    setCertificate(null);
    setVerificationResult(null);
    setError("");
    lastVerifiedIdRef.current = null;
  };

  const clearFileSelection = () => {
    setSelectedFile(null);
    setCertificate(null);
    setVerificationResult(null);
    setError("");
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // ── Infosys Springboard Verification ───────────────────────────
  const executeInfosysVerification = async (fileToUpload) => {
    if (!fileToUpload) {
      setInfosysError("Please select an Infosys Springboard certificate PDF.");
      return;
    }

    if (fileToUpload.size > 10 * 1024 * 1024) {
      setInfosysError("The selected file exceeds the 10 MB maximum upload limit.");
      return;
    }

    if (
      fileToUpload.type &&
      fileToUpload.type !== "application/pdf" &&
      !fileToUpload.name.toLowerCase().endsWith(".pdf")
    ) {
      setInfosysError("Only PDF documents (.pdf) are supported for verification.");
      return;
    }

    setInfosysLoading(true);
    setInfosysError("");
    setInfosysResult(null);

    try {
      const formData = new FormData();
      formData.append("file", fileToUpload);

      const response = await fetch(`${API_BASE_URL}/verify/infosys`, {
        method: "POST",
        body: formData,
      });

      const data = await response.json();

      if (!response.ok && response.status !== 400) {
        throw new Error(data.message || "Server error while verifying Infosys certificate.");
      }

      setInfosysResult(data);
    } catch (err) {
      setInfosysError(
        err.message ||
          "Could not connect to the verification server. Ensure the backend is active."
      );
    } finally {
      setInfosysLoading(false);
    }
  };

  const handleInfosysSubmit = (event) => {
    event?.preventDefault();
    executeInfosysVerification(selectedInfosysFile);
  };

  const handleInfosysFileChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedInfosysFile(file);
      setInfosysError("");
      setInfosysResult(null);
    }
  };

  const clearInfosysFile = () => {
    setSelectedInfosysFile(null);
    setInfosysResult(null);
    setInfosysError("");
    if (infosysFileInputRef.current) {
      infosysFileInputRef.current.value = "";
    }
  };

  const getInfosysBadge = () => {
    if (!infosysResult) return null;
    const r = infosysResult.result;
    if (r === "VERIFIED") {
      return {
        icon: "✅",
        label: "VERIFIED & AUTHENTIC",
        heading: "Infosys Springboard Certificate Verified",
        className: "status-verified",
      };
    }
    if (r === "TAMPERING_DETECTED") {
      return {
        icon: "⚠️",
        label: "TAMPERING DETECTED",
        heading: "Document Tampering Detected",
        className: "status-tampered",
      };
    }
    if (r === "NEEDS_REVIEW") {
      return {
        icon: "🔍",
        label: "NEEDS REVIEW",
        heading: "Manual Verification Required",
        className: "status-review",
      };
    }
    if (r === "INVALID_CREDENTIAL" || r === "INVALID_SIGNATURE") {
      return {
        icon: "❌",
        label: "INVALID CREDENTIAL",
        heading: "Invalid Credential or Corrupted Proof",
        className: "status-tampered",
      };
    }
    return {
      icon: "❓",
      label: "UNABLE TO VERIFY",
      heading: "Verification Inconclusive",
      className: "status-unable",
    };
  };

  const handleDownloadPdf = async () => {
    if (!certificate) return;
    setDownloading(true);
    try {
      await downloadCertificatePDF(certificate);
    } catch (err) {
      console.error("Failed to download PDF:", err);
    } finally {
      setDownloading(false);
    }
  };

  // Status mapping
  const status = verificationResult?.verificationResult;
  const isAuthentic =
    (status === "VERIFIED" || status === "VALID") &&
    verificationResult?.blockchainVerified === true;
  const isTampered = status === "TAMPERING_DETECTED" || status === "TAMPERED";
  const isRevoked = status === "REVOKED";
  const isNotFound = status === "NOT_FOUND";
  const isUnableToVerify =
    status === "UNABLE_TO_VERIFY" || status === "BLOCKCHAIN_UNAVAILABLE";
  const isNeedsReview = status === "NEEDS_REVIEW";

  const getStatusBadge = () => {
    if (isAuthentic) {
      return {
        icon: "✅",
        label: "VERIFIED & AUTHENTIC",
        heading: "Certificate Verified Authentic",
        className: "status-verified",
      };
    }
    if (isTampered) {
      return {
        icon: "⚠️",
        label: "TAMPERING DETECTED",
        heading: "Document Tampering Detected",
        className: "status-tampered",
      };
    }
    if (isRevoked) {
      return {
        icon: "🚫",
        label: "REVOKED",
        heading: "Certificate Has Been Revoked",
        className: "status-revoked",
      };
    }
    if (isNotFound) {
      return {
        icon: "❌",
        label: "NOT FOUND",
        heading: "Certificate Record Not Found",
        className: "status-notfound",
      };
    }
    if (isNeedsReview) {
      return {
        icon: "🔍",
        label: "NEEDS REVIEW",
        heading: "External Certificate — Manual Verification Required",
        className: "status-review",
      };
    }
    if (isUnableToVerify) {
      return {
        icon: "❓",
        label: "UNABLE TO VERIFY",
        heading: "Verification Inconclusive",
        className: "status-unable",
      };
    }
    return {
      icon: "❓",
      label: status || "UNKNOWN",
      heading: "Verification Status",
      className: "status-unable",
    };
  };

  const badge = getStatusBadge();

  return (
    <div className="verify-page">
      <div className="verify-bg" aria-hidden="true">
        <div className="vorb vorb-1" />
        <div className="vorb vorb-2" />
      </div>

      <div className="container verify-container">
        <header className="verify-header">
          <span className="section-label">CERTIFICATE AUTHENTICATION</span>
          <h1 className="verify-title">Verify a Certificate</h1>
          <p className="verify-subtitle">
            Upload an official PDF certificate to inspect its cryptographic integrity
            or verify directly by Certificate ID against the Ethereum blockchain registry.
          </p>
        </header>

        {/* Tab switchers */}
        <div className="verify-tabs glass-card" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "upload"}
            className={`verify-tab ${activeTab === "upload" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("upload");
              setError("");
            }}
          >
            📄 CertChain PDF
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "id"}
            className={`verify-tab ${activeTab === "id" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("id");
              setError("");
            }}
          >
            🔎 CertChain ID
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "infosys"}
            className={`verify-tab ${activeTab === "infosys" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("infosys");
              setInfosysError("");
            }}
          >
            🌐 Infosys Springboard
          </button>
        </div>

        {/* Mode 1: PDF File Upload */}
        {activeTab === "upload" && (
          <form
            className="verify-form glass-card"
            onSubmit={handleFileUploadSubmit}
            aria-label="Upload PDF Certificate Form"
          >
            <div
              className={`dropzone ${isDragging ? "dropzone-active" : ""} ${
                selectedFile ? "dropzone-filled" : ""
              }`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  fileInputRef.current?.click();
                }
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="application/pdf,.pdf"
                className="file-input-hidden"
                onChange={handleFileChange}
                aria-label="Upload PDF file"
              />

              <div className="dropzone-content">
                <span className="dropzone-icon" aria-hidden="true">
                  {selectedFile ? "📑" : "📥"}
                </span>

                {selectedFile ? (
                  <div className="file-info">
                    <p className="file-name">{selectedFile.name}</p>
                    <p className="file-size">
                      {(selectedFile.size / 1024).toFixed(1)} KB — Ready to inspect
                    </p>
                  </div>
                ) : (
                  <div>
                    <p className="dropzone-prompt">
                      Drag & drop your certificate PDF here, or{" "}
                      <span className="browse-link">browse</span>
                    </p>
                    <p className="dropzone-limits">
                      Maximum upload size: 10 MB | In-process tamper analysis
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className="upload-actions-row">
              {selectedFile && (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={clearFileSelection}
                >
                  ✕ Remove File
                </button>
              )}

              <button
                type="submit"
                className="btn-primary verify-submit-btn"
                disabled={loading || !selectedFile}
              >
                {loading ? (
                  <>
                    <span className="spinner" aria-hidden="true" /> Analyzing
                    Document...
                  </>
                ) : (
                  "🛡 Verify Uploaded Certificate"
                )}
              </button>
            </div>

            {error && (
              <p className="verify-error" role="alert">
                {error}
              </p>
            )}
          </form>
        )}

        {/* Mode 2: Verify by Certificate ID */}
        {activeTab === "id" && (
          <form
            className="verify-form glass-card"
            onSubmit={handleIdSubmit}
            aria-label="Verify Certificate by ID Form"
          >
            <label htmlFor="certificate-id">Certificate ID</label>

            <div className="verify-input-row">
              <div className="verify-input-wrap">
                <span className="verify-input-icon" aria-hidden="true">
                  🔎
                </span>

                <input
                  id="certificate-id"
                  className="verify-input"
                  type="text"
                  placeholder="e.g. CERT-2026-18587"
                  value={certificateId}
                  onChange={(event) => setCertificateId(event.target.value)}
                  autoComplete="off"
                  aria-describedby={error ? "verify-error" : undefined}
                />

                {certificateId && (
                  <button
                    className="verify-clear-btn"
                    type="button"
                    onClick={clearIdSearch}
                    aria-label="Clear certificate ID"
                  >
                    ✕
                  </button>
                )}
              </div>

              <button
                className="btn-primary verify-submit"
                type="submit"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="spinner" /> Checking...
                  </>
                ) : (
                  "Verify"
                )}
              </button>
            </div>

            {error && (
              <p className="verify-error" id="verify-error" role="alert">
                {error}
              </p>
            )}

            <div className="verify-form-footer">
              <button
                type="button"
                className="demo-link"
                onClick={() => {
                  setCertificateId("CERT-2026-44779");
                  setCertificate(null);
                  setVerificationResult(null);
                  setError("");
                  lastVerifiedIdRef.current = null;
                }}
              >
                Try a sample certificate
              </button>

              <span className="verify-hint">
                Direct blockchain registry lookup
              </span>
            </div>
          </form>
        )}

        {/* Mode 3: Infosys Springboard Verification */}
        {activeTab === "infosys" && (
          <form
            className="verify-form glass-card"
            onSubmit={handleInfosysSubmit}
            aria-label="Verify Infosys Certificate Form"
          >
            <div className="infosys-tag">
              <span>✦</span> INFOSYS SPRINGBOARD W3C CREDENTIAL VERIFICATION
            </div>

            <div
              className={`dropzone ${infosysDragging ? "dropzone-active" : ""} ${
                selectedInfosysFile ? "dropzone-filled" : ""
              }`}
              onDragOver={(e) => {
                e.preventDefault();
                setInfosysDragging(true);
              }}
              onDragLeave={(e) => {
                e.preventDefault();
                setInfosysDragging(false);
              }}
              onDrop={(e) => {
                e.preventDefault();
                setInfosysDragging(false);
                const file = e.dataTransfer.files?.[0];
                if (file) {
                  if (!file.name.toLowerCase().endsWith(".pdf")) {
                    setInfosysError("Please drop a valid Infosys .pdf certificate.");
                    return;
                  }
                  setSelectedInfosysFile(file);
                  setInfosysError("");
                  setInfosysResult(null);
                }
              }}
              onClick={() => infosysFileInputRef.current?.click()}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  infosysFileInputRef.current?.click();
                }
              }}
            >
              <input
                ref={infosysFileInputRef}
                type="file"
                accept="application/pdf,.pdf"
                className="file-input-hidden"
                onChange={handleInfosysFileChange}
                aria-label="Upload Infosys PDF file"
              />

              <div className="dropzone-content">
                <span className="dropzone-icon" aria-hidden="true">
                  {selectedInfosysFile ? "📑" : "📥"}
                </span>

                {selectedInfosysFile ? (
                  <div className="file-info">
                    <p className="file-name">{selectedInfosysFile.name}</p>
                    <p className="file-size">
                      {(selectedInfosysFile.size / 1024).toFixed(1)} KB — Ready to inspect
                    </p>
                  </div>
                ) : (
                  <div>
                    <p className="dropzone-prompt">
                      Drag & drop your <strong>Infosys Springboard certificate PDF</strong> here, or{" "}
                      <span className="browse-link">browse</span>
                    </p>
                    <p className="dropzone-limits">
                      Decodes Sunbird RC / W3C QR code & compares against visible PDF text
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className="upload-actions-row">
              {selectedInfosysFile && (
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={clearInfosysFile}
                >
                  ✕ Remove File
                </button>
              )}

              <button
                type="submit"
                className="btn-primary verify-submit-btn"
                disabled={infosysLoading || !selectedInfosysFile}
              >
                {infosysLoading ? (
                  <>
                    <span className="spinner" aria-hidden="true" /> Analyzing Credential & QR...
                  </>
                ) : (
                  "🔍 Verify Infosys Certificate"
                )}
              </button>
            </div>

            {infosysError && (
              <p className="verify-error" role="alert">
                {infosysError}
              </p>
            )}
          </form>
        )}

        {/* Loading skeleton */}
        {(loading || infosysLoading) && (
          <div className="verify-skeleton glass-card" aria-live="polite">
            <div className="skeleton-line skeleton-medium" />
            <div className="skeleton-line skeleton-wide" />
            <div className="skeleton-line skeleton-wide" />
            <div className="skeleton-line skeleton-short" />
          </div>
        )}

        {/* Mode 1 & 2: CertChain Verification Result Section */}
        {activeTab !== "infosys" && verificationResult && !loading && (
          <section
            className={`verify-result glass-card ${badge.className}`}
            aria-live="polite"
            aria-label="Verification result"
          >
            <div className="result-heading">
              <span className="result-badge-icon" aria-hidden="true">
                {badge.icon}
              </span>
              <div>
                <span className={`status-pill ${badge.className}`}>
                  {badge.label}
                </span>
                <h2 className="result-heading-title">{badge.heading}</h2>
                <p className="result-heading-desc">
                  {verificationResult.message || "Verification completed."}
                </p>
              </div>
            </div>

            {/* Tampering Comparison Table */}
            {isTampered && (
              <div className="tamper-alert-box">
                <div className="tamper-alert-header">
                  <span className="tamper-warn-icon" aria-hidden="true">
                    ⚠️
                  </span>
                  <div>
                    <h3 className="tamper-title">
                      Detected Field Discrepancies
                    </h3>
                    <p className="tamper-desc">
                      The document text extracted from the uploaded PDF does not match
                      the authoritative, immutable record anchored on the blockchain.
                    </p>
                  </div>
                </div>

                {verificationResult.mismatchDetails &&
                  verificationResult.mismatchDetails.length > 0 && (
                    <div className="tamper-table-wrap">
                      <table className="tamper-table">
                        <thead>
                          <tr>
                            <th>Attribute</th>
                            <th>Authoritative Record (Registry)</th>
                            <th>Uploaded Document (Untrusted)</th>
                          </tr>
                        </thead>
                        <tbody>
                          {verificationResult.mismatchDetails.map((m, idx) => (
                            <tr key={idx} className="mismatch-row">
                              <td className="field-name-cell">
                                <strong>{m.field}</strong>
                              </td>
                              <td className="expected-cell">
                                <span className="cell-tag expected-tag">
                                  ✓ Authentic:
                                </span>{" "}
                                {m.expected || "—"}
                              </td>
                              <td className="found-cell">
                                <span className="cell-tag found-tag">
                                  ✕ Found in PDF:
                                </span>{" "}
                                {m.found || "—"}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
              </div>
            )}

            {/* AI-Assisted Document Analysis */}
            {verificationResult.aiAnalysis && (
              <div
                className={`ai-analysis-card glass-card ai-risk-${
                  verificationResult.aiAnalysis.riskLevel?.toLowerCase() ||
                  "unknown"
                }`}
              >
                <div className="ai-header">
                  <div className="ai-title-wrap">
                    <span className="ai-badge-icon" aria-hidden="true">
                      🤖
                    </span>
                    <div>
                      <div className="ai-badge-row">
                        <h3 className="ai-title">AI-Assisted Analysis</h3>
                        <span
                          className={`ai-risk-pill risk-${
                            verificationResult.aiAnalysis.riskLevel?.toLowerCase() ||
                            "unknown"
                          }`}
                        >
                          Risk Level: {verificationResult.aiAnalysis.riskLevel || "UNKNOWN"}
                        </span>
                      </div>
                      <p className="ai-subtitle">
                        Heuristic anomaly detection (Supplements, does not replace, cryptographic verification).
                      </p>
                    </div>
                  </div>
                  <span className="ai-method-tag">
                    {verificationResult.aiAnalysis.modelOrMethod ||
                      "CertChain Heuristic Anomaly Engine"}
                  </span>
                </div>

                {isUnableToVerify && (
                  <div className="ai-advisory-banner" role="status">
                    ⚠️ <strong>Advisory Note:</strong> AI analysis evaluates heuristic document formatting only. A &quot;LOW&quot; heuristic risk does <strong>NOT</strong> verify certificate authenticity because blockchain verification is unconfirmed.
                  </div>
                )}
                {isTampered && (
                  <div className="ai-advisory-banner ai-advisory-tampered" role="status">
                    ⛔ <strong>Cryptographic Override:</strong> Cryptographic mismatch takes absolute precedence over AI heuristic risk scores. This document has been tampered with.
                  </div>
                )}
                {isNotFound && (
                  <div className="ai-advisory-banner ai-advisory-tampered" role="status">
                    ⚠️ <strong>Unregistered Document:</strong> While document text conforms to expected formatting, this certificate has <strong>NO registered record</strong> on the current blockchain registry. It cannot be verified as authentic.
                  </div>
                )}

                <div className="ai-body">
                  <div className="ai-section">
                    <h4 className="ai-section-title">Assessment Narrative</h4>
                    <p className="ai-explanation">
                      {verificationResult.aiAnalysis.explanation}
                    </p>
                  </div>

                  {verificationResult.aiAnalysis.suspiciousIndicators &&
                    verificationResult.aiAnalysis.suspiciousIndicators.length >
                      0 && (
                      <div className="ai-section">
                        <h4 className="ai-section-title">
                          Document Observations & Anomaly Flags
                        </h4>
                        <ul className="ai-indicators-list">
                          {verificationResult.aiAnalysis.suspiciousIndicators.map(
                            (ind, idx) => (
                              <li key={idx} className="ai-indicator-item">
                                <span className="ai-bullet" aria-hidden="true">
                                  •
                                </span>{" "}
                                <span>{ind}</span>
                              </li>
                            ),
                          )}
                        </ul>
                      </div>
                    )}

                  {verificationResult.aiAnalysis.recommendedAction && (
                    <div className="ai-action-box">
                      <strong className="ai-action-label">
                        Recommended Next Action:
                      </strong>{" "}
                      <span className="ai-action-text">
                        {verificationResult.aiAnalysis.recommendedAction}
                      </span>
                    </div>
                  )}

                  <p className="ai-disclaimer">
                    ℹ️ <em>{verificationResult.aiAnalysis.disclaimer}</em>
                  </p>
                </div>
              </div>
            )}


            {/* Authoritative Certificate Details */}
            {certificate && (
              <div className="certificate-details">
                <div>
                  <span>Certificate ID</span>
                  <strong>{certificate.certificateId}</strong>
                </div>
                <div>
                  <span>Student Name</span>
                  <strong>{certificate.studentName}</strong>
                </div>
                <div>
                  <span>Roll Number</span>
                  <strong>{certificate.studentRollNumber || "—"}</strong>
                </div>
                <div>
                  <span>Course</span>
                  <strong>{certificate.course || "—"}</strong>
                </div>
                <div>
                  <span>Institution</span>
                  <strong>{certificate.institution || "—"}</strong>
                </div>
                <div>
                  <span>Grade</span>
                  <strong>{certificate.grade || "—"}</strong>
                </div>
                <div>
                  <span>Issue Date</span>
                  <strong>{certificate.issueDate || "—"}</strong>
                </div>
                <div>
                  <span>Verification Status</span>
                  <strong className={
                    isAuthentic
                      ? "status-val-success"
                      : isRevoked
                      ? "status-val-danger"
                      : isTampered
                      ? "status-val-danger"
                      : isUnableToVerify
                      ? "status-val-warning"
                      : isNeedsReview
                      ? "status-val-info"
                      : "status-val-muted"
                  }>
                    {isAuthentic
                      ? "VALID & ON-CHAIN CONFIRMED"
                      : isRevoked
                      ? "REVOKED"
                      : isTampered
                      ? "TAMPERED / INVALID"
                      : isUnableToVerify
                      ? "UNCONFIRMED (Blockchain Unavailable)"
                      : isNotFound
                      ? "NOT REGISTERED ON BLOCKCHAIN"
                      : isNeedsReview
                      ? "NEEDS REVIEW (External Credential)"
                      : certificate.status || "Unknown"}
                  </strong>
                </div>
                {isUnableToVerify && (
                  <div>
                    <span>Database Record</span>
                    <strong className="status-val-muted">
                      {certificate.status === "VALID" ? "Active in DB (Pending On-Chain Proof)" : certificate.status}
                    </strong>
                  </div>
                )}
                {isNotFound && (
                  <div>
                    <span>Database Record</span>
                    <strong className="status-val-danger">
                      Present in Database, but Missing on Blockchain Registry
                    </strong>
                  </div>
                )}
                <div className="certificate-hash">
                  <span>Canonical Content Hash</span>
                  <code>{certificate.certificateHash || "Not available"}</code>
                </div>
                {verificationResult.uploadedFileHash && (
                  <div className="certificate-hash">
                    <span>Uploaded PDF File Hash (SHA-256)</span>
                    <code>{verificationResult.uploadedFileHash}</code>
                  </div>
                )}
              </div>
            )}

            {/* Blockchain Notice */}
            <div className="blockchain-notice">
              <strong>
                {verificationResult.blockchainVerified
                  ? "Blockchain verification confirmed"
                  : "Blockchain verification unavailable / not confirmed"}
              </strong>
              <p>
                {verificationResult.blockchainNote ||
                  "This result reflects the verification checks currently available."}
              </p>
              {certificate?.transactionHash && (
                <p>
                  Transaction hash: <code>{certificate.transactionHash}</code>
                </p>
              )}
            </div>

            {/* Actions for VERIFIED certificate only */}
            {isAuthentic && certificate && (
              <div className="verify-actions">
                <button
                  type="button"
                  className="btn-outline-teal"
                  onClick={() => setIsModalOpen(true)}
                >
                  👁 View Certificate
                </button>
                <button
                  id="download-official-pdf-btn"
                  type="button"
                  className="btn-primary"
                  onClick={handleDownloadPdf}
                  disabled={downloading}
                >
                  {downloading ? (
                    <>
                      <span className="spinner" aria-hidden="true" /> Generating
                      PDF...
                    </>
                  ) : (
                    "📥 Download Official PDF"
                  )}
                </button>
              </div>
            )}
          </section>
        )}

        {/* Mode 3: Infosys Springboard Result Section */}
        {activeTab === "infosys" && infosysResult && !infosysLoading && (() => {
          const ibadge = getInfosysBadge();
          const cred = infosysResult.credential;
          const isTamperedInfosys = infosysResult.result === "TAMPERING_DETECTED";

          return (
            <section
              className={`verify-result glass-card ${ibadge?.className || "status-verified"}`}
              aria-live="polite"
              aria-label="Infosys verification result"
            >
              <div className="result-heading">
                <span className="result-badge-icon" aria-hidden="true">
                  {ibadge?.icon || "🌐"}
                </span>
                <div>
                  <div className="infosys-tag">
                    <span>✦</span> INFOSYS SPRINGBOARD CERTIFICATION
                  </div>
                  <span className={`status-pill ${ibadge?.className}`}>
                    {ibadge?.label}
                  </span>
                  <h2 className="result-heading-title">{ibadge?.heading}</h2>
                  <p className="result-heading-desc">
                    {infosysResult.message || "Verification completed."}
                  </p>
                </div>
              </div>

              {/* Fraud Detection / Tampering Alert */}
              {isTamperedInfosys && (
                <div className="tamper-alert-box">
                  <div className="tamper-alert-header">
                    <span className="tamper-warn-icon" aria-hidden="true">⚠️</span>
                    <div>
                      <h3 className="tamper-title">Fraud Alert: Certificate Modification Detected</h3>
                      <p className="tamper-desc">
                        The visible text printed on this PDF contradicts the cryptographic credential embedded in the QR code.
                        This certificate appears to have been altered or forged.
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Field Comparison Table */}
              <div className="infosys-table-wrap">
                <table className="infosys-table">
                  <thead>
                    <tr>
                      <th>Attribute</th>
                      <th>Visible PDF Text</th>
                      <th>Signed QR Credential (W3C Standard)</th>
                      <th>Match Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      { name: "Student Name", data: infosysResult.studentName },
                      { name: "Course Name", data: infosysResult.course },
                      { name: "Completion Date", data: infosysResult.completionDate },
                      { name: "Issuance Date", data: infosysResult.issuanceDate },
                    ].map((row, idx) => {
                      const matches = row.data?.matches;
                      const rowClass = matches === true ? "infosys-row-match" : matches === false ? "infosys-row-mismatch" : "";
                      return (
                        <tr key={idx} className={rowClass}>
                          <td className="infosys-field-name">{row.name}</td>
                          <td>
                            <strong>{row.data?.pdfValue || "—"}</strong>
                            {row.data?.pdfExtractionConfidence && (
                              <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>
                                Extraction Confidence: {row.data.pdfExtractionConfidence}
                              </div>
                            )}
                          </td>
                          <td>
                            <strong>{row.data?.credentialValue || "—"}</strong>
                          </td>
                          <td>
                            {matches === true && (
                              <span className="status-badge-match">✓ Match</span>
                            )}
                            {matches === false && (
                              <span className="status-badge-mismatch">✕ Mismatch</span>
                            )}
                            {matches === null && (
                              <span className="status-badge-inconclusive">? Inconclusive</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Credential Metadata Grid */}
              <div className="infosys-meta-grid">
                <div className="infosys-meta-item">
                  <span>Platform / Issuer</span>
                  <strong>{infosysResult.issuer || "Infosys Springboard"}</strong>
                </div>
                <div className="infosys-meta-item">
                  <span>Credential Standard</span>
                  <strong>{cred?.credentialTypes?.join(", ") || "VerifiableCredential (W3C)"}</strong>
                </div>
                <div className="infosys-meta-item">
                  <span>Proof Cryptography</span>
                  <strong>{cred?.proofType || "Ed25519Signature2018"}</strong>
                </div>
                <div className="infosys-meta-item">
                  <span>Verification Method</span>
                  <strong>{cred?.verificationMethod || "did:india"}</strong>
                </div>
              </div>

              {/* Official Portal Verification Box */}
              <div className="infosys-portal-box">
                <strong>🔗 Official Infosys Cross-Verification</strong>
                <p className="infosys-portal-desc">
                  Infosys Springboard certifies completions via the Wingspan platform using decentralized identity (Sunbird RC).
                  Field consistency checks confirm that this certificate has not been altered. You can also confirm this credential directly on the official portal:
                </p>
                <a
                  href={infosysResult.officialVerificationUrl || "https://verify.onwingspan.com"}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="infosys-portal-btn"
                >
                  ↗ Open verify.onwingspan.com
                </a>
              </div>

              {/* Raw JSON-LD Inspector */}
              {cred?.rawQrContent && (
                <div className="raw-json-wrap">
                  <button
                    type="button"
                    className="toggle-raw-btn"
                    onClick={() => setShowRawJson(!showRawJson)}
                  >
                    {showRawJson ? "Hide" : "Show"} Decoded W3C Verifiable Credential (JSON-LD)
                  </button>
                  {showRawJson && (
                    <pre className="raw-json-block">
                      {(() => {
                        try {
                          return JSON.stringify(JSON.parse(cred.rawQrContent), null, 2);
                        } catch {
                          return cred.rawQrContent;
                        }
                      })()}
                    </pre>
                  )}
                </div>
              )}
            </section>
          );
        })()}

        {/* Preview Modal */}
        {certificate && (
          <CertificateModal
            isOpen={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            certificate={certificate}
          />
        )}

        {/* Verification guidance tips */}
        <div className="verify-tips">
          <article className="tip-card glass-card">
            <span className="tip-icon" aria-hidden="true">
              📑
            </span>
            <div>
              <h3>How does PDF tamper detection work?</h3>
              <p>
                The server extracts the internal text and identifiers directly
                from the uploaded PDF file using in-process parsing, recalculates
                the canonical SHA-256 digest, and checks it against the on-chain
                smart contract registry to detect forged names or altered credentials.
              </p>
            </div>
          </article>

          <article className="tip-card glass-card">
            <span className="tip-icon" aria-hidden="true">
              🔐
            </span>
            <div>
              <h3>What is exact-file integrity?</h3>
              <p>
                A raw file hash confirms exact bit-for-bit file matching, while
                canonical field hashing protects against altered grades, roll
                numbers, or recipient names across formatting differences.
              </p>
            </div>
          </article>
        </div>
      </div>
    </div>
  );
}

export default Verify;
