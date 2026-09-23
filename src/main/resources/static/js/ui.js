'use strict';
(() => {
  const paths = {
    plus:'M12 5v14M5 12h14',x:'M6 6l12 12M18 6L6 18',search:'M21 21l-5-5M19 10a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
    bell:'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',users:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-4M17 3a4 4 0 0 1 0 8',
    chat:'M21 11a9 9 0 0 1-9 9H3l2-5a9 9 0 1 1 16-4M8 10h8M8 14h5',hash:'M5 9h14M4 15h14M11 3L7 21M17 3l-4 18',
    settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M10 2h4l1 3 3 1 3 3-2 3 2 3-3 3-3 1-1 3h-4l-1-3-3-1-3-3 2-3-2-3 3-3 3-1z',
    send:'M22 2L9 15M22 2l-8 20-5-7-7-5z',arrow:'M5 12h14M13 6l6 6-6 6',refresh:'M20 7v5h-5M4 17v-5h5M6 5a8 8 0 0 1 13 3M18 19A8 8 0 0 1 5 16',
    edit:'M15 5l4 4M4 20l4-1L21 6l-4-4L4 15z',trash:'M3 6h18M8 6V3h8v3M5 6l1 15h12l1-15M10 10v7M14 10v7',copy:'M9 9h12v12H9zM15 5V2H2v13h3',
    smile:'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0M8 9h.01M16 9h.01M8 15q4 5 8 0',menu:'M4 6h16M4 12h16M4 18h16',mute:'M3 3l18 18M10 3a6 6 0 0 1 8 5v3M6 8c0 7-3 7-3 9h14M10 21h4',logout:'M9 3H3v18h6M10 12h12M17 7l5 5-5 5',check:'M4 12l5 5L20 6'
  };
  const el = (tag, cls, text) => { const n = document.createElement(tag); if(cls) n.className=cls; if(text!==undefined) n.textContent=text; return n; };
  function icon(name) { const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'); svg.setAttribute('viewBox','0 0 24 24'); svg.setAttribute('class','icon'); svg.setAttribute('fill','none'); svg.setAttribute('stroke','currentColor'); svg.setAttribute('stroke-width','1.7'); svg.setAttribute('stroke-linecap','round'); svg.setAttribute('stroke-linejoin','round'); svg.setAttribute('aria-hidden','true'); const p=document.createElementNS(svg.namespaceURI,'path'); p.setAttribute('d',paths[name]||paths.chat); svg.append(p); return svg; }
  function button(label, action, cls='secondary', glyph) { const n=el('button',cls); n.type='button'; n.setAttribute('aria-label',label); n.title=label; if(glyph)n.append(icon(glyph)); if(!cls.includes('icon-button'))n.append(document.createTextNode(label)); n.addEventListener('click',action); return n; }
  function avatar(name, self=false) { const n=el('span',`avatar${self?' self-avatar':''}`,Array.from(name||'?')[0]?.toUpperCase()); n.setAttribute('aria-hidden','true'); return n; }
  function empty(title,description,action) { const box=el('div','empty-state'); box.append(icon('chat'),el('h3','',title),el('p','',description)); if(action)box.append(action); return box; }
  function toast(title,description,onClick) { const stack=document.getElementById('toast-stack'); if(!stack)return; const box=el('div','toast'); const body=el(onClick?'button':'div','toast-body'); if(onClick){body.type='button';body.onclick=()=>{onClick();box.remove();};} body.append(el('strong','',title),el('p','',description)); box.append(icon('bell'),body,button('알림 닫기',()=>box.remove(),'icon-button','x')); stack.append(box); while(stack.children.length>3)stack.firstElementChild.remove(); setTimeout(()=>box.remove(),8000); }
  const storage={get(key,fallback=null){try{const value=localStorage.getItem(key);return value===null?fallback:JSON.parse(value);}catch(_){return fallback;}},set(key,value){try{localStorage.setItem(key,JSON.stringify(value));return true;}catch(_){return false;}},remove(key){try{localStorage.removeItem(key);}catch(_){}}};
  function form({title,description='',fields=[],submitText='저장',danger=false,onSubmit}) {
    const dialog=document.getElementById('app-dialog'); const body=document.getElementById('dialog-body');
    if(dialog.open)dialog.close(); body.replaceChildren(); document.getElementById('dialog-title').textContent=title;
    if(description)body.append(el('p','dialog-description',description));
    const form=el('form'); const inputs={};
    for(const field of fields){const label=el('label','form-field',field.label);const input=el(field.type==='textarea'?'textarea':field.type==='select'?'select':'input');input.name=field.name; if(field.type==='select'){for(const [value,text]of field.options){const o=el('option','',text);o.value=value;input.append(o);}}else if(field.type!=='textarea')input.type=field.type||'text';input.value=field.value||'';input.required=field.required!==false;if(field.maxLength)input.maxLength=field.maxLength;if(field.placeholder)input.placeholder=field.placeholder;inputs[field.name]=input;label.append(input);form.append(label);}
    const error=el('p','dialog-error'); error.setAttribute('role','alert'); const actions=el('div','dialog-actions'); const cancel=button('취소',()=>dialog.close(),'secondary'); const submit=el('button',danger?'danger-button':'primary',submitText);submit.type='submit';actions.append(cancel,submit);form.append(error,actions);body.append(form);
    form.onsubmit=async event=>{event.preventDefault();if(submit.disabled)return;submit.disabled=true;error.textContent='';try{await onSubmit(Object.fromEntries(Object.entries(inputs).map(([k,v])=>[k,v.value])));if(body.contains(form))dialog.close();}catch(e){error.textContent=e.message||'요청을 처리하지 못했습니다.';}finally{submit.disabled=false;}};
    dialog.showModal(); return {dialog,body,form,inputs};
  }
  window.UI={el,icon,button,avatar,empty,toast,storage,form};
  document.addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('[data-icon]').forEach(n=>n.replaceChildren(icon(n.dataset.icon)));document.getElementById('dialog-close')?.addEventListener('click',()=>document.getElementById('app-dialog').close());});
})();
