# DriveU: 학부생을 위한 생성형 AI 기반 클라우드 아카이빙 서비스

## 요약
학부생을 위한 생성형 AI 기반 클라우드 아카이빙 플랫폼. 학기·과목 단위 파일 관리와 AI 학습 도구(노트 요약, 문제 생성)를 제공합니다.

## 기간
2025.03 ~ 2025.12

## 기술 스택
Spring, Java, JPA, MySQL, AWS EC2, AWS S3, GPT API, Google OAuth2

## 팀 구성
BackEnd 1명(본인) / FrontEnd 1명 / 보안 & 인프라 1명

## GitHub
https://github.com/DriveU

## 역할 및 기여

### 대용량 파일 처리 시스템 설계
서버 부하를 최소화하기 위해 AWS S3 Presigned URL 기반 Multipart Upload 아키텍처를 설계하고 Spring Boot 환경에서 구현했습니다.
서버의 직접 I/O 부하를 줄이고, 대용량 파일 업로드 속도를 약 2배 향상시켰습니다.

### 계층적 디렉토리 관리 및 쿼리 성능 최적화
Closure Table 패턴으로 임의 깊이의 중첩 디렉토리 조회·삭제를 단일 쿼리로 처리했습니다.
풀 테이블 스캔 문제를 발견하고 5개 엔티티에 복합 인덱스 10개를 추가해 디렉토리 트리 조회 성능을 약 3배 개선했습니다.

### S3-DB 정합성 보장 및 복구 JOB 설계
Presigned URL 발급 시점에 업로드 상태를 DB에 선제 기록하고, 복구 Job이 주기적으로 미완료 건을 감지해 S3 정리와 DB 복구를 자동 수행하는 구조를 설계했습니다.
불완전한 멀티파트 업로드를 자동 정리하여 불필요한 S3 과금을 제거했습니다.

### 휴지통 리소스 자동 정리 배치 시스템 설계
휴지통에 30일 이상 보관된 파일·노트·링크·디렉토리를 자동 영구 삭제하는 Spring Batch Job을 설계·구현했습니다.
커서 버퍼 방식과 ExecutionContext 기반 재시작 체크포인트를 적용했고, Exponential Backoff·Circuit Breaker·Skip 처리를 조합해 외부 장애가 전체 Job을 중단시키지 않도록 내결함성을 확보했습니다.
Skip 발생 건은 별도 skip_log 테이블에 기록하여 사후 추적과 수동 복구가 가능하도록 했습니다.

### 보안 중심 AWS 인프라 설계 (VPC / ALB)
VPC 기반 계층형 네트워크 아키텍처를 설계·구현했습니다.
Public Subnet에는 ALB, Bastion Host, NAT Gateway만 배치하고, 애플리케이션 서버와 DB는 Private Subnet에 배치하여 외부 직접 접근을 차단했습니다.
AWS WAF, GuardDuty + EventBridge + Discord 알림을 연동해 보안 이벤트 실시간 모니터링 환경을 구축했습니다.

### 비동기 아키텍처 전환
외부 AI API 호출에 Spring WebFlux + WebClient 기반 Non-blocking 구조를 도입했습니다.
I/O 대기 시간이 서버 병목으로 이어지는 문제를 해결하여 응답 효율 개선 및 장애 지점을 감소시켰습니다.

## 성과
- 대용량 파일 업로드 속도 2배 향상 및 서버 부하 감소
- 외부 공격으로 인한 서버 다운 0건 달성
- DB 인덱스 최적화로 디렉토리 트리 조회 성능 약 3배 개선