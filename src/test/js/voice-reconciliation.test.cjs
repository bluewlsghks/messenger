'use strict';
const {test, afterEach} = require('node:test');
const assert = require('node:assert/strict');
const {VoiceCalls} = require('../../main/resources/static/js/voice-call.js');
const makeView = (id = 'call') => ({id, roomId: 'dm', callerId: 'alice', calleeId: 'bob', callerClientId: 'tab-a', calleeClientId: null, status: 'RINGING'});
function harness(outgoing = true) {
  const voice = Object.create(VoiceCalls.prototype);
  voice.user = outgoing ? 'alice' : 'bob'; voice.clientId = outgoing ? 'tab-a' : 'tab-b';
  voice.nameOf = id => id; voice.render = () => {}; voice.realtime = {ready: true};
  voice.audio = {pause() {}, srcObject: {}}; voice.teardowns = [];
  voice.teardown = session => voice.teardowns.push(session.view.id);
  global.UI = {toast() {}};
  voice.current = {view: makeView(), outgoing, phase: 'ringing', inbox: Promise.resolve(),
    outgoingIce: [], incomingIce: [], closed: false};
  return voice;
}
afterEach(() => { delete global.Auth; delete global.UI; });

test('missed terminal event is reconciled without sending another hangup', async () => {
  const voice = harness(), session = voice.current;
  session.registered = true; let closed = 0, stopped = 0;
  session.pc = {close() { closed++; }};
  session.stream = {getTracks: () => [{stop() { stopped++; }}]};
  global.Auth = {request: async path => { assert.equal(path, '/api/voice/current'); return null; }};
  await voice.reconcileSession(session);
  assert.equal(voice.current, null); assert.equal(closed, 1); assert.equal(stopped, 1);
  assert.equal(voice.teardowns.length, 0);
});
test('pending unregistered start is not mistaken for an ended call', async () => {
  const voice = harness(), session = voice.current;
  global.Auth = {request() { assert.fail('start is not registered yet'); }};
  await voice.resynchronize(); await voice.reconcileSession(session);
  assert.equal(voice.current, session);
});
test('late empty snapshot cannot terminate a newer session', async () => {
  const voice = harness(); voice.current.registered = true;
  let respond; global.Auth = {request: () => new Promise(resolve => { respond = resolve; })};
  const pending = voice.resynchronize();
  const replacement = {...voice.current, view: makeView('new')}; voice.current = replacement;
  respond(null); await pending;
  assert.equal(voice.current, replacement); assert.equal(voice.synchronizing, false);
});
test('late snapshot cannot undo an accepted event on the same session', async () => {
  const voice = harness(), session = voice.current; session.registered = true;
  let respond; global.Auth = {request: () => new Promise(resolve => { respond = resolve; })};
  const pending = voice.resynchronize();
  session.view = {...session.view, status: 'ACCEPTED', calleeClientId: 'tab-b'};
  session.accepted = true; session.phase = 'connected';
  respond(null); await pending;
  assert.equal(voice.current, session); assert.equal(session.phase, 'connected');
});
test('reconciliation closes only this tab after another tab accepts', async () => {
  const voice = harness(false), session = voice.current; session.registered = true;
  global.Auth = {request: async () => ({...makeView(), status: 'ACCEPTED', calleeClientId: 'other-tab'})};
  voice.prepare = () => assert.fail('no unsolicited microphone access');
  await voice.resynchronize();
  assert.equal(voice.current, null); assert.equal(voice.teardowns.length, 0);
});
test('state requests never overlap and errors do not end an active call', async () => {
  const voice = harness(), session = voice.current; session.registered = true;
  let reject, count = 0;
  global.Auth = {request: () => { count++; return new Promise((_, fail) => { reject = fail; }); }};
  const pending = voice.reconcileSession(session);
  await voice.reconcileSession(session); assert.equal(count, 1);
  reject(new Error('temporary network error')); await pending;
  assert.equal(voice.current, session); assert.equal(voice.synchronizing, false);
});
test('polling is limited to the current registered online call and cleaned on hangup', async t => {
  t.mock.timers.enable({apis: ['setInterval', 'setTimeout']});
  const voice = harness(false); voice.current = null; let count = 0;
  global.Auth = {request: async () => { count++; return makeView(); }};
  const session = voice.session(makeView(), false);
  t.mock.timers.tick(3000); await new Promise(resolve => setImmediate(resolve));
  assert.equal(count, 1); assert.equal(session.registered, true);
  voice.realtime.ready = false;
  t.mock.timers.tick(3000); await new Promise(resolve => setImmediate(resolve)); assert.equal(count, 1);
  voice.cleanup(session); voice.realtime.ready = true;
  t.mock.timers.tick(9000); await new Promise(resolve => setImmediate(resolve)); assert.equal(count, 1);
});
test('snapshot started without a session cannot resurrect a call after local lifecycle changes', async () => {
  const voice = harness(false); voice.current = null;
  let respond; global.Auth = {request: () => new Promise(resolve => { respond = resolve; })};
  const pending = voice.resynchronize();
  const transient = voice.session(makeView('transient'), false); voice.cleanup(transient);
  respond(makeView()); await pending;
  assert.equal(voice.current, null);
});
