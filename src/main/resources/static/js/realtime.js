'use strict';
(() => {
  function expiresAt(token){try{const part=token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/');return Number(JSON.parse(atob(part)).exp)*1000||0;}catch(_){return 0;}}
  if(typeof module!=='undefined'&&module.exports)module.exports={expiresAt};
  if(typeof window==='undefined')return;
  class Realtime extends EventTarget{
    constructor(){super();this.ready=false;this.readTopic=null;this.readCallback=null;window.addEventListener('messenger:logout',()=>this.stop());window.addEventListener('pagehide',()=>this.stop());window.addEventListener('storage',event=>{if(event.key==='token'||event.key==='loginId'){this.stop();location.replace('/login');}});}
    state(ready,label){this.ready=ready;this.dispatchEvent(new CustomEvent('state',{detail:{ready,label}}));}
    start(){
      if(!window.StompJs||!window.SockJS){this.state(false,'연결 모듈을 불러오지 못했습니다.');return;}
      this.client=new StompJs.Client({webSocketFactory:()=>new SockJS('/ws-stomp'),reconnectDelay:2000,connectionTimeout:10000,heartbeatIncoming:0,heartbeatOutgoing:10000,debug:()=>{},
        beforeConnect:async()=>{const exp=expiresAt(Auth.getToken()||'');if(exp<=Date.now()){await this.client.deactivate();Auth.clearAuthStorage();location.replace('/login');return;}this.client.connectHeaders={Authorization:`Bearer ${Auth.getToken()}`};clearTimeout(this.expiry);this.expiry=setTimeout(()=>{this.stop();Auth.clearAuthStorage();location.replace('/login');},Math.min(exp-Date.now(),2147483647));this.state(false,'연결 중…');},
        onConnect:()=>{this.client.subscribe('/user/queue/events',frame=>{try{const event=JSON.parse(frame.body);if(event.message?.id&&event.message.roomId===event.roomId)this.dispatchEvent(new CustomEvent('notice',{detail:event}));}catch(_){this.dispatchEvent(new CustomEvent('error',{detail:'새 메시지 형식을 확인하지 못했습니다.'}));}});this.client.subscribe('/user/queue/errors',()=>this.dispatchEvent(new CustomEvent('error',{detail:'채팅 요청을 처리하지 못했습니다.'})));this.installRead();this.state(true,'연결됨');},
        onWebSocketClose:()=>this.state(false,'연결 끊김 · 재연결 중'),onStompError:()=>this.state(false,'연결 거부 · 로그인과 권한을 확인해 주세요')});
      Auth.setStompClient(this.client);this.client.activate();
    }
    setReadTopic(roomId,callback){try{this.readSubscription?.unsubscribe();}catch(_){}this.readSubscription=null;this.readTopic=roomId?`/sub/chat/${roomId}/read`:null;this.readCallback=callback;if(this.client?.connected)this.installRead();}
    installRead(){if(this.readTopic)this.readSubscription=this.client.subscribe(this.readTopic,frame=>{try{this.readCallback?.(JSON.parse(frame.body));}catch(_){}});}
    stop(){clearTimeout(this.expiry);this.ready=false;this.client?.deactivate({force:true});}
  }
  window.Realtime=Realtime;
})();
