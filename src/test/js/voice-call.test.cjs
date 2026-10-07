'use strict';
const {test, afterEach} = require('node:test');
const assert = require('node:assert/strict');
const {VoiceCalls, acquireAudio, stopStream, duration, ownsCall, microphoneError} = require('../../main/resources/static/js/voice-call.js');
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

test('microphone capture requests audio only after an explicit caller invokes it', async () => {
  let constraints; const stream = {getTracks: () => []};
  const result = await acquireAudio(async value => { constraints = value; return stream; }, () => true);
  assert.equal(result, stream); assert.equal(constraints.video, false); assert.equal(constraints.audio.echoCancellation, true);
});
test('late microphone permission after cancellation stops every acquired track', async () => {
  let finish; let stopped = 0; let active = true;
  const pending = acquireAudio(() => new Promise(resolve => { finish = resolve; }), () => active);
  active = false; finish({getTracks: () => [{stop() { stopped++; }}, {stop() { stopped++; }}]});
  assert.equal(await pending, null); assert.equal(stopped, 2);
});
test('permission rejection is propagated with a useful UI message', async () => {
  const error = Object.assign(new Error('denied'), {name: 'NotAllowedError'});
  await assert.rejects(acquireAudio(async () => { throw error; }, () => true), error);
  assert.match(microphoneError(error), /마이크 권한/);
});
test('duration is stable at minute and hour boundaries', () => {
  assert.equal(duration(-1), '00:00'); assert.equal(duration(59.9), '00:59');
  assert.equal(duration(60), '01:00'); assert.equal(duration(3600), '60:00');
});
test('accepted calls are pinned to the correct account and tab', () => {
  const view = makeView();
  assert.equal(ownsCall(view, 'bob', 'tab-b'), true);
  view.calleeClientId = 'tab-b';
  assert.equal(ownsCall(view, 'bob', 'other-tab'), false);
  assert.equal(ownsCall(view, 'alice', 'tab-b'), false);
  assert.equal(ownsCall(view, 'outsider', 'tab-b'), false);
});
test('ICE is buffered until remote description exists then drained in order', async () => {
  const voice = harness(), session = voice.current, applied = [];
  session.pc = {remoteDescription: null, async addIceCandidate(candidate) { applied.push(candidate); }};
  await voice.signal(session, {action: 'ICE', candidate: {candidate: 'first'}});
  await voice.signal(session, {action: 'ICE', candidate: {candidate: 'second'}});
  assert.equal(applied.length, 0);
  session.pc.remoteDescription = {type: 'answer'}; await voice.flushRemoteIce(session);
  assert.deepEqual(applied.map(item => item.candidate), ['first', 'second']);
  assert.equal(session.incomingIce.length, 0);
});
test('negotiation arriving after cleanup cannot reach a closed peer', async () => {
  const voice = harness(), session = voice.current;
  session.pc = {remoteDescription: {}, addIceCandidate() { assert.fail('closed call'); }};
  voice.current = null;
  await voice.signal(session, {action: 'ICE', candidate: {candidate: 'late'}});
  assert.equal(session.incomingIce.length, 0);
});
test('duplicate ACCEPTED does not trigger duplicate offers', async () => {
  const voice = harness(), session = voice.current; let count = 0;
  voice.signal = async () => { count++; };
  const view = {...makeView(), calleeClientId: 'tab-b', status: 'ACCEPTED'};
  const event = {type: 'VOICE_CALL', action: 'ACCEPTED', roomId: 'dm', call: view};
  voice.receive(event); voice.receive(event); await session.inbox;
  assert.equal(count, 1); voice.cleanup(session);
});
test('cleanup closes peer and microphone tracks exactly once', () => {
  const voice = harness(), session = voice.current; let closed = 0, stopped = 0;
  const track = {stop() { stopped++; }};
  session.stream = {getTracks: () => [track]}; session.pc = {close() { closed++; }};
  voice.cleanup(session); voice.cleanup(session);
  assert.equal(closed, 1); assert.equal(stopped, 1); assert.equal(voice.audio.srcObject, null);
});
test('an old call termination cannot close the current call', () => {
  const voice = harness(), session = voice.current;
  voice.receive({type: 'VOICE_CALL', roomId: 'dm', action: 'ENDED', call: makeView('old')});
  assert.equal(voice.current, session);
});
test('another tab accepting only cleans local state and never hangs up its call', () => {
  const voice = harness(false), session = voice.current; let stopped = false;
  session.stream = {getTracks: () => [{stop() { stopped = true; }}]};
  voice.receive({type: 'VOICE_CALL', roomId: 'dm', action: 'ACCEPTED', call: {...makeView(), calleeClientId: 'other-tab'}});
  assert.equal(voice.current, null); assert.equal(stopped, true); assert.equal(voice.teardowns.length, 0);
});
test('RING presents a call without acquiring a microphone', () => {
  const voice = harness(false); voice.current = null;
  voice.prepare = () => assert.fail('unsolicited microphone request');
  voice.receive({type: 'VOICE_CALL', roomId: 'dm', action: 'RING', call: makeView()});
  assert.equal(voice.current.phase, 'ringing'); voice.cleanup(voice.current);
});
test('cancelling an in-flight start sends cleanup again if the server creates it later', async () => {
  const voice = harness(); voice.current = null; voice.supported = () => {};
  voice.room = {id: 'dm', type: 'DIRECT', members: ['alice', 'bob']}; voice.prepare = async () => {};
  let resolveStart, sent;
  global.Auth = {request: async (_, options) => {
    sent = JSON.parse(options.body); return new Promise(resolve => { resolveStart = resolve; });
  }};
  const pending = voice.start(); await new Promise(resolve => setImmediate(resolve));
  voice.hangup(); resolveStart({...makeView(sent.callId)}); await pending;
  assert.equal(voice.current, null); assert.equal(voice.teardowns.length, 2);
});
test('cancelling while accept prepares microphone prevents a later ACCEPT', async () => {
  const voice = harness(false); voice.supported = () => {};
  let finish; voice.prepare = () => new Promise(resolve => { finish = resolve; });
  voice.command = () => assert.fail('cancelled acceptance');
  const pending = voice.accept(); voice.hangup(); finish(); await pending;
  assert.equal(voice.current, null);
});
test('mute controls audio track enabled state', () => {
  const voice = harness(), track = {enabled: true};
  voice.current.stream = {getAudioTracks: () => [track]};
  voice.toggleMute(); assert.equal(track.enabled, false); voice.toggleMute(); assert.equal(track.enabled, true);
});
test('stopStream tolerates a missing stream', () => assert.doesNotThrow(() => stopStream(null)));
test('end-of-candidates marker is delivered intact instead of failing the call', async () => {
  const voice = harness(), session = voice.current, applied = [];
  session.pc = {remoteDescription: {type: 'answer'}, async addIceCandidate(candidate) { applied.push(candidate); }};
  const marker = {candidate: '', sdpMid: null, sdpMLineIndex: null};
  await voice.signal(session, {action: 'ICE', candidate: marker});
  assert.deepEqual(applied, [marker]); assert.equal(voice.current, session);
});
