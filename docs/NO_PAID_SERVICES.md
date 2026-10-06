# 유료 기능 제외 및 개발 실행 범위

2026-10-06 · 사용자 요청에 따라 이번 로드맵 작업은 **새 결제·구독·유료 인프라 생성 없이** 진행합니다. 기능 구현, 로컬/CI 실행, 실제 외부 서비스 운영은 구분합니다.

## 포함 / 제외

| 영역 | 이번 작업에 포함 | 이번 작업에서 제외 |
|---|---|---|
| 채팅·커뮤니티·첨부·세션 | 기존 개발 MongoDB에 저장, 브라우저와 Java 코드로 처리 | 유료 메시징 API, 유료 파일 저장소 신규 생성 |
| 음성·영상·화면 공유 | 브라우저 WebRTC, 최대 4명 mesh, 선택적인 직접 호스팅 coturn 설정 | 관리형 유료 TURN/SFU 가입·대역폭 구매, 큰 방을 위한 서버 임대 |
| Push | VAPID 키 로컬 생성 및 표준 Web Push 코드, 사용자 동의 후 구독 | 유료 알림 SaaS, 문자/SMS 인증 |
| 검색·분산 브로커 | 선택적인 직접 호스팅 Elasticsearch **Basic** / RabbitMQ | Elastic Cloud·유료 검색 기능·trial 시작, 관리형 브로커 결제 |
| 공개 URL | 기존 PC의 설치된 cloudflared Quick Tunnel | 도메인 구매, 유료 VPS/상시 호스팅 생성 |
| 검증·운영 도구 | 로컬 백업/새 DB 복구·OSV 검사, 공개 저장소의 표준 GitHub Actions runner | 유료 보안 스캐너·모니터링 SaaS·대형 유료 runner |
| 기존 AI 봇 | 호환 코드만 보존, **추가 비용 허용과 AI 활성화를 모두 꺼 둠** | OpenAI API 호출·유료 AI 기능 확장 |

검색/브로커/Push는 기본 비활성입니다. IntelliJ Run은 Docker 서비스나 클라우드 계정을 자동 생성하지 않습니다. 로컬에서 직접 호스팅하는 경우에도 PC·전기·인터넷·저장 공간은 필요합니다. 회사 PC의 Docker Desktop 등 기존 도구의 사용권 조건까지 이 저장소가 보장하거나 대신 계약하지 않습니다. 기존 Atlas/인터넷 등 사용자가 이미 계약한 서비스의 요금도 이 코드가 변경하지 않습니다.

## 유료 API 기본 차단

`APP_ALLOW_PAID_SERVICES=false`가 기본입니다. 이전 실행 설정에 `APP_OPENAI_ENABLED=true`나 API 키가 남아 있어도, 이 정책이 false면 **OpenAIClient를 생성하거나 응답 API를 호출하지 않습니다.** `/ai` 호환 명령 경로 자체는 삭제하지 않습니다. 이번 작업에서는 이 허용값을 true로 설정하지 않으며 유료 기능 테스트도 하지 않습니다. 이는 애플리케이션의 알려진 유료 AI 연동 차단이지, 임의로 변경한 원격 인프라의 청구까지 탐지하는 시스템은 아닙니다.

Elasticsearch는 Compose와 CI 모두 `xpack.license.self_generated.type: basic`을 명시합니다. CI는 실제 `/_license` 응답도 Basic인지 검사하며 trial/상용 기능을 전제로 테스트하지 않습니다.

## 이번 후속 수정

백업 도구는 Mongo URI의 기본 DB와 `--source`가 다를 때 생기는 충돌을 제거하고, 명시적인 `authSource` 또는 원래 기본 인증 DB를 보존합니다. URI/비밀번호는 계속 권한 제한 임시 파일에만 넘기고 명령행에 출력하지 않습니다. 복구는 다른 이름의 새 DB로만 가능하며 기존 컬렉션이 있는 대상은 거부합니다.

배포 컨테이너 검사는 친구 요청 직후 친구가 아님을 확인하고 상대의 수락 후 양쪽 친구 목록과 메시지 저장을 검증합니다. 새 승인 정책을 테스트를 위해 되돌리지 않았습니다.

앞선 `1b49240` 실행의 OSV 보고서에서 발견된 Jackson/Tomcat 항목을 위해 공개 패치 버전 Jackson 2.21.7 / 3.1.7, Tomcat 11.0.25를 적용했습니다. 기존 JSON 호환 방식을 유지하며 전체 테스트와 정확히 resolve된 의존성 스캔으로 확인합니다. 검사 실패나 발견 항목은 무시하지 않습니다. 성공 수치는 [ROADMAP_VALIDATION.md](ROADMAP_VALIDATION.md)의 해당 실행 결과만 근거로 합니다.

## 별도 실제 환경 검증

실제 기기의 마이크/스피커, OS 알림 도착, 화면 선택 창, 회사망·모바일망·TURN, Windows IntelliJ의 실제 Cloudflare 연결, 장시간 고가용성 검증은 자동 테스트와 다릅니다. 유료 서버가 필요하면 구매하지 않고 보류합니다. 무료로 가능한 실기기 확인도 실행하지 않았다면 완료로 표시하지 않습니다.

근거 문서: [Elastic Basic 설정](https://www.elastic.co/docs/reference/elasticsearch/configuration-reference/license-settings), [GitHub Actions 과금 범위](https://docs.github.com/en/billing/concepts/product-billing/github-actions), [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/), [Jackson 보안 공지](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24), [Tomcat 보안 공지](https://tomcat.apache.org/security-11.html), [Spring Boot 버전 속성](https://docs.spring.io/spring-boot/appendix/dependency-versions/properties.html).
