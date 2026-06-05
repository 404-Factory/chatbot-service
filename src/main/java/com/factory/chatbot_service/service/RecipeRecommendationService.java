package com.factory.chatbot_service.service;
import com.factory.chatbot_service.dto.RecipeRecommendDto;
import com.factory.chatbot_service.dto.RecipeAgentDto;

import com.factory.chatbot_service.dto.RecipeRecommendationContext;
import com.factory.chatbot_service.dto.SensorContext;
import com.factory.chatbot_service.dto.ExpectedEffect;
import com.factory.chatbot_service.dto.RecipeParameter;
import com.factory.chatbot_service.dto.RecipeParameterValue;
import com.factory.chatbot_service.dto.RecipeHistoryCase;
import com.factory.chatbot_service.dto.SensorSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecipeRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecipeRecommendationService.class);
    private static final Map<String, String> RDS_TO_S3_EQUIPMENT_ID = buildRdsToS3EquipmentId();

    private final RecipeContextResolver recipeContextResolver;
    private final SensorContextProvider sensorContextProvider;
    private final RecipeHistoryProvider recipeHistoryProvider;
    private final RecipeAgentClient recipeAgentClient;
    private final RecipeSafetyService recipeSafetyService;

    public RecipeRecommendationService(
            RecipeContextResolver recipeContextResolver,
            SensorContextProvider sensorContextProvider,
            RecipeHistoryProvider recipeHistoryProvider,
            RecipeAgentClient recipeAgentClient,
            RecipeSafetyService recipeSafetyService
    ) {
        this.recipeContextResolver = recipeContextResolver;
        this.sensorContextProvider = sensorContextProvider;
        this.recipeHistoryProvider = recipeHistoryProvider;
        this.recipeAgentClient = recipeAgentClient;
        this.recipeSafetyService = recipeSafetyService;
    }

    public RecipeRecommendDto.Response recommend(RecipeRecommendDto.Request request) {
        log.info("Starting Bedrock-backed recipe recommendation: equipmentId={}, defectType={}",
                request == null ? null : request.getEquipmentId(),
                request == null ? null : request.getDefectType());
        ResolvedRecommendationInput resolved = resolveRecommendationInput(request);
        if (resolved.response != null) {
            log.info("Recommendation context resolution returned early: status={}", resolved.response.getStatus());
            return resolved.response;
        }

        RecipeAgentDto.Response agentResponse;
        try {
            log.info("Invoking Bedrock Agent for recipe recommendation: equipmentId={}, processId={}, productId={}, histories={}",
                    resolved.context.getEquipmentId(),
                    resolved.context.getProcessId(),
                    resolved.context.getProductId(),
                    resolved.histories.size());
            agentResponse = recipeAgentClient.recommend(
                    RecipeAgentDto.Request.from(
                            request,
                            resolved.context,
                            resolved.sensorSnapshot,
                            resolved.histories
                    )
            );
            log.info("Bedrock Agent returned recipe recommendation: status={}", agentResponse.getStatus());
        } catch (Exception e) {
            log.warn("Bedrock Agent recipe recommendation failed: {}", e.getMessage());
            return RecipeRecommendDto.Response.builder()
                    .status("AGENT_UNAVAILABLE")
                    .summary("Bedrock Agent could not generate a recommendation. Check agent settings, AWS credentials, and network access.")
                    .recommendedRecipe(null)
                    .expectedEffect(null)
                    .evidence(buildContextEvidence(resolved.context, resolved.sensorSnapshot, resolved.histories))
                    .warnings(mergeWarnings(resolved.context.getWarnings(), List.of(e.getMessage())))
                    .confidence(0.0)
                    .build();
        }

        return buildValidatedResponse(agentResponse, resolved.context, resolved.sensorSnapshot, resolved.histories);
    }

    public RecipeRecommendDto.Response recommendForActionGroup(RecipeRecommendDto.Request request) {
        return recommendLocally(request);
    }

    public RecipeRecommendDto.Response recommendLocally(RecipeRecommendDto.Request request) {
        // /api/chat의 기본 경로: RDS/S3 데이터를 모으고 백엔드에서 추천 범위를 계산한다.
        ResolvedRecommendationInput resolved = resolveRecommendationInput(request);
        if (resolved.response != null) {
            return resolved.response;
        }

        RecipeAgentDto.Response localResponse = buildLocalRecommendation(
                resolved.context,
                resolved.sensorSnapshot,
                resolved.histories
        );

        return buildValidatedResponse(localResponse, resolved.context, resolved.sensorSnapshot, resolved.histories);
    }

    private ResolvedRecommendationInput resolveRecommendationInput(RecipeRecommendDto.Request request) {
        if (request == null || !StringUtils.hasText(request.getEquipmentId())) {
            return ResolvedRecommendationInput.withResponse(RecipeRecommendDto.Response.builder()
                    .status("INSUFFICIENT_CONTEXT")
                    .summary("equipmentId is required to resolve production context and recommend a recipe.")
                    .recommendedRecipe(null)
                    .recommendedParameters(List.of())
                    .expectedEffect(null)
                    .evidence(List.of())
                    .warnings(List.of("Request body must include equipmentId, for example {\"equipmentId\":\"1\"}."))
                    .confidence(0.0)
                    .build());
        }

        log.info("Resolving RDS recipe context: equipmentId={}, defectType={}",
                request.getEquipmentId(), request.getDefectType());
        RecipeRecommendationContext context = recipeContextResolver.resolve(request);

        if (!context.hasRequiredRecommendationContext()) {
            return ResolvedRecommendationInput.withResponse(RecipeRecommendDto.Response.builder()
                    .status("INSUFFICIENT_CONTEXT")
                    .summary("The backend could not resolve enough production context to ask the agent for a recipe recommendation.")
                    .recommendedRecipe(null)
                    .recommendedParameters(List.of())
                    .expectedEffect(null)
                    .evidence(buildContextEvidence(context, null, List.of()))
                    .warnings(context.getWarnings())
                    .confidence(0.0)
                    .build());
        }

        String sensorEquipmentId = resolveSensorEquipmentId(request, context);
        log.info("Loading sensor context: rdsEquipmentId={}, sensorEquipmentId={}, processId={}",
                context.getEquipmentId(), sensorEquipmentId, context.getProcessId());
        SensorSnapshot sensorSnapshot = sensorContextProvider
                .loadLatest(sensorEquipmentId, context.getProcessId())
                .orElseGet(() -> toSensorSnapshot(request.getSensorContext(), sensorEquipmentId, context.getProcessId()));

        log.info("Loading historical recipe cases: equipmentId={}, processId={}, productId={}, defectType={}",
                context.getEquipmentId(), context.getProcessId(), context.getProductId(), context.getDefectType());
        List<RecipeHistoryCase> histories = recipeHistoryProvider.findRelevantHistories(
                context.getEquipmentId(),
                context.getProcessId(),
                context.getProductId(),
                context.getDefectType()
        );

        return new ResolvedRecommendationInput(context, sensorSnapshot, histories, null);
    }

    private RecipeRecommendDto.Response buildValidatedResponse(
            RecipeAgentDto.Response agentResponse,
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        if (!"SUCCESS".equals(agentResponse.getStatus())) {
            return toResponse(agentResponse, context, sensorSnapshot, histories);
        }

        List<String> safetyViolations = validateAgentRecommendation(agentResponse);
        if (!safetyViolations.isEmpty()) {
            List<String> warnings = mergeWarnings(
                    context.getWarnings(),
                    safeList(agentResponse.getWarnings())
            );
            warnings.add("The AI recommendation was excluded from direct use because it did not pass backend safety validation.");

            return RecipeRecommendDto.Response.builder()
                    .status("UNSAFE_RECOMMENDATION")
                    .summary("Bedrock Agent recommended a recipe, but backend safety validation rejected it.")
                    .recommendedRecipe(agentResponse.getRecommendedRecipe())
                    .recommendedParameters(agentResponse.getRecommendedParameters())
                    .expectedEffect(agentResponse.getExpectedEffect())
                    .evidence(mergeEvidence(agentResponse, context, sensorSnapshot, histories, safetyViolations))
                    .warnings(warnings)
                    .confidence(agentResponse.getConfidence())
                    .build();
        }

        return toResponse(agentResponse, context, sensorSnapshot, histories);
    }

    private RecipeAgentDto.Response buildLocalRecommendation(
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        RecipeAgentDto.Response response = new RecipeAgentDto.Response();
        response.setEvidence(new ArrayList<>());
        response.setWarnings(new ArrayList<>());

        List<RecipeParameterValue> currentParameters = safeParameterList(context.getCurrentRecipeParameters());
        if (!currentParameters.isEmpty()) {
            List<RecipeParameterValue> recommendedParameters = currentParameters.stream()
                    .map(parameter -> recommendParameter(parameter, sensorSnapshot, histories))
                    .toList();

            response.setStatus("SUCCESS");
            response.setSummary("RDS base sensor limits, historical low-defect sensor ranges, and latest S3 sensor statistics were used to calculate recommended sensor measurement min/max ranges for this equipment.");
            response.setRecommendedParameters(recommendedParameters);
            response.setRecommendedRecipe(null);
            response.setExpectedEffect(defaultExpectedEffect());
            response.setEvidence(buildLocalRecommendationEvidence(recommendedParameters, sensorSnapshot, histories));
            response.setWarnings(buildLocalRecommendationWarnings(sensorSnapshot, histories));
            response.setConfidence(calculateLocalConfidence(sensorSnapshot, histories));
            return response;
        }

        RecipeParameter currentRecipe = context.getCurrentRecipe();
        if (currentRecipe == null) {
            response.setStatus("INSUFFICIENT_DATA");
            response.setSummary("Current recipe data was not available for backend-only recommendation.");
            response.setRecommendedRecipe(null);
            response.setRecommendedParameters(List.of());
            response.setExpectedEffect(null);
            response.setConfidence(0.0);
            response.getWarnings().add("currentRecipe or currentRecipeParameters is required.");
            return response;
        }

        response.setStatus("SUCCESS");
        response.setSummary("Latest S3 sensor context was used to adjust the current recipe within backend safety limits.");
        response.setRecommendedRecipe(recommendFixedRecipe(currentRecipe, sensorSnapshot));
        response.setRecommendedParameters(List.of());
        response.setExpectedEffect(defaultExpectedEffect());
        response.setEvidence(buildLocalRecipeEvidence(sensorSnapshot, histories));
        response.setWarnings(buildLocalRecommendationWarnings(sensorSnapshot, histories));
        response.setConfidence(calculateLocalConfidence(sensorSnapshot, histories));
        return response;
    }

    private String resolveSensorEquipmentId(
            RecipeRecommendDto.Request request,
            RecipeRecommendationContext context
    ) {
        SensorContext sensorContext = request.getSensorContext();
        if (sensorContext != null && StringUtils.hasText(sensorContext.getEquipmentId())) {
            return sensorContext.getEquipmentId();
        }
        return RDS_TO_S3_EQUIPMENT_ID.getOrDefault(context.getEquipmentId(), context.getEquipmentId());
    }

    private SensorSnapshot toSensorSnapshot(
            SensorContext sensorContext,
            String defaultEquipmentId,
            String defaultProcessId
    ) {
        if (sensorContext == null) {
            return null;
        }

        return SensorSnapshot.builder()
                .equipmentId(firstText(sensorContext.getEquipmentId(), defaultEquipmentId))
                .processId(defaultProcessId)
                .source(sensorContext.getSource())
                .measuredAt(sensorContext.getMeasuredAt())
                .temperature(sensorContext.getTemperature())
                .pressure(sensorContext.getPressure())
                .speed(sensorContext.getSpeed())
                .vibration(sensorContext.getVibration())
                .humidity(sensorContext.getHumidity())
                .build();
    }

    private RecipeRecommendDto.Response toResponse(
            RecipeAgentDto.Response agentResponse,
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        return RecipeRecommendDto.Response.builder()
                .status(agentResponse.getStatus())
                .summary(agentResponse.getSummary())
                .recommendedRecipe(agentResponse.getRecommendedRecipe())
                .recommendedParameters(agentResponse.getRecommendedParameters())
                .expectedEffect(agentResponse.getExpectedEffect())
                .evidence(mergeEvidence(agentResponse, context, sensorSnapshot, histories, List.of()))
                .warnings(mergeWarnings(context.getWarnings(), safeList(agentResponse.getWarnings())))
                .confidence(agentResponse.getConfidence())
                .build();
    }

    private List<String> mergeEvidence(
            RecipeAgentDto.Response agentResponse,
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories,
            List<String> safetyViolations
    ) {
        List<String> evidence = new ArrayList<>(safeList(agentResponse.getEvidence()));
        evidence.addAll(buildContextEvidence(context, sensorSnapshot, histories));
        evidence.addAll(safetyViolations);
        return evidence;
    }

    private List<String> buildContextEvidence(
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        List<String> evidence = new ArrayList<>();

        evidence.add("Resolved equipmentId: " + context.getEquipmentId());
        evidence.add("Resolved processId: " + context.getProcessId());
        evidence.add("Resolved productId: " + context.getProductId());
        evidence.add("Resolved defectType: " + context.getDefectType());
        String s3EquipmentId = RDS_TO_S3_EQUIPMENT_ID.get(context.getEquipmentId());
        if (s3EquipmentId != null) {
            evidence.add("Resolved S3 equipmentId: " + s3EquipmentId);
        }

        if (context.getLotId() != null) {
            evidence.add("Resolved latest lot_id: " + context.getLotId());
        }
        if (context.getMasterRecipeId() != null) {
            evidence.add("Resolved master_recipe_id: " + context.getMasterRecipeId());
        }
        if (context.getCurrentRecipeSource() != null) {
            evidence.add("Current recipe source: " + context.getCurrentRecipeSource());
        }
        if (context.getCurrentRecipeParameters() != null && !context.getCurrentRecipeParameters().isEmpty()) {
            context.getCurrentRecipeParameters().forEach(parameter -> evidence.add(
                    "Current recipe parameter: " + parameter.getName()
                            + ", min=" + parameter.getMin()
                            + ", max=" + parameter.getMax()
                            + ", currentValue=" + parameter.getCurrentValue()
            ));
        }

        if (sensorSnapshot == null) {
            evidence.add("S3 latest sensor snapshot was not found.");
        } else {
            evidence.add("S3 sensor source: " + sensorSnapshot.getSource());
            if (sensorSnapshot.getMeasuredAt() != null) {
                evidence.add("S3 sensor measuredAt: " + sensorSnapshot.getMeasuredAt());
            }
            if (sensorSnapshot.getSampleCount() != null) {
                evidence.add("S3 accumulated sensor samples: " + sensorSnapshot.getSampleCount()
                        + ", averageTemperature=" + sensorSnapshot.getAverageTemperature()
                        + ", averagePressure=" + sensorSnapshot.getAveragePressure()
                        + ", averageSpeed=" + sensorSnapshot.getAverageSpeed());
            }
            if (sensorSnapshot.getAverageSensorValues() != null
                    && !sensorSnapshot.getAverageSensorValues().isEmpty()) {
                evidence.add("S3 configured sensor averages: " + sensorSnapshot.getAverageSensorValues());
            }
        }

        evidence.add("RDS historical recipe cases: " + histories.size());
        histories.forEach(history -> evidence.add(
                "equipment_rec_id=" + history.getId()
                        + ", defectRate=" + history.getDefectRate()
                        + "%, defectCount=" + history.getDefectCount()
                        + ", productQuantity=" + history.getProductQuantity()
        ));

        return evidence;
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        List<String> warnings = new ArrayList<>(safeList(first));
        warnings.addAll(safeList(second));
        return warnings;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<RecipeParameterValue> safeParameterList(List<RecipeParameterValue> values) {
        return values == null ? List.of() : values;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> validateAgentRecommendation(RecipeAgentDto.Response agentResponse) {
        if (agentResponse.getRecommendedParameters() != null
                && !agentResponse.getRecommendedParameters().isEmpty()) {
            return recipeSafetyService.validateParameters(agentResponse.getRecommendedParameters());
        }

        return recipeSafetyService.validate(agentResponse.getRecommendedRecipe());
    }

    private RecipeParameterValue recommendParameter(
            RecipeParameterValue parameter,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        Double min = parameter.getMin();
        Double max = parameter.getMax();
        Double currentValue = parameter.getCurrentValue();
        Double sensorAverage = averageSensorValue(parameter.getName(), sensorSnapshot);
        RecommendedRange recommendedRange = chooseRecommendedRange(
                min,
                max,
                sensorAverage,
                historicalTargetRange(parameter.getName(), histories)
        );

        return RecipeParameterValue.builder()
                .name(parameter.getName())
                .min(min)
                .max(max)
                .currentValue(sensorAverage != null ? sensorAverage : currentValue)
                .recommendedMin(recommendedRange.min())
                .recommendedMax(recommendedRange.max())
                .recommendedValue(recommendedRange.midpoint())
                .unit(parameter.getUnit())
                .build();
    }

    private RecipeParameter recommendFixedRecipe(
            RecipeParameter currentRecipe,
            SensorSnapshot sensorSnapshot
    ) {
        return new RecipeParameter(
                chooseRecommendedValue(160.0, 190.0, currentRecipe.getTemperature(), firstNonNull(
                        sensorSnapshot == null ? null : sensorSnapshot.getAverageTemperature(),
                        sensorSnapshot == null ? null : sensorSnapshot.getTemperature()
                ), null),
                chooseRecommendedValue(2.0, 3.0, currentRecipe.getPressure(), firstNonNull(
                        sensorSnapshot == null ? null : sensorSnapshot.getAveragePressure(),
                        sensorSnapshot == null ? null : sensorSnapshot.getPressure()
                ), null),
                chooseRecommendedValue(90.0, 140.0, currentRecipe.getSpeed(), firstNonNull(
                        sensorSnapshot == null ? null : sensorSnapshot.getAverageSpeed(),
                        sensorSnapshot == null ? null : sensorSnapshot.getSpeed()
                ), null),
                chooseRecommendedValue(30.0, 120.0, currentRecipe.getDuration(), null, null)
        );
    }

    private Double averageSensorValue(String parameterName, SensorSnapshot sensorSnapshot) {
        if (sensorSnapshot == null || !StringUtils.hasText(parameterName)) {
            return null;
        }

        Map<String, Double> averageSensorValues = sensorSnapshot.getAverageSensorValues();
        if (averageSensorValues != null && averageSensorValues.containsKey(parameterName)) {
            return averageSensorValues.get(parameterName);
        }

        return switch (parameterName.toLowerCase()) {
            case "temperature", "temp" -> firstNonNull(sensorSnapshot.getAverageTemperature(), sensorSnapshot.getTemperature());
            case "pressure" -> firstNonNull(sensorSnapshot.getAveragePressure(), sensorSnapshot.getPressure());
            case "speed" -> firstNonNull(sensorSnapshot.getAverageSpeed(), sensorSnapshot.getSpeed());
            case "vibration" -> firstNonNull(sensorSnapshot.getAverageVibration(), sensorSnapshot.getVibration());
            case "humidity" -> firstNonNull(sensorSnapshot.getAverageHumidity(), sensorSnapshot.getHumidity());
            default -> null;
        };
    }

    private RecommendedRange historicalTargetRange(String parameterName, List<RecipeHistoryCase> histories) {
        if (!StringUtils.hasText(parameterName) || histories == null || histories.isEmpty()) {
            return null;
        }

        double weightedMinSum = 0.0;
        double weightedMaxSum = 0.0;
        double totalWeight = 0.0;
        String normalizedParameterName = normalize(parameterName);

        for (int index = 0; index < histories.size(); index++) {
            RecipeHistoryCase history = histories.get(index);
            RecipeParameterValue matchingParameter = findMatchingHistoryParameter(normalizedParameterName, history);
            if (matchingParameter == null) {
                continue;
            }

            Double historicalMin = firstNonNull(matchingParameter.getRecommendedMin(), matchingParameter.getMin());
            Double historicalMax = firstNonNull(matchingParameter.getRecommendedMax(), matchingParameter.getMax());
            if (historicalMin == null || historicalMax == null) {
                continue;
            }

            double weight = historicalQualityWeight(history, index);
            weightedMinSum += Math.min(historicalMin, historicalMax) * weight;
            weightedMaxSum += Math.max(historicalMin, historicalMax) * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return null;
        }

        return RecommendedRange.of(round(weightedMinSum / totalWeight), round(weightedMaxSum / totalWeight));
    }

    private RecipeParameterValue findMatchingHistoryParameter(
            String normalizedParameterName,
            RecipeHistoryCase history
    ) {
        if (history == null || history.getParameters() == null) {
            return null;
        }

        for (RecipeParameterValue parameter : history.getParameters()) {
            if (parameter != null && normalizedParameterName.equals(normalize(parameter.getName()))) {
                return parameter;
            }
        }
        return null;
    }

    private double historicalQualityWeight(RecipeHistoryCase history, int rankIndex) {
        double rankWeight = 1.0 / (rankIndex + 1.0);
        double defectWeight = history.getDefectRate() == null ? 1.0 : 1.0 / (1.0 + Math.max(0.0, history.getDefectRate()));
        double quantityWeight = history.getProductQuantity() == null || history.getProductQuantity() <= 0
                ? 1.0
                : Math.log10(history.getProductQuantity() + 10.0);
        return rankWeight * defectWeight * quantityWeight;
    }

    private Double chooseRecommendedValue(
            Double min,
            Double max,
            Double currentValue,
            Double sensorAverage,
            Double historicalTarget
    ) {
        if (min == null || max == null) {
            return firstNonNull(historicalTarget, currentValue);
        }

        Double baseValue = currentValue == null ? round((min + max) / 2) : clamp(currentValue, min, max);
        double target = baseValue;

        if (historicalTarget != null) {
            target = moveToward(target, historicalTarget, 0.65);
        }

        if (sensorAverage != null) {
            double sensorWeight = historicalTarget == null ? 0.45 : 0.20;
            target = moveToward(target, sensorAverage, sensorWeight);
        }

        if (currentValue != null) {
            target = limitSingleRecommendationStep(currentValue, target, min, max);
        }

        return clamp(target, min, max);
    }

    private RecommendedRange chooseRecommendedRange(
            Double baseMin,
            Double baseMax,
            Double sensorAverage,
            RecommendedRange historicalTarget
    ) {
        if (baseMin == null || baseMax == null) {
            return RecommendedRange.of(baseMin, baseMax);
        }

        // 기준 안전 범위를 출발점으로 삼고, 과거 저불량 범위와 최신 센서 평균 쪽으로 조금씩 이동한다.
        double lower = Math.min(baseMin, baseMax);
        double upper = Math.max(baseMin, baseMax);
        double recommendedMin = lower;
        double recommendedMax = upper;

        if (historicalTarget != null && historicalTarget.isComplete()) {
            recommendedMin = moveToward(recommendedMin, historicalTarget.min(), 0.65);
            recommendedMax = moveToward(recommendedMax, historicalTarget.max(), 0.65);
        }

        if (sensorAverage != null) {
            double center = (recommendedMin + recommendedMax) / 2.0;
            double width = recommendedMax - recommendedMin;
            double sensorWeight = historicalTarget == null ? 0.35 : 0.15;
            double adjustedCenter = moveToward(center, sensorAverage, sensorWeight);
            recommendedMin = adjustedCenter - (width / 2.0);
            recommendedMax = adjustedCenter + (width / 2.0);
        }

        recommendedMin = limitBoundaryStep(lower, recommendedMin, lower, upper);
        recommendedMax = limitBoundaryStep(upper, recommendedMax, lower, upper);

        // 최종 추천 범위는 항상 RDS 기준 안전 범위 안에 머물도록 보정한다.
        recommendedMin = clamp(recommendedMin, lower, upper);
        recommendedMax = clamp(recommendedMax, lower, upper);

        if (recommendedMin > recommendedMax) {
            double midpoint = clamp((recommendedMin + recommendedMax) / 2.0, lower, upper);
            recommendedMin = midpoint;
            recommendedMax = midpoint;
        }

        return RecommendedRange.of(recommendedMin, recommendedMax);
    }

    private double limitBoundaryStep(double currentBoundary, double targetBoundary, double baseMin, double baseMax) {
        double range = baseMax - baseMin;
        if (range <= 0) {
            return targetBoundary;
        }

        double maxStep = Math.max(range * 0.20, 0.1);
        return Math.max(currentBoundary - maxStep, Math.min(currentBoundary + maxStep, targetBoundary));
    }

    private double moveToward(double baseValue, double targetValue, double weight) {
        return baseValue + ((targetValue - baseValue) * weight);
    }

    private double limitSingleRecommendationStep(
            double currentValue,
            double targetValue,
            double min,
            double max
    ) {
        double range = max - min;
        if (range <= 0) {
            return targetValue;
        }

        double maxStep = Math.max(range * 0.20, 0.1);
        double lowerBound = currentValue - maxStep;
        double upperBound = currentValue + maxStep;
        return Math.max(lowerBound, Math.min(upperBound, targetValue));
    }

    private Double clamp(Double value, Double min, Double max) {
        return round(Math.max(min, Math.min(max, value)));
    }

    private Double round(Double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }

    private ExpectedEffect defaultExpectedEffect() {
        return new ExpectedEffect(
                "defect_rate",
                "decrease",
                "Recommended sensor measurement min/max ranges stay inside the backend safety range and are expected to reduce defect risk."
        );
    }

    private List<String> buildLocalRecommendationEvidence(
            List<RecipeParameterValue> recommendedParameters,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        List<String> evidence = new ArrayList<>();
        for (RecipeParameterValue parameter : recommendedParameters) {
            Double sensorAverage = averageSensorValue(parameter.getName(), sensorSnapshot);
            evidence.add(parameter.getName()
                    + ": baseSensorLimit=" + parameter.getMin() + "-" + parameter.getMax()
                    + ", recommendedSensorRange=" + parameter.getRecommendedMin() + "-" + parameter.getRecommendedMax()
                    + ", recommendedSensorCenter=" + parameter.getRecommendedValue()
                    + ", sensorAverage=" + sensorAverage
                    + ", reason=historical low-defect sensor ranges are prioritized and latest S3 sensor average adjusts the recommended measurement range center");
        }
        evidence.addAll(buildLocalRecipeEvidence(sensorSnapshot, histories));
        return evidence;
    }

    private List<String> buildLocalRecipeEvidence(
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        List<String> evidence = new ArrayList<>();
        if (sensorSnapshot == null) {
            evidence.add("No S3 sensor snapshot was available, so current values and RDS limits were prioritized.");
        } else {
            evidence.add("S3 sensor source used by backend-only recommendation: " + sensorSnapshot.getSource());
        }
        evidence.add("Historical RDS cases considered by backend-only recommendation: " + histories.size());
        return evidence;
    }

    private List<String> buildLocalRecommendationWarnings(
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        List<String> warnings = new ArrayList<>();
        if (sensorSnapshot == null) {
            warnings.add("S3 sensor snapshot was not found.");
        }
        if (histories.size() < 2) {
            warnings.add("Few historical recipe cases were available, so confidence is limited.");
        }
        return warnings;
    }

    private double calculateLocalConfidence(
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories
    ) {
        // 신뢰도는 최신 센서 데이터와 과거 이력 개수가 충분할수록 높아진다.
        double confidence = 0.45;

        if (sensorSnapshot != null) {
            confidence += 0.15;
            if (sensorSnapshot.getSampleCount() != null && sensorSnapshot.getSampleCount() >= 5) {
                confidence += 0.08;
            }
            if (sensorSnapshot.getMeasuredAt() != null) {
                confidence += 0.04;
            }
        }

        int historyCount = histories == null ? 0 : histories.size();
        if (historyCount >= 10) {
            confidence += 0.20;
        } else if (historyCount >= 5) {
            confidence += 0.15;
        } else if (historyCount >= 2) {
            confidence += 0.08;
        } else {
            confidence -= 0.08;
        }

        return Math.max(0.0, Math.min(0.92, round(confidence)));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private record RecommendedRange(Double min, Double max) {

        private static RecommendedRange of(Double min, Double max) {
            return new RecommendedRange(min == null ? null : roundStatic(min), max == null ? null : roundStatic(max));
        }

        private boolean isComplete() {
            return min != null && max != null;
        }

        private Double midpoint() {
            if (!isComplete()) {
                return null;
            }
            return roundStatic((min + max) / 2.0);
        }

        private static Double roundStatic(Double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    private record ResolvedRecommendationInput(
            RecipeRecommendationContext context,
            SensorSnapshot sensorSnapshot,
            List<RecipeHistoryCase> histories,
            RecipeRecommendDto.Response response
    ) {
        private static ResolvedRecommendationInput withResponse(RecipeRecommendDto.Response response) {
            return new ResolvedRecommendationInput(null, null, List.of(), response);
        }
    }

    private static Map<String, String> buildRdsToS3EquipmentId() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("1", "EQP-DEPOSITION-001");
        values.put("2", "EQP-DEPOSITION-002");
        values.put("3", "EQP-PHOTO-001");
        values.put("4", "EQP-PHOTO-002");
        values.put("5", "EQP-PHOTO-003");
        values.put("6", "EQP-PHOTO-004");
        values.put("7", "EQP-ETCH-001");
        values.put("8", "EQP-ETCH-002");
        values.put("9", "EQP-CLEANING-001");
        values.put("10", "EQP-CLEANING-002");
        return Map.copyOf(values);
    }
}