# 🤖 SIGMA 스마트 팩토리 진단 챗봇 서비스 (DANAI)

본 서비스는 스마트 팩토리 플랫폼 **SIGMA**의 제조 공정 데이터(S3 및 RDS)를 비교·분석하여, 장비의 실시간 상태 및 불량 인과관계를 진단하는 AI 엔지니어 에이전트 **DANAI**의 백엔드 서비스입니다. 

---

## 🏗️ 전체 아키텍처 및 데이터 흐름

```mermaid
graph TD
    User([엔지니어]) -->|질문 입력 / 대화| FE[React Frontend :5174]
    FE -->|API Request| BE[Spring Boot Backend :8085]
    BE -->|AWS SDK InvokeAgent| Bedrock[AWS Bedrock Agent :DANAI]
    
    subgraph AWS Bedrock Agent Cloud
        Bedrock -->|도구 판단 & 실행| ActionGroup[Action Group: S3-Data-Fetcher]
        ActionGroup -->|Trigger| Lambda[AWS Lambda Function]
    end

    subgraph Data Sources
        Lambda -->|Raw Logs / Parquet 요약| S3[(Amazon S3)]
        Lambda -->|이상 로그 / 불량 목록| RDS[(Amazon RDS MariaDB)]
        BE -->|대화 내역 / 세션 저장| RDS
    end
```

---

## 🌟 핵심 제공 기능 (Core Features)

1. **자연스러운 한국어 설비 ID 매핑**
   - 엔지니어가 자연어로 질문해도 에이전트가 시스템의 표준 설비 ID(Equipment ID)로 변환하여 S3/RDS 도구를 호출합니다.
   - *예: "세정 설비 1번 데이터 조회해줘" ➔ `EQP-CLEANING-001` 자동 매핑*

2. **양방향 분석 선택 가이드 (Intent Clarification)**
   - 사용자가 모호하게 날짜만 요청한 경우, 의도를 파악하기 위해 2가지 옵션을 제시합니다.
   - ① **실제 센서 데이터 예시 조회** (Raw metrics sample)
   - ② **요약 및 진단 분석 리포트 조회** (Daily summary stats)

3. **데이터 부재 시 지능적인 날짜 추천 (Date Discovery)**
   - S3에 해당 날짜 데이터가 없을 경우, 에러로 멈추지 않고 **실제로 데이터가 존재하는 날짜 목록**을 스캔하여 사용자에게 추천합니다.

---

## ☁️ AWS Agent AI 통합 및 인프라 구조 (가장 중요)

본 프로젝트의 핵심은 **AWS Bedrock Agent**와 **AWS Lambda**를 활용한 서버리스 데이터 수집 및 추론 엔진입니다. 에이전트는 프롬프트 인스트럭션과 Action Group Schema를 바탕으로 스스로 필요한 도구를 판단하여 AWS 인프라의 데이터를 조회합니다.

### 1. Amazon S3 데이터 레이어 (센서 로그)
- **버킷명**: `sigma-310095858382-ap-northeast-2-an`
- **Raw Data (실시간 센서 데이터)**
  - **경로**: `YYYY/MM/DD/` 형태로 저장되는 실시간 설비 JSON 데이터.
  - **보존 정책**: S3 정책상 **최근 30일 이내**의 데이터만 보존됩니다.
- **Summary Data (일일 통계 요약 데이터)**
  - **경로**: `summary-data/date=YYYY-MM-DD/` 형태로 저장되는 Parquet 포맷 통계.
  - **데이터 폴백 (Fallback) 정책**: 오늘 날짜 기준 30일이 초과된 과거 날짜의 데이터를 요청받은 경우, 에이전트는 Raw 데이터가 파기되었음을 인지하고 사용자의 의도와 무관하게 **강제로 Summary Data를 조회**하여 리포트를 제공합니다.

### 2. Amazon RDS 데이터 레이어 (불량 및 이상 로그)
에이전트는 Lambda를 통해 `factory_db` (MariaDB)에 접근하여 다음 테이블들을 교차 분석합니다.
- `equipment_info`: 설비명과 설비 ID의 매핑 정보 제공.
- `defect_info`: 불량 발생 시간, 불량 타입/코드, 원인 공정 및 설비 이력.
- `lot_info`: Lot 품질 등급 판정 이력.
- `anomaly_log`: 규칙 기반 또는 이상 탐지 모델이 판별한 레시피 이탈 로그.
- **교차 상관 분석(Correlation Analysis)**: 특정 설비의 이상 로그(Anomaly Log) 발생 시점 기준 **±30분 이내**에 발생한 불량(Defect) 내역을 Join하여, 이상 수치와 실제 불량 간의 직접적인 인과관계를 에이전트가 추론합니다.

### 3. 골든 셋 (Golden Set & Scenarios)
현재 정상 동작이 검증된 주요 프로젝트 골든 셋 시나리오는 다음과 같습니다:
- **시나리오 A**: `포토 공정 4번 분석` / `5월 26일 데이터 보여줘`
  - S3에서 `EQP-PHOTO-004`의 Raw JSON 로그 또는 Parquet 요약 로그를 가져와 정상 여부와 수치 대조.
- **시나리오 B**: `세정 1번 이상 분석`
  - RDS에서 `EQP-CLEANING-001`의 Anomaly Log와 Defect Info를 시간순 교차 분석하여 불량 원인 추론.

### 4. 미구현 및 향후 과제 (TBD)
- **레시피 추천 AI**: 프론트엔드에 UI는 존재하나 현재 "PATCHING(준비 중)" 상태로 연동되어 있지 않음. 이상 패턴을 분석해 최적의 파라미터 보정 수치를 역으로 제안하는 로직 추가 필요.
- **다중 에이전트 협업**: 현재 단일 DANAI 에이전트가 모든 처리를 담당하나, 향후 공정별 특화 에이전트를 도입한 Multi-Agent 라우팅 도입 고려.

---

## 📂 프로젝트 구조

```
C:/inspire/404factory/
├── chatbot-service/         
│   ├── chatbot_service/     # Spring Boot Backend (현재 위치, 핵심 API 및 엔티티)
│   │   ├── src/main/java/...
│   │   ├── src/main/resources/application.yml
│   │   ├── .env             # AWS Bedrock/DB 환경변수
│   │   └── build.gradle
│   └── lambda_src/          # AWS Lambda Python 소스코드 (S3/RDS 연동 로직)
│       └── lambda_function.py 
├── frontend/                # React / Vite Frontend (챗봇 UI)
│   ├── src/pages/ChatbotPage.tsx 
│   └── package.json
└── ... (기타 MSA 서비스)
```
> **참고**: README 문서는 `chatbot-service/chatbot_service` 내부에만 존재하며, 프론트엔드는 `404factory/frontend`에서 별도로 관리됩니다.

---

## ⚙️ 실행 및 로컬 구동 가이드

### 1. 환경 변수 구성 (.env)
백엔드 루트 경로(`chatbot-service/chatbot_service/.env`)에 아래와 같이 DB 및 AWS 에이전트 자격 증명을 작성합니다.
```env
# Database Credentials
DB_URL=jdbc:mariadb://factory-db.c5g4a4ekcfvb.ap-northeast-2.rds.amazonaws.com:3306/factory_db?useSSL=true&trustServerCertificate=true
DB_USERNAME=root
DB_PASSWORD=12345678

# AWS Bedrock Agent Credentials
AWS_AGENT_ID=SHFTOIN2IV
AWS_AGENT_ALIAS_ID=MTN5THYODH
```

### 2. 백엔드 실행 (Spring Boot)
Spring Boot 백엔드 폴더로 이동한 후 Gradle 부트런 명령어로 서버를 실행합니다. (포트: `8085`)
```bash
cd C:/inspire/404factory/chatbot-service/chatbot_service
./gradlew bootRun
```

### 3. 프론트엔드 실행 (React)
프론트엔드 폴더로 이동하여 패키지를 설치하고 개발 서버를 가동합니다. (포트: `5174`)
```bash
cd C:/inspire/404factory/frontend
pnpm install
pnpm run dev
```

---

## 🌐 주요 API 사양

### 1. AI 진단 분석 요청
- **Endpoint**: `POST /api/chat/insight`
- **Request Body**:
  ```json
  {
    "equipmentId": 4, // 특정 설비 번호 (null 허용)
    "content": "포토 공정 4번 2026-05-26 데이터 보여줘",
    "roomId": "room-session-uuid"
  }
  ```
- **Response**:
  ```json
  {
    "reply": "데이터 기준 시각: 2026년 05월 26일 ... \n\n1. 진단 결과 ... \n2. 근거 데이터 ... \n3. 권장 조치 ..."
  }
  ```

### 2. 대화 기록 관리
- **대화방 목록 조회**: `GET /api/chat/rooms`
- **대화 내역 상세 조회**: `GET /api/chat/rooms/{roomId}/messages`
- **대화방 삭제**: `DELETE /api/chat/rooms/{roomId}`
