'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const orderMessages = (a, b) => (Date.parse(a.createdAt) - Date.parse(b.createdAt)) || String(a.id).localeCompare(String(b.id));
  const isComposingEnter = event => event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229;
  // Exposed separately for deterministic Node tests without a browser or an API key.
  if (typeof module !== 'undefined' && module.exports) module.exports = {orderMessages, isComposingEnter};
  if (typeof window === 'undefined') return;

  class ChatPanel {
    constructor() {
      this.list = byId('message-list'); this.input = byId('message-input');
      this.sendButton = byId('send-message'); this.olderButton = byId('load-older');
      this.context = null;
      byId('message-form').addEventListener('submit', event => { event.preventDefault(); this.send(); });
      this.input.addEventListener('keydown', event => { if (isComposingEnter(event)) { event.preventDefault(); this.send(); } });
      this.olderButton.addEventListener('click', () => this.history(this.context, true));
      this.list.addEventListener('scroll', () => this.queueRead(this.context), {passive:true});
      document.addEventListener('visibilitychange', () => this.queueRead(this.context));
      window.addEventListener('focus', () => this.queueRead(this.context));
      window.addEventListener('pagehide', () => this.close());
    }
    error(message) { const el = byId('chat-error'); el.textContent = message || ''; el.hidden = !message; }
    state(text) { byId('connection-state').textContent = text; }
    current(ctx) { return ctx && this.context === ctx; }
    close() {
      const old = this.context; this.context = null;
      if (old) {
        clearTimeout(old.retry); clearTimeout(old.readTimer); clearTimeout(old.expiryTimer);
        try { old.socket?.close(); } catch (_) { /* Already closed. */ }
      }
      this.input.disabled = true; this.sendButton.disabled = true; this.olderButton.disabled = true;
    }
    open(roomId, title) {
      this.close(); this.error(''); this.input.value = ''; this.list.replaceChildren();
      byId('chat-title').textContent = title;
      const ctx = {roomId, messages:new Map(), failures:0, ready:false, loading:false, sending:false, hasMore:true, initialized:false};
      this.context = ctx;
      this.connect(ctx);
    }
    expired() {
      const token = Auth.getToken();
      if (!token) return true;
      try {
        const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        return !JSON.parse(atob(part)).exp || JSON.parse(atob(part)).exp * 1000 <= Date.now();
      } catch (_) { return true; }
    }
    connect(ctx) {
      if (!this.current(ctx)) return;
      if (this.expired()) { this.close(); Auth.clearAuthStorage(); location.href = '/login'; return; }
      this.state(ctx.failures ? '재연결 중…' : '연결 중…');
      const socket = new SockJS('/ws-stomp'); const client = Stomp.over(socket);
      ctx.socket = socket; ctx.client = client; client.debug = null;
      client.heartbeat.outgoing = 10000;
      // Spring simple broker has no configured STOMP heartbeat. SockJS still supplies transport heartbeats.
      client.heartbeat.incoming = 0;
      Auth.setStompClient(client);
      client.connect({Authorization:`Bearer ${Auth.getToken()}`}, () => {
        if (!this.current(ctx)) { socket.close(); return; }
        ctx.ready = true; ctx.failures = 0;
        this.input.disabled = false; this.sendButton.disabled = false; this.error(''); this.state('연결됨');
        client.subscribe(`/sub/chat/${ctx.roomId}`, frame => {
          if (!this.current(ctx)) return;
          try {
            const message = JSON.parse(frame.body);
            const bottom = this.list.scrollHeight - this.list.scrollTop - this.list.clientHeight < 100;
            this.merge(ctx, [message]); this.render(ctx, bottom); this.queueRead(ctx);
          } catch (_) { this.error('메시지를 표시하지 못했습니다. 다시 연결해 주세요.'); }
        });
        client.subscribe(`/sub/chat/${ctx.roomId}/read`, frame => {
          if (!this.current(ctx)) return;
          try {
            const {messageIds = [], readerId} = JSON.parse(frame.body);
            for (const id of messageIds) {
              const message = ctx.messages.get(id);
              if (message) message.readBy = [...new Set([...(message.readBy || []), readerId])];
            }
            this.render(ctx, false);
          } catch (_) { /* Ignore malformed read receipts. */ }
        });
        client.subscribe('/user/queue/errors', frame => {
          if (this.current(ctx)) { try { this.error(JSON.parse(frame.body).message); } catch (_) { this.error('채팅 요청이 거부되었습니다.'); } }
        });
        // Subscribe before snapshot, then merge by ID. This is reconciliation, not a durable replay protocol.
        this.history(ctx, false);
        clearTimeout(ctx.expiryTimer);
        const exp = JSON.parse(atob(Auth.getToken().split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000;
        ctx.expiryTimer = setTimeout(() => { if (this.current(ctx)) { this.close(); Auth.clearAuthStorage(); location.href = '/login'; } }, Math.max(0, exp - Date.now()));
      }, () => {
        if (!this.current(ctx)) return;
        ctx.ready = false; this.sendButton.disabled = true; this.state('연결 끊김 · 재시도');
        try { socket.close(); } catch (_) { /* Transport already closed. */ }
        clearTimeout(ctx.retry);
        ctx.retry = setTimeout(() => this.connect(ctx), Math.min(15000, 1000 * 2 ** Math.min(ctx.failures++, 4)));
      });
    }
    merge(ctx, messages) {
      for (const message of messages) {
        if (!message.id || message.roomId !== ctx.roomId) continue;
        const previous = ctx.messages.get(message.id);
        ctx.messages.set(message.id, {...message, readBy:[...new Set([...(previous?.readBy || []), ...(message.readBy || [])])]});
      }
    }
    async history(ctx, older) {
      if (!this.current(ctx) || ctx.loading || (older && !ctx.hasMore)) return;
      ctx.loading = true; this.olderButton.disabled = true;
      const previousHeight = this.list.scrollHeight; const previousTop = this.list.scrollTop;
      const ordered = [...ctx.messages.values()].sort(orderMessages);
      let url = `/api/messages/${encodeURIComponent(ctx.roomId)}?limit=100`;
      if (older && ordered.length) {
        const oldest = ordered[0]; url += `&before=${encodeURIComponent(oldest.createdAt)}&beforeId=${encodeURIComponent(oldest.id)}`;
      }
      try {
        const response = await Auth.authFetch(url);
        if (!response.ok) throw new Error((await response.json()).message || '내역 조회 실패');
        const messages = await response.json();
        if (!this.current(ctx)) return;
        this.merge(ctx, messages);
        if (older || !ctx.initialized) ctx.hasMore = messages.length === 100;
        const initial = !ctx.initialized; ctx.initialized = true;
        this.render(ctx, initial);
        if (older) this.list.scrollTop = previousTop + this.list.scrollHeight - previousHeight;
        this.queueRead(ctx);
      } catch (error) { if (this.current(ctx)) this.error(error.message); }
      finally {
        if (this.current(ctx)) {
          ctx.loading = false; this.olderButton.disabled = !ctx.hasMore;
          this.olderButton.textContent = ctx.hasMore ? '이전 메시지 더 보기' : '대화의 시작입니다';
        }
      }
    }
    render(ctx, bottom) {
      if (!this.current(ctx)) return;
      const top = this.list.scrollTop; const fragment = document.createDocumentFragment(); let day = '';
      for (const message of [...ctx.messages.values()].sort(orderMessages)) {
        const date = new Date(message.createdAt); const nextDay = date.toLocaleDateString('ko-KR');
        if (day !== nextDay) { const divider = document.createElement('div'); divider.className = 'day-divider'; divider.textContent = nextDay; fragment.append(divider); day = nextDay; }
        const row = document.createElement('article'); row.className = 'message'; row.dataset.messageId = message.id;
        const avatar = document.createElement('span'); avatar.className = 'avatar'; avatar.setAttribute('aria-hidden','true'); avatar.textContent = Array.from(message.senderName || message.senderId || '?')[0];
        const main = document.createElement('div'); const meta = document.createElement('div'); meta.className = 'message-meta';
        const name = document.createElement('strong'); name.textContent = message.senderName || message.senderId;
        const time = document.createElement('time'); time.dateTime = message.createdAt; time.textContent = date.toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'});
        meta.append(name,time);
        if (message.senderId === Auth.getLoginId()) {
          const read = document.createElement('span'); read.className = 'read-status';
          const count = (message.readBy || []).filter(id => id !== message.senderId).length;
          read.textContent = count ? `읽음 ${count}` : '안 읽음'; meta.append(read);
        }
        const content = document.createElement('div'); content.className = 'message-content'; content.textContent = message.content;
        main.append(meta,content); row.append(avatar,main); fragment.append(row);
      }
      if (!ctx.messages.size) { const empty = document.createElement('p'); empty.className = 'empty'; empty.textContent = '첫 메시지를 남겨 보세요.'; fragment.append(empty); }
      this.list.replaceChildren(fragment); this.list.scrollTop = bottom ? this.list.scrollHeight : top;
    }
    async send() {
      const ctx = this.context; const text = this.input.value.trim();
      if (!ctx?.ready || ctx.sending || !text) return;
      if (text.length > 4000) { this.error('메시지는 4000자 이하여야 합니다.'); return; }
      const draft = this.input.value; ctx.sending = true; this.sendButton.disabled = true; this.error('');
      try {
        // REST response acknowledges persistence; STOMP supplies live fan-out. No automatic resend on ambiguous failure.
        const response = await Auth.authFetch('/api/messages', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({roomId:ctx.roomId,content:text})});
        const result = await response.json(); if (!response.ok) throw new Error(result.message || '전송 실패');
        if (!this.current(ctx)) return;
        this.merge(ctx,[result]); this.render(ctx,true);
        if (this.input.value === draft) this.input.value = '';
      } catch (error) {
        if (this.current(ctx)) this.error(`${error.message} · 입력 내용은 보존했습니다. 중복 전송을 피하려면 대화 내역을 먼저 확인하세요.`);
      } finally { if (this.current(ctx)) { ctx.sending = false; this.sendButton.disabled = !ctx.ready; this.input.focus(); } }
    }
    queueRead(ctx) {
      if (!this.current(ctx)) return;
      clearTimeout(ctx.readTimer); ctx.readTimer = setTimeout(() => this.markVisibleRead(ctx),250);
    }
    async markVisibleRead(ctx) {
      if (!this.current(ctx) || ctx.reading || document.hidden || !document.hasFocus()) return;
      const reader = Auth.getLoginId(); const bounds = this.list.getBoundingClientRect(); const ids = [];
      for (const row of this.list.querySelectorAll('[data-message-id]')) {
        const message = ctx.messages.get(row.dataset.messageId); const rect = row.getBoundingClientRect();
        if (rect.bottom <= bounds.top || rect.top >= bounds.bottom) continue;
        if (message && message.senderId !== reader && !(message.readBy || []).includes(reader)) ids.push(message.id);
        if (ids.length === 100) break;
      }
      if (!ids.length) return; ctx.reading = true;
      try {
        const response = await Auth.authFetch('/api/messages/read',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({roomId:ctx.roomId,messageIds:ids})});
        if (response.ok && this.current(ctx)) {
          for (const id of ids) { const message = ctx.messages.get(id); if (message) message.readBy = [...new Set([...(message.readBy || []),reader])]; }
        }
      } catch (_) { /* Keep unread so the next visible interaction can retry. */ }
      finally { ctx.reading = false; }
    }
  }
  window.ChatPanel = ChatPanel;
  document.addEventListener('DOMContentLoaded', () => {
    if (document.body.dataset.page === 'chat' && Auth.requireLogin()) {
      Auth.attachLogoutButton('logoutBtn');
      const roomId = decodeURIComponent(location.pathname.substring('/chat/'.length));
      new ChatPanel().open(roomId, '대화방');
    }
  });
})();
