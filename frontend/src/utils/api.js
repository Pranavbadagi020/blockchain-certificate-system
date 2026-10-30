const BACKEND_TUNNEL_URL =
  "https://ant-approval-pct-photographers.trycloudflare.com/api";

function resolveApiBaseUrl() {
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  if (typeof window !== "undefined") {
    if (window.location.hostname.includes("trycloudflare.com")) {
      return BACKEND_TUNNEL_URL;
    }
    const host = window.location.hostname || "localhost";
    return `http://${host}:8080/api`;
  }
  return "http://localhost:8080/api";
}

export const API_BASE_URL = resolveApiBaseUrl();

export default API_BASE_URL;
