'use strict';
(() => {
  function friendRow(friend, startDm, doc) {
    const row = doc.createElement('li');
    row.className = 'p-3 flex items-center justify-between hover:bg-zinc-800/60';
    const details = doc.createElement('div');
    const name = doc.createElement('div'); name.className = 'text-sm font-medium';
    name.textContent = friend.userName || friend.userId;
    const id = doc.createElement('div'); id.className = 'text-xs text-zinc-400'; id.textContent = friend.userId;
    details.append(name, id);
    const button = doc.createElement('button'); button.type = 'button'; button.textContent = 'DM';
    button.className = 'px-3 py-1 rounded bg-emerald-700 text-white text-sm';
    button.addEventListener('click', () => startDm(friend.userId));
    row.append(details, button);
    return row;
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = {friendRow};
  if (typeof document === 'undefined') return;

  document.addEventListener('DOMContentLoaded', () => {
    if (!Auth.requireLogin()) return;
    Auth.attachLogoutButton('logoutBtn');
    const el = id => document.getElementById(id);
    const list = el('friendList');
    let loadingVersion = 0;
    function status(message, failure = false) {
      const item = document.createElement('li');
      item.className = failure ? 'p-3 text-red-400' : 'p-3 text-zinc-400';
      item.textContent = message; list.replaceChildren(item);
    }
    async function loadFriends() {
      const version = ++loadingVersion; status('불러오는 중...');
      try {
        const response = await Auth.authFetch('/api/friends');
        if (!response.ok) throw new Error(await response.text());
        const friends = await response.json();
        if (version !== loadingVersion) return;
        if (!friends.length) { status('친구가 없습니다. 위에서 추가해 보세요.'); return; }
        list.replaceChildren(...friends.map(friend => friendRow(friend, startDm, document)));
      } catch (error) {
        if (version === loadingVersion) status(`목록을 불러오지 못했습니다: ${error.message}`, true);
      }
    }
    async function startDm(peerId) {
      try {
        const response = await Auth.authFetch('/api/rooms/dm', {
          method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({peerId})
        });
        if (!response.ok) throw new Error(await response.text());
        const room = await response.json(); location.href = `/chat/${encodeURIComponent(room.id)}`;
      } catch (error) { el('friendMsg').textContent = `DM 시작 실패: ${error.message}`; }
    }
    async function addFriend() {
      const input = el('friendId'); const id = input.value.trim(); const button = el('addFriendBtn');
      if (!id) { el('friendMsg').textContent = '친구 ID를 입력해 주세요.'; return; }
      if (button.disabled) return;
      button.disabled = true; el('friendMsg').textContent = '추가 중...';
      try {
        const response = await Auth.authFetch('/api/friends', {
          method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({friendId:id})
        });
        if (!response.ok) throw new Error(await response.text());
        el('friendMsg').textContent = '친구로 추가되었습니다.';
        input.value = ''; await loadFriends();
      } catch (error) { el('friendMsg').textContent = `추가 실패: ${error.message}`; }
      finally { button.disabled = false; }
    }
    el('addFriendBtn').addEventListener('click', addFriend);
    el('startDmBtn').addEventListener('click', () => {
      const id = el('friendId').value.trim();
      if (id) startDm(id); else el('friendMsg').textContent = '친구 ID를 입력해 주세요.';
    });
    el('reloadBtn').addEventListener('click', loadFriends);
    el('friendId').addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.isComposing && event.keyCode !== 229) { event.preventDefault(); addFriend(); }
    });
    window.FriendsPanel = {reload:loadFriends};
    // The home page loads this panel only when its friends tab is selected.
    if (!el('tabFriends')) loadFriends();
  });
})();
