# ONE AI 수집 MVP 설계

2026-10-26 현재 저장소 구현과 대조한 기준 문서다. API·DB·세션의 **현재 동작**을 기록하고 미구현·미확정 항목은 9절에 분리한다. 실행·프로젝트 구조 안내는 [README.md](README.md), 작업·데이터 원칙은 [AGENTS.md](AGENTS.md)를 따른다.

## 1. 기존 문서와 실제 구현의 차이·정리 목록

| 기존 내용 | 확인한 현재 상태·이번 정리 |
|---|---|
| 정적 사이트만 존재하고 API·DB는 없음 | 프런트 수집, Spring Boot API, SQLite 저장·조회가 구현되어 있어 현재형으로 수정 |
| Java·DB 엔진·빌드 도구·드라이버가 미확정 | Java 21, Spring Boot 3.5.16, Maven, Spring JDBC, SQLite JDBC 3.53.4.0으로 확정된 구현 반영 |
| collection.js·persistence 패키지·별도 DTO 제안 | 실제 수집은 common.js, JDBC 접근은 service, JSON 검증은 JsonNode 기반. 존재하지 않는 구조 제안 제거 |
| DB 위치·Git 제외는 향후 제안 | application.properties의 oneai.db와 LocalConfig의 루트 탐색·경로 해석, 실제 .gitignore 반영 |
| 평가 사건·테이블·인덱스가 현재 계약처럼 포함 | 수신 사건 4종·실제 테이블 9개로 수정. 평가 전체는 미확정·미구현으로 분리 |
| 예시의 demo-home·demo-guide-001·demo-v1 | 실제 카탈로그의 manual-home·contact·local-v1으로 예시 교체 |
| USERID 문자·접두어 제한 | 접두어 없음. 한글·공백·기호 허용, 빈 값·제어문자·길이 제한과 프런트 공백 제거 반영 |
| 연결 실패 조회를 direct로 표시할 수 있음 | 현재 프런트는 연결 성공 시 search, 그 외 unknown만 생성. API가 허용하는 다른 source와 구분 |
| 지수 백오프·선행 전송 후 재시도 제안 | 현재는 순차 메모리 큐·타임아웃만 있고 자동 재시도 없음. retryable은 서버 응답 힌트로 구분 |
| 새 탭 독립 세션·복제 방지가 완료된 것처럼 읽힘 | 현재 복원 조건만 명시하고 새 탭·BFCache의 전체 검증은 후속 항목으로 분리 |
| FK 삭제 RESTRICT·모든 길이 CHECK 제안 | schema.sql의 기본 NO ACTION 및 실제 CHECK만 명시. 서버 검증과 DB 제약을 구분 |
| DB·브라우저 검증 모두 향후 계획 | 기존 테스트 보고서와 과거 브라우저 검증 기록을 구분. 이번 문서 작업은 실행 검증이 아님 |
| 초기 제안과 후속 구현 설명이 중복 | 현재 구조·계약·한계 중심으로 통합하고 작성 당시의 작업 메모·미생성 안내 제거 |

이번 정리는 문서만 변경한다. 기존 사용자 작업, 코드·설정·DB·카탈로그는 수정하거나 삭제하지 않는다.

## 2. 범위와 현재 전체 흐름

- 기존 검색·정렬·결과 화면·문서 이동을 유지하고 로컬에서 검색·결과 노출·클릭·조회 사건을 수집한다. 분석용 로그인은 수집 라벨 선택이며 비밀번호·계정 인증·사용자 진위 검증이 아니다.
- 초기 실험은 임의 ID와 공개·합성 자료만 사용한다. 기존 업무 매뉴얼이 저장소에 있다는 사실은 실험 사용 승인이나 공개 자료임을 의미하지 않는다. 실제 회사 ID·내부 자료·상담 기록·개인정보는 승인 전 사용하지 않는다.
- 유용성 평가는 미구현이다. AI/ML·추천 정렬·자동 재학습·통계 대시보드·고객용 기능·클라우드 배포는 현재 범위 밖이다. 개발자 이벤트 조회는 로컬 읽기 기능이다.

흐름: `라벨 입력 → 탭 세션 시작 → 검색 → 결과 DOM 표시 → 클릭 → 대상 문서 조회 → 비동기 API 전송 → SQLite 커밋 → 개발자 화면에서 조회`.

common.js의 searchPages는 path·title·text를 가진 수동 정적 색인이다. 검색어를 NFKC 정규화·소문자화·공백 정리한 뒤 모든 단어가 제목·본문에 포함된 항목을 색인 순서대로 표시한다. 수집은 검색을 서버로 이전하거나 결과 순서를 변경하지 않는다. 노출은 목록 DOM 표시를 뜻하며 실제 읽음·화면 가시성·문제 해결 성공을 뜻하지 않는다.

**JSON은 HTTP 전송 형식이고 영구 저장소는 SQLite다.** 요청 원문 JSON을 파일이나 DB에 통째로 이중 저장하지 않는다. 브라우저 sessionStorage는 세션·이동 연결·검색어 강조를 위한 일시 상태이며 사건 영구 저장소가 아니다.

현재 구성의 책임:

| 구현 파일 | 역할 |
|---|---|
| [common.js](assets/js/common.js) | 기존 검색·화면, 라벨 UI, 세션·이동 연결, 사건 생성·전송 |
| [LocalController.java](server/src/main/java/local/oneai/api/LocalController.java) | POST 수신, JSON 파싱·크기 검사·오류 매핑, 정적 홈 제공 |
| [EventService.java](server/src/main/java/local/oneai/service/EventService.java) | 입력·관계 검증, 중복·트랜잭션·저장, 스키마·카탈로그 초기 적용 |
| [EventQueryService.java](server/src/main/java/local/oneai/service/EventQueryService.java) | 기존 DB에서 읽기용 사건 JSON·연결값 복원 |
| [DeveloperController.java](server/src/main/java/local/oneai/api/DeveloperController.java) | loopback 전용 조회·선택 삭제 API·개발자 화면 |
| [LocalConfig.java](server/src/main/java/local/oneai/config/LocalConfig.java) | 저장소 탐색·DB 경로·SQLite 연결·정적 리소스 |
| [schema.sql](server/src/main/resources/db/schema.sql), [documents.tsv](server/src/main/resources/db/documents.tsv) | 실제 스키마·문서 카탈로그 |

## 3. 라벨·세션·문서 식별

| 필드 | 현재 규칙 |
|---|---|
| userId | 분석용 라벨. 접두어 없이 한글·영문·공백·기호 등 허용. 빈 값·공백만 있는 값·제어문자·64 UTF-16 코드 단위 초과 거부 |
| sessionId | 로그인마다 클라이언트에서 새 UUID 생성. 같은 세션의 다른 userId는 서버에서 충돌 거부 |
| eventId | 사건마다 새 UUID. 동일 사건 재전송 시 ID·내용 유지 |
| searchId | 검색마다 새 ID, 검색 사건의 eventId와 동일 |
| impressionId | 노출 사건의 eventId |
| clickId | 클릭 사건의 eventId |
| viewId | 조회 사건의 eventId와 동일. 재초기화된 페이지 조회는 새 사건 |
| documentId | 프런트 고정 카탈로그와 documents.tsv의 명시적 키. 제목·검색 배열 순번으로 생성하지 않음 |
| documentVersion | 현재 local-v1 고정. 서버는 등록된 문서·버전 조합만 허용 |

프런트는 userId 앞뒤 공백을 제거한다. 서버는 받은 문자열을 그대로 검사·저장하며 자동으로 앞뒤 공백을 제거하지 않는다. userId는 개인정보 탐지·마스킹 기능이 아니므로 사용자가 임의의 비식별 라벨을 입력해야 한다.

- UI: 분석용 로그인 버튼 → 대화상자 → 세션 시작 → 현재 라벨·로그아웃 표시. 취소·미로그인·로그아웃 상태에서도 검색·열람을 허용하고 사건은 소급 수집하지 않는다. startSession·reset·renderSession이 생성·해제·UI 표시를 나눈다.
- 세션 키는 oneai-session-v1, 이동 연결 키는 oneai-navigation-v1이다. 세션을 sessionStorage에 쓸 수 있어야 시작하며 localStorage는 사용하지 않는다. 로그아웃은 브라우저 세션·이동 연결을 해제하지만 DB의 과거 사건을 삭제하지 않는다.
- 서버의 collection_sessions는 최초 정상 사건과 함께 생성된다. created_at은 브라우저 로그인 시간이 아닌 서버 최초 수신 시각이다. 별도 로그인·세션 생성 API는 없다.
- 복원은 opener가 없고 라벨이 유효하며 새로고침 또는 10초 이내의 대상 경로 일치 이동 정보가 있을 때 수행한다. 무활동 만료는 없다. 브라우저 탭 복제·BFCache·복잡한 뒤로가기에서의 완전한 격리·복원은 보장하지 않는다.
- 일반 검색 결과 클릭 시 검색·노출·클릭·세션 ID와 대상 경로·시각을 전달한다. 대상 문서에서 세션·경로 일치 및 10초 이내를 확인해 조회에 연결한다. 연결 불가 시 source=unknown, 세 연결 ID=null이다. 마지막 검색으로 추측 연결하지 않는다.
- 프런트의 click 리스너가 처리하는 클릭과 새 탭·보조 클릭의 범위는 다르다. Ctrl/Meta/Shift/Alt 클릭에는 일반 이동 연결을 저장하지 않고 중간 버튼 auxclick 전용 처리는 없다.
- 검색어 강조용 manual-search-highlight는 별도 키다. userId·검색어를 새 URL 파라미터에 넣지 않는다.

문서 카탈로그는 현재 17개 항목으로 홈·목록·업무·문의 페이지를 포함한다. 프런트는 경로의 index.html 별칭을 정규화한다. HTML data-group·data-page는 메뉴 상태용이며 고유 ID가 아니다. 문서 절은 기존 앵커를 쓰고 절별 별도 수집 ID는 없다.

서버는 documents.tsv의 ID·경로를 시작 시 INSERT OR IGNORE하고 버전은 local-v1으로 등록한다. 같은 ID·버전의 경로를 파일에서 바꿔도 기존 DB 행을 자동 갱신하지 않는다. 문서 개정·경로 변경 정책은 후속 항목이다. 구조화한 출처 필드는 현재 DB에 없으며 업무 출처는 [additional_info.md](additional_info.md)와 기존 검토 기록을 참조한다.

## 4. 이벤트 수신 API

`POST /api/v1/events`, Content-Type: application/json. 요청당 사건 하나이며 인증 헤더·배치·수정·삭제 API는 없다.

### 공통 필드·검증

| 필드 | 자료형·제약 |
|---|---|
| schemaVersion | 정수 1 |
| eventId·sessionId | 소문자 하이픈 형식 UUID 문자열 |
| eventType | 아래 네 사건 중 하나 |
| occurredAt | 최대 40자 문자열, Instant.parse 가능한 시각. UTC 밀리초 문자열로 저장 |
| userId | 3절의 라벨 문자열 |
| payload | 사건별 필수 필드만 가진 객체 |

공통·payload·결과 항목의 누락 필드와 미허용 필드는 거부한다. 요청 본문은 최대 65,536바이트이며 중복 JSON 키와 뒤따르는 추가 JSON도 거부한다. 길이는 Java 문자열의 UTF-16 코드 단위 기준이다. 서버가 receivedAt을 별도로 기록한다.

| eventType | payload 필수 필드 | 제약 |
|---|---|---|
| search_submitted | searchId, sourceDocumentId, query | searchId=eventId. source는 등록 ID 또는 명시적 null. query는 공백이 아닌 최대 200자, 서버에서 Java trim 결과와 같아야 함 |
| results_exposed | searchId, totalCount, items | 기존 검색과 같은 세션. 0≤totalCount≤100, totalCount=items.length. items의 문서 중복 금지·rank는 1부터 연속 |
| document_clicked | searchId, impressionId, documentId, documentVersion, rank | 노출의 검색·세션과 일치하고 해당 순위의 문서·버전과 일치 |
| document_viewed | viewId, documentId, documentVersion, source, searchId, impressionId, clickId | viewId=eventId. source=search이면 실제 클릭의 검색·노출·문서·버전·세션과 일치 |

각 items 항목은 documentId·documentVersion(각 최대 64자)·rank(정수)다. sourceDocumentId도 최대 64자이며 등록 여부를 검사한다. 0건은 totalCount=0·items=[]다. 빈 검색 제출은 프런트에서 안내만 표시하고 수집하지 않는다.

조회 source는 API에서 search·navigation·direct·unknown을 허용한다. search 이외에는 searchId·impressionId·clickId가 모두 명시적 null이어야 한다. **현재 프런트는 search와 unknown만 생성한다.**

검색어는 검색 사건에만 저장하고 다른 사건은 searchId로 연결한다. 테스트는 합성 문구만 사용한다. 실제 검색어의 저장·마스킹·보존 정책은 미확정이다.

### 등록 카탈로그를 사용하는 합성 예시

다음은 하나의 흐름이다. 후속 payload는 공통 필드로 감싸고 같은 userId·sessionId를 사용한다. 문서 ID는 현재 등록값을 참조하지만 문서 본문·실제 상담 내용은 포함하지 않는다.

```json
{
  "schemaVersion": 1,
  "eventId": "00000000-0000-4000-8000-000000000001",
  "eventType": "search_submitted",
  "occurredAt": "2026-10-26T00:00:00Z",
  "userId": "테스트상담사",
  "sessionId": "00000000-0000-4000-8000-000000000010",
  "payload": {
    "searchId": "00000000-0000-4000-8000-000000000001",
    "sourceDocumentId": "manual-home",
    "query": "합성 안내 가나다"
  }
}
```

results_exposed의 eventId 끝자리는 002:

```json
{
  "searchId": "00000000-0000-4000-8000-000000000001",
  "totalCount": 1,
  "items": [{"documentId": "contact", "documentVersion": "local-v1", "rank": 1}]
}
```

document_clicked의 eventId 끝자리는 003:

```json
{
  "searchId": "00000000-0000-4000-8000-000000000001",
  "impressionId": "00000000-0000-4000-8000-000000000002",
  "documentId": "contact",
  "documentVersion": "local-v1",
  "rank": 1
}
```

document_viewed의 eventId 끝자리는 004:

```json
{
  "viewId": "00000000-0000-4000-8000-000000000004",
  "documentId": "contact",
  "documentVersion": "local-v1",
  "source": "search",
  "searchId": "00000000-0000-4000-8000-000000000001",
  "impressionId": "00000000-0000-4000-8000-000000000002",
  "clickId": "00000000-0000-4000-8000-000000000003"
}
```

### 저장 응답과 오류

최초 저장은 201, 동일 재전송은 200이다. 다음은 최초 저장 예시이며, 재전송에서는 status=duplicate, receivedAt은 최초 수신 시각을 반환한다.

```json
{
  "eventId": "00000000-0000-4000-8000-000000000001",
  "status": "stored",
  "receivedAt": "2026-10-26T00:00:01.000Z"
}
```

```json
{
  "eventId": "00000000-0000-4000-8000-000000000003",
  "error": {
    "code": "DEPENDENCY_NOT_READY",
    "retryable": true,
    "message": "이벤트를 저장하지 못했습니다."
  }
}
```

| HTTP | 코드 | retryable |
|---|---|---|
| 400 | INVALID_JSON, VALIDATION_FAILED, UNSUPPORTED_SCHEMA | false |
| 409 | EVENT_ID_CONFLICT, SESSION_LABEL_CONFLICT, RELATION_MISMATCH | false |
| 409 | DEPENDENCY_NOT_READY | true |
| 413 / 415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | false |
| 503 | STORAGE_UNAVAILABLE | true |
| 500 | INTERNAL_ERROR | true |

파싱 가능한 정규 UUID eventId가 없으면 오류 응답의 eventId는 null이다. 미등록 문서는 검증 오류, 아직 없는 선행 사건·노출 항목은 DEPENDENCY_NOT_READY다. 성공은 커밋 후에만 응답한다. retryable은 재전송 가능성에 대한 서버 힌트이며 현재 프런트의 자동 재시도를 뜻하지 않는다.

## 5. SQLite 테이블·관계·인덱스

실제 기준은 [schema.sql](server/src/main/resources/db/schema.sql)이다. 아래 `?`만 NULL 허용이며 나머지는 명시적 NOT NULL이다. UUID·라벨·시각·해시는 TEXT, 건수·순위·스키마 버전은 INTEGER다. 시각은 UTC `YYYY-MM-DDTHH:mm:ss.SSSZ`로 저장한다.

| 테이블 | 컬럼·자료형 | 키·관계 |
|---|---|---|
| collection_sessions | session_id TEXT, user_id TEXT, created_at TEXT | PK session_id. 사용자 계정 FK 없음 |
| documents | document_id TEXT | PK document_id |
| document_versions | document_id TEXT, version TEXT, path TEXT | PK(document_id,version), document_id → documents |
| events | event_id TEXT, session_id TEXT, event_type TEXT, schema_version INTEGER, occurred_at TEXT, received_at TEXT, payload_digest TEXT | PK event_id, session_id → collection_sessions |
| searches | search_id TEXT, source_document_id TEXT?, query TEXT | PK/FK search_id → events, source_document_id → documents |
| result_exposures | impression_id TEXT, search_id TEXT, total_count INTEGER | PK/FK impression_id → events, search_id → searches |
| result_items | impression_id TEXT, rank INTEGER, document_id TEXT, document_version TEXT | PK(impression_id,rank), UNIQUE(impression_id,document_id), impression_id → result_exposures, (document_id,document_version) → document_versions |
| document_clicks | click_id TEXT, impression_id TEXT, rank INTEGER | PK/FK click_id → events, (impression_id,rank) → result_items |
| document_views | view_id TEXT, document_id TEXT, document_version TEXT, source TEXT, click_id TEXT? | PK/FK view_id → events, click_id → document_clicks, (document_id,document_version) → document_versions |

- 평가 테이블·평가 인덱스는 현재 없다. 평가 사건도 events의 허용 종류에 포함되지 않는다.
- DB CHECK는 사건 종류·schema_version=1, query의 trim 후 비어 있지 않음, total_count 정수·0 이상, result_items.rank 정수·1 이상, 조회 source와 click_id의 NULL 관계를 검사한다. 최대 길이·순위 연속성·세션 일치 등은 서버 검증이며 전부 DB CHECK로 보장되는 것은 아니다.
- FK는 LocalConfig에서 연결마다 활성화한다. 스키마 FK는 삭제 시 기본 NO ACTION이다. 개발자 삭제 API는 트랜잭션 안에서 종속 조회·클릭·노출·검색 행을 FK 역순으로 직접 삭제한다.
- 문서별 조회의 searchId·impressionId는 클릭·노출에서 도출하며 document_views에 중복 저장하지 않는다. 세션 1:N 사건, 검색 1:N 노출, 노출 1:N 항목, 항목 1:N 클릭, 클릭 1:N 조회를 허용한다.

PK·UNIQUE 외 실제 보조 인덱스:

| 이름 | 컬럼 |
|---|---|
| ix_sessions_user | collection_sessions(user_id,created_at) |
| ix_events_session | events(session_id,received_at) |
| ix_events_type | events(event_type,received_at) |
| ix_search_source | searches(source_document_id) |
| ix_exposure_search | result_exposures(search_id) |
| ix_item_document | result_items(document_id,document_version) |
| ix_view_document | document_views(document_id,document_version) |
| ix_view_click | document_views(click_id) |

## 6. 트랜잭션·중복·전송 실패

- EventService.store는 단일 API 프로세스에서 synchronized로 직렬화하고 TransactionTemplate으로 저장한다. events·해당 subtype·노출 항목 전체를 한 트랜잭션으로 저장하며 실패 시 새 세션 행 생성도 함께 롤백한다.
- 검색과 결과 노출은 별도 HTTP 요청·별도 트랜잭션이다. 검색만 성공하고 노출이 전달되지 않은 상태를 허용하며 성공한 검색을 되돌리지 않는다.
- 기존 eventId는 payload_digest를 비교해 동일하면 duplicate, 다르면 409를 반환한다. digest는 수신 JSON 전체의 객체 키를 정렬하고 배열 순서를 유지해 SHA-256으로 계산한다. 시각 문자열 등 값의 의미까지 정규화하지 않으며 DB 저장용 시각 정규화와 별개다.
- DB PK·UNIQUE도 중복을 방어한다. 여러 API 프로세스 사이의 별도 충돌 조정·복구 로직은 구현하지 않았다.
- 프런트는 fetch를 순차 메모리 큐로 전송하고 대기 사건을 최대 100개로 제한한다. 요청 타임아웃은 3초, keepalive=true, 인증 정보 없이 같은 로컬 출처로 전송한다. 페이지 이동을 기다리게 하거나 사건을 디스크에 저장하지 않는다.
- 전송 실패는 UI 상태로 알리고 검색 처리로 예외를 전파하지 않는다. 큐 초과·페이지 종료·세션 종료 등으로 사건이 유실될 수 있으며 재전송·서버 저장 성공을 보장하지 않는다. 자동 재시도는 없다.
- DB 연결·잠금·쓰기 실패는 저장 오류 응답으로 처리한다. SQLite 잠금 대기는 2초이며 journal 모드는 따로 지정하지 않는다. JSON 파일로 우회 저장하거나 유실 사건을 추측 복원하지 않는다.

## 7. 로컬 실행·저장·조회

### 실행·저장 경계

Java 21·Spring Boot 3.5.16·Maven·Spring JDBC·SQLite JDBC 3.53.4.0을 사용한다. 별도 프런트 프레임워크·ORM·DB 서버·인증 라이브러리는 없다. 빌드·실행 명령은 README를 따른다.

[application.properties](server/src/main/resources/application.properties)의 `oneai.db=local-data/oneai-collection.sqlite`가 기본 위치다. LocalConfig는 JAR·클래스 위치의 상위에서 AGENTS.md가 있는 저장소 루트를 찾고 상대 DB 경로를 그 루트 기준으로 해석한다. 작업 폴더 기준이 아니며 필요 시 절대경로 oneai.root로 루트를 지정할 수 있다. 부모 폴더는 생성하지만 기존 DB를 삭제하거나 초기화하지 않는다.

서버 시작 시 schema.sql의 CREATE IF NOT EXISTS와 카탈로그 INSERT OR IGNORE를 수행한다. 기존 테이블 정의를 마이그레이션하거나 잘못된 기존 DB를 자동 복구하는 기능은 없다.

기본 바인딩은 127.0.0.1:8080이다. 루트 홈과 pages·assets만 정적 제공하고 저장소 전체·DB·문서·설정을 노출하지 않는다. 프런트 수집은 localhost·127.0.0.1·[::1]의 HTTP(S)에서만 활성화한다. file:// 직접 열기는 검색·열람용이며 수집은 시작하지 않는다. 현재 서버 기본 바인딩 자체는 IPv4다.

서버가 정적 파일과 API를 함께 제공하므로 서버 전체 중단 시 새 HTTP 문서를 열 수 없다. 이미 열린 화면의 검색은 유지하고 정적 파일 직접 열기로 기존 문서를 확인할 수 있다.

.gitignore의 /local-data/는 DB·journal·WAL/SHM·그 안의 백업·로그·시험 자료를 제외한다. /server/target/, 환경 비밀설정·로그 제외 규칙도 적용되어 있다. 제외 규칙은 이미 추적된 파일을 자동 해제하지 않으므로 git ls-files와 git check-ignore로 확인한다. 백업·덤프도 local-data 경계 밖에 임의 생성하지 않는다.

### 로컬 개발자 조회·삭제

- 화면: `/dev/events`. 기존 검색 화면과 분리되며 common.js를 로드하지 않아 조회 자체가 수집 사건을 만들지 않는다. 새로고침·필터·상세 JSON·빈 목록·실패 안내를 제공한다. 현재 조회된 최대 100건 안에서 행 선택·전체 선택을 지원한다.
- API: `GET /api/v1/dev/events?userId=테스트상담사&eventType=search_submitted`. 실제 요청은 URLSearchParams 등으로 인코딩한다. 필터 생략 시 전체, userId는 정확 일치, eventType은 현재 네 종류 중 하나다.
- API·DB 시각은 UTC RFC 3339 문자열로 유지한다. 개발자 화면의 표·상세 JSON은 한국 시간 `YYYY-MM-DD HH:mm:ss`로 변환해 표시하고 시간대 접미사는 표시하지 않는다.
- 삭제 API: `DELETE /api/v1/dev/events` 요청 본문은 `{"eventIds":["<event UUID>"]}`이며 1~100개의 중복 없는 기존 형식 UUID만 받는다. 화면 확인 뒤 선택 행 또는 현재 표시 행의 ID만 전송한다. 필터 전체를 서버에서 다시 삭제하지 않는다.
- 삭제는 실제 SQLite 이벤트 행 삭제이며 한 트랜잭션으로 처리한다. 검색 사건을 선택하면 그 검색의 노출·클릭·클릭에서 파생된 조회를, 노출 사건을 선택하면 해당 노출의 클릭·연결 조회를, 클릭 사건을 선택하면 연결 조회를 함께 삭제한다. FK 역순으로 subtype·결과 항목·events를 삭제하고, 이벤트가 더 남지 않은 관련 세션만 정리한다. 문서·문서 버전 카탈로그와 무관한 사건은 유지한다. DB 삭제의 복구는 제공하지 않는다.
- SQL SELECT와 읽기 트랜잭션으로 조회한다. received_at DESC, events.rowid DESC로 최근 최대 100건을 반환하며 페이지 나누기는 없다.
- 응답은 `{ "events": [], "limit": 100 }`. 각 항목은 기존 공통 필드·payload를 복원하고 receivedAt·context를 추가한다. context는 searchId·documentId·rank를 담으며 사건에 따라 null일 수 있다. 검색 사건의 documentId는 출발 문서다. 노출 사건의 다문서·순위는 payload.items에서 표시한다.
- 검색 사건의 results 배열은 해당 검색에 연결된 impressionId·totalCount·items 목록이다. 노출 미수신은 results=[], 0건 노출 수신은 totalCount=0·items=[]로 구분한다. 읽기용 추가 필드를 수집 POST에 다시 보내면 미허용 필드로 거부된다.
- 실제 원격 주소가 loopback인 경우만 화면·조회·삭제 API를 허용하고 Cache-Control:no-store를 적용한다. 외부 주소는 403/LOCAL_ONLY, 필터·삭제 요청 오류는 400/VALIDATION_FAILED, 조회 실패는 503/READ_UNAVAILABLE, 삭제 실패는 503/DELETE_UNAVAILABLE이다. 오류 응답에 SQL·검색어·경로를 넣지 않는다. 실제 수집 자료에 적용하기 전 백업·보존 정책을 별도로 정한다.

### SQLite 파일 열람

DB가 존재하는지 먼저 확인한 뒤 로컬 CLI·뷰어에서 읽기 전용으로 연다. 외부 업로드형 뷰어를 사용하거나 빈 DB를 새로 만들어 저장 성공으로 오인하지 않는다.

```powershell
sqlite3 -readonly local-data/oneai-collection.sqlite
```

```sql
SELECT s.user_id, e.session_id, q.search_id, q.query, e.occurred_at, e.received_at
FROM searches q JOIN events e ON e.event_id=q.search_id
JOIN collection_sessions s ON s.session_id=e.session_id;
SELECT x.search_id, x.total_count, i.rank, i.document_id
FROM result_exposures x LEFT JOIN result_items i ON i.impression_id=x.impression_id;
SELECT v.view_id, v.source, v.document_id, c.click_id, x.search_id
FROM document_views v LEFT JOIN document_clicks c ON c.click_id=v.click_id
LEFT JOIN result_exposures x ON x.impression_id=c.impression_id;
```

0건 검색은 total_count=0이며 LEFT JOIN의 항목이 null이다. 파일 크기만으로 저장 성공을 판정하지 않는다. 실행 중 DB 본체만 임의 복사하지 않으며 잠금이 있으면 API를 정상 종료하고 확인한다.

## 8. 데이터·로그·검증 기록

- 클릭·조회·노출은 서로 다른 신호이며 정답·상담 해결 성공으로 판정하지 않는다. 추천 점수·성공 확률은 저장하거나 표시하지 않는다.
- 검색어 외 문서 본문·상담 메모·계정 비밀번호·IP·User-Agent는 수집 테이블에 저장하지 않는다. 개인정보 마스킹·자동 탐지는 없으므로 실제 자료를 입력하지 않는다.
- 수신 오류 응답은 일반화한 메시지만 반환한다. 애플리케이션은 payload를 로그로 출력하지 않으며 logback-spring.xml에서 WARN 미만 출력을 차단한다. Tomcat 접근 로그도 비활성이다.
- 기존 검토 문서 docs/phase-4-review.md·docs/exception-guidance-review.md는 매뉴얼 검토·회귀 참고이며 수집 MVP 단계 완료 기록이 아니다.

기존 검증 기록: CollectionTest 보고서는 15개 테스트, 실패·오류·건너뜀 0개다. 저장·연결·한글 시각·0건·중복/동시 재전송·롤백·조회 필터·외부 주소 거부·자유 라벨 저장을 포함한다. 과거 브라우저 검증에서는 공개·합성 시험 본문으로 수집·개발자 조회·필터·API 중단·라벨 로그인·복원·모바일 입력창을 확인했다. 실회사 자료는 사용하지 않았다.

이번 최신화는 코드·설정 대조와 문서 검사만 수행하며 테스트·빌드·브라우저·DB 검증을 다시 실행한 결과가 아니다.

## 9. 미구현·미확정·현재 한계

| 항목 | 상태 |
|---|---|
| 유용성 평가 | UI·허용값·재평가/취소 정책 미확정. usefulness_rated 수신·테이블·인덱스 미구현 |
| 실제 검색어·자료 | 사용 승인·마스킹·보존 기간·운영 삭제 정책 미확정. 현재는 합성 데이터 실험만 |
| 세션 | 운영 만료·복원 정책, 새 탭 복제 방지·BFCache·뒤로가기 전체 검증 미완료 |
| 전송 | 자동 재시도·페이지 종료 시 보장·영구 큐 미구현. 현재 큐·타임아웃의 유실 한계 존재 |
| 문서 개정 | local-v1 고정. 버전 증가·동일 버전 경로 변경·구조화한 출처 관리 정책 미확정 |
| 스키마 변경 | 초기 생성만 구현. 버전별 마이그레이션·기존 데이터 변환은 후속 작업 |
| 시각 | 클라이언트 허용 오차 정책 미확정. 수신 시각과 분리 저장하며 사용자 시각을 정답으로 간주하지 않음 |
| 검증 차이 | JS trim과 Java isBlank/trim의 유니코드 공백 처리 차이·모든 경계 문자 동작은 전체 검증하지 않음 |
| 조회·운영 범위 | 최근 100건, 단일 로컬 프로세스. 다프로세스·외부 배포·통계·데이터 편집/삭제는 구현 범위 밖 |
