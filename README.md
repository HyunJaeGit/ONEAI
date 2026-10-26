# ONE AI — 로컬 개발 프로토타입

기존 디벗 양품화 정적 매뉴얼 검색을 유지하며 상담사의 검색 행동을 로컬에서 수집하는 ONE AI를 개발합니다. 현재 목표는 합성 데이터로 수집 흐름을 구현하고 검증하는 것입니다.

## 현재 구현과 개발 범위

- 기존 정적 매뉴얼의 문서 열람·검색·검색어 강조·인쇄·패치노트·문의하기 기능을 유지합니다.
- 합성 USERID·세션, 검색·결과 노출·클릭·조회 이벤트와 Java 21/Spring Boot API·SQLite 저장을 구현했습니다.
- 유용성 평가 UI·허용값은 미확정이며 AI 추천·학습은 현재 범위에 포함하지 않습니다.
- 자동 배포 설정은 제거한 상태이며, 현재 작업에 외부 업로드나 배포는 포함하지 않습니다.

## 프로젝트 구조

```text
ONEAI/
├─ AGENTS.md                         개발 범위·데이터·작업 지침
├─ README.md                         현재 기능·구조·실행·확인 방법
├─ 수집-MVP-설계.md                  이벤트 계약·DB 관계·설계 및 미확정 정책
├─ additional_info.md                매뉴얼 운영정보의 출처·적용 범위
├─ 패치내역.md                       변경 이력
├─ index.html                        기존 매뉴얼 홈
├─ pages/                            업무별 정적 문서
├─ assets/
│  ├─ css/common.css                 공통 화면·분석용 로그인 스타일
│  └─ js/common.js                   검색·목차·문서 이동·세션·수집 이벤트
├─ docs/                             기존 매뉴얼 내용·화면 검토 기록
├─ server/
│  ├─ pom.xml                        Java 21·Spring Boot·SQLite 의존성과 빌드
│  ├─ src/main/java/local/oneai/
│  │  ├─ CollectionApplication.java  서버 진입점
│  │  ├─ api/
│  │  │  ├─ LocalController.java     이벤트 수신·오류 응답·문서 홈 제공
│  │  │  └─ DeveloperController.java 로컬 전용 조회 API·개발자 화면
│  │  ├─ service/
│  │  │  ├─ EventService.java        검증·ID 연결·중복·트랜잭션·저장
│  │  │  └─ EventQueryService.java   기존 테이블에서 이벤트 JSON 복원·필터
│  │  └─ config/LocalConfig.java     저장소 루트·DB 연결·정적 리소스 설정
│  ├─ src/main/resources/
│  │  ├─ application.properties     로컬 주소·포트·DB 경로 설정
│  │  ├─ logback-spring.xml          로그 출력 제한
│  │  ├─ db/schema.sql              SQLite 테이블·제약·인덱스
│  │  ├─ db/documents.tsv            문서 ID·경로 카탈로그
│  │  └─ dev/events.html            개발자용 읽기 전용 조회 화면
│  ├─ src/test/java/local/oneai/
│  │  └─ CollectionTest.java         합성 데이터 API·SQLite 통합 테스트
│  └─ target/                       생성된 JAR·테스트 결과 (Git 제외)
└─ local-data/                      DB·로그·로컬 시험 파일 (Git 제외)
   └─ oneai-collection.sqlite        기본 이벤트 영구 저장소
```

현재 프런트는 별도 프레임워크·패키지 설치 없이 동작합니다. 기존 문서·검색은 루트의 HTML과 assets에서 관리하고, 수집·조회 API는 server에 둡니다. 문서 내용이나 검색 순서를 백엔드로 옮기지 않습니다.

## 수집 흐름과 API

`분석용 로그인 → 탭 세션 시작 → 검색·결과 노출 → 문서 클릭 → 문서 조회 → 로컬 API → SQLite`

- `userId`는 합성 분석 라벨입니다. `startSession`·`reset`이 세션 생명주기를, `renderSession`이 로그인 상태 표시를 담당합니다. 추후 인증 연동 지점은 분리했지만 현재 계정·비밀번호·인증 토큰은 없습니다.
- ID는 접두어 없이 자유롭게 정합니다. 한글·영문·공백·기호를 허용하고 앞뒤 공백은 입력 시 제거합니다. 빈 값·제어문자·64 UTF-16 코드 단위 초과만 제한합니다. 실제 이름·회사 ID·개인정보를 사용해도 된다는 뜻은 아닙니다. 기존 demo- ID와 저장 데이터도 그대로 사용할 수 있습니다.
- 검색마다 새로운 `searchId`를 만들고 검색어·표시 결과·1부터 시작하는 순위를 기록합니다. 결과 0건도 검색 사건과 빈 노출 목록으로 남깁니다.
- 클릭과 조회는 별도 사건입니다. 가능한 경우 `sessionId → searchId → impressionId → clickId → viewId`로 연결하고 직접 조회는 검색에 추측 연결하지 않습니다.
- JSON은 HTTP 전송 형식이고 SQLite가 영구 저장소입니다. 같은 eventId·같은 내용은 중복 성공으로 처리하고, 다른 내용의 ID 재사용은 거부합니다. 한 사건과 그 결과 항목은 트랜잭션으로 저장하지만 검색 요청과 노출 요청 전체가 하나의 트랜잭션은 아닙니다.

| 경로 | 역할 |
|---|---|
| `POST /api/v1/events` | 검색·결과 노출·클릭·조회 수신. 최초 저장 201, 동일 사건 재전송 200 |
| `GET /api/v1/dev/events` | 최근 100건 읽기. userId·eventType 선택 필터 |
| `/dev/events` | 개발자용 이벤트 조회 화면 |

프런트의 문서 ID·초기 버전 `local-v1`과 서버의 documents.tsv 카탈로그는 일치해야 합니다. 제목·검색 배열 순번을 문서 ID로 사용하지 않습니다. 문서 경로·카탈로그를 바꾸면 양쪽 매핑과 테스트를 함께 확인합니다.

## 로컬 실행

저장소 루트의 [index.html](index.html)을 브라우저에서 열면 기존 정적 매뉴얼을 확인할 수 있습니다. 현재 정적 화면에는 별도 패키지 설치나 빌드가 필요하지 않습니다. 공통 메뉴와 검색을 사용하려면 JavaScript를 활성화합니다.

### 로컬 수집 API 실행

Java 21, Maven 3.6.3 이상이 필요합니다. 수집 서버는 Spring Boot 3.5.16·Spring JDBC·SQLite JDBC 3.53.4.0을 사용합니다. Java 호환 기준은 [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/3.5/system-requirements.html), 드라이버는 [SQLite JDBC 공식 저장소](https://github.com/xerial/sqlite-jdbc)를 참고합니다.

저장소 루트에서 실행합니다.

```powershell
mvn -f server/pom.xml test
mvn -f server/pom.xml package
java -jar server/target/collection-0.1.0.jar
```

Maven이 PATH에 없다면 이번 검증에서 사용한 임시 설치본으로 `mvn`을 대체할 수 있습니다.

```powershell
& "$env:TEMP/oneai-build-tools/apache-maven-3.9.11/bin/mvn.cmd" -f server/pom.xml "-Dmaven.repo.local=$((Get-Location).Path)/local-data/maven-repository" package
```

`http://127.0.0.1:8080/`에서 화면과 API를 함께 제공합니다. 루트는 현재 작업 폴더가 아닌 JAR·클래스 위치의 상위 저장소에서 찾으므로 다른 폴더에서 실행해도 같은 DB를 사용합니다. JAR을 저장소 밖으로 옮겼다면 `java "-Doneai.root=<저장소 절대경로>" -jar <JAR 경로>`로 루트를 지정합니다. `.git`·설계 문서·DB 파일은 HTTP로 제공하지 않습니다.

헤더의 **분석용 로그인**에서 `테스트상담사` 같은 합성 USERID를 입력하면 세션을 시작합니다. 인증·비밀번호 확인은 없으며, 로그인 상태에는 라벨과 로그아웃 버튼이 표시됩니다. 같은 탭의 이동·새로고침에서는 세션을 유지하고 로그아웃 후 다시 로그인하면 새 세션을 만듭니다. 입력 창 취소 또는 로그아웃 상태에도 검색·열람은 가능합니다. 수집 시작 전에는 사건을 기록하지 않습니다. 검색·노출·클릭·조회는 서로 다른 사건이며 평가 기능은 아직 비활성입니다. 검색어는 합성 테스트 문구만 사용하고 기존 업무 본문을 공개·합성 테스트 자료로 간주하지 않습니다. 통합 검증은 Git 제외된 `local-data/browser-fixture/`의 합성 본문·시험 색인으로 수행했습니다.

API가 실패해도 이미 열린 화면의 검색과 링크 동작을 막지 않습니다. 같은 서버가 정적 파일도 제공하므로 서버 전체를 끄면 새 HTTP 페이지는 열 수 없습니다. 이 경우 기존 파일 직접 열기 방식으로 매뉴얼을 사용할 수 있지만 이벤트 전송은 하지 않습니다. 브라우저의 전송 큐는 메모리에만 있고 페이지 종료 시 유실될 수 있으며 자동 재시도는 아직 없습니다.

### 데이터 저장과 확인

DB는 저장소 루트의 `local-data/oneai-collection.sqlite`입니다. JSON 파일로 저장하지 않습니다. 합성 실험 DB이며 실데이터 수집에 대한 승인을 의미하지 않습니다. DB·보조 파일·로컬 시험 화면·Maven 캐시와 `server/target/`은 Git에서 제외합니다. 파일 자동 삭제·백업은 수행하지 않습니다.

SQLite CLI가 설치되어 있으면 저장소 루트에서 읽기 전용으로 엽니다. CLI가 없으면 로컬 SQLite 뷰어에서 같은 파일을 읽기 전용으로 열 수 있습니다. 외부 업로드형 뷰어는 사용하지 않습니다.

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

0건 검색은 `total_count=0`인 노출 행과 결과 항목 0개로 확인합니다. API 저장 성공은 DB 커밋 후에만 응답합니다. 자세한 데이터 계약·미확정 정책은 [수집 MVP 설계](수집-MVP-설계.md)를 참고합니다.

### 개발자용 이벤트 조회

로컬 서버 실행 후 `http://127.0.0.1:8080/dev/events`를 엽니다. 기존 검색 화면과 분리된 읽기 전용 화면이며 조회 화면 자체는 수집 사건을 만들지 않습니다. 새로고침으로 DB의 최근 100건을 수신 시각 역순으로 가져옵니다. USERID는 정확히 일치하는 값, 이벤트 종류는 선택값으로 필터링합니다.

목록에서 세션·검색·문서 ID·순위를 비교하고 상세를 펼쳐 사건 JSON을 확인합니다. 검색 사건의 상세에는 검색어와 해당 검색에 연결된 노출 목록이 포함됩니다. 직접 조회의 searchId와 순위는 비어 있으며 추측 연결하지 않습니다. 결과가 없는 경우와 API 실패 시 안내가 표시됩니다. 서버 전체 종료 후에는 이미 열린 화면에서 실패 안내를 확인할 수 있고 새 화면 HTTP 로드는 불가능합니다.

읽기 API는 `GET /api/v1/dev/events?userId=tester01&eventType=search_submitted`입니다. 필터를 생략하면 전체 최근 사건을 반환합니다. 조회 화면과 API는 loopback 접속만 허용하며 응답은 `Cache-Control: no-store`입니다. 외부 배포·편집·삭제·차트는 제공하지 않습니다. DB 위치 설정은 `server/src/main/resources/application.properties`의 `oneai.db` 한 곳이며 상대경로는 저장소 루트 기준으로 해석합니다. 기존 DB가 있으면 그대로 사용합니다.

## 데이터와 개발 원칙

- 회사·발주처 승인 전에는 실제 상담 기록이나 내부 자료를 새로 수집·학습·외부 전송하지 않습니다. 로컬 개발에도 같은 기준을 적용합니다.
- 개인정보를 최소화하고 사업별 데이터와 지식을 교차 사용하지 않습니다.
- 클릭·체류·채택은 서로 다른 신호이며, 해결 성공이나 검증된 정답으로 취급하지 않습니다. 검증 전 추천 점수를 해결 확률로 표시하지 않습니다.
- 온라인 자동 재학습, 외부 AI 호출, 고객용 생성형 답변과 다른 사업 확장은 별도 승인·검증 전까지 범위에 포함하지 않습니다.
- 기술 스택·인증·배포 환경·일정은 확정된 것으로 간주하지 않습니다. 구체적인 작업 기준은 [AGENTS.md](AGENTS.md)를 따릅니다.

## 검증과 남은 범위

이전 구현 단계에서 API·SQLite 통합 테스트 14개와 JAR 빌드를 통과했습니다. 브라우저에서는 합성 로그인·세션 복원·로그아웃·재로그인, 검색·노출·클릭·조회 연결, 0건·필터·빈 목록·API 중단 안내를 확인했습니다. 이는 당시의 검증 기록이며 이번 README 수정에서 테스트·빌드를 다시 실행한 것은 아닙니다.

- 자동 재시도·페이지 종료 시 전송 보장, 새 탭 복제·뒤로가기의 전체 검증은 남아 있습니다.
- 조회는 최근 100건만 제공하며 편집·삭제·페이지 나누기·차트는 없습니다.
- 유용성 평가 UI·허용값, 실제 자료의 검색어 저장·마스킹·보존·삭제 정책은 미확정입니다.
- DB 초기 스키마는 존재 여부를 확인해 적용하며 기존 DB를 초기화하지 않습니다. 이후 스키마 변경을 위한 버전별 마이그레이션은 별도 작업입니다.

[수집-MVP-설계.md](수집-MVP-설계.md)는 상세 데이터 계약과 미확정 정책의 참조 문서로 유지합니다. 과거 설계 제안과 후속 구현 상태가 구분되어 있으므로 현재 동작은 README·실제 코드와 후속 구현 항목을 함께 확인합니다. docs의 검토 기록은 기존 매뉴얼 검토용이며 수집 MVP의 단계 완료 기록을 대신하지 않습니다.

## 첫 화면에서 찾기

- **양품화 사업 안내**에서 사업 흐름, 수거 대상과 품목, 현장 점검 기준, 양품화 후 관리전환·배부 내용을 확인합니다. 펜슬 취급과 제공 기준은 사업 안내의 품목별 상세 페이지에서 볼 수 있습니다.
- **학교 측 업무 프로세스**에서 수거 전 준비부터 배부·설치 확인까지 7단계를 순서대로 확인합니다.
- **디벗 재배치·여분 기기**는 양품화 사업과 구분되는 별도 메뉴입니다.
- **문의 경로**에서 문의 유형에 맞는 연락 방법과 시스템 접속 안내를 확인합니다.

## 페이지 이용 방법

- 페이지 위쪽 검색창에서 `수리동의서`, `계정 로그아웃`, `일정 변경`처럼 궁금한 말을 검색하면 관련 안내로 이동할 수 있습니다.
- 본문에 `해당하는 경우 추가 확인`이 있으면 상황에 해당하는 항목을 펼쳐 확인합니다. 일부 항목은 검색 결과나 바로가기 링크를 선택하면 자동으로 열립니다.
- 현재 페이지를 출력하려면 위쪽의 인쇄 버튼을 사용합니다. 출력할 때는 접힌 추가 안내도 함께 포함됩니다.
- 헤더의 메뉴에서 **패치노트**를 선택하면 날짜별 주요 변경 내역을 볼 수 있습니다. **문의하기**를 선택하면 안내 내용에 대한 의견이나 수정 요청을 보낼 수 있습니다.

## 확인할 때 참고하세요

학교별 일정·수량·진행 상태와 시스템 메뉴는 학교에 안내된 공문과 디벗관리시스템에서 확인하세요. 기기 상태, 보급 연도, 관리 사업자에 따라 절차가 달라질 수 있으므로 해당 기기의 라벨과 안내 범위를 함께 확인합니다.

별도 표시된 예외 안내는 특정 상황이나 기기에만 적용됩니다. 일반 절차와 구분해 해당 조건을 확인한 뒤 안내하세요.

## 공통 코드 관리

- 헤더·푸터·상세 목차는 `assets/js/common.js`에서 생성합니다. 각 HTML에는 `site-header`, `site-footer`, `section-menu` 자리만 둡니다.
- 페이지의 `body`에 있는 `data-root`는 루트까지의 상대 경로, `data-group`과 `data-page`는 현재 메뉴와 문서를 지정합니다.
- 헤더·푸터의 이름은 `siteName`, 목차 항목은 `sections`, 공통 창의 열기·닫기는 `bindDialog`에서 관리합니다.
- JavaScript를 끄면 공통 메뉴·목차·검색 대신 안내 문구가 표시됩니다. 본문과 본문 링크는 HTML에 유지합니다.
- 각 페이지의 탭 제목과 본문은 HTML에서 수정합니다. 본문을 바꾸면 공통 JS의 검색 색인도 함께 확인합니다.
