import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { API_BASE_URL } from "../utils/api";
import "./Login.css";

function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [form, setForm] = useState({ email: "", password: "" });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState("");
  const [loading, setLoading] = useState(false);
  const [remember, setRemember] = useState(false);

  const validate = () => {
    const e = {};

    if (!form.email.trim()) {
      e.email = "Email is required.";
    } else if (!/\S+@\S+\.\S+/.test(form.email)) {
      e.email = "Enter a valid email.";
    }

    if (!form.password) {
      e.password = "Password is required.";
    } else if (form.password.length < 6) {
      e.password = "Password must be at least 6 characters.";
    }

    return e;
  };

  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));

    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: "" }));
    }

    if (serverError) {
      setServerError("");
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const validationErrors = validate();

    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setErrors({});
    setServerError("");
    setLoading(true);

    try {
      const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: form.email.trim(),
          password: form.password,
        }),
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        setServerError(
          response.status === 401
            ? "Invalid email or password."
            : data.message || "Login failed. Please try again.",
        );
        return;
      }

      if (data.role !== "ADMIN" && data.role !== "ISSUER") {
        setServerError("This account does not have authorized dashboard or issuance access.");
        return;
      }

      login(data.token, data);
      if (data.role === "ADMIN") {
        navigate("/dashboard");
      } else {
        navigate("/issue");
      }
    } catch {
      setServerError(
        "Cannot connect to the backend. Make sure the backend server is running.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-bg" aria-hidden="true">
        <div className="lorb lorb-1"></div>
        <div className="lorb lorb-2"></div>
      </div>

      <div className="login-container">
        <div className="login-info" aria-hidden="true">
          <div className="login-info-logo">
            <div className="logo-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                <path
                  d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                <path
                  d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <span className="logo-text">CertChain</span>
          </div>

          <h2 className="login-info-title">
            Secure Admin
            <br />
            <span className="gradient-text">Portal</span>
          </h2>

          <p className="login-info-text">
            Access the administration panel to issue certificates, manage
            records, and monitor blockchain activity.
          </p>

          <ul className="login-features" aria-label="Admin features">
            <li>✓ Issue certificates on-chain</li>
            <li>✓ View all issued records</li>
            <li>✓ Monitor verification requests</li>
            <li>✓ Manage institutions</li>
          </ul>
        </div>

        <div className="login-card glass-card">
          <div className="login-card-header">
            <h1>Admin Login</h1>
            <p>Sign in to access the dashboard.</p>
          </div>

          <form
            id="login-form"
            onSubmit={handleSubmit}
            noValidate
            aria-label="Admin login form"
          >
            <div className="form-group">
              <label className="form-label" htmlFor="login-email">
                Email Address
              </label>

              <input
                id="login-email"
                type="email"
                className={`form-input ${errors.email ? "input-error" : ""}`}
                placeholder="admin@institution.edu"
                value={form.email}
                onChange={(e) => handleChange("email", e.target.value)}
                autoComplete="email"
                aria-required="true"
                aria-invalid={Boolean(errors.email)}
              />

              {errors.email && (
                <span className="field-error" role="alert">
                  {errors.email}
                </span>
              )}
            </div>

            <div className="form-group" style={{ marginTop: "18px" }}>
              <label className="form-label" htmlFor="login-password">
                Password
              </label>

              <input
                id="login-password"
                type="password"
                className={`form-input ${errors.password ? "input-error" : ""}`}
                placeholder="••••••••"
                value={form.password}
                onChange={(e) => handleChange("password", e.target.value)}
                autoComplete="current-password"
                aria-required="true"
                aria-invalid={Boolean(errors.password)}
              />

              {errors.password && (
                <span className="field-error" role="alert">
                  {errors.password}
                </span>
              )}
            </div>

            <div className="login-extras">
              <label className="remember-label">
                <input
                  id="login-remember"
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                Remember me
              </label>

              <a
                href="#"
                className="forgot-link"
                onClick={(e) => e.preventDefault()}
              >
                Forgot password?
              </a>
            </div>

            {serverError && (
              <p role="alert" style={{ color: "#dc2626", marginTop: "16px" }}>
                {serverError}
              </p>
            )}

            <button
              id="login-submit-btn"
              type="submit"
              className="btn-primary login-submit"
              disabled={loading}
            >
              {loading && <span className="spinner" aria-hidden="true"></span>}
              {loading ? "Signing In…" : "Sign In"}
            </button>
          </form>

          <p className="login-back">
            <Link to="/" className="back-link">
              ← Back to Home
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

export default Login;
