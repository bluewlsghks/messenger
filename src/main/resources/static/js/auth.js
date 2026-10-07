"use strict";
(() => {
  const getToken = () => localStorage.getItem('token');
  const getLoginId = () => localStorage.getItem('loginId');
  const getUserName = () => localStorage.getItem('userName');
  let stompClient = null, refreshing = null;
  function expiry(token) { try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000; } catch (_) { return 0; } }
  function setAuth(data) {
    const previous = getToken();
    if (data.token) localStorage.setItem('token', data.token);
    if (data.id) localStorage.setItem('loginId', data.id);
    if (data.userName) localStorage.setItem('userName', data.userName);
    if (data.token && data.token !== previous) window.dispatchEvent(new Event('messenger:token'));
  }
  function clearAuthStorage() {
    const me = getLoginId();
    Object.keys(localStorage).filter(k => k.startsWith(`messenger:draft:${me}:`) || k.startsWith(`messenger:pending:${me}:`)).forEach(k => localStorage.removeItem(k));
    ['token', 'loginId', 'userName'].forEach(k => localStorage.removeItem(k));
  }
  function disconnectStomp() { try { stompClient?.deactivate?.({force:true}); } catch (_) {} stompClient = null; }
  async function refresh(force = false) {
    if (refreshing) return refreshing;
    const previous = getToken();
    const rotate = async () => {
      if (getToken() !== previous && expiry(getToken() || '') > Date.now() + 30000) return;
      if (!force && expiry(getToken() || '') > Date.now() + 30000) return;
      const response = await fetch('/api/auth/refresh', {method:'POST', credentials:'same-origin',
        headers:{'X-Requested-With':'XMLHttpRequest'}, signal:AbortSignal.timeout(15000)});
      if (!response.ok) { const error = new Error('다시 로그인해 주세요.'); error.status = response.status; throw error; }
      setAuth(await response.json());
    };
    // Web Locks serialize refresh across tabs so a simultaneous refresh is not mistaken for token replay.
    refreshing = (navigator.locks ? navigator.locks.request('messenger:refresh', rotate) : rotate()).finally(() => { refreshing = null; });
    return refreshing;
  }
  async function authFetch(url, options = {}) {
    const target = new URL(url, location.origin);
    if (target.origin !== location.origin) throw new Error('외부 주소로 인증 정보를 전송할 수 없습니다.');
    try {
      if (getToken() && expiry(getToken()) < Date.now() + 30000) await refresh();
      for (let attempt = 0; attempt < 2; attempt++) {
        const headers = new Headers(options.headers || {}), token = getToken();
        if (token) headers.set('Authorization', `Bearer ${token}`);
        if (typeof options.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
        const response = await fetch(target, {...options, credentials:'same-origin', headers,
          signal:options.signal || AbortSignal.timeout(30000)});
        if (response.status !== 401) return response;
        if (attempt === 0) await refresh(true);
      }
      const error = new Error('로그인이 만료되었습니다.'); error.status = 401; throw error;
    } catch (error) {
      if (error.status === 401) { disconnectStomp(); clearAuthStorage(); location.assign('/login'); }
      if (error.name === 'AbortError' || error.name === 'TimeoutError') throw new Error('서버 응답이 지연되고 있습니다. 다시 시도해 주세요.');
      throw error;
    }
  }
  async function request(url, options = {}) {
    const res = await authFetch(url, options);
    if (res.status === 204) return null;
    let data; try { data = await res.json(); } catch (_) { data = {}; }
    if (!res.ok) { const error = new Error(data.message || `요청을 처리하지 못했습니다. (${res.status})`); error.status = res.status; error.code = data.error; throw error; }
    return data;
  }
  async function logout() {
    window.dispatchEvent(new Event('messenger:logout'));
    try { await window.WebPush?.disable?.(); } catch (_) {}
    try { await fetch('/api/auth/logout', {method:'POST', credentials:'same-origin', headers:{'X-Requested-With':'XMLHttpRequest'}, signal:AbortSignal.timeout(5000)}); } catch (_) {}
    navigator.serviceWorker?.controller?.postMessage({type:'LOGOUT'});
    disconnectStomp(); clearAuthStorage(); location.assign('/login');
  }
  function requireLogin() { if (!getToken()) { location.replace('/login'); return false; } return true; }
  function attachLogoutButton(id) { const button = document.getElementById(id); if (button && !button.dataset.logoutBound) { button.dataset.logoutBound = 'true'; button.addEventListener('click', logout); } }
  window.Auth = {getToken,getLoginId,getUserName,setAuth,clearAuthStorage,authFetch,request,logout,requireLogin,attachLogoutButton,
    disconnectStomp,refresh,setStompClient:client => {stompClient = client;},getStompClient:() => stompClient,
    token:getToken,loginId:getLoginId,userName:getUserName,clear:clearAuthStorage,fetch:authFetch};
})();
