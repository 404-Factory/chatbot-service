# SIGMA 스마트 팩토리 챗봇 서비스

이 프로젝트는 **스프링 부트(Spring Boot)** 기반의 백엔드 애플리케이션으로, 스마트 팩토리 공정 데이터를 분석하는 **AI 챗봇 서비스**를 제공합니다. AWS Bedrock과 연동하여 공정 이상 진단 및 레시피 추천 결과를 생성하며, **RDS(마리아DB)**와 **S3** 데이터 연동을 통해 실시간 설비 상태와 불량/이상 로그를 비교 분석합니다.

## 주요 목적

- 공정 설비별 AI 진단 대화(Insight) 제공
- 설비별 레시피 추천 및 불량 원인 분석
- 채팅 방, 메시지 기록 저장 및 조회
- AWS Bedrock Agent 기반 대화 및 추천 모델 통합
- S3 실시간 센서 로그와 RDS 이상/불량 데이터를 연계

## 프로젝트 구조 요약

```
src/main/java/com/factory/chatbot_service/
  ├── config/              # AWS Bedrock, S3, CORS 등 설정
  ├── controller/          # REST API 엔드포인트
  ├── dto/                 # 요청/응답 데이터 객체
  ├── entity/              # JPA 엔티티 매핑
  ├── repository/          # Spring Data JPA 리포지토리
  └── service/             # 비즈니스 로직 및 Bedrock 연동
src/main/resources/
  ├── application.yml      # 기본 환경 설정
  ├── application-dev.yaml
  ├── application-local.yaml
  └── application-prod.yaml
build.gradle               # Gradle 빌드 및 의존성
ChatbotServiceApplication.java  # 앱 진입점, .env 파일 로드
```

## 핵심 컴포넌트

### 1. `ChatbotServiceApplication`

- Spring Boot 애플리케이션 진입점
- 루트 폴더의 `.env` 파일을 읽어 JVM 시스템 속성으로 등록
- 로컬 개발 환경에서도 환경 변수를 편리하게 관리할 수 있도록 지원

### 2. `AwsBedrockConfig`

- AWS Bedrock Agent 및 S3 클라이언트를 생성
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` 등을 환경 변수나 시스템 속성에서 자동 탐지
- Bedrock API 호출 타임아웃, HTTP 연결 타임아웃 등을 구성

### 3. `ChatbotController`

주요 REST API를 제공하는 컨트롤러입니다.

- `POST /api/chat/insight` : 설비 분석 요청 및 AI 응답 생성
- `POST /api/chat/message` : 채팅 메시지 저장
- `GET /api/chat/rooms` : 전체 채팅 방 목록 조회
- `GET /api/chat/rooms/{roomId}/messages` : 특정 방 메시지 조회
- `DELETE /api/chat/rooms/{roomId}` : 채팅 방 삭제
- `POST /api/chat/recipe` : 자연어 기반 레시피 추천 대화

### 4. `RecipeRecommendationController`

- `POST /api/ai/recipe/recommend` : JSON 기반 레시피 추천 요청 처리
- `GET /api/ai/recipe/version` : 컨트롤러 버전 확인

### 5. `InternalRecipeRecommendationController`

- `POST /api/internal/recipe/recommend` : 내부 Action Group 또는 시스템 연동용 추천 API

### 6. `MainInsightService`

- 사용자의 질문을 도메인 필터링하여 공정 관련 질문인지 확인
- 설비 ID가 없는 요청은 재질문 또는 예외 처리
- `equipmentId`가 있으면 `AnomalyLog`, `DefectInfo`, `EquipmentInfo` 데이터를 조회하여 Bedrock Agent에 컨텍스트 전달
- 채팅방과 메시지를 `ChatRoomRepository`, `ChatMessageRepository`에 저장

### 7. `RecipeChatService`

- 자연어 메시지에서 설비 ID와 불량 유형을 추출
- 백엔드 추천(로컬 계산)과 Bedrock LLM 후보 생성을 함께 수행
- 추천 의도가 아닌 경우에는 안전하게 예외 응답 반환
- 추천 결과를 `ChatDto.Response` 형태로 반환

### 8. `RecipeRecommendationService`

- `RecipeRecommendDto.Request`를 기반으로 공정/제품/설비 컨텍스트를 해석
- `RecipeContextResolver`, `SensorContextProvider`, `RecipeHistoryProvider`를 통해 추천 컨텍스트 구성
- Bedrock Agent 호출 결과를 검증하고, 안전성 점검 후 최종 결과 생성
- 로컬 추천 모델을 통해 백엔드만으로도 응답 생성 가능

## 주요 데이터 모델

### 엔티티

- `ChatRoom` : 채팅방 정보
- `ChatMessage` : 채팅 메시지 이력
- `AnomalyLog` : 이상 탐지 로그
- `DefectInfo` : 불량 정보
- `EquipmentInfo` : 설비 메타 정보

### DTO

- `ChatDto` : 챗봇 대화 요청/응답
- `RecipeRecommendDto` : 레시피 추천 요청/응답
- `RecipeAgentDto` : Bedrock Agent에 전달되는 추천 컨텍스트
- `RecipeRecommendationContext`, `SensorContext`, `RecipeHistoryCase` 등 : 추천 의사결정에 필요한 컨텍스트 객체

## AI 답변 흐름: Bedrock Agent 기반 인사이트 생성

### 1. `POST /api/chat/insight` 요청 처리

- `ChatbotController`는 `MainInsightService.getEquipmentAnalysis()`를 호출합니다.
- `MainInsightService`는 먼저 자연어 질문이 공정/설비/레시피 도메인과 관련 있는지 필터링합니다.
- `equipmentId`가 없으면 일반적인 질문으로 간주하고 Bedrock Agent에 직접 질문을 전송합니다.
- `equipmentId`가 있는 경우, `AnomalyLog`와 `DefectInfo`, `EquipmentInfo` 데이터를 조회하여 Bedrock Agent가 분석에 사용할 컨텍스트를 구성합니다.
- `BedrockAgentService.askInsightAI()`는 구성된 프롬프트를 Bedrock 에이전트로 전달하고, 에이전트가 생성한 분석 결과를 받아옵니다.

### 2. 프롬프트 구성과 컨텍스트

- `MainInsightService`는 최근 이상 로그 데이터를 `AnomalyLogRepository`에서 최대 5건까지 조회합니다.
- 동일 설비의 불량 데이터(`DefectInfo`)도 함께 불러와, 에이전트가 이상 징후와 불량 인과관계를 해석할 수 있도록 합니다.
- 프롬프트는 다음 정보를 포함합니다:
  - 최근 이상 감지 항목과 탐지 규칙
  - 불량 발생 유형, 발생 공정, 발생 시각
  - 사용자의 추가 질문
- 결과적으로 Bedrock Agent는 단순한 챗봇 답변이 아니라, 실제 RDS 기반 공정 분석 데이터를 참고한 진단형 답변을 생성합니다.

### 3. Bedrock Agent와 AWS SDK

- `AwsBedrockConfig`에서 `BedrockAgentRuntimeAsyncClient`와 `BedrockRuntimeClient`를 빈으로 생성합니다.
- 이 설정은 Bedrock 요청의 타임아웃, 연결 타임아웃, 재시도 관련 값을 제어합니다.
- AWS 자격 증명은 `.env` 파일 또는 환경 변수에서 불러오고, 없을 경우 `DefaultCredentialsProvider`를 사용합니다.
- `POST /api/chat/insight` 요청이 성공하면 최종 AI 답변은 `ChatMessage`로 저장되어 채팅 기록에 남습니다.

## 레시피 추천 로직 상세 설명

### 1. 자연어 입력에서 추천 의도 파악

- `POST /api/chat/recipe`는 `RecipeChatService.chat()`에서 처리됩니다.
- `RecipeChatService`는 메시지에서 설비 ID와 불량 유형을 추출합니다.
- 주요 역할:
  - `EQUIPMENT_ID_PATTERN` 정규식을 사용해 `설비 1번`, `EQP-...` 형태를 모두 인식
  - `defectType` 또는 `불량 유형`을 추출
  - 추천 의도를 판단하는 키워드를 탐지하여 레시피 추천 요청인지 확인
- 설비 ID가 없거나 추천 의도가 없으면 명확한 안내 메시지를 반환합니다.

### 2. 추천 컨텍스트 해결

- `RecipeRecommendationService`는 `RecipeContextResolver`를 통해 다음을 이해합니다:
  - 설비 ID에 대응하는 공정(Process)
  - 현재 동작 중인 레시피와 파라미터
  - 해당 제품과 공정에 대한 최근 히스토리
- `SensorContextProvider`는 `S3` 센서 데이터를 가져와 최신 센서 스냅샷을 구성합니다.
- `RecipeHistoryProvider`는 과거 유사 사례를 `RDS`에서 조회하여 추천 근거를 만듭니다.

### 3. Bedrock Agent와 로컬 추천 분리

- `RecipeRecommendationService.recommend()`는 두 가지 추천 경로를 모두 지원합니다.
  1. **Bedrock Agent 추천 경로**: `RecipeAgentClient`가 구성한 요청을 Bedrock으로 전송하고, AI가 생성한 레시피 조정 결과를 수신합니다.
  2. **로컬 추천 경로**: `recommendLocally()`는 백엔드에서 안전하게 추천 값을 계산합니다.

- 로컬 추천은 다음 방식으로 동작합니다:
  - 현재 레시피 파라미터와 최신 센서 스냅샷을 비교
  - 역사적 사례와 센서 기준 범위를 바탕으로 `RecipeParameterValue`를 조정
  - 안전 검증(`RecipeSafetyService`)을 거쳐 권장값을 확정

### 4. 추천 후보 검증 및 최종 선택

- `RecipeChatService`는 먼저 백엔드 추천을 기준으로 사용합니다.
- 백엔드 추천이 `SUCCESS`일 때만 `BedrockRecipeCandidateService`가 LLM 후보를 생성합니다.
- 이후 `RecommendationSelectionService`가 백엔드 추천과 LLM 후보를 비교하여 더 안전하고 신뢰 가능한 결과를 선택합니다.
- 최종 결과는 `BedrockRecipeAnswerService`가 자연어 설명으로 변환한 뒤 `ChatDto.Response`로 반환됩니다.

### 5. REST API와 내부 Action Group

- `RecipeRecommendationController`는 외부 JSON 요청을 처리하는 정형화된 엔드포인트입니다.
- `InternalRecipeRecommendationController`는 내부 시스템 또는 Bedrock Action Group에서 직접 사용할 수 있는 추천 API를 제공합니다.
- 이 두 경로 모두 `RecipeRecommendationService`의 추천 로직을 재사용하므로, 일관된 추천 결과를 유지합니다.

## 시스템 동작 요약

### 인사이트 대화(`POST /api/chat/insight`)

1. 사용자 질문 수신
2. 도메인 필터링 및 설비 ID 검증
3. RDS 이상 로그/불량 정보 조회
4. Bedrock Agent에 컨텍스트와 질문 전달
5. AI 분석 답변 수신 및 채팅 기록 저장

### 레시피 추천(`POST /api/chat/recipe` 또는 `/api/ai/recipe/recommend`)

1. 자연어에서 설비 ID 및 불량 유형 추출
2. 추천 의도 판별
3. 공정/제품/센서/히스토리 컨텍스트 구성
4. 로컬 추천 계산 또는 Bedrock Agent 추천 수행
5. 안전 검증 및 후보 선택
6. 자연어 설명 응답 생성

## 실행 방법

### 요구 사항

- Java 17
- Gradle Wrapper(`./gradlew`) 사용
- MariaDB 또는 MySQL 호환 데이터베이스
- AWS 자격 증명 및 Bedrock 접근 권한

### 로컬 실행

```bash
./gradlew bootRun
```

### 테스트 실행

```bash
./gradlew test
```

## 환경 변수 및 설정

`src/main/resources/application.yml`는 다음 환경 변수를 사용하여 외부 설정을 주입합니다.

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_MAX_POOL_SIZE` (옵션)
- `DB_MIN_IDLE` (옵션)
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_AGENT_ID`
- `AWS_AGENT_ALIAS_ID`
- `BEDROCK_AGENT_ID`
- `BEDROCK_AGENT_ALIAS_ID`
- `BEDROCK_MODEL_ID`
- `S3_BUCKET_NAME`
- `S3_REALTIME_PREFIX`
- `S3_REALTIME_LOOKBACK_DAYS`
- `S3_ALLOW_BROAD_REALTIME_SCAN`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

### 예시 `.env`

```env
DB_URL=jdbc:mariadb://localhost:3306/your_database
DB_USERNAME=your_user
DB_PASSWORD=your_password
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
AWS_AGENT_ID=bedrock-agent-id
AWS_AGENT_ALIAS_ID=bedrock-agent-alias-id
BEDROCK_MODEL_ID=anthropic.claude-3-haiku-20240307-v1:0
S3_BUCKET_NAME=your-bucket-name
S3_REALTIME_PREFIX=sensor
S3_REALTIME_LOOKBACK_DAYS=3
CORS_ALLOWED_ORIGIN_PATTERNS=*
```

## REST API 예시

### 설비 인사이트 질의

```http
POST /api/chat/insight
Content-Type: application/json

{
  "equipmentId": 4,
  "content": "포토 4번 설비 최근 이상 징후 알려줘",
  "roomId": "room-123"
}
```

### 채팅 메시지 저장

```http
POST /api/chat/message
Content-Type: application/json

{
  "roomId": "room-123",
  "role": "USER",
  "content": "안녕하세요",
  "title": "새 채팅"
}
```

### 레시피 추천 자연어 대화

```http
POST /api/chat/recipe
Content-Type: application/json

{
  "sessionId": "session-123",
  "message": "설비 1번의 Scratch 불량을 줄일 레시피를 추천해줘"
}
```

### JSON 레시피 추천

```http
POST /api/ai/recipe/recommend
Content-Type: application/json

{
  "equipmentId": "1",
  "defectType": "Scratch"
}
```

## AWS Bedrock 연동

- `AwsBedrockConfig`는 `BedrockAgentRuntimeAsyncClient`와 `BedrockRuntimeClient`, `S3Client`를 빈으로 등록합니다.
- `chatbot.aws.region`과 `chatbot.bedrock.*` 설정을 통해 Bedrock 호출 동작을 제어합니다.
- AWS 크레덴셜이 `.env` 또는 시스템 환경에 없으면 `DefaultCredentialsProvider`를 사용합니다.

## 개발 참고

- `MainInsightService`는 도메인 외 질문을 필터링하고, 설비 ID가 존재할 때만 `BedrockAgentService`를 호출합니다.
- `RecipeChatService`는 자연어 메시지에서 설비 ID와 불량 유형을 추출하여 레시피 추천 의도를 판단합니다.
- `RecipeRecommendationService`는 Bedrock 추천 결과를 안전성 검증 후 응답으로 변환합니다.
- `application.yml`의 `spring.jpa.hibernate.ddl-auto=validate`로 인해 스키마가 데이터베이스와 일치해야 합니다.

## 주요 의존성

- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter JDBC
- MyBatis Spring Boot Starter
- AWS SDK for Bedrock Agent Runtime
- AWS SDK for S3
- MariaDB JDBC Driver
- Lombok

## 요약

이 프로젝트는 AWS Bedrock AI와 스마트 팩토리 센서/이상/불량 데이터를 결합하여
- 설비 상태 진단
- 이상 원인 탐지
- 레시피 추천
- 대화형 AI 챗봇 인터페이스
를 제공하는 백엔드 서비스입니다.

코드 이해를 빠르게 하기 위해서는 먼저 `controller` → `service` → `repository/entity` 흐름을 따라가고, `AwsBedrockConfig`와 `application.yml`의 환경 설정 규칙을 함께 확인하는 것이 좋습니다.
