"use strict";
(() => {
  class Conference {
    constructor(realtime){
      this.realtime=realtime;this.user=Auth.getLoginId();this.clientId=crypto.randomUUID();this.current=null;this.context={};
      this.joinButton=UI.button('다인 통화 참여',()=>this.join().catch(error=>UI.toast('다인 통화',error.message)),'secondary');this.joinButton.id='conference-join';this.joinButton.hidden=true;
      document.querySelector('#voice-start').after(this.joinButton);
      this.panel=UI.el('section','conference-panel');this.panel.id='conference-panel';this.panel.hidden=true;
      this.title=UI.el('strong');this.status=UI.el('p','muted');this.controls=UI.el('div','conference-controls');this.peers=UI.el('div','conference-peers');
      this.mute=UI.button('마이크 끄기',()=>this.toggleMute(),'secondary');this.mute.id='conference-mute';
      this.cameraButton=UI.button('카메라',()=>this.video('camera').catch(error=>UI.toast('카메라',error.message)),'secondary');this.cameraButton.id='conference-camera';
      this.screenButton=UI.button('화면 공유',()=>this.video('screen').catch(error=>UI.toast('화면 공유',error.message)),'secondary');this.screenButton.id='conference-screen';
      this.leaveButton=UI.button('나가기',()=>this.leave(),'danger-button');this.leaveButton.id='conference-leave';
      this.controls.append(this.mute,this.cameraButton,this.screenButton,this.leaveButton);this.panel.append(this.title,this.status,this.controls,this.peers);document.body.append(this.panel);
      document.addEventListener('messenger:context',event=>{this.context=event.detail;this.joinButton.hidden=!this.context.roomId||this.context.room?.type==='DIRECT';});
      realtime.addEventListener('conference',event=>this.receive(event.detail));
      realtime.addEventListener('state',event=>{const session=this.current;if(!session)return;if(event.detail.ready){clearTimeout(session.connectionTimer);this.refresh(session).catch(()=>{});}
        else{clearTimeout(session.connectionTimer);session.connectionTimer=setTimeout(()=>this.leave('실시간 연결을 복구하지 못했습니다.'),30000);}});
      window.addEventListener('pagehide',()=>this.leave());window.addEventListener('messenger:logout',()=>this.leave());
    }
    live(session){return session&&this.current===session&&!session.closed;}
    async join(){
      if(this.current||window.messengerVoice?.current)throw new Error('기존 통화를 종료한 후 참여하세요.');
      if(!this.realtime.ready||!this.context.roomId)throw new Error('실시간 연결 후 참여하세요.');
      if(!isSecureContext||!navigator.mediaDevices?.getUserMedia)throw new Error('HTTPS 또는 localhost에서 마이크를 사용할 수 있습니다.');
      const session={room:this.context.roomId,epoch:null,closed:false,peers:new Map(),muted:false,pending:[],registered:false};this.current=session;
      this.panel.hidden=false;this.title.textContent='다인 통화 · 최대 4명';this.status.textContent='마이크를 준비하는 중…';this.joinButton.disabled=true;
      try{
        const microphone=window.MediaPreferences?.microphone;
        session.stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,...(microphone?{deviceId:{exact:microphone}}:{})},video:false});
        if(!this.live(session)){session.stream.getTracks().forEach(track=>track.stop());return;}
        session.config=await Auth.request('/api/voice/config');if(!this.live(session))return;
        const view=await Auth.request(`/api/conferences/${encodeURIComponent(session.room)}/join`,{method:'POST',body:JSON.stringify({clientId:this.clientId})});
        session.registered=true;if(!this.live(session)){this.teardown(session);return;}
        this.members(session,view);for(const event of session.pending.splice(0))this.receive(event);
        session.heartbeat=setInterval(()=>this.command(session,'PING').catch(error=>this.leave(error.message)),20000);
        session.poll=setInterval(()=>this.refresh(session).catch(()=>{}),10000);
        this.status.textContent=`${view.members.length}/4명 · 음성 연결 중`;
      }catch(error){if(this.live(session))this.leave(error.message);throw error;}
    }
    async refresh(session){if(!this.live(session)||!session.registered)return;const view=await Auth.request(`/api/conferences/${encodeURIComponent(session.room)}`);if(this.live(session))this.members(session,view);}
    members(session,view){
      if(!this.live(session))return;
      if(session.epoch&&session.epoch!==view.epoch){this.leave('통화방이 종료되거나 새로 만들어졌습니다.');return;}
      const own=view.members.find(member=>member.userId===this.user&&member.clientId===this.clientId);
      if(!own){this.cleanup(session,'통화 참여가 종료되었습니다.');return;}
      session.epoch=view.epoch;
      const active=new Set(view.members.filter(member=>member.userId!==this.user).map(member=>member.userId));
      for(const [user,peer]of session.peers)if(!active.has(user)){this.closePeer(peer);session.peers.delete(user);}
      for(const user of active)if(!session.peers.has(user)){
        const peer=this.peer(session,user);session.peers.set(user,peer);
        if(this.user<user)peer.inbox=peer.inbox.then(()=>this.offer(session,peer,false)).catch(error=>this.peerError(session,peer,error));
      }
      this.status.textContent=`${view.members.length}/4명 · ${view.members.map(member=>member.userId).join(', ')}`;
    }
    peer(session,user){
      const pc=new RTCPeerConnection(session.config),peer={user,pc,inbox:Promise.resolve(),sending:Promise.resolve(),remoteIce:[],localIce:[],localSent:false,restarts:0};
      const audio=session.stream.getAudioTracks()[0];peer.audio=pc.addTransceiver(audio,{direction:'sendrecv',streams:[session.stream]});
      peer.video=pc.addTransceiver('video',{direction:'sendrecv'});if(session.videoTrack)peer.video.sender.replaceTrack(session.videoTrack).catch(()=>{});
      peer.element=UI.el('div','conference-peer');peer.element.dataset.userId=user;peer.media=document.createElement('video');peer.media.autoplay=true;peer.media.playsInline=true;peer.media.controls=true;
      peer.stream=new MediaStream();peer.media.srcObject=peer.stream;peer.element.append(UI.el('strong','',user),peer.media);this.peers.append(peer.element);
      pc.ontrack=event=>{if(!this.live(session))return;if(!peer.stream.getTracks().some(track=>track.id===event.track.id))peer.stream.addTrack(event.track);
        const play=async()=>{if(MediaPreferences?.speaker&&peer.media.setSinkId)await peer.media.setSinkId(MediaPreferences.speaker);await peer.media.play();};
        play().catch(()=>{if(!peer.playButton){peer.playButton=UI.button('음성 재생',()=>play().then(()=>peer.playButton.remove()).catch(error=>UI.toast('재생',error.message)));peer.element.append(peer.playButton);}});};
      pc.onicecandidate=event=>{if(!this.live(session)||!event.candidate)return;peer.localIce.push(event.candidate.toJSON());if(peer.localSent)this.flushLocal(session,peer);};
      pc.onconnectionstatechange=()=>{if(!this.live(session))return;peer.element.dataset.state=pc.connectionState;
        if(pc.connectionState==='connected'){clearTimeout(peer.failureTimer);peer.element.classList.add('connected');}
        if(pc.connectionState==='disconnected'||pc.connectionState==='failed'){clearTimeout(peer.failureTimer);peer.failureTimer=setTimeout(()=>{
          if(!this.live(session)||pc.connectionState==='connected')return;
          if(this.user<user)peer.inbox=peer.inbox.then(()=>this.offer(session,peer,true)).catch(error=>this.peerError(session,peer,error));
          else this.command(session,'RESTART',{targetUserId:user}).catch(error=>this.peerError(session,peer,error));
        },5000);}};
      return peer;
    }
    async offer(session,peer,restart){if(!this.live(session)||peer.pc.signalingState!=='stable')return;
      if(restart&&++peer.restarts>3)throw new Error('재연결에 실패했습니다. 나간 후 다시 참여하세요.');
      peer.localSent=false;peer.localIce=[];const offer=await peer.pc.createOffer({iceRestart:restart});if(!this.live(session))return;
      await peer.pc.setLocalDescription(offer);if(!this.live(session))return;
      await this.command(session,'OFFER',{targetUserId:peer.user,sdp:peer.pc.localDescription.sdp});peer.localSent=true;this.flushLocal(session,peer);
    }
    receive(event){const session=this.current;if(!this.live(session)||event?.type!=='CONFERENCE'||event.roomId!==session.room||event.targetClientId!==this.clientId)return;
      if(!session.registered){if(session.pending.length<100)session.pending.push(event);return;}
      if(event.action==='LEFT'){this.cleanup(session,'통화 참여가 종료되었습니다.');return;}
      if(session.epoch&&event.conference?.epoch!==session.epoch)return;
      if(event.action==='MEMBERS'){this.members(session,event.conference);return;}
      this.members(session,event.conference);const peer=session.peers.get(event.fromUserId);if(!peer)return;
      peer.inbox=peer.inbox.then(()=>this.signal(session,peer,event)).catch(error=>this.peerError(session,peer,error));
    }
    async signal(session,peer,event){if(!this.live(session))return;const pc=peer.pc;
      if(event.action==='RESTART'&&this.user<peer.user){await this.offer(session,peer,true);return;}
      if(event.action==='OFFER'){
        if(this.user<peer.user||pc.remoteDescription?.sdp===event.sdp)return;
        peer.localSent=false;peer.localIce=[];await pc.setRemoteDescription({type:'offer',sdp:event.sdp});if(!this.live(session))return;
        await this.flushRemote(session,peer);const answer=await pc.createAnswer();await pc.setLocalDescription(answer);if(!this.live(session))return;
        await this.command(session,'ANSWER',{targetUserId:peer.user,sdp:pc.localDescription.sdp});peer.localSent=true;this.flushLocal(session,peer);
      }else if(event.action==='ANSWER'){
        if(pc.remoteDescription?.sdp===event.sdp||pc.signalingState!=='have-local-offer')return;
        await pc.setRemoteDescription({type:'answer',sdp:event.sdp});await this.flushRemote(session,peer);
      }else if(event.action==='ICE'&&event.candidate){if(peer.remoteIce.length>=256)throw new Error('연결 후보가 너무 많습니다.');peer.remoteIce.push(event.candidate);await this.flushRemote(session,peer);}
    }
    async flushRemote(session,peer){while(this.live(session)&&peer.pc.remoteDescription&&peer.remoteIce.length){const candidate=peer.remoteIce.shift();
      if(candidate.usernameFragment&&!peer.pc.remoteDescription.sdp.includes('a=ice-ufrag:'+candidate.usernameFragment))continue;
      await peer.pc.addIceCandidate(candidate);}}
    flushLocal(session,peer){peer.sending=peer.sending.then(async()=>{while(this.live(session)&&peer.localSent&&peer.localIce.length)
      await this.command(session,'ICE',{targetUserId:peer.user,candidate:peer.localIce.shift()});}).catch(error=>this.peerError(session,peer,error));}
    command(session,action,body={}){if(!this.live(session))return Promise.resolve();return Auth.request(`/api/conferences/${encodeURIComponent(session.room)}`,{method:'POST',body:JSON.stringify({clientId:this.clientId,action,...body})});}
    peerError(session,peer,error){if(this.live(session)){peer.element.dataset.state='failed';UI.toast('통화 연결',`${peer.user}: ${error.message}`);}}
    toggleMute(){const session=this.current;if(!session?.stream)return;session.muted=!session.muted;session.stream.getAudioTracks().forEach(track=>track.enabled=!session.muted);
      this.mute.textContent=session.muted?'마이크 켜기':'마이크 끄기';this.mute.setAttribute('aria-pressed',String(session.muted));}
    async changeMicrophone(deviceId){const session=this.current;if(!session?.stream)return;
      const stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,...(deviceId?{deviceId:{exact:deviceId}}:{})},video:false});
      if(!this.live(session)){stream.getTracks().forEach(track=>track.stop());return;}
      const next=stream.getAudioTracks()[0];next.enabled=!session.muted;
      try{await Promise.all([...session.peers.values()].map(peer=>peer.audio.sender.replaceTrack(next)));if(!this.live(session)){stream.getTracks().forEach(track=>track.stop());return;}
        session.stream.getTracks().forEach(track=>track.stop());session.stream=stream;
      }catch(error){stream.getTracks().forEach(track=>track.stop());throw error;}
    }
    async video(kind){const session=this.current;if(!this.live(session)||!session.registered)return;
      if(session.videoKind===kind&&session.videoTrack){await this.stopVideo(session);return;}
      if(session.changingVideo)return;session.changingVideo=true;
      try{
        const stream=kind==='screen'?await navigator.mediaDevices.getDisplayMedia({video:true,audio:false}):await navigator.mediaDevices.getUserMedia({video:{width:{ideal:640},height:{ideal:360},frameRate:{ideal:15,max:24}},audio:false});
        if(!this.live(session)){stream.getTracks().forEach(track=>track.stop());return;}
        const track=stream.getVideoTracks()[0];try{await Promise.all([...session.peers.values()].map(peer=>peer.video.sender.replaceTrack(track)));}
        catch(error){stream.getTracks().forEach(track=>track.stop());throw error;}
        if(!this.live(session)){stream.getTracks().forEach(track=>track.stop());return;}
        if(session.videoTrack){session.videoTrack.onended=null;session.videoTrack.stop();}
        session.videoTrack=track;session.videoKind=kind;track.onended=()=>this.stopVideo(session).catch(()=>{});
        if(!session.preview){session.preview=document.createElement('video');session.preview.muted=true;session.preview.autoplay=true;session.preview.playsInline=true;session.preview.className='conference-preview';this.peers.prepend(session.preview);}
        session.preview.srcObject=stream;session.preview.play().catch(()=>{});this.cameraButton.setAttribute('aria-pressed',String(kind==='camera'));this.screenButton.setAttribute('aria-pressed',String(kind==='screen'));
      }finally{session.changingVideo=false;}
    }
    async stopVideo(session){if(!session)return;await Promise.all([...session.peers.values()].map(peer=>peer.video.sender.replaceTrack(null).catch(()=>{})));
      if(session.videoTrack){session.videoTrack.onended=null;session.videoTrack.stop();}session.videoTrack=null;session.videoKind=null;session.preview?.remove();session.preview=null;
      this.cameraButton.setAttribute('aria-pressed','false');this.screenButton.setAttribute('aria-pressed','false');}
    closePeer(peer){clearTimeout(peer.failureTimer);peer.pc.onicecandidate=peer.pc.ontrack=peer.pc.onconnectionstatechange=null;peer.pc.close();peer.media.pause();peer.media.srcObject=null;peer.element.remove();}
    teardown(session){const token=Auth.getToken();if(!token)return;fetch(`/api/conferences/${encodeURIComponent(session.room)}`,{method:'POST',keepalive:true,
      headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({clientId:this.clientId,action:'LEAVE'})}).catch(()=>{});}
    leave(message){const session=this.current;if(!session)return;this.teardown(session);this.cleanup(session,message);}
    cleanup(session,message){if(!this.live(session))return;session.closed=true;this.current=null;clearInterval(session.heartbeat);clearInterval(session.poll);clearTimeout(session.connectionTimer);
      for(const peer of session.peers.values())this.closePeer(peer);session.peers.clear();session.stream?.getTracks().forEach(track=>track.stop());
      if(session.videoTrack){session.videoTrack.onended=null;session.videoTrack.stop();}session.preview?.remove();this.peers.replaceChildren();this.panel.hidden=true;this.joinButton.disabled=false;
      if(message)UI.toast('다인 통화',message);}
  }
  window.Conference=Conference;
  document.addEventListener('DOMContentLoaded',()=>{if(window.messengerRealtime)window.messengerConference=new Conference(window.messengerRealtime);});
})();
