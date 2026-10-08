"""Real HTTP/STOMP collaboration and reconnect checks on an isolated local test server."""
import json, os, pathlib, uuid
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect
BASE=os.environ.get('MESSENGER_E2E_URL','http://127.0.0.1:8080').rstrip('/')
assert urlparse(BASE).hostname in ('localhost','127.0.0.1'), 'Use only a disposable local test server'
OUT=pathlib.Path('build/e2e-artifacts');OUT.mkdir(parents=True,exist_ok=True)
checks=[];errors=[];contexts=[]
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get('MESSENGER_CHROMIUM'))
    def account(label):
        context=browser.new_context(viewport={'width':1440,'height':1000});contexts.append(context)
        user='collab_'+label+'_'+uuid.uuid4().hex[:8];password='Collab_Test42!'
        registered=context.request.post(BASE+'/api/auth/register',data={'id':user,'userName':label,'password':password});assert registered.status==201,registered.text()
        token=context.request.post(BASE+'/api/auth/login',data={'id':user,'password':password}).json()['token']
        page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto(BASE+'/login');page.locator('#login-id').fill(user);page.locator('#login-password').fill(password);page.locator('#auth-submit').click()
        expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
        return context,user,token,page
    def api(context,token,method,path,data=None,status=200):
        response=context.request.fetch(BASE+path,method=method,headers={'Authorization':'Bearer '+token},data=data)
        assert response.status==status,f'{method} {path}: {response.status}: {response.text()[:200]}'
        return response.json() if status==200 else None
    def passed(text):checks.append(text);print('PASS collaboration:',text,flush=True)
    try:
        ac,aid,at,a=account('alice');bc,bid,bt,b=account('bob')
        room=api(ac,at,'POST','/api/rooms/dm',{'peerId':bid})['id']
        sync_requests=[]
        for page in (a,b):
            page.on('request',lambda r:sync_requests.append(r.url) if '/sync?' in r.url or '/changes?' in r.url else None)
            page.goto(BASE+'/chat/'+room);expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
        message=api(ac,at,'POST','/api/messages',{'roomId':room,'content':'collaboration original','clientRequestId':str(uuid.uuid4())})
        selector=f'.message[data-message-id="{message["id"]}"]'
        ar=a.locator(selector);br=b.locator(selector)
        expect(br).to_have_count(1,timeout=15000)
        br.get_by_role('button',name='반응',exact=True).click()
        b.get_by_role('button',name='👍 반응 추가',exact=True).click()
        expect(ar.get_by_role('button',name='👍 반응 1명, 추가',exact=True)).to_be_visible(timeout=15000)
        api(bc,bt,'PUT',f'/api/messages/{room}/{message["id"]}/reactions/like')
        current=api(ac,at,'POST',f'/api/messages/{room}/snapshots',[message['id']])[0]
        assert current['reactions']['like']==[bid]
        passed('Reaction UI updates the other browser via real STOMP; repeat PUT stays at one reactor')
        ar.get_by_role('button',name='반응한 사람',exact=True).click();expect(a.locator('#app-dialog')).to_contain_text(bid)
        a.locator('#dialog-close').click()
        expect(br.get_by_role('button',name='☆ 북마크 저장',exact=True)).to_be_enabled(timeout=15000)
        br.get_by_role('button',name='☆ 북마크 저장',exact=True).click()
        expect(br.get_by_role('button',name='★ 북마크 해제',exact=True)).to_be_visible()
        assert api(ac,at,'GET',f'/api/messages/{room}/bookmarks')['items']==[]
        b.locator('#show-bookmarks').click();expect(b.locator('.saved-message')).to_contain_text('collaboration original');b.locator('#dialog-close').click()
        passed('Bookmarks are persisted privately and displayed only in the owner collection')
        ar.get_by_role('button',name='📌 고정',exact=True).click();expect(br.locator('.message-pin')).to_be_visible(timeout=15000)
        b.locator('#show-pins').click();expect(b.locator('.saved-message')).to_contain_text('collaboration original');b.locator('#dialog-close').click()
        passed('DM pin is shared and the pinned-message list loads current content')
        b.screenshot(path=str(OUT/'collaboration-active.png'),full_page=True)
        # Keep the in-memory message window, stop the real transport, and deny new browser networking.
        b.evaluate('window.messengerRealtime.stop()');bc.set_offline(True)
        current=api(ac,at,'POST',f'/api/messages/{room}/snapshots',[message['id']])[0]
        api(ac,at,'PATCH',f'/api/messages/{room}/{message["id"]}',{'content':'edited while offline','version':current['version']})
        bc.set_offline(False);b.evaluate('window.messengerRealtime.start()')
        expect(br.locator('.message-content')).to_have_text('edited while offline',timeout=20000)
        passed('Reconnect recovers an old-message edit without scanning legacy full-sync pages')
        b.locator('#show-bookmarks').click();expect(b.locator('.saved-message')).to_contain_text('edited while offline')
        current=api(ac,at,'POST',f'/api/messages/{room}/snapshots',[message['id']])[0]
        api(ac,at,'DELETE',f'/api/messages/{room}/{message["id"]}?version={current["version"]}')
        expect(b.locator('.saved-message')).to_have_count(0,timeout=15000);b.locator('#dialog-close').click()
        expect(br.locator('.message-content')).to_have_text('삭제된 메시지입니다.')
        assert not api(bc,bt,'GET',f'/api/messages/{room}/pins')['items']
        assert not api(bc,bt,'GET',f'/api/messages/{room}/bookmarks')['items']
        assert any('/changes?' in url for url in sync_requests)
        assert not any('/sync?' in url for url in sync_requests),sync_requests
        assert not errors,errors
        passed('Deleting the source clears pins, hides saved content, and updates an already-open bookmark list')
        b.screenshot(path=str(OUT/'collaboration.png'),full_page=True)
        (OUT/'collaboration-checks.json').write_text(json.dumps({'checks':checks,'sync_requests':sync_requests,'errors':errors},ensure_ascii=False,indent=2),encoding='utf-8')
    except BaseException:
        (OUT/'collaboration-failure.json').write_text(json.dumps({'checks':checks,'errors':errors},ensure_ascii=False,indent=2),encoding='utf-8')
        for i,context in enumerate(contexts):
            for j,page in enumerate(context.pages):
                try:page.screenshot(path=str(OUT/f'collaboration-failure-{i}-{j}.png'),full_page=True,timeout=5000)
                except Exception:pass
        raise
    finally:
        for context in contexts:context.close()
        browser.close()
