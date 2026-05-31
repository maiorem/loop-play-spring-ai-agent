# 3단계: InMemory vs JdbcChatMemory — 의사결정 트리


## 검증 — 시나리오 5종 (JDBC 프로필)

> 1단계와 동일한 시나리오를 JDBC 프로필에서 다시 실행한다. 응답이 InMemory와 동일해야 한다.

### 시나리오 1 (JDBC)

**요청**

```bash
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: jdbc-s1" -d '{"message":"2024-1234 어디쯤이에요?"}'
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: jdbc-s1" -d '{"message":"그거 몇 시에 도착해요?"}'
```

**1회차 응답**

```
현재 라이더는 역삼역 사거리 부근에서 배송 중입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다.
```

**2회차 응답**

```
예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다. 정확한 도착 시간은 실시간 교통 상황에 따라 약간 변동될 수 있습니다.
```

**Memory 상태**

```json
[
  { "type": "USER",      "content": "2024-1234 어디쯤이에요?" },
  { "type": "ASSISTANT", "content": "현재 라이더는 역삼역 사거리 부근에서 배송 중입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다." },
  { "type": "USER",      "content": "그거 몇 시에 도착해요?" },
  { "type": "ASSISTANT", "content": "예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다. 정확한 도착 시간은 실시간 교통 상황에 따라 약간 변동될 수 있습니다." }
]
```

> 1단계 InMemory 실행과 응답이 동일하다. 저장소가 바뀌어도 LLM이 받는 프롬프트 구조는 같기 때문이다. `MessageChatMemoryAdvisor`가 저장소 종류에 상관없이 대화 이력을 꺼내 프롬프트 앞에 붙여주므로, LLM 입장에서는 Memory가 HashMap에 있든 H2에 있든 차이가 없다.
>
> "그거 몇 시에 도착해요?"에 Tool 재호출 없이 1회차 응답에서 도착 시간을 꺼낸 것도 1단계와 같다. ASSISTANT_PROMPT의 재사용 지시가 저장소 종류와 무관하게 동작한다는 확인이기도 하다.
>
> 단, 여기서 돌려준 "오후 5시 5분"은 1회차 조회 시점 기준이다. 재시작 실험 2에서 파일 H2를 쓰면 이 오래된 시간이 재시작 이후에도 그대로 답변에 나온다. 저장소 교체로 생기는 새로운 문제다.

### 시나리오 4 (세션 격리, JDBC)

**요청**

```bash
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: jdbc-A4" -d '{"message":"2024-1234 배달 현황 알려줘"}'
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: jdbc-B4" -d '{"message":"그 주문 어디쯤이야?"}'
```

**세션 A 응답**

```
현재 배달 상태는 "배달 중"이며, 라이더의 위치는 역삼역 사거리 부근입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 13분 11초입니다.
```

**세션 B 응답**

```
어떤 주문을 말씀하시는 건가요? 최근에 처리한 주문번호를 알려주시면 도와드리겠습니다.
```

**세션 B Memory**

```json
[
  { "type": "USER",      "content": "그 주문 어디쯤이야?" },
  { "type": "ASSISTANT", "content": "어떤 주문을 말씀하시는 건가요? 최근에 처리한 주문번호를 알려주시면 도와드리겠습니다." }
]
```

> B는 A의 1234 맥락을 전혀 모른다. 격리가 됐다는 건 확인됐다. 그런데 InMemory와 JDBC에서 격리가 구현되는 방식이 다르다.
>
> InMemory에서는 세션마다 별개의 `List<Message>` 객체가 HashMap에 들어간다. 물리적으로 분리된 메모리다. JDBC에서는 모든 세션 데이터가 `SPRING_AI_CHAT_MEMORY` 테이블 한 곳에 섞여 있고, `conversation_id` 컬럼으로 구분한다. 테이블을 열면 jdbc-A4와 jdbc-B4의 행이 나란히 보인다. 격리는 `WHERE conversation_id = ?` 쿼리 하나가 담당한다.
>
> 이 차이는 운영 측면에서 의미가 있다. DB 관리자가 테이블 전체를 조회하면 모든 고객 대화를 볼 수 있다. InMemory에서는 접근 자체가 애플리케이션을 통해서만 가능했는데, JDBC로 전환하는 순간 DB 접근 권한을 가진 사람이 데이터를 볼 수 있게 된다. 컬럼 암호화나 접근 제어가 필요해지는 시점이다.

---

## H2 Console 캡처


**쿼리 결과**

```json
[
  { "CONVERSATION_ID": "jdbc-s1",  "TYPE": "USER",      "timestamp": "2026-05-31T07:58:34.046+00:00", "CONTENT": "2024-1234 어디쯤이에요?" },
  { "CONVERSATION_ID": "jdbc-s1",  "TYPE": "ASSISTANT", "timestamp": "2026-05-31T07:58:34.047+00:00", "CONTENT": "현재 라이더는 역삼역 사거리 부근에서 배송 중입니다..." },
  { "CONVERSATION_ID": "jdbc-s1",  "TYPE": "USER",      "timestamp": "2026-05-31T07:58:34.048+00:00", "CONTENT": "그거 몇 시에 도착해요?" },
  { "CONVERSATION_ID": "jdbc-s1",  "TYPE": "ASSISTANT", "timestamp": "2026-05-31T07:58:34.049+00:00", "CONTENT": "예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다..." },
  { "CONVERSATION_ID": "jdbc-A4",  "TYPE": "USER",      "timestamp": "2026-05-31T07:58:41.141+00:00", "CONTENT": "2024-1234 배달 현황 알려줘" },
  { "CONVERSATION_ID": "jdbc-A4",  "TYPE": "ASSISTANT", "timestamp": "2026-05-31T07:58:41.142+00:00", "CONTENT": "현재 배달 상태는 \"배달 중\"이며, 라이더의 위치는 역삼역 사거리..." },
  { "CONVERSATION_ID": "jdbc-B4",  "TYPE": "USER",      "timestamp": "2026-05-31T07:58:42.367+00:00", "CONTENT": "그 주문 어디쯤이야?" },
  { "CONVERSATION_ID": "jdbc-B4",  "TYPE": "ASSISTANT", "timestamp": "2026-05-31T07:58:42.368+00:00", "CONTENT": "어떤 주문을 말씀하시는 건가요? 최근에 처리한 주문번호를 알려주시면..." }
]
```

> 한 대화 안에서 USER→ASSISTANT→USER→ASSISTANT 행들의 timestamp가 밀리초 단위(046, 047, 048, 049)로 찍혀 있다. 대화가 진행되는 동안 실시간으로 쓰는 게 아니라, 응답이 완성된 뒤 한 번에 저장하기 때문이다.
>
> TOOL 타입 행이 없다. Tool을 호출한 세션도 마찬가지다. `getDeliveryStatus(2024-1234)` 호출이 일어났어도 그 흔적은 DB에 없다. Tool 결과는 ASSISTANT 응답 문장에 녹아들어 있을 뿐이다. "역삼역 사거리 부근에서 배송 중입니다"라는 ASSISTANT 행이 Tool 호출의 유일한 증거다.
>
> ASSISTANT 행의 content 길이가 짧다. SUBSTRING(content, 1, 80)으로 잘린 결과다. 실제로 DB에 들어간 content는 전체 응답 문장이고, 이 안에 주문번호·금액·메뉴 정보까지 포함된다. 고객 주문 정보가 평문으로 저장된다는 뜻이다.

---

## 재시작 실험 — 영속성 검증

### 실험 1: `jdbc:h2:mem:...` (인메모리 H2)

```bash
# 대화 기록
curl -s -X POST http://localhost:8080/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Session-Id: restart-test" \
  -d '{"message":"2024-1234 주문 상태 알려줘"}'

# 앱 종료 후 재시작
./gradlew bootRun --args='--spring.profiles.active=jdbc'

# 재시작 후 "그거" 질문
curl -s -X POST http://localhost:8080/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Session-Id: restart-test" \
  -d '{"message":"그거 언제 도착해요?"}'
```

**재시작 전 Memory 상태 (`/api/v1/session/jdbc-s1/messages`)**

```json
[
  { "type": "USER",      "content": "2024-1234 어디쯤이에요?" },
  { "type": "ASSISTANT", "content": "현재 라이더는 역삼역 사거리 부근에서 배송 중입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 5분경입니다." }
]
```

**재시작 후 세션 목록 및 Memory 상태**

```bash
curl -s http://localhost:8080/api/v1/session/ids       # []
curl -s http://localhost:8080/api/v1/session/jdbc-s1/messages  # []
```

```
세션 목록: []
jdbc-s1 Memory: []
```

**재시작 후 "그거 언제 도착해요?" 응답**

```
어떤 주문을 말씀하시는 건가요? 최근에 언급된 주문번호를 알려주시면 감사하겠습니다.
```

> 세션 목록이 `[]`가 됐다. `DB_CLOSE_DELAY=-1` 옵션이 있는데도. 이 옵션은 "마지막 커넥션이 닫혀도 DB를 유지하라"는 뜻이지, JVM 재시작을 버텨낸다는 게 아니다. JVM이 꺼지면 힙도 사라지고 H2도 함께 사라진다.
>
> InMemory(Spring의 `ConcurrentHashMap`)와 `jdbc:h2:mem`은 결국 같은 운명이다. 저장 위치만 다를 뿐 둘 다 JVM 메모리 위에 산다. JDBC 드라이버를 거친다고 영속성이 생기는 게 아니다.
>
> 그렇다면 `jdbc:h2:mem`이 InMemory보다 나은 점은 없을까. SQL 쿼리로 데이터를 직접 조회할 수 있고, 트랜잭션 지원이 있고, DB 스키마 검증이 된다. 테스트 환경에서 실제 DB 동작을 흉내 낼 때 유용하다. 하지만 운영에서 영속성 목적으로 쓸 이유는 없다.

### 실험 2: `jdbc:h2:file:./data/baedal` (파일 기반 H2)

`application-jdbc.yml`의 `url`을 아래로 변경하고 `initialize-schema: always`로 함께 설정:

```yaml
url: jdbc:h2:file:./data/baedal;MODE=PostgreSQL
...
initialize-schema: always
```

**재시작 전 대화**

```bash
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: file-test" -d '{"message":"2024-1234 주문 상태 알려줘"}'
```

```
2024-1234번 주문의 상태는 배달 중입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 15분입니다.
허니콤보(1개, 23,000원)와 콜라 1.25L(1개, 3,000원). 총 26,000원.
```

파일 생성 확인:
```
./data/baedal.mv.db  (24576 bytes)
```

**재시작 후 Memory 상태**

```bash
curl -s http://localhost:8080/api/v1/session/ids
# ["file-test"]

curl -s http://localhost:8080/api/v1/session/file-test/messages
```

```json
[
  { "type": "USER",      "content": "2024-1234 주문 상태 알려줘" },
  { "type": "ASSISTANT", "content": "2024-1234번 주문의 상태는 배달 중입니다. 예상 도착 시간은 2026년 5월 31일 오후 5시 15분입니다..." }
]
```

**재시작 후 "그거" 질문**

```bash
curl -s -X POST http://localhost:8080/api/v1/assistant -H "Content-Type: application/json" -H "X-Session-Id: file-test" -d '{"message":"그거 언제 도착해요?"}'
```

```
2024-1234번 주문의 예상 도착 시간은 2026년 5월 31일 오후 5시 15분입니다. 안전하게 배달이 이루어질 수 있도록 기다려주시기 바랍니다.
```

> 재시작 후 세션 목록에 `file-test`가 살아있고, "그거"가 1234를 가리켰다. 새 JVM이 `baedal.mv.db`를 열면서 이전 대화 이력을 그대로 가져온 것이다.
>
> 그런데 답변에 "오후 5시 15분"이 그대로 나왔다. 재시작 전에 조회한 시각이다. 재시작 사이에 실제 배달 상황이 바뀌었을 수 있는데, Memory에 저장된 오래된 정보를 꺼내 답한다. 1단계 시나리오 1에서 지적한 문제가 여기서 더 심각해진다. InMemory는 재시작하면 Memory가 사라지니까 이 문제가 자동으로 해소됐다. 파일 영속 Memory를 쓰면 오래된 정보가 계속 살아남아 잘못된 답변을 준다.
>
> 실시간 데이터(배달 위치, 도착 시간)를 Memory에서 재사용하지 않도록 프롬프트를 수정하거나, 배달 완료 시점에 해당 세션 Memory를 지워야 한다.

### 영속성 비교 표

| 저장소 설정 | 재시작 후 Memory 유지? | 비고 |
|---|---|---|
| InMemory (기본) | 아니오 | JVM 힙에만 존재 |
| `jdbc:h2:mem:...` | 아니오 | JVM 생존 동안만 유지, 재시작 시 소실 |
| `jdbc:h2:file:...` | 예 | `baedal.mv.db` 파일이 남아 재시작 후에도 유지 |

> InMemory와 `jdbc:h2:mem`의 재시작 결과가 같다. "JDBC를 쓰면 영속성이 생긴다"는 오해를 가장 직접적으로 깨주는 비교다. 영속성은 저장 방식이 아니라 저장 위치에서 온다.
>
> `jdbc:h2:file`이 처음으로 재시작을 버텼지만, 단일 서버에서만 의미가 있다. 서버가 2대 이상이면 파일이 각각 따로 존재하므로 세션이 어느 서버로 라우팅되느냐에 따라 Memory가 달라진다. 멀티 인스턴스 환경이라면 파일이 아니라 공유 DB가 필요하다.

---

## 설계 결정 문서 — 의사결정 트리

### 저장소 선택 기준표

| 운영 조건 | Yes | No |
|---|---|---|
| 서비스가 로드밸런서 뒤 멀티 인스턴스로 뜨는가? | JDBC (공유 DB 필요) | InMemory 가능 |
| 서버 재시작 후에도 고객 대화가 이어져야 하는가? | JDBC (파일 또는 외부 DB) | InMemory 가능 |
| 법적/감사 이유로 상담 이력을 N년 보관해야 하는가? | JDBC + 외부 DB (백업 필요) | InMemory 가능 |
| 단일 인스턴스 + 세션이 분 단위로 짧은가? | InMemory 충분 | 영속화 검토 필요 |

---

### InMemory로 충분한 3가지 조건

1. **단일 인스턴스**: 로드밸런서 없이 서버 1대로 운영. 세션이 항상 같은 JVM으로 라우팅된다.
2. **짧은 세션 수명**: 배달 상담 세션이 완료되면 이력이 필요 없다. 재시작 간격보다 세션이 훨씬 짧다.
3. **개발/스테이징 환경**: 재시작이 잦고, 이력 영속이 불필요하다.

---

### JDBC가 필요한 3가지 조건

1. **멀티 인스턴스 배포**: 로드밸런서가 요청을 여러 인스턴스로 분산하면 인스턴스 A에 쌓인 Memory가 인스턴스 B에 없다. → 공유 DB 필요
2. **서버 재시작 후 대화 연속성**: 배포 중 재시작, 크래시 후 재기동 시에도 "아까 말씀드린 그 주문" 맥락을 유지해야 한다.
3. **감사 로그 / 컴플라이언스**: 고객 상담 이력을 법적으로 N년 보관해야 하거나, 품질 관리를 위해 대화를 검색·조회해야 할 때.

---

### 배달 실제 운영 시 DB 선택

PostgreSQL을 선택했다.

| 옵션 | 평가 |
|---|---|
| PostgreSQL | Round 4에서 PgVector 사용 예정 → 같은 인스턴스에서 Chat Memory + Vector Search 통합 가능. 트랜잭션, 인덱스, 파티셔닝 성숙도 높음 |
| MySQL | PostgreSQL과 유사하지만 PgVector 미지원 → Round 4 연계 불가 |
| Redis | 빠르지만 영속성 보장이 약함 (AOF/RDB 설정 필요). 상담 이력 장기 보관에 부적합 |
| DynamoDB | 관리형으로 운영 부담 낮음. 단, Spring AI JdbcChatMemoryRepository는 JDBC 기반 — 별도 어댑터 구현 필요 |

PgVector와 같은 DB를 쓰면 Memory(대화 이력)와 RAG(지식 검색)를 하나의 트랜잭션으로 관리할 수 있고, Round 4 작업량이 줄어든다.

---

### JDBC 도입 시 동시에 고려할 비기능 요구사항

| 요구사항 | 내용 |
|---|---|
| **TTL (데이터 만료)** | 세션이 종료된 지 N일이 지난 행은 자동 삭제. 배달 상담 특성상 90일 이내 대부분 종결. 배치 잡 또는 DB 파티션 TTL로 구현 |
| **인덱스** | `conversation_id` 컬럼에 인덱스 필수. `timestamp` 복합 인덱스로 최신 N개 조회 성능 보장. 트래픽이 높으면 파티셔닝(`conversation_id` 해시) 고려 |
| **암호화** | `content` 컬럼에 고객 주소, 전화번호가 포함될 수 있음. 컬럼 레벨 암호화 또는 애플리케이션 레벨 암호화 적용. GDPR/개인정보보호법 대응 |

---

## 회고

### 회고 1 — 저장소가 바뀌어도 LLM에게는 동일하다

시나리오 1을 InMemory와 JDBC에서 각각 실행했을 때 응답 내용이 동일했다. "그거 몇 시에 도착해요?"에서 1234를 꺼내는 방식도, 응답 문장 구조도 차이가 없었다.

LLM은 Memory가 어디에 저장됐는지 알지 못한다. `MessageChatMemoryAdvisor`가 저장소를 추상화하고 프롬프트에 대화 이력을 넣어주므로, 저장소 교체는 LLM 동작에 전혀 영향을 주지 않는다. InMemory에서 JDBC로 전환해도 A/B 테스트 없이 그냥 배포할 수 있다는 뜻이다.

---

### 회고 2 — `jdbc:h2:mem`은 재시작하면 InMemory와 똑같이 사라진다

재시작 실험에서 `jdbc:h2:mem`으로 기동한 서버를 내렸다가 올리면 세션 목록이 `[]`가 됐다. JDBC를 쓰는데도 데이터가 날아간다는 게 처음엔 이상해 보였는데, 생각해보면 당연하다. `mem:` H2는 JVM 메모리에 올라가는 인메모리 DB다. JVM이 꺼지면 DB도 같이 사라진다.

"JDBC를 쓰면 영속성이 생긴다"는 오해가 생기기 쉬운 지점이다. 영속성을 얻으려면 저장 방식(JDBC 드라이버)이 아니라 저장 위치(파일 또는 외부 DB 서버)가 바뀌어야 한다. `mem` → `file`로 URL 한 줄만 바꿔도 차이가 생긴다.

---

### 회고 3 — 파일 H2 재시작 후 고객은 서버가 내려갔는지 알 수 없다

`jdbc:h2:file`로 전환하고 서버를 완전히 내렸다가 다시 올린 뒤 같은 세션 ID로 "그거 언제 도착해요?"를 보냈더니 1234를 정확히 가리켰다. 재시작 전 응답에서 언급된 1234 맥락이 DB 파일에 그대로 남아 있었기 때문이다.

고객 입장에서는 서버가 재시작됐는지 전혀 모른다. 배포나 크래시 복구 이후에도 상담이 자연스럽게 이어지는 것이다. InMemory에서는 재시작 직후 "그거"를 물으면 "어떤 주문을 말씀하시나요?"로 되돌아간다. 이 차이가 운영 환경에서 사용자 경험에 직접 영향을 준다.

---

### 회고 4 — DB에는 TOOL 행이 없다

raw-table로 `SPRING_AI_CHAT_MEMORY`를 직접 조회하니 USER와 ASSISTANT 행만 있었다. Tool을 호출한 세션도 마찬가지였다.

`MessageChatMemoryAdvisor`가 Memory를 저장할 때 TOOL 메시지를 빼는 설계다. Tool 결과는 LLM이 ASSISTANT 응답을 만들 때 이미 소비된다. "2024-1235번 주문이 취소됐습니다"라는 ASSISTANT 응답 안에 Tool 호출의 결과가 녹아들어 있으니 지시 대명사 해결에는 충분하다.

문제가 생기는 건 감사(audit) 목적일 때다. "어떤 Tool을 어떤 파라미터로 몇 번 호출했는지" DB에서 쿼리할 방법이 없다. 이 이력이 필요하면 `PerformanceLoggingAdvisor`를 확장하거나 Tool 메서드에 직접 별도 로그를 남겨야 한다.

---

### 회고 5 — `conversation_id` 컬럼이 세션 격리의 실체다

InMemory에서 세션 격리는 `ConcurrentHashMap<String, List<Message>>`의 key가 담당한다. JDBC에서는 같은 역할을 `conversation_id` 컬럼이 한다.

raw-table을 보면 `jdbc-s1`, `jdbc-A4`, `jdbc-B4` 행들이 한 테이블에 섞여 있지만, 조회는 `WHERE conversation_id = ?`로 필터링된다. 세션이 다르면 같은 테이블 안에 있어도 서로 보이지 않는다.

이 구조가 중요한 이유는 격리 보장의 경계가 어디에 있는지 명확히 보여주기 때문이다. DB 레벨에서는 모든 세션 데이터가 한 테이블에 있다. 격리는 애플리케이션 레벨, 즉 `conversation_id` 파라미터를 올바르게 전달하는 코드 한 줄에 달려 있다. 1단계 회고에서 지적한 것과 같다. 코드가 틀려도 오류가 나지 않고 조용히 오염된다.

---

## 자가 점검 체크리스트

- [x] JDBC 프로필로 `bootRun` 성공 (`schema-h2.sql` 직접 추가로 해결)
- [x] `SPRING_AI_CHAT_MEMORY` 테이블의 실제 행(USER/ASSISTANT)이 JdbcTemplate으로 캡처됨
- [x] 재시작 후 유지 여부가 `mem` vs `file` 조건별로 표에 기록됨
- [x] 의사결정 트리 표 작성
- [x] "InMemory 충분 조건 3개 / JDBC 필요 조건 3개" 작성
