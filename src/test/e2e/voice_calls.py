"""Real localhost HTTP/STOMP/WebRTC. Chromium fake microphone, not physical audio QA."""
import json
import os
import pathlib
import uuid
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('MESSENGER_E2E_URL', 'http://127.0.0.1:8080')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Disposable localhost app only'
OUT = pathlib.Path('build/e2e-artifacts')
OUT.mkdir(parents=True, exist_ok=True)
PASSWORD = 'Voice_Test_42!'
RUN = uuid.uuid4().hex[:8]
INSTRUMENT = """
window.__voicePeers = []; window.__voiceStreams = []; window.__voiceMediaRequests = 0;
const Peer = window.RTCPeerConnection;
window.RTCPeerConnection = new Proxy(Peer, {construct(target, args) {
  const peer = Reflect.construct(target, args); window.__voicePeers.push(peer); return peer;
}});
const capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
navigator.mediaDevices.getUserMedia = async options => {
  window.__voiceMediaRequests++;
  const stream = await capture(options); window.__voiceStreams.push(stream); return stream;
};
"""
checks = []
def passed(name):
    checks.append(name)
    print('PASS voice:', name, flush=True)

with sync_playwright() as p:
    # Fake hardware and browser permission UI; actual microphone/permission UX is separate QA.
    browser = p.chromium.launch(args=['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'])
    pages = []
    errors = []
    contexts = []
    diagnostics = []
    def observe(page):
        pages.append(page)
        page.on('pageerror', lambda error: errors.append(str(error)))
        def response_done(response):
            path = urlparse(response.url).path
            if path.startswith('/api/voice/'):
                request = response.request
                body = request.post_data_json if request.method == 'POST' else None
                diagnostics.append({'method': request.method, 'path': path,
                                    'action': body.get('action') if isinstance(body, dict) else None,
                                    'status': response.status})
        page.on('response', response_done)
        page.on('requestfailed', lambda request: diagnostics.append({
            'failedPath': urlparse(request.url).path, 'failure': request.failure}))
    def account(name):
        context = browser.new_context(permissions=['microphone'], viewport={'width': 1440, 'height': 960})
        contexts.append(context)
        identifier = f'voice_{name}_{RUN}'
        response = context.request.post(BASE + '/api/auth/register', data={
            'id': identifier, 'userName': name, 'password': PASSWORD, 'phoneNumber': '01012345678'})
        assert response.status == 201, f'register: {response.status}'
        response = context.request.post(BASE + '/api/auth/login', data={'id': identifier, 'password': PASSWORD})
        assert response.status == 200, f'login: {response.status}'
        context.add_init_script(INSTRUMENT)
        return context, identifier, response.json()['token']
    def api(context, token, method, path, data=None, expected=200):
        response = context.request.fetch(BASE + path, method=method, headers={'Authorization': 'Bearer ' + token}, data=data)
        assert response.status == expected, f'{method} {path}: {response.status}'
        return response.json() if response.status not in (204,) and expected < 400 else None
    def login(context, identifier):
        page = context.new_page(); observe(page)
        page.goto(BASE + '/login'); page.locator('#login-id').fill(identifier)
        page.locator('#login-password').fill(PASSWORD); page.get_by_role('button', name='로그인', exact=True).click()
        expect(page.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
        return page
    def connected(page):
        expect(page.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
    try:
        ac, aid, at = account('alice'); bc, bid, bt = account('bob'); ec, eid, et = account('outsider')
        a = login(ac, aid); b = login(bc, bid)
        sibling = bc.new_page(); observe(sibling)
        sibling.goto(BASE + '/home'); connected(sibling)
        room = api(ac, at, 'POST', '/api/rooms/dm', {'peerId': bid})
        a.goto(BASE + '/chat/' + room['id']); connected(a)
        starts = []
        a.on('request', lambda request: starts.append(request.post_data_json)
             if request.method == 'POST' and urlparse(request.url).path == '/api/voice/calls' else None)
        a.bring_to_front()
        a.locator('#voice-start').click()
        expect(b.locator('#voice-accept')).to_be_visible(timeout=15000)
        expect(sibling.locator('#voice-accept')).to_be_visible(timeout=15000)
        assert b.evaluate('window.__voiceMediaRequests') == 0
        assert sibling.evaluate('window.__voiceMediaRequests') == 0
        passed('Incoming calls are delivered to own tabs without capturing their microphones')
        call_id = starts[-1]['callId']
        api(ec, et, 'POST', '/api/voice/calls/' + call_id,
            {'clientId': str(uuid.uuid4()), 'action': 'END'}, expected=403)
        response = ec.request.get(BASE + '/api/voice/config')
        assert response.status == 401
        passed('Outsiders cannot control calls; ICE configuration requires authentication')
        b.bring_to_front()
        b.locator('#voice-accept').click()
        expect(a.locator('#voice-state')).to_have_text('통화 중', timeout=30000)
        expect(b.locator('#voice-state')).to_have_text('통화 중', timeout=30000)
        expect(sibling.locator('#voice-panel')).not_to_be_visible()
        assert sibling.evaluate('window.__voiceMediaRequests') == 0
        passed('Accept selects one tab and establishes real RTCPeerConnection on both ends')
        for page in (a, b):
            page.wait_for_function("""async () => {
              const peer = window.__voicePeers.at(-1); if (!peer) return false;
              const stats = await peer.getStats();
              return [...stats.values()].some(s => s.type === 'inbound-rtp' && s.kind === 'audio' && s.packetsReceived > 0);
            }""", timeout=15000)
        passed('Both peers receive real audio RTP packets from Chromium fake microphones')
        a.locator('#voice-mute').click()
        expect(a.locator('#voice-mute')).to_have_attribute('aria-pressed', 'true')
        assert a.evaluate('window.__voiceStreams.at(-1).getAudioTracks().every(t => !t.enabled)')
        a.locator('#voice-mute').click()
        assert a.evaluate('window.__voiceStreams.at(-1).getAudioTracks().every(t => t.enabled)')
        a.locator('#friends-nav').click()
        expect(a.locator('#voice-state')).to_have_text('통화 중')
        a.screenshot(path=str(OUT / 'voice-connected.png'), full_page=True)
        passed('Mute toggles real tracks; navigating within the workspace keeps the call')
        b.locator('#voice-end').click()
        for page in (a, b):
            expect(page.locator('#voice-panel')).not_to_be_visible(timeout=15000)
            assert page.evaluate('window.__voicePeers.every(p => p.connectionState === "closed")')
            assert page.evaluate('window.__voiceStreams.every(s => s.getTracks().every(t => t.readyState === "ended"))')
        passed('Hangup closes both peer connections and releases microphone tracks')
        a.goto(BASE + '/chat/' + room['id']); connected(a); a.wait_for_timeout(3100)
        previous_requests = b.evaluate('window.__voiceMediaRequests')
        a.bring_to_front()
        a.locator('#voice-start').click(); expect(b.locator('#voice-decline')).to_be_visible(timeout=15000)
        b.locator('#voice-decline').click()
        expect(a.locator('#voice-panel')).not_to_be_visible(timeout=15000)
        expect(sibling.locator('#voice-panel')).not_to_be_visible(timeout=15000)
        assert b.evaluate('window.__voiceMediaRequests') == previous_requests
        passed('Decline clears all ringing tabs without requesting a microphone')
        group = api(ac, at, 'POST', '/api/rooms/group', {'members': [bid, eid]})
        a.goto(BASE + '/chat/' + group['id']); connected(a)
        expect(a.locator('#voice-start')).not_to_be_visible()
        api(ac, at, 'POST', '/api/voice/calls', {'callId': str(uuid.uuid4()), 'clientId': str(uuid.uuid4()), 'roomId': group['id']}, expected=400)
        api(ec, et, 'POST', '/api/voice/calls', {'callId': str(uuid.uuid4()), 'clientId': str(uuid.uuid4()), 'roomId': room['id']}, expected=403)
        assert not errors, errors
        passed('Group voice and nonmember DM calls are rejected; no unhandled page errors')
        (OUT / 'voice-checks.json').write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding='utf-8')
    except BaseException:
        for index, page in enumerate(pages):
            if not page.is_closed():
                try:
                    diagnostics.append({'page': index, 'state': page.evaluate('''() => ({
                      path: location.pathname,
                      panelVisible: !!document.querySelector('#voice-panel:not([hidden])'),
                      state: document.querySelector('#voice-state')?.textContent,
                      toasts: document.querySelector('#toast-stack')?.textContent,
                      focused: document.hasFocus(), visibility: document.visibilityState,
                      captures: window.__voiceMediaRequests,
                      peers: (window.__voicePeers || []).map(p => ({connection: p.connectionState,
                        signaling: p.signalingState, ice: p.iceConnectionState}))
                    })''')})
                    page.screenshot(path=str(OUT / f'voice-failure-{index}.png'), full_page=True, timeout=5000)
                except Exception: pass
        # Only method/action/status and UI state; never serialize auth headers, SDP or ICE addresses.
        (OUT / 'voice-diagnostics.json').write_text(json.dumps({'checks': checks, 'errors': errors,
            'events': diagnostics}, ensure_ascii=False, indent=2), encoding='utf-8')
        print('Voice failure diagnostics:', json.dumps(diagnostics, ensure_ascii=False), flush=True)
        raise
    finally:
        for context in contexts: context.close()
        browser.close()
