const test=require('node:test');const assert=require('node:assert/strict');
const {reconcile}=require('../../main/resources/static/js/message-sync.js');
const {EMOJI,members}=require('../../main/resources/static/js/message-collaboration.js');
const page=(cursor,items=[],hasMore=false,reset=false)=>({cursor,items,hasMore,reset});
test('incremental cursor is committed only after successful page application',async()=>{
 const state={cursor:'epoch:10'},urls=[];let applied=false;
 await reconcile({roomId:'room',state,current:()=>true,request:async url=>{urls.push(url);return page('epoch:11',[{id:'a'}]);},apply:()=>{assert.equal(state.cursor,'epoch:10');applied=true;}});
 assert.equal(state.cursor,'epoch:11');assert.ok(applied);assert.equal(urls.length,1);assert.match(urls[0],/changes/);assert.doesNotMatch(urls[0],/\/sync\?/);
});
test('duplicate invalidations still advance using server cursor not item count',async()=>{
 const state={cursor:'a:0'},seen=[];let i=0;
 await reconcile({roomId:'r',state,current:()=>true,request:async()=>++i===1?page('a:100',[{id:'same'}],true):page('a:102',[{id:'same'}]),apply:p=>seen.push(p)});
 assert.equal(seen.length,2);assert.equal(state.cursor,'a:102');
});
test('room switch ignores late response and cursor',async()=>{
 const state={cursor:'old'},applied=[];let live=true;
 await reconcile({roomId:'r',state,current:()=>live,request:async()=>{live=false;return page('new');},apply:p=>applied.push(p)});
 assert.equal(state.cursor,'old');assert.deepEqual(applied,[]);
});
test('failed page apply preserves old cursor for safe retry',async()=>{
 const state={cursor:'old'};
 await assert.rejects(reconcile({roomId:'r',state,current:()=>true,request:async()=>page('new'),apply:()=>{throw Error('merge failed');}}),/merge failed/);
 assert.equal(state.cursor,'old');
});
test('reset signal reaches cache handler rather than silently appending stale data',async()=>{
 const state={cursor:'expired'},cache=new Map([['old',{}]]);
 await reconcile({roomId:'r',state,current:()=>true,request:async()=>page('fresh',[{id:'new'}],false,true),apply:p=>{if(p.reset)cache.clear();for(const m of p.items)cache.set(m.id,m);}});
 assert.deepEqual([...cache.keys()],['new']);assert.equal(state.cursor,'fresh');
});
test('sustained changes are bounded to ten requests per pass',async()=>{
 const state={cursor:null};let requests=0;
 await reconcile({roomId:'r',state,current:()=>true,request:async()=>page(`epoch:${++requests}`,[],true),apply:()=>{}});
 assert.equal(requests,10);assert.equal(state.cursor,'epoch:10');
});
test('nonadvancing cursor does not create an infinite loop',async()=>{
 await assert.rejects(reconcile({roomId:'r',state:{cursor:'same'},current:()=>true,request:async()=>page('same',[],true),apply:()=>{}}),/진행/);
});
test('invalid sync response does not corrupt cursor',async()=>{
 const state={cursor:'safe'};await assert.rejects(reconcile({roomId:'r',state,current:()=>true,request:async()=>({items:[]}),apply:()=>{}}));assert.equal(state.cursor,'safe');
});
test('reaction keys are fixed and actor lists are unique',()=>{
 assert.equal(Object.keys(EMOJI).length,7);assert.ok(Object.keys(EMOJI).every(k=>/^[a-z]+$/.test(k)));
 assert.deepEqual(members({reactions:{like:['alice','alice','bob']}},'like'),['alice','bob']);assert.deepEqual(members({},'like'),[]);
});
