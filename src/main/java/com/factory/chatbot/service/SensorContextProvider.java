package com.factory.chatbot.service;

import com.factory.chatbot.dto.SensorSnapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class SensorContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SensorContextProvider.class);
    private static final DateTimeFormatter REALTIME_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");
    private static final String[] TEMPERATURE_ALIASES = {
            "temperature", "temp", "temperatureC", "temperatureCelsius", "temperatureValue",
            "chamberTemperature", "processTemperature", "bakeTemperature", "softBakeTemperature", "온도"
    };
    private static final String[] PRESSURE_ALIASES = {
            "pressure", "press", "pressureValue", "chamberPressure", "processPressure",
            "vacuumPressure", "압력"
    };
    private static final String[] SPEED_ALIASES = {
            "speed", "rpm", "speedValue", "spinSpeed", "spin_speed", "motorSpeed",
            "conveyorSpeed", "rotationSpeed", "속도"
    };
    private static final String[] VIBRATION_ALIASES = {
            "vibration", "vib", "vibrationValue", "vibrationRms", "vibration_rms", "진동"
    };
    private static final String[] HUMIDITY_ALIASES = {
            "humidity", "hum", "relativeHumidity", "rh", "humidityValue", "습도"
    };
    private static final String[] MEASURED_AT_ALIASES = {
            "createdAt", "timestamp", "measuredAt", "eventTime", "time", "datetime"
    };
    private static final String[] VALUE_FIELD_ALIASES = {
            "value", "sensorValue", "sensor_value", "reading", "measurement", "avg", "average"
    };
    private static final String[] METRIC_NAME_FIELD_ALIASES = {
            "name", "metric", "metricName", "metric_name", "sensor", "sensorName", "sensor_name",
            "sensorType", "sensor_type", "type", "tag", "key"
    };
    private static final Map<String, List<String>> PROCESS_SENSOR_TYPES = Map.of(
            "DEPOSITION", List.of("Spin Speed", "Soft Bake Temperature"),
            "PHOTO", List.of("Exposure Dose", "PEB"),
            "ETCH", List.of("Chamber Pressure", "Chuck Temperature"),
            "CLEANING", List.of("Chemical Temperature", "Chemical Concentration")
    );
    private static final Map<String, List<String>> EQUIPMENT_SENSOR_TYPES = buildEquipmentSensorTypes();

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucketName;
    private final String latestKeyTemplate;
    private final String realtimePrefix;
    private final int realtimeLookbackDays;
    private final boolean allowBroadRealtimeScan;

    public SensorContextProvider(
            S3Client s3Client,
            ObjectMapper objectMapper,
            @Value("${chatbot.s3.bucket-name}") String bucketName,
            @Value("${chatbot.s3.sensor-latest-key-template:sensor/{equipmentId}/latest.json}") String latestKeyTemplate,
            @Value("${chatbot.s3.realtime-prefix:}") String realtimePrefix,
            @Value("${chatbot.s3.realtime-lookback-days:3}") int realtimeLookbackDays,
            @Value("${chatbot.s3.allow-broad-realtime-scan:false}") boolean allowBroadRealtimeScan
    ) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
        this.latestKeyTemplate = latestKeyTemplate;
        this.realtimePrefix = realtimePrefix;
        this.realtimeLookbackDays = Math.max(1, realtimeLookbackDays);
        this.allowBroadRealtimeScan = allowBroadRealtimeScan;
    }

    public Optional<SensorSnapshot> loadLatest(String equipmentId, String processId) {
        if (!StringUtils.hasText(bucketName)) {
            log.warn("S3 bucket name is empty. Skipping S3 sensor context lookup for equipmentId={}.", equipmentId);
            return Optional.empty();
        }

        Optional<SensorSnapshot> latestFile = loadFromLatestFile(equipmentId, processId);
        if (latestFile.isPresent()) {
            log.info("Loaded latest S3 sensor context for equipmentId={}, source={}",
                    equipmentId, latestFile.get().getSource());
            return latestFile;
        }

        if (!StringUtils.hasText(realtimePrefix)) {
            log.info("No S3 latest sensor file found for equipmentId={} and realtime prefix is empty.", equipmentId);
            return Optional.empty();
        }

        Optional<SensorSnapshot> realtimeFile = loadFromRealtimePrefix(equipmentId, processId);
        realtimeFile.ifPresentOrElse(
                snapshot -> log.info("Loaded fallback S3 sensor context for equipmentId={}, source={}",
                        equipmentId, snapshot.getSource()),
                () -> log.info("No fallback S3 sensor context found for equipmentId={}, realtimePrefix={}",
                        equipmentId, realtimePrefix)
        );
        return realtimeFile;
    }

    private Optional<SensorSnapshot> loadFromLatestFile(String equipmentId, String processId) {
        String key = resolveTemplate(latestKeyTemplate, equipmentId);
        log.info("Loading S3 latest sensor context: bucket={}, key={}", bucketName, key);
        try {
            return Optional.of(loadSnapshot(equipmentId, processId, key, false));
        } catch (NoSuchKeyException e) {
            log.info("S3 latest sensor key does not exist: bucket={}, key={}", bucketName, key);
            return Optional.empty();
        } catch (RuntimeException | IOException e) {
            log.warn("Failed to load S3 latest sensor context: bucket={}, key={}, error={}",
                    bucketName, key, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SensorSnapshot> loadFromRealtimePrefix(String equipmentId, String processId) {
        String resolvedRealtimePrefix = resolveTemplate(realtimePrefix, equipmentId);
        // 날짜/설비 단위 prefix부터 조회해 전체 연도 prefix를 훑는 시간을 줄인다.
        List<String> candidatePrefixes = realtimeCandidatePrefixes(resolvedRealtimePrefix, equipmentId);
        log.info("Searching fallback S3 realtime sensor context: bucket={}, prefixes={}",
                bucketName, candidatePrefixes);

        AggregatedSensorSnapshot aggregated = new AggregatedSensorSnapshot(equipmentId, processId);
        for (String candidatePrefix : candidatePrefixes) {
            listRealtimeObjects(candidatePrefix)
                    .stream()
                    .filter(object -> object.key().endsWith(".json"))
                    .filter(object -> containsPathSegment(object.key(), equipmentId))
                    .sorted(Comparator.comparing(S3Object::lastModified).reversed())
                    .map(object -> loadMatchingSnapshot(equipmentId, processId, object.key()))
                    .flatMap(Optional::stream)
                    .forEach(aggregated::accept);

            if (aggregated.hasSamples()) {
                break;
            }
        }

        return aggregated.toSnapshot();
    }

    private List<String> realtimeCandidatePrefixes(String resolvedRealtimePrefix, String equipmentId) {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        String basePrefix = ensureTrailingSlash(resolvedRealtimePrefix);

        if (containsPathSegment(basePrefix, equipmentId)) {
            prefixes.add(basePrefix);
            return List.copyOf(prefixes);
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int daysAgo = 0; daysAgo < realtimeLookbackDays; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            prefixes.add(basePrefix + REALTIME_DATE_FORMATTER.format(date) + "/" + equipmentId + "/");
        }

        if (allowBroadRealtimeScan) {
            prefixes.add(basePrefix);
        }

        return List.copyOf(prefixes);
    }

    private String ensureTrailingSlash(String value) {
        if (!StringUtils.hasText(value) || value.endsWith("/")) {
            return value;
        }
        return value + "/";
    }

    private List<S3Object> listRealtimeObjects(String resolvedRealtimePrefix) {
        List<S3Object> objects = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(resolvedRealtimePrefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            var response = s3Client.listObjectsV2(requestBuilder.build());
            objects.addAll(response.contents());
            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        return objects;
    }

    private Optional<SensorSnapshot> loadMatchingSnapshot(String equipmentId, String processId, String key) {
        try {
            return Optional.of(loadSnapshot(equipmentId, processId, key, true));
        } catch (RuntimeException | IOException e) {
            log.debug("Skipped S3 realtime sensor object that does not match context: bucket={}, key={}, error={}",
                    bucketName, key, e.getMessage());
            return Optional.empty();
        }
    }

    private SensorSnapshot loadSnapshot(
            String equipmentId,
            String processId,
            String key,
            boolean requireIdentityFields
    ) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            JsonNode root = objectMapper.readTree(response);
            boolean identityResolvedFromKey = containsPathSegment(key, equipmentId);
            boolean requireIdentityInPayload = requireIdentityFields && !identityResolvedFromKey;
            JsonNode sensor = selectSensorNode(root, equipmentId, requireIdentityInPayload)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No sensor measurement matched equipmentId=" + equipmentId));
            validateIdentity(sensor, equipmentId, processId, requireIdentityInPayload);
            Map<String, Double> sensorValues = extractConfiguredSensorValues(root, sensor, equipmentId);

            return SensorSnapshot.builder()
                    .equipmentId(textOrDefault(sensor, "equipmentId", equipmentId))
                    .processId(textOrDefault(sensor, "processId", processId))
                    .source("s3://" + bucketName + "/" + key)
                    .measuredAt(firstText(root, sensor, MEASURED_AT_ALIASES))
                    .temperature(doubleValue(sensor, TEMPERATURE_ALIASES))
                    .pressure(doubleValue(sensor, PRESSURE_ALIASES))
                    .speed(doubleValue(sensor, SPEED_ALIASES))
                    .vibration(doubleValue(sensor, VIBRATION_ALIASES))
                    .humidity(doubleValue(sensor, HUMIDITY_ALIASES))
                    .latestSensorValues(sensorValues)
                    .build();
        }
    }

    private Optional<JsonNode> selectSensorNode(JsonNode root, String equipmentId, boolean requireIdentityFields) {
        JsonNode measurements = root.path("measurements");
        if (!measurements.isArray()) {
            return Optional.of(root);
        }

        JsonNode fallback = measurements.isEmpty() ? root : measurements.get(measurements.size() - 1);
        for (JsonNode item : measurements) {
            if (equipmentId.equals(item.path("equipmentId").asText())) {
                return Optional.of(item);
            }
        }
        return requireIdentityFields ? Optional.empty() : Optional.of(fallback);
    }

    private void validateIdentity(
            JsonNode sensor,
            String equipmentId,
            String processId,
            boolean requireIdentityFields
    ) {
        String sensorEquipmentId = textValue(sensor, "equipmentId");
        if (requireIdentityFields && !StringUtils.hasText(sensorEquipmentId)) {
            throw new IllegalArgumentException("S3 sensor object does not contain equipmentId.");
        }
        if (StringUtils.hasText(sensorEquipmentId) && !equipmentId.equals(sensorEquipmentId)) {
            throw new IllegalArgumentException("S3 sensor equipmentId does not match requested equipmentId.");
        }

        String sensorProcessId = textValue(sensor, "processId");
        if (StringUtils.hasText(processId)
                && StringUtils.hasText(sensorProcessId)
                && !processId.equals(sensorProcessId)) {
            throw new IllegalArgumentException("S3 sensor processId does not match resolved processId.");
        }
    }

    private String resolveTemplate(String template, String equipmentId) {
        return template.replace("{equipmentId}", equipmentId);
    }

    private boolean containsPathSegment(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return ("/" + key + "/").contains("/" + value + "/");
    }

    private String firstText(JsonNode root, JsonNode sensor, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String sensorValue = textValue(sensor, fieldName);
            if (sensorValue != null) {
                return sensorValue;
            }

            String rootValue = textValue(root, fieldName);
            if (rootValue != null) {
                return rootValue;
            }
        }
        return null;
    }

    private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = textValue(node, fieldName);
        return value == null ? defaultValue : value;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private Double doubleValue(JsonNode node, String... fieldAliases) {
        Set<String> normalizedAliases = normalizedSet(fieldAliases);
        return directNumberValue(node, normalizedAliases)
                .or(() -> metricObjectValue(node, normalizedAliases))
                .or(() -> recursiveNumberValue(node, normalizedAliases))
                .orElse(null);
    }

    private Optional<Double> directNumberValue(JsonNode node, Set<String> normalizedAliases) {
        if (!node.isObject()) {
            return Optional.empty();
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (normalizedAliases.contains(normalize(field.getKey()))) {
                Optional<Double> value = numberFromNode(field.getValue());
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Double> metricObjectValue(JsonNode node, Set<String> normalizedAliases) {
        if (node.isObject()) {
            Optional<String> metricName = metricName(node);
            if (metricName.isPresent() && normalizedAliases.contains(normalize(metricName.get()))) {
                return valueFromMetricObject(node);
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                Optional<Double> value = metricObjectValue(fields.next().getValue(), normalizedAliases);
                if (value.isPresent()) {
                    return value;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                Optional<Double> value = metricObjectValue(item, normalizedAliases);
                if (value.isPresent()) {
                    return value;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Double> recursiveNumberValue(JsonNode node, Set<String> normalizedAliases) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (normalizedAliases.contains(normalize(field.getKey()))) {
                    Optional<Double> value = numberFromNode(field.getValue());
                    if (value.isPresent()) {
                        return value;
                    }
                }

                Optional<Double> nestedValue = recursiveNumberValue(field.getValue(), normalizedAliases);
                if (nestedValue.isPresent()) {
                    return nestedValue;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                Optional<Double> value = recursiveNumberValue(item, normalizedAliases);
                if (value.isPresent()) {
                    return value;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<String> metricName(JsonNode node) {
        if (!node.isObject()) {
            return Optional.empty();
        }

        Set<String> fieldAliases = normalizedSet(METRIC_NAME_FIELD_ALIASES);
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (fieldAliases.contains(normalize(field.getKey())) && field.getValue().isValueNode()) {
                String value = field.getValue().asText();
                if (StringUtils.hasText(value)) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Double> valueFromMetricObject(JsonNode node) {
        if (!node.isObject()) {
            return Optional.empty();
        }

        Set<String> fieldAliases = normalizedSet(VALUE_FIELD_ALIASES);
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (fieldAliases.contains(normalize(field.getKey()))) {
                Optional<Double> value = numberFromNode(field.getValue());
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Double> numberFromNode(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (value.isNumber()) {
            return Optional.of(value.asDouble());
        }
        if (value.isTextual()) {
            try {
                return Optional.of(Double.parseDouble(value.asText().trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (value.isObject()) {
            return valueFromMetricObject(value);
        }
        return Optional.empty();
    }

    private Set<String> normalizedSet(String... values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            normalized.add(normalize(value));
        }
        return normalized;
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

    private Map<String, Double> extractConfiguredSensorValues(
            JsonNode root,
            JsonNode selectedSensor,
            String equipmentId
    ) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String sensorType : expectedSensorTypes(equipmentId)) {
            String[] aliases = sensorTypeAliases(sensorType);
            Double value = doubleValue(selectedSensor, aliases);
            if (value == null) {
                value = doubleValue(root, aliases);
            }
            if (value != null) {
                values.put(sensorType, value);
            }
        }
        return values;
    }

    private List<String> expectedSensorTypes(String equipmentId) {
        return EQUIPMENT_SENSOR_TYPES.getOrDefault(equipmentId, List.of());
    }

    private String[] sensorTypeAliases(String sensorType) {
        if ("Spin Speed".equals(sensorType)) {
            return concat(new String[]{sensorType}, SPEED_ALIASES);
        }
        if ("Soft Bake Temperature".equals(sensorType)
                || "Chuck Temperature".equals(sensorType)
                || "Chemical Temperature".equals(sensorType)) {
            return concat(new String[]{sensorType}, TEMPERATURE_ALIASES);
        }
        if ("Chamber Pressure".equals(sensorType)) {
            return concat(new String[]{sensorType}, PRESSURE_ALIASES);
        }
        return new String[]{sensorType};
    }

    private String[] concat(String[] first, String[] second) {
        String[] values = new String[first.length + second.length];
        System.arraycopy(first, 0, values, 0, first.length);
        System.arraycopy(second, 0, values, first.length, second.length);
        return values;
    }

    private static Map<String, List<String>> buildEquipmentSensorTypes() {
        Map<String, List<String>> equipmentSensorTypes = new LinkedHashMap<>();
        equipmentSensorTypes.put("EQP-DEPOSITION-001", PROCESS_SENSOR_TYPES.get("DEPOSITION"));
        equipmentSensorTypes.put("EQP-DEPOSITION-002", PROCESS_SENSOR_TYPES.get("DEPOSITION"));
        equipmentSensorTypes.put("EQP-PHOTO-001", PROCESS_SENSOR_TYPES.get("PHOTO"));
        equipmentSensorTypes.put("EQP-PHOTO-002", PROCESS_SENSOR_TYPES.get("PHOTO"));
        equipmentSensorTypes.put("EQP-PHOTO-003", PROCESS_SENSOR_TYPES.get("PHOTO"));
        equipmentSensorTypes.put("EQP-PHOTO-004", PROCESS_SENSOR_TYPES.get("PHOTO"));
        equipmentSensorTypes.put("EQP-ETCH-001", PROCESS_SENSOR_TYPES.get("ETCH"));
        equipmentSensorTypes.put("EQP-ETCH-002", PROCESS_SENSOR_TYPES.get("ETCH"));
        equipmentSensorTypes.put("EQP-CLEANING-001", PROCESS_SENSOR_TYPES.get("CLEANING"));
        equipmentSensorTypes.put("EQP-CLEANING-002", PROCESS_SENSOR_TYPES.get("CLEANING"));
        return Map.copyOf(equipmentSensorTypes);
    }

    private static class AggregatedSensorSnapshot {

        private final String equipmentId;
        private final String processId;
        private SensorSnapshot latest;
        private int sampleCount;
        private final MetricStats temperature = new MetricStats();
        private final MetricStats pressure = new MetricStats();
        private final MetricStats speed = new MetricStats();
        private final MetricStats vibration = new MetricStats();
        private final MetricStats humidity = new MetricStats();
        private final Map<String, MetricStats> sensorValueStats = new LinkedHashMap<>();

        AggregatedSensorSnapshot(String equipmentId, String processId) {
            this.equipmentId = equipmentId;
            this.processId = processId;
        }

        void accept(SensorSnapshot snapshot) {
            if (latest == null) {
                latest = snapshot;
            }

            sampleCount++;
            temperature.accept(snapshot.getTemperature());
            pressure.accept(snapshot.getPressure());
            speed.accept(snapshot.getSpeed());
            vibration.accept(snapshot.getVibration());
            humidity.accept(snapshot.getHumidity());
            if (snapshot.getLatestSensorValues() != null) {
                snapshot.getLatestSensorValues().forEach((name, value) ->
                        sensorValueStats.computeIfAbsent(name, ignored -> new MetricStats()).accept(value));
            }
        }

        Optional<SensorSnapshot> toSnapshot() {
            if (sampleCount == 0 || latest == null) {
                return Optional.empty();
            }

            return Optional.of(SensorSnapshot.builder()
                    .equipmentId(equipmentId)
                    .processId(processId)
                    .source(latest.getSource())
                    .measuredAt(latest.getMeasuredAt())
                    .temperature(latest.getTemperature())
                    .pressure(latest.getPressure())
                    .speed(latest.getSpeed())
                    .vibration(latest.getVibration())
                    .humidity(latest.getHumidity())
                    .latestSensorValues(latest.getLatestSensorValues())
                    .sampleCount(sampleCount)
                    .averageTemperature(temperature.average())
                    .minTemperature(temperature.min())
                    .maxTemperature(temperature.max())
                    .averagePressure(pressure.average())
                    .minPressure(pressure.min())
                    .maxPressure(pressure.max())
                    .averageSpeed(speed.average())
                    .minSpeed(speed.min())
                    .maxSpeed(speed.max())
                    .averageVibration(vibration.average())
                    .minVibration(vibration.min())
                    .maxVibration(vibration.max())
                    .averageHumidity(humidity.average())
                    .minHumidity(humidity.min())
                    .maxHumidity(humidity.max())
                    .averageSensorValues(sensorValues(MetricStats::average))
                    .minSensorValues(sensorValues(MetricStats::min))
                    .maxSensorValues(sensorValues(MetricStats::max))
                    .build());
        }

        boolean hasSamples() {
            return sampleCount > 0;
        }

        private Map<String, Double> sensorValues(java.util.function.Function<MetricStats, Double> mapper) {
            Map<String, Double> values = new LinkedHashMap<>();
            sensorValueStats.forEach((name, stats) -> {
                Double value = mapper.apply(stats);
                if (value != null) {
                    values.put(name, value);
                }
            });
            return values;
        }
    }

    private static class MetricStats {

        private int count;
        private double sum;
        private Double min;
        private Double max;

        void accept(Double value) {
            if (value == null) {
                return;
            }

            count++;
            sum += value;
            min = min == null ? value : Math.min(min, value);
            max = max == null ? value : Math.max(max, value);
        }

        Double average() {
            if (count == 0) {
                return null;
            }
            return Math.round((sum / count) * 100.0) / 100.0;
        }

        Double min() {
            return min;
        }

        Double max() {
            return max;
        }
    }
}