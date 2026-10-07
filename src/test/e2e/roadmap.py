"""Local-only roadmap integration. Real APIs, browser UI and WebRTC; fake camera/microphone hardware."""
import json
import os
import pathlib
import uuid
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE=os.environ.get('MESSENGER_E2E_URL','http://127.0.0.1:8080')
assert urlparse(BASE).hostname in ('localhost','127.0.0.1'), 'Use an isolated local test server'
OUT=pathlib.Path('build/e2e-artifacts');OUT.mkdir(parents=True,exist_ok=True)
checks=[];errors=[];contexts=[];pages=[]
def passed(name):
    checks.append(name);print('PASS roadmap:',name,flush=True)
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get('MESSENGER_CHROMIUM'),args=['--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream'])
    def account(name):
        context=browser.new_context(permissions=['microphone','camera'],viewport={'width':1440,'height':1000});contexts.append(context)
        user='roadmap_'+name+'_'+uuid.uuid4().hex[:8];password='Roadmap_Test42!'
        result=context.request.post(BASE+'/api/auth/register',data={'id':user,'userName':name,'password':password});assert result.status==201,result.text()
        token=context.request.post(BASE+'/api/auth/login',data={'id':user,'password':password}).json()['token']
        page=context.new_page();pages.append(page);page.on('pageerror',lambda error:errors.append(str(error)))
        page.goto(BASE+'/login');page.locator('#login-id').fill(user);page.locator('#login-password').fill(password);page.locator('#auth-submit').click()
        expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
        return context,user,token,page
    def api(context,token,method,path,data=None,status=200):
        result=context.request.fetch(BASE+path,method=method,headers={'Authorization':'Bearer '+token},data=data)
        assert result.status==status,f'{method} {path}: {result.status}: {result.text()[:200]}'
        return result.json() if status!=204 and status<400 else None
    def go(page,room):
        page.goto(BASE+'/chat/'+room);expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
    try:
        ac,aid,at,a=account('alice');bc,bid,bt,b=account('bob');cc,cid,ct,c=account('carol')
        a.locator('#add-friend').click();a.locator('#app-dialog input[name=friendId]').fill(bid);a.locator('#app-dialog button[type=submit]').click()
        expect(a.locator('#app-dialog')).not_to_be_visible();expect(a.locator('.friend-row')).to_have_count(0)
        b.get_by_role('button',name='친구 요청',exact=True).click()
        b.locator(f'#app-dialog [data-peer-id="{aid}"]').get_by_role('button',name='수락',exact=True).click()
        expect(b.locator('.friend-row')).to_have_count(1);b.locator('#app-dialog button[type=submit]').click()
        a.reload();expect(a.locator('.friend-row')).to_have_count(1)
        passed('Friend UI requires recipient approval and refreshes both friend lists')
        room=api(ac,at,'POST','/api/rooms/dm',{'peerId':bid})['id'];go(a,room);go(b,room)
        key=str(uuid.uuid4());body={'roomId':room,'content':'idempotent message','clientRequestId':key}
        first=api(ac,at,'POST','/api/messages',body);again=api(ac,at,'POST','/api/messages',body)
        assert first['id']==again['id'];api(ac,at,'POST','/api/messages',{**body,'content':'changed payload'},status=409)
        expect(b.locator(f'[data-message-id="{first["id"]}"]')).to_have_count(1)
        passed('HTTP retry stores one message and a reused ID with another payload is rejected')
        a.locator('#attachment-input').set_input_files({'name':'sample.txt','mimeType':'text/plain','buffer':b'roadmap attachment'})
        expect(a.locator('.pending-file')).to_contain_text('sample.txt')
        a.locator('#message-input').fill('file for @'+bid);a.locator('#send-message').click()
        expect(b.locator('.message-content').last).to_have_text('file for @'+bid,timeout=15000)
        records=api(ac,at,'GET','/api/messages/'+room);message=next(m for m in records if m['content']=='file for @'+bid)
        assert message['mentions']==[bid];file_id=message['attachmentIds'][0]
        download=bc.request.get(BASE+'/api/files/'+file_id+'/content',headers={'Authorization':'Bearer '+bt})
        assert download.status==200 and download.body()==b'roadmap attachment'
        assert download.headers['content-type'].startswith('application/octet-stream') and 'attachment' in download.headers['content-disposition']
        api(cc,ct,'GET','/api/files/'+file_id,status=403)
        passed('Attachment UI uploads a real file; authenticated room-only download and mentions work')
        reply=api(bc,bt,'POST','/api/messages',{'roomId':room,'content':'thread reply','clientRequestId':str(uuid.uuid4()),'replyToId':message['id']})
        assert reply['threadId']==message['id']
        thread=api(ac,at,'GET',f'/api/messages/{room}/threads/{message["id"]}')
        assert {m['id'] for m in thread}=={message['id'],reply['id']}
        api(ac,at,'DELETE',f'/api/messages/{room}/{message["id"]}?version=0',status=200)
        api(bc,bt,'GET','/api/files/'+file_id,status=403)
        passed('Replies form a room-scoped thread and deleting the original revokes its attachment')
        a.locator('#message-input').fill('typing preview');a.locator('#message-input').press('x')
        expect(b.locator('#typing-indicator')).to_contain_text(aid,timeout=12000)
        a.locator('#message-input').fill('');passed('Typing state comes from an authenticated per-tab presence lease')
        group=api(ac,at,'POST','/api/rooms/group',{'members':[bid,cid]})['id']
        for page in (a,b,c):go(page,group)
        for page in (a,b,c):
            page.bring_to_front();page.locator('#conference-join').click();expect(page.locator('#conference-panel')).to_be_visible()
            page.wait_for_function('window.messengerConference?.current?.registered === true',timeout=15000)
        for page in (a,b,c):
            expect(page.locator('.conference-peer.connected')).to_have_count(2,timeout=30000)
            page.wait_for_function('''async()=>{
                const peers=[...window.messengerConference.current.peers.values()];
                if(peers.length!==2)return false;
                const ok=await Promise.all(peers.map(async peer=>[...(await peer.pc.getStats()).values()].some(s=>s.type==='inbound-rtp'&&s.kind==='audio'&&s.packetsReceived>0)));
                return ok.every(Boolean);
            }''',timeout=30000)
        passed('Three browser participants receive real audio RTP from both other peers')
        a.bring_to_front();a.locator('#conference-camera').click()
        expect(a.locator('#conference-camera')).to_have_attribute('aria-pressed','true')
        for page in (b,c):
            page.wait_for_function('''async user=>{
                const peer=window.messengerConference.current.peers.get(user);if(!peer)return false;
                return [...(await peer.pc.getStats()).values()].some(s=>s.type==='inbound-rtp'&&s.kind==='video'&&s.framesDecoded>0);
            }''',arg=aid,timeout=30000)
        passed('Camera enable replaces the negotiated video track and both peers decode video frames')
        # A canvas is an explicit display-capture fixture, not a test of the OS screen selection dialog.
        a.evaluate('''()=>{
            window.__screenCaptureRequests=0;
            navigator.mediaDevices.getDisplayMedia=async()=>{
                window.__screenCaptureRequests++;const canvas=document.createElement('canvas');canvas.width=640;canvas.height=360;
                const paint=()=>{const ctx=canvas.getContext('2d');ctx.fillStyle='white';ctx.fillRect(0,0,640,360);ctx.fillStyle='black';ctx.fillText(String(Date.now()),20,20);};
                paint();const timer=setInterval(paint,100);const stream=canvas.captureStream(10);stream.getTracks()[0].addEventListener('ended',()=>clearInterval(timer));window.__screenFixture=stream;return stream;
            };
        }''')
        a.locator('#conference-screen').click();expect(a.locator('#conference-screen')).to_have_attribute('aria-pressed','true')
        assert a.evaluate('window.__screenCaptureRequests')==1
        assert a.evaluate("window.messengerConference.current.videoKind === 'screen'")
        a.locator('#conference-screen').click();expect(a.locator('#conference-screen')).to_have_attribute('aria-pressed','false')
        assert a.evaluate("window.__screenFixture.getTracks().every(t=>t.readyState==='ended')")
        passed('Screen-share UI acquires and releases an explicit display-track fixture without an OS dialog')
        a.locator('#conference-mute').click();expect(a.locator('#conference-mute')).to_have_attribute('aria-pressed','true')
        a.screenshot(path=str(OUT/'roadmap-conference.png'),full_page=True)
        a.locator('#conference-leave').click();expect(a.locator('#conference-panel')).not_to_be_visible()
        for page in (b,c):expect(page.locator('.conference-peer.connected')).to_have_count(1,timeout=15000)
        for page in (b,c):page.locator('#conference-leave').click()
        passed('Group mute and leave clean up peers and microphone tracks on all participants')
        # New sessions are revocable; refresh cookie is never available through document.cookie.
        assert 'messenger_refresh' not in a.evaluate('document.cookie')
        session_list=api(ac,at,'GET','/api/auth/sessions');assert session_list
        assert all('currentHash' not in item and 'consumedHashes' not in item for item in session_list)
        api(ac,at,'GET','/api/operations/audit',status=403)
        assert not errors,errors
        passed('Refresh secrets stay HttpOnly and chat users cannot access operator diagnostics')
        (OUT/'roadmap-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    except BaseException:
        for index,page in enumerate(pages):
            if not page.is_closed():
                try:page.screenshot(path=str(OUT/f'roadmap-failure-{index}.png'),full_page=True,timeout=5000)
                except Exception:pass
        (OUT/'roadmap-failure.json').write_text(json.dumps({'checks':checks,'errors':errors},ensure_ascii=False,indent=2))
        raise
    finally:
        for context in contexts:context.close()
        browser.close()
