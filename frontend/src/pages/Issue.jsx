import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import { API_BASE_URL } from "../utils/api";
import CertificateModal from "../components/CertificateModal";
import { downloadCertificatePDF } from "../utils/pdfGenerator";
import "./Issue.css";

const INITIAL_FORM = {
  holderName: "",
  holderEmail: "",
  course: "",
  institution: "",
  issueDate: "",
  grade: "",
};

function Issue() {
  const { getAuthHeaders } = useAuth();
  const [form, setForm] = useState(INITIAL_FORM);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(null); // stores complete certificate response object
  const [errors, setErrors] = useState({});
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const validate = () => {
    const e = {};
    if (!form.holderName.trim()) e.holderName = "Holder name is required.";
    if (!form.holderEmail.trim()) e.holderEmail = "Email address is required.";
    else if (!/\S+@\S+\.\S+/.test(form.holderEmail))
      e.holderEmail = "Enter a valid email.";
    if (!form.course.trim()) e.course = "Course / programme is required.";
    if (!form.institution.trim())
      e.institution = "Institution name is required.";
    if (!form.issueDate) e.issueDate = "Issue date is required.";
    return e;
  };

  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: "" }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const e2 = validate();
    if (Object.keys(e2).length) {
      setErrors(e2);
      return;
    }

    setLoading(true);
    setErrors({});

    try {
      const response = await fetch(
        `${API_BASE_URL}/certificates/issue`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...getAuthHeaders(),
          },
          body: JSON.stringify({
            studentName: form.holderName.trim(),
            studentRollNumber: form.holderEmail.trim(),
            course: form.course.trim(),
            institution: form.institution.trim(),
            grade: form.grade.trim(),
            issueDate: form.issueDate,
          }),
        },
      );

      const data = await response.json();

      if (!response.ok) {
        throw new Error(
          data.message ||
            "Unable to issue certificate. Please check the details.",
        );
      }

      setSuccess(data);
    } catch (error) {
      setErrors({
        submit: error.message || "Could not connect to the backend.",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setForm(INITIAL_FORM);
    setSuccess(null);
    setErrors({});
    setIsModalOpen(false);
  };

  const handleDownloadPdf = async () => {
    if (!success) return;
    setDownloading(true);
    try {
      await downloadCertificatePDF(success);
    } catch (err) {
      console.error("Failed to download PDF:", err);
    } finally {
      setDownloading(false);
    }
  };

  if (success) {
    const certId = success.certificateId || success.id;
    const txHash = success.transactionHash || success.txHash;

    return (
      <div className="issue-page">
        <div className="issue-bg" aria-hidden="true">
          <div className="iorb iorb-1"></div>
        </div>
        <div className="container issue-container">
          <div
            className="success-card glass-card"
            role="alert"
            aria-live="polite"
          >
            <div className="success-icon" aria-hidden="true">
              🎉
            </div>
            <h1>Certificate Issued!</h1>
            <p className="success-subtitle">
              The certificate has been recorded permanently on the Ethereum
              blockchain.
            </p>

            <div className="success-details">
              <div className="success-field">
                <span className="cert-field-label">Certificate ID</span>
                <code className="success-value success-id">{certId}</code>
              </div>
              <div className="success-field">
                <span className="cert-field-label">Student Name</span>
                <span className="success-value">
                  {success.studentName || form.holderName}
                </span>
              </div>
              <div className="success-field">
                <span className="cert-field-label">Course</span>
                <span className="success-value">
                  {success.course || form.course}
                </span>
              </div>
              {txHash && (
                <div className="success-field">
                  <span className="cert-field-label">Transaction Hash</span>
                  <code className="success-value success-hash">
                    {txHash}
                  </code>
                </div>
              )}
            </div>

            <div className="success-actions">
              <button
                type="button"
                className="btn-outline-teal"
                onClick={() => setIsModalOpen(true)}
              >
                👁 View Certificate
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={handleDownloadPdf}
                disabled={downloading}
              >
                {downloading ? (
                  <>
                    <span className="spinner" aria-hidden="true" /> Generating...
                  </>
                ) : (
                  "📥 Download PDF"
                )}
              </button>
              {txHash && (
                <a
                  href={`https://sepolia.etherscan.io/tx/${txHash}`}
                  target="_blank"
                  rel="noreferrer"
                  className="btn-outline-teal"
                >
                  ↗ View on Etherscan
                </a>
              )}
              <button
                id="issue-another-btn"
                type="button"
                className="btn-secondary"
                onClick={handleReset}
              >
                📜 Issue Another
              </button>
            </div>
          </div>
        </div>

        <CertificateModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          certificate={success}
        />
      </div>
    );
  }

  return (
    <div className="issue-page">
      <div className="issue-bg" aria-hidden="true">
        <div className="iorb iorb-1"></div>
        <div className="iorb iorb-2"></div>
      </div>

      <div className="container issue-container">
        <div className="issue-header">
          <span className="section-tag">Blockchain Issuance</span>
          <h1>
            Issue a <span className="gradient-text">Certificate</span>
          </h1>
          <p>
            Fill in the details below. The certificate will be hashed and
            recorded permanently on the Ethereum blockchain.
          </p>
        </div>

        <form
          id="issue-form"
          className="issue-form glass-card"
          onSubmit={handleSubmit}
          noValidate
          aria-label="Issue certificate form"
        >
          {/* Section: Recipient */}
          <div className="form-section">
            <h2 className="form-section-title">
              <span className="form-section-icon" aria-hidden="true">
                👤
              </span>
              Recipient Details
            </h2>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="holderName">
                  Full Name *
                </label>
                <input
                  id="holderName"
                  type="text"
                  className={`form-input ${errors.holderName ? "input-error" : ""}`}
                  placeholder="e.g. Arjun Mehta"
                  value={form.holderName}
                  onChange={(e) => handleChange("holderName", e.target.value)}
                  aria-required="true"
                  aria-describedby={
                    errors.holderName ? "holderName-err" : undefined
                  }
                />
                {errors.holderName && (
                  <span
                    id="holderName-err"
                    className="field-error"
                    role="alert"
                  >
                    {errors.holderName}
                  </span>
                )}
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="holderEmail">
                  Email Address *
                </label>
                <input
                  id="holderEmail"
                  type="email"
                  className={`form-input ${errors.holderEmail ? "input-error" : ""}`}
                  placeholder="e.g. arjun@example.com"
                  value={form.holderEmail}
                  onChange={(e) => handleChange("holderEmail", e.target.value)}
                  aria-required="true"
                  aria-describedby={
                    errors.holderEmail ? "holderEmail-err" : undefined
                  }
                />
                {errors.holderEmail && (
                  <span
                    id="holderEmail-err"
                    className="field-error"
                    role="alert"
                  >
                    {errors.holderEmail}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Section: Certificate */}
          <div className="form-section">
            <h2 className="form-section-title">
              <span className="form-section-icon" aria-hidden="true">
                📜
              </span>
              Certificate Details
            </h2>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="course">
                  Course / Programme *
                </label>
                <input
                  id="course"
                  type="text"
                  className={`form-input ${errors.course ? "input-error" : ""}`}
                  placeholder="e.g. B.Tech Computer Science"
                  value={form.course}
                  onChange={(e) => handleChange("course", e.target.value)}
                  aria-required="true"
                />
                {errors.course && (
                  <span className="field-error" role="alert">
                    {errors.course}
                  </span>
                )}
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="institution">
                  Institution *
                </label>
                <input
                  id="institution"
                  type="text"
                  className={`form-input ${errors.institution ? "input-error" : ""}`}
                  placeholder="e.g. IIT Delhi"
                  value={form.institution}
                  onChange={(e) => handleChange("institution", e.target.value)}
                  aria-required="true"
                />
                {errors.institution && (
                  <span className="field-error" role="alert">
                    {errors.institution}
                  </span>
                )}
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label" htmlFor="issueDate">
                  Date of Issue *
                </label>
                <input
                  id="issueDate"
                  type="date"
                  className={`form-input ${errors.issueDate ? "input-error" : ""}`}
                  value={form.issueDate}
                  onChange={(e) => handleChange("issueDate", e.target.value)}
                  aria-required="true"
                />
                {errors.issueDate && (
                  <span className="field-error" role="alert">
                    {errors.issueDate}
                  </span>
                )}
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="grade">
                  Grade / Honours <span className="optional">(optional)</span>
                </label>
                <input
                  id="grade"
                  type="text"
                  className="form-input"
                  placeholder="e.g. First Class with Distinction"
                  value={form.grade}
                  onChange={(e) => handleChange("grade", e.target.value)}
                />
              </div>
            </div>
          </div>

          {/* Notice */}
          {errors.submit && (
            <div
              className="field-error"
              role="alert"
              style={{ marginBottom: "16px" }}
            >
              {errors.submit}
            </div>
          )}
          <div className="issue-notice">
            <span aria-hidden="true">⚠️</span>
            <p>
              This action is <strong>irreversible</strong>. Once issued, the
              certificate is permanently recorded on the Ethereum blockchain and
              cannot be modified.
            </p>
          </div>

          {/* Submit */}
          <div className="form-submit-row">
            <button
              type="button"
              className="btn-secondary"
              onClick={handleReset}
              disabled={loading}
            >
              Reset
            </button>
            <button
              id="issue-submit-btn"
              type="submit"
              className="btn-primary issue-submit-btn"
              disabled={loading}
            >
              {loading && <span className="spinner" aria-hidden="true"></span>}
              {loading ? "Recording on Blockchain…" : "⛓ Issue on Blockchain"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default Issue;
