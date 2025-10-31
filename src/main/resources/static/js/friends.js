document.addEventListener('DOMContentLoaded', async () => {
  // ✅ 로그인 필수
  if (!Auth.requireLogin()) return;
  Auth.attachLogoutButton('logoutBtn');

  const friendIdEl = document.getElementById('friendId');
  const addBtn = document.getElementById('addFriendBtn');
  const dmBtn = document.getElementById('startDmBtn');
  const msgEl = document.getElementById('friendMsg');
  const listEl = document.getElementById('friendList');
  const reloadBtn = document.getElementById('reloadBtn');

  /** 🔹 친구 목록 불러오기 */
  async function loadFriends() {
    listEl.innerHTML = `<li class="p-3 text-zinc-400">불러오는 중...</li>`;
    try {
      const res = await Auth.authFetch('/api/friends');
      if (!res.ok) throw new Error(await res.text());
      const items = await res.json(); // [{userId, userName}]

      if (!items.length) {
        listEl.innerHTML = `<li class="p-3 text-zinc-400">친구가 없습니다. 위에서 추가해 보세요.</li>`;
        return;
      }

      listEl.innerHTML = '';
      for (const f of items) {
        const li = document.createElement('li');
        li.className = 'p-3 flex items-center justify-between hover:bg-zinc-800/60';
        li.innerHTML = `
          <div>
            <div class="text-sm font-medium">${f.userName || f.userId}</div>
            <div class="text-xs text-zinc-400">${f.userId}</div>
          </div>
          <div class="flex gap-2">
            <button class="px-3 py-1 rounded bg-emerald-700 text-white text-sm" data-peer="${f.userId}">
              DM
            </button>
          </div>
        `;
        li.querySelector('button').onclick = () => startDm(f.userId);
        listEl.appendChild(li);
      }
    } catch (e) {
      listEl.innerHTML = `<li class="p-3 text-red-400">목록을 불러오지 못했습니다: ${e.message}</li>`;
    }
  }

  /** 🔹 친구 추가 */
  async function addFriend() {
    const id = friendIdEl.value.trim();
    if (!id) {
      msgEl.textContent = '친구 ID를 입력해 주세요.';
      return;
    }

    msgEl.textContent = '추가 중...';
    try {
      const res = await Auth.authFetch('/api/friends', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ friendId: id })
      });

      if (!res.ok && res.status !== 204) throw new Error(await res.text());
      msgEl.textContent = '친구로 추가되었습니다.';
      friendIdEl.value = '';
      await loadFriends();
    } catch (e) {
      msgEl.textContent = `추가 실패: ${e.message}`;
    }
  }

  /** 🔹 1:1 DM 시작 */
  async function startDm(peerId) {
    try {
      const res = await Auth.authFetch('/api/rooms/dm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ peerId })
      });

      if (!res.ok) throw new Error(await res.text());
      const room = await res.json(); // {id, type, members}
      location.href = `/chat/${room.id}`;
    } catch (e) {
      alert('DM 시작 실패: ' + e.message);
    }
  }

  /** 🔹 이벤트 등록 */
  addBtn.onclick = addFriend;
  dmBtn.onclick = () => {
    const id = friendIdEl.value.trim();
    if (!id) {
      msgEl.textContent = '친구 ID를 입력해 주세요.';
      return;
    }
    startDm(id);
  };
  reloadBtn.onclick = loadFriends;

  friendIdEl.addEventListener('keydown', e => {
    if (e.key === 'Enter') addFriend();
  });

  /** 🔹 초기 로드 */
  await loadFriends();
});
