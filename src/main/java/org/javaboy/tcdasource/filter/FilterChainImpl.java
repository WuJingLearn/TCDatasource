package org.javaboy.tcdasource.filter;


import org.javaboy.tcdasource.proxy.PreparedStatementProxy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author:majin.wj
 */
public class FilterChainImpl implements FilterChain {

    private List<Filter> filters;
    private int pos;

    public FilterChainImpl(List<Filter> filters) {
        this.filters = filters;
    }


    @Override
    public ResultSet executeQuery(PreparedStatementProxy preparedStatement) throws SQLException {
        if (pos < filters.size()) {
            return nextFilter().executeQuery(this, preparedStatement);
        }
        return preparedStatement.getRawStatement().executeQuery();
    }


    @Override
    public int executeUpdate(PreparedStatementProxy preparedStatement) throws SQLException {
        if (pos < filters.size()) {
            return nextFilter().executeUpdate(this, preparedStatement);
        }
        return preparedStatement.getRawStatement().executeUpdate();
    }

    private Filter nextFilter() {
        return filters.get(pos++);
    }

}
