

## TCDataSource

一款简单的数据库连接池,提供了基本的连接管理,以及慢Sql打印,sql执行记录功能;
通过Proxy机制增强jdbc原生对象,通过可配置的Filter责任链,可以在jdbc操作过程进行额外的业务处理


## 基本使用

```
String url = "jdbc:mysql://localhost:3306/student?serverTimezone=UTC&characterEncoding=utf8&useUnicode=true&useSSL=false&allowPublicKeyRetrieval=true";
TCDataSource datasource = new TCDataSource();
datasource.setUrl(url);
datasource.setPassword("root1234");
datasource.setUsername("root");
datasource.setLazyInit(false);
datasource.setInitialSize(5);
datasource.setMinIdle(5);
datasource.setMaxActive(10);
datasource.setMaxWaitThreadCount(10);
datasource.setMinEvictableIdleTimeMillis(1000L * 60L * 30L);
datasource.setMaxEvictableIdleTimeMillis(1000 * 60 * 60 * 7);
datasource.setTimeBetweenEvictionRunMills(1000 * 60);
datasource.addFilter(new LogFilter());
StatFilter statFilter = new StatFilter();
statFilter.setSlowSqlMills(1000 * 3L);
datasource.addFilter(statFilter);
Connection connection = datasource.getConnection();
```

## 核心参数
* activeCount: 活跃连接数
* poolCount: 空闲连接数
* maxActive: 允许的最大连接数 即 activeCount + poolCount <= maxActive
* minIdle: 最小空闲连接数，当空闲连接超过minIdle时. 会进行空闲连接检测,可被回收的连接数最小是poolCount - minIdle
* minEvictableIdleTimeMills: 空闲连接回收最小满足时间,需要满足连接的空闲时间需要超过该值
* maxEvictableIdleTimeMills: 空闲连接强制回收时间,当连接空闲时间超过maxEvictableIdleTimeMills,会被直接回收,即使当前空闲数不超过minIdle
* timeBetweenEvictionRunMills: 空闲线程回收任务每次执行间隔时间
* maxWaitThreadCount: 在获取连接时,如果已经有maxWaitThreadCount线程在等待连接,会直接报错返回
* maxActive: 获取连接的超时时间,超过maxActive还未获取连接时,抛出GetConnectionTimeoutException异常。


## 作者介绍

* 姓名: 吴靖
* 花名: 码劲

* 工作经历\
 杭州智云健康集团      2020.7 - 2022.4  中间件开发工程师\
 阿里巴巴乌鸫科技      2022.4 - 至今     java工程师



