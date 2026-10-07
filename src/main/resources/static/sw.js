/* No offline cache of credentials, messages or attachments. Native browser push delivery only. */
self.addEventListener('install',()=>self.skipWaiting());
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('push',event=>event.waitUntil((async()=>{
  let data;try{data=event.data?.json();}catch{return;}
  if(!data || typeof data.roomId!=='string' || !/^[A-Za-z0-9_-]{1,100}$/.test(data.roomId))return;
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  if(windows.some(client=>client.visibilityState==='visible'))return;
  await self.registration.showNotification('Messenger', {body:data.kind==='call'?'음성통화가 도착했습니다. 열어서 수락하세요.':'새 메시지가 도착했습니다.',tag:(data.kind==='call'?'call-':'room-')+data.roomId,
    data:{roomId:data.roomId},renotify:false});
})()));
self.addEventListener('notificationclick',event=>{
  event.notification.close();const room=event.notification.data?.roomId;
  if(typeof room!=='string' || !/^[A-Za-z0-9_-]{1,100}$/.test(room))return;
  event.waitUntil((async()=>{
    const url=new URL('/chat/'+encodeURIComponent(room),self.location.origin).href;
    const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
    const client=windows.find(client=>new URL(client.url).origin===self.location.origin);
    if(client){await client.navigate(url);await client.focus();}else await self.clients.openWindow(url);
  })());
});
self.addEventListener('message',event=>{
  if(event.data?.type==='LOGOUT')event.waitUntil(self.registration.getNotifications().then(items=>items.forEach(item=>item.close())));
});
