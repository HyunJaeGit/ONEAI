# ONE AI 수집 MVP 설계

상태: Phase 2 설계 제안. 승인된 Phase 1 분석을 입력으로 작성했으며, 이 문서의 구체적인 계약·구조는 구현 완료 또는 기술 선택 승인을 뜻하지 않는다.

## 1. 기준과 범위

- 기존 합의: [AGENTS.md](AGENTS.md)를 따른다. 로컬에서 검색·결과 노출·클릭·문서 조회·유용성 평가를 수집한다. 수집 장애에도 기존 검색·문서 열람을 유지한다.
- 제외: 인증·비밀번호·사용자 무결성 검증, AI 추천·학습, 대시보드, 고객용 기능, 클라우드 배포.
- 확인한 사실: 현재 HTML·CSS·JS 정적 사이트이며 수집 API·DB·서버·패키지 실행 설정은 없다. `assets/js/common.js`의 `searchPages`는 `path`, `title`, `text`를 가진 수동 관리 색인이다. 검색은 모든 검색 단어의 포함 여부로 필터링하고 색인 순서로 표시한다.
- 확인한 사실: `data-group`·`data-page`는 메뉴 상태용이며 빈 값이 있어 전체 문서의 고유 식별자로 사용할 수 없다. 검색 결과 일반 클릭 시 `sessionStorage`로 검색어를 전달해 대상 문서에서 강조한다.
- 기존 자료 활용: [README.md](README.md)는 실행·현재 상태 안내, [additional_info.md](additional_info.md)는 업무정보 출처, [기존 화면 검토](docs/exception-guidance-review.md)와 [과거 검토 기록](docs/phase-4-review.md)은 회귀 검증 근거로 유지한다. 과거 Phase 4는 ONE AI 수집 MVP의 완료 단계를 뜻하지 않는다.
- README의 추천 관련 설명보다 최신 AGENTS.md의 수집 전용 범위를 우선한다. README 수정은 이번 작업에 포함하지 않는다.
- 아래 API·논리 DB·파일 배치는 제안이며, 미확정 목록은 10절에 모은다. 테스트 값과 예시는 모두 합성이다.

## 2. 전체 흐름과 저장 역할

1. 임의 USERID 라벨·세션과 문서 카탈로그를 준비한다.
2. 유효한 검색 제출 시 검색 사건을 만들고 기존 로컬 검색을 즉시 수행한다.
3. 결과 DOM 표시 후 순서 있는 결과 목록을 노출 사건으로 만든다. 결과 0건도 기록한다.
4. 결과 링크 활성화 시 클릭 사건을 만들고 기존 이동을 실행한다.
5. 대상 문서의 본문 초기화 후 조회 사건을 만든다. 직접 진입도 별도로 기록한다.
6. 사용자가 평가를 선택하면 조회에 연결된 평가 사건을 만든다.
7. 수집 전용 코드가 로컬 API에 비동기 전송한다. 서버가 검증 후 DB 트랜잭션을 커밋한 경우에만 저장 성공을 응답한다.

**JSON은 HTTP API 요청·응답의 직렬화 형식이다. JSON 파일을 이벤트 영구 저장소로 사용하지 않는다.** 영구 저장소는 관계형 DB를 제안하며 엔진은 미확정이다. 요청 본문을 파일로 쌓거나 JSON 파일과 DB에 이중 저장하지 않는다. DB 안에 원문 JSON을 통째로 저장하는 방식도 기본 설계에 포함하지 않는다.

프런트의 제한된 메모리 재전송 큐는 일시 전달 수단이다. 브라우저 종료·이동 시 유실될 수 있으며 영구 저장이나 전송 보장을 의미하지 않는다. 디스크 큐·JSON 내보내기·브라우저 영구 큐는 별도 결정 전 구현하지 않는다.

## 3. 식별자와 연결 규칙

| API 필드 | 제안 규칙 |
|---|---|
| `userId` | 업무상 USERID에 대응하는 분석 라벨. 예: `demo-agent-01`. 실제 회사 ID·이름·이메일 금지. 인증·소유권 증명으로 사용하지 않음 |
| `sessionId` | 클라이언트가 만든 무작위 UUID. 여러 검색·조회를 묶음. 생성·만료·새 탭 공유 정책은 미확정 |
| `eventId` | 사건마다 새 UUID. 같은 사건 재전송에는 같은 ID·같은 내용을 유지 |
| `searchId` | 유효한 검색 제출마다 새 UUID. 같은 문구의 재검색도 새 ID. 제안상 검색 사건의 `eventId`와 같게 사용 |
| `documentId` | 경로와 독립적인 카탈로그 키. 합성 예: `demo-guide-001`. 제목·배열 순번으로 생성하지 않음 |
| `viewId` | 문서 진입마다 새 UUID. 조회 사건의 `eventId`와 같게 사용. 새로고침도 새 조회 |
| `impressionId` | 결과 노출 사건의 `eventId`. 클릭이 어느 결과 목록에서 발생했는지 연결 |
| `clickId` | 클릭 사건의 `eventId`. 조회와 클릭을 연결할 수 있을 때 사용 |

- 세션은 최초 정상 이벤트 수신 시 생성하는 방식을 제안한다. 같은 `sessionId`에서 다른 USERID를 보내면 충돌로 거부한다. 라벨을 바꿀 때는 새 세션을 만든다.
- USERID의 형식·일관성 검사만 수행하며 실제 사용자 여부나 라벨 진위를 검증하지 않는다. 사용자 계정 테이블은 만들지 않는다.
- 문서 카탈로그는 하나의 로컬 시험 데이터 범위만 가진다. 사업별 데이터 교차 사용은 금지한다. 다사업 지원용 구조 확장은 현재 범위에 포함하지 않는다.
- 문서 경로 변경에도 `documentId`는 유지한다. `/`, `/index.html`, 디렉터리 URL 등의 별칭은 카탈로그 규칙으로 정규화한다. 루트 상대 경로만 허용하고 호스트·로컬 절대경로·쿼리 문자열을 식별자로 저장하지 않는다.
- `documentVersion`은 카탈로그에서 관리하는 버전 문자열 후보다. 버전 생성·수정 정책 및 홈·목록 페이지의 수집 포함 여부는 미확정이다.
- 검색 사건 → 노출 사건 → 클릭 사건 → 조회 사건 → 평가 사건을 각각 FK로 연결한다. 동일 세션이라는 이유만으로 마지막 검색에 조회를 추측 연결하지 않는다.
- 검색 유입은 `searchId`, `impressionId`, `clickId`를 명시적으로 전달했을 때만 연결한다. 전달 실패·직접 진입은 검색 연결 없이 `unknown` 또는 `direct`로 기록한다.
- 현재 검색어 강조용 세션 저장소와 수집 연결 정보는 별개로 취급한다. 새 탭 전달 방식·유효 기간·소비 규칙은 미확정이다. 검색어·USERID를 URL에 추가하지 않는다.

## 4. JSON 이벤트 및 API 계약 제안

### 공통 계약

- 경로: `POST /api/v1/events`. 단건 사건만 받는다. 검색 결과를 서버에서 계산하는 API가 아니다.
- `Content-Type: application/json; charset=utf-8`. 응답도 JSON이다. 로그인·인증 헤더는 사용하지 않는다.
- 공통 필수값: `schemaVersion`(현재 제안값 `1`), `eventId`, `eventType`, `occurredAt`, `userId`, `sessionId`, `payload`.
- UUID는 문자열, `occurredAt`은 UTC RFC 3339 문자열, 순위·건수는 정수다. 서버는 `receivedAt`을 별도로 기록한다. 클라이언트 시각으로 전달 순서를 판단하지 않는다.
- 알 수 없는 필드·사건 종류·스키마 버전은 거부한다. 허용 데이터와 길이만 저장하고 원문 요청 로깅은 하지 않는다.
- 아래 크기·길이는 제안 상한이며 구현 전 확정한다: 요청 64 KiB, 라벨 1~64자 `[A-Za-z0-9_-]`, 문서 ID 1~64자, 버전 1~64자, 경로 512자, 결과 항목 100개. 현재 검색 입력 길이는 200자다.

### 사건별 payload

| `eventType` | 필수 payload | 발생 의미·제약 |
|---|---|---|
| `search_submitted` | `searchId`, `sourceDocumentId` | 검색 ID는 eventId와 동일. source는 등록 문서 또는 명시적 null. 공백 검색은 기본적으로 수집하지 않는 제안 |
| `results_exposed` | `searchId`, `totalCount`, `items` | 결과 DOM 표시 완료. 실제 시선·읽음·화면 가시성 보장 아님 |
| `document_clicked` | `searchId`, `impressionId`, `documentId`, `documentVersion`, `rank` | 검색 결과 링크 활성화. 순위는 1부터 시작하며 해당 노출 목록의 문서·버전과 일치해야 함 |
| `document_viewed` | `viewId`, `documentId`, `documentVersion`, `source`, `searchId`, `impressionId`, `clickId` | 본문 초기화 완료. `viewId=eventId`. 체류·해결 성공 아님 |
| `usefulness_rated` | `viewId`, `value` | 명시적 선택에만 생성. 평가값과 변경·재평가 정책 미확정 |

`results_exposed.items`의 각 항목은 `documentId`, `documentVersion`, `rank`를 가진다. 순위·문서는 목록 내 중복 금지. 현재 검색에는 페이지 나누기가 없으므로 `totalCount=items.length`, 순위는 1부터 연속이다. 0건은 `items: []`. 향후 페이지 나누기 도입 시 스키마를 재검토한다.

`document_viewed.source` 제안값은 `search`, `navigation`, `direct`, `unknown`이다. `search`이면 검색·노출·클릭 연결값이 모두 필수이고 실제 클릭의 문서·버전과 일치해야 한다. 다른 source이면 세 연결값은 null이다. 출처 판별 실패를 direct로 단정하지 않는다.

검색어 정책은 **미확정**이다. 따라서 기본 계약 예시에는 `query`를 넣지 않는다. 실제 검색어 저장은 금지한 상태로 두고, 이후 승인된 정책에서 필드 허용 여부·마스킹 시점·보존 기간을 확정한다. 해시도 익명화 보장이 아니므로 임의로 추가하지 않는다. 검색어 없는 메타데이터 수집만으로 요구사항이 충족되는지도 확정이 필요하다.

합성 검색 요청 예시:

```json
{
  "schemaVersion": 1,
  "eventId": "00000000-0000-4000-8000-000000000001",
  "eventType": "search_submitted",
  "occurredAt": "2026-10-26T00:00:00Z",
  "userId": "demo-agent-01",
  "sessionId": "00000000-0000-4000-8000-000000000010",
  "payload": {
    "searchId": "00000000-0000-4000-8000-000000000001",
    "sourceDocumentId": "demo-home"
  }
}
```

다음 payload 예시는 각각 위 공통 필드로 감싸 전송한다. 노출의 eventId는 `00000000-0000-4000-8000-000000000002`, 클릭은 끝자리 `003`, 조회는 `004`, 평가는 `005`를 사용한 합성 흐름이다.

```json
{
  "searchId": "00000000-0000-4000-8000-000000000001",
  "totalCount": 1,
  "items": [{"documentId": "demo-guide-001", "documentVersion": "demo-v1", "rank": 1}]
}
```

```json
{
  "searchId": "00000000-0000-4000-8000-000000000001",
  "impressionId": "00000000-0000-4000-8000-000000000002",
  "documentId": "demo-guide-001",
  "documentVersion": "demo-v1",
  "rank": 1
}
```

```json
{
  "viewId": "00000000-0000-4000-8000-000000000004",
  "documentId": "demo-guide-001",
  "documentVersion": "demo-v1",
  "source": "search",
  "searchId": "00000000-0000-4000-8000-000000000001",
  "impressionId": "00000000-0000-4000-8000-000000000002",
  "clickId": "00000000-0000-4000-8000-000000000003"
}
```

평가 payload는 `viewId`와 `value`를 가진다. `value`의 열거값은 **미확정**이므로 실행 가능한 평가 요청 예시는 제공하지 않는다. 미확정이라는 문자열을 실제 평가값으로 저장해서는 안 된다. 계약 확정 전 평가 기능을 활성화하지 않는다.

### 응답과 오류

최초 저장 성공은 `201 Created`, 동일 사건 재전송은 `200 OK`:

```json
{
  "eventId": "00000000-0000-4000-8000-000000000001",
  "status": "stored",
  "receivedAt": "2026-10-26T00:00:01Z"
}
```

중복 성공은 `status: "duplicate"`이고 최초 수신 시각을 반환한다. DB 커밋 전에 성공을 응답하지 않는다.

```json
{
  "eventId": "00000000-0000-4000-8000-000000000003",
  "error": {
    "code": "DEPENDENCY_NOT_READY",
    "retryable": true,
    "message": "선행 이벤트를 먼저 전송해야 합니다."
  }
}
```

| HTTP | 오류 코드 | 처리 |
|---|---|---|
| 400 | `INVALID_JSON`, `VALIDATION_FAILED`, `UNSUPPORTED_SCHEMA` | 수정 전 재시도하지 않음 |
| 409 | `EVENT_ID_CONFLICT`, `SESSION_LABEL_CONFLICT`, `RELATION_MISMATCH` | ID 재사용·관계 오류. 자동 재시도하지 않음 |
| 409 | `DEPENDENCY_NOT_READY` | 아직 없는 선행 사건. 선행 전송 후 제한 재시도 |
| 413 / 415 | `PAYLOAD_TOO_LARGE` / `UNSUPPORTED_MEDIA_TYPE` | 자동 재시도하지 않음 |
| 503 | `STORAGE_UNAVAILABLE` | 롤백 후 제한 재시도 가능 |
| 500 | `INTERNAL_ERROR` | 세부 정보 미노출. 동일 eventId로 제한 재시도 가능 |

eventId를 파싱할 수 없으면 오류 응답의 eventId는 null이다. 메시지에 검색어·원문 payload·SQL·서버 경로를 반사하지 않는다. `receivedAt`은 커밋 성공 응답에만 제공한다.

## 5. DB 논리 설계 제안

엔진·실제 DDL·마이그레이션 도구는 **미확정**이다. 아래는 이식 가능한 논리 자료형이다. UUID는 논리 UUID(물리적으로 UUID/CHAR(36) 등 선택), 시각은 UTC timestamp, INT는 정수, VARCHAR(n)은 길이 제한 문자열이다. `?`는 NULL 허용이며 나머지는 NOT NULL이다.

| 테이블 | 컬럼·자료형 | 키·제약 |
|---|---|---|
| `collection_sessions` | `session_id UUID`, `user_id VARCHAR(64)`, `created_at TIMESTAMP` | PK session_id. 사용자 계정 FK 없음 |
| `documents` | `document_id VARCHAR(64)` | PK document_id. 허용 문서 카탈로그 |
| `document_versions` | `document_id VARCHAR(64)`, `version VARCHAR(64)`, `path VARCHAR(512)` | 복합 PK(document_id, version), FK document_id → documents |
| `events` | `event_id UUID`, `session_id UUID`, `event_type VARCHAR(32)`, `schema_version INT`, `occurred_at TIMESTAMP`, `received_at TIMESTAMP`, `payload_digest CHAR(64)` | PK event_id, FK session_id → collection_sessions. digest는 정규화한 허용 요청의 SHA-256, 검색어 해시 아님 |
| `searches` | `search_id UUID`, `source_document_id VARCHAR(64)?` | PK/FK search_id → events.event_id, FK source_document_id → documents |
| `result_exposures` | `impression_id UUID`, `search_id UUID`, `total_count INT` | PK/FK impression_id → events, FK search_id → searches, total_count ≥ 0 |
| `result_items` | `impression_id UUID`, `rank INT`, `document_id VARCHAR(64)`, `document_version VARCHAR(64)` | 복합 PK(impression_id, rank), FK impression_id → result_exposures, 복합 FK(document_id, document_version) → document_versions, UNIQUE(impression_id, document_id), rank ≥ 1 |
| `document_clicks` | `click_id UUID`, `impression_id UUID`, `rank INT` | PK/FK click_id → events, 복합 FK(impression_id, rank) → result_items |
| `document_views` | `view_id UUID`, `document_id VARCHAR(64)`, `document_version VARCHAR(64)`, `source VARCHAR(16)`, `click_id UUID?` | PK/FK view_id → events, 복합 FK(document_id, document_version) → document_versions, FK click_id → document_clicks |
| `usefulness_ratings` | `rating_id UUID`, `view_id UUID`, `value VARCHAR(32)` | PK/FK rating_id → events, FK view_id → document_views. value 허용값·재평가 제약은 미확정 |

API의 `searchId`·`documentId` 등 중복되는 연결값은 서버에서 대조하고, 클릭 테이블에서는 노출 항목으로부터, 조회 테이블에서는 클릭으로부터 검색 연결을 도출한다. 같은 검색 연결 정보를 DB에 중복 저장하지 않는다.

- 검색어 컬럼은 현재 설계에 넣지 않는다. 저장 정책 승인 시 별도 변경한다. 문서 본문·제목·전체 URL·상담 내용·IP·User-Agent는 수집 테이블에 저장하지 않는다.
- 결과 목록은 정적 색인 순서의 기록이며 추천 점수·성공 확률 컬럼은 없다.
- subtype의 ID가 가리키는 events.event_type 일치, 서로 연결된 사건의 session_id 일치, 조회 문서와 클릭 문서 일치는 서비스 트랜잭션에서 검사한다. 단순 FK만으로 보장된다고 간주하지 않는다.
- document_views는 search source일 때 click_id 필수, 다른 source일 때 null. API의 searchId·impressionId도 클릭의 연결 체인과 대조한다.
- 문서 버전·노출 목록은 과거 사건 해석을 위해 덮어쓰지 않는다. 문서 개정 시 버전을 추가한다. 삭제·보존 기간은 정책 확정 전 임의로 실행하지 않는다.
- FK 삭제 동작은 RESTRICT를 제안한다. 보존기간 만료 삭제는 별도 승인된 순서·범위로 처리한다.

인덱스 제안(PK·UNIQUE가 만드는 인덱스는 중복 생성하지 않음):

| 테이블 | 보조 인덱스 | 목적 |
|---|---|---|
| collection_sessions | (user_id, created_at) | 합성 라벨별 세션 확인 |
| events | (session_id, received_at), (event_type, received_at) | 세션 흐름·사건별 수신 확인 |
| searches | (source_document_id) | 출발 문서 참조 |
| result_exposures | (search_id) | 검색별 결과 표시 |
| result_items | (document_id, document_version) | 문서별 노출·버전 참조 |
| document_views | (document_id, document_version), (click_id) | 문서별 조회·클릭 연결 |
| usefulness_ratings | (view_id) | 조회별 평가 |

관계는 세션 1:N 사건, 검색 1:N 노출, 노출 1:N 결과 항목, 결과 항목 1:N 클릭, 클릭 1:N 조회 후보, 조회 1:N 평가 후보다. 조회·평가의 업무상 개수 제한은 미확정이며 임의 UNIQUE 제약을 추가하지 않는다.

## 6. 중복·순서·트랜잭션

1. 요청을 검증하고 허용 필드·정규화 규칙에 따라 digest를 계산한다. JSON 키 순서·공백만 다른 요청은 동일하게 취급한다. 배열 순서는 보존한다.
2. 트랜잭션에서 세션을 생성하거나 기존 라벨 일치를 검사한다. 문서 카탈로그·선행 사건·사건 종류·동일 세션 관계를 확인한다.
3. events와 해당 subtype을 함께 INSERT한다. 노출은 모든 result_items도 같은 트랜잭션에 넣는다. 하나라도 실패하면 전부 롤백한다.
4. 커밋 후 stored를 반환한다. 일부 결과 항목만 저장되는 부분 성공은 허용하지 않는다.
5. event_id 중복이면 기존의 세션·사건 종류·정규화 요청 digest를 비교한다. 동일하면 duplicate, 다르면 EVENT_ID_CONFLICT다. 기존 사건은 갱신하지 않는다.

동시 요청은 DB PK·UNIQUE를 최종 중복 방어선으로 사용한다. 유일성 오류로 트랜잭션이 무효화된 경우 롤백한 뒤 별도 읽기에서 기존 사건을 비교한다. 응답 유실 후 재시도도 같은 규칙을 따른다.

선행 사건이 없으면 고아 행을 저장하지 않고 DEPENDENCY_NOT_READY를 반환한다. 프런트는 같은 검색 체인의 전송 순서를 유지하되 화면 동작을 기다리게 하지 않는다. 새 탭 등으로 순서가 바뀌면 선행 사건 확인 후 재시도한다. 관계가 이미 존재하지만 세션·문서가 다르면 재시도 가능한 순서 문제로 취급하지 않는다.

새 eventId로 들어온 실제 재검색·재클릭은 별도 사건이다. 본문 유사도로 사건을 합치지 않는다. 평가값 변경은 기존 사건 재전송과 구분해야 하며 정책 확정 후 계약을 보완한다.

## 7. 프런트·백엔드 구조와 패키지 제안

기존 정적 구조를 유지하고 프런트 프레임워크·번들러·패키지 관리자를 필수로 도입하지 않는다. 아래는 향후 배치 예시이며 이번에 디렉터리·코드를 생성하지 않는다.

```text
index.html, pages/, assets/css/     기존 화면 유지
assets/js/common.js                기존 검색·문서 UI, 필요한 최소 연동만
assets/js/collection.js             사건 생성·연결·비동기 전송·실패 격리 제안
server/                            백엔드 추가 위치 제안
  <source>/collection/api/          요청·응답 DTO, HTTP 오류 매핑
  <source>/collection/service/      관계 검증·중복·트랜잭션
  <source>/collection/persistence/  DB 접근·엔티티
  <source>/collection/config/       로컬 바인딩·허용 출처·외부 설정 연결
  <resources>/db/                   엔진 결정 후 스키마 변경 관리
  <tests>/                         API·DB 통합 검증
```

패키지는 위 네 책임부터 시작하고 인터페이스·계층을 필요 이상 추가하지 않는다. Spring Boot를 선택할 경우 이 책임을 Java 패키지로 매핑할 수 있으나 Spring Boot·Java·Maven/Gradle 채택은 미확정이다. DB 드라이버·검증·테스트 의존성도 엔진·서버 선택 후 최소한으로 정한다.

로컬 HTTP로 정적 화면과 API를 동일 출처에서 제공하는 방식을 우선 제안한다. 개발 서버를 나누면 지정 로컬 origin만 CORS 허용하며 와일드카드 허용은 피한다. 포트·실행 명령은 기술 결정 후 README에 작성한다. 기존 파일 직접 열기는 기존 검색용으로 유지하되 수집 동작 보장은 별도로 검증한다. 서버는 loopback 바인딩을 기본 제안하며 외부 공개는 범위 밖이다.

## 8. 입력 검증·개인정보 최소화·실패 처리

- 서버 검증: Content-Type·크기·형식·필드 허용 목록·길이·정수 범위·UUID·시각·enum·문서 버전·FK 관계. SQL은 바인딩 파라미터를 사용한다. 클라이언트 검증만 신뢰하지 않는다.
- 시각의 허용 오차 범위는 미확정이다. 서버 수신 시각을 기준으로 저장 순서를 확인하며 비정상 클라이언트 시각을 임의 보정하지 않는다.
- 평가값은 확정된 허용 목록만 수용한다. 자유서술 평가·상담 메모는 수집하지 않는다. 평가 enum 미정 상태에서 임의 숫자 척도를 채택하지 않는다.
- 검색어 정책 미정 시 실제 검색어·실제 자료를 테스트에 쓰지 않는다. 정책 승인 전 원문 저장 필드를 열지 않는다. 공개·합성 시험 문서도 사용 범위를 확인한다.
- 서버 접근 로그·오류 로그에도 요청 본문·USERID·IP·검색어가 불필요하게 남지 않게 설정을 검토한다. 진단에는 eventId·오류 코드·시각만 최소 사용한다. IP는 접속 과정에서 처리될 수 있으나 영구 분석 데이터로 저장하지 않는다.
- 현재 `.gitignore`는 `/reference/`만 제외한다. 구현 전 로컬 DB·덤프·로그·환경 비밀설정·수집 결과의 실제 경로를 확정하고 제외 규칙을 추가해야 한다. 이번에는 설정을 수정하지 않는다. DB 연결 비밀값은 코드·문서·커밋에 넣지 않는다.
- 수집 코드 초기화·통신·저장소 오류를 기존 검색 처리로 전파하지 않는다. 클릭 이동을 막거나 API 응답을 기다려 페이지 이동을 지연하지 않는다.
- 제한 재시도 제안: 네트워크 오류·5xx·선행 사건 지연만 같은 eventId로 지수 백오프한다. 최대 횟수·타임아웃·큐 크기·보존 시간은 미확정이다. 초과 시 유실 가능성을 명시하고 무제한 큐를 만들지 않는다.
- DB 장애는 전체 롤백·503. 재전송으로 복구하되 JSON 파일로 우회 저장하지 않는다. 큐 소실 사건을 나중에 추측 복원하지 않는다.
- 사용자에게 저장 성공을 표시한다면 서버 커밋 응답 후에만 표시한다. 수집 장애가 기존 검색 오류처럼 보이지 않도록 별도 상태로 취급한다. 상태 UI 상세는 미확정이다.

## 9. 구현 단계의 검증 기준

아래는 계획이며 이번에 실행한 테스트가 아니다.

| 시나리오 | 기대 결과 |
|---|---|
| 합성 검색 → 노출 → 클릭 → 조회 → 평가 | 확정 계약에 따라 각 사건과 FK 연결 확인. 평가 정책 확정 후 평가 검사 |
| 0건·공백 검색·같은 문구 재검색 | 0건 노출 저장, 공백 정책 일치, 재검색은 새 ID |
| 동일 사건 재전송·동시 수신·응답 유실 | 한 행만 저장, duplicate 응답, ID 충돌은 409 |
| 결과 항목 일부 실패·DB 중단 | 해당 사건 전체 롤백, 성공 응답 없음, 검색은 정상 |
| 순서 역전·다른 세션·다른 문서 연결 | 미도착 선행 사건만 제한 재시도, 잘못된 관계는 거부 |
| 직접 진입·새 탭·키보드·새로고침·뒤로가기 | 출처를 추측하지 않음, 정의된 새 조회·연결 규칙 준수 |
| 잘못된 JSON·과대 요청·알 수 없는 필드·민감정보 필드 | 저장 없이 규격 오류, 로그·응답에 원문 미노출 |
| API 응답 지연·미실행·수집 코드 오류 | 기존 검색·클릭·목차·앵커·강조·접기·인쇄 유지 |
| Git 제외·로그·DB 데이터 점검 | 합성 데이터만 사용, 비밀정보·DB·수집 로그가 추적 대상에 없음 |

## 10. 구현 전 확정할 항목

| 항목 | 상태·필요 결정 |
|---|---|
| DB 엔진·백엔드·빌드 도구 | **미확정**. 엔진별 자료형·트랜잭션·마이그레이션·실행 명령 확정 |
| 평가값·재평가 | **미확정**. 허용 enum, 변경·반복·취소 정책과 DB 제약 확정 |
| 검색어 정책 | **미확정**. 원문 저장 여부·마스킹·보존·삭제·로그 정책, 메타데이터만 수집할 때의 요구사항 충족 여부 |
| USERID·세션 | **미확정**. 입력·보관 방식, 세션 만료·새 탭·라벨 변경 UX |
| 문서 카탈로그 | **미확정**. ID 부여·경로 별칭·버전 생성 방식, 목록·홈 포함 여부, 합성 데이터 범위 |
| 조회·노출·전달 방식 | **미확정**. 초기화 기준, DOM 노출 정의 승인, BFCache 처리, 새 탭 연결·유효 기간 |
| 전송 한계 | **미확정**. 요청 상한·재시도·타임아웃·큐 제한·허용 시각 오차·유실 허용 기준 |
| 보존·접근·삭제 | **미확정**. DB·진단 로그 보존 기간, 로컬 접근 범위·삭제 절차 |
| 실행 환경 | **미확정**. 동일 출처 방식·포트·지원 브라우저, file:// 수집 지원 범위 |

미확정 항목을 임의 기본값으로 실제 데이터에 적용하지 않는다. 다음 구현은 정책·계약 확정 → 합성 데이터 API·DB 검증 → 기존 화면의 사건 수집 연결 → 평가 연결 → 장애·회귀 검증 순서를 제안한다.
