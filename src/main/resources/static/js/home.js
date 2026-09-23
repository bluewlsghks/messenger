'use strict';
document.addEventListener('DOMContentLoaded', () => {
  if (!Auth.requireLogin()) return;
  const el = id => document.getElementById(id);
  const list = el('roomList'); let loadVersion = 0;
  function status(text, failure = false) {
    const paragraph = document.createElement('p');
    paragraph.className = failure ? 'text-red-400 text-center mt-8' : 'text-zinc-400 text-center mt-8';
    paragraph.textContent = text; list.replaceChildren(paragraph);
  }
  async function loadRooms() {
    const version = ++loadVersion; status('불러오는 중...');
    try {
      const response = await Auth.authFetch('/api/rooms/my');
      if (!response.ok) throw new Error('방 목록을 불러오지 못했습니다.');
      const rooms = await response.json(); if (version !== loadVersion) return;
      if (!rooms.length) { status('참여 중인 대화방이 없습니다.'); return; }
      const fragment = document.createDocumentFragment();
      for (const room of rooms) {
        const link = document.createElement('a');
        link.className = 'block p-3 bg-zinc-800 hover:bg-zinc-700 rounded-lg';
        link.textContent = `${room.type} | ${room.members.join(', ')}`;
        link.href = `/chat/${encodeURIComponent(room.id)}`; fragment.append(link);
      }
      list.replaceChildren(fragment);
    } catch (error) { if (version === loadVersion) status(error.message, true); }
  }
  function switchTab(tab) {
    const rooms = tab === 'rooms';
    el('roomsTab').classList.toggle('hidden', !rooms); el('friendsTab').classList.toggle('hidden', rooms);
    for (const className of ['text-emerald-400','border-emerald-600','font-semibold']) {
      el('tabRooms').classList.toggle(className, rooms); el('tabFriends').classList.toggle(className, !rooms);
    }
    if (rooms) loadRooms(); else window.FriendsPanel.reload();
  }
  el('tabRooms').addEventListener('click', () => switchTab('rooms'));
  el('tabFriends').addEventListener('click', () => switchTab('friends'));
  el('createGroupBtn').addEventListener('click', async () => {
    const members = el('groupMembers').value.split(',').map(id => id.trim()).filter(Boolean);
    const message = el('groupMsg'); const button = el('createGroupBtn');
    if (!members.length) { message.textContent = '멤버를 입력하세요.'; return; }
    button.disabled = true; message.textContent = '생성 중...';
    try {
      const response = await Auth.authFetch('/api/rooms/group', {
        method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({members})
      });
      if (!response.ok) throw new Error(await response.text());
      const room = await response.json(); location.href = `/chat/${encodeURIComponent(room.id)}`;
    } catch (error) { message.textContent = `생성 실패: ${error.message}`; }
    finally { button.disabled = false; }
  });
  switchTab('rooms');
});
