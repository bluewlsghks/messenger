'use strict';
document.addEventListener('DOMContentLoaded',()=>{
  const form=document.getElementById('auth-form');if(!form)return;
  const mode=document.body.dataset.authMode;const error=document.getElementById('auth-error');const submit=document.getElementById('auth-submit');const password=document.getElementById('login-password');
  document.getElementById('show-password').onclick=event=>{const show=password.type==='password';password.type=show?'text':'password';event.currentTarget.textContent=show?'숨기기':'보기';event.currentTarget.setAttribute('aria-pressed',String(show));};
  if(new URLSearchParams(location.search).get('registered'))error.textContent='회원가입이 완료되었습니다. 로그인해 주세요.';
  form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;const data=Object.fromEntries(new FormData(form));data.id=data.id.trim();if(data.userName)data.userName=data.userName.trim();if(data.phoneNumber)data.phoneNumber=data.phoneNumber.trim();error.textContent='';
    if(mode==='register'&&data.password!==data.passwordConfirm){error.textContent='비밀번호가 서로 다릅니다.';return;}delete data.passwordConfirm;submit.disabled=true;submit.textContent=mode==='login'?'로그인 중…':'가입 중…';
    try{const response=await fetch(`/api/auth/${mode}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});let result;try{result=await response.json();}catch(_){result={};}if(!response.ok)throw new Error(result.message||'요청을 처리하지 못했습니다.');if(mode==='login'){Auth.setAuth(result);location.assign('/home');}else location.assign('/login?registered=1');}
    catch(e){error.textContent=e.message||'네트워크 연결을 확인해 주세요.';}
    finally{submit.disabled=false;submit.textContent=mode==='login'?'로그인':'회원가입';}
  };
});
