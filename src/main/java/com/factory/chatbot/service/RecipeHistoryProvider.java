package com.factory.chatbot.service;

import com.factory.chatbot.dto.RecipeParameter;
import com.factory.chatbot.dto.RecipeHistoryCase;

import com.factory.chatbot.dto.RecipeParameterValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecipeHistoryProvider {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String mgmtDb;

    public RecipeHistoryProvider(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${chatbot.db.management-name:management_db}") String managementDbName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mgmtDb = managementDbName;
    }

    @SuppressWarnings("deprecated")
    public List<RecipeHistoryCase> findRelevantHistories(
            String equipmentId,
            String processId,
            String productId,
            String defectType
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentId", equipmentId)
                .addValue("processId", processId)
                .addValue("productId", productId)
                .addValue("defectType", defectType);

        String historyQuery = """
                SELECT
                    er.id AS equipment_rec_id,
                    CAST(er.equipment_id AS CHAR) AS equipment_id,
                    CAST(mr.process_id AS CHAR) AS process_id,
                    CAST(mr.product_id AS CHAR) AS product_id,
                    :defectType AS defect_type,
                    COUNT(DISTINCT d.id) AS defect_count,
                    COALESCE(SUM(DISTINCT l.product_qty), 0) AS product_quantity,
                    CASE
                        WHEN COALESCE(SUM(DISTINCT l.product_qty), 0) > 0
                            THEN ROUND((COUNT(DISTINCT d.id) / COALESCE(SUM(DISTINCT l.product_qty), 0)) * 100, 4)
                        ELSE 0
                    END AS defect_rate
                FROM %s.equipment_recipes er
                JOIN %s.master_recipes mr
                    ON mr.id = er.master_recipe_id
                LEFT JOIN %s.lots l
                    ON l.equipment_id = er.equipment_id
                    AND l.process_id = mr.process_id
                    AND l.product_id = mr.product_id
                    AND l.master_recipe_id = mr.id
                LEFT JOIN anomaly_db.defects d
                    ON d.lot_id = l.id
                    AND d.defect_type = :defectType
                WHERE CAST(er.equipment_id AS CHAR) = :equipmentId
                    AND (:processId IS NULL OR CAST(mr.process_id AS CHAR) = :processId)
                    AND (:productId IS NULL OR CAST(mr.product_id AS CHAR) = :productId)
                GROUP BY
                    er.id,
                    er.equipment_id,
                    mr.process_id,
                    mr.product_id
                ORDER BY defect_rate ASC, er.id DESC
                LIMIT 10
                """.formatted(mgmtDb, mgmtDb, mgmtDb, mgmtDb);

        List<RecipeHistoryCase> histories = jdbcTemplate.query(historyQuery, params, this::mapHistoryCase);
        histories.forEach(history -> history.getParameters().addAll(findParameters(history.getId())));
        return histories;
    }

    private RecipeHistoryCase mapHistoryCase(ResultSet rs, int rowNum) throws SQLException {
        return RecipeHistoryCase.builder()
                .id(rs.getLong("equipment_rec_id"))
                .equipmentId(rs.getString("equipment_id"))
                .processId(rs.getString("process_id"))
                .productId(rs.getString("product_id"))
                .defectType(rs.getString("defect_type"))
                .parameters(new java.util.ArrayList<>())
                .defectRate(rs.getDouble("defect_rate"))
                .defectCount(rs.getInt("defect_count"))
                .productQuantity(rs.getInt("product_quantity"))
                .build();
    }

    private List<RecipeParameterValue> findParameters(Long equipmentRecipeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("equipmentRecipeId", equipmentRecipeId);

        String sql = """
                SELECT
                    erd.param,
                    erd.min AS min_value,
                    erd.max AS max_value
                FROM %s.equipment_recipe_details erd
                WHERE erd.equipment_recipe_id = :equipmentRecipeId
                ORDER BY erd.param
                """.formatted(mgmtDb);

        return jdbcTemplate.query(sql, params, this::mapParameter);
    }

    private RecipeParameterValue mapParameter(ResultSet rs, int rowNum) throws SQLException {
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
                .build();
    }
}