'use strict';
(() => {
  function expiresAt(token) {
    try {
      const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return Number(JSON.parse(atob(part)).exp) * 1000 || 0;
    } catch (_) { return 0; }
  }
  function sessionAction(originalUser, storedUser, token, changedKey) {
    if (!['token', 'loginId'].includes(changedKey)) return 'ignore';
    if (!token || storedUser !== originalUser) return 'signout';
    return changedKey === 'token' ? 'reconnect' : 'ignore';
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = {expiresAt, sessionAction};
  if (typeof window === 'undefined') return;

  class Realtime extends EventTarget {
    constructor() {
      super();
      this.identity = Auth.getLoginId();
      this.ready = false;
      this.generation = 0;
      this.readTopic = null;
      this.readCallback = null;
      window.addEventListener('messenger:logout', () => this.stop());
      window.addEventListener('pagehide', () => this.stop());
      window.addEventListener('pageshow', event => {
        // A page restored from the browser's back/forward cache needs a fresh transport.
        if (event.persisted && Auth.getToken() && Auth.getLoginId() === this.identity) this.start();
      });
      window.addEventListener('storage', event => {
        const action = sessionAction(this.identity, Auth.getLoginId(), Auth.getToken(), event.key);
        if (action === 'signout') {
          this.stop();
          location.replace('/login');
        } else if (action === 'reconnect') this.start();
      });
    }

    state(ready, label) {
      this.ready = ready;
      this.dispatchEvent(new CustomEvent('state', {detail: {ready, label}}));
    }

    async start() {
      const generation = ++this.generation;
      const previous = this.client;
      this.client = null;
      this.readSubscription = null;
      clearTimeout(this.expiry);
      this.state(false, '연결 중…');
      if (previous) await previous.deactivate({force: true});
      if (generation !== this.generation) return;
      if (!window.StompJs || !window.SockJS) {
        this.state(false, '연결 모듈을 불러오지 못했습니다.');
        return;
      }
      const current = () => generation === this.generation;
      const client = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws-stomp'),
        reconnectDelay: 2000,
        connectionTimeout: 10000,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 10000,
        debug: () => {},
        beforeConnect: async () => {
          if (!current()) return;
          const token = Auth.getToken() || '';
          const expiry = expiresAt(token);
          if (expiry <= Date.now() || Auth.getLoginId() !== this.identity) {
            this.stop();
            location.replace('/login');
            return;
          }
          client.connectHeaders = {Authorization: `Bearer ${token}`};
          clearTimeout(this.expiry);
          this.expiry = setTimeout(() => {
            if (!current() || Auth.getToken() !== token) return;
            this.stop();
            Auth.clearAuthStorage();
            location.replace('/login');
          }, Math.min(expiry - Date.now(), 2147483647));
          this.state(false, '연결 중…');
        },
        onConnect: () => {
          if (!current()) return;
          client.subscribe('/user/queue/events', frame => {
            if (!current()) return;
            try {
              const event = JSON.parse(frame.body);
              if (event.message?.id && event.message.roomId === event.roomId
                  && ['MESSAGE_CREATED', 'MESSAGE_UPDATED', 'MESSAGE_DELETED'].includes(event.type)) {
                this.dispatchEvent(new CustomEvent('notice', {detail: event}));
              }
            } catch (_) {
              this.dispatchEvent(new CustomEvent('error', {detail: '새 메시지 형식을 확인하지 못했습니다.'}));
            }
          });
          client.subscribe('/user/queue/errors', () => {
            if (current()) this.dispatchEvent(new CustomEvent('error', {detail: '채팅 요청을 처리하지 못했습니다.'}));
          });
          this.installRead();
          this.state(true, '연결됨');
        },
        onWebSocketClose: () => {
          if (current()) this.state(false, '연결 끊김 · 재연결 중');
        },
        onStompError: () => {
          if (current()) this.state(false, '연결 거부 · 로그인과 권한을 확인해 주세요');
        }
      });
      this.client = client;
      Auth.setStompClient(client);
      client.activate();
    }

    setReadTopic(roomId, callback) {
      try { this.readSubscription?.unsubscribe(); } catch (_) {}
      this.readSubscription = null;
      this.readTopic = roomId ? `/sub/chat/${roomId}/read` : null;
      this.readCallback = callback;
      if (this.client?.connected) this.installRead();
    }

    installRead() {
      if (!this.readTopic) return;
      const callback = this.readCallback;
      this.readSubscription = this.client.subscribe(this.readTopic, frame => {
        try { callback?.(JSON.parse(frame.body)); } catch (_) {}
      });
    }

    stop() {
      ++this.generation;
      clearTimeout(this.expiry);
      this.ready = false;
      const client = this.client;
      this.client = null;
      this.readSubscription = null;
      client?.deactivate({force: true});
    }
  }
  window.Realtime = Realtime;
})();
