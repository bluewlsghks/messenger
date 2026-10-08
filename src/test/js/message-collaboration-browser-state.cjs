const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const MessageSync = require('../../main/resources/static/js/message-sync.js');

function harness() {
  const nodes = new Map();
  const node = id => { if (!nodes.has(id)) nodes.set(id, {disabled:false}); return nodes.get(id); };
  const scope = {
    window:{}, document:{getElementById:node}, MessageSync,
    MessageMerge:(old,next)=>({...old,...next}), Auth:{request:async()=>{throw Error('unexpected request');}},
    UI:{empty:(title,description)=>({title,description})}
  };
  vm.createContext(scope);
  for (const name of ['message-collaboration.js','conversation.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js',name),'utf8'),scope);
  }
  const chat = Object.create(scope.window.ChatPanel.prototype);
  const ctx = {roomId:'room',messages:new Map(),rows:new Map(),heights:new Map(),sync:{cursor:'before'},
    bookmarks:new Set(),bookmarksKnown:new Set(),bookmarkGeneration:0,cacheGeneration:0,
    collaborationPending:new Set(),initialized:true,blocked:false};
  chat.context=ctx;chat.input={disabled:false};chat.list={replaceChildren:value=>{chat.display=value;}};
  chat.realtime={ready:true};chat.notices={refresh:()=>{}};
  chat.atBottom=()=>false;chat.render=()=>{};chat.queueRead=()=>{};chat.error=value=>{chat.lastError=value;};
  chat.collaboration={invalidate:()=>{},close:()=>{},refresh:async()=>{chat.refreshed=true;}};
  return {chat,ctx,scope};
}
function fill(ctx) {
  for(let i=0;i<1005;i++)ctx.messages.set(String(i),{id:String(i),roomId:'room',createdAt:new Date(i).toISOString()});
}
function deferred() { let resolve; const promise=new Promise(r=>{resolve=r;}); return {promise,resolve}; }

test('background delta does not evict older messages while reading history',async()=>{
  const {chat,ctx,scope}=harness();fill(ctx);
  scope.Auth.request=async()=>({items:[],cursor:'after',reset:false,hasMore:false});
  await chat.reconcile(ctx);
  assert.equal(ctx.messages.size,1005);assert.ok(ctx.messages.has('0'));assert.equal(ctx.sync.cursor,'after');
});
test('latest view still trims to a thousand messages',async()=>{
  const {chat,ctx,scope}=harness();fill(ctx);chat.atBottom=()=>true;
  scope.Auth.request=async()=>({items:[],cursor:'after',reset:false,hasMore:false});
  await chat.reconcile(ctx);
  assert.equal(ctx.messages.size,1000);assert.equal(ctx.oldest.id,'5');assert.equal(ctx.hasMore,true);
});
test('late sync success cannot undo access revocation or erase its error',async()=>{
  const {chat,ctx,scope}=harness();const response=deferred();scope.Auth.request=()=>response.promise;
  const pending=chat.reconcile(ctx);chat.revoke(ctx,'access revoked');
  response.resolve({items:[{id:'secret',roomId:'room'}],cursor:'after',reset:true,hasMore:false});await pending;
  assert.equal(ctx.messages.size,0);assert.equal(ctx.sync.cursor,null);assert.equal(chat.lastError,'access revoked');
  assert.equal(ctx.blocked,true);assert.equal(chat.refreshed,undefined);
});
test('late collaboration status cannot restore private data after revocation',async()=>{
  const {chat,ctx,scope}=harness();const response=deferred();scope.Auth.request=()=>response.promise;
  const collaboration=Object.create(scope.window.MessageCollaboration.prototype);collaboration.chat=chat;
  const pending=collaboration.refresh(ctx);chat.revoke(ctx,'revoked');response.resolve({canReact:true,canPin:true,ids:['secret']});await pending;
  assert.equal(ctx.bookmarks.size,0);assert.equal(ctx.capabilities,null);assert.equal(ctx.blocked,true);
});
test('late mutation response cannot repopulate revoked messages',async()=>{
  const {chat,ctx,scope}=harness();const response=deferred();scope.Auth.request=()=>response.promise;
  const collaboration=Object.create(scope.window.MessageCollaboration.prototype);collaboration.chat=chat;
  const button={disabled:false};const pending=collaboration.change(ctx,{id:'secret'},'pin',true,button);
  chat.revoke(ctx,'revoked');response.resolve({id:'secret',roomId:'room',content:'do not restore'});await pending;
  assert.equal(ctx.messages.size,0);assert.equal(ctx.blocked,true);assert.equal(ctx.collaborationPending.size,0);
});
test('queued realtime event cannot repopulate a revoked room',()=>{
  const {chat,ctx}=harness();chat.revoke(ctx,'revoked');
  chat.notice({roomId:'room',type:'MESSAGE_CREATED',message:{id:'secret',roomId:'room'}});
  assert.equal(ctx.messages.size,0);
});
