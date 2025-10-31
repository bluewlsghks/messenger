document.addEventListener("DOMContentLoaded", async () => {
  if (!Auth.requireLogin()) return;
  Auth.attachLogoutButton("logoutBtn");

  const tabRooms = document.getElementById("tabRooms");
  const tabFriends = document.getElementById("tabFriends");
  const roomsTab = document.getElementById("roomsTab");
  const friendsTab = document.getElementById("friendsTab");
  const roomList = document.getElementById("roomList");

  tabRooms.onclick = () => switchTab("rooms");
  tabFriends.onclick = () => switchTab("friends");

  function switchTab(tab) {
    if (tab === "rooms") {
      roomsTab.classList.remove("hidden");
      friendsTab.classList.add("hidden");
      tabRooms.classList.add("text-emerald-400", "border-emerald-600", "font-semibold");
      tabFriends.classList.remove("text-emerald-400", "border-emerald-600", "font-semibold");
      loadRooms();
    } else {
      roomsTab.classList.add("hidden");
      friendsTab.classList.remove("hidden");
      tabFriends.classList.add("text-emerald-400", "border-emerald-600", "font-semibold");
      tabRooms.classList.remove("text-emerald-400", "border-emerald-600", "font-semibold");
      loadFriends();
    }
  }

  // --- 대화 목록 ---
  async function loadRooms() {
    const token = Auth.getToken();
    roomList.innerHTML = `<p class='text-zinc-400 text-sm text-center'>불러오는 중...</p>`;
    try {
      const res = await fetch("/api/rooms/my", {
        headers: { "Authorization": "Bearer " + token }
      });
      if (!res.ok) throw new Error("방 목록을 불러오지 못했습니다.");
      const rooms = await res.json();

      if (!rooms.length) {
        roomList.innerHTML = `<p class='text-zinc-400 text-center mt-8'>참여 중인 대화방이 없습니다.</p>`;
        return;
      }

      roomList.innerHTML = "";
      rooms.forEach(r => {
        const div = document.createElement("div");
        div.className = "p-3 bg-zinc-800 hover:bg-zinc-700 rounded-lg cursor-pointer";
        div.textContent = `${r.type} | ${r.members.join(", ")}`;
        div.onclick = () => location.href = `/chat/${r.id}`;
        roomList.appendChild(div);
      });
    } catch (err) {
      console.error(err);
      roomList.innerHTML = `<p class='text-red-400 text-center mt-8'>서버 오류가 발생했습니다.</p>`;
    }
  }

  // --- 그룹 생성 ---
  document.getElementById("createGroupBtn").onclick = async () => {
    const input = document.getElementById("groupMembers");
    const msg = document.getElementById("groupMsg");
    const raw = input.value.trim();
    if (!raw) return msg.textContent = "멤버를 입력하세요.";

    const members = raw.split(",").map(s => s.trim()).filter(Boolean);
    msg.textContent = "생성 중...";
    try {
      const res = await Auth.authFetch("/api/rooms/group", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ members })
      });
      if (!res.ok) throw new Error(await res.text());
      const room = await res.json();
      msg.textContent = `생성 완료: ${room.id}`;
      location.href = `/chat/${room.id}`;
    } catch (e) {
      msg.textContent = `생성 실패: ${e.message}`;
    }
  };

  // --- 친구 목록 ---
  async function loadFriends() {
    const listEl = document.getElementById("friendList");
    listEl.innerHTML = `<li class="p-3 text-zinc-400">불러오는 중...</li>`;
    try {
      const res = await Auth.authFetch("/api/friends");
      if (!res.ok) throw new Error(await res.text());
      const items = await res.json();

      if (!items.length) {
        listEl.innerHTML = `<li class="p-3 text-zinc-400">친구가 없습니다. 위에서 추가해 보세요.</li>`;
        return;
      }

      listEl.innerHTML = "";
      for (const f of items) {
        const li = document.createElement("li");
        li.className = "p-3 flex items-center justify-between hover:bg-zinc-800/60";
        li.innerHTML = `
          <div>
            <div class="text-sm font-medium">${f.userName || f.userId}</div>
            <div class="text-xs text-zinc-400">${f.userId}</div>
          </div>
          <div class="flex gap-2">
            <button class="px-3 py-1 rounded bg-emerald-700 text-white text-sm">DM</button>
          </div>
        `;
        li.querySelector("button").onclick = () => startDm(f.userId);
        listEl.appendChild(li);
      }
    } catch (e) {
      listEl.innerHTML = `<li class="p-3 text-red-400">목록을 불러오지 못했습니다: ${e.message}</li>`;
    }
  }

  // --- 친구 추가 ---
  document.getElementById("addFriendBtn").onclick = async () => {
    const idEl = document.getElementById("friendId");
    const msgEl = document.getElementById("friendMsg");
    const id = idEl.value.trim();
    if (!id) return msgEl.textContent = "친구 ID를 입력해 주세요.";

    msgEl.textContent = "추가 중...";
    try {
      const res = await Auth.authFetch("/api/friends", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ friendId: id })
      });
      if (!res.ok && res.status !== 204) throw new Error(await res.text());
      msgEl.textContent = "친구로 추가되었습니다.";
      idEl.value = "";
      await loadFriends();
    } catch (e) {
      msgEl.textContent = `추가 실패: ${e.message}`;
    }
  };

  // --- DM 시작 ---
  document.getElementById("startDmBtn").onclick = () => {
    const id = document.getElementById("friendId").value.trim();
    const msgEl = document.getElementById("friendMsg");
    if (!id) return (msgEl.textContent = "친구 ID를 입력해 주세요.");
    startDm(id);
  };

  async function startDm(peerId) {
    try {
      const res = await Auth.authFetch("/api/rooms/dm", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ peerId })
      });
      if (!res.ok) throw new Error(await res.text());
      const room = await res.json();
      location.href = `/chat/${room.id}`;
    } catch (e) {
      alert("DM 시작 실패: " + e.message);
    }
  }

  document.getElementById("reloadBtn").onclick = loadFriends;
  document.getElementById("friendId").addEventListener("keydown", e => {
    if (e.key === "Enter") document.getElementById("addFriendBtn").click();
  });

  // 최초 탭
  switchTab("rooms");
});
