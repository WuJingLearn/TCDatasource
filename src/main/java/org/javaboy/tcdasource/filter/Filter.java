package org.javaboy.tcdasource.filter;


import org.javaboy.tcdasource.proxy.PreparedStatementProxy;

import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * @author:majin.wj
 */
public interface Filter {

    ResultSet executeQuery(FilterChain chain, PreparedStatementProxy statement) throws SQLException;

    int executeUpdate(FilterChainImpl filterChain, PreparedStatementProxy preparedStatement) throws SQLException;
}
