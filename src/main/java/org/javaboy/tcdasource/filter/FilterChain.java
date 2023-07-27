package org.javaboy.tcdasource.filter;


import org.javaboy.tcdasource.proxy.PreparedStatementProxy;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author:majin.wj
 */
public interface FilterChain {

    public ResultSet executeQuery(PreparedStatementProxy preparedStatement) throws SQLException;

    public int executeUpdate(PreparedStatementProxy preparedStatement) throws SQLException;



}
