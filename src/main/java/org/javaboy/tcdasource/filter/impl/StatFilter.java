package org.javaboy.tcdasource.filter.impl;

import org.javaboy.tcdasource.filter.Filter;
import org.javaboy.tcdasource.filter.FilterChain;
import org.javaboy.tcdasource.proxy.PreparedStatementProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author:majin.wj
 */
public class StatFilter implements Filter {

    public static final int DEFAULT_SLOW_SQL = 1000 * 3;
    private long slowSqlMills = DEFAULT_SLOW_SQL;
    private static final Logger LOG = LoggerFactory.getLogger(StatFilter.class);


    @Override
    public ResultSet executeQuery(FilterChain chain, PreparedStatementProxy statement) throws SQLException {
        beforeStatementExecute(statement);
        ResultSet result = chain.executeQuery(statement);
        afterStatementExecute(statement);
        return result;
    }

    @Override
    public int executeUpdate(FilterChainImpl chain, PreparedStatementProxy statement) throws SQLException {
        beforeStatementExecute(statement);
        int result = chain.executeUpdate(statement);
        afterStatementExecute(statement);
        return result;
    }

    void beforeStatementExecute(PreparedStatementProxy statement) {
        statement.setLastExecuteTime(System.currentTimeMillis());
    }

    void afterStatementExecute(PreparedStatementProxy statement) {
        long now = System.currentTimeMillis();
        long executeTime = now - statement.getLastExecuteTime();
        if (executeTime > slowSqlMills) {
            String sql = statement.getSql();
            List<Object> parameters = statement.getParameters();
            LOG.error("slow sql:{} param:{} executeTime:{}", sql, parameters, executeTime);
        }

    }
}
