"use strict";
(() => {
  // The cursor is kept with its in-memory cache, never independently persisted.
  async function reconcile({roomId, state, request, current, apply, maxPages = 10}) {
    for (let count = 0; count < maxPages && current(); count++) {
      const path = `/api/messages/${encodeURIComponent(roomId)}/changes?limit=100`
        + (state.cursor ? `&cursor=${encodeURIComponent(state.cursor)}` : '');
      const page = await request(path);
      if (!current()) return;
      if (!page || !Array.isArray(page.items) || typeof page.cursor !== 'string'
          || typeof page.reset !== 'boolean' || typeof page.hasMore !== 'boolean') {
        throw new Error('동기화 응답 형식이 유효하지 않습니다.');
      }
      if (page.hasMore && !page.reset && page.cursor === state.cursor) {
        throw new Error('동기화 커서가 진행되지 않았습니다.');
      }
      await apply(page);
      if (!current()) return;
      state.cursor = page.cursor; // Advance only after the whole page was merged successfully.
      if (!page.hasMore) return;
    }
  }
  const api = {reconcile};
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.MessageSync = api;
})();
