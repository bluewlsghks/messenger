"""Run against a disposable localhost app. Real HTTP/MongoDB/STOMP; OS Notification is a recording stub."""
import json
import os
import pathlib
import uuid
from urllib.parse import urlparse, quote
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('MESSENGER_E2E_URL', 'http://127.0.0.1:8080')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Use a disposable local test server only'
OUT = pathlib.Path('build/e2e-artifacts')
OUT.mkdir(parents=True, exist_ok=True)
PASSWORD = 'Messenger_Test_42!'
RUN = uuid.uuid4().hex[:8]
STUB = """
window.__notifications = [];
window.__permissionRequests = 0;
class RecordingNotification {
  static permission = 'granted';
  static requestPermission() { window.__permissionRequests++; return Promise.resolve('granted'); }
  constructor(title, options) { this.title = title; this.options = options; window.__notifications.push(this); }
  close() { this.closed = true; }
}
Object.defineProperty(window, 'Notification', {value: RecordingNotification, configurable: true});
"""
checks = []
def passed(name):
    checks.append(name)
    print('PASS:', name, flush=True)

with sync_playwright() as p:
    browser = p.chromium.launch()
    contexts = []
    errors = []
    def account(short, display):
        context = browser.new_context(viewport={'width': 1440, 'height': 960}, locale='ko-KR')
        contexts.append(context)
        identifier = f'e2e_{short}_{RUN}'
        res = context.request.post(BASE + '/api/auth/register', data={'id': identifier, 'userName': display, 'password': PASSWORD, 'phoneNumber': '01012345678'})
        assert res.status == 201, res.text()
        token = context.request.post(BASE + '/api/auth/login', data={'id': identifier, 'password': PASSWORD}).json()['token']
        return context, identifier, token
    ac, aid, at = account('alice', '지안')
    bc, bid, bt = account('bob', '민준')
    ec, eid, et = account('eve', '외부 사용자')
    def api(context, token, method, path, data=None, expected=200):
        res = context.request.fetch(BASE + path, method=method, headers={'Authorization': 'Bearer ' + token}, data=data)
        assert res.status == expected, f'{method} {path}: {res.status}: {res.text()[:500]}'
        return res.json() if res.status != 204 else None
    def login(context, identifier):
        context.add_init_script(STUB)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE + '/login')
        page.locator('#login-id').fill(identifier)
        page.locator('#login-password').fill(PASSWORD)
        page.locator('#auth-submit').click()
        page.wait_for_url('**/home')
        expect(page.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
        return page
    a = login(ac, aid)
    b = login(bc, bid)
    eve_frames = []
    ec.add_init_script(STUB)
    e = ec.new_page()
    e.on('pageerror', lambda error: errors.append(str(error)))
    e.on('websocket', lambda ws: ws.on('framereceived', lambda frame: eve_frames.append(str(frame))))
    e.goto(BASE + '/login')
    e.locator('#login-id').fill(eid); e.locator('#login-password').fill(PASSWORD); e.locator('#auth-submit').click()
    expect(e.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
    passed('Three real browser accounts log in and establish authenticated STOMP connections')

    a.locator('#add-friend').click()
    a.locator('#app-dialog input[name=friendId]').fill(bid)
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(a.locator('.friend-row')).to_have_count(1)
    expect(a.locator('.friend-row')).to_contain_text('민준')
    b.reload(); expect(b.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
    expect(b.locator('.friend-row')).to_contain_text('지안')
    assert not a.locator('#workspace-error').is_visible()
    assert not b.locator('#workspace-error').is_visible()
    passed('Friend list, mutual addition and display names work without INTERNAL_ERROR')

    a.locator('.friend-row').get_by_role('button', name='민준님에게 메시지').click()
    a.wait_for_url('**/chat/*')
    a.locator('#message-input').fill('안녕하세요! 실시간 대화 테스트입니다.')
    a.locator('#send-message').click()
    expect(a.locator('.message-content').last).to_have_text('안녕하세요! 실시간 대화 테스트입니다.')
    expect(b.locator('#toast-stack .toast')).to_be_visible(timeout=15000)
    expect(b.locator('#total-unread')).to_be_visible(timeout=10000)
    assert b.evaluate('window.__notifications.length') == 0
    b.locator('#toast-stack .toast-body').first.click()
    b.bring_to_front()
    expect(b.locator('#chat-title')).to_have_text('지안')
    expect(b.locator('.message-content').last).to_have_text('안녕하세요! 실시간 대화 테스트입니다.')
    expect(b.locator('#total-unread')).not_to_be_visible(timeout=15000)
    b.locator('#message-input').fill('네, 메시지를 받았어요.')
    b.locator('#send-message').click()
    expect(a.locator('.message-content').last).to_have_text('네, 메시지를 받았어요.', timeout=15000)
    passed('DM delivery, cross-view toast, unread badge, notification navigation and read clearing')

    b.locator('#message-input').fill('이 대화에 저장되는 초안')
    b.locator('#friends-nav').click()
    b.locator('.conversation-link').first.click()
    expect(b.locator('#message-input')).to_have_value('이 대화에 저장되는 초안')
    b.locator('#message-input').fill('')
    passed('Conversation-specific draft survives navigation without sending')

    a.locator('#create-server').click()
    a.locator('#app-dialog input[name=name]').fill('프로젝트 라운지')
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(a.locator('#chat-title')).to_have_text('일반')
    a.locator('#add-conversation').click()
    a.locator('#app-dialog input[name=name]').fill('제품-디자인')
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(a.locator('#chat-title')).to_have_text('제품-디자인')
    server_id = a.evaluate("new URLSearchParams(location.search).get('server')")
    channel_id = a.evaluate("new URLSearchParams(location.search).get('channel')")
    a.locator('#invite-members').click()
    expect(a.locator('#app-dialog input[name=code]')).to_be_visible()
    invite = a.locator('#app-dialog input[name=code]').input_value()
    a.locator('#dialog-close').click()
    b.locator('#join-server').click()
    b.locator('#app-dialog input[name=code]').fill(invite)
    b.locator('#app-dialog button[type=submit]').click()
    expect(b.locator('#app-dialog')).not_to_be_visible()
    expect(b.locator('#chat-title')).to_have_text('일반')
    assert not b.locator('#add-conversation').is_visible()
    first = api(ac, at, 'POST', '/api/messages', {'roomId': channel_id, 'content': '디자인 회의는 내일 오후에 진행해요.'})
    expect(b.locator('#toast-stack .toast').last).to_contain_text('프로젝트 라운지 · #제품-디자인')
    api(ec, et, 'GET', '/api/messages/' + channel_id, expected=403)
    assert channel_id not in api(ec, et, 'GET', '/api/notifications/unread')
    assert not any('디자인 회의는 내일' in frame for frame in eve_frames)
    passed('Server/channel creation, invitation, member restrictions and cross-channel private notification feed')

    b.locator('#open-settings').click()
    b.locator('#app-dialog').get_by_role('button', name='데스크톱 알림 허용', exact=True).click()
    expect(b.locator('#app-dialog').get_by_role('button', name='데스크톱 알림 끄기', exact=True)).to_be_visible()
    assert b.evaluate('window.__permissionRequests') == 1
    b.locator('#dialog-close').click()
    # Only the browser notification API and background flags are simulated; the message arrives over real STOMP.
    b.evaluate("Object.defineProperty(document,'hidden',{get:()=>true,configurable:true});Object.defineProperty(document,'hasFocus',{value:()=>false,configurable:true});")
    native_message = api(ac, at, 'POST', '/api/messages', {'roomId': channel_id, 'content': '민감한 내용은 기본 알림 미리보기에 노출하지 않습니다.'})
    b.wait_for_function('window.__notifications.length === 1')
    notice = b.evaluate('({title:window.__notifications[0].title,options:window.__notifications[0].options})')
    assert '민감한 내용' not in notice['options']['body']
    assert '프로젝트 라운지' in notice['options']['body']
    b.evaluate('delete document.hidden;delete document.hasFocus;window.__notifications[0].onclick();')
    b.bring_to_front()
    expect(b.locator('#chat-title')).to_have_text('제품-디자인')
    expect(b.locator(f'[data-message-id="{native_message["id"]}"]')).to_be_visible()
    passed('Explicit desktop opt-in, private default preview and notification click routing (OS API stub)')

    api(ac, at, 'POST', '/api/messages', {'roomId': channel_id, 'content': '현재 보고 있는 대화에는 팝업을 중복 표시하지 않아요.'})
    expect(b.locator('.message-content').last).to_have_text('현재 보고 있는 대화에는 팝업을 중복 표시하지 않아요.')
    assert b.evaluate('window.__notifications.length') == 1
    b.locator('#mute-room').click()
    b.locator('.conversation-link.channel').filter(has_text='일반').click()
    api(ac, at, 'POST', '/api/messages', {'roomId': channel_id, 'content': '알림을 끈 채널의 메시지'})
    expect(b.locator('#total-unread')).to_be_visible()
    assert b.evaluate('window.__notifications.length') == 1
    b.locator('.conversation-link.channel').filter(has_text='제품-디자인').click()
    b.locator('#mute-room').click()
    passed('Focused-chat suppression and per-conversation mute retain unread badges')

    row = a.locator(f'[data-message-id="{first["id"]}"]')
    row.hover()
    row.get_by_role('button', name='메시지 수정', exact=True).click()
    a.locator('#app-dialog textarea[name=content]').fill('디자인 회의를 오후 세 시로 변경했습니다.')
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(b.locator(f'[data-message-id="{first["id"]}"] .message-content')).to_have_text('디자인 회의를 오후 세 시로 변경했습니다.')
    api(ac, at, 'PATCH', '/api/messages/' + channel_id + '/' + first['id'], {'content': 'stale', 'version': 0}, expected=409)
    api(bc, bt, 'PATCH', '/api/messages/' + channel_id + '/' + first['id'], {'content': 'forged', 'version': 1}, expected=403)
    a.locator('#search-messages').click()
    a.locator('#search-query').fill('오후 세 시')
    a.locator('#search-form button[type=submit]').click()
    expect(a.locator('.search-result')).to_contain_text('디자인 회의를 오후 세 시로 변경했습니다.')
    a.locator('#close-search').click()
    row.hover(); row.get_by_role('button', name='메시지 삭제', exact=True).click()
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(b.locator(f'[data-message-id="{first["id"]}"] .message-content')).to_have_text('삭제된 메시지입니다.')
    assert b.evaluate('window.__notifications.length') == 1
    assert api(ac, at, 'GET', '/api/messages/' + channel_id + '/search?q=' + quote('오후 세 시')) == []
    passed('Real-time edit/delete, ownership and stale-version rejection, scoped search, no edit notifications')

    a.locator('#toggle-members').click()
    expect(a.locator('#member-list .member-row')).to_have_count(2)
    a.screenshot(path=str(OUT / 'workspace-chat.png'), full_page=True)
    a.locator('#close-members').click()
    a.locator('#home-button').click()
    for short, display in [('friend1', '서연'), ('friend2', '윤서')]:
        _, identifier, _ = account(short, display)
        api(ac, at, 'POST', '/api/friends', {'friendId': identifier}, expected=204)
    a.reload(); expect(a.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
    expect(a.locator('.friend-row')).to_have_count(3)
    a.screenshot(path=str(OUT / 'workspace-desktop.png'), full_page=True)
    a.locator('#open-settings').click()
    a.locator('#app-dialog input[name=userName]').fill('지안 새이름')
    a.locator('#app-dialog select[name=theme]').select_option('light')
    a.locator('#app-dialog button[type=submit]').click()
    expect(a.locator('#app-dialog')).not_to_be_visible()
    expect(a.locator('#my-name')).to_have_text('지안 새이름')
    assert a.evaluate('document.documentElement.dataset.theme') == 'light'
    assert api(ac, at, 'GET', '/api/users/me')['userName'] == '지안 새이름'
    a.screenshot(path=str(OUT / 'workspace-light.png'), full_page=True)
    passed('Real server-backed member panel, persistent profile name and light/dark theme')

    b.locator('#home-button').click()
    b.set_viewport_size({'width': 390, 'height': 844})
    assert b.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    b.locator('#mobile-menu').click()
    expect(b.locator('#sidebar')).to_have_class('sidebar open')
    b.locator('#sidebar-backdrop').click(position={'x': 380, 'y': 500})
    b.screenshot(path=str(OUT / 'workspace-mobile.png'), full_page=True)
    assert not errors, '\n'.join(errors)
    passed('Mobile navigation, no horizontal overflow and no unhandled browser JavaScript errors')

    report = {'checks': checks, 'count': len(checks), 'browserErrors': errors,
              'notes': ['HTTP, MongoDB, login, STOMP and UI actions are real.',
                        'Native Notification constructor/permission and one background state are stubbed; OS popup appearance is not validated.',
                        'Screenshots contain disposable test-account data only.']}
    (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    for context in contexts:
        context.close()
    browser.close()
    print(f'Browser end-to-end scenarios passed: {len(checks)}', flush=True)
