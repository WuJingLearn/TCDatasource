package org.javaboy.tcdasource.unittest;


import org.javaboy.tcdasource.TCConnection;
import org.javaboy.tcdasource.TCDataSource;
import org.javaboy.tcdasource.filter.LogFilter;
import org.javaboy.tcdasource.filter.StatFilter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Test {


    public static void main(String[] args) throws SQLException {
        String url = "jdbc:mysql://localhost:3306/student?serverTimezone=UTC&characterEncoding=utf8&useUnicode=true&useSSL=false&allowPublicKeyRetrieval=true";
        TCDataSource datasource = new TCDataSource();
        datasource.setUrl(url);
        datasource.setPassword("root1234");
        datasource.setUsername("root");

        datasource.setMaxActive(10);
        datasource.setMaxWaitThreadCount(10);

        datasource.addFilter(new LogFilter());
        datasource.addFilter(new StatFilter());
        test1(datasource.getConnection());
    }

    static void test2(TCDataSource dataSource) {
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try {
                    TCConnection connection = dataSource.getConnection();
                    System.out.println(connection);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

            }).start();
        }
    }

    static void test1(TCConnection connection) throws SQLException {
        System.out.println(connection.getRawConnection());
        PreparedStatement statement = connection.prepareStatement("select * from a where score = ?");
        statement.setInt(1, 100);
        ResultSet result = statement.executeQuery();
        while (result.next()) {
            String name = result.getString(1);
            int age = result.getInt(2);
            System.out.println("name:" + name + ",age:" + age);
        }
    }


}
