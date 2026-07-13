# Spring框架与MyBatis 面试真题（牛客面经）

什么是 IoC 和 AOP？介绍一下
Spring IoC 是什么，解决什么问题？IoC 容器的核心原理是什么？如何实现依赖注入？
Spring IoC 容器启动流程？Bean 的生命周期是怎样的？
Spring Bean 底层存储结构、三级缓存、Map 的 key 是什么？
Spring 怎么解决循环依赖？怎么解决 Bean 的冲突？
Bean 单例怎么实现？Bean 的作用域有哪些？怎么决定它是单例的还是什么？
定义 Bean 的时候有哪些需要注意的？多线程下需要注意些什么？
有哪些依赖注入的方式，有什么区别，推荐用哪一个？
@Autowired 和 @Resource 的区别？
Spring IoC 怎么自动装配 Bean？Spring 是如何实现自动装配的？
Spring 常见注解？@Controller、@Service、@Repository 注解各自作用？
Spring 启动类注解的原理？启动类注解里面有什么注解？
除了启动类，还有什么方式启动 Spring？

---

Spring AOP 的原理？大概流程和机制是什么？
AOP 支持的两种动态代理是什么？默认用哪个动态代理？
JDK 动态代理和 CGLIB 动态代理的区别？什么时候使用 CGLIB 代理？CGLIB 动态代理实现原理？
AOP 的具体使用场景？
切面代码报错是否会影响核心业务流程？如何处理？
拦截器的实现原理？是在哪一层实现的？
传统的 Filter 和 SpringMVC 里面的拦截器有什么区别？

---

Spring 事务传播机制
Spring 有哪些启动事务的方式？
事务注解如何实现原子性的？事务注解这块有什么需要注意的？
@Transactional 失效的场景？解决方法是什么？
Spring 事务中 try-catch 捕获到异常还会回滚吗？Spring 事务哪些情况不会回滚？
private 方法事务会不会生效？

---

Spring 和 SpringBoot 的区别和关系？SpringBoot 的核心优势是什么？
SpringBoot 的自动装配 / 自动配置原理？
SpringBoot 的启动流程？启动原理？打的包结构是怎么样的？服务是怎么启动的？
SpringBoot Starter 是什么？怎么理解 starter？自己写过 starter 吗？
SpringBoot 和 SpringMVC 的区别
SpringBoot 的端口怎么配？怎么读配置文件？
Spring 框架用到了什么设计模式？

---

MyBatis 注解有哪些？XML 是怎么写的？
MyBatis 中 #{} 与 ${} 有什么区别？
MyBatis 怎么实现批量插入？插入数据的时候怎么知道自增 ID？
MyBatis 的 selectOne 和 selectList 底层实现有什么区别？
MyBatis 中绑定 SQL 入参有哪几种方式？
如何避免 MyBatis 结果字段与实体属性不一致？
MyBatis 的一级缓存和二级缓存用过吗？
MyBatis foreach 作用，核心属性？
MyBatis 的切面操作了解吗？能做哪些事情？
MyBatis 和 MyBatisPlus 区别，接口怎么实现的，用到 AOP 了吗？
分页查询的方案有哪些？
怎么防止 SQL 注入？SQL 注入是什么？
