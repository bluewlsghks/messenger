(() => {
  let registration;
  async function api(method,path,data){const response=await Auth.authFetch(path,{method,headers:{'Content-Type':'application/json'},body:data?JSON.stringify(data):undefined});
    if(!response.ok)throw new Error((await response.json().catch(()=>({}))).message||'브라우저 알림 설정에 실패했습니다.');return response.status===204?null:response.json();}
  function decode(value){return Uint8Array.from(atob(value.replace(/-/g,'+').replace(/_/g,'/').padEnd(Math.ceil(value.length/4)*4,'=')),char=>char.charCodeAt(0));}
  async function enable(){
    if(!isSecureContext || !('serviceWorker' in navigator) || !('PushManager' in window))throw new Error('이 브라우저는 HTTPS Web Push를 지원하지 않습니다.');
    const configuration=await api('GET','/api/push/config');
    if(!configuration.enabled)throw new Error('서버에 무료 VAPID 키 설정이 필요합니다. README의 Web Push 설정을 확인해 주세요.');
    if(await Notification.requestPermission()!=='granted')throw new Error('알림 권한이 허용되지 않았습니다.');
    registration=await navigator.serviceWorker.register('/sw.js',{scope:'/'});
    await navigator.serviceWorker.ready;
    let subscription=await registration.pushManager.getSubscription();
    if(!subscription)subscription=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:decode(configuration.publicKey)});
    try{await api('POST','/api/push/subscriptions',subscription.toJSON());}
    catch(error){await subscription.unsubscribe();throw error;}
    localStorage.setItem('messenger:push-owner',Auth.getLoginId());
    await syncPreferences();
  }
  async function disable(){
    registration=registration||await navigator.serviceWorker?.getRegistration('/');
    const subscription=await registration?.pushManager.getSubscription();
    if(subscription){await api('DELETE','/api/push/subscriptions',{endpoint:subscription.endpoint}).catch(()=>{});await subscription.unsubscribe();}
    if(registration)(await registration.getNotifications()).forEach(item=>item.close());
    localStorage.removeItem('messenger:push-owner');
  }
  async function syncPreferences(){
    if(localStorage.getItem('messenger:push-owner')!==Auth.getLoginId())return;
    registration=registration||await navigator.serviceWorker?.getRegistration('/');
    const subscription=await registration?.pushManager.getSubscription();if(!subscription)return;
    let preferences={};try{preferences=JSON.parse(localStorage.getItem(`messenger:prefs:${Auth.getLoginId()}`)||'{}');}catch{}
    await api('PUT','/api/push/preferences',{endpoint:subscription.endpoint,mutedRooms:(preferences.muted||[]).slice(0,200)});
  }
  window.WebPush={enable,disable,syncPreferences};
  document.addEventListener('messenger:notification-preferences',()=>syncPreferences().catch(()=>{}));
  const footer=document.querySelector('.notification-footer')||document.querySelector('#notification-panel');
  if(footer){const button=document.createElement('button');button.type='button';button.textContent='브라우저를 닫아도 알림 받기';button.id='web-push-toggle';
    button.onclick=async()=>{try{if(localStorage.getItem('messenger:push-owner')===Auth.getLoginId()){await disable();button.textContent='브라우저를 닫아도 알림 받기';}
      else{await enable();button.textContent='이 브라우저 Push 알림 해제';}}catch(error){alert(error.message);}};
    if(localStorage.getItem('messenger:push-owner')===Auth.getLoginId())button.textContent='이 브라우저 Push 알림 해제';footer.append(button);}
  if(localStorage.getItem('messenger:push-owner') && localStorage.getItem('messenger:push-owner')!==Auth.getLoginId())disable().catch(()=>{});
})();
