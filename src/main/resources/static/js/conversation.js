'use strict';
(() => {
  const orderMessages=(a,b)=>(Date.parse(a.createdAt)-Date.parse(b.createdAt))||String(a.id).localeCompare(String(b.id));
  const isComposingEnter=event=>event.key==='Enter'&&!event.shiftKey&&!event.isComposing&&event.keyCode!==229;
  if(typeof module!=='undefined'&&module.exports)module.exports={orderMessages,isComposingEnter};
  if(typeof window==='undefined')return;
  const $=id=>document.getElementById(id);
  class ChatPanel{
    constructor(realtime,notices){
      this.realtime=realtime;this.notices=notices;this.list=$('message-list');this.input=$('message-input');this.context=null;
      $('message-form').onsubmit=event=>{event.preventDefault();this.send();};
      this.input.addEventListener('keydown',event=>{if(isComposingEnter(event)){event.preventDefault();this.send();}});
      this.input.addEventListener('input',()=>{this.saveDraft();this.resize();});
      $('load-older').onclick=()=>this.history(this.context,true);
      $('jump-latest').onclick=()=>{this.list.scrollTop=this.list.scrollHeight;$('jump-latest').hidden=true;this.queueRead(this.context);};
      this.list.addEventListener('scroll',()=>{if(this.context&&!this.virtualFrame){this.virtualFrame=requestAnimationFrame(()=>{this.virtualFrame=null;if(this.context)this.render(this.context,false);});}if(this.atBottom())$('jump-latest').hidden=true;this.queueRead(this.context);},{passive:true});
      window.addEventListener('pagehide',()=>this.saveDraft());window.addEventListener('focus',()=>this.queueRead(this.context));
      document.addEventListener('visibilitychange',()=>this.queueRead(this.context));
      realtime.addEventListener('notice',event=>this.notice(event.detail));
      realtime.addEventListener('state',event=>{const ctx=this.context;if(ctx){$('send-message').disabled=!event.detail.ready||ctx.sending||ctx.blocked;if(event.detail.ready){this.history(ctx,false);this.reconcile(ctx);}}});
      $('emoji-button').onclick=()=>{$('emoji-picker').hidden=!$('emoji-picker').hidden;};
      for(const emoji of ['😀','😊','👍','❤️','🎉','🙏','👀','🚀']){$('emoji-picker').append(UI.button(emoji,()=>{if(this.input.disabled)return;const start=this.input.selectionStart;const end=this.input.selectionEnd;if(this.input.value.length-(end-start)+emoji.length>4000)return;this.input.setRangeText(emoji,start,end,'end');this.saveDraft();this.resize();this.input.focus();$('emoji-picker').hidden=true;},''));}
    }
    current(ctx){return ctx&&this.context===ctx;}
    draftKey(roomId){return `messenger:draft:${Auth.getLoginId()}:${roomId}`;}
    saveDraft(){if(!this.context)return;const key=this.draftKey(this.context.roomId);if(this.input.value)UI.storage.set(key,this.input.value);else UI.storage.remove(key);}
    resize(){this.input.style.height='auto';this.input.style.height=Math.min(this.input.scrollHeight,150)+'px';$('character-count').textContent=`${this.input.value.length} / 4000`;}
    atBottom(){return this.list.scrollHeight-this.list.scrollTop-this.list.clientHeight<100;}
    error(message){$('chat-error').textContent=message||'';$('chat-error').hidden=!message;}
    close(){this.saveDraft();const old=this.context;this.context=null;this.notices.activeRoom=null;if(old)clearTimeout(old.readTimer);this.realtime.setReadTopic(null,null);$('search-panel').hidden=true;this.input.disabled=true;$('send-message').disabled=true;$('emoji-picker').hidden=true;}
    open(roomId,title){
      if(this.context?.roomId===roomId)return;
      this.close();this.error('');this.list.replaceChildren();$('jump-latest').hidden=true;
      const draft=UI.storage.get(this.draftKey(roomId),'');this.input.value=typeof draft==='string'?draft:'';this.input.disabled=false;this.input.placeholder=`${title}에 메시지 보내기`;this.resize();
      const ctx={roomId,messages:new Map(),rows:new Map(),heights:new Map(),attachments:[],replyToId:null,loading:false,sending:false,hasMore:true,initialized:false,reading:false,blocked:false};this.context=ctx;this.notices.activeRoom=roomId;
      $('send-message').disabled=!this.realtime.ready;$('load-older').disabled=true;$('load-older').textContent='이전 메시지 더 보기';
      this.list.append(UI.empty('대화를 불러오는 중…','저장된 메시지를 확인하고 있습니다.'));
      this.realtime.setReadTopic(roomId,receipt=>{if(!this.current(ctx))return;for(const id of receipt.messageIds||[]){const message=ctx.messages.get(id);if(message)message.readBy=[...new Set([...(message.readBy||[]),receipt.readerId])];}this.render(ctx,false);this.notices.refresh();});
      this.history(ctx,false);this.reconcile(ctx);
    }
    merge(ctx,messages){for(const message of messages){if(message.id&&message.roomId===ctx.roomId)ctx.messages.set(message.id,MessageMerge(ctx.messages.get(message.id),message));}}
    notice(event){const ctx=this.context;if(!ctx||ctx.roomId!==event.roomId)return;const bottom=this.atBottom();this.merge(ctx,[event.message]);this.render(ctx,bottom);if(event.type==='MESSAGE_CREATED'&&!bottom)$('jump-latest').hidden=false;if(event.type==='MESSAGE_CREATED')$('announcements').textContent=`${event.message.senderName||event.message.senderId}님의 새 메시지`;this.queueRead(ctx);}
    async history(ctx,older){
      if(!this.current(ctx)||ctx.loading||(older&&!ctx.hasMore))return;ctx.loading=true;$('load-older').disabled=true;
      const height=this.list.scrollHeight;const top=this.list.scrollTop;const bottom=this.atBottom();let url=`/api/messages/${encodeURIComponent(ctx.roomId)}?limit=100`;
      if(older&&ctx.oldest)url+=`&before=${encodeURIComponent(ctx.oldest.createdAt)}&beforeId=${encodeURIComponent(ctx.oldest.id)}`;
      try{const messages=await Auth.request(url);if(!this.current(ctx))return;this.merge(ctx,messages);if(older||!ctx.initialized){ctx.hasMore=messages.length===100;if(messages.length)ctx.oldest=messages[0];}const initial=!ctx.initialized;ctx.initialized=true;ctx.blocked=false;this.render(ctx,initial||(!older&&bottom));if(older)this.list.scrollTop=top+this.list.scrollHeight-height;this.error('');this.queueRead(ctx);}
      catch(error){if(this.current(ctx)){this.error(error.message);if(error.status===403||error.status===404){ctx.blocked=true;this.input.disabled=true;$('send-message').disabled=true;this.list.replaceChildren(UI.empty('이 대화에 접근할 수 없습니다.','서버 참여 여부 또는 대화방을 확인해 주세요.'));}else if(!ctx.initialized)this.list.replaceChildren(UI.empty('대화를 불러오지 못했습니다.',error.message,UI.button('다시 시도',()=>this.history(ctx,false))));}}
      finally{if(this.current(ctx)){ctx.loading=false;$('load-older').disabled=!ctx.hasMore;$('load-older').textContent=ctx.hasMore?'이전 메시지 더 보기':'대화의 시작입니다';}}
    }
    async reconcile(ctx) {
      if(!this.current(ctx)||ctx.reconciling)return;ctx.reconciling=true;let cursor=null,total=0;
      try {
        while(this.current(ctx)) {
          const page=await Auth.request(`/api/messages/${encodeURIComponent(ctx.roomId)}/sync?limit=100${cursor?'&afterId='+encodeURIComponent(cursor):''}`);
          if(!this.current(ctx))return;
          this.merge(ctx,page);total+=page.length;
          // All pages are reconciled; keep only a bounded live window. Older data stays available through history.
          if(ctx.messages.size>1000){const all=[...ctx.messages.values()].sort(orderMessages);for(const item of all.slice(0,all.length-1000)){ctx.messages.delete(item.id);ctx.rows.delete(item.id);ctx.heights.delete(item.id);}ctx.oldest=all.at(-1000);ctx.hasMore=true;}
          if(page.length<100)break;cursor=page.at(-1).id;
        }
        if(this.current(ctx)){this.render(ctx,true);this.notices.refresh();}
      }catch(error){if(this.current(ctx))this.error(`동기화를 완료하지 못했습니다: ${error.message} · 새로고침으로 다시 시도할 수 있습니다.`);}
      finally{ctx.reconciling=false;}
    }
    setReply(message){if(!this.context)return;this.context.replyToId=message.id;document.dispatchEvent(new CustomEvent('messenger:reply',{detail:message}));this.input.focus();}
    async showThread(ctx,rootId){
      const dialog=UI.form({title:'답장 스레드',description:'같은 대화의 답장을 페이지별로 확인합니다.',submitText:'닫기',onSubmit:async()=>{}});
      const list=UI.el('div','thread-results');dialog.form.prepend(list);let after=null;
      const more=UI.button('더 보기',async()=>{more.disabled=true;try{const page=await Auth.request(`/api/messages/${encodeURIComponent(ctx.roomId)}/threads/${encodeURIComponent(rootId)}?limit=100${after?'&afterId='+after:''}`);
        for(const item of page){const row=UI.el('div','thread-row');row.append(UI.el('strong','',item.senderName||item.senderId),UI.el('p','',item.deletedAt?'삭제된 메시지':item.content));if(!item.deletedAt)row.append(UI.button('이 메시지에 답장',()=>{dialog.dialog.close();this.setReply(item);}));list.append(row);}after=page.at(-1)?.id;more.hidden=page.length<100;}catch(e){UI.toast('스레드를 불러오지 못했어요',e.message);}finally{more.disabled=false;}});
      dialog.form.prepend(more);more.click();
    }
    render(ctx,bottom){
      if(!this.current(ctx))return;const top=this.list.scrollTop;const fragment=document.createDocumentFragment();let day='';
      const items=[...ctx.messages.values()].sort(orderMessages);
      const range=MessageWindow.windowRange(items,ctx.heights,bottom?Math.max(0,items.reduce((sum,m)=>sum+(ctx.heights.get(m.id)||100),0)-this.list.clientHeight):top,this.list.clientHeight);
      const spacer=height=>{const node=UI.el('div','virtual-spacer');node.style.height=height+'px';node.setAttribute('aria-hidden','true');return node;};
      fragment.append(spacer(range.top));
      const visibleIds=new Set();
      for(const message of items.slice(range.start,range.end)){visibleIds.add(message.id);
        const date=new Date(message.createdAt);const next=date.toLocaleDateString('ko-KR');if(day!==next){fragment.append(UI.el('div','day-divider',next));day=next;}
        const signature=JSON.stringify([message.version,message.readBy,message.senderName,message.content,message.deletedAt]);let cached=ctx.rows.get(message.id);
        if(!cached||cached.signature!==signature){const row=UI.el('article',`message${message.deletedAt?' deleted':''}`);row.dataset.messageId=message.id;
          const main=UI.el('div','message-main');const meta=UI.el('div','message-meta');const time=UI.el('time','',date.toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'}));time.dateTime=message.createdAt;
          meta.append(UI.el('strong','',message.senderName||message.senderId),time);if(message.editedAt&&!message.deletedAt)meta.append(UI.el('span','edited-label','수정됨'));
          if(message.senderId===Auth.getLoginId()&&!message.deletedAt){const count=(message.readBy||[]).filter(id=>id!==message.senderId).length;meta.append(UI.el('span','read-status',count?`읽음 ${count}`:'안 읽음'));}
          main.append(meta,UI.el('div','message-content',message.deletedAt?'삭제된 메시지입니다.':message.content));
          if(!message.deletedAt){if(message.replyToId)main.append(UI.button('↩ 답장 스레드',()=>this.showThread(ctx,message.threadId||message.replyToId),'text-button'));
            if((message.mentions||[]).includes(Auth.getLoginId()))main.append(UI.el('span','mention-label','나를 멘션함'));
            for(const fileId of message.attachmentIds||[]){const download=UI.button('📎 첨부파일',async()=>{try{const info=await Auth.request(`/api/files/${encodeURIComponent(fileId)}`);const res=await Auth.authFetch(`/api/files/${encodeURIComponent(fileId)}/content`);if(!res.ok)throw new Error('파일을 내려받을 수 없습니다.');const url=URL.createObjectURL(await res.blob()),link=document.createElement('a');link.href=url;link.download=info.name;link.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}catch(e){UI.toast('첨부파일',e.message);}},'attachment-link');main.append(download);}
          }row.append(UI.avatar(message.senderName||message.senderId,message.senderId===Auth.getLoginId()),main);
          if(!message.deletedAt){const tools=UI.el('div','message-tools');tools.append(UI.button('메시지에 답장',()=>this.setReply(message),'icon-button','chat'));tools.append(UI.button('메시지 복사',async()=>{try{await navigator.clipboard.writeText(message.content);UI.toast('복사했습니다.','메시지를 클립보드에 복사했습니다.');}catch(_){UI.toast('복사할 수 없습니다.','메시지 텍스트를 선택해 직접 복사해 주세요.');}},'icon-button','copy'));
            if(message.senderId===Auth.getLoginId()){tools.append(UI.button('메시지 수정',()=>this.edit(ctx,message),'icon-button','edit'),UI.button('메시지 삭제',()=>this.remove(ctx,message),'icon-button','trash'));}row.append(tools);}
          cached={signature,row};ctx.rows.set(message.id,cached);
        }fragment.append(cached.row);
      }
      fragment.append(spacer(range.bottom));for(const id of ctx.rows.keys())if(!visibleIds.has(id))ctx.rows.delete(id);
      if(!ctx.messages.size)fragment.append(UI.empty('첫 대화를 시작해 보세요','메시지를 남기면 이 공간의 대화가 차곡차곡 쌓입니다.'));
      this.list.replaceChildren(fragment);this.list.scrollTop=bottom?this.list.scrollHeight:top;
      for(const id of visibleIds){const row=ctx.rows.get(id)?.row;if(row)ctx.heights.set(id,Math.max(48,row.getBoundingClientRect().height+12));}
    }
    async send(){
      const ctx=this.context;const text=this.input.value.trim()||(ctx?.attachments?.length?'[첨부파일]':'');if(!ctx||ctx.sending||ctx.blocked||!this.realtime.ready||!text)return;
      if(text.length>4000){this.error('메시지는 4000자 이하여야 합니다.');return;}const draft=this.input.value;const key=this.draftKey(ctx.roomId);ctx.sending=true;$('send-message').disabled=true;this.error('');
      const pendingKey=`messenger:pending:${Auth.getLoginId()}:${ctx.roomId}`;
      const payload={roomId:ctx.roomId,content:text,replyToId:ctx.replyToId||null,attachmentIds:(ctx.attachments||[]).map(f=>f.id)};
      const fingerprint=JSON.stringify(payload),previous=UI.storage.get(pendingKey);
      payload.clientRequestId=previous?.fingerprint===fingerprint?previous.requestId:crypto.randomUUID();
      UI.storage.set(pendingKey,{fingerprint,requestId:payload.clientRequestId});
      try{const message=await Auth.request('/api/messages',{method:'POST',body:JSON.stringify(payload)});UI.storage.remove(pendingKey);ctx.attachments=[];ctx.replyToId=null;document.dispatchEvent(new Event('messenger:compose-reset'));if(UI.storage.get(key,'')===draft)UI.storage.remove(key);if(!this.current(ctx))return;this.merge(ctx,[message]);this.render(ctx,true);if(this.input.value===draft)this.input.value='';this.saveDraft();this.resize();}
      catch(error){if(this.current(ctx))this.error(`${error.message} 입력을 보존했습니다. 같은 내용으로 재시도하면 동일 전송 ID를 사용합니다.`);}
      finally{ctx.sending=false;if(this.current(ctx)){$('send-message').disabled=!this.realtime.ready;this.input.focus();}}
    }
    edit(ctx,message){UI.form({title:'메시지 수정',fields:[{name:'content',label:'메시지',type:'textarea',value:message.content,maxLength:4000}],onSubmit:async values=>{try{const changed=await Auth.request(`/api/messages/${encodeURIComponent(ctx.roomId)}/${encodeURIComponent(message.id)}`,{method:'PATCH',body:JSON.stringify({content:values.content,version:message.version||0})});if(this.current(ctx)){this.merge(ctx,[changed]);this.render(ctx,false);}}catch(error){if(error.status===409)this.history(ctx,false);throw error;}}});}
    remove(ctx,message){UI.form({title:'메시지를 삭제할까요?',description:'내용이 지워지고 삭제 표시가 남습니다. 이 작업은 되돌릴 수 없습니다.',fields:[],submitText:'삭제',danger:true,onSubmit:async()=>{const changed=await Auth.request(`/api/messages/${encodeURIComponent(ctx.roomId)}/${encodeURIComponent(message.id)}?version=${message.version||0}`,{method:'DELETE'});if(this.current(ctx)){this.merge(ctx,[changed]);this.render(ctx,false);}this.notices.refresh();}});}
    queueRead(ctx){if(!this.current(ctx))return;clearTimeout(ctx.readTimer);ctx.readTimer=setTimeout(()=>this.markVisibleRead(ctx),250);}
    async markVisibleRead(ctx){
      if(!this.current(ctx)||ctx.reading||document.hidden||!document.hasFocus())return;const reader=Auth.getLoginId();const bounds=this.list.getBoundingClientRect();const ids=[];
      for(const row of this.list.querySelectorAll('[data-message-id]')){const message=ctx.messages.get(row.dataset.messageId);const rect=row.getBoundingClientRect();if(rect.bottom<=bounds.top||rect.top>=bounds.bottom)continue;if(message&&!message.deletedAt&&message.senderId!==reader&&!(message.readBy||[]).includes(reader))ids.push(message.id);if(ids.length===100)break;}
      if(!ids.length)return;ctx.reading=true;
      try{await Auth.request('/api/messages/read',{method:'POST',body:JSON.stringify({roomId:ctx.roomId,messageIds:ids})});if(this.current(ctx)){for(const id of ids){const message=ctx.messages.get(id);if(message)message.readBy=[...new Set([...(message.readBy||[]),reader])];}this.notices.refresh();}}
      catch(_){/* Leave unread; retry on the next visible interaction. */}finally{ctx.reading=false;}
    }
  }
  window.ChatPanel=ChatPanel;
})();
