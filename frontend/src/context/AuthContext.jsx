import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("certchain_token") || "");
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem("certchain_user");
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const isLoggedIn = !!token && !!user;
  const isAdmin = user?.role === "ADMIN";
  const isIssuer = user?.role === "ISSUER" || user?.role === "ADMIN";

  const login = (authToken, userData) => {
    localStorage.setItem("certchain_token", authToken);
    localStorage.setItem("certchain_user", JSON.stringify(userData));
    sessionStorage.setItem("certchain_admin_logged_in", "true");
    setToken(authToken);
    setUser(userData);
  };

  const logout = () => {
    localStorage.removeItem("certchain_token");
    localStorage.removeItem("certchain_user");
    sessionStorage.removeItem("certchain_admin_logged_in");
    setToken("");
    setUser(null);
  };

  const getAuthHeaders = () => {
    if (!token) return {};
    return {
      Authorization: `Bearer ${token}`,
    };
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isLoggedIn,
        isAdmin,
        isIssuer,
        login,
        logout,
        getAuthHeaders,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
}
