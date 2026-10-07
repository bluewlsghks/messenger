"use strict";
document.addEventListener('DOMContentLoaded', () => {
  if(document.body.dataset.page!=='workspace')return;
  const $=id=>document.getElementById(id), {el,button,form,toast}=UI;
  const run=fn=>async()=>{try{await fn();}catch(error){toast('요청을 완료하지 못했어요',error.message);}};
  let context={},pulseTimer=null,roomTimer=null,lastTyping=0,lastTypingSent=0;
  const clientId=crypto.randomUUID();
  function panel(title,description='') {
    const dialog=form({title,description,submitText:'닫기',onSubmit:async()=>{}});
    const list=el('div','feature-list');dialog.form.prepend(list);return {...dialog,list};
  }
  async function requests() {
    const dialog=panel('친구 요청','상대방의 수락 후 친구로 등록됩니다.');
    const refresh=async()=>{
      const rows=await Auth.request('/api/contacts/requests');dialog.list.replaceChildren();
      if(!rows.length)dialog.list.append(el('p','muted','대기 중인 요청이 없습니다.'));
      for(const row of rows){const item=el('div','feature-row');item.dataset.peerId=row.peerId;
        item.append(el('strong','',row.peerId),el('span','',row.direction==='INCOMING'?'받은 요청':'보낸 요청'));
        for(const [action,label]of(row.direction==='INCOMING'?[['ACCEPT','수락'],['DECLINE','거절']]:[['CANCEL','취소']]))
          item.append(button(label,run(async()=>{await Auth.request(`/api/contacts/requests/${encodeURIComponent(row.peerId)}`,{method:'POST',body:JSON.stringify({action})});await refresh();await WorkspaceActions.reload();})));
        dialog.list.append(item);
      }
    };await refresh();
  }
  async function blocks() {
    const dialog=panel('차단 목록','차단은 DM 전송·1:1 통화·친구 요청을 막습니다. 공유 서버의 과거 대화를 삭제하지는 않습니다.');
    const reload=async()=>{const rows=await Auth.request('/api/contacts/blocks');dialog.list.replaceChildren();
      for(const peer of rows){const item=el('div','feature-row');item.append(el('strong','',peer),button('차단 해제',run(async()=>{await Auth.request(`/api/contacts/blocks/${encodeURIComponent(peer)}`,{method:'DELETE'});await reload();})));dialog.list.append(item);}
      if(!rows.length)dialog.list.append(el('p','muted','차단한 사용자가 없습니다.'));
    };
    dialog.form.prepend(button('사용자 차단',()=>form({title:'사용자를 차단할까요?',description:'친구 관계가 해제되고 새 DM·통화·친구 요청을 차단합니다.',danger:true,submitText:'차단',fields:[{name:'peer',label:'정확한 사용자 아이디',maxLength:100}],onSubmit:async({peer})=>{await Auth.request(`/api/contacts/blocks/${encodeURIComponent(peer.trim())}`,{method:'PUT'});await WorkspaceActions.reload();}})));
    await reload();
  }
  async function sessions() {
    const dialog=panel('로그인 세션','세션을 폐기하면 해당 세션의 토큰으로 API와 실시간 메시지를 사용할 수 없습니다.');
    for(const session of await Auth.request('/api/auth/sessions')){const item=el('div','feature-row');item.append(el('span','',`로그인 ${new Date(session.createdAt).toLocaleString()} · 최근 갱신 ${new Date(session.lastUsedAt).toLocaleString()}`),button('폐기',run(async()=>{await Auth.request(`/api/auth/sessions/${encodeURIComponent(session.id)}`,{method:'DELETE'});item.remove();})));dialog.list.append(item);}
  }
  function manageServer() {
    const s=context.server;if(!s)return;
    const owner=s.ownerId===Auth.getLoginId(), moderator=(s.moderators||[]).includes(Auth.getLoginId());
    const actions=owner?[['TRANSFER','소유권 이전'],['PROMOTE','관리자로 지정'],['DEMOTE','일반 멤버로 변경'],['KICK','멤버 강퇴'],['BAN','멤버 강퇴 및 재참여 차단'],['UNBAN','재참여 차단 해제'],['REVOKE_INVITES','기존 초대 코드 모두 철회'],['DELETE','서버 삭제']]:moderator?[['KICK','멤버 강퇴'],['BAN','멤버 강퇴 및 재참여 차단'],['LEAVE','서버 탈퇴']]:[['LEAVE','서버 탈퇴']];
    const dialog=form({title:`${s.name} 관리`,description:'삭제한 서버의 메시지를 자동으로 지우지는 않지만 접근은 차단됩니다. 소유자는 소유권 이전 후 탈퇴할 수 있습니다.',fields:[
      {name:'action',label:'작업',type:'select',options:actions},
      {name:'targetId',label:'대상 아이디 (대상이 없는 작업은 비워 둡니다)',required:false,maxLength:100},
      {name:'confirmation',label:`서버 삭제 시에만 서버 이름 “${s.name}” 입력`,required:false,maxLength:80}
    ],onSubmit:async body=>{if(body.action==='DELETE'&&body.confirmation!==s.name)throw new Error('서버 이름을 정확히 입력해 주세요.');await Auth.request(`/api/servers/${encodeURIComponent(s.id)}/administration`,{method:'POST',body:JSON.stringify({action:body.action,targetId:body.targetId.trim()||null})});await WorkspaceActions.reload();}});
    const members=el('p','settings-help',`멤버: ${(s.members||[]).join(', ')}`);dialog.form.prepend(members);
    if(owner&&context.roomId)dialog.form.prepend(button('현재 채널 권한 설정',()=>channelPermissions(s)));
  }
  function channelPermissions(s) {
    const c=s.channels.find(channel=>channel.id===context.roomId);if(!c)return;
    const format=v=>v===null||v===undefined?'*':v.join(', ');
    form({title:`#${c.name} 권한`,description:'*는 모든 서버 멤버, 빈 값은 소유자만 허용합니다. 아이디는 쉼표로 구분하세요. 쓰기 권한과 별개로 읽기 권한이 있어야 작성할 수 있습니다.',fields:[
      {name:'readers',label:'읽기 가능 아이디',value:format(c.readers),required:false},
      {name:'writers',label:'쓰기 가능 아이디',value:format(c.writers),required:false}],onSubmit:async body=>{
        const parse=value=>value.trim()==='*'?null:value.split(',').map(v=>v.trim()).filter(Boolean);
        await Auth.request(`/api/servers/${encodeURIComponent(s.id)}/channels/${encodeURIComponent(c.id)}/permissions`,{method:'PUT',body:JSON.stringify({readers:parse(body.readers),writers:parse(body.writers)})});await WorkspaceActions.reload();
      }});
  }
  $('all-friends').parentElement.append(button('친구 요청',run(requests),'text-tab'),button('차단 관리',run(blocks),'text-tab'));
  const administration=button('서버 관리',manageServer,'secondary small-button');$('server-tools').append(administration);
  $('notification-panel').querySelector('.notification-footer').append(button('로그인 세션',run(sessions),'secondary'));
  const typing=el('div','typing-indicator');typing.id='typing-indicator';typing.setAttribute('role','status');$('message-form').before(typing);
  async function heartbeat(typingNow=false){if(!Auth.getToken())return;await Auth.request('/api/presence',{method:'POST',body:JSON.stringify({clientId,roomId:context.roomId||null,typing:typingNow})});}
  async function updateStatuses(){
    if(!Auth.getToken())return;
    const statuses=await Auth.request(context.roomId?`/api/presence/rooms/${encodeURIComponent(context.roomId)}`:'/api/presence/contacts');
    const active=statuses.filter(s=>s.typing&&s.userId!==Auth.getLoginId());typing.textContent=active.length?`${active.map(s=>s.userId).slice(0,3).join(', ')}님이 입력 중…`:'';
    for(const status of statuses){for(const row of document.querySelectorAll('[data-user-id]')){if(row.dataset.userId!==status.userId)continue;let label=row.querySelector('.presence-status');if(!label){label=el('span','presence-status');row.append(label);}label.textContent=status.online?'온라인':'오프라인';label.dataset.online=String(status.online);}}
  }
  document.addEventListener('messenger:context',event=>{context=event.detail;heartbeat(false).catch(()=>{});updateStatuses().catch(()=>{});});
  $('message-input').addEventListener('input',()=>{lastTyping=Date.now();if(lastTyping-lastTypingSent>2500){lastTypingSent=lastTyping;heartbeat(true).catch(()=>{});}});
  function resumePresence(){
    clearInterval(pulseTimer);clearInterval(roomTimer);
    heartbeat(false).catch(()=>{});updateStatuses().catch(()=>{});
    pulseTimer=setInterval(()=>heartbeat(Date.now()-lastTyping<4000).catch(()=>{}),25000);
    roomTimer=setInterval(()=>updateStatuses().catch(()=>{}),4000);
  }
  resumePresence();
  window.addEventListener('pageshow',event=>{if(event.persisted)resumePresence();});
  window.addEventListener('pagehide',()=>{clearInterval(pulseTimer);clearInterval(roomTimer);const token=Auth.getToken();if(token)fetch(`/api/presence/${clientId}`,{method:'DELETE',keepalive:true,headers:{Authorization:`Bearer ${token}`}}).catch(()=>{});});
});
