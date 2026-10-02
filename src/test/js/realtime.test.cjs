const {test} = require('node:test');
const assert = require('node:assert/strict');
const {sessionAction} = require('../../main/resources/static/js/realtime.js');

test('token renewal in another tab reconnects the same user instead of logging out', () => {
  assert.equal(sessionAction('alice', 'alice', 'renewed-token', 'token'), 'reconnect');
});
test('a different signed-in account cannot inherit the old workspace', () => {
  assert.equal(sessionAction('alice', 'bob', 'token', 'loginId'), 'signout');
});
test('logout in one tab disconnects other tabs', () => {
  assert.equal(sessionAction('alice', 'alice', null, 'token'), 'signout');
});
test('preference changes do not restart the WebSocket', () => {
  assert.equal(sessionAction('alice', 'alice', 'token', 'messenger:prefs:alice'), 'ignore');
});
