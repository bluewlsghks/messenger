'use strict';
(() => {
  const getToken=()=>localStorage.getItem('token');
  const getLoginId=()=>localStorage.getItem('loginId');
  const getUserName=()=>localStorage.getItem('userName');
  let stompClient=null;
  function setAuth(data){if(data.token)localStorage.setItem('token',data.token);if(data.id)localStorage.setItem('loginId',data.id);if(data.userName)localStorage.setItem('userName',data.userName);}
  function clearAuthStorage(){const me=getLoginId();Object.keys(localStorage).filter(k=>k.startsWith(`messenger:draft:${me}:`)).forEach(k=>localStorage.removeItem(k));['token','loginId','userName'].forEach(k=>localStorage.removeItem(k));}
  function disconnectStomp(){try{if(stompClient?.deactivate)stompClient.deactivate({force:true});else if(stompClient?.connected)stompClient.disconnect(()=>{});}catch(_){}stompClient=null;}
  async function authFetch(url,options={}){
    const target=new URL(url,location.origin);if(target.origin!==location.origin)throw new Error('외부 주소로 인증 정보를 전송할 수 없습니다.');
    const headers=new Headers(options.headers||{});const token=getToken();if(token)headers.set('Authorization',`Bearer ${token}`);
    if(typeof options.body==='string'&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);
    try{const res=await fetch(target,{...options,headers,signal:options.signal||controller.signal});if(res.status===401){disconnectStomp();clearAuthStorage();location.assign('/login');throw new Error('로그인이 만료되었습니다.');}return res;}
    catch(error){if(error.name==='AbortError')throw new Error('서버 응답이 지연되고 있습니다. 다시 시도해 주세요.');throw error;}
    finally{clearTimeout(timer);}
  }
  async function request(url,options={}){const res=await authFetch(url,options);if(res.status===204)return null;let data;try{data=await res.json();}catch(_){data={};}if(!res.ok){const error=new Error(data.message||`요청을 처리하지 못했습니다. (${res.status})`);error.status=res.status;error.code=data.error;throw error;}return data;}
  function logout(){window.dispatchEvent(new Event('messenger:logout'));disconnectStomp();clearAuthStorage();location.assign('/login');}
  function requireLogin(){if(!getToken()){location.replace('/login');return false;}return true;}
  function attachLogoutButton(id){const button=document.getElementById(id);if(button&&!button.dataset.logoutBound){button.dataset.logoutBound='true';button.addEventListener('click',logout);}}
  window.Auth={getToken,getLoginId,getUserName,setAuth,clearAuthStorage,authFetch,request,logout,requireLogin,attachLogoutButton,disconnectStomp,setStompClient:client=>{stompClient=client;},getStompClient:()=>stompClient,token:getToken,loginId:getLoginId,userName:getUserName,clear:clearAuthStorage,fetch:authFetch};
})();
