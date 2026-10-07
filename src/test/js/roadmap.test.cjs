const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const vm=require('node:vm');
const {windowRange}=require('../../main/resources/static/js/message-window.js');
test('virtual window bounds DOM work for ten thousand messages',()=>{
 const rows=Array.from({length:10000},(_,i)=>({id:String(i)}));const range=windowRange(rows,new Map(),500000,800);
 assert.ok(range.start>4900);assert.ok(range.end-range.start<30);assert.equal(range.top+range.bottom+(range.end-range.start)*100,1000000);
});
test('virtual window handles empty and measured-height lists',()=>{
 assert.deepEqual(windowRange([],new Map(),0,800),{start:0,end:0,top:0,bottom:0,total:0});
 const range=windowRange([{id:'a'},{id:'b'}],new Map([['a',20],['b',300]]),0,100);assert.equal(range.total,320);
});
function worker(){const events={},shown=[],opened=[];const self={location:{origin:'https://messenger.test'},addEventListener:(n,f)=>events[n]=f,
 registration:{showNotification:async(...v)=>shown.push(v),getNotifications:async()=>[]},clients:{matchAll:async()=>[],openWindow:async u=>opened.push(u)}};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/sw.js','utf8'),{self,URL});return {events,shown,opened};}
test('native push worker rejects untrusted room paths and keeps payload generic',async()=>{
 const {events,shown}=worker();let pending;events.push({data:{json:()=>({roomId:'../evil',content:'secret'})},waitUntil:p=>pending=p});await pending;assert.equal(shown.length,0);
 events.push({data:{json:()=>({roomId:'valid-room',content:'secret'})},waitUntil:p=>pending=p});await pending;assert.equal(shown.length,1);assert.ok(!JSON.stringify(shown).includes('secret'));
});
test('notification click is pinned to the same origin',async()=>{
 const {events,opened}=worker();let pending;events.notificationclick({notification:{data:{roomId:'room-1'},close(){}},waitUntil:p=>pending=p});await pending;
 assert.deepEqual(opened,['https://messenger.test/chat/room-1']);
});
