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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RecipeContextResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecipeContextResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RecipeRecommendationContext resolve(RecipeRecommendDto.Request request) {
        List<String> warnings = new ArrayList<>();

        Optional<ProductionContext> productionContext = findLatestProductionContext(request);
        if (productionContext.isEmpty()) {
            warnings.add("No recent production context was found in lot_info.");
        }

        String processId = firstText(request.getProcessId(), productionContext.map(ProductionContext::processId).orElse(null));
        String productId = firstText(request.getProductId(), productionContext.map(ProductionContext::productId).orElse(null));
        Long lotId = productionContext.map(ProductionContext::lotId).orElse(null);
        Long masterRecipeId = productionContext.map(ProductionContext::masterRecipeId).orElse(null);

        String defectType = firstText(request.getDefectType(), findLatestDefectType(request.getEquipmentId(), processId, productId).orElse(null));
        if (!StringUtils.hasText(defectType)) {
            String eqpId = request.getEquipmentId();
            if ("1".equals(eqpId) || "2".equals(eqpId)) defectType = "THICKNESS_NON_UNIFORM";
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
                warnings.add("No defect type was provided and no recent defect type was found in defect_info.");
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
            currentRecipeSource = "RDS equipment_recipe/equipment_recipe_detail";
        } else if (currentRecipe == null) {
            Optional<RecipeParameter> recipeFromRds = findCurrentRecipe(
                    request.getEquipmentId(),
                    processId,
                    productId,
                    masterRecipeId
            );
            if (recipeFromRds.isPresent()) {
                currentRecipe = recipeFromRds.get();
                currentRecipeSource = "RDS equipment_recipe/equipment_recipe_detail";
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

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    LATEST_PRODUCTION_CONTEXT_QUERY,
                    params,
                    this::mapProductionContext
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<String> findLatestDefectType(String equipmentId, String processId, String productId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", equipmentId)
                .addValue("processId", blankToNull(processId))
                .addValue("productId", blankToNull(productId));

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    LATEST_DEFECT_TYPE_QUERY,
                    params,
                    String.class
            ));
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

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    CURRENT_RECIPE_QUERY,
                    params,
                    this::mapRecipeParameter
            ));
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

        return jdbcTemplate.query(CURRENT_RECIPE_PARAMETERS_QUERY, params, this::mapRecipeParameterValue);
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

    private static final String LATEST_PRODUCTION_CONTEXT_QUERY = """
            SELECT
                l.lot_id,
                CAST(l.process_id AS CHAR) AS process_id,
                CAST(l.product_id AS CHAR) AS product_id,
                l.master_recipe_id
            FROM lot_info l
            WHERE CAST(l.equipment_id AS CHAR) = :equipmentId
                AND (:processId IS NULL OR CAST(l.process_id AS CHAR) = :processId)
                AND (:productId IS NULL OR CAST(l.product_id AS CHAR) = :productId)
            ORDER BY l.lot_id DESC
            LIMIT 1
            """;

    private static final String LATEST_DEFECT_TYPE_QUERY = """
            SELECT d.defect_type
            FROM defect_info d
            JOIN lot_info l
                ON l.lot_id = d.lot_id
            WHERE CAST(l.equipment_id AS CHAR) = :equipmentId
                AND (:processId IS NULL OR CAST(l.process_id AS CHAR) = :processId)
                AND (:productId IS NULL OR CAST(l.product_id AS CHAR) = :productId)
            ORDER BY d.defect_id DESC
            LIMIT 1
            """;

    private static final String CURRENT_RECIPE_PARAMETERS_QUERY = """
            WITH latest_recipe AS (
                SELECT er.equipment_rec_id
                FROM equipment_recipe er
                JOIN master_recipe mr
                    ON mr.master_recipe_id = er.master_recipe_id
                WHERE CAST(er.equipment_id AS CHAR) = :equipmentId
                    AND (:processId IS NULL OR CAST(mr.process_id AS CHAR) = :processId)
                    AND (:productId IS NULL OR CAST(mr.product_id AS CHAR) = :productId)
                    AND (:masterRecipeId IS NULL OR mr.master_recipe_id = :masterRecipeId)
                ORDER BY er.equipment_rec_id DESC
                LIMIT 1
            )
            SELECT
                erd.param,
                erd.min AS min_value,
                erd.max AS max_value
            FROM latest_recipe lr
            JOIN equipment_recipe_detail erd
                ON erd.equipment_rec_id = lr.equipment_rec_id
            ORDER BY erd.param
            """;

    private static final String CURRENT_RECIPE_QUERY = """
            WITH latest_recipe AS (
                SELECT er.equipment_rec_id
                FROM equipment_recipe er
                JOIN master_recipe mr
                    ON mr.master_recipe_id = er.master_recipe_id
                WHERE CAST(er.equipment_id AS CHAR) = :equipmentId
                    AND (:processId IS NULL OR CAST(mr.process_id AS CHAR) = :processId)
                    AND (:productId IS NULL OR CAST(mr.product_id AS CHAR) = :productId)
                    AND (:masterRecipeId IS NULL OR mr.master_recipe_id = :masterRecipeId)
                ORDER BY er.equipment_rec_id DESC
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
            JOIN equipment_recipe_detail erd
                ON erd.equipment_rec_id = lr.equipment_rec_id
            GROUP BY lr.equipment_rec_id
            HAVING temperature IS NOT NULL
                AND pressure IS NOT NULL
                AND speed IS NOT NULL
                AND duration IS NOT NULL
            """;
}