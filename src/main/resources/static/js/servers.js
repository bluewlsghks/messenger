'use strict';
document.addEventListener('DOMContentLoaded', () => {
  if (!Auth.requireLogin()) return;
  Auth.attachLogoutButton('logoutBtn');
  const el = id => document.getElementById(id);
  const panel = new ChatPanel(); const dialog = el('server-dialog');
  let servers = []; let selected = null; let selectionVersion = 0; let mode = '';
  el('my-name').textContent = Auth.getUserName() || Auth.getLoginId();
  function error(message) { el('app-error').textContent = message || ''; el('app-error').hidden = !message; }
  async function api(path, body) {
    const options = body === undefined ? {} : {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)};
    const response = await Auth.authFetch(path,options);
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || `요청 실패 (${response.status})`);
    return result;
  }
  function drawServers() {
    el('server-list').replaceChildren();
    for (const server of servers) {
      const button = document.createElement('button'); button.className = 'server-icon'; button.title = server.name;
      button.setAttribute('aria-label',server.name); button.classList.toggle('active',selected?.id === server.id);
      button.textContent = Array.from(server.name).slice(0,2).join('');
      button.addEventListener('click',() => select(server.id)); el('server-list').append(button);
    }
  }
  function drawServer() {
    el('server-name').textContent = selected.name;
    const owner = selected.ownerId === Auth.getLoginId();
    el('create-channel').hidden = !owner; el('invite-member').hidden = !owner;
    el('channel-list').replaceChildren();
    for (const channel of selected.channels) {
      const button = document.createElement('button'); button.className = 'channel'; button.textContent = `# ${channel.name}`;
      button.dataset.channelId = channel.id;
      button.addEventListener('click',() => openChannel(channel)); el('channel-list').append(button);
    }
    el('member-list').replaceChildren(); el('member-count').textContent = selected.members.length;
    for (const member of selected.members) {
      const row = document.createElement('div'); row.className = 'member'; row.textContent = member;
      if (member === selected.ownerId) { const badge = document.createElement('small'); badge.textContent = '소유자'; row.append(badge); }
      el('member-list').append(row);
    }
    drawServers();
  }
  function openChannel(channel) {
    for (const button of el('channel-list').children) button.classList.toggle('active',button.dataset.channelId === channel.id);
    panel.open(channel.id,`# ${channel.name}`);
  }
  async function select(id, channelId) {
    const version = ++selectionVersion; error(''); panel.close();
    try {
      const server = await api(`/api/servers/${encodeURIComponent(id)}`);
      if (version !== selectionVersion) return;
      selected = server; drawServer();
      const channel = server.channels.find(item => item.id === channelId) || server.channels[0];
      if (channel) openChannel(channel);
    } catch (failure) { if (version === selectionVersion) error(failure.message); }
  }
  async function refresh(id, channelId) {
    servers = await api('/api/servers'); drawServers();
    if (id || servers[0]) await select(id || servers[0].id,channelId);
  }
  function showDialog(nextMode) {
    mode = nextMode; el('dialog-error').hidden = true;
    const input = el('dialog-input'); input.value = ''; input.readOnly = false;
    input.maxLength = mode === 'join' ? 100 : mode === 'channel' ? 40 : 80;
    el('dialog-title').textContent = {create:'서버 만들기',join:'서버에 참여',channel:'텍스트 채널 만들기'}[mode];
    el('dialog-label').textContent = mode === 'join' ? '초대 코드' : '이름';
    el('dialog-description').textContent = mode === 'join' ? '서버 소유자에게 받은 초대 코드를 붙여 넣으세요.' : mode === 'channel' ? '문자, 숫자, -, _를 사용할 수 있습니다.' : '친구들과 이야기할 공간입니다. 기본 채널 #일반이 함께 생성됩니다.';
    el('dialog-submit').textContent = '확인'; dialog.showModal(); input.focus();
  }
  el('create-server').addEventListener('click',() => showDialog('create'));
  el('join-server').addEventListener('click',() => showDialog('join'));
  el('create-channel').addEventListener('click',() => showDialog('channel'));
  el('dialog-cancel').addEventListener('click',() => dialog.close());
  el('invite-member').addEventListener('click',async () => {
    if (!selected) return;
    const serverId = selected.id; const button = el('invite-member'); button.disabled = true;
    try {
      const invite = await api(`/api/servers/${encodeURIComponent(serverId)}/invites`,{});
      mode = 'invite'; el('dialog-title').textContent = '초대 코드'; el('dialog-label').textContent = '코드를 복사해 친구에게 전달하세요';
      el('dialog-description').textContent = `${new Date(invite.expiresAt).toLocaleString('ko-KR')}까지 유효합니다. 코드를 가진 로그인 사용자는 서버에 참여할 수 있습니다.`;
      const input = el('dialog-input'); input.value = invite.code; input.readOnly = true; input.maxLength = 100;
      el('dialog-submit').textContent = '복사'; el('dialog-error').hidden = true; dialog.showModal(); input.select();
    } catch (failure) { error(failure.message); } finally { button.disabled = false; }
  });
  el('dialog-form').addEventListener('submit',async event => {
    event.preventDefault(); const submit = el('dialog-submit'); submit.disabled = true;
    try {
      const value = el('dialog-input').value.trim();
      if (mode === 'invite') {
        await navigator.clipboard.writeText(value); submit.textContent = '복사됨'; return;
      }
      if (!value) throw new Error('값을 입력해 주세요.');
      if (mode === 'create') { const server = await api('/api/servers',{name:value}); await refresh(server.id); }
      if (mode === 'join') { const server = await api('/api/servers/join',{code:value}); await refresh(server.id); }
      if (mode === 'channel') {
        if (!selected) throw new Error('서버를 선택해 주세요.');
        const id = selected.id; const channel = await api(`/api/servers/${encodeURIComponent(id)}/channels`,{name:value}); await select(id,channel.id);
      }
      dialog.close();
    } catch (failure) { el('dialog-error').textContent = mode === 'invite' ? '자동 복사가 안 되면 코드를 선택해서 직접 복사하세요.' : failure.message; el('dialog-error').hidden = false; }
    finally { submit.disabled = false; }
  });
  refresh().catch(failure => error(failure.message));
});
