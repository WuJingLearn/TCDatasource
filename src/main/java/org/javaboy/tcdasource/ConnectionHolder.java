package org.javaboy.tcdasource;

/**
 * @author:majin.wj
 */
public class ConnectionHolder {

    private TCConnection connection;

    private TCDataSource dataSource;
    /**
     * 上一次被使用的时间
     */
    private long lastActiveTimeInMills;

    public ConnectionHolder(){

    }

    public ConnectionHolder(TCConnection connection,TCDataSource dataSource) {
        this.connection = connection;
        this.dataSource = dataSource;
        connection.bindToConnectionHolder(this);
    }


    public TCConnection getConnection() {
        return connection;
    }

    public TCDataSource getDataSource() {
        return dataSource;
    }

    public long getLastActiveTimeInMills() {
        return lastActiveTimeInMills;
    }

    public void setLastActiveTimeInMills(long lastActiveTimeInMills) {
        this.lastActiveTimeInMills = lastActiveTimeInMills;
    }
}
