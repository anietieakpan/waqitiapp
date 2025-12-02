package com.waqiti.common.database.dto;

import lombok.Data;
import java.util.List;

/**
 * Query analysis result containing information about SQL query structure and characteristics.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class QueryAnalysis {
    private String sql;
    private String operation;
    private String tableName;
    private boolean hasWhereClause;
    private boolean hasJoin;
    private boolean hasOrderBy;
    private boolean hasGroupBy;
    private String queryType;
    private List<String> tables;
    private List<String> columns;
    private List<String> whereConditions;
    private boolean hasJoins;
    private boolean hasSubqueries;
    private boolean hasFullTableScan;
    private int complexity;
}