package com.wontlost.ckeditor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle 数据库 Schema 迁移
 * 修复 Oracle CHECK 约束以支持新增的枚举值
 *
 * 背景：Hibernate 为 @Enumerated(EnumType.STRING) 列生成 CHECK 约束，
 * 当枚举新增值时，旧约束会阻止插入。此迁移在启动时自动修复。
 */
@Component("oracleSchemaMigration")
@ConditionalOnProperty(name = "app.sync.oracle-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OracleSchemaMigration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(OracleSchemaMigration.class);

    @Autowired
    @Qualifier("oracleDataSource")
    private DataSource oracleDataSource;

    @Override
    public void afterPropertiesSet() {
        migrateSourceColumnConstraint();
    }

    /**
     * 查找并删除 subscribers.source 列上的旧 CHECK 约束
     * 不重新创建约束 — Java 枚举已在应用层保证值的合法性
     */
    private void migrateSourceColumnConstraint() {
        try (Connection conn = oracleDataSource.getConnection()) {
            log.info("Oracle 迁移: 开始检查 subscribers.source 列约束...");

            // 先列出所有 CHECK 约束用于诊断
            listAllCheckConstraints(conn);

            // 查找需要删除的约束
            List<String> constraintsToDrop = findSourceEnumConstraints(conn);

            if (constraintsToDrop.isEmpty()) {
                log.info("Oracle 迁移: 未找到需要修改的 source 枚举 CHECK 约束");
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                for (String constraintName : constraintsToDrop) {
                    stmt.execute("ALTER TABLE SUBSCRIBERS DROP CONSTRAINT \"" + constraintName + "\"");
                    log.info("Oracle 迁移完成: 删除旧 CHECK 约束 {}", constraintName);
                }
            }
        } catch (Exception e) {
            log.error("Oracle schema 迁移失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 诊断：列出 SUBSCRIBERS 表上的所有 CHECK 约束
     */
    private void listAllCheckConstraints(Connection conn) {
        String sql = """
            SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC
            FROM USER_CONSTRAINTS
            WHERE TABLE_NAME = 'SUBSCRIBERS'
              AND CONSTRAINT_TYPE = 'C'
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                count++;
                log.info("Oracle 迁移诊断: CHECK 约束 {} = {}",
                    rs.getString("CONSTRAINT_NAME"),
                    rs.getString("SEARCH_CONDITION_VC"));
            }
            if (count == 0) {
                log.info("Oracle 迁移诊断: SUBSCRIBERS 表无 CHECK 约束（可能表名大小写不匹配）");

                // 尝试查所有表的 CHECK 约束
                String allSql = """
                    SELECT TABLE_NAME, CONSTRAINT_NAME, SEARCH_CONDITION_VC
                    FROM USER_CONSTRAINTS
                    WHERE CONSTRAINT_TYPE = 'C'
                      AND TABLE_NAME LIKE '%SUBSCRIBER%'
                    """;
                try (PreparedStatement ps2 = conn.prepareStatement(allSql);
                     ResultSet rs2 = ps2.executeQuery()) {
                    while (rs2.next()) {
                        log.info("Oracle 迁移诊断(扩展): 表 {} 约束 {} = {}",
                            rs2.getString("TABLE_NAME"),
                            rs2.getString("CONSTRAINT_NAME"),
                            rs2.getString("SEARCH_CONDITION_VC"));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Oracle 迁移诊断查询失败: {}", e.getMessage());
        }
    }

    /**
     * 查找 SUBSCRIBERS 表上与 source 枚举相关的 CHECK 约束
     * 通过检查约束条件文本中是否包含已知枚举值来识别
     */
    private List<String> findSourceEnumConstraints(Connection conn) throws SQLException {
        List<String> constraints = new ArrayList<>();

        // 使用 UPPER() 确保大小写无关的匹配
        String sql = """
            SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC
            FROM USER_CONSTRAINTS
            WHERE TABLE_NAME = 'SUBSCRIBERS'
              AND CONSTRAINT_TYPE = 'C'
              AND (UPPER(SEARCH_CONDITION_VC) LIKE '%COPY_CODE%'
                   OR UPPER(SEARCH_CONDITION_VC) LIKE '%DOWNLOAD_FILE%')
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("CONSTRAINT_NAME");
                String condition = rs.getString("SEARCH_CONDITION_VC");
                log.info("Oracle 迁移: 找到 source 枚举约束 {} = {}", name, condition);
                // 跳过已包含 ANONYMOUS 的约束（已迁移过）
                if (condition != null && condition.toUpperCase().contains("ANONYMOUS")) {
                    log.info("Oracle 迁移: CHECK 约束 {} 已包含 ANONYMOUS，跳过", name);
                    continue;
                }
                constraints.add(name);
            }
        } catch (SQLException e) {
            log.warn("SEARCH_CONDITION_VC 查询失败，尝试备选方案: {}", e.getMessage());
            return findSourceEnumConstraintsLegacy(conn);
        }

        return constraints;
    }

    /**
     * 备选方案：通过 DBMS_METADATA 获取约束定义
     */
    private List<String> findSourceEnumConstraintsLegacy(Connection conn) throws SQLException {
        List<String> constraints = new ArrayList<>();

        String sql = """
            SELECT CONSTRAINT_NAME
            FROM USER_CONSTRAINTS
            WHERE TABLE_NAME = 'SUBSCRIBERS'
              AND CONSTRAINT_TYPE = 'C'
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("CONSTRAINT_NAME");
                String ddl = getConstraintDdl(conn, name);
                log.info("Oracle 迁移(备选): 约束 {} DDL = {}", name, ddl);
                if (ddl != null && ddl.toUpperCase().contains("COPY_CODE") && !ddl.toUpperCase().contains("ANONYMOUS")) {
                    constraints.add(name);
                }
            }
        }

        return constraints;
    }

    /**
     * 通过 DBMS_METADATA 获取约束的 DDL 定义
     */
    private String getConstraintDdl(Connection conn, String constraintName) {
        String sql = "SELECT DBMS_METADATA.GET_DDL('CONSTRAINT', ?) FROM DUAL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            log.debug("获取约束 {} DDL 失败: {}", constraintName, e.getMessage());
        }
        return null;
    }
}
