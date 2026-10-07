"use strict";
document.addEventListener('DOMContentLoaded',()=>{
  if(document.body.dataset.page!=='workspace')return;
  const form=document.getElementById('message-form'),composer=form.querySelector('.composer');
  const input=UI.el('input');input.type='file';input.multiple=true;input.accept='.png,.jpg,.jpeg,.webp,.pdf,.txt';input.hidden=true;input.id='attachment-input';
  const add=UI.button('파일 첨부',()=>input.click(),'icon-button');add.textContent='📎';add.id='attach-file';composer.prepend(add,input);
  const pending=UI.el('div','attachment-pending'),reply=UI.el('div','reply-preview');form.prepend(pending,reply);
  const ctx=()=>window.WorkspaceActions?.chat?.context;
  function render(){pending.replaceChildren();reply.replaceChildren();const current=ctx();if(!current)return;
    for(const file of current.attachments||[]){const row=UI.el('span','pending-file',file.name);row.append(UI.button('첨부 취소',async()=>{try{await Auth.request(`/api/files/${encodeURIComponent(file.id)}`,{method:'DELETE'});current.attachments=current.attachments.filter(f=>f.id!==file.id);render();}catch(e){UI.toast('첨부파일',e.message);}},'text-button'));pending.append(row);}
    if(current.replyToId){reply.append(document.createTextNode(`↩ 답장: ${current.replyPreview||current.replyToId}`),UI.button('답장 취소',()=>{current.replyToId=null;render();},'text-button'));}
  }
  input.onchange=async()=>{const current=ctx();if(!current)return;const selected=[...input.files];input.value='';if(selected.length+(current.attachments||[]).length>5){UI.toast('첨부파일','최대 5개까지 첨부할 수 있습니다.');return;}add.disabled=true;
    try{for(const file of selected){if(file.size>10*1024*1024||!file.size)throw new Error('각 파일은 1바이트~10MB여야 합니다.');const body=new FormData();body.append('roomId',current.roomId);body.append('file',file);const saved=await Auth.request('/api/files',{method:'POST',body});if(ctx()!==current){await Auth.request(`/api/files/${saved.id}`,{method:'DELETE'});break;}current.attachments.push(saved);render();}}
    catch(e){UI.toast('첨부파일',e.message);}finally{add.disabled=false;}
  };
  document.addEventListener('messenger:reply',event=>{const current=ctx();if(current)current.replyPreview=(event.detail.content||'').slice(0,100);render();});
  document.addEventListener('messenger:compose-reset',render);document.addEventListener('messenger:context',render);
});
