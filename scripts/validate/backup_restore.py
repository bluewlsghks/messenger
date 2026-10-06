#!/usr/bin/env python3
"""Disposable local Database Tools round trip. Never uses an existing application database."""
import json,os,pathlib,subprocess,sys,uuid
from urllib.parse import urlsplit
uri=os.environ.get('MONGODB_URI','mongodb://127.0.0.1:27017')
assert urlsplit(uri).hostname in ('localhost','127.0.0.1')
suffix=uuid.uuid4().hex;source='messenger_backup_test_'+suffix;target='messenger_restore_test_'+suffix
out=pathlib.Path('build/backup-test');out.mkdir(parents=True,exist_ok=True);archive=out/(suffix+'.archive.gz')
env=dict(os.environ,MONGODB_URI=uri,BACKUP_SOURCE=source,BACKUP_TARGET=target)
def mongo(code):
 command="const c=new Mongo(process.env.MONGODB_URI);const s=c.getDB(process.env.BACKUP_SOURCE);const t=c.getDB(process.env.BACKUP_TARGET);"+code
 return subprocess.check_output(['mongosh','--nodb','--quiet','--eval',command],env=env,text=True).strip()
try:
 mongo("s.messages.insertMany([{content:'first'},{content:'second'}]);s.auth_sessions.insertOne({secret:'disposable-fixture'});s.createCollection('fs.files');")
 base=[sys.executable,'scripts/ops/mongo_backup.py']
 subprocess.run(base+['backup','--source',source,'--archive',str(archive),'--writers-stopped'],env=env,check=True)
 subprocess.run(base+['restore','--source',source,'--archive',str(archive),'--writers-stopped','--target',target,'--confirm-new-database',target],env=env,check=True)
 counts=json.loads(mongo("print(JSON.stringify({messages:t.messages.countDocuments({}),sessions:t.auth_sessions.countDocuments({}),source:s.messages.countDocuments({})}));"))
 assert counts=={'messages':2,'sessions':0,'source':2},counts
 result=subprocess.run(base+['restore','--source',source,'--archive',str(archive),'--writers-stopped','--target',target,'--confirm-new-database',target],env=env,capture_output=True)
 assert result.returncode!=0,'Existing target unexpectedly accepted'
 assert json.loads(mongo("print(JSON.stringify(t.messages.countDocuments({})));"))==2
 (out/'result.json').write_text(json.dumps({'roundTrip':True,'ephemeralExcluded':True,'existingTargetRefused':True,'sourcePreserved':True},indent=2))
 print('PASS backup: real archive round trip, session exclusion, existing-target refusal, source preservation')
finally:
 # These exact UUID databases were allocated above, never from a caller-supplied target.
 mongo("s.dropDatabase();t.dropDatabase();")
 archive.unlink(missing_ok=True)
