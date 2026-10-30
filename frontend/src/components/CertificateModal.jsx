import { useEffect, useState } from "react";
import {
  downloadCertificatePDF,
  generateQRCodeDataUrl,
  getVerificationUrl,
} from "../utils/pdfGenerator";
import "./CertificateModal.css";

/**
 * Modal displaying a professional academic diploma preview with unique verification QR code.
 */
function CertificateModal({ certificate, isOpen, onClose }) {
  const [qrCodeUrl, setQrCodeUrl] = useState(null);
  const [downloading, setDownloading] = useState(false);

  const certId =
    certificate?.certificateId || certificate?.id || "CERT-UNKNOWN";
  const studentName =
    certificate?.studentName || certificate?.holderName || "Recipient";
  const rollNumber =
    certificate?.studentRollNumber || certificate?.holderEmail || "—";
  const course = certificate?.course || "Programme of Study";
  const institution = certificate?.institution || "Issuing Institution";
  const issueDate = certificate?.issueDate || "—";
  const grade = certificate?.grade ? String(certificate.grade).trim() : "";
  const certHash = certificate?.certificateHash || "";
  const txHash = certificate?.transactionHash || certificate?.txHash || "";

  useEffect(() => {
    if (!isOpen || !certId) return;

    let isMounted = true;
    const verifyUrl = getVerificationUrl(certId);

    generateQRCodeDataUrl(verifyUrl).then((dataUrl) => {
      if (isMounted) {
        setQrCodeUrl(dataUrl);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [isOpen, certId]);

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen || !certificate) return null;

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadCertificatePDF(certificate);
    } catch (err) {
      console.error("Failed to download PDF:", err);
    } finally {
      setDownloading(false);
    }
  };

  const shortHash = (hash) => {
    if (!hash || hash.length < 18) return hash || "—";
    return `${hash.slice(0, 10)}...${hash.slice(-8)}`;
  };

  return (
    <div
      className="cert-modal-backdrop"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="cert-modal-dialog"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cert-modal-title"
      >
        {/* Modal Header */}
        <div className="cert-modal-header">
          <div>
            <h2 id="cert-modal-title" className="cert-modal-title">
              Official Certificate Preview
            </h2>
            <p className="cert-modal-subtitle">
              Generated with verifiable Ethereum blockchain credentials
            </p>
          </div>
          <button
            type="button"
            className="cert-modal-close"
            onClick={onClose}
            aria-label="Close certificate preview"
          >
            ✕
          </button>
        </div>

        {/* Certificate Visual Diploma */}
        <div className="cert-modal-body">
          <div className="diploma-frame">
            <div className="diploma-inner-border">
              {/* Corner decorations */}
              <div className="corner-accent corner-tl" aria-hidden="true" />
              <div className="corner-accent corner-tr" aria-hidden="true" />
              <div className="corner-accent corner-bl" aria-hidden="true" />
              <div className="corner-accent corner-br" aria-hidden="true" />

              <div className="diploma-header">
                <span className="diploma-crest" aria-hidden="true">
                  🏛
                </span>
                <h3 className="diploma-institution">{institution}</h3>
                <span className="diploma-tag">
                  CERTIFICATE OF ACADEMIC ACHIEVEMENT
                </span>
                <div className="diploma-divider" />
              </div>

              <div className="diploma-body">
                <p className="diploma-certify-text">This is to certify that</p>
                <h4 className="diploma-recipient">{studentName}</h4>
                <p className="diploma-roll">
                  Institutional ID / Roll Number: <strong>{rollNumber}</strong>
                </p>
                <p className="diploma-statement">
                  has successfully met all academic requirements and completed
                  the prescribed programme in
                </p>
                <h5 className="diploma-course">{course}</h5>

                {grade && (
                  <p className="diploma-grade">
                    Grade / Classification: <strong>{grade}</strong>
                  </p>
                )}

                <p className="diploma-date">Conferred on: {issueDate}</p>
              </div>

              {/* Diploma Footer with Verification QR */}
              <div className="diploma-footer">
                <div className="diploma-metadata">
                  <div className="diploma-meta-item">
                    <span className="meta-label">CERTIFICATE ID</span>
                    <strong className="meta-id">{certId}</strong>
                  </div>

                  {certHash && (
                    <div className="diploma-meta-item">
                      <span className="meta-label">SHA-256 CONTENT HASH</span>
                      <code className="meta-hash" title={certHash}>
                        {shortHash(certHash)}
                      </code>
                    </div>
                  )}

                  {txHash && (
                    <div className="diploma-meta-item">
                      <span className="meta-label">
                        BLOCKCHAIN TRANSACTION HASH
                      </span>
                      <code className="meta-hash" title={txHash}>
                        {shortHash(txHash)}
                      </code>
                    </div>
                  )}

                  <span className="diploma-badge">
                    ✓ Verified on Ethereum Smart Contract Registry
                  </span>
                </div>

                <div className="diploma-qr-section">
                  {qrCodeUrl ? (
                    <img
                      src={qrCodeUrl}
                      alt={`Verification QR Code for ${certId}`}
                      className="diploma-qr-img"
                    />
                  ) : (
                    <div className="diploma-qr-skeleton" aria-hidden="true" />
                  )}
                  <span className="qr-label">SCAN TO VERIFY</span>
                  <span className="qr-sublabel">Instant Online Check</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Modal Actions */}
        <div className="cert-modal-actions">
          <button
            type="button"
            className="btn-secondary"
            onClick={onClose}
          >
            Close
          </button>
          <button
            type="button"
            className="btn-primary cert-download-btn"
            onClick={handleDownload}
            disabled={downloading}
          >
            {downloading ? (
              <>
                <span className="spinner" aria-hidden="true" /> Generating
                PDF...
              </>
            ) : (
              "📥 Download PDF Certificate"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

export default CertificateModal;
