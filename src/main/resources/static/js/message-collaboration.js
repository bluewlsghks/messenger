"use strict";
(() => {
  const EMOJI = Object.freeze({like:'👍', heart:'❤️', laugh:'😄', party:'🎉', thanks:'🙏', eyes:'👀', rocket:'🚀'});
  const members = (message, key) => [...new Set(message.reactions?.[key] || [])];
  if (typeof module !== 'undefined' && module.exports) module.exports = {EMOJI, members};
  if (typeof window === 'undefined') return;

  class MessageCollaboration {
    constructor(chat) {
      this.chat = chat;
      document.getElementById('show-pins').onclick = () => this.list('pins');
      document.getElementById('show-bookmarks').onclick = () => this.list('bookmarks');
    }
    path(ctx, suffix) { return `/api/messages/${encodeURIComponent(ctx.roomId)}/${suffix}`; }
    async refresh(ctx) {
      if (!this.chat.current(ctx) || ctx.collaborationLoading) return;
      ctx.collaborationLoading = true;
      const generation = ctx.bookmarkGeneration || 0;
      try {
        const ids = [...ctx.messages.keys()].slice(-1000);
        const [capabilities, saved] = await Promise.all([
          Auth.request(this.path(ctx, 'collaboration')),
          Auth.request(this.path(ctx, 'bookmark-status'), {method:'POST', body:JSON.stringify(ids)})
        ]);
        if (!this.chat.current(ctx) || generation !== (ctx.bookmarkGeneration || 0)) return;
        ctx.capabilities = capabilities;
        ctx.bookmarks = new Set(saved.ids);
        ctx.bookmarksKnown = new Set(ids);
        this.chat.render(ctx, false);
      } catch (error) {
        if (this.chat.current(ctx) && [401,403,404].includes(error.status)) this.chat.revoke(ctx, error.message);
      } finally { ctx.collaborationLoading = false; }
    }
    async change(ctx, message, suffix, enable, button, bookmark = false) {
      if (!this.chat.current(ctx) || ctx.blocked) return;
      const operation = message.id + '/' + suffix;
      if (ctx.collaborationPending.has(operation)) return;
      ctx.collaborationPending.add(operation);
      button.disabled = true;
      if (bookmark) ctx.bookmarkGeneration++;
      try {
        const changed = await Auth.request(this.path(ctx, `${encodeURIComponent(message.id)}/${suffix}`),
          {method: enable ? 'PUT' : 'DELETE'});
        if (!this.chat.current(ctx)) return;
        if (bookmark) {
          ctx.bookmarkGeneration++;
          if (enable) ctx.bookmarks.add(message.id); else ctx.bookmarks.delete(message.id);
          ctx.bookmarksKnown.add(message.id);
        } else this.chat.merge(ctx, [changed]);
        this.chat.render(ctx, false);
      } catch (error) {
        if (this.chat.current(ctx)) {
          UI.toast('요청을 처리하지 못했어요', error.message);
          await this.chat.refreshKnown(ctx);
          await this.refresh(ctx);
        }
      } finally {
        ctx.collaborationPending.delete(operation);
        button.disabled = false;
      }
    }
    reactionPicker(ctx, message) {
      const modal = UI.form({title:'메시지 반응', description:'같은 반응을 다시 선택하면 취소됩니다.', submitText:'닫기', onSubmit:async()=>{}});
      this.modal = {ctx, ...modal};
      const choices = UI.el('div', 'reaction-choices');
      for (const [key, emoji] of Object.entries(EMOJI)) {
        const reacted = members(message, key).includes(Auth.getLoginId());
        const button = UI.button(emoji, async () => {
          await this.change(ctx, message, 'reactions/' + key, !reacted, button);
          if (modal.body.contains(modal.form)) modal.dialog.close();
        }, 'reaction-choice');
        button.setAttribute('aria-label', `${emoji} 반응 ${reacted ? '취소' : '추가'}`);
        button.setAttribute('aria-pressed', String(reacted));
        choices.append(button);
      }
      modal.form.prepend(choices);
    }
    reactors(ctx, message) {
      const modal = UI.form({title:'반응한 사람', submitText:'닫기', onSubmit:async()=>{}});
      this.modal = {ctx, ...modal};
      const list = UI.el('div', 'reaction-members');
      for (const [key, emoji] of Object.entries(EMOJI)) {
        const people = members(message, key);
        if (people.length) list.append(UI.el('p', '', `${emoji} ${people.length}명 · ${people.join(', ')}`));
      }
      modal.form.prepend(list);
    }
    decorate(ctx, message, main) {
      if (message.deletedAt) return;
      if (message.pinned) main.append(UI.el('span', 'message-pin', '📌 고정된 메시지'));
      const reactions = UI.el('div', 'message-reactions');
      for (const [key, emoji] of Object.entries(EMOJI)) {
        const people = members(message, key);
        if (!people.length) continue;
        const mine = people.includes(Auth.getLoginId());
        const button = UI.button(`${emoji} ${people.length}`, () => this.change(ctx, message, 'reactions/' + key, !mine, button), 'reaction-chip');
        button.setAttribute('aria-label', `${emoji} 반응 ${people.length}명, ${mine ? '취소' : '추가'}`);
        button.setAttribute('aria-pressed', String(mine));
        button.disabled = !ctx.capabilities?.canReact;
        reactions.append(button);
      }
      if (reactions.children.length) reactions.append(UI.button('반응한 사람', () => this.reactors(ctx, message), 'text-button small-button'));
      main.append(reactions);
      const actions = UI.el('div', 'message-collaboration-actions');
      if (ctx.capabilities?.canReact) actions.append(UI.button('반응', () => this.reactionPicker(ctx, message), 'text-button'));
      const saved = ctx.bookmarks.has(message.id);
      const bookmark = UI.button(saved ? '★ 북마크 해제' : '☆ 북마크 저장', () => this.change(ctx, message, 'bookmark', !saved, bookmark, true), 'text-button');
      bookmark.setAttribute('aria-pressed', String(saved));
      bookmark.disabled = !ctx.bookmarksKnown.has(message.id);
      actions.append(bookmark);
      if (ctx.capabilities?.canPin) {
        const pin = UI.button(message.pinned ? '고정 해제' : '📌 고정', () => this.change(ctx, message, 'pin', !message.pinned, pin), 'text-button');
        pin.setAttribute('aria-pressed', String(message.pinned));
        actions.append(pin);
      }
      main.append(actions);
    }
    async list(kind) {
      const ctx = this.chat.context;
      if (!ctx || ctx.blocked) return;
      const modal = UI.form({title:kind === 'pins' ? '고정 메시지' : '내 북마크',
        description:kind === 'pins' ? '이 대화의 참여자에게 공유됩니다.' : '이 대화에서 나만 저장한 메시지입니다.',
        submitText:'닫기', onSubmit:async()=>{}});
      this.modal = {ctx, kind, ...modal};
      const list = UI.el('div', 'saved-message-list');
      let cursor = null, shown = 0;
      const valid = () => this.chat.current(ctx) && modal.dialog.open && modal.body.contains(modal.form);
      const more = UI.button('더 보기', async () => {
        if (!valid() || more.disabled) return;
        more.disabled = true;
        try {
          const page = await Auth.request(this.path(ctx, kind) + '?limit=50' + (cursor ? '&after=' + encodeURIComponent(cursor) : ''));
          if (!valid()) return;
          for (const message of page.items) {
            const row = UI.el('article', 'saved-message');row.dataset.savedMessageId=message.id;
            row.append(UI.el('strong', '', message.senderName || message.senderId), UI.el('p', '', message.content));
            row.append(UI.button('이 메시지 보기', async () => {
              try {
                const current = await Auth.request(this.path(ctx, 'snapshots'), {method:'POST', body:JSON.stringify([message.id])});
                if (!valid()) return;
                if (!current[0] || current[0].deletedAt) { row.remove(); UI.toast('메시지가 삭제되었습니다.', '목록을 다시 열어 확인해 주세요.'); return; }
                this.chat.merge(ctx, current);
                modal.dialog.close();
                const items = [...ctx.messages.values()].sort((a,b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id));
                this.chat.list.scrollTop = items.slice(0, items.findIndex(m => m.id === message.id)).reduce((sum,m) => sum + (ctx.heights.get(m.id) || 100), 0);
                this.chat.render(ctx, false);this.refresh(ctx);
                this.chat.list.querySelector(`[data-message-id="${message.id}"]`)?.scrollIntoView({block:'center'});
              } catch (error) { if (valid()) UI.toast('메시지를 열지 못했어요', error.message); }
            }, 'text-button'));
            list.append(row); shown++;
          }
          cursor = page.next;
          more.hidden = !page.hasMore;
          if (!shown && !page.hasMore) list.append(UI.el('p', 'muted', '저장된 메시지가 없습니다.'));
        } catch (error) { if (valid()) { list.replaceChildren(UI.el('p', 'error-banner', error.message)); more.hidden = true; } }
        finally { more.disabled = false; }
      }, 'secondary');
      modal.form.prepend(list, more);
      more.click();
    }
    invalidate(ctx, message) {
      if (this.modal?.ctx !== ctx || !this.modal.body.contains(this.modal.form)) return;
      const row=this.modal.form.querySelector(`[data-saved-message-id="${message.id}"]`);
      if (!row) return;
      if (message.deletedAt || this.modal.kind === 'pins' && !message.pinned) row.remove();
      else row.querySelector('p').textContent=message.content;
    }
    close(ctx) {
      if (this.modal?.ctx === ctx && this.modal.body.contains(this.modal.form)) this.modal.dialog.close();
      this.modal = null;
    }
  }
  window.MessageCollaboration = MessageCollaboration;
})();
