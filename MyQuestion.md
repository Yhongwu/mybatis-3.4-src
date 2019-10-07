### 官网中文：
http://www.mybatis.org/mybatis-3/zh/configuration.html#databaseIdProvider
### 缓存模块
装饰者模式  
BlockingCache  
中getObject会获取锁，如果获取不到缓
存数据，不会释放锁，在putObject才会释放锁，
如果get之后不调用put,锁就永远不会释放了？？