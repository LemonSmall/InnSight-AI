package com.sushijia.hotel.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TenantPoiSchemaMigrator {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        List<ColumnSpec> columns = List.of(
            new ColumnSpec("poi_provider", "VARCHAR(30) NULL COMMENT '现实酒店绑定来源：amap/baidu/tencent'"),
            new ColumnSpec("poi_id", "VARCHAR(80) NULL COMMENT '地图 POI ID'"),
            new ColumnSpec("poi_name", "VARCHAR(150) NULL COMMENT '地图返回酒店名称'"),
            new ColumnSpec("poi_address", "VARCHAR(300) NULL COMMENT '地图返回详细地址'"),
            new ColumnSpec("poi_province", "VARCHAR(80) NULL"),
            new ColumnSpec("poi_city", "VARCHAR(80) NULL"),
            new ColumnSpec("poi_district", "VARCHAR(80) NULL"),
            new ColumnSpec("poi_adcode", "VARCHAR(20) NULL"),
            new ColumnSpec("poi_longitude", "DECIMAL(12,6) NULL"),
            new ColumnSpec("poi_latitude", "DECIMAL(12,6) NULL"),
            new ColumnSpec("poi_type_code", "VARCHAR(30) NULL"),
            new ColumnSpec("poi_type_name", "VARCHAR(120) NULL"),
            new ColumnSpec("poi_verified", "TINYINT(1) NOT NULL DEFAULT 0"),
            new ColumnSpec("poi_synced_at", "DATETIME NULL")
        );
        for (ColumnSpec column : columns) {
            addColumnIfMissing(column);
        }
    }

    private void addColumnIfMissing(ColumnSpec column) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'tenants'
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            column.name()
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE tenants ADD COLUMN " + column.name() + " " + column.definition());
    }

    private record ColumnSpec(String name, String definition) {
    }
}
