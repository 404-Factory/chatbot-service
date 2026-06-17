package com.factory.chatbot.service;
import com.factory.chatbot.dto.RecipeRecommendDto;

import com.factory.chatbot.dto.RecipeRecommendationContext;

import com.factory.chatbot.dto.RecipeParameter;
import com.factory.chatbot.dto.RecipeParameterValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecipeContextResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String mgmtDb;

    public RecipeContextResolver(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${chatbot.db.management-name:management_db}") String managementDbName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mgmtDb = managementDbName;
    }

    public RecipeRecommendationContext resolve(RecipeRecommendDto.Request request) {
        List<String> warnings = new ArrayList<>();

        Optional<ProductionContext> productionContext = findLatestProductionContext(request);
        if (productionContext.isEmpty()) {
            warnings.add("No recent production context was found in lots.");
        }

        String processId = firstText(request.getProcessId(), productionContext.map(ProductionContext::processId).orElse(null));
        String productId = firstText(request.getProductId(), productionContext.map(ProductionContext::productId).orElse(null));
        Long lotId = productionContext.map(ProductionContext::lotId).orElse(null);
        Long masterRecipeId = productionContext.map(ProductionContext::masterRecipeId).orElse(null);

        String defectType = firstText(request.getDefectType(), findLatestDefectType(request.getEquipmentId(), processId, productId).orElse(null));
        if (!StringUtils.hasText(defectType)) {
            String eqpId = request.getEquipmentId();
            if ("1".equals(eqpId) || "2".equals(eqpId)) defectType = "THICKNESS";
            else if ("3".equals(eqpId)) defectType = "CD";
            else if ("4".equals(eqpId)) defectType = "OVER_EXPOSURE";
            else if ("5".equals(eqpId)) defectType = "UNDER_EXPOSURE";
            else if ("6".equals(eqpId)) defectType = "DEVELOP_POOR";
            else if ("7".equals(eqpId)) defectType = "PATTERN";
            else if ("8".equals(eqpId)) defectType = "OVER_ETCHING";
            else if ("9".equals(eqpId)) defectType = "RESIDUE";
            else if ("10".equals(eqpId)) defectType = "BOTTOM_LAYER_DAMAGE";

            if (StringUtils.hasText(defectType)) {
                warnings.add("No defect type was provided/found; fallback to default: " + defectType);
            } else {
                warnings.add("No defect type was provided and no recent defect type was found in defects.");
            }
        }

        RecipeParameter currentRecipe = request.getCurrentRecipe();
        List<RecipeParameterValue> currentRecipeParameters = findCurrentRecipeParameters(
                request.getEquipmentId(),
                processId,
                productId,
                masterRecipeId
        );
        String currentRecipeSource = "request";
        if (!currentRecipeParameters.isEmpty()) {
            currentRecipeSource = "RDS equipment_recipes/equipment_recipe_details";
        } else if (currentRecipe == null) {
            Optional<RecipeParameter> recipeFromRds = findCurrentRecipe(
                    request.getEquipmentId(),
                    processId,
                    productId,
                    masterRecipeId
            );
            if (recipeFromRds.isPresent()) {
                currentRecipe = recipeFromRds.get();
                currentRecipeSource = "RDS equipment_recipes/equipment_recipe_details";
            } else {
                currentRecipeSource = null;
                warnings.add("No current recipe was provided and no matching recipe was found in RDS.");
            }
        }

        return RecipeRecommendationContext.builder()
                .equipmentId(request.getEquipmentId())
                .processId(processId)
                .productId(productId)
                .defectType(defectType)
                .currentRecipe(currentRecipe)
                .currentRecipeParameters(currentRecipeParameters)
                .currentRecipeSource(currentRecipeSource)
                .lotId(lotId)
                .masterRecipeId(masterRecipeId)
                .warnings(warnings)
                .build();
    }

    private Optional<ProductionContext> findLatestProductionContext(RecipeRecommendDto.Request request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", request.getEquipmentId())
                .addValue("processId", blankToNull(request.getProcessId()))
                .addValue("productId", blankToNull(request.getProductId()));

        String sql = """
                SELECT
                    l.id AS lot_id,
                    CAST(l.process_id AS CHAR) AS process_id,
                    CAST(l.product_id AS CHAR) AS product_id,
                    l.master_recipe_id
                FROM %s.lots l
                WHERE CAST(l.equipment_id AS CHAR) = :equipmentId
                    AND (:processId IS NULL OR CAST(l.process_id AS CHAR) = :processId)
                    AND (:productId IS NULL OR CAST(l.product_id AS CHAR) = :productId)
                ORDER BY l.id DESC
                LIMIT 1
                """.formatted(mgmtDb);

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, this::mapProductionContext));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<String> findLatestDefectType(String equipmentId, String processId, String productId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", equipmentId)
                .addValue("processId", blankToNull(processId))
                .addValue("productId", blankToNull(productId));

        String sql = """
                SELECT d.defect_type
                FROM anomaly_db.defects d
                WHERE CAST(d.cause_equipment_id AS CHAR) = :equipmentId
                    AND (:processId IS NULL OR CAST(d.cause_process_id AS CHAR) = :processId)
                ORDER BY d.id DESC
                LIMIT 1
                """;

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, String.class));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<RecipeParameter> findCurrentRecipe(
            String equipmentId,
            String processId,
            String productId,
            Long masterRecipeId
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", equipmentId)
                .addValue("processId", blankToNull(processId))
                .addValue("productId", blankToNull(productId))
                .addValue("masterRecipeId", masterRecipeId);

        String sql = """
                WITH latest_recipe AS (
                    SELECT er.id AS equipment_rec_id
                    FROM %s.equipment_recipes er
                    JOIN %s.master_recipes mr
                        ON mr.id = er.master_recipe_id
                    WHERE CAST(er.equipment_id AS CHAR) = :equipmentId
                        AND (:processId IS NULL OR CAST(mr.process_id AS CHAR) = :processId)
                        AND (:productId IS NULL OR CAST(mr.product_id AS CHAR) = :productId)
                        AND (:masterRecipeId IS NULL OR mr.id = :masterRecipeId)
                    ORDER BY er.id DESC
                    LIMIT 1
                )
                SELECT
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('temperature', 'temp')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS temperature,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('pressure', 'press')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS pressure,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('speed', 'rpm')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS speed,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('duration', 'time')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS duration
                FROM latest_recipe lr
                JOIN %s.equipment_recipe_details erd
                    ON erd.equipment_recipe_id = lr.equipment_rec_id
                GROUP BY lr.equipment_rec_id
                HAVING temperature IS NOT NULL
                    AND pressure IS NOT NULL
                    AND speed IS NOT NULL
                    AND duration IS NOT NULL
                """.formatted(mgmtDb, mgmtDb, mgmtDb);

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, this::mapRecipeParameter));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private List<RecipeParameterValue> findCurrentRecipeParameters(
            String equipmentId,
            String processId,
            String productId,
            Long masterRecipeId
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", equipmentId)
                .addValue("processId", blankToNull(processId))
                .addValue("productId", blankToNull(productId))
                .addValue("masterRecipeId", masterRecipeId);

        String sql = """
                WITH latest_recipe AS (
                    SELECT er.id AS equipment_rec_id
                    FROM %s.equipment_recipes er
                    JOIN %s.master_recipes mr
                        ON mr.id = er.master_recipe_id
                    WHERE CAST(er.equipment_id AS CHAR) = :equipmentId
                        AND (:processId IS NULL OR CAST(mr.process_id AS CHAR) = :processId)
                        AND (:productId IS NULL OR CAST(mr.product_id AS CHAR) = :productId)
                        AND (:masterRecipeId IS NULL OR mr.id = :masterRecipeId)
                    ORDER BY er.id DESC
                    LIMIT 1
                )
                SELECT
                    erd.param,
                    erd.min AS min_value,
                    erd.max AS max_value
                FROM latest_recipe lr
                JOIN %s.equipment_recipe_details erd
                    ON erd.equipment_recipe_id = lr.equipment_rec_id
                ORDER BY erd.param
                """.formatted(mgmtDb, mgmtDb, mgmtDb);

        return jdbcTemplate.query(sql, params, this::mapRecipeParameterValue);
    }

    private ProductionContext mapProductionContext(ResultSet rs, int rowNum) throws SQLException {
        return new ProductionContext(
                rs.getLong("lot_id"),
                rs.getString("process_id"),
                rs.getString("product_id"),
                rs.getLong("master_recipe_id")
        );
    }

    private RecipeParameter mapRecipeParameter(ResultSet rs, int rowNum) throws SQLException {
        return new RecipeParameter(
                rs.getDouble("temperature"),
                rs.getDouble("pressure"),
                rs.getDouble("speed"),
                rs.getDouble("duration")
        );
    }

    private RecipeParameterValue mapRecipeParameterValue(ResultSet rs, int rowNum) throws SQLException {
        Double min = rs.getObject("min_value") == null ? null : rs.getDouble("min_value");
        Double max = rs.getObject("max_value") == null ? null : rs.getDouble("max_value");
        Double currentValue = null;
        if (min != null && max != null) {
            currentValue = Math.round(((min + max) / 2.0) * 10.0) / 10.0;
        }

        return RecipeParameterValue.builder()
                .name(rs.getString("param"))
                .min(min)
                .max(max)
                .currentValue(currentValue)
                .recommendedValue(null)
                .unit(null)
                .build();
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record ProductionContext(
            Long lotId,
            String processId,
            String productId,
            Long masterRecipeId
    ) {
    }
}