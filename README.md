# 🤖 SIGMA 스마트 팩토리 진단 챗봇 서비스 (DANAI)

본 서비스는 스마트 팩토리 플랫폼 **SIGMA**의 제조 공정 데이터(S3 및 RDS)를 비교·분석하여, 장비의 실시간 상태 및 불량 인과관계를 진단하는 AI 엔지니어 에이전트 **DANAI**의 백엔드 및 프론트엔드 모듈입니다.

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

## 🌟 핵심 제공 기능 및 구현 아키텍처

### 1. 자연스러운 한국어 설비 ID 매핑
- 엔지니어가 자연어(구어체)로 질문해도 에이전트가 시스템의 표준 설비 ID(Equipment ID)로 변환하여 S3/RDS 도구를 호출합니다.
  - *예: "세정 설비 1번 데이터 조회해줘" ➔ `EQP-CLEANING-001` 자동 매핑*
  - *예: "포토 공정 4번" ➔ `EQP-PHOTO-004` 자동 매핑*

### 2. 1개월 Raw 데이터 보존 정책 및 자동 폴백 (Fallback)
- S3의 실시간 센서 로그(Raw JSON) 보존 정책(1개월)에 맞추어 지능적으로 데이터 조회를 분기 처리합니다.
  - **최근 30일 이내 날짜**: 사용자의 선택에 따라 **실시간 Raw 센서 로그** 샘플 또는 **요약 데이터**를 자유롭게 조회할 수 있습니다.
  - **30일 초과 과거 날짜**: Raw 센서 데이터가 파기되었음을 인지하고, 사용자의 의도와 상관없이 자동으로 **일일 요약 통계(Summary Parquet) 데이터**를 조회하여 진단 분석 리포트를 작성합니다.
  - **[업데이트] 경고 조건 고도화**: 실제로 30일이 초과된 과거 데이터를 강제로 요약 조회할 때에만 최상단에 규정 안내 경고문("해당 날짜는 30일이 지난 데이터이므로 Raw 로그가 삭제되어...")을 출력하며, 최근 날짜 조회 시에는 해당 안내가 노출되지 않도록 최적화되었습니다.

### 3. 데이터 부재 시 지능적인 날짜 추천 (Date Discovery)
- 사용자가 조회하려는 날짜에 데이터가 적재되어 있지 않은 경우, 단순 에러로 종료하지 않고 **S3에 실제 데이터가 존재하는 날짜 목록**을 스캔하여 사용자에게 대안으로 제시하고 재조회를 유도합니다.
  - *예: "죄송합니다. 2026-05-25에는 데이터가 존재하지 않습니다. 현재 조회 가능한 날짜는 2026-05-26, 2026-05-27 등입니다. 다른 날짜를 조회하시겠습니까?"*

### 4. 양방향 분석 선택 가이드 (Intent Clarification)
- 사용자가 날짜 정보만 모호하게 요청한 경우(예: "5월 27일 데이터 보여줘"), 사용자의 실제 니즈를 파악하기 위해 두 가지 조회 옵션을 제공하는 선택식 메뉴를 출력합니다.
  1. **실제 센서 데이터 예시 조회** (Raw metrics sample)
  2. **요약 및 진단 분석 리포트 조회** (Daily summary stats & anomaly correlation)

---

## 🎨 프론트엔드 마크다운 렌더링 최적화

챗봇 답변 내 마크다운 문법(볼드 `**`, 리스트 `-`, 헤더 `#`)이 일반 텍스트로 보이지 않고 직관적으로 표시될 수 있도록 렌더링 기능을 강화했습니다.

- **기술 스택**: `react-markdown` (v10.1.0) 기반에 `remark-gfm`(GitHub Flavored Markdown) 및 `remark-breaks`(엔터 줄바꿈 보존) 플러그인을 도입했습니다.
- **테마 맞춤형 스타일링 (Tailwind CSS)**:
  - **강조구문 (`**`)**: 어두운 테마 대비 가독성을 위해 에이전트 답변 내 강조 텍스트에는 미래지향적인 사이언 컬러(`text-cyan-300`)를 적용하고, 사용자 말풍선 내에서는 흰색(`text-white`)으로 일관되게 렌더링합니다.
  - **글머리/번호 목록 (`-`, `1.`)**: 적절한 들여쓰기(`pl-5`)와 커스텀 마커 컬러(`text-cyan-400`)를 매핑하여 계층적 데이터 분석이 한눈에 파악됩니다.
  - **줄 바꿈 및 단락**: 단락 간의 간격과 원시 줄 바꿈 규칙을 그대로 유지하여 긴 진단 리포트가 질서정연하게 표시됩니다.

---

## 📂 프로젝트 구조

```
C:/inspire/404factory/chatbot-service/
├── chatbot_service/         # Spring Boot Backend (Git Repository Root)
│   ├── src/main/java/...    # JPA Entities, Repositories, Controllers, Services
│   ├── src/main/resources/  # application.yml 설정 파일
│   ├── .env                 # AWS Bedrock 및 DB 연결용 로컬 환경변수 파일
│   └── build.gradle         # Gradle 빌드 스크립트
├── frontend/                # React / Vite Frontend (Git Repository Root)
│   ├── src/                 # React 컴포넌트, 챗 페이지 및 스타일시트
│   ├── package.json         # npm 패키지 의존성
│   └── vite.config.ts       # Vite 설정 파일
└── README.md                # 전체 서비스 통합 안내 파일
```

---

## ⚙️ 실행 및 로컬 구동 가이드

### 1. 환경 변수 구성 (.env)
백엔드 루트 경로(`chatbot_service/.env`)에 아래와 같이 DB 및 AWS 에이전트 자격 증명을 작성합니다.
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
백엔드 폴더로 이동한 후 Gradle 부트런 명령어로 서버를 실행합니다. (기본 포트: `8085`)
```bash
cd chatbot_service
./gradlew bootRun
```

### 3. 프론트엔드 실행 (React)
프론트엔드 폴더로 이동하여 패키지를 설치하고 개발 서버를 가동합니다. (포트: `5174`)
```bash
cd frontend
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
    "content": "세정 설비 1번 2026-05-27 데이터 보여줘", // 유저 프롬프트
    "roomId": "room-session-uuid" // 세션 유지용 대화방 ID
  }
  ```
- **Response**:
  ```json
  {
    "reply": "데이터 기준 시각: 2026년 05월 27일 ... \n\n1. 진단 결과 ... \n2. 근거 데이터 ... \n3. 권장 조치 ..."
  }
  ```

### 2. 대화 기록 관리
- **대화방 목록 조회**: `GET /api/chat/rooms`
- **대화 내역 상세 조회**: `GET /api/chat/rooms/{roomId}/messages`
- **대화방 삭제**: `DELETE /api/chat/rooms/{roomId}`
