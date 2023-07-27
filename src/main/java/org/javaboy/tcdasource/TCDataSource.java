package org.javaboy.tcdasource;


import org.javaboy.tcdasource.execption.GetConnectionTimeoutException;
import org.javaboy.tcdasource.filter.Filter;
import org.javaboy.tcdasource.util.JdbcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author:majin.wj
 */
public class TCDataSource extends AbstractTiaoCaoDataSource {

    private static final String DATA_SOURCE_INNER_INFO = "datasource.detail";
    private static final Logger LOG = LoggerFactory.getLogger(DATA_SOURCE_INNER_INFO);

    /**
     * 30 mini
     */
    private static final long DEFAULT_MIN_EVICTABLE_IDLE_TIMEMILLIS = 1000L * 60L * 30L;
    /**
     * 7 hour
     */
    private static final long DEFAULT_MAX_EVICTABLE_IDLE_TIMEMILLIS = 1000L * 60L * 60L * 7;

    /**
     * 1mini
     */
    private static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = 60 * 1000L;

    private static final int DEFAULT_INITIAL_SIZE = 0;
    private static final int DEFAULT_MIN_IDLE_SIZE = 0;
    private final static int DEFAULT_MAX_ACTIVE_SIZE = 8;

    /**
     * 连接池初始化时创建的数量
     */
    private int initialSize = DEFAULT_INITIAL_SIZE;
    /**
     * 空闲连接池中最小的连接数，需要配合minEvictableIdleTimeMillis时间
     * 可以淘汰的连接数为poolCount-minIdle
     */
    private int minIdle = DEFAULT_MIN_IDLE_SIZE;
    /**
     * 最大连接数 minIdle+activeCount <= maxActive
     */
    private int maxActive = DEFAULT_MAX_ACTIVE_SIZE;
    /**
     * 活跃的连接，即被拿走的连接
     */
    private int activeCount;
    /**
     * connectionPool中连接数量,即空闲连接数
     */
    private int poolingCount;
    /**
     * 空闲连接池中，可以被淘汰的连接，空闲时间需要满足比minEvictableIdleTimeMillis大
     */
    private long minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIMEMILLIS;
    private long maxEvictableIdleTimeMillis = DEFAULT_MAX_EVICTABLE_IDLE_TIMEMILLIS;
    /**
     * 空闲连接检测任务时间间隔
     */
    private long timeBetweenEvictionRunMills = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * 获取连接最大等待时间,超过时间未获取连接则抛出异常
     */
    private long maxWait = -1;
    /**
     * 当等待连接的线程超过该值，直接返回失败。
     */
    private int maxWaitThreadCount;
    /**
     * 等待连接的线程数
     */
    private int waitThreadNums;
    /**
     * 是否懒加载
     */
    private boolean lazyInit;
    private boolean initized;

    private ConnectionHolder[] connectionPool = new ConnectionHolder[maxActive];

    private List<Filter> filterChain = new ArrayList<>();

    private Lock lock = new ReentrantLock();
    /**
     * for producer
     */
    private Condition empty = lock.newCondition();
    /**
     * for consumer
     */
    private Condition notEmpty = lock.newCondition();

    private Thread createThread;
    private Thread destroyThread;

    public TCDataSource() {
        if (!lazyInit) {
            init();
        }
    }

    private void init() {
        if (initized) {
            return;
        }
        lock.lock();
        try {
            if (initized) {
                return;
            }
            doInit();
            initized = true;
        } finally {
            lock.unlock();
        }
    }

    private void doInit() {
        Assert.isTrue(minIdle <= maxActive, "minIdle must less then maxActive ");
        Assert.isTrue(initialSize >= 0, "initialSize must more then 0");
        Assert.isTrue(initialSize <= maxActive, "initialSize must less then maxActive ");
        Assert.isTrue(minEvictableIdleTimeMillis <= maxEvictableIdleTimeMillis, "minEvictableIdleTimeMillis must less then maxEvictableIdleTimeMillis ");

        for (int i = 0; i < initialSize; i++) {
            try {
                connectionPool[poolingCount++] = createConnectionHolder();
            } catch (SQLException e) {
                LOG.error("failed to create connection when init datasource", e);
            }
        }
        startCreateConnThread();
        startDestroyConnThread();
    }

    private ConnectionHolder createConnectionHolder() throws SQLException {
        TCConnection connection = createPhysicsConnection();
        return new ConnectionHolder(connection, this);
    }

    private TCConnection createPhysicsConnection() throws SQLException {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new SQLException("create connection error", e);
        }
        return new TCConnection(connection);
    }


    private void startCreateConnThread() {
        this.createThread = new Thread(new CreateConnectionRunnable());
        createThread.start();
    }

    /**
     * 什么使用应该创建?
     */
    class CreateConnectionRunnable implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                lock.lock();
                try {
                    LOG.debug("creator get lock");
                    // 需有等待连接时才会创建
                    if (poolingCount >= waitThreadNums) {
                        LOG.debug("creator wait. poolingCount:{},waitThreadNums:{}", poolingCount, waitThreadNums);
                        empty.await();
                        LOG.debug("creator notify");
                    }
                    if (minIdle + activeCount >= maxActive) {
                        LOG.debug("creator wait,minIdle:{},activeCount:{},maxActive:{}", minIdle, activeCount, maxActive);
                        empty.await();
                        LOG.debug("creator notify");
                        continue;
                    }
                    ConnectionHolder connectionHolder = null;
                    try {
                        connectionHolder = createConnectionHolder();
                    } catch (SQLException e) {
                        LOG.error("CreateConnectionRunnable failed to create thread", e);
                        Thread.sleep(3000);
                        continue;
                    }
                    connectionPool[poolingCount++] = connectionHolder;
                    LOG.debug("creator create a connection,poolCount:{}", poolingCount);
                    //唤醒消费者
                    notEmpty.signal();
                } catch (InterruptedException e) {
                    break;
                } finally {
                    lock.unlock();
                }
            }

        }
    }

    private void startDestroyConnThread() {
        this.destroyThread = new Thread(new DestroyConnectionRunnable());
        destroyThread.start();
    }

    /**
     * 如何销毁?
     * poolingCount: 池子里的空闲连接
     * minIdle: 允许存在的空闲连接
     * poolingCount-minIdle: 就是可以被销毁的连接
     */
    class DestroyConnectionRunnable implements Runnable {
        @Override
        public void run() {

            while (true) {
                interval();
                int checkCount = poolingCount - minIdle;
                int evictCount = 0;
                List<ConnectionHolder> evictConnection = new ArrayList<>();
                lock.lock();
                try {
                    for (int i = 0; i < poolingCount; i++) {
                        ConnectionHolder connectionHolder = connectionPool[i];
                        long lastActiveTime = connectionHolder.getLastActiveTimeInMills();
                        long idleTime = System.currentTimeMillis() - lastActiveTime;
                        if (idleTime < minEvictableIdleTimeMillis) {
                            break;
                        }
                        if (evictCount < checkCount) {
                            evictConnection.add(connectionHolder);
                            evictCount++;
                        } else if (idleTime > maxEvictableIdleTimeMillis) { // 空闲时长超过maxEvictableIdleTimeMillis时,销毁
                            evictConnection.add(connectionHolder);
                            evictCount++;
                        }
                    }
                    // 移除连接
                    if (evictCount > 0) {
                        System.arraycopy(connectionPool, evictCount, connectionPool, 0, poolingCount - evictCount);
                        Arrays.fill(connectionPool, poolingCount - evictCount, poolingCount - 1, null);
                        poolingCount -= evictCount;
                    }

                } finally {
                    lock.unlock();
                }
                for (ConnectionHolder connectionHolder : evictConnection) {
                    // 销毁连接
                    TCConnection tccConnection = connectionHolder.getConnection();
                    Connection connection = tccConnection.getRawConnection();
                    JdbcUtil.close(connection);
                }

            }

        }

        void interval() {
            try {
                Thread.sleep(timeBetweenEvictionRunMills);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }


    @Override
    public TCConnection getConnection() throws SQLException {
        init();
        lock.lock();
        try {
            if (maxWaitThreadCount > 0 && waitThreadNums >= maxWaitThreadCount) {
                LOG.error("current wait thread num:{} more then maxWaitThreadCount:{}", waitThreadNums, maxWaitThreadCount);
                throw new SQLException("current wait thread num more then maxWaitThreadCount:" + maxWaitThreadCount);
            }

            if (poolingCount == 0) {
                waitThreadNums++;
                // 向生产者线程发送信号
                empty.signal();
                if (maxWait > 0) {
                    long waitNanos = TimeUnit.MICROSECONDS.toNanos(maxWait);
                    while (poolingCount == 0) {
                        if (waitNanos <= 0) {
                            throw new GetConnectionTimeoutException("failed to get connection waitMills more then:" + maxWait);
                        }
                        LOG.debug("thread:{} wait peeking connection", Thread.currentThread());
                        waitNanos = notEmpty.awaitNanos(waitNanos);
                        LOG.debug("thread:{} notify", Thread.currentThread());
                    }
                } else {
                    while (poolingCount == 0) {
                        LOG.debug("thread:{} wait peeking connection", Thread.currentThread());
                        notEmpty.await();
                        LOG.debug("thread:{} notify", Thread.currentThread());
                    }
                }
                waitThreadNums--;
            }
            LOG.debug("thread:{} peek a  connection,waitThreadNum:{}", Thread.currentThread(), waitThreadNums);
            poolingCount--;
            ConnectionHolder connectionHolder = connectionPool[poolingCount];
            connectionPool[poolingCount] = null;
            activeCount++;
            return connectionHolder.getConnection();
        } catch (InterruptedException e) {
            throw new SQLException("get connection be Interrupted", e);
        } finally {
            lock.unlock();
        }
    }


    public void recycle(TCConnection connection) {
        ConnectionHolder holder = connection.getHolder();
        lock.lock();
        try {
            // 更新上次活跃时间
            holder.setLastActiveTimeInMills(System.currentTimeMillis());
            connectionPool[poolingCount++] = holder;
            activeCount--;
            // 通知有连接可用
            notEmpty.signal();
        } finally {
            lock.unlock();
        }

    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    public void addFilter(Filter filter) {
        this.filterChain.add(filter);
    }

    public List<Filter> getFilters() {
        return this.filterChain;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    public long getMaxEvictableIdleTimeMillis() {
        return maxEvictableIdleTimeMillis;
    }

    public void setMaxEvictableIdleTimeMillis(long maxEvictableIdleTimeMillis) {
        this.maxEvictableIdleTimeMillis = maxEvictableIdleTimeMillis;
    }

    public long getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(long maxWait) {
        this.maxWait = maxWait;
    }

    public int getMaxWaitThreadCount() {
        return maxWaitThreadCount;
    }

    public void setMaxWaitThreadCount(int maxWaitThreadCount) {
        this.maxWaitThreadCount = maxWaitThreadCount;
    }

    public int getWaitThreadNums() {
        return waitThreadNums;
    }

    public void setWaitThreadNums(int waitThreadNums) {
        this.waitThreadNums = waitThreadNums;
    }

    public boolean isLazyInit() {
        return lazyInit;
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }
}
