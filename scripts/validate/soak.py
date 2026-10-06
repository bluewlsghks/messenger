#!/usr/bin/env python3
"""Bounded localhost-only HTTP/STOMP delivery and reconnect soak. Uses disposable accounts, never public URLs."""
from __future__ import annotations
import json, os, pathlib, statistics, time, uuid
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright, expect
BASE=os.environ.get('MESSENGER_E2E_URL','http://127.0.0.1:8080')
assert urlsplit(BASE).hostname in ('localhost','127.0.0.1'), 'Local test server only'
DURATION=int(os.environ.get('MESSENGER_SOAK_SECONDS','60'))
assert 10<=DURATION<=1800, 'Use 10..1800 seconds; explicit isolated test only'
OUT=pathlib.Path('build/e2e-artifacts');OUT.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get('MESSENGER_CHROMIUM'))
    contexts=[];pages=[];tokens=[];ids=[];password='Soak_Test_42!';suffix=uuid.uuid4().hex[:10]
    for index in range(2):
        c=browser.new_context();contexts.append(c);user=f'soak{index}_{suffix}';ids.append(user)
        assert c.request.post(BASE+'/api/auth/register',data={'id':user,'userName':user,'password':password}).status==201
        tokens.append(c.request.post(BASE+'/api/auth/login',data={'id':user,'password':password}).json()['token'])
        page=c.new_page();pages.append(page);page.goto(BASE+'/login')
        page.locator('#login-id').fill(user);page.locator('#login-password').fill(password);page.locator('#auth-submit').click()
        expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
    room=contexts[0].request.post(BASE+'/api/rooms/dm',headers={'Authorization':'Bearer '+tokens[0]},data={'peerId':ids[1]}).json()['id']
    for page in pages:
        page.goto(BASE+'/chat/'+room);expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=30000)
    latencies=[];count=0;reconnects=0;started=time.monotonic()
    while time.monotonic()-started<DURATION:
        before=time.monotonic();content=f'soak-{suffix}-{count}'
        payload={'roomId':room,'content':content,'clientRequestId':str(uuid.uuid4())}
        h={'Authorization':'Bearer '+tokens[0]}
        one=contexts[0].request.post(BASE+'/api/messages',headers=h,data=payload)
        two=contexts[0].request.post(BASE+'/api/messages',headers=h,data=payload)
        assert one.status==two.status==200 and one.json()['id']==two.json()['id']
        expect(pages[1].locator('.message-content').filter(has_text=content)).to_have_count(1,timeout=15000)
        latencies.append((time.monotonic()-before)*1000);count+=1
        if count%10==0:
            pages[1].reload();expect(pages[1].locator('#connection-state')).to_have_text('연결됨',timeout=30000);reconnects+=1
            expect(pages[1].locator('.message-content').filter(has_text=content)).to_have_count(1)
        pages[0].wait_for_timeout(1100)  # Respect the real 120 messages/min rate limit including retries.
    report={'durationSeconds':round(time.monotonic()-started,2),'messages':count,'httpRetries':count,'browserReconnects':reconnects,
            'medianDeliveryMs':round(statistics.median(latencies),2),'maxDeliveryMs':round(max(latencies),2),
            'scope':'Two local browser clients. Not a production load-capacity or 24h stability claim.'}
    (OUT/'soak.json').write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
    for c in contexts:c.close()
    browser.close()
