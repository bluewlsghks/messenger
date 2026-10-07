"""Two local app processes, real external RabbitMQ and Elasticsearch. No physical TURN/Web Push provider."""
import json,os,pathlib,time,uuid
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright,expect
A=os.environ.get('MESSENGER_E2E_URL','http://127.0.0.1:8080')
B=os.environ.get('MESSENGER_SECOND_URL','http://127.0.0.1:8081')
ES=os.environ.get('MESSENGER_ELASTIC_URL','http://127.0.0.1:9200')
for url in (A,B,ES):assert urlparse(url).hostname in ('localhost','127.0.0.1')
assert A!=B
TOKEN=os.environ['APP_OPERATIONS_TOKEN'];suffix=uuid.uuid4().hex[:8];password='Cluster_Test_42!'
checks=[];out=pathlib.Path('build/e2e-artifacts');out.mkdir(parents=True,exist_ok=True)
def passed(value):checks.append(value);print('PASS infrastructure:',value,flush=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=os.environ.get('MESSENGER_CHROMIUM'))
 contexts=[];pages=[];ids=[];tokens=[];errors=[]
 for name,base in [('alice',A),('bob',B)]:
  c=browser.new_context();contexts.append(c);identifier=name+'_'+suffix;ids.append(identifier)
  assert c.request.post(base+'/api/auth/register',data={'id':identifier,'userName':name,'password':password}).status==201
  page=c.new_page();pages.append(page);page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(base+'/login');page.locator('#login-id').fill(identifier);page.locator('#login-password').fill(password);page.locator('#auth-submit').click()
  expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=45000);tokens.append(page.evaluate('Auth.getToken()'))
 def api(index,method,path,data=None,expected=200,base=None):
  response=contexts[index].request.fetch((base or [A,B][index])+path,method=method,data=data,headers={'Authorization':'Bearer '+tokens[index]})
  assert response.status==expected,(path,response.status,response.text()[:200])
  return response.json() if expected not in (204,) else None
 # A JWT and session issued on B must be valid on A without sticky HTTP routing.
 assert api(1,'GET','/api/users/me',base=A)['id']==ids[1]
 passed('Authentication sessions are shared between independent app processes')
 room=api(0,'POST','/api/rooms/dm',{'peerId':ids[1]})['id']
 for page,base in zip(pages,[A,B]):page.goto(base+'/chat/'+room);expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=45000)
 content='검색연동 '+suffix
 sent=api(0,'POST','/api/messages',{'roomId':room,'content':content,'clientRequestId':str(uuid.uuid4())})
 expect(pages[1].locator('.message-content').last).to_have_text(content,timeout=30000)
 passed('RabbitMQ delivers a message from app A to a browser connected to app B')
 index='messenger-messages-v1';deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  response=contexts[0].request.get(ES+'/'+index+'/_doc/'+sent['id'])
  if response.status==200:break
  pages[0].wait_for_timeout(300)
 assert response.status==200 and response.json()['_source']['content']==content
 contexts[0].request.post(ES+'/'+index+'/_refresh')
 assert any(m['id']==sent['id'] for m in api(0,'GET',f'/api/messages/{room}/search?q={suffix}'))
 passed('Persistent outbox asynchronously indexes a real Elasticsearch document and search returns it')
 api(0,'DELETE',f"/api/messages/{room}/{sent['id']}?version=0")
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  response=contexts[0].request.get(ES+'/'+index+'/_doc/'+sent['id'])
  if response.status==200 and response.json()['_source']['deleted']:break
  pages[0].wait_for_timeout(300)
 assert response.json()['_source']['deleted'] and response.json()['_source']['content']==''
 contexts[0].request.post(ES+'/'+index+'/_refresh')
 assert not api(0,'GET',f'/api/messages/{room}/search?q={suffix}')
 passed('Deletion becomes a versioned Elasticsearch tombstone and cannot be returned by search')
 headers={'X-Operations-Token':TOKEN}
 assert contexts[0].request.post(A+'/api/operations/search/reindex',headers=headers).status==202
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  status=contexts[0].request.get(B+'/api/operations/search',headers=headers).json()
  if status.get('reindex',{}).get('pending') is False:break
  pages[0].wait_for_timeout(300)
 assert status['enabled'] and status['reindex']['pending'] is False
 assert contexts[0].request.get(B+'/api/operations/audit',headers=headers).json()['loginIdUniqueIndex']
 passed('Reindex work and restricted operator diagnostics are available across app processes')
 server=api(0,'POST','/api/servers',{'name':'ACL '+suffix},expected=201)
 invite=api(0,'POST','/api/servers/'+server['id']+'/invites',{},expected=201)
 api(1,'POST','/api/servers/join',{'code':invite['code']})
 channel=server['channels'][0]['id']
 page=pages[1];page.goto(B+'/chat/'+channel);expect(page.locator('#connection-state')).to_have_text('연결됨',timeout=45000)
 api(0,'PUT',f"/api/servers/{server['id']}/channels/{channel}/permissions",{'readers':[],'writers':[]},expected=204)
 api(1,'GET','/api/messages/'+channel,expected=403)
 secret='not-delivered-'+suffix
 api(0,'POST','/api/messages',{'roomId':channel,'content':secret})
 page.wait_for_timeout(2000)
 assert page.locator('.message-content').filter(has_text=secret).count()==0
 passed('Removing channel access on A blocks API and real broker delivery on B')
 assert not errors,errors
 (out/'infrastructure-checks.json').write_text(json.dumps(checks,indent=2));browser.close()
