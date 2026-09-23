'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {friendRow} = require('../../main/resources/static/js/friends.js');

function documentStub() {
  return {createElement(tag) {
    return {
      tag, children:[], events:{}, textContent:'',
      append(...children) { this.children.push(...children); },
      addEventListener(name, handler) { this.events[name] = handler; },
      set innerHTML(value) { throw new Error('Untrusted values must never use HTML parsing'); }
    };
  }};
}
test('friend name and ID containing HTML are rendered only as literal text', () => {
  const friend = {userName:'<img src=x onerror=alert(1)>', userId:'"><script>alert(1)</script>'};
  const row = friendRow(friend, () => {}, documentStub());
  assert.equal(row.children[0].children[0].textContent, friend.userName);
  assert.equal(row.children[0].children[1].textContent, friend.userId);
  assert.equal(row.children[1].tag, 'button');
});
test('DM click uses the exact ID without interpreting HTML or normalizing case', () => {
  let selected;
  const friend = {userId:'Alice', userName:'Alice'};
  const row = friendRow(friend, id => { selected = id; }, documentStub());
  row.children[1].events.click();
  assert.equal(selected,'Alice');
});
