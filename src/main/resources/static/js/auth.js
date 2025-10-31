// src/main/resources/static/js/auth.js
(function () {
  function getToken()       { return localStorage.getItem("token"); }
  function getLoginId()     { return localStorage.getItem("loginId"); }
  function getUserName()    { return localStorage.getItem("userName"); }

  function setAuth(data) {
    if (!data) return;
    if (data.token)    localStorage.setItem("token", data.token);
    if (data.id)       localStorage.setItem("loginId", data.id);
    if (data.userName) localStorage.setItem("userName", data.userName);
  }

  function clearAuthStorage() {
    localStorage.removeItem("token");
    localStorage.removeItem("loginId");
    localStorage.removeItem("userName");
  }

  async function authFetch(url, options = {}) {
    const t = getToken();
    const headers = new Headers(options.headers || {});
    if (t) headers.set("Authorization", `Bearer ${t}`);
    const res = await fetch(url, { ...options, headers });
    if (res.status === 401) {
      clearAuthStorage();
      location.href = "/login";
      throw new Error("Unauthorized");
    }
    return res;
  }

  let stompClient = null;
  function setStompClient(client) { stompClient = client; }
  function getStompClient()       { return stompClient; }
  function disconnectStomp() {
    try { if (stompClient && stompClient.connected) stompClient.disconnect(() => {}); }
    catch(_) {}
    stompClient = null;
  }

  async function serverLogoutPing() {
    const t = getToken();
    if (!t) return;
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        headers: { "Authorization": `Bearer ${t}` }
      });
    } catch(_) {}
  }

  async function logout() {
    await serverLogoutPing();
    disconnectStomp();
    clearAuthStorage();
    location.href = "/login";
  }

  function attachLogoutButton(buttonId) {
    const el = document.getElementById(buttonId);
    if (el) {
        el.addEventListener("click", logout);
    }

  }

  function requireLogin() {
    if (!getToken()) { location.href = "/login"; return false; }
    return true;
  }

  // ???�역 ?�출 (+ ?�환??별칭 추�?)
  window.Auth = {
    // ?�래 메서??
    getToken, getLoginId, getUserName,
    setAuth, clearAuthStorage,
    authFetch,
    setStompClient, getStompClient, disconnectStomp,
    logout, attachLogoutButton, requireLogin,

    // ??별칭(rooms.html ??기존 ?�용 코드 ?�환)
    token: getToken,
    loginId: getLoginId,
    userName: getUserName,
    clear: clearAuthStorage,
    fetch: authFetch
  };

  // ?�택: 로드 ?�인??
  // console.assert(typeof window.Auth !== "undefined", "Auth 로드 ?�패");
})();

