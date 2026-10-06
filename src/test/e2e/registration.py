"""Real browser signup; only creates accounts on a disposable localhost app."""
import os, json, uuid
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect
BASE = os.environ.get('MESSENGER_E2E_URL', 'http://127.0.0.1:8080')
assert urlparse(BASE).hostname in ('localhost', '127.0.0.1'), 'Disposable localhost app only'

def run():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get("MESSENGER_CHROMIUM"), )
        page = browser.new_page()
        try:
            identifier = 'signup_' + uuid.uuid4().hex[:12]
            page.goto(BASE + '/register')
            expect(page.locator('input[name="phoneNumber"]')).to_have_count(0)
            page.locator('#login-id').fill(identifier)
            page.locator('input[name="userName"]').fill('가입 테스트')
            page.locator('#login-password').fill('Password!234')
            page.locator('input[name="passwordConfirm"]').fill('Password!234')
            with page.expect_request(lambda r: r.url.endswith('/api/auth/register')) as request:
                with page.expect_response(lambda r: r.url.endswith('/api/auth/register')) as response:
                    page.locator('#auth-submit').click()
            assert response.value.status == 201
            assert 'phoneNumber' not in request.value.post_data_json
            page.wait_for_url('**/login?registered=1')
            page.locator('#login-id').fill(identifier)
            page.locator('#login-password').fill('Password!234')
            page.locator('#auth-submit').click()
            expect(page.locator('#connection-state')).to_have_text('연결됨', timeout=30000)
            out = Path('build/e2e-artifacts'); out.mkdir(parents=True, exist_ok=True)
            checks = ['phone input absent', 'phone excluded from request', 'signup 201', 'success redirect', 'real login and STOMP connection']
            (out / 'registration-checks.json').write_text(json.dumps(checks), encoding='utf-8')
            print('PASS registration:', ', '.join(checks), flush=True)
        finally:
            browser.close()

if __name__ == '__main__':
    run()
