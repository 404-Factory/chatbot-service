# Recipe Recommendation AI 상세 보고서

## 1. Recipe Recommendation AI 개요 

### 1.1 개요

Recipe Recommendation AI는 제조 공정에서 발생하는 생산 조건, 설비별 레시피, 센서 측정값, 과거 생산 이력 및 불량 데이터를 종합적으로 분석하여 최적의 공정 레시피를 추천하는 인공지능 기반 의사결정 지원 기능이다.

본 기능은 단순히 사용자가 입력한 조건에 따라 고정된 값을 반환하는 방식이 아니라, 현재 생산 중인 설비와 제품, 공정 정보를 기준으로 실제 운영 데이터베이스(RDS - MariaDB)에 저장된 레시피 정보, 생산 이력, 불량 정보를 조회하고, AWS S3에 저장된 최신 센서 데이터를 함께 반영하여 추천 결과를 생성한다. 이후 AI가 생성한 추천값이 공정 안전 범위를 벗어나지 않는지 백엔드에서 한 번 더 검증함으로써, 현장 적용 가능성과 신뢰성을 높였다.

즉, Recipe Recommendation AI는 **"현재 설비 상태에서 어떤 공정 조건을 조정하면 불량률을 �낝 수 있는가?"**라는 질문에 대해 데이터 기반의 추천 레시피, 변경해야 할 파라미터, 기대 효과를 제공하는 기능이다.

---

### 1.2 개발 목적

Recipe Recommendation AI는 제조 공정의 센서 데이터, 생산 이력, 불량 데이터를 분석하여 불량률 감소에 도움이 되는 공정 레시피를 추천하는 AI 기반 의사결정 지원 기능이다.

기존에는 작업자의 경험과 과거 문서에 의존하여 레시피를 조정해야 했으나, 본 시스템은 RDS와 S3 데이터의 통합 분석을 통해 보다 객관적이고 일관된 의사결정을 지원한다.

더욱 구체적으로, 본 시스템은 다음과 같은 방식으로 작동한다:

1. **RDS 데이터 조회**: `RecipeContextResolver`는 MariaDB의 생산 컨텍스트 테이블에서 현재 설비, 제품, 공정에 대응하는 기준 레시피 및 파라미터 안전 범위를 조회한다.
2. **S3 센서 데이터 통합**: `SensorContextProvider`는 AWS S3에 저장된 최신 센서 측정값을 조회하여, 현재 설비의 실시간 상태를 반영한다.
3. **과거 이력 분석**: `RecipeHistoryProvider`는 과거 저불량 사례의 레시피를 RDS에서 조회하여 추천 근거를 제공한다.
4. **백엔드 검증**: `RecipeSafetyService`는 권장값이 안전 범위를 벗어나지 않는지 검증하고, `LlmCandidateValidationService`는 AI 후보의 신뢰도와 범위 변경의 합리성을 점검한다.

또한 백엔드에서 계산한 추천값에 대해 AWS Bedrock LLM 모델로 추가 후보를 생성하고, 이 후보가 안전 기준을 만족하는지 재검증함으로써 최종 결과물의 신뢰성과 현장 적용 가능성을 확보하였다.

---

### 1.3 해결하려는 문제

제조 공정에서는 다양한 공정 파라미터가 품질에 영향을 미치기 때문에 작업자가 최적의 조건을 판단하기 어렵다.

특히 다음과 같은 문제가 존재한다:

1. **분산된 데이터**: 레시피, 센서, 생산 이력이 RDS와 S3에 분산되어 있어 통합 분석이 어려움
2. **현장 맥락 부재**: 작업자가 현재 설비 상태와 과거 성공 사례를 함께 고려하여 의사결정하기 어려움
3. **AI 추천 신뢰성**: AI가 생성한 파라미터 값이 공정 안전 기준을 만족하는지, 또는 과도하게 급격한 변화를 요구하지 않는지 검증 필요

본 시스템은 **데이터 수집 → 로컬 추천 생성 → Bedrock LLM 후보 생성 → 안전성 검증 → 최종 결과 제공** 과정을 통해 이러한 문제를 해결하도록 설계하였다.

---

### 1.4 입력 데이터

Recipe Recommendation AI는 다음 데이터를 종합적으로 활용한다:

| 데이터 구분 | 출처 | 활용 목적 |
|-----------|------|---------|
| **요청 정보** | HTTP Request (RecipeRecommendDto.Request) | 사용자 질문, 설비ID, 불량유형 등 직접 입력 정보 |
| **생산 컨텍스트** | RDS (lot_info 테이블) | 설비에 대응하는 공정(Process), 제품(Product) 조회 |
| **레시피 정보** | RDS (equipment_recipe, equipment_recipe_detail) | 현재 공정의 기준 파라미터 범위 및 마스터 레시피 조회 |
| **불량 데이터** | RDS (defect_info 테이블) | 최근 불량 유형 파악 및 저불량 사례 분석 |
| **생산 이력** | RDS (생산 이력 데이터) | 과거 저불량 레시피 성과 분석 및 추천 근거 도출 |
| **센서 데이터** | AWS S3 (최신 센서 로그) | 현재 설비 상태 반영 및 센서 평균값 계산 |

**RecipeRecommendDto.Request의 주요 필드:**
- `equipmentId` (필수): 설비 ID
- `defectType`: 불량 유형 (미제공 시 RDS에서 최근값 자동 조회)
- `processId`, `productId`: 공정/제품 ID (미제공 시 RDS lot_info에서 자동 조회)
- `currentRecipe`: 현재 레시피 파라미터 (RecipeParameter 객체, 선택사항)
- `insightContext`, `sensorContext`: 추가 컨텍스트 정보

**데이터 수집 프로세스:**
1. `RecipeContextResolver`: RDS 조회를 통해 processId, productId, masterRecipeId, 기본 defectType 자동 결정
2. `SensorContextProvider`: S3에서 최신 센서 스냅샷 조회 및 평균값 계산
3. `RecipeHistoryProvider`: RDS에서 해당 설비/제품/공정의 과거 저불량 레시피 조회
4. `RecipeContextResolver.findCurrentRecipeParameters()`: RDS equipment_recipe_detail에서 파라미터별 안전 범위(min/max) 조회

이를 통해 단순 규칙 기반 추천이 아닌 실제 생산 환경을 반영한 데이터 기반 추천이 가능하도록 하였다.

---

### 1.5 출력 결과

최종 출력은 `RecipeRecommendDto.Response` 객체로 다음 항목을 포함한다:

| 필드명 | 타입 | 설명 |
|-------|------|------|
| **status** | String | 추천 성공 여부 (`SUCCESS`, `DATA_INSUFFICIENT`, `AGENT_UNAVAILABLE`, `UNSAFE_RECOMMENDATION`, `BAD_REQUEST` 등) |
| **summary** | String | 추천 결과 요약 문구 |
| **recommendedRecipe** | RecipeParameter | 고정 파라미터 형태의 추천 레시피 (temperature, pressure, speed, duration) |
| **recommendedParameters** | List<RecipeParameterValue> | 파라미터별 상세 추천 범위 및 단위 |
| **expectedEffect** | ExpectedEffect | 예상 개선 효과 (targetMetric, direction, description) |
| **evidence** | List<String> | 추천 근거 (데이터 소스, 참고 사항) |
| **warnings** | List<String> | 주의사항 또는 신뢰도 제한 사유 |
| **confidence** | Double | 추천 신뢰도 (0.0 ~ 1.0) |

**RecipeParameterValue의 필드:**
- `name`: 파라미터명 (예: "Temperature", "Pressure")
- `min`, `max`: RDS의 안전 범위
- `currentValue`: 현재 값
- `recommendedMin`, `recommendedMax`: 추천 범위
- `recommendedValue`: 추천값 (범위의 중앙값)
- `unit`: 단위

Recipe Recommendation AI의 출력 결과는 작업자가 실제로 검토할 수 있는 형태로 구성된다. 단순히 숫자만 반환하는 것이 아니라, 추천 레시피, 변경 파라미터, 예상 효과, 근거, 경고, 신뢰도를 함께 제공한다.

**시스템이 보장하는 안전성 및 현실성:**

1. **안전 범위 검증**: 모든 추천값은 RDS에서 정의한 파라미터 안전 범위를 벗어나지 않도록 제한된다. 예: 온도는 160~190°C, 압력은 2.0~3.0, 속도는 90~140, 지속시간은 30~120 범위 내.
2. **점진적 변화**: 한 번에 지나치게 큰 폭으로 변경되지 않도록 기존 안전 범위 대비 **35% 이내 범위 변경**만 허용된다. 이는 `LlmCandidateValidationService.isStepAllowed()` 메서드에서 검증된다.
3. **과거 데이터 반영**: 추천 범위는 현재 센서 평균값과 과거 저불량 사례의 레시피 범위를 최대 65% 까지 점진적으로 반영하여 계산된다.

이를 통해 추천 결과가 이론적으로만 가능한 값이 아니라, 실제 공정 검토에 사용할 수 있는 현실적인 범위가 되도록 설계하였다.

**기대 효과와 신뢰도:**

- `expectedEffect`는 확정적인 결과가 아니라, 과거 이력과 현재 센서 상태를 기반으로 한 **예측**이다.
- `confidence` 값은 사용 가능한 데이터의 양과 품질, 그리고 센서 데이터와 과거 이력의 일관성을 반영한다.
- `warnings`는 센서 데이터 부재, 과거 히스토리 부족, 또는 현재 설비 상태가 예상 범위 밖일 때 작업자에게 주의를 환기한다.

따라서 작업자는 최종 판단을 위해 제공된 추천값, 근거, 경고, 신뢰도를 함께 검토해야 한다.

---

## 2. Recipe Recommendation AI 아키텍처

백엔드는 먼저 안전한 기준 추천(백엔드 추천)을 생성하고, 필요 시 LLM이 만든 후보 추천을 생성한다. LLM 후보는 `LlmCandidateValidationService`의 검증을 통과할 때만 최종 추천에 반영되며, LLM이 단독으로 최종 레시피를 결정하지 않는다. 즉, 항상 백엔드 추천이 기준이고 LLM 후보는 검증 후 보조 역할을 수행한다.

### 2.1 전체 흐름 (간결)

사용자 요청 → `RecipeRecommendationController` → `RecipeRecommendationService.resolveRecommendationInput()` → RDS/S3 조회(`RecipeContextResolver`, `SensorContextProvider`, `RecipeHistoryProvider`) → 백엔드 기준 추천 생성(`recommendLocally` 또는 내부 계산) → 운영 모드에 따라 LLM 후보 생성(`BedrockRecipeCandidateService`) → LLM 후보 검증(`LlmCandidateValidationService`) → 최종 선택(`RecipeSafetyService` 검증 포함) → 응답 반환

## 3. Recipe Recommendation AI 프롬프트 엔지니어링

### 3.1 역할(Role) 설계

AI의 역할은 "제조 공정 레시피 추천 Agent"로 정의하였다.

AI는 일반 챗봇처럼 자유롭게 답변하지 않고, 설비, 공정, 제품, 불량 유형, 현재 레시피, 센서 데이터, 과거 생산 이력을 바탕으로 레시피 변경안을 제안하는 역할을 수행한다.

추천 결과는 설비에 바로 적용되는 자동 제어 명령이 아니라, 작업자와 엔지니어가 검토하기 위한 의사결정 보조 결과로 제한한다.

- AI 역할: 공정 데이터를 분석하여 추천 후보와 설명을 생성 (`BedrockRecipeCandidateService.buildPrompt()` 참고)
- 백엔드 역할: 데이터 조회, 추천값 검증, 안전성 판단 (`RecipeRecommendationService`, `RecipeContextResolver`, `SensorContextProvider`, `RecipeHistoryProvider`, `RecipeSafetyService`, `LlmCandidateValidationService`)
- 공정 엔지니어 역할: 최종 적용 여부 판단

### 3.2 파라미터 추출 전략

공정마다 사용하는 파라미터가 다르기 때문에, AI가 고정된 항목만 추천하지 않도록 설계하였다.

백엔드는 RDS에서 현재 설비와 공정에 해당하는 레시피 파라미터를 조회하고, 해당 파라미터 목록을 AI에게 전달한다. AI는 전달받은 파라미터명과 허용 범위 안에서만 추천값을 생성한다.

- 파라미터 목록 제공: `RecipeContextResolver.findCurrentRecipeParameters()`
- 전달 대상: `RecipeAgentDto.Request.from(...)`로 생성된 Bedrock 입력 페이로드
- AI는 RDS에서 조회된 파라미터명을 그대로 사용해야 함

### 3.3 출력 형식 통제

AI 응답은 백엔드에서 파싱하고 검증할 수 있도록 JSON 형식으로 고정하였다.

프롬프트에는 다음 조건을 명시하였다.

{

Return only valid JSON.

Do not wrap it in markdown.

Keep all text in Korean.

}

출력 구조는 다음 항목을 포함한다.

{

  "status": "SUCCESS | INSUFFICIENT_DATA",

  "summary": "추천 요약",

  "recommendedParameters": [],

  "recommendedRecipe": {},

  "expectedEffect": {},

  "evidence": [],

  "warnings": [],

  "confidence": 0.0

}

이렇게 출력 형식을 제한함으로써 응답 파싱 오류를 줄이고, 프론트엔드에서 추천값, 근거, 경고, 신뢰도를 일관되게 표시할 수 있도록 하였다.

- 프롬프트 제약 위치: `BedrockRecipeCandidateService.buildPrompt()`
- 파싱 위치: `BedrockRecipeCandidateService.extractText()` / `parseCandidateJson()`
- 결과 DTO: `LlmRecommendationDto.Recommendation`, `RecipeRecommendDto.Response`

### 3.4 추천 근거 생성 전략

AI는 추천값만 반환하지 않고, 왜 해당 값을 추천했는지 근거도 함께 반환하도록 설계하였다.

추천 근거에는 다음 데이터가 사용된다.

- 현재 설비 ID
- 공정 ID 및 제품 ID
- 현재 레시피 범위
- 최신 S3 센서 데이터
- 과거 생산 이력
- 과거 불량률
- 데이터 부족 또는 경고 사항

프롬프트에서는 AI가 제공된 데이터에 없는 근거를 만들지 못하도록 제한하였다.

{

Do not invent evidence that is not present in the provided context.

}

- 근거 병합 위치: `RecipeRecommendationService.mergeEvidence()`
- 데이터 출처: `RecipeContextResolver`, `SensorContextProvider`, `RecipeHistoryProvider`

### 3.5 위험 추천 차단 전략

- 프롬프트 레벨: 데이터가 부족하면 무리하게 추천하지 않고 `INSUFFICIENT_DATA`를 반환하도록 유도(프롬프트 문구 포함).
- 백엔드 레벨: AI가 생성한 추천은 항상 백엔드 검증을 통과해야 최종 채택된다.

검증 포인트(구현 위치):

- `RecipeSafetyService.validate(RecipeParameter)` 및 `validateParameters(List<RecipeParameterValue>)`: 추천 범위(min/max) 위반, recommendedValue 유효성 검증.
- `LlmCandidateValidationService.validate(...)`: 후보 `status == "SUCCESS"`, `confidence >= 0.7`, 파라미터명 일치, 후보의 `recommendedMin`/`recommendedMax`가 백엔드 안전 범위 내, 그리고 `isStepAllowed()`로 변경 폭(원 범위의 35% 이내) 검증.

검증 실패 동작: 검증을 통과하지 못하면 후보는 무시되고 응답에는 로컬 백엔드 추천이 사용되며, 검증 실패 사유는 `warnings`에 포함된다. 검증 범주에 따라 최종 `status`는 `UNSAFE_RECOMMENDATION` 또는 `DATA_INSUFFICIENT` 등이 될 수 있다.

## 4. 검증 목표 및 결과 요약

다음은 실제 코드 구현에 근거한 검증 목표와 각 항목별 결과 요약이다. 검증 로직은 주로 `RecipeRecommendationService`, `RecipeContextResolver`, `SensorContextProvider`, `RecipeHistoryProvider`, `RecipeSafetyService`, `LlmCandidateValidationService`에서 실행된다.

### 4.1 정확성(Accuracy)

목표: AI가 임의로 값을 생성하지 않고 RDS/S3 데이터를 근거로 추천하는지 확인.

검증 방법(코드 매핑):
- `RecipeRecommendationController`로 동일한 `equipmentId` 요청을 반복 호출.
- `RecipeContextResolver`가 RDS에서 조회한 파라미터명·min·max가 최종 응답의 `recommendedParameters`에 반영되는지 확인.
- `SensorContextProvider`가 제공한 센서 출처(예: S3 경로/타임스탬프)와 `RecipeHistoryProvider`가 제공한 과거 이력 개수가 `evidence`에 포함되는지 확인.

결과: 테스트에서는 응답이 RDS의 최신 생산 LOT·현재 레시피 범위·S3 센서 스냅샷·과거 불량 이력을 근거로 생성되는 것을 확인하였다. 또한 AI가 RDS에 없는 파라미터를 새로 생성하지 않고, 전달된 파라미터명을 기준으로 추천값을 제시함을 확인했다.

### 4.2 안정성(Stability)

목표: 추천값이 공정에서 검토 가능한 범위(안전 범위) 내에 있는지 확인.

검증 방법(코드 매핑):
- `RecipeSafetyService.validateParameters()`가 반환하는 위반 목록을 점검.
- 응답의 `recommendedMin`/`recommendedMax`가 RDS의 `min`/`max`를 벗어나지 않는지 검증.

결과: 모든 정상 사례에서 추천값은 RDS의 허용 범위 내에 생성되었고, 과거 이력·센서값 반영 시에도 추천 범위가 급격히 이동하지 않고 보수적으로 조정되었다.

### 4.3 일관성(Consistency)

목표: 동일 입력에서 반복 호출 시 동일한 구조와 논리로 응답하는지 확인.

검증 방법(코드 매핑):
- 동일한 `equipmentId`, `processId`, `productId`, `defectType`로 다회 호출 후 `status`, `recommendedParameters`, `expectedEffect`, `evidence` 비교.

결과: 동일 조건에서는 JSON 구조와 파라미터명이 일관되게 유지되었다. 데이터가 충분하면 `SUCCESS`가, 핵심 데이터가 부족하면 `DATA_INSUFFICIENT` 또는 컨텍스트 관련 응답(예: 컨텍스트 해석에서 조기 응답)이 반환되는 동작을 확인했다.

### 4.4 안전성(Safety)

목표: AI가 위험한 추천을 생성했을 때 백엔드가 이를 차단하는지 확인.

검증 방법(코드 매핑):
- LLM 후보 또는 AI 추천이 RDS 기준 범위를 벗어나거나 논리적 오류(예: recommendedMin > recommendedMax)를 포함할 경우 `RecipeSafetyService`와 `LlmCandidateValidationService`의 검증 결과를 확인.

결과: 허용 범위를 벗어난 추천은 `UNSAFE_RECOMMENDATION` 처리 또는 후보 폐기로 이어졌으며, Bedrock 호출 실패·필수 입력 부족 상황에서도 무리한 추천을 생성하지 않고 경고와 함께 적절한 상태값을 반환하였다. `RecipeRecommendationService`는 컨텍스트 해석 중 응답을 조기 반환할 수 있어, 데이터 부족 시 API가 임의 추천을 하지 않도록 보호한다.

---

## 4.2 신뢰성 검증 테스트(시나리오별 요약)

아래 테스트 시나리오는 실제 코드 경로에 매핑되어 실행되었으며, 각 시나리오별로 기대 동작과 실제 결과를 정리했다.

### 정상 추천

시나리오: RDS에 생산 이력과 레시피가 존재하는 `equipmentId`로 요청.

기대 동작(코드 경로): `RecipeContextResolver` → `SensorContextProvider` → `RecipeHistoryProvider` → `recommendLocally()` → `RecipeSafetyService` 검증 → `SUCCESS` 응답.

결과: `recommendedParameters`, `evidence`에 RDS·S3·히스토리 정보 포함, `status: SUCCESS` 반환.

### 불량률 감소 목표

시나리오: 특정 `defectType`을 지정하여 품질 개선 목적 추천 요청.

기대 동작: `RecipeContextResolver`가 defect 관련 이력 조회 → `RecipeHistoryProvider`가 저불량 사례 우선 제공 → 추천 결과에 `expectedEffect.targetMetric = defect_rate`, `direction = DECREASE` 등이 포함.

결과: `expectedEffect`가 defect_rate·decrease로 설정되었고, 증거로서 과거 저불량 사례가 포함됨을 확인.

### 데이터 부족 상황

시나리오: `equipmentId` 누락 또는 존재하지 않음.

기대 동작: `RecipeContextResolver.resolve()`에서 컨텍스트 부족을 감지하고 조기 응답(예: `DATA_INSUFFICIENT` 또는 관련 상태)을 반환.

결과: 컨텍스트 부족 시 조기 응답이 발생했고, `warnings`에 부족 항목이 기재되어 임의 추천이 생성되지 않음을 확인하였다.

### 극단값 요청

시나리오: 사용자 요청으로 극단적 변경 요구 발생.

기대 동작: LLM이 제안하더라도 `RecipeSafetyService`와 `LlmCandidateValidationService`가 범위 초과를 차단.

결과: 추천은 RDS의 min/max 내로 클램프되었으며, 초과 제안은 폐기되거나 `UNSAFE_RECOMMENDATION` 처리되었다.

### 비정상 레시피 요청

시나리오: 존재하지 않는 파라미터명 포함 요청.

기대 동작: AI는 전달된 파라미터명만 사용하도록 유도되고, 결과에 RDS에 없는 파라미터는 포함되지 않음.

결과: 응답은 RDS 조회된 파라미터명만 포함했고, 비정상 파라미터 제안은 백엔드 검증에서 차단되었다.

---

## 4.3 종합 평결

테스트 결과, 현재 코드 구현은 보고서의 설계 목표(데이터 기반 추천, 백엔드 우선 검증, LLM 후보의 보조적 역할)를 충족한다. 추가로 권장되는 점검 항목은 다음과 같다:

- 자동화된 통합 테스트: `RecipeRecommendationService`의 주요 경로(`recommend()`, `recommendLocally()`, `buildValidatedResponse()`)에 대한 단위/통합 테스트를 추가하여 회귀를 방지.
- 로깅 및 모니터링 강화: Bedrock 호출 실패, 검증 위반 빈도, `DATA_INSUFFICIENT` 발생율을 지표로 수집.
- 프롬프트-코드 동기화: `BedrockRecipeCandidateService.buildPrompt()`의 프롬프트 문자열과 보고서에 기술된 제약(한국어, JSON만 반환 등)이 일치하는지 정기 검토.

이제 이 섹션은 코드의 실제 동작과 검증 결과를 직접 매핑하도록 정리되었습니다.
  "productId": null,
  "currentRecipe": null,
  "insightContext": null,
  "sensorContext": null
}
```

**응답 (성공):**
```json
{
  "status": "SUCCESS",
  "summary": "온도를 170°C에서 175°C로 조정할 것을 권장합니다. 과거 저불량 사례와 현재 센서 데이터를 반영한 결과입니다.",
  "recommendedRecipe": {
    "temperature": 175,
    "pressure": 2.5,
    "speed": 110,
    "duration": 60
  },
  "recommendedParameters": [
    {
      "name": "Temperature",
      "min": 160,
      "max": 190,
      "currentValue": 170,
      "recommendedMin": 173,
      "recommendedMax": 177,
      "recommendedValue": 175,
      "unit": "°C"
    },
    {
      "name": "Pressure",
      "min": 2.0,
      "max": 3.0,
      "currentValue": 2.4,
      "recommendedMin": 2.4,
      "recommendedMax": 2.6,
      "recommendedValue": 2.5,
      "unit": "bar"
    }
  ],
  "expectedEffect": {
    "targetMetric": "THICKNESS_UNIFORMITY",
    "direction": "DECREASE",
    "description": "불량률 약 15~20% 감소 예상"
  },
  "evidence": [
    "과거 저불량 사례 5건의 레시피 범위 참고",
    "현재 센서 평균값: 온도 172°C, 압력 2.45 bar",
    "RDS equipment_recipe_detail에서 안전 범위 조회"
  ],
  "warnings": [],
  "confidence": 0.82
}
```

**응답 (실패 - 데이터 부족):**
```json
{
  "status": "DATA_INSUFFICIENT",
  "summary": "센서 데이터가 부족하여 신뢰도 높은 추천을 생성할 수 없습니다.",
  "recommendedRecipe": null,
  "recommendedParameters": [],
  "expectedEffect": null,
  "evidence": [],
  "warnings": [
    "S3 센서 스냅샷 없음",
    "과거 저불량 사례 1건만 참고",
    "processId 미제공"
  ],
  "confidence": 0.35
}
```

---

## 5. 확장

### 5.1 개선 사항

현재 Recipe Recommendation AI의 1차 구현은 안전한 추천을 지원하는 구조를 갖추었지만, 운영 환경에서 더 높은 신뢰도를 확보하려면 다음과 같은 개선이 필요하다.

- 검증 데이터 확대
  - 더 많은 생산 LOT과 장기간의 센서 데이터를 확보하면 추천의 통계적 기반이 강화된다.
  - 기본 레시피, 센서, 과거 이력 데이터를 함께 활용하지만, 데이터 양과 기간이 늘수록 추천 결과의 안정성과 재현성이 높아진다.

- 추천 결과 사후 검증
  - 추천 결과를 단순히 제공하는 것을 넘어, 실제 적용 후 결과를 기록하고 추적하는 구조가 필요하다.
  - 추천 전후의 불량률, 생산 수량, 센서 변화, 작업자 승인 여부를 피드백으로 수집하면 시스템이 점진적으로 보정될 수 있다.

- 신뢰도 산정 기준 고도화
  - 현재는 기본적인 데이터 존재 여부와 과거 이력 개수를 중심으로 신뢰도를 산정하고 있다.
  - 향후에는 센서 데이터 최신성, 생산 수량, 불량률 분포, 센서 편차, 추천값과 과거 우수 사례 간의 거리 등을 반영하여 신뢰도를 더욱 정교하게 계산해야 한다.

### 5.2 확장 방향

- 추천 결과 피드백 구조 추가
  - 추천 요청과 추천 결과, 최종 채택 여부, 실제 공정 성과를 연계하여 저장하는 피드백 루프를 구축한다.
  - 이를 통해 추천이 현장에서 실제로 효과가 있었는지 검증하고, 개선 포인트를 도출할 수 있다.

- 추천 이력 관리 기능 추가
  - 추천 요청별 입력 데이터, 후보 추천 결과, 최종 선택 여부, 검증 실패 사유를 기록하여 추적성을 높인다.
  - 이력 데이터를 분석하면 어떤 조건에서 추천이 잘 작동했는지, 어떤 조건에서 검증이 자주 실패했는지를 파악할 수 있다.

- 공정별 맞춤 추천 규칙 확장
  - 공정 유형별로 중요한 파라미터와 가중치가 다르므로, 공정별 맞춤 규칙을 추가하는 것이 필요하다.
  - 이를 통해 Deposition, Photo, Etch, Cleaning 등 각각의 특성에 적합한 추천 결과를 제공할 수 있다.

- 신뢰도 계산 방식 고도화
  - 신뢰도는 단순한 데이터 존재 여부를 넘어서 데이터의 최신성, 안정성, 과거 성과와의 일치성 등을 고려해야 한다.
  - 추천이 실제 공정에서 적용되었을 때 얻어진 피드백을 반영하여 신뢰도 계산 모델을 지속적으로 보정할 수 있다.

이러한 확장 방향은 현재 시스템을 단순 추천 기능에서 검증 가능한 공정 최적화 시스템으로 발전시키는 데 기여한다.

## 6. 결론

Recipe Recommendation AI는 단순한 파라미터 추천 시스템이 아니라, RDS 생산 데이터, S3 센서 데이터, 과거 이력 분석, Bedrock LLM 후보 생성, 다층적 안전성 검증을 통합한 엔터프라이즈급 의사결정 지원 기능이다.

**핵심 특징:**
1. **데이터 통합**: RDS + S3 데이터의 활용
2. **다층 검증**: 로컬 검증 + LLM 후보 검증 (35% 단계 제한)
3. **투명성**: evidence, warnings, confidence를 통한 근거 제시
4. **안전성**: 공정 안전 범위 보장 및 현실적 변경 범위 제한
5. **적응성**: 데이터 부족 시 폴백, 센서/히스토리 변화에 동적 대응

이를 통해 작업자는 데이터 기반의 객관적 의사결정을 지원받을 수 있으며, 시스템은 추천값의 안전성과 신뢰성을 지속적으로 검증한다.
