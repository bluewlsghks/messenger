'use strict';
(() => {
  const REASONS = {DECLINED: '상대방이 통화를 거절했습니다.', HANGUP: '통화가 종료되었습니다.',
    NO_ANSWER: '상대방이 응답하지 않습니다.', CONNECTION_LOST: '연결이 끊겨 통화를 종료했습니다.',
    TIME_LIMIT: '1시간 통화 제한에 도달했습니다. 새 통화를 시작해 주세요.'};
  function stopStream(stream) { stream?.getTracks().forEach(track => track.stop()); }
  async function acquireAudio(getMedia, isCurrent) {
    const stream = await getMedia({audio: {echoCancellation: true, noiseSuppression: true, autoGainControl: true}, video: false});
    if (!isCurrent()) { stopStream(stream); return null; }
    return stream;
  }
  function duration(seconds) {
    const value = Math.max(0, Math.floor(seconds));
    return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
  }
  function ownsCall(call, user, clientId) {
    return call.callerId === user ? call.callerClientId === clientId
      : call.calleeId === user && (!call.calleeClientId || call.calleeClientId === clientId);
  }
  function microphoneError(error) {
    if (['NotAllowedError', 'SecurityError'].includes(error?.name)) return '마이크 권한이 필요합니다. 브라우저 주소창의 사이트 권한을 확인해 주세요.';
    if (error?.name === 'NotFoundError') return '사용할 마이크를 찾지 못했습니다.';
    if (error?.name === 'NotReadableError') return '마이크를 사용할 수 없습니다. 장치 연결과 다른 앱의 사용 여부를 확인해 주세요.';
    return error?.message || '음성통화를 처리하지 못했습니다.';
  }
  function uuid() {
    if (globalThis.crypto.randomUUID) return globalThis.crypto.randomUUID();
    const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
    const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  }

  class VoiceCalls {
    constructor(realtime, nameOf = id => id) {
      window.messengerVoice=this;
      this.realtime = realtime; this.nameOf = nameOf; this.user = Auth.getLoginId();
      this.clientId = uuid(); this.current = null; this.room = null;
      this.$ = id => document.getElementById(id);
      this.audio = this.$('voice-audio');
      this.$('voice-start').onclick = () => this.start();
      this.$('voice-accept').onclick = () => this.accept();
      this.$('voice-decline').onclick = () => this.hangup();
      this.$('voice-end').onclick = () => this.hangup();
      this.$('voice-mute').onclick = () => this.toggleMute();
      this.$('voice-play').onclick = () => this.playAudio(this.current);
      this.$('voice-volume').oninput = event => { this.audio.volume = Number(event.target.value); };
      realtime.addEventListener('voice', event => this.receive(event.detail));
      realtime.addEventListener('state', event => {
        if (!event.detail.ready && this.current) {
          const session=this.current;clearTimeout(session.signalingTimer);
          session.signalingTimer=setTimeout(()=>{if(this.live(session))this.hangup('실시간 연결을 복구하지 못해 통화를 종료했습니다.');},30000);
        } else if(event.detail.ready) {
          if(this.current)clearTimeout(this.current.signalingTimer);
          this.resynchronize().catch(()=>{});
        }
        this.render();
      });
      window.addEventListener('pagehide', () => this.hangup());
      window.addEventListener('messenger:logout', () => this.hangup());
      this.render();
    }
    live(session) { return !!session && this.current === session && !session.closed; }
    setRoom(room) {
      this.room = room?.type === 'DIRECT' && room.members?.length === 2 ? room : null;
      this.render();
    }
    supported() {
      if (!window.isSecureContext) throw new Error('마이크는 HTTPS 또는 localhost에서만 사용할 수 있습니다.');
      if (!navigator.mediaDevices?.getUserMedia || !window.RTCPeerConnection)
        throw new Error('이 브라우저에서는 음성통화를 지원하지 않습니다. 최신 Chrome, Edge, Firefox 또는 Safari를 사용해 주세요.');
    }
    session(view, outgoing) {
      const session = {view, outgoing, phase: outgoing ? 'preparing' : 'ringing', closed: false,
        inbox: Promise.resolve(), outgoingIce: [], incomingIce: [], localSent: false, muted: false};
      this.current = session;
      session.ringTimer = setTimeout(() => { if (this.live(session)) this.hangup(REASONS.NO_ANSWER); }, 50_000);
      this.render(); return session;
    }
    async start() {
      if (this.current || globalThis.messengerConference?.current || !this.room || !this.realtime.ready) return;
      try { this.supported(); } catch (error) { UI.toast('음성통화', error.message); return; }
      const room = this.room;
      const session = this.session({id: uuid(), roomId: room.id, callerId: this.user,
        calleeId: room.members.find(id => id !== this.user), callerClientId: this.clientId,
        calleeClientId: null, status: 'RINGING'}, true);
      try {
        await this.prepare(session);
        if (!this.live(session)) return;
        session.phase = 'ringing'; this.render();
        const view = await Auth.request('/api/voice/calls', {method: 'POST', body: JSON.stringify({
          callId: session.view.id, clientId: this.clientId, roomId: room.id})});
        session.registered = true;
        // The peer may accept before this HTTP response arrives. Never roll back an ACCEPTED event.
        if (!this.live(session)) { this.teardown(session); return; }
        if (!session.accepted) session.view = view;
        this.render();
      } catch (error) { this.fail(session, error); }
    }
    async accept() {
      const session = this.current;
      if (!session || session.outgoing || session.phase !== 'ringing') return;
      try {
        this.supported(); session.phase = 'preparing'; this.render();
        await this.prepare(session);
        if (!this.live(session)) return;
        await this.command(session, 'ACCEPT');
      } catch (error) { this.fail(session, error); }
    }
    async prepare(session) {
      session.stream = await acquireAudio(constraints => navigator.mediaDevices.getUserMedia({...constraints,audio:{...constraints.audio,...(globalThis.MediaPreferences?.microphone?{deviceId:{exact:MediaPreferences.microphone}}:{})}}), () => this.live(session));
      if (!this.live(session)) return;
      const config = await Auth.request('/api/voice/config');
      if (!this.live(session)) return;
      const pc = session.pc = new RTCPeerConnection(config);
      for (const track of session.stream.getAudioTracks()) {
        pc.addTrack(track, session.stream);
        track.onended = () => this.fail(session, new Error('마이크 연결이 끊겼습니다.'));
      }
      pc.onicecandidate = event => {
        if (!event.candidate || !this.live(session)) return;
        session.outgoingIce.push(event.candidate.toJSON());
        if (session.localSent) this.flushLocalIce(session);
      };
      pc.ontrack = event => {
        if (!this.live(session)) return;
        this.audio.srcObject = event.streams[0] || new MediaStream([event.track]);
        this.playAudio(session);
      };
      pc.onconnectionstatechange = () => {
        if (!this.live(session)) return;
        if (pc.connectionState === 'connected') {
          clearTimeout(session.connectTimer); clearTimeout(session.disconnectTimer);
          session.phase = 'connected'; session.startedAt ||= Date.now();
          session.clock ||= setInterval(() => {
            if (this.live(session)) this.$('voice-duration').textContent = duration((Date.now() - session.startedAt) / 1000);
          }, 1000);
          this.render();
        } else if (pc.connectionState === 'failed') this.restart(session).catch(error=>this.fail(session,error));
        else if (pc.connectionState === 'disconnected') {
          session.phase = 'reconnecting'; this.render();
          clearTimeout(session.disconnectTimer);
          session.disconnectTimer = setTimeout(() => this.restart(session).catch(error=>this.fail(session,error)), 5000);
        }
      };
    }
    receive(event) {
      const view = event?.call;
      if (event?.type !== 'VOICE_CALL' || !view?.id || event.roomId !== view.roomId
          || ![view.callerId, view.calleeId].includes(this.user)) return;
      if (event.targetClientId && event.targetClientId !== this.clientId) return;
      let session = this.current;
      if (event.action === 'RING') {
        if (view.calleeId !== this.user) return;
        if (session?.view.id === view.id) return;
        if (session) { this.teardown({view}); return; }
        session = this.session(view, false);
        globalThis.MediaPreferences?.ring?.();
        UI.toast('음성통화 수신', `${this.nameOf(view.callerId)}님이 통화를 요청했습니다.`);
        return;
      }
      if (!session || session.view.id !== view.id) return;
      if (event.action === 'ENDED') { this.cleanup(session, REASONS[event.reason] || REASONS.HANGUP); return; }
      if (!ownsCall(view, this.user, this.clientId)) {
        this.cleanup(session, '다른 탭에서 통화를 수락했습니다.'); return;
      }
      session.view = view;
      if (event.action === 'ACCEPTED') {
        if (session.accepted) return;
        globalThis.MediaPreferences?.stopRing?.();
        session.accepted = true; session.registered = true; session.phase = 'connecting';
        clearTimeout(session.ringTimer);
        session.connectTimer = setTimeout(() => this.fail(session, new Error('음성 연결 시간이 초과되었습니다. 네트워크와 TURN 설정을 확인해 주세요.')), 30_000);
        session.heartbeat = setInterval(() => {
          if (this.live(session)) this.command(session, 'PING').catch(error => this.fail(session, error));
        }, 20_000);
        this.render();
      }
      // Serializing incoming negotiation prevents addIceCandidate racing setRemoteDescription.
      session.inbox = session.inbox.then(() => this.signal(session, event)).catch(error => this.fail(session, error));
    }
    async signal(session, event) {
      if (!this.live(session)) return;
      const pc = session.pc;
      if(event.action==='RESTART_REQUEST'&&session.outgoing){await this.restart(session);return;}
      if(event.action==='RESTART_OFFER'&&!session.outgoing) {
        if(pc.remoteDescription?.sdp===event.sdp)return;
        session.localSent=false;session.outgoingIce=[];
        await pc.setRemoteDescription({type:'offer',sdp:event.sdp});if(!this.live(session))return;
        await this.flushRemoteIce(session);const answer=await pc.createAnswer();await pc.setLocalDescription(answer);
        if(!this.live(session))return;
        await this.command(session,'RESTART_ANSWER',{sdp:pc.localDescription.sdp});session.localSent=true;this.flushLocalIce(session);return;
      }
      if(event.action==='RESTART_ANSWER'&&session.outgoing) {
        if(pc.remoteDescription?.sdp===event.sdp||pc.signalingState!=='have-local-offer')return;
        await pc.setRemoteDescription({type:'answer',sdp:event.sdp});if(this.live(session))await this.flushRemoteIce(session);return;
      }
      if (event.action === 'ACCEPTED' && session.outgoing) {
        if (session.offerStarted) return;
        session.offerStarted = true;
        const offer = await pc.createOffer();
        if (!this.live(session)) return;
        await pc.setLocalDescription(offer);
        if (!this.live(session)) return;
        await this.command(session, 'OFFER', {sdp: pc.localDescription.sdp});
        if (!this.live(session)) return;
        session.localSent = true; this.flushLocalIce(session);
      } else if (event.action === 'OFFER' && !session.outgoing) {
        if(pc.remoteDescription?.sdp===event.sdp)return;
        await pc.setRemoteDescription({type: 'offer', sdp: event.sdp});
        if (!this.live(session)) return;
        await this.flushRemoteIce(session);
        const answer = await pc.createAnswer();
        if (!this.live(session)) return;
        await pc.setLocalDescription(answer);
        if (!this.live(session)) return;
        await this.command(session, 'ANSWER', {sdp: pc.localDescription.sdp});
        if (!this.live(session)) return;
        session.localSent = true; this.flushLocalIce(session);
      } else if (event.action === 'ANSWER' && session.outgoing) {
        if(pc.remoteDescription?.sdp===event.sdp)return;
        await pc.setRemoteDescription({type: 'answer', sdp: event.sdp});
        if (this.live(session)) await this.flushRemoteIce(session);
      } else if (event.action === 'ICE' && event.candidate) {
        if (session.incomingIce.length >= 256) throw new Error('연결 후보 수 제한을 초과했습니다.');
        session.incomingIce.push(event.candidate);
        if (pc?.remoteDescription) await this.flushRemoteIce(session);
      }
    }
    async resynchronize() {
      const view=await Auth.request('/api/voice/current');
      const session=this.current;
      if(!view){if(session?.registered)this.cleanup(session,'통화가 종료되었습니다.');return;}
      if(view.status==='RINGING' && view.calleeId===this.user && !session)
        this.receive({type:'VOICE_CALL',action:'RING',roomId:view.roomId,call:view});
      else if(view.status==='ACCEPTED' && session?.view.id===view.id && !session.accepted)
        this.receive({type:'VOICE_CALL',action:'ACCEPTED',roomId:view.roomId,call:view});
    }
    async restart(session) {
      if(!this.live(session)||!session.accepted)return;
      if(!this.realtime.ready)throw new Error('신호 연결을 복구한 후 다시 통화해 주세요.');
      if(session.restarting)return;
      if((session.restartCount||0)>=3)throw new Error('재연결 한도를 초과했습니다. 다른 네트워크에서는 TURN 설정이 필요할 수 있습니다.');
      session.restarting=true;session.restartCount=(session.restartCount||0)+1;
      try {
        session.phase='reconnecting';this.render();
        if(!session.outgoing){await this.command(session,'RESTART_REQUEST');}
        else {
          session.localSent=false;session.outgoingIce=[];
          const offer=await session.pc.createOffer({iceRestart:true});if(!this.live(session))return;
          await session.pc.setLocalDescription(offer);if(!this.live(session))return;
          await this.command(session,'RESTART_OFFER',{sdp:session.pc.localDescription.sdp});
          session.localSent=true;this.flushLocalIce(session);
        }
        clearTimeout(session.restartTimer);session.restartTimer=setTimeout(()=>{if(this.live(session)&&session.pc.connectionState!=='connected')this.fail(session,new Error('음성 연결을 복구하지 못했습니다.'));},20000);
      } finally {session.restarting=false;}
    }
    async changeMicrophone(deviceId) {
      const session=this.current;if(!session?.stream)return;
      const stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,...(deviceId?{deviceId:{exact:deviceId}}:{})},video:false});
      if(!this.live(session)){stopStream(stream);return;}
      const track=stream.getAudioTracks()[0],sender=session.pc?.getSenders().find(sender=>sender.track?.kind==='audio');
      try {if(sender)await sender.replaceTrack(track);if(!this.live(session)){stopStream(stream);return;}
        const old=session.stream;old.getTracks().forEach(track=>track.onended=null);session.stream=stream;track.enabled=!session.muted;
        track.onended=()=>this.fail(session,new Error('마이크 연결이 끊겼습니다.'));stopStream(old);
      } catch(error){stopStream(stream);throw error;}
    }
    async flushRemoteIce(session) {
      while (this.live(session) && session.pc?.remoteDescription && session.incomingIce.length) {
        const candidate=session.incomingIce.shift();
        if(candidate.usernameFragment && !session.pc.remoteDescription.sdp.includes('a=ice-ufrag:'+candidate.usernameFragment))continue;
        await session.pc.addIceCandidate(candidate);
      }
    }
    async flushLocalIce(session) {
      if (session.flushing || !session.localSent || !this.live(session)) return;
      session.flushing = true;
      try {
        while (this.live(session) && session.outgoingIce.length)
          await this.command(session, 'ICE', {candidate: session.outgoingIce.shift()});
      } catch (error) { this.fail(session, error); }
      finally { session.flushing = false; }
    }
    command(session, action, data = {}) {
      if (!this.live(session)) return Promise.resolve();
      return Auth.request(`/api/voice/calls/${encodeURIComponent(session.view.id)}`, {
        method: 'POST', body: JSON.stringify({clientId: this.clientId, action, ...data})});
    }
    teardown(session) {
      const token = Auth.getToken();
      if (!token || !session?.view?.id) return;
      // Best effort on navigation/logout; server leases also reclaim abruptly closed tabs.
      fetch(`/api/voice/calls/${encodeURIComponent(session.view.id)}`, {method: 'POST', keepalive: true,
        headers: {'Content-Type': 'application/json', Authorization: `Bearer ${token}`},
        body: JSON.stringify({clientId: this.clientId, action: 'END'})}).catch(() => {});
    }
    hangup(message) {
      const session = this.current;
      if (!session) return;
      this.teardown(session); this.cleanup(session, message);
    }
    fail(session, error) {
      if (!this.live(session)) return;
      this.teardown(session); this.cleanup(session, microphoneError(error));
    }
    cleanup(session, message) {
      if (!this.live(session)) return;
      session.closed = true; this.current = null; globalThis.MediaPreferences?.stopRing?.();
      for (const key of ['ringTimer', 'connectTimer', 'disconnectTimer','signalingTimer','restartTimer']) clearTimeout(session[key]);
      for (const key of ['heartbeat', 'clock']) clearInterval(session[key]);
      if (session.pc) {
        session.pc.onicecandidate = session.pc.ontrack = session.pc.onconnectionstatechange = null;
        session.pc.close();
      }
      session.stream?.getTracks().forEach(track => { track.onended = null; });
      stopStream(session.stream); session.outgoingIce = []; session.incomingIce = [];
      this.audio.pause(); this.audio.srcObject = null; this.render();
      if (message) UI.toast('음성통화', message);
    }
    toggleMute() {
      const session = this.current;
      if (!session?.stream) return;
      session.muted = !session.muted;
      session.stream.getAudioTracks().forEach(track => { track.enabled = !session.muted; });
      this.render();
    }
    async playAudio(session) {
      if (!this.live(session) || !this.audio.srcObject) return;
      try { if(globalThis.MediaPreferences?.speaker && this.audio.setSinkId) await this.audio.setSinkId(MediaPreferences.speaker); await this.audio.play(); if (this.live(session)) this.$('voice-play').hidden = true; }
      catch (_) { if (this.live(session)) this.$('voice-play').hidden = false; }
    }
    render() {
      const session = this.current;
      this.$('voice-start').hidden = !this.room;
      this.$('voice-start').disabled = !!session || !this.realtime.ready;
      this.$('voice-panel').hidden = !session;
      if (!session) { this.$('voice-play').hidden = true; this.$('voice-duration').textContent = '00:00'; return; }
      this.$('voice-peer').textContent = this.nameOf(session.outgoing ? session.view.calleeId : session.view.callerId);
      const ringing = session.phase === 'ringing' && !session.outgoing;
      this.$('voice-state').textContent = {preparing: '마이크를 준비하는 중…', ringing: session.outgoing ? '상대방의 응답을 기다리는 중…' : '음성통화가 왔습니다.',
        connecting: '음성을 연결하는 중…', connected: '통화 중', reconnecting: '음성 연결을 복구하는 중…'}[session.phase] || '연결 중…';
      this.$('voice-accept').hidden = !ringing; this.$('voice-decline').hidden = !ringing;
      this.$('voice-end').hidden = ringing;
      this.$('voice-mute').hidden = !session.stream;
      this.$('voice-mute').textContent = session.muted ? '마이크 켜기' : '마이크 끄기';
      this.$('voice-mute').setAttribute('aria-pressed', String(session.muted));
      this.$('voice-output').hidden = !session.accepted;
    }
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = {VoiceCalls, acquireAudio, stopStream, duration, ownsCall, microphoneError};
  if (typeof window !== 'undefined') window.VoiceCalls = VoiceCalls;
})();
