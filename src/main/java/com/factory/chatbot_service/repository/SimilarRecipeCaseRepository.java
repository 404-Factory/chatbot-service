package com.factory.chatbot_service.repository;

import com.factory.chatbot_service.dto.SimilarRecipeCase;

import com.factory.chatbot_service.dto.RecipeParameter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SimilarRecipeCaseRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SimilarRecipeCaseRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SimilarRecipeCase> findTop3ByEquipmentIdAndProcessIdAndProductIdAndDefectTypeOrderByDefectRateAsc(
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

        return jdbcTemplate.query(RECIPE_QUERY, params, this::mapSimilarRecipeCase);
    }

    private SimilarRecipeCase mapSimilarRecipeCase(ResultSet rs, int rowNum) throws SQLException {
        RecipeParameter recipe = new RecipeParameter(
                rs.getDouble("temperature"),
                rs.getDouble("pressure"),
                rs.getDouble("speed"),
                rs.getDouble("duration")
        );

        return SimilarRecipeCase.builder()
                .id(rs.getLong("equipment_rec_id"))
                .equipmentId(rs.getString("equipment_id"))
                .processId(rs.getString("process_id"))
                .productId(rs.getString("product_id"))
                .defectType(rs.getString("defect_type"))
                .recipe(recipe)
                .defectRate(rs.getDouble("defect_rate"))
                .defectCount(rs.getInt("defect_count"))
                .productQuantity(rs.getInt("product_quantity"))
                .build();
    }

    private static final String RECIPE_QUERY = """
            WITH recipe_params AS (
                SELECT
                    er.equipment_rec_id,
                    er.equipment_id,
                    mr.process_id,
                    mr.product_id,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('temperature', 'temp', '온도')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS temperature,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('pressure', 'press', '압력')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS pressure,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('speed', 'rpm', '속도')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS speed,
                    AVG(CASE
                        WHEN LOWER(erd.param) IN ('duration', 'time', '시간', '공정시간')
                            THEN (COALESCE(erd.min, 0) + COALESCE(erd.max, 0)) / 2
                    END) AS duration
                FROM equipment_recipe er
                JOIN master_recipe mr
                    ON mr.master_recipe_id = er.master_recipe_id
                JOIN equipment_recipe_detail erd
                    ON erd.equipment_rec_id = er.equipment_rec_id
                GROUP BY
                    er.equipment_rec_id,
                    er.equipment_id,
                    mr.process_id,
                    mr.product_id
            ),
            quality_metrics AS (
                SELECT
                    er.equipment_rec_id,
                    COUNT(DISTINCT d.defect_id) AS defect_count,
                    COALESCE(SUM(DISTINCT l.product_qty), 0) AS product_quantity
                FROM equipment_recipe er
                JOIN master_recipe mr
                    ON mr.master_recipe_id = er.master_recipe_id
                LEFT JOIN lot_info l
                    ON l.equipment_id = er.equipment_id
                    AND l.process_id = mr.process_id
                    AND l.product_id = mr.product_id
                    AND l.master_recipe_id = mr.master_recipe_id
                LEFT JOIN defect_info d
                    ON d.lot_id = l.lot_id
                    AND d.defect_type = :defectType
                GROUP BY er.equipment_rec_id
            )
            SELECT
                rp.equipment_rec_id,
                CAST(rp.equipment_id AS CHAR) AS equipment_id,
                CAST(rp.process_id AS CHAR) AS process_id,
                CAST(rp.product_id AS CHAR) AS product_id,
                :defectType AS defect_type,
                rp.temperature,
                rp.pressure,
                rp.speed,
                rp.duration,
                qm.defect_count,
                qm.product_quantity,
                CASE
                    WHEN qm.product_quantity > 0
                        THEN ROUND((qm.defect_count / qm.product_quantity) * 100, 4)
                    ELSE 0
                END AS defect_rate
            FROM recipe_params rp
            JOIN quality_metrics qm
                ON qm.equipment_rec_id = rp.equipment_rec_id
            WHERE CAST(rp.equipment_id AS CHAR) = :equipmentId
                AND CAST(rp.process_id AS CHAR) = :processId
                AND CAST(rp.product_id AS CHAR) = :productId
                AND rp.temperature IS NOT NULL
                AND rp.pressure IS NOT NULL
                AND rp.speed IS NOT NULL
                AND rp.duration IS NOT NULL
            ORDER BY defect_rate ASC, rp.equipment_rec_id DESC
            LIMIT 3
            """;
}