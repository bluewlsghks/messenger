# 1:1 음성통화

2026-10-06 추가. 기존 Thymeleaf/Vanilla JavaScript 작업 화면, JWT, DM, STOMP 개인 이벤트를 재사용합니다. 이 문서는 구현 범위와 실행 조건을 설명하며 TURN 호스팅·유료 서비스 구매·운영 배포 완료를 뜻하지 않습니다.

## 사용 방법

두 사용자 모두 메신저에 로그인하고 왼쪽 아래 상태가 `연결됨`인지 확인합니다. 친구에게 메시지를 보내 DM을 연 다음 상단의 `☎` 버튼을 누릅니다. 발신자는 이때 마이크 사용 권한을 허용합니다. 상대방은 어느 내부 화면에 있든 수신 패널에서 `수락` 또는 `거절`을 선택할 수 있습니다. 수신자의 마이크는 **수락을 누른 뒤에만** 요청합니다.

연결 후 통화 시간, `마이크 끄기/켜기`, 상대방 음량, `통화 종료`를 사용할 수 있습니다. 브라우저가 소리 자동 재생을 차단하면 `상대방 소리 재생` 버튼을 누릅니다. 앱 안의 친구·대화·서버 이동은 통화를 유지하지만 새로고침, 탭 종료, 로그아웃, STOMP 연결 상실은 통화를 종료합니다. 브라우저를 닫거나 휴대폰을 잠근 상태에서 전화 앱처럼 수신하는 기능은 없습니다.

테스트용 두 계정은 다른 브라우저 프로필 또는 일반 창/시크릿 창으로 분리합니다. 같은 일반 탭끼리는 로그인 저장소가 공유됩니다. 한 계정의 여러 탭에는 수신 패널이 표시되지만 먼저 수락한 탭만 연결하며 다른 탭은 패널을 닫습니다. 여러 스피커를 가까이 두면 하울링이 생길 수 있으므로 이어폰을 사용합니다.

## 실행 환경과 네트워크

`getUserMedia()`는 보안 컨텍스트와 사용자 허용이 필요합니다. 개발 PC에서는 `http://localhost:8080` 또는 `http://127.0.0.1:8080`, 외부 접속은 HTTPS를 사용합니다. 휴대폰에서 `http://192.168.x.x:8080`으로 접속하는 방식은 마이크 권한이 제공되지 않을 수 있습니다. 기존 공개 접속은 [PUBLIC_ACCESS.md](PUBLIC_ACCESS.md)를 따르고 현재 HTTPS Origin을 `APP_ALLOWED_ORIGINS`에 정확히 등록합니다.

기본값은 `VOICE_STUN_URLS=stun:stun.l.google.com:19302`이며 별도 통화 SDK나 유료 계정은 추가하지 않았습니다. 이 주소의 가용성이나 모든 네트워크의 P2P 성공을 보장하지 않습니다. 회사 방화벽·일부 NAT/모바일망에서 직접 연결이 막히면 별도의 TURN이 필요합니다. 웹용 HTTPS 터널은 애플리케이션·시그널링 경로일 뿐 WebRTC 음성의 TURN 중계 서버를 대신하지 않습니다.

### 선택적 TURN 설정

이 프로젝트는 coturn의 `use-auth-secret` 방식과 호환되는 자격증명을 발급합니다. **TURN 서버를 자동 생성하거나 설치하지 않습니다.** 이미 준비한 서버의 URL과 공유 비밀값을 애플리케이션 실행 환경에 설정합니다.

```powershell
# 아래 URL은 예시이며 실제 준비한 서버 주소로 교체합니다.
$env:VOICE_TURN_URLS = "turn:turn.example.com:3478?transport=udp,turns:turn.example.com:5349?transport=tcp"
$env:VOICE_TURN_SECRET = "<TURN 서버와 동일한 서버 전용 공유 비밀값>"
$env:VOICE_RELAY_ONLY = "false"
.\gradlew.bat bootRun
```

coturn은 `use-auth-secret` 및 대응하는 `static-auth-secret` 또는 비밀값 저장소를 설정해야 합니다. `turns:`를 사용하려면 해당 서버의 TLS 인증서와 리스너가 실제로 구성되어 있어야 합니다. DNS, 공개 IP/NAT 매핑, 리스너·릴레이 포트 방화벽, 할당량·대역폭 제한, 내부망/루프백 peer 접근 제한과 운영비는 TURN 운영자가 별도 검토해야 합니다. 익명 공개 릴레이를 만들지 않습니다. 이 문서의 두 환경 변수만으로 TURN 서버 설치가 끝나는 것은 아닙니다.

서버는 `만료 Unix 시각:로그인ID` 형식 사용자명과 `Base64(HMAC-SHA1(sharedSecret, username))` 자격증명을 생성합니다. 발급 유효기간은 2시간이며 통화는 최대 1시간입니다. `/api/voice/config`는 JWT 인증이 필요하고 `Cache-Control: no-store`를 반환합니다. 브라우저에 임시 자격증명은 전달되지만 **공유 비밀값 자체는 전달하지 않습니다.** 이미 발급된 자격증명의 즉시 회수·계정별 TURN 사용량 과금 제한은 구현하지 않았습니다.

기본 `iceTransportPolicy=all`은 직접 연결 후보를 사용하므로 상대에게 네트워크 주소가 알려질 수 있습니다. 직접 후보를 사용하지 않으려면 TURN을 구성한 뒤 `VOICE_RELAY_ONLY=true`로 설정합니다. TURN 없이 relay-only를 설정하거나 TURN URL만 설정하고 비밀값을 빠뜨리면 서버 시작을 거부합니다. URL은 쉼표로 구분하며 STUN에는 `stun:`/`stuns:`, TURN에는 `turn:`/`turns:` 스킴만 허용합니다.

로컬 자동화 테스트에서는 `VOICE_STUN_URLS`를 빈 값으로 설정해 외부 STUN 없이 host 후보만 사용합니다. 이 설정은 외부망 연결 품질 검증용이 아닙니다.

## 프로토콜

모든 명령은 `Authorization: Bearer <JWT>`가 있는 REST로 전송합니다. 실제 음성은 WebRTC로 전달하며 REST/STOMP 요청에 음성 파일이나 프레임을 넣지 않습니다.

### 통화 요청

`POST /api/voice/calls` → `201`과 통화 View.

```json
{
  "callId": "f2c36e60-fc73-4931-97bb-c62d93cc84ca",
  "clientId": "d7e80f0d-e8e9-4dc8-8fe5-8da0b5f2a449",
  "roomId": "EXISTING_DIRECT_ROOM_ID"
}
```

`callId`와 `clientId`는 UUID입니다. `clientId`는 페이지 로드마다 새로 만들어 같은 계정의 탭을 구분하며 인증 수단을 대체하지 않습니다. 통화 ID를 먼저 생성해 HTTP 응답보다 STOMP 수락 이벤트가 먼저 도착해도 같은 통화를 식별합니다. 상대방 ID는 서버가 인증 사용자와 해당 DIRECT 방의 두 멤버로 결정합니다.

### 통화 명령

`POST /api/voice/calls/{callId}` → 성공 시 `204`.

```json
{
  "clientId": "d7e80f0d-e8e9-4dc8-8fe5-8da0b5f2a449",
  "action": "END"
}
```

| action | 허용 및 데이터 |
|---|---|
| `ACCEPT` | 수신자만, RINGING에서 한 번. 수락 탭을 고정 |
| `DECLINE` | 수신자만, 아직 수락하지 않은 통화 |
| `END` | 통화 당사자. 수신자가 마이크 준비 중 취소할 때도 허용. 이미 없는 통화의 반복 종료는 no-op |
| `OFFER` | 수락 후 발신 탭에서 한 번, `sdp` 문자열 |
| `ANSWER` | OFFER 뒤 수신 탭에서 한 번, `sdp` 문자열 |
| `ICE` | 수락 후 선택된 각 탭, `candidate` 객체 |
| `PING` | 해당 통화·탭의 활동 시각 갱신 |

ICE 객체는 브라우저 `RTCIceCandidate.toJSON()` 형태인 `candidate`, `sdpMid`, `sdpMLineIndex`, 선택적 `usernameFragment`입니다. `sdpMid` 또는 `sdpMLineIndex` 중 하나가 필요합니다. SDP는 16,000자, candidate 문자열은 2,048자, 참여자별 ICE는 256개 제한입니다.

상태가 맞지 않거나 이미 통화 중이면 409, 잘못된 입력은 400, 인증/멤버십 실패는 401/403, 이미 종료된 통화의 END 이외 명령은 404, 발신 간격/후보 제한 초과는 429입니다. 클라이언트 UI는 서버 오류를 표시하고 자신의 통화 자원을 정리합니다.

### 개인 이벤트

기존 `/user/queue/events`에 다음 구조를 보냅니다. 새 공개 broadcast 토픽은 만들지 않습니다.

```json
{
  "type": "VOICE_CALL",
  "action": "RING",
  "roomId": "EXISTING_DIRECT_ROOM_ID",
  "call": {
    "id": "f2c36e60-fc73-4931-97bb-c62d93cc84ca",
    "roomId": "EXISTING_DIRECT_ROOM_ID",
    "callerId": "alice",
    "calleeId": "bob",
    "callerClientId": "d7e80f0d-e8e9-4dc8-8fe5-8da0b5f2a449",
    "calleeClientId": null,
    "status": "RINGING",
    "expiresAt": 1791244800000
  },
  "targetClientId": null,
  "sdp": null,
  "candidate": null,
  "reason": null
}
```

`expiresAt`은 Unix 밀리초 예시입니다. 이벤트 action은 `RING`, `ACCEPTED`, `OFFER`, `ANSWER`, `ICE`, `ENDED`입니다. 협상 메시지에는 대상 탭 `targetClientId`를 넣으며 다른 탭은 처리하지 않습니다. `ENDED`의 reason은 `DECLINED`, `HANGUP`, `NO_ANSWER`, `CONNECTION_LOST`, `TIME_LIMIT`입니다. View의 ACCEPTED는 서버가 수락을 기록했다는 뜻이며 실제 미디어 연결 완료는 브라우저 `connectionState`로 판단합니다.

서버 `VoiceEvents`는 `chatRecipient`, `chatRoomId` 헤더를 설정해 기존 STOMP outbound 재인가를 거칩니다. 서버 상태가 정해준 참여자·역할과 JWT를 모두 확인하며 사용자가 보낸 sender ID를 신뢰하지 않습니다.

## 상태와 경쟁 조건

통화 상태는 동기화된 단일 JVM 맵에 있습니다. 사용자당 한 통화만 예약하며 오프라인 상대, 겹치는 발신·수신, 잘못된 통화 제어를 거부합니다. 호출 대기는 45초이고 만료 정리는 5초 주기이므로 알림까지 추가 지연이 있을 수 있습니다. 수락 후 각 참여자는 20초마다 PING을 보내고 한쪽이라도 70초 이상 갱신하지 않으면 정리합니다. 통화는 수락 후 최대 1시간, 빠른 재발신은 3초 간격으로 제한합니다.

브라우저는 통화 세션의 유효성을 비동기 작업마다 재확인합니다. 취소 뒤 늦게 허용된 마이크는 즉시 정지합니다. 시작 HTTP 요청 도중 취소한 뒤 서버가 통화를 생성하는 경쟁에는 늦은 응답을 받은 뒤 END를 다시 보냅니다. 수락 전에 ICE/SDP를 보내지 않으며, 원격 SDP 이전 ICE는 버퍼링하고 협상 명령을 직렬 처리합니다. 같은 ACCEPTED를 중복 수신해도 offer를 다시 만들지 않습니다.

종료 시 RTCPeerConnection, 마이크 트랙, 원격 오디오, 타이머와 후보 큐를 정리합니다. `pagehide`/로그아웃에서는 keepalive END를 최선 노력으로 보내며, 갑작스러운 종료에는 서버 만료가 보완합니다. WebRTC 일시 disconnected 상태는 10초를 기다린 뒤 종료하고, 연결 협상은 30초 제한입니다. ICE restart와 끊긴 통화 자동 복구는 없습니다.

## 검증

로컬 Node 회귀 테스트:

```bash
node --test src/test/js/voice-call.test.cjs
```

Java 단위 및 기존 MongoDB 통합 테스트는 루트 README의 전용 테스트 DB 설정으로 `bash ./gradlew test bootJar` 또는 Windows ` .\gradlew.bat test bootJar`를 실행합니다. Java 음성 테스트 15개는 DM/참여자/역할/탭 인가, 중복·오프라인, 만료·PING·종료 멱등성, 후보 제한, TURN 설정과 HMAC을 확인합니다.

브라우저 검증은 **개발용 localhost 앱**을 먼저 실행하고 CI에 명시된 Playwright/Chromium을 설치한 뒤 실행합니다.

```bash
python3 src/test/e2e/voice_calls.py
```

스크립트는 테스트 계정을 생성하고 가상 마이크를 사용하는 Chromium을 구동합니다. 외부 호스트 실행을 거부합니다. 실제 로그인·STOMP 개인 수신, 수락 전 마이크 미사용, 비멤버 차단, 다중 탭, 양방향 오디오 RTP 수신, 트랙 음소거, 내부 이동 유지, 거절·종료와 자원 해제를 확인합니다. 결과는 `build/e2e-artifacts/voice-checks.json`과 스크린샷에 보관하며 SDP·토큰·비밀값을 결과 JSON에 기록하지 않습니다.

**확인된 실행 결과와 CI 링크는 루트 README의 Tests & Verification을 기준으로 합니다.** 가상 마이크 패킷 검증을 실제 사람이 양쪽 음성을 들은 테스트로 표현하지 않습니다. 다음은 별도 수동 검증이 필요합니다: 실제 두 기기와 이어폰, 권한 거부/장치 제거, 서로 다른 인터넷망, 설정된 TURN의 relay 후보 선택, Safari·모바일 음량/자동 재생, 장시간 백그라운드·잠금·네트워크 전환.

## 남은 범위

1:1 DM·단일 프로세스만 지원합니다. 그룹 음성채널, SFU, 녹음·통화 이력, 카메라·화면 공유, 장치 선택, 벨소리·Web Push, 분산 상태·영속 이벤트·네트워크 단절 뒤 재협상은 구현하지 않았습니다. 통화 권한은 DM 멤버십에 기반하므로 친구 삭제만으로 기존 DM의 통화 권한을 철회하지 않습니다. 차단·수신 허용 정책과 운영 수준 남용 방지는 후속 과제입니다.

## 참고 문서

- [MDN getUserMedia: 보안 컨텍스트와 사용자 권한](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia)
- [MDN WebRTC signaling](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Signaling_and_video_calling)
- [coturn README.turnserver: TURN REST API](https://github.com/coturn/coturn/blob/master/README.turnserver)

기능·프로토콜·설정·검증 범위를 변경하면 이 문서와 루트 README를 같은 변경 묶음에서 함께 갱신합니다.
