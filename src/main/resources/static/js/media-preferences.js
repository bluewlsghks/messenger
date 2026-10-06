(() => {
  let context,ringTimer;
  const preferences={microphone:localStorage.getItem('messenger:microphone')||'',speaker:localStorage.getItem('messenger:speaker')||'',
    ringEnabled:localStorage.getItem('messenger:ring')==='true',
    stopRing(){clearInterval(ringTimer);ringTimer=null;},
    ring(){preferences.stopRing();if(!preferences.ringEnabled)return;
      const beep=()=>{try{context=context||new (window.AudioContext||window.webkitAudioContext)();if(context.state!=='running')return;
        const oscillator=context.createOscillator(),gain=context.createGain();oscillator.frequency.value=660;gain.gain.value=0.05;
        oscillator.connect(gain).connect(context.destination);oscillator.start();oscillator.stop(context.currentTime+0.25);}catch(_){}};
      beep();ringTimer=setInterval(beep,1800);
    }};
  window.MediaPreferences=preferences;
  document.addEventListener('DOMContentLoaded',()=>{
    const target=document.querySelector('.notification-footer');if(!target)return;
    const button=UI.button('통화 장치 · 벨소리',async()=>{
      try{
        const devices=await navigator.mediaDevices.enumerateDevices();
        const options=kind=>[['','시스템 기본 장치'],...devices.filter(d=>d.kind===kind).map((d,i)=>[d.deviceId,d.label||`장치 ${i+1} (마이크 허용 후 이름 표시)`])];
        UI.form({title:'통화 장치 설정',description:'설정 화면을 열기만 해서는 마이크를 켜지 않습니다. 출력 장치 선택은 브라우저 지원 여부에 따라 제한됩니다.',fields:[
          {name:'microphone',label:'마이크',type:'select',value:preferences.microphone,options:options('audioinput'),required:false},
          {name:'speaker',label:'스피커',type:'select',value:preferences.speaker,options:options('audiooutput'),required:false},
          {name:'ring',label:'벨소리',type:'select',value:String(preferences.ringEnabled),options:[['false','꺼짐'],['true','켜짐']]}],onSubmit:async values=>{
            await window.messengerVoice?.changeMicrophone(values.microphone);await window.messengerConference?.changeMicrophone(values.microphone);
            preferences.microphone=values.microphone;preferences.speaker=values.speaker;preferences.ringEnabled=values.ring==='true';
            localStorage.setItem('messenger:microphone',values.microphone);localStorage.setItem('messenger:speaker',values.speaker);localStorage.setItem('messenger:ring',values.ring);
            if(preferences.ringEnabled){context=context||new (window.AudioContext||window.webkitAudioContext)();await context.resume();}
          }});
      }catch(error){UI.toast('통화 설정',error.message);}
    },'secondary');target.append(button);
    target.append(UI.button('최근 통화',async()=>{try{const rows=await Auth.request('/api/voice/history');
      const dialog=UI.form({title:'최근 통화',description:'내 통화 이력 최대 100개 · 30일 보관. 음성이나 화면은 저장하지 않습니다.',submitText:'닫기',onSubmit:async()=>{}});
      const list=UI.el('div','feature-list');for(const row of rows){const item=UI.el('div','feature-row');
        item.append(UI.el('span','',`${new Date(row.startedAt).toLocaleString()} · ${row.kind==='CONFERENCE'?'다인 통화':row.callerId===Auth.getLoginId()?row.calleeId:row.callerId} · ${row.reason}`));list.append(item);}if(!rows.length)list.append(UI.el('p','muted','통화 이력이 없습니다.'));dialog.form.prepend(list);
      }catch(error){UI.toast('통화 이력',error.message);}},'secondary'));
    window.addEventListener('pagehide',preferences.stopRing);window.addEventListener('messenger:logout',preferences.stopRing);
  });
})();
