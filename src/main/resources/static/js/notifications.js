'use strict';
(() => {
  const defaults={desktop:false,inApp:true,sound:false,preview:false,theme:'dark',muted:[]};
  function shouldNotify(event,{me,activeRoom,visible,focused,muted=[]}){return event.type==='MESSAGE_CREATED'&&event.message?.senderId!==me&&!muted.includes(event.roomId)&&!(event.roomId===activeRoom&&visible&&focused);}
  function mergeVersion(previous,incoming){if(previous&&(previous.version||0)>(incoming.version||0))return previous;return {...incoming,readBy:[...new Set([...(previous?.readBy||[]),...(incoming.readBy||[])])]};}
  if(typeof module!=='undefined'&&module.exports)module.exports={shouldNotify,mergeVersion,defaults};
  if(typeof window==='undefined')return;
  class NotificationCenter extends EventTarget{
    constructor(realtime,onOpen){super();this.me=Auth.getLoginId();this.onOpen=onOpen;this.key=`messenger:prefs:${this.me}`;this.prefs={...defaults,...UI.storage.get(this.key,{})};if(!Array.isArray(this.prefs.muted))this.prefs.muted=[];this.records=[];this.seen=new Set();this.counts={};this.serial=0;this.activeRoom=null;document.documentElement.dataset.theme=this.prefs.theme==='light'?'light':'dark';realtime.addEventListener('notice',e=>this.receive(e.detail));realtime.addEventListener('state',e=>{if(e.detail.ready)this.refresh();});document.addEventListener('visibilitychange',()=>{if(!document.hidden)this.refresh();});this.render();}
    save(){UI.storage.set(this.key,this.prefs);document.documentElement.dataset.theme=this.prefs.theme==='light'?'light':'dark';}
    muted(roomId){return this.prefs.muted.includes(roomId);}
    toggleMute(roomId){this.prefs.muted=this.muted(roomId)?this.prefs.muted.filter(id=>id!==roomId):[...this.prefs.muted,roomId];this.save();return this.muted(roomId);}
    async refresh(){const serial=++this.serial;try{const counts=await Auth.request('/api/notifications/unread');if(serial!==this.serial)return;this.counts=counts;const total=Object.values(counts).reduce((sum,n)=>sum+Number(n||0),0);const badge=document.getElementById('total-unread');if(badge){badge.textContent=total>99?'99+':String(total);badge.hidden=!total;}document.title=total?`(${total}) Messenger`:'Messenger';this.dispatchEvent(new CustomEvent('counts',{detail:counts}));}catch(_){/* Retain the last confirmed badge until connectivity returns. */}}
    receive(event){
      if(event.type!=='MESSAGE_CREATED'){this.records=this.records.map(item=>item.message.id===event.message.id?{...item,message:mergeVersion(item.message,event.message)}:item).filter(item=>!item.message.deletedAt);this.render();this.refresh();return;}
      if(this.seen.has(event.message.id))return;this.seen.add(event.message.id);if(this.seen.size>1000)this.seen.delete(this.seen.values().next().value);this.refresh();
      if(event.message.senderId===this.me)return;
      this.records.unshift(event);this.records=this.records.slice(0,50);this.render();
      if(!shouldNotify(event,{me:this.me,activeRoom:this.activeRoom,visible:!document.hidden,focused:document.hasFocus(),muted:this.prefs.muted}))return;
      const name=event.message.senderName||event.message.senderId;const text=this.prefs.preview?event.message.content.slice(0,160):`${event.label}에 새 메시지가 도착했습니다.`;
      if(this.prefs.inApp)UI.toast(name,text,()=>this.onOpen(event));
      if(this.prefs.desktop&&(!document.hasFocus()||document.hidden))this.desktop(event,name,text);
      if(this.prefs.sound)this.playSound();
    }
    async desktop(event,title,body){
      if(!window.isSecureContext||!('Notification'in window)||Notification.permission!=='granted')return;
      const display=()=>{const key=`messenger:notice-claims:${this.me}`;const claims=UI.storage.get(key,{});const now=Date.now();for(const id of Object.keys(claims)){if(now-claims[id]>120000)delete claims[id];}if(claims[event.message.id])return;claims[event.message.id]=now;UI.storage.set(key,claims);try{const notice=new Notification(`${title} · Messenger`,{body,tag:`messenger:${this.me}:${event.roomId}`});notice.onclick=()=>{window.focus();this.onOpen(event);notice.close();};setTimeout(()=>notice.close(),12000);}catch(_){if(!this.unsupportedShown){UI.toast('시스템 알림을 사용할 수 없습니다.','앱 안 알림과 읽지 않은 메시지 배지는 계속 사용할 수 있어요.');this.unsupportedShown=true;}}};
      try{if(navigator.locks)await navigator.locks.request(`messenger:notifications:${this.me}`,display);else display();}catch(_){/* Permission or window lifecycle changed. */}
    }
    async enableDesktop(){if(!('Notification'in window)){UI.toast('알림을 지원하지 않는 브라우저입니다.','앱 안 알림은 계속 표시됩니다.');return false;}if(!window.isSecureContext){UI.toast('보안 연결이 필요합니다.','데스크톱 알림은 HTTPS 또는 localhost에서 설정할 수 있습니다.');return false;}const permission=await Notification.requestPermission();this.prefs.desktop=permission==='granted';this.save();UI.toast(this.prefs.desktop?'데스크톱 알림을 켰습니다.':'데스크톱 알림이 허용되지 않았습니다.',this.prefs.desktop?'다른 앱을 사용하는 동안 새 메시지를 알려드려요.':'브라우저 사이트 설정에서 알림 권한을 변경할 수 있습니다.');return this.prefs.desktop;}
    async enableSound(){try{const Context=window.AudioContext||window.webkitAudioContext;if(!Context)return;this.audio=this.audio||new Context();await this.audio.resume();}catch(_){UI.toast('알림음을 재생할 수 없습니다.','브라우저의 소리 허용 설정을 확인해 주세요.');}}
    playSound(){if(this.audio?.state!=='running')return;const oscillator=this.audio.createOscillator();const gain=this.audio.createGain();oscillator.connect(gain);gain.connect(this.audio.destination);oscillator.frequency.value=660;gain.gain.setValueAtTime(.035,this.audio.currentTime);gain.gain.exponentialRampToValueAtTime(.001,this.audio.currentTime+.18);oscillator.start();oscillator.stop(this.audio.currentTime+.18);}
    render(){const list=document.getElementById('notification-list');if(!list)return;list.replaceChildren();if(!this.records.length){list.append(UI.empty('아직 새 알림이 없어요','다른 사람이 보낸 새 메시지가 여기에 표시됩니다.'));return;}for(const event of this.records){const item=UI.button('대화 열기',()=>{this.onOpen(event);document.getElementById('notification-panel').hidden=true;},'notice-item');item.replaceChildren();const main=UI.el('div');main.append(UI.el('strong','',event.message.senderName||event.message.senderId),UI.el('p','',this.prefs.preview?event.message.content.slice(0,120):event.label),UI.el('time','',new Date(event.message.createdAt).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'})));item.append(UI.avatar(event.message.senderName||event.message.senderId),main);list.append(item);}}
    clear(){this.records=[];this.render();}
  }
  window.MessageMerge=mergeVersion;window.NotificationCenter=NotificationCenter;
})();
