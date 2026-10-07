# 로드맵 최종 검증 기록

## 검증 기준

2026-10-07 · 실행한 기능 소스 커밋: **`0e0b8cb1b83e4b208eb4e62bae510495a8709440`**. 뒤따르는 README/문서 정리 커밋은 애플리케이션·테스트·실행 설정을 변경하지 않습니다. 아래 결과는 명시한 소스와 실행의 결과이며, master 병합이나 실제 서비스 배포를 뜻하지 않습니다.

Java 21, 독립적인 MongoDB 7.0.43·RabbitMQ 4.3.1·Elasticsearch 9.4.6 Basic, Node 22, Playwright 1.55/Chromium을 사용했습니다. 테스트 계정과 DB는 CI용으로 생성했으며 실제 사용자 데이터·운영 DB는 변경하지 않았습니다. 유료 서비스 활성화·가입·새 서버 구매는 하지 않았습니다.

## CI 전체 상태

| 실행 | 결과 | 범위 |
|---|---|---|
| [Messenger CI · 37590069565](https://github.com/bluewlsghks/messenger/actions/runs/37590069565) | **성공** | 메신저 빌드와 단일 서버 브라우저 회귀 |
| [Roadmap infrastructure and security · 37590069647](https://github.com/bluewlsghks/messenger/actions/runs/37590069647) | **성공** | Java/MongoDB, 브라우저, 실제 두 앱·브로커·검색·새 DB 복구, OSV |
| [Public access scripts · 37590069644](https://github.com/bluewlsghks/messenger/actions/runs/37590069644) | **성공** | 공개 접속 PowerShell 스크립트 검사 |
| [Public tunnel launcher · 37590069680](https://github.com/bluewlsghks/messenger/actions/runs/37590069680) | **성공** | Windows/Linux 자식 프로세스 및 guardian 정리 |
| [Render free deployment checks · 37590069575](https://github.com/bluewlsghks/messenger/actions/runs/37590069575) | **성공** | 로컬 Docker/배포 구성 검사. Render에 서비스를 배포한 결과 아님 |

## 세부 결과

| 검증 | 확인 결과 |
|---|---|
| Java 단위·실제 MongoDB/HTTP·빌드 | **148개**, 실패 0 / 오류 0 / 건너뜀 0, `bootJar` 성공 |
| JavaScript | **48개 통과**. 활성 통화 상태 대조 신규 8개 포함. 같은 소스를 로컬 Node에서도 재실행 |
| 운영/비용 정책 Python 검사 | **20개 통과** |
| 배포 구성 Python 검사 | **9개 통과** |
| 기존 작업 화면 브라우저 | **10개**, 처리되지 않은 JavaScript 오류 없음 |
| 전화번호 없는 가입·로그인 | **5개 확인** |
| 1:1 WebRTC | 단일 서버 및 **서로 다른 두 서버**에서 동일 8개 시나리오 통과 |
| 신규 로드맵 브라우저 | **10개**: 친구 승인, 멱등 저장, 파일/권한, 멘션/스레드, 입력 상태, 3인 미디어, 세션/운영 권한 |
| 두 앱/RabbitMQ/Elasticsearch | **6개**: 공유 인증, 앱 간 전송, 비동기 색인/검색, 삭제 tombstone, 재색인/운영 진단, 권한 철회 |
| 실제 백업/새 DB 복구 | `roundTrip`, `ephemeralExcluded`, `existingTargetRefused`, `sourcePreserved` 모두 true |
| 터널 프로세스 | Windows/Linux 각각 기존 **17개 + 강제 부모 종료 guardian 검사** 성공. 실제 cloudflared 대신 테스트 자식 프로세스 사용 |
| resolved 의존성 OSV | **152개 패키지**, 조회 완료, 보고된 항목 0. 조회 시각 2026-10-07 07:59:28 UTC |
| 제한된 재연결 반복 검사 | **60.30초**, 메시지 52개·동일 요청 ID 재시도 52회·브라우저 재연결 5회 |

세부 원본은 인프라 실행의 `roadmap-validation` 아티팩트에 있습니다. Java XML 23개 보고서, `infrastructure-checks.json`, `roadmap-checks.json`, `voice-checks.json`, 가입/작업 화면 보고서, `backup-test/result.json`, `security/osv-report.json`, `soak.json`을 확인했습니다. 음성 스크립트를 단일 서버와 두 서버에서 순차 실행하므로 마지막 `voice-checks.json`은 두 서버 실행의 결과입니다. 두 실행의 성공 여부는 완료된 CI 단계와 스크립트 실행 순서로 구분합니다.

OSV 항목 0은 해당 시점·해당 resolved graph에서 조회된 결과입니다. 취약점이 영구적으로 없거나 애플리케이션 보안 감사가 끝났다는 뜻이 아닙니다. 60초의 반복 검사는 수용 인원·장시간 무중단·성능 SLA 측정이 아닙니다.

## 이번 재개에서 수정한 실패

`88b50ab`의 인프라 실행 `37427487654`는 실제 broker channel의 `Already immutable` 오류로 실패했습니다. `58ea66a`에서 서버 발행을 SIMP로 유지해 framework의 동결 경계를 올바르게 통과하도록 수정했습니다. [브로커 호환 처리](BROKER_COMPATIBILITY.md)에 근거와 실제 채널 경계 회귀 검사를 기록했습니다.

그 다음 실행 `37588676313`에서는 두 앱의 메시지·검색 6개 검사는 통과했으나 새로고침 후 통화 거절 화면 정리에서 실패했습니다. `0e0b8cb`는 활성 등록 통화에 한해 서버 현재 상태를 주기 대조하고, 오래된 응답·다른 탭 수락·종료 타이머를 안전하게 처리합니다. 기존 두 서버 통화 거절 검사를 약화하거나 건너뛰지 않았고, 최종 실행에서 통과했습니다. [통화 상태 대조](VOICE_STATE_RECONCILIATION.md)를 참조합니다.

## 완료로 표시하지 않은 것

물리 마이크/스피커 음질, 실제 OS 권한/화면 선택 UI, Push 제공자의 실제 알림 도착, 회사망·모바일망·TURN 중계, 사용자 PC의 Windows IntelliJ/Cloudflare 외부 접속, 장시간 부하/운영 HA는 이 검증에 포함하지 않습니다. 가상 마이크·카메라로 실제 RTCPeerConnection/RTP/영상 프레임을 검사하지만 물리 장치 검증은 아닙니다. 화면 공유 및 OS Notification 대역도 실제 OS UI와 구분합니다.

유료 관리형 TURN/SFU·검색/브로커·알림 SaaS·도메인·상시 호스팅·AI 호출은 이번 작업에서 제외했습니다. 자체 호스팅 선택 기능도 설정이 필요하며 사용자의 기존 계약·PC·전기·인터넷 비용까지 없어진다는 뜻은 아닙니다. 과거 리팩터링 결과는 [REFACTOR_VALIDATION.md](REFACTOR_VALIDATION.md)에 별도로 보존합니다.
