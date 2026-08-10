# 面渣逆袭 —— Spring

> 来源：面渣逆袭Spring篇V2.0亮白版.pdf

基础

1.Spring是什么？

Spring 是一个 Java 后端开发框架，其最核心的作用是帮我们管理 Java 对象。

其最重要的特性就是 IoC，也就是控制反转。以前我们要使用一个对象时，都要自己先 new 出来。但有了 Spring 之后，我们只需要告诉 Spring 我们需要什么对象，它就会自动帮我们创建好并注入到 Spring 容器当中。比如我在一个 Service 类里需要用到 Dao 对象，只需要加个 @Autowired 注解，Spring 就会自动把 Dao 对象注入到 Spring 容器当中，这样就不需要我们手动去管理这些对象之间的依赖关系了。

另外，Spring 还提供了 AOP，也就是面向切面编程，在我们需要做一些通用功能的时候特别有用，比如说日志记录、权限校验、事务管理这些，我们不用在每个方法里都写重复的代码，直接用 AOP 就能统一处理。

Spring 的生态也特别丰富，像 Spring Boot 能让我们快速搭建项目，Spring MVC 能帮我们处理 web 请求，Spring Data 能帮我们简化数据库操作，Spring Cloud 能帮我们做微服务架构等等。

Spring有哪些特性？

Spring 的特性还是挺多的，我按照在实际工作/学习中用得最多的几个来说吧。

首先最核心的就是 IoC 控制反转和 DI 依赖注入。这个我前面也提到了，就是 Spring 能帮我们管理对象的创建和依赖关系。比如我写一个 UserService，需要用到 UserDao，以前得自己 new 一个 UserDao 出来，现在只要在 UserService 上加个 @Service 注解，在 UserDao 字段上加个 @Autowired ，Spring 就会自动帮我们处理好这些依赖关系。这样代码的耦合度就大大降低了，测试的时候也更容易 mock。

第二个就是 AOP 面向切面编程。这个在我们处理一些横切关注点的时候特别有用，比如说我们要给某些 Controller 方法都加上权限控制，如果没有 AOP 的话，每个方法都要写一遍加权代码，维护起来很麻烦。

用了 AOP 之后，我们只需要写一个切面类，定义好切点和通知，就能统一处理了。事务管理也是同样的道理，加个 @Transactional 注解就搞定了。

还有就是 Spring 对各种企业级功能的集成支持也特别好。比如数据库访问，不管我们用 JDBC、MyBatis-Plus 还是 Hibernate，Spring 都能很好地集成。消息队列、缓存、安全认证这些， Spring 都有对应的模块来支持。

另外 Spring 的配置也很灵活，既支持 XML 配置，也支持注解配置，现在我们基本都用注解了，写起来更简洁。Spring Boot 出来之后就更方便了，约定大于配置，很多东西都是开箱即用的。

简单说一下什么是AOP和IoC？

AOP 面向切面编程，简单点说就是把一些通用的功能从业务代码里抽取出来，统一处理。比如说技术派中的 @MdcDot 注解的作用是配合 AOP 在日志中加入 MDC 信息，方便进行日志追踪。

IoC 控制反转是一种设计思想，它的主要作用是将对象的创建和对象之间的调用过程交给 Spring 容器来管理。比如说在技术派项目当中，@PostConstruct 注解表明这个方法由 Spring 容器在 Bean 初始化完成后自动调用，我们不需要手动调用 init 方法。

Spring源码看过吗？

看过一些，主要是带着问题去看的，比如遇到一些技术难点或者想深入理解某个功能的时候。

我重点看过的是 IoC 容器的初始化过程，特别是 ApplicationContext 的启动流程。从 refresh() 方法开始，包括 Bean 的定义和加载、Bean 工厂的准备、Bean 的实例化和初始化这些关键步骤。

看源码的时候发现 Spring 用了很多设计模式，比如工厂模式、单例模式、模板方法模式等等，这对我平时写代码也很有启发。

还有就是 Spring 的 Bean 生命周期，从 BeanDefinition 的创建到 Bean 的实例化、属性注入、初始化回调，再到最后的销毁，整个过程还是挺复杂的。看了源码之后对 @PostConstruct 、@PreDestroy 这些注解的执行时机就更清楚了。

不过说实话，Spring 的源码确实比较难啃，涉及的概念和技术点太多了。我一般是结合一些技术博客和 Claude 一起看，这样理解起来会相对容易一些。

1. Java 面试指南（付费）收录的京东面经同学 5 Java 后端技术一面面试原题：IOC与AOP

2.Spring有哪些模块呢？

我按照平时工作/学习中接触的频率来说一下。

首先是 Spring Core 模块，这是整个 Spring 框架的基础，包含了 IoC 容器和依赖注入等核心功能。还有 Spring Beans 模块，负责 Bean 的配置和管理。这两个模块基本上是其他所有模块的基础，不管用 Spring 的哪个功能都会用到。

然后是 Spring Context 上下文模块，它在 Core 的基础上提供了更多企业级的功能，比如国际化、事件传播、资源加载这些。ApplicationContext 就是在这个模块里面的。

Spring AOP 模块提供了面向切面编程的支持，我们用的 @Transactional 、自定义切面这些都是基于这个模块。

Web 开发方面，Spring Web 模块提供了基础的 Web 功能，Spring WebMVC 就是我们常用的 MVC 框架，用来处理 HTTP 请求和响应。现在还有 Spring WebFlux，支持响应式编程。

比如说技术派项目中，GlobalExceptionHandler 类就使用了 @RestControllerAdvice 来实现统一的异常处理。

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // Spring Boot会自动创建ApplicationContext
        ApplicationContext context = SpringApplication.run(Application.class, args);
    }
}

数据访问方面，Spring JDBC 简化了 JDBC 的使用，在技术派项目中，我们就 JdbcTemplate 来检查表是否存在、执行数据库初始化脚本。

Spring ORM 提供了对 MyBatis-Plus 等 ORM 框架的集成支持。在技术派项目中，我们就用了 @TableName 、@TableField 等注解进行对象关系映射，通过继承 BaseMapper 来获取通用的 CRUD 能力。

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = ForumAdviceException.class)
    public ResVo<String> handleForumAdviceException(ForumAdviceException e) {
        return ResVo.fail(e.getStatus());
    }
}

Spring Test 模块提供了测试支持，可以很方便地进行单元测试和集成测试。我们写测试用例的时候经常用 @SpringBootTest 这些注解。比如说在技术派项目中，我们就用 @SpringBootTest 注解来加载 Spring 上下文，进行集成测试。

还有一些其他的模块，比如 Spring Security 负责安全认证，Spring Batch 处理批处理任务等等。

现在我们基本都是用 Spring Boot 来开发，它把这些模块都整合好了，用起来更方便。

@Slf4j
@SpringBootTest(classes = QuickForumApplication.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class BasicTest {
}

3.Spring有哪些常用注解呢？

Spring 的注解挺多的，我按照不同的功能分类来说一下平时用得最多的那些。

首先是 Bean 管理相关的注解。@Component 是最基础的，用来标识一个类是 Spring 组件。像 @Service 、@Repository 、@Controller 这些都是 @Component 的特化版本，分别用在服务层、数据访问层和控制器层。

依赖注入方面，@Autowired 是用得最多的，可以标注在字段、setter 方法或者构造方法上。@Qualifier 在有多个同类型 Bean 的时候用来指定具体注入哪一个。@Resource 和 @Autowired 功能差不多，不过它是按名称注入的。

配置相关的注解也很常用。@Configuration 标识配置类，@Bean 用来定义 Bean，@Value 用来注入配置文件中的属性值。我们项目里的数据库连接信息、Redis 配置这些都是用 @Value 来注入的。@PropertySource 用来指定配置文件的位置。

Web 开发的注解就更多了。@RestController 相当于 @Controller 加 @ResponseBody ，用来做 RESTful 接口。

@RequestMapping 及其变体@GetMapping 、@PostMapping 、@PutMapping 、@DeleteMapping 用来映射 HTTP 请求。@PathVariable 获取路径参数，@RequestParam 获取请求参数，@RequestBody 接收 JSON 数据。

AOP 相关的注解，@Aspect 定义切面，@Pointcut 定义切点，@Before 、@After 、@Around 这些定义通知类型。

不过我们用得最多的还是@Transactional ，基本上 Service 层需要保证事务原子性的方法都会加上这个注解。

生命周期相关的，@PostConstruct 在 Bean 初始化后执行，@PreDestroy 在 Bean 销毁前执行。测试的时候 @SpringBootTest 也经常用到。

还有一些 Spring Boot 特有的注解，比如 @SpringBootApplication 这个启动类注解，@ConditionalOnProperty 做条件装配，@EnableAutoConfiguration 开启自动配置等等。

1. Java 面试指南（付费）收录的微众银行同学 1 Java 后端一面的原题：说说 Spring 常见的注解？

4.Spring用了哪些设计模式？

Spring 框架里面确实用了很多设计模式，我从平时工作中能观察到的几个来说说。

首先是工厂模式，这个在 Spring 里用得非常多。BeanFactory 就是一个典型的工厂，它负责创建和管理所有的 Bean 对象。我们平时用的 ApplicationContext 其实也是 BeanFactory 的一个实现。当我们通过 @Autowired 获取一个 Bean 的时候，底层就是通过工厂模式来创建和获取对象的。

单例模式也是 Spring 的默认行为。默认情况下，Spring 容器中的 Bean 都是单例的，整个应用中只会有一个实例。这样可以节省内存，提高性能。当然我们也可以通过 @Scope 注解来改变 Bean 的作用域，比如设置为 prototype 就是每次获取都创建新实例。

代理模式在 AOP 中用得特别多。Spring AOP 的底层实现就是基于动态代理的，对于实现了接口的类用 JDK 动态代理，没有实现接口的类用 CGLIB 代理。比如我们用 @Transactional 注解的时候，Spring 会为我们的类创建一个代理对象，在方法执行前后添加事务处理逻辑。

模板方法模式在 Spring 里也很常见，比如 JdbcTemplate。它定义了数据库操作的基本流程：获取连接、执行 SQL、处理结果、关闭连接，但是具体的 SQL 语句和结果处理逻辑由我们来实现。

观察者模式在 Spring 的事件机制中有所体现。我们可以通过 ApplicationEvent 和 ApplicationListener 来实现事件的发布和监听。比如用户注册成功后，我们可以发布一个用户注册事件，然后有多个监听器来处理后续的业务逻辑，比如发送邮件、记录日志等。

这些设计模式的应用让 Spring 框架既灵活又强大，也让我在实际的开发中学到很多经典的设计思想。

Spring如何实现单例模式？

传统的单例模式是在类的内部控制只能创建一个实例，比如用 private 构造方法加 static getInstance() 这种方式。但是 Spring 的单例是容器级别的，同一个 Bean 在整个 Spring 容器中只会有一个实例。

具体的实现机制是这样的：Spring 在启动的时候会把所有的 Bean 定义信息加载进来，然后在 DefaultSingletonBeanRegistry 这个类⾥⾯维护了⼀个叫 singletonObjects 的 ConcurrentHashMap，这个 Map 
就是⽤来存储单例 Bean 的。key 是 Bean 的名称，value 就是 Bean 的实例对象。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 26 / 180

---

当我们第⼀次获取某个 Bean 的时候，Spring 会先检查 singletonObjects 这个 Map ⾥⾯有没有这个 Bean，如果
没有就会创建⼀个新的实例，然后放到 Map ⾥⾯。后⾯再获取同⼀个 Bean 的时候，直接从 Map ⾥⾯取就⾏了，
这样就保证了单例。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 27 / 180

---

还有⼀个细节就是 Spring 为了解决循环依赖的问题，还⽤了三级缓存。除了 singletonObjects 这个⼀级缓存，还
有 earlySingletonObjects ⼆级缓存和 singletonFactories 三级缓存。这样即使有循环依赖，Spring 也能正确处
理。
⽽且 Spring 的单例是线程安全的，因为⽤的是 ConcurrentHashMap，多线程访问不会有问题。
1. Java ⾯试指南（付费）收录的携程⾯经同学 10 Java 暑期实习⼀⾯⾯试原题：Spring IoC 的设计模式，
AOP 的设计模式
2. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：Spring 框架使⽤到的设计模
式？
3. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：Spring⽤了什么设计模式？
4. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：Spring中使⽤了哪些设计模式，以其中⼀种模式举
例说明？Spring如何实现单例模式？
memo：2025 年 6 ⽉ 20 ⽇修改⾄此，今天帮球友修改简历的时候，有碰到重庆邮电⼤学本，电⼦科技⼤学硕的
球友，期间还有过清华⼤学科研项⽬的经历，基本上也是把学历这块拉的满中满了，那希望星球能帮助到更多院校
的同学，不管是⼯作党还是学⽣党，都希望⼤家都拿到更好的 offer。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 28 / 180

---

5.Spring容器和Web容器之间的区别知道吗？（补充）
 
2024 年 7 ⽉ 11 ⽇增补
⾸先从概念上来说，Spring 容器是⼀个 IoC 容器，主要负责管理 Java 对象的⽣命周期和依赖关系。⽽ Web 容
器，⽐如 Tomcat、Jetty 这些，是⽤来运⾏ Web 应⽤的容器，负责处理 HTTP 请求和响应，管理 Servlet 的⽣命
周期。
从功能上看，Spring 容器专注于业务逻辑层⾯的对象管理，⽐如我们的 Service、Dao、Controller 这些 Bean 都
是由 Spring 容器来创建和管理的。⽽ Web 容器主要处理⽹络通信，⽐如接收 HTTP 请求、解析请求参数、调⽤相
应的 Servlet，然后把响应返回给客户端。
/**
 * SpringUtil.java
 * ⽤于获取 Spring 容器中的 Bean，技术派源码：https://github.com/itwanger/paicoding
 */
@Component
public class SpringUtil implements ApplicationContextAware {
    private volatile static ApplicationContext context;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringUtil.context = applicationContext;
    }
    
    public static <T> T getBean(Class<T> bean) {
        return context.getBean(bean);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 29 / 180

---

在实际项⽬中，这两个容器是相辅相成的。我们的 Web 项⽬部署在 Tomcat 上的时候，Tomcat 会负责接收 HTTP 
请求，然后把请求交给 DispatcherServlet 处理，⽽ DispatcherServlet ⼜会去 Spring 容器中查找相应的 
Controller 来处理业务逻辑。
还有⼀个重要的区别是⽣命周期。Web 容器的⽣命周期跟 Web 应⽤程序的部署和卸载相关，⽽ Spring 容器的⽣
命周期是在 Web 应⽤启动的时候初始化，应⽤关闭的时候销毁。
现在我们都⽤ Spring Boot 了，Spring Boot 内置了 Tomcat，把 Web 容器和 Spring 容器都整合在⼀起了，我们
只需要运⾏⼀个 jar 包就可以了。
/**
 * GlobalViewInterceptor.java
 * ⽤于全局拦截器，技术派源码：https://github.com/itwanger/paicoding
 */
@Component
public class GlobalViewInterceptor implements HandlerInterceptor {
    @Autowired
    private GlobalInitService globalInitService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // Web 容器的 HTTP 请求 + Spring 容器的业务服务
    }
}
@SpringBootApplication
public class QuickForumApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickForumApplication.class, args);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 30 / 180

---

1. Java ⾯试指南（付费）收录的去哪⼉同学 1 技术⼆⾯原题：spring的容器、web容器、springmvc的容
器之间的区别
memo：2024 年 7 ⽉ 11 ⽇修改⾄此，今天在帮球友们修改简历的时候，碰到北京交通⼤学本，北京航空航天⼤
学硕的球友，她的简历上有很多校园荣誉奖项，像优秀学⽣、奖学⾦、英语四六级等，这些都是⾮常好的加分项。
IoC
 
6.
说⼀说什么是IoC？
 
推荐阅读：IoC 扫盲
IoC 的全称是 Inversion of Control，也就是控制反转。这⾥的“控制”指的是对象创建和依赖关系管理的控制权。
以前我们写代码的时候，如果 A 类需要⽤到 B 类，我们就在 A 类⾥⾯直接 new ⼀个 B 对象出来，这样 A 类就控
制了 B 类对象的创建。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 31 / 180

---

有了 IoC 之后，这个控制权就“反转”了，不再由 A 类来控制 B 对象的创建，⽽是交给外部的容器来管理。
----这部分⾯试中可以不背 start----
没有 IoC 之前：
我需要⼀个⼥朋友，刚好⼤街上突然看到了⼀个⼩姐姐，⼈很好看，于是我就⾃⼰主动上去搭讪，要她的微
信号，找机会聊天关⼼她，然后约她出来吃饭，打听她的爱好，三观。。。
有了 IoC 之后：
我需要⼀个⼥朋友，于是我就去找婚介所，告诉婚介所，我需要⼀个⻓的像赵露思的，会打 Dota2 的，于是
婚介所在它的⼈才库⾥开始找，找不到它就直接说没有，找到它就直接介绍给我。
婚介所就相当于⼀个 IoC 容器，我就是⼀个对象，我需要的⼥朋友就是另⼀个对象，我不⽤关⼼⼥朋友是怎么来
的，我只需要告诉婚介所我需要什么样的⼥朋友，婚介所就帮我去找。
// 传统⽅式：对象主动创建依赖
public class UserService {
    private UserDao userDao;
    
    public UserService() {
        // 主动创建依赖对象
        this.userDao = new UserDaoImpl();
    }
}
/** 
 * 使⽤ Spring IoC 容器来管理 UserDao 的创建和注⼊
 * 技术派源码：https://github.com/itwanger/paicoding
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDao userDao;
    
    // 不需要主动创建 UserDao，由 Spring 容器注⼊
    public BaseUserInfoDTO getAndUpdateUserIpInfoBySessionId(String session, String 
clientIp) {
        // 直接使⽤注⼊的 userDao
        return userDao.getBySessionId(session);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 32 / 180

---

----这部分⾯试中可以不背 end----
DI和IoC的区别了解吗？
 
IoC 的思想是把对象创建和依赖关系的控制权由业务代码转移给 Spring 容器。这是⼀个⽐较抽象的概念，告诉我
们应该怎么去设计系统架构。
⽽ DI，也就是依赖注⼊，它是实现 IoC 这种思想的具体技术⼿段。在 Spring ⾥，我们⽤ @Autowired 注解就是在
使⽤ DI 的字段注⼊⽅式。
从实现⻆度来看，DI 除了字段注⼊，还有构造⽅法注⼊和 Setter ⽅法注⼊等⽅式。在做技术派项⽬的时候，我就
尝试过构造⽅法注⼊的⽅式。
@Service
public class ArticleReadServiceImpl implements ArticleReadService {
    @Autowired
    private ArticleDao articleDao;  // 字段注⼊
    
    @Autowired
    private UserDao userDao;
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 33 / 180

---

当然了，DI 并不是实现 IoC 的唯⼀⽅式，还有 Service Locator 模式，可以通过实现 ApplicationContextAware 接
⼝来获取 Spring 容器中的 Bean。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 34 / 180

---

之所以 ID 后成为 IoC 的⾸选实现⽅式，是因为代码更清晰、可读性更⾼。
为什么要使⽤ IoC 呢？
 
在⽇常开发中，如果我们需要实现某⼀个功能，可能⾄少需要两个以上的对象来协助完成，在没有 Spring 之前，
每个对象在需要它的合作对象时，需要⾃⼰ new ⼀个，⽐如说 A 要使⽤ B，A 就对 B 产⽣了依赖，也就是 A 和 B 
之间存在了⼀种耦合关系。
IoC（控制反转）
├── DI（依赖注⼊）          ← 主要实现⽅式
│   ├── 构造器注⼊
│   ├── 字段注⼊
│   └── Setter注⼊
├── 服务定位器模式
├── ⼯⼚模式
└── 其他实现⽅式
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 35 / 180

---

有了 Spring 之后，创建 B 的⼯作交给了 Spring 来完成，Spring 创建好了 B 对象后就放到容器中，A 告诉 Spring 
我需要 B，Spring 就从容器中取出 B 交给 A 来使⽤。
⾄于 B 是怎么来的，A 就不再关⼼了，Spring 容器想通过 newnew 创建 B 还是 new 创建 B，⽆所谓。
这就是 IoC 的好处，它降低了对象之间的耦合度，让每个对象只关注⾃⼰的业务实现，不关⼼其他对象是怎么创建
的。
推荐阅读：孤傲苍狼：谈谈对 Spring IOC 的理解
1. Java ⾯试指南（付费）收录的⼩⽶ 25 届⽇常实习⼀⾯原题：说说你对 AOP 和 IoC 的理解。
2. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：介绍 Spring IoC 和 AOP?
3. Java ⾯试指南（付费）收录的招商银⾏⾯经同学 6 招银⽹络科技⾯试原题：SpringBoot框架的AOP、
IOC/DI？
4. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：IOC，AOP
5. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：解释下什么是IOC和AOP？分别解决了什么问题？
IOC和DI的区别？
memo：2025 年 6 ⽉ 22 ⽇修改⾄此，今天有球友发喜报说拿到了两个 offer，⼀个是做 B 端电商的，另⼀个是外
企，主要做 Power BI 的低代码开发，我的建议是去外企，因为实习最重要的是混个 title，有更多的时间，可以去
学习星球⾥的项⽬，其实会更实在。
// 传统⽅式：对象⾃⼰创建依赖
public class UserService {
    private UserDao userDao = new UserDaoImpl(); // 硬编码依赖
    
    public User getUser(Long id) {
        return userDao.findById(id);
    }
}
// IoC ⽅式：依赖由外部注⼊
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDao userDao; // 依赖注⼊，不关⼼具体实现
    
    public User getUser(Long id) {
        return userDao.findById(id);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 36 / 180

---

7.能说⼀下IoC的实现机制吗？
 
好的，Spring IoC 的实现机制还是⽐较复杂的，我尽量⽤⽐较通俗的⽅式来解释⼀下整个流程。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 37 / 180

---

第⼀步是加载 Bean 的定义信息。Spring 会扫描我们配置的包路径，找到所有标注了 @Component 、@Service 、
@Repository 这些注解的类，然后把这些类的元信息封装成 BeanDefinition 对象。
第⼆步是 Bean ⼯⼚的准备。Spring 会创建⼀个 DefaultListableBeanFactory 作为 Bean ⼯⼚来负责 Bean 的创
建和管理。
// Bean定义信息
public class BeanDefinition {
    private String beanClassName;     // 类名
    private String scope;            // 作⽤域
    private boolean lazyInit;        // 是否懒加载
    private String[] dependsOn;      // 依赖的Bean
    private ConstructorArgumentValues constructorArgumentValues; // 构造参数
    private MutablePropertyValues propertyValues; // 属性值
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 38 / 180

---

第三步是 Bean 的实例化和初始化。这个过程⽐较复杂，Spring 会根据 BeanDefinition 来创建 Bean 实例。
对于单例 Bean，Spring 会先检查缓存中是否已经存在，如果不存在就创建新实例。创建实例的时候会通过反射调
⽤构造⽅法，然后进⾏属性注⼊，最后执⾏初始化回调⽅法。
// 简化的Bean创建流程
public class AbstractBeanFactory {
    
    protected Object createBean(String beanName, BeanDefinition bd) {
        // 1. 实例化前处理
        Object bean = resolveBeforeInstantiation(beanName, bd);
        if (bean != null) {
            return bean;
        }
        
        // 2. 实际创建Bean
        return doCreateBean(beanName, bd);
    }
    
    protected Object doCreateBean(String beanName, BeanDefinition bd) {
        // 2.1 实例化
        Object bean = createBeanInstance(beanName, bd);
        
        // 2.2 属性填充（依赖注⼊）
        populateBean(beanName, bd, bean);
        
        // 2.3 初始化
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 39 / 180

---

依赖注⼊的实现主要是通过反射来完成的。⽐如我们⽤ @Autowired 标注了⼀个字段，Spring 在创建 Bean 的时
候会扫描这个字段，然后从容器中找到对应类型的 Bean，通过反射的⽅式设置到这个字段上。
你是怎么理解 Spring IoC 的？
 
IoC 本质上⼀个超级⼯⼚，这个⼯⼚的产品就是各种 Bean 对象。
        Object exposedObject = initializeBean(beanName, bean, bd);
        
        return exposedObject;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 40 / 180

---

我们通过 @Component 、@Service 这些注解告诉⼯⼚：“我要⽣产什么样的产品，这个产品有什么特性，需要什
么原材料”。
然后⼯⼚⾥各种⽣产线，在 Spring 中就是各种 BeanPostProcessor。⽐如 
AutowiredAnnotationBeanPostProcessor 专⻔负责处理 @Autowired 注解。
⼯⼚⾥还有各种缓存机制⽤来存放产品，⽐如说 singletonObjects 是成品仓库，存放完⼯的单例 Bean；
earlySingletonObjects 是半成品仓库，⽤来解决循环依赖问题。
// Spring单例Bean注册表
public class DefaultSingletonBeanRegistry {
    // ⼀级缓存：完成初始化的单例Bean
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    
    // ⼆级缓存：早期暴露的单例Bean（解决循环依赖）
    private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);
    
    // 三级缓存：单例Bean⼯⼚
    private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
    
    public Object getSingleton(String beanName) {
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
            singletonObject = this.earlySingletonObjects.get(beanName);
            if (singletonObject == null) {
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 41 / 180

---

最有意思的是，这个⼯⼚还很智能，它知道产品之间的依赖关系。它会根据依赖关系来决定 Bean 的创建顺序。如
果发现循环依赖，它还会⽤三级缓存机制来巧妙地解决。
能⼿写⼀个简单的 IoC 容器吗？
 
1、⾸先定义基础的注解，⽐如说 @Component 、@Autowired 等。
2、核⼼的 IoC 容器类，负责扫描包路径，创建 Bean 实例，并处理依赖注⼊。
                ObjectFactory<?> singletonFactory = 
this.singletonFactories.get(beanName);
                if (singletonFactory != null) {
                    singletonObject = singletonFactory.getObject();
                    this.earlySingletonObjects.put(beanName, singletonObject);
                    this.singletonFactories.remove(beanName);
                }
            }
        }
        return singletonObject;
    }
}
// 组件注解
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Component {
}
// ⾃动注⼊注解
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Autowired {
}
public class SimpleIoC {
    // Bean容器
    private Map<Class<?>, Object> beans = new HashMap<>();
    
    /**
     * 注册Bean
     */
    public void registerBean(Class<?> clazz) {
        try {
            // 创建实例
            Object instance = clazz.getDeclaredConstructor().newInstance();
            beans.put(clazz, instance);
        } catch (Exception e) {
            throw new RuntimeException("创建Bean失败: " + clazz.getName(), e);
        }
    }
    
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 42 / 180

---

3、使⽤示例，定义⼀些 Bean 类，并注册到 IoC 容器中。
    /**
     * 获取Bean
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) beans.get(clazz);
    }
    
    /**
     * 依赖注⼊
     */
    public void inject() {
        for (Object bean : beans.values()) {
            injectFields(bean);
        }
    }
    
    /**
     * 字段注⼊
     */
    private void injectFields(Object bean) {
        Field[] fields = bean.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Autowired.class)) {
                try {
                    field.setAccessible(true);
                    Object dependency = getBean(field.getType());
                    field.set(bean, dependency);
                } catch (Exception e) {
                    throw new RuntimeException("注⼊失败: " + field.getName(), e);
                }
            }
        }
    }
}
// DAO层
@Component
class UserDao {
    public void save(String user) {
        System.out.println("保存⽤户: " + user);
    }
}
// Service层
@Component
class UserService {
    @Autowired
    private UserDao userDao;
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 43 / 180

---

4、可以加上组件扫描。
    
    public void createUser(String name) {
        userDao.save(name);
        System.out.println("⽤户创建完成");
    }
}
// 测试
public class Test {
    public static void main(String[] args) {
        SimpleIoC ioc = new SimpleIoC();
        
        // 注册Bean
        ioc.registerBean(UserDao.class);
        ioc.registerBean(UserService.class);
        
        // 依赖注⼊
        ioc.inject();
        
        // 使⽤
        UserService userService = ioc.getBean(UserService.class);
        userService.createUser("王⼆");
    }
}
import java.lang.reflect.Field;
import java.util.*;
public class SimpleIoC {
    private Map<Class<?>, Object> beans = new HashMap<>();
    
    /**
     * 扫描并注册组件
     */
    public void scan(String packageName) {
        // 简化版：⼿动添加需要扫描的类
        List<Class<?>> classes = getClassesInPackage(packageName);
        
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Component.class)) {
                registerBean(clazz);
            }
        }
        
        // 依赖注⼊
        inject();
    }
    
    /**
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 44 / 180

---

IoC 容器的核⼼是管理对象和依赖注⼊，⾸先定义注解，然后实现容器的三个核⼼⽅法：注册Bean、获取Bean、
依赖注⼊；关键是⽤反射创建对象和注⼊依赖。
memo：2025 年 6 ⽉ 23 ⽇修改⾄此，今天有球友发喜报说拿到了京东的社招 offer，这真的要恭喜他，也希望所
有看到这⾥的⼩伙伴都能有⼀个好的结果。
     * 获取包下的类（简化实现）
     */
    private List<Class<?>> getClassesInPackage(String packageName) {
        // ⾯试时可以说："实际实现需要扫描classpath，这⾥简化处理"
        return Arrays.asList(UserDao.class, UserService.class);
    }
    
    private void registerBean(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            beans.put(clazz, instance);
        } catch (Exception e) {
            throw new RuntimeException("创建Bean失败", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) beans.get(clazz);
    }
    
    private void inject() {
        for (Object bean : beans.values()) {
            Field[] fields = bean.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    try {
                        field.setAccessible(true);
                        Object dependency = getBean(field.getType());
                        field.set(bean, dependency);
                    } catch (Exception e) {
                        throw new RuntimeException("注⼊失败", e);
                    }
                }
            }
        }
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 45 / 180

---

8.说说BeanFactory和ApplicantContext的区别?
 
BeanFactory 算是 Spring 的“⼼脏”，⽽ ApplicantContext 可以说是 Spring 的完整“身躯”。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 46 / 180

---

BeanFactory 提供了最基本的 IoC 能⼒。它就像是⼀个 Bean ⼯⼚，负责 Bean 的创建和管理。他采⽤的是懒加载
的⽅式，也就是说只有当我们真正去获取某个 Bean 的时候，它才会去创建这个 Bean。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 47 / 180

---

它最主要的⽅法就是 getBean() ，负责从容器中返回特定名称或者类型的 Bean 实例。
ApplicationContext 是 BeanFactory 的⼦接⼝，在 BeanFactory 的基础上扩展了很多企业级的功能。它不仅包含
了 BeanFactory 的所有功能，还提供了国际化⽀持、事件发布机制、AOP、JDBC、ORM 框架集成等等。
public class BeanFactoryExample {
    public static void main(String[] args) {
        // 创建 BeanFactory
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        
        // ⼿动注册 Bean 定义
        BeanDefinition beanDefinition = new RootBeanDefinition(UserService.class);
        beanFactory.registerBeanDefinition("userService", beanDefinition);
        
        // 懒加载：此时才创建 Bean 实例
        UserService userService = beanFactory.getBean("userService", UserService.class);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 48 / 180

---

ApplicationContext 采⽤的是饿加载的⽅式，容器启动的时候就会把所有的单例 Bean 都创建好，虽然这样会导致
启动时间⻓⼀点，但运⾏时性能更好。
@Configuration
public class AppConfig {
    @Bean
    public UserService userService() {
        return new UserService();
    }
}
public class ApplicationContextExample {
    public static void main(String[] args) {
        // 创建 ApplicationContext，启动时就创建所有 Bean
        ApplicationContext context = new 
AnnotationConfigApplicationContext(AppConfig.class);
        
        // 获取 Bean
        UserService userService = context.getBean(UserService.class);
        
        // 发布事件
        context.publishEvent(new CustomEvent("Hello World"));
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 49 / 180

---

从使⽤场景来说，实际开发中⽤得最多的是 ApplicationContext。像 AnnotationConfigApplicationContext、
WebApplicationContext 这些都是 ApplicationContext 的实现类。
另外⼀个重要的区别是⽣命周期管理。ApplicationContext 会⾃动调⽤ Bean 的初始化和销毁⽅法，⽽ 
BeanFactory 需要我们⼿动管理。
在 Spring Boot 项⽬中，我们可以通过 @Autowired 注⼊ ApplicationContext，或者通过实现 
ApplicationContextAware 接⼝来获取 ApplicationContext。
1. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：BeanFactory和
ApplicationContext
memo：2025 年 6 ⽉ 25 ⽇修改⾄此，今天给⼀个华科本硕研 0 的球友修改简历后，发来这样的感慨，要是早点
知道你的⽹站和星球就好了，技术派不⽐外卖强多了？再次感谢⼆哥。
9.
项⽬启动时Spring的IoC会做什么？
 
第⼀件事是扫描和注册 Bean。IoC 容器会根据我们的配置，⽐如 @ComponentScan 指定的包路径，去扫描所有标
注了 @Component 、@Service 、@Controller 这些注解的类。然后把这些类的元信息包装成 BeanDefinition 对
象，注册到容器的 BeanDefinitionRegistry 中。这个阶段只是收集信息，还没有真正创建对象。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 50 / 180

---

第⼆件事是 Bean 的实例化和注⼊。这是最核⼼的过程，IoC 容器会按照依赖关系的顺序开始创建 Bean 实例。对
于单例 Bean，容器会通过反射调⽤构造⽅法创建实例，然后进⾏属性注⼊，最后执⾏初始化回调⽅法。
在依赖注⼊时，容器会根据 @Autowired 、@Resource 这些注解，把相应的依赖对象注⼊到⽬标 Bean 中。⽐如 
UserService 需要 UserDao，容器就会把 UserDao 的实例注⼊到 UserService 中。
说说Spring的Bean实例化⽅式？
 
Spring 提供了 4 种⽅式来实例化 Bean，以满⾜不同场景下的需求。
第⼀种是通过构造⽅法实例化，这是最常⽤的⽅式。当我们⽤ @Component 、@Service 这些注解标注类的时候，
Spring 默认通过⽆参构造器来创建实例的。如果类只有⼀个有参构造⽅法，Spring 会⾃动进⾏构造⽅法注⼊。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 51 / 180

---

第⼆种是通过静态⼯⼚⽅法实例化。有时候对象的创建⽐较复杂，我们会写⼀个静态⼯⼚⽅法来创建，然后⽤ 
@Bean 注解来标注这个⽅法。Spring 会调⽤这个静态⽅法来获取 Bean 实例。
第三种是通过实例⼯⼚⽅法实例化。这种⽅式是先创建⼯⼚对象，然后通过⼯⼚对象的⽅法来创建Bean：
第四种是通过 FactoryBean 接⼝实例化。这是 Spring 提供的⼀个特殊接⼝，当我们需要创建复杂对象的时候特别
有⽤：
@Service
public class UserService {
    private UserDao userDao;
    
    public UserService(UserDao userDao) {  // 构造⽅法注⼊
        this.userDao = userDao;
    }
}
@Configuration
public class AppConfig {
    @Bean
    public static DataSource createDataSource() {
        // 复杂的DataSource创建逻辑
        return new HikariDataSource();
    }
}
@Configuration
public class AppConfig {
    @Bean
    public ConnectionFactory connectionFactory() {
        return new ConnectionFactory();
    }
    
    @Bean
    public Connection createConnection(ConnectionFactory factory) {
        return factory.createConnection();
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 52 / 180

---

在实际⼯作中，⽤得最多的还是构造⽅法实例化，因为简单直接。⼯⼚⽅法⼀般⽤在需要复杂初始化逻辑的场景，
⽐如数据库连接池、消息队列连接这些。FactoryBean 主要是在框架开发或者需要动态创建对象的时候使⽤。
Spring 在实例化的时候会根据 Bean 的定义⾃动选择合适的⽅式，我们作为开发者主要是通过注解和配置来告诉 
Spring 应该怎么创建对象。
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：说说 Spring 的 Bean 实例化⽅式
2. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：bean加⼯有哪些⽅法？
memo：2025 年 6 ⽉ 27 ⽇修改⾄此，今天看到有球友发的 offer 选择提问贴，其中⼀个是杭州六⼩⻰群核科技，
我个⼈认为还是⾮常值得去的，毕竟是杭州的独⻆兽公司，薪资待遇都不错。
10.你是怎么理解Bean的？
 
在我看来，Bean 本质上就是由 Spring 容器管理的 Java 对象，但它和普通的 Java 对象有很⼤区别。普通的 Java 
对象我们是通过 new 关键字创建的。⽽ Bean 是交给 Spring 容器来管理的，从创建到销毁都由容器负责。
@Component
public class MyFactoryBean implements FactoryBean<MyObject> {
    @Override
    public MyObject getObject() throws Exception {
        // 复杂的对象创建逻辑
        return new MyObject();
    }
    
    @Override
    public Class<?> getObjectType() {
        return MyObject.class;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 53 / 180

---

从实际使⽤的⻆度来说，我们项⽬⾥的 Service、Dao、Controller 这些都是 Bean。⽐如 UserService 被标注了 
@Service 注解，它就成了⼀个 Bean，Spring 会⾃动创建它的实例，管理它的依赖关系，当其他地⽅需要⽤到 
UserService 的时候，Spring 就会把这个实例注⼊进去。
这种依赖注⼊的⽅式让对象之间的关系变得松耦合。
Spring 提供了多种 Bean 的配置⽅式，基于注解的⽅式是最常⽤的。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 54 / 180

---

基于 XML 配置的⽅式在 Spring Boot 项⽬中已经不怎么⽤了。Java 配置类的⽅式则可以⽤来解决⼀些⽐较复杂的
场景，⽐如说主从数据源，我们可以⽤ @Primary 注解标注主数据源，⽤ @Qualifier 来指定备⽤数据源。
那在使⽤的时候，当我们直接⽤ @Autowired 注解注⼊ DataSource 时，Spring 默认会使⽤ HikariDataSource；
当加上 @Qualifier("secondary") 注解时，Spring 则会注⼊ BasicDataSource。
@Component 和 @Bean 有什么区别？
 
@Configuration
public class AppConfig {
    
    @Bean
    @Primary  // 主要候选者
    public DataSource primaryDataSource() {
        return new HikariDataSource();
    }
    
    @Bean
    @Qualifier("secondary")
    public DataSource secondaryDataSource() {
        return new BasicDataSource();
    }
}
@Autowired
private DataSource dataSource; // 会注⼊ primaryDataSource（因为有 @Primary）
@Autowired
@Qualifier("secondary")
private DataSource secondaryDataSource;
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 55 / 180

---

⾸先从使⽤上来说，@Component 是标注在类上的，⽽ @Bean 是标注在⽅法上的。@Component 告诉 Spring 这
个类是⼀个组件，请把它注册为 Bean，⽽ @Bean 则告诉 Spring 请将这个⽅法返回的对象注册为 Bean。
从控制权的⻆度来说，@Component 是由 Spring ⾃动创建和管理的。
⽽ @Bean 则是由我们⼿动创建的，然后再交给 Spring 管理，我们对对象的创建过程有完全的控制权。
@Component  // Spring⾃动创建UserService实例
public class UserService {
    @Autowired
    private UserDao userDao;
}
@Configuration
public class AppConfig {
    @Bean  // 我们⼿动创建DataSource实例
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/test");
        ds.setUsername("root");
        ds.setPassword("123456");
        return ds;  // 返回给Spring管理
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 56 / 180

---

1. Java ⾯试指南（付费）收录的京东⾯经同学 9 ⾯试原题：怎么理解spring的bean，@Component 和 
@Bean 的区别
memo：2025 年 6 ⽉ 28 ⽇修改⾄此，今天在帮球友修改简历的时候，⼜碰到⼀个杭电本硕的球友。我这⾥想说
的⼀点是，杭电的计算机专业⾮常强，虽然他只是⼀所双⾮，如果能把项⽬经历、专业技能好好写的话，拿个⼤⼚
的顶级 offer 是完全没问题的。
11.
能说⼀下Bean的⽣命周期吗？
 
推荐阅读：三分恶：Spring Bean ⽣命周期，好像⼈的⼀⽣
好的。
Bean 的⽣命周期可以分为 5 个主要阶段，我按照实际的执⾏顺序来说⼀下。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 57 / 180

---

第⼀个阶段是实例化。Spring 容器会根据 BeanDefinition，通过反射调⽤ Bean 的构造⽅法创建对象实例。如果
有多个构造⽅法，Spring 会根据依赖注⼊的规则选择合适的构造⽅法。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 58 / 180

---

第⼆阶段是属性赋值。这个阶段 Spring 会给 Bean 的属性赋值，包括通过 @Autowired 、@Resource 这些注解注
⼊的依赖对象，以及通过 @Value 注⼊的配置值。
第三阶段是初始化。这个阶段会依次执⾏：
@PostConstruct 标注的⽅法
InitializingBean 接⼝的 afterPropertiesSet ⽅法
通过 @Bean 的 initMethod 指定的初始化⽅法
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 59 / 180

---

我在项⽬中经常⽤ @PostConstruct 来做⼀些初始化⼯作，⽐如缓存预加载、DB 配置等等。
初始化后，Spring 还会调⽤所有注册的 BeanPostProcessor 后置处理⽅法。这个阶段经常⽤来创建代理对象，⽐
如 AOP 代理。
第五阶段是使⽤ Bean。⽐如我们的 Controller 调⽤ Service，Service 调⽤ DAO。
// CategoryServiceImpl中的缓存初始化
@PostConstruct
public void init() {
    categoryCaches = CacheBuilder.newBuilder().maximumSize(300).build(new 
CacheLoader<Long, CategoryDTO>() {
        @Override
        public CategoryDTO load(@NotNull Long categoryId) throws Exception {
            CategoryDO category = categoryDao.getById(categoryId);
            // ...
        }
    });
}
// DynamicConfigContainer中的配置初始化
@PostConstruct
public void init() {
    cache = Maps.newHashMap();
    bindBeansFromLocalCache("dbConfig", cache);
}
// UserController中的使⽤示例
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 60 / 180

---

最后是销毁阶段。当容器关闭或者 Bean 被移除的时候，会依次执⾏：
@PreDestroy 标注的⽅法
DisposableBean 接⼝的 destroy ⽅法
通过 @Bean 的 destroyMethod 指定的销毁⽅法
@Autowired
private UserService userService;
@GetMapping("/users/{id}")
public UserDTO getUser(@PathVariable Long id) {
    return userService.getUserById(id);
}
// UserService中的使⽤示例
@Autowired
private UserDao userDao;
public UserDTO getUserById(Long id) {
    return userDao.getById(id);
}
// UserDao中的使⽤示例
@Autowired
private JdbcTemplate jdbcTemplate;
public UserDTO getById(Long id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    return jdbcTemplate.queryForObject(sql, new Object[]{id}, new UserRowMapper());
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 61 / 180

---

Aware 类型的接⼝有什么作⽤？
 
Aware 接⼝在 Spring 中是⼀个很有意思的设计，它们的作⽤是让 Bean 能够感知到 Spring 容器的⼀些内部组件。
从设计理念来说，Aware 接⼝实现了⼀种“回调”机制。正常情况下，Bean 不应该直接依赖 Spring 容器，这样可以
保持代码的独⽴性。但有些时候，Bean 确实需要获取容器的⼀些信息或者组件，Aware 接⼝就提供了这样⼀个能
⼒。
我最常⽤的 Aware 接⼝是 ApplicationContextAware，它可以让 Bean 获取到 ApplicationContext 容器本身。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 62 / 180

---

在技术派项⽬中，我就通过实现 ApplicationContextAware 和 EnvironmentAware 接⼝封装了⼀个 SpringUtil ⼯
具类，通过 getBean 和 getProperty ⽅法来获取 Bean 和配置属性。
如果配置了 init-method 和 destroy-method，Spring 会在什么时候调⽤其配置的⽅法？ 
init-method 指定的初始化⽅法会在 Bean 的初始化阶段被调⽤，具体的执⾏顺序是：
先执⾏ @PostConstruct 标注的⽅法
然后执⾏ InitializingBean 接⼝的 afterPropertiesSet() ⽅法
最后再执⾏ init-method 指定的⽅法
也就是说，init-method 是在所有其他初始化⽅法之后执⾏的。
// 静态⽅法获取Bean，⽅便在⾮Spring管理的类中使⽤
public static <T> T getBean(Class<T> clazz) {
    return context.getBean(clazz);
}
// 获取配置属性
public static String getProperty(String key) {
    return environment.getProperty(key);
}
@Component
public class MyService {
    @Autowired
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 63 / 180

---

destroy-method 会在 Bean 销毁阶段被调⽤。
不过在实际开发中，通常⽤ @PostConstruct 和 @PreDestroy 就够了，它们更简洁。
1. Java ⾯试指南（付费）收录的⼩⽶ 25 届⽇常实习⼀⾯原题：说说 Bean 的⽣命周期
2. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：Spring中bean⽣
命周期
3. Java ⾯试指南（付费）收录的8 后端开发秋招⼀⾯⾯试原题：讲⼀下Spring Bean的⽣命周期
4. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：bean⽣命周期
5. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：介绍下Bean的⽣命周期？Aware类型接⼝的作
⽤？如果配置了init-method和destroy-method，Spring会在什么时候调⽤其配置的⽅法？
memo：2025 年 6 ⽉ 30 ⽇修改⾄此。昨天有读者发消息说有三个 offer 要选择，中科⼤读博、中海油、商⻜北
研，问我该怎么选择？说实话，这三个都是⾮常优质的选择，我个⼈的建议是优先考虑中科⼤读博，毕竟是国内顶
尖学府，博⼠毕业后可以选择在⾼校任教，会更符合他的家庭条件，当然了，我深知，读博的产出压⼒⾮常⼤。
    private UserDao userDao;
    
    @PostConstruct
    public void postConstruct() {
        System.out.println("1. @PostConstruct执⾏");
    }
    
    public void customInit() {  // 通过@Bean的initMethod指定
        System.out.println("3. init-method执⾏");
    }
}
@Configuration
public class AppConfig {
    @Bean(initMethod = "customInit")
    public MyService myService() {
        return new MyService();
    }
}
@Component
public class MyService {
    @PreDestroy
    public void preDestroy() {
        System.out.println("1. @PreDestroy执⾏");
    }
    
    public void customDestroy() {  // 通过@Bean的destroyMethod指定
        System.out.println("3. destroy-method执⾏");
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 64 / 180

---

12.为什么IDEA不推荐使⽤@Autowired注解注⼊Bean？
 
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 65 / 180

---

前情提要：当使⽤ @Autowired 注解注⼊ Bean 时，IDEA 会提示“Field injection is not recommended”。
⾯试回答：
主要有⼏个原因。
第⼀个是字段注⼊不利于单元测试。字段注⼊需要使⽤反射或 Spring 容器才能注⼊依赖，测试更复杂；⽽构造⽅
法注⼊可以直接通过构造⽅法传⼊ Mock 对象，测试起来更简单。
// 字段注⼊的测试困难
@Test
public void testUserService() {
    UserService userService = new UserService();
    // ⽆法直接设置userRepository，需要反射或Spring容器
    // userService.userRepository = Mockito.mock(UserRepository.class);
    // 需要⼿动设置依赖，测试不⽅便
    ReflectionTestUtils.setField(userService, "userRepository", 
Mockito.mock(UserRepository.class));
    userService.doSomething();
    // ...
}
// 构造⽅法注⼊的测试简单
@Test
public void testUserService() {
    UserRepository mockRepository = Mockito.mock(UserRepository.class);
    UserService userService = new UserService(mockRepository); // 直接注⼊
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 66 / 180

---

第⼆个是字段注⼊会隐藏循环依赖问题，⽽构造⽅法注⼊会在项⽬启动时就去检查依赖关系，能更早发现问题。
第三个是构造⽅法注⼊可以使⽤ final 字段确保依赖在对象创建时就被初始化，避免了后续修改的⻛险。
在技术派项⽬中，我们已经在使⽤构造⽅法注⼊的⽅式来管理依赖关系。
不过话说回来，@Autowired 的字段注⼊⽅式在⼀些简单的场景下还是可以⽤的，主要看团队的编码规范吧。
@Autowired 和 @Resource 注解的区别？
 
⾸先从来源上说，@Autowired 是 Spring 框架提供的注解，⽽ @Resource 是 Java EE 标准提供的注解。换句话
说，@Resource 是 JDK ⾃带的，⽽ @Autowired 是 Spring 特有的。
虽然 IDEA 不推荐使⽤ @Autowired ，但对 @Resource 注解却没有任何提示。
从注⼊⽅式上说，@Autowired 默认按照类型，也就是 byType 进⾏注⼊，⽽ @Resource 默认按照名称，也就是 
byName 进⾏注⼊。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 67 / 180

---

当容器中存在多个相同类型的 Bean， ⽐如说有两个 UserRepository 的实现类，直接⽤ @Autowired 注⼊ 
UserRepository 时就会报错，因为 Spring 容器不知道该注⼊哪个实现类。
这时候，有两种解决⽅案，第⼀种是使⽤ @Autowired + @Qualifier 指定具体的 Bean 名称来解决冲突。
第⼆种是使⽤ @Resource 注解按名称进⾏注⼊。
1. Java ⾯试指南（付费）收录的京东⾯经同学 9 ⾯试原题：依赖注⼊的时候，直接Autowired⽐较直接，
为什么推荐构造⽅法注⼊呢
memo：2025 年 7 ⽉ 1 ⽇修改⾄此，今天在帮球友修改简历的时候，碰到⼀个郑州⼤学本硕的球友，这也是我们
河南省最好的⼤学了，但也仅仅是⼀所 211，所以希望所有河南的同学都能加把劲，证明⾃⼰的实⼒，去拿到更好
的 offer，为校争光。
@Component
public class UserRepository21 implements UserRepository2 {}
@Component
public class UserRepository22 implements UserRepository2 {}
@Component
public class UserService2 {
    @Autowired
    private UserRepository2 userRepository; // 冲突
}
@Component("userRepository21")
public class UserRepository21 implements UserRepository2 {
}
@Component("userRepository22")
public class UserRepository22 implements UserRepository2 {
}
@Autowired
@Qualifier("userRepository22")
private UserRepository2 userRepository22;
@Resource(name = "userRepository21")
private UserRepository2 userRepository21;
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 68 / 180

---

13.@Autowired的实现原理了解吗？
 
@Autowired 是 Spring 实现依赖注⼊的核⼼注解，其实现原理基于反射机制和 BeanPostProcessor 接⼝。
整个过程分为两个主要阶段。第⼀个阶段是依赖收集阶段，发⽣在 Bean 实例化之后、属性赋值之前。Autowired 
的 Processor 会扫描 Bean 的所有字段、⽅法和构造⽅法，找出标注了 @Autowired 注解的地⽅，然后把这些信
息封装成 Injection 元数据对象缓存起来。这个过程⽤到了⼤量的反射操作，需要分析类的结构、注解信息等
等。
第⼆个阶段是依赖注⼊阶段，Spring 会取出之前缓存的 Injection 元数据对象，然后逐个处理每个注⼊点。对于
每个 @Autowired 标注的字段或⽅法，Spring 会根据类型去容器中查找匹配的 Bean。
在具体的注⼊过程中，Spring 会使⽤反射来设置字段的值或者调⽤ setter ⽅法。⽐如对于字段注⼊，会调⽤ 
Field.set() ⽅法；对于 setter 注⼊，会调⽤ Method.invoke() ⽅法。
14.什么是⾃动装配？
 
⾃动装配的本质就是让 Spring 容器⾃动帮我们完成 Bean 之间的依赖关系注⼊，⽽不需要我们⼿动去指定每个依
赖。简单来说，就是“我们不⽤告诉 Spring 具体怎么注⼊，Spring ⾃⼰会想办法找到合适的 Bean 注⼊进来”。
⾃动装配的⼯作原理简单来说就是，Spring 容器在启动时⾃动扫描 @ComponentScan 指定包路径下的所有类，然
后根据类上的注解，⽐如 @Autowired 、@Resource 等，来判断哪些 Bean 需要被⾃动装配。
// 1. 按类型查找（byType）
Map<String, Object> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
    this.beanFactory, type);
// 2. 如果找到多个候选者，按名称筛选（byName）
String autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
// 3. 考虑@Primary和@Priority注解
// 4. 最后按照字段名或参数名匹配
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 69 / 180

---

之后分析每个 Bean 的依赖关系，在创建 Bean 的时候，根据装配规则⾃动找到合适的依赖 Bean，最后根据反射
将这些依赖注⼊到⽬标 Bean 中。
Spring提供了哪⼏种⾃动装配类型？
 
Spring 的⾃动装配⽅式有好⼏种，在 XML 配置时代，主要有 byName、byType、constructor 和 autodetect 四
种⽅式。
到了注解驱动时代，⽤得最多的是 @Autowired 注解，默认按照类型装配。
其次还有 @Resource 注解，它默认按照名称装配，如果找不到对应名称的 Bean，就会按类型装配。
@Configuration
@ComponentScan("com.github.paicoding.forum.service")
@MapperScan(basePackages = {
    "com.github.paicoding.forum.service.article.repository.mapper",
    "com.github.paicoding.forum.service.user.repository.mapper"
    // ... 更多包路径
})
public class ServiceAutoConfig {
    // Spring⾃动扫描指定包下的所有组件并注册为Bean
}
@Service
public class UserService {
    @Autowired  // 按类型⾃动装配
    private UserRepository userRepository;
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 70 / 180

---

Spring Boot 的⾃动装配还有⼀套更⾼级的机制，通过 @EnableAutoConfiguration 和各种 @Conditional 注解
来实现，这个是框架级别的⾃动装配，会根据 classpath 中的类和配置来⾃动配置 Bean。
memo：2025 年 7 ⽉ 2 ⽇修改⾄此，今天在帮球友修改简历的时候，碰到⼀个北京航空航天⼤学的球友，他在邮
件中说到：在星球⾥学到了好多东⻄，⽬前正在准备技术派和 MYDB，打算好好冲秋招，能帮助到⼤家我真的很欣
慰。
15.Bean的作⽤域有哪些?
 
Bean 的作⽤域决定了 Bean 实例的⽣命周期和创建策略，singleton 是默认的作⽤域。整个 Spring 容器中只会有
⼀个 Bean 实例。不管在多少个地⽅注⼊这个 Bean，拿到的都是同⼀个对象。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 71 / 180

---

⽣命周期和 Spring 容器相同，容器启动时创建，容器销毁时销毁。
实际开发中，像 Service、Dao 这些业务组件基本都是单例的，因为单例既能节省内存，⼜能提⾼性能。
当把 scope 设置为 prototype 时，每次从容器中获取 Bean 的时候都会创建⼀个新的实例。
当需要处理⼀些有状态的 Bean 时会⽤到 prototype，⽐如每个订单处理器需要维护不同的状态信息。
需要注意的是，在 singleton Bean 中注⼊ prototype Bean 时要⼩⼼，因为 singleton Bean 只创建⼀次，所以 
prototype Bean 也只会注⼊⼀次。这时候可以⽤ @Lookup 注解或者 ApplicationContext 来动态获取。
除了 singleton 和 prototype，Spring 还⽀持其他作⽤域，⽐如 request、session、application 和 websocket。
@Component  // 默认就是singleton
public class UserService {
    // 整个应⽤中只有⼀个UserService实例
}
@Component
@Scope("prototype")
public class OrderProcessor {
    // 每次注⼊或获取都是新的实例
}
@Component
public class SingletonService {
    // 错误的做法，prototypeBean只会注⼊⼀次
    @Autowired
    private PrototypeBean prototypeBean;
    
    // 正确的做法，每次调⽤都获取新实例
    @Lookup
    public PrototypeBean getPrototypeBean() {
        return null;  // Spring会重写这个⽅法
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 72 / 180

---

如果作⽤于是 request，表示在 Web 应⽤中，每个 HTTP 请求都会创建⼀个新的 Bean 实例，请求结束后 Bean 就
被销毁。
如果作⽤于是 session，表示在 Web 应⽤中，每个 HTTP 会话都会创建⼀个新的 Bean 实例，会话结束后 Bean 被
销毁。
典型的使⽤场景是购物⻋、⽤户登录状态这些需要在整个会话期间保持的信息。
application 作⽤域表示在整个应⽤中只有⼀个 Bean 实例，类似于 singleton，但它的⽣命周期与 ServletContext 
绑定。
@Component
@Scope("request")
public class RequestContext {
    // 每个HTTP请求都有⾃⼰的实例
}
@Component
@Scope("session")
public class UserSession {
    // 每个⽤户会话都有⾃⼰的实例
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 73 / 180

---

websocket 作⽤域表示在 WebSocket 会话中每个连接都有⾃⼰的 Bean 实例。WebSocket 连接建⽴时创建，连接
关闭时销毁。
1. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：bean是单例还是多例的，具体
怎么修改
memo：2025 年 7 ⽉ 3 ⽇修改⾄此，今天在帮球友修改简历的时候，碰到⼀个郑州⼤学硕，河北师范⼤学本的球
友，整体在校的经历⾮常出⾊，奖学⾦、论⽂期刊基本上都拉满了。那这么多优秀的球友选择来到这⾥，也是对星
球的⼜⼀次认可和肯定，我也⼀定会继续努⼒，提供更多优质的内容和服务。
16.Spring中的单例Bean会存在线程安全问题吗？
 
⾸先要明确⼀点。Spring 容器本身保证了 Bean 创建过程的线程安全，也就是说不会出现多个线程同时创建同⼀个
单例 Bean 的情况。但是 Bean 创建完成后的使⽤过程，Spring 就不管了。
换句话说，单例 Bean 在被创建后，如果它的内部状态是可变的，那么在多线程环境下就可能会出现线程安全问
题。
@Component
@Scope("application")
public class AppConfig {
    // 整个应⽤中只有⼀个实例
}
@Component
@Scope("websocket")
public class WebSocketHandler {
    // 每个WebSocket连接都有⾃⼰的实例
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 74 / 180

---

⽐如说在技术派项⽬中，有⼀个敏感词过滤的 Bean，我们就需要使⽤ volatile 关键字来保证多线程环境下的可⻅
性。
如果 Bean 中没有成员变量，或者成员变量都是不可变的，final 修饰的，那么就不存在线程安全问题。
@Service
public class SensitiveService {
    private volatile SensitiveWordBs sensitiveWordBs; // 使⽤volatile保证可⻅性
    
    @PostConstruct
    public void refresh() {
        // 重新初始化sensitiveWordBs
    }
}
@Service
public class UserServiceImpl implements UserService {
    @Resource
    private UserDao userDao;
    @Autowired
    private CountService countService;
    // 只有依赖注⼊的⽆状态字段
}
@Service
public class ConfigService {
    private final String appName;  // final修饰，不可变
    
    public ConfigService(@Value("${app.name}") String appName) {
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 75 / 180

---

单例Bean的线程安全问题怎么解决呢？
 
第⼀种，使⽤局部变量，也就是使⽤⽆状态的单例 Bean，把所有状态都通过⽅法参数传递：
第⼆种，当确实需要维护线程相关的状态时，可以使⽤ ThreadLocal 来保存状态。ThreadLocal 可以保证每个线程
都有⾃⼰的变量副本，互不⼲扰。
第三种，如果需要缓存数据或者计数，使⽤ JUC 包下的线程安全类，⽐如说 AtomicInteger、
ConcurrentHashMap、CopyOnWriteArrayList 等。
        this.appName = appName;
    }
}
@Service
public class UserService {
    @Autowired
    private UserDao userDao;
    
    // ⽆状态⽅法，所有数据通过参数传递
    public User processUser(Long userId, String operation) {
        User user = userDao.findById(userId);
        // 处理逻辑...
        return user;
    }
}
@Service
public class UserContextService {
    private static final ThreadLocal<User> userThreadLocal = new ThreadLocal<>();
    
    public void setCurrentUser(User user) {
        userThreadLocal.set(user);
    }
    
    public User getCurrentUser() {
        return userThreadLocal.get();
    }
    
    public void clear() {
        userThreadLocal.remove(); // 防⽌内存泄漏
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 76 / 180

---

第四种，对于复杂的状态操作，可以使⽤ synchronized 或 Lock：
第五种，如果 Bean 确实需要维护状态，可以考虑将其改为 prototype 作⽤域，这样每次注⼊都会创建⼀个新的实
例，避免了多线程共享同⼀个实例的问题。
或者使⽤ request 作⽤域，这样每个 HTTP 请求都会创建⼀个新的实例。
@Service
public class CacheService {
    // 使⽤线程安全的集合
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0);
    
    public void put(String key, Object value) {
        cache.put(key, value);
        counter.incrementAndGet();
    }
}
@Service
public class CacheService {
    private final Map<String, Object> cache = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    
    public void put(String key, Object value) {
        lock.lock();
        try {
            cache.put(key, value);
        } finally {
            lock.unlock();
        }
    }
}
@Service
@Scope("prototype") // 每次注⼊都创建新实例
public class StatefulService {
    private String state; // 现在每个实例都有独⽴状态
    
    public void setState(String state) {
        this.state = state;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 77 / 180

---

1. Java ⾯试指南（付费）收录的阿⾥⾯经同学 1 闲⻥后端⼀⾯的原题：spring的bean的并发安全问题
memo：2025 年 7 ⽉ 4 ⽇修改⾄此，今天在帮球友修改简历的时候，碰到⼀个武汉理⼯⼤学本硕的球友。说真
的，和武汉理⼯⼤学挺有缘的，2023 年去武汉，就线下⻅了⼀名武理的球友，他当时签约的是⼩⽶，⾮常优秀。
17.什么是循环依赖?
 
简单来说就是两个或多个 Bean 相互依赖，⽐如说 A 依赖 B，B 依赖 A，或者 C 依赖 C，就成了循环依赖。
@Service
@Scope("request")
public class RequestScopedService {
    private String requestData;
    // 每个请求都有独⽴的实例
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 78 / 180

---

18.
Spring怎么解决循环依赖呢？
 
Spring 通过三级缓存机制来解决循环依赖：
1. ⼀级缓存：存放完全初始化好的单例 Bean。
2. ⼆级缓存：存放提前暴露的 Bean，实例化完成，但未初始化完成。
3. 三级缓存：存放 Bean ⼯⼚，⽤于⽣成提前暴露的 Bean。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 79 / 180

---

以 A、B 两个类发⽣循环依赖为例：
第 1 步：开始创建 Bean A。
Spring 调⽤ A 的构造⽅法，创建 A 的实例。此时 A 对象已存在，但 b属性还是 null。
将 A 的对象⼯⼚放⼊三级缓存。
开始进⾏ A 的属性注⼊。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 80 / 180

---

第 2 步：A 需要注⼊ B，开始创建 Bean B。
发现需要 B，但 B 还不存在，所以开始创建 B。
调⽤ B 的构造⽅法，创建 B 的实例。此时 B 对象已存在，但 a 属性还是 null。
将 B 的对象⼯⼚放⼊三级缓存。
开始进⾏ B 的属性注⼊。
第 3 步：B 需要注⼊ A，从缓存中获取 A。
B 需要注⼊ A，先从⼀级缓存找 A，没找到。
再从⼆级缓存找 A，也没找到。
最后从三级缓存找 A，找到了 A 的对象⼯⼚。
调⽤ A 的对象⼯⼚得到 A 的实例。这时 A 已经实例化了，虽然还没完全初始化。
将 A 从三级缓存移到⼆级缓存。
B 拿到 A 的引⽤，完成属性注⼊。
第 4 步：B 完成初始化。
B 的属性注⼊完成，执⾏ @PostConstruct 等初始化逻辑。
B 完全创建完成，从三级缓存移除，放⼊⼀级缓存。
第 5 步：A 完成初始化。
回到 A 的创建过程，A 拿到完整的 B 实例，完成属性注⼊。
A 执⾏初始化逻辑，创建完成。
A 从⼆级缓存移除，放⼊⼀级缓存。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 81 / 180

---

⽤代码来模拟这个过程，是这样的：
// 模拟Spring的解决过程
public class CircularDependencyDemo {
    // 三级缓存
    Map<String, Object> singletonObjects = new HashMap<>();
    Map<String, Object> earlySingletonObjects = new HashMap<>();
    Map<String, ObjectFactory> singletonFactories = new HashMap<>();
    
    public Object getBean(String beanName) {
        // 先从⼀级缓存获取
        Object bean = singletonObjects.get(beanName);
        if (bean != null) return bean;
        
        // 再从⼆级缓存获取
        bean = earlySingletonObjects.get(beanName);
        if (bean != null) return bean;
        
        // 最后从三级缓存获取
        ObjectFactory factory = singletonFactories.get(beanName);
        if (factory != null) {
            bean = factory.getObject();
            earlySingletonObjects.put(beanName, bean);  // 移到⼆级缓存
            singletonFactories.remove(beanName);        // 从三级缓存移除
        }
        
        return bean;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 82 / 180

---

哪些情况下Spring⽆法解决循环依赖？
 
Spring 虽然能解决⼤部分循环依赖问题，但确实有⼏种情况是⽆法处理的，我来详细说说。
第⼀种，构造⽅法的循环依赖，这种情况 Spring 会直接抛出 BeanCurrentlyInCreationException 异常。
因为构造⽅法注⼊发⽣在实例化阶段，创建 A 的时候必须先有 B，但创建 B⼜必须先有 A，这时候两个对象都还没
创建出来，⽆法提前暴露到缓存中。
第⼆种，prototype 作⽤域的循环依赖。prototype 作⽤域的 Bean 每次获取都会创建新实例，Spring ⽆法缓存这
些实例，所以也⽆法解决循环依赖。
@Component
public class A {
    private B b;
    
    public A(B b) {  // 构造⽅法注⼊
        this.b = b;
    }
}
@Component
public class B {
    private A a;
    
    public B(A a) {  // 构造⽅法注⼊
        this.a = a;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 83 / 180

---

----⾯试中可以不背，⽅便⼤家理解 start----
我们来看⼀个实例，先是 PrototypeBeanA：
然后是 PrototypeBeanB：
再然后是测试：
运⾏结果：
@Component
@Scope("prototype")
public class PrototypeBeanA {
    private final PrototypeBeanB prototypeBeanB;
    @Autowired
    public PrototypeBeanA(PrototypeBeanB prototypeBeanB) {
        this.prototypeBeanB = prototypeBeanB;
    }
}
@Component
@Scope("prototype")
public class PrototypeBeanB {
    private final PrototypeBeanA prototypeBeanA;
    @Autowired
    public PrototypeBeanB(PrototypeBeanA prototypeBeanA) {
        this.prototypeBeanA = prototypeBeanA;
    }
}
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    @Bean
    CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            // 尝试获取PrototypeBeanA的实例
            PrototypeBeanA beanA = ctx.getBean(PrototypeBeanA.class);
        };
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 84 / 180

---

----⾯试中可以不背，⽅便⼤家理解 end----
1. Java ⾯试指南（付费）收录的⼩⽶ 25 届⽇常实习⼀⾯原题：如何解决循环依赖？
2. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：Spring如何解决循
环依赖？
3. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：Spring源码看过吗？Spring的三级缓存知
道吗？
4. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：spring三级缓存解决循环依赖问题
memo：2025 年 7 ⽉ 5 ⽇修改⾄此。今天 VIP 群来了⾮常多的球友，不知不觉我们已经 12 群了，也是⼀个⼤家
庭了，希望⼤家都能在这⾥找到⾃⼰的归属感，我们⼀起学习，⼀起进步。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 85 / 180

---

19.为什么需要三级缓存⽽不是两级？
 
Spring 设计三级缓存主要是为了解决 AOP 代理的问题。
我举个具体的例⼦来说明⼀下。假设我们有 A 和 B 两个类相互依赖，A 的某个⽅法上⾯还标注了 
@Transactional 注解，这意味着 A 最终需要被 Spring 创建成⼀个代理对象。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 86 / 180

---

如果只有⼆级缓存的话，当创建 A 的时候，我们需要把 A 的原始对象提前放到缓存⾥⾯，然后 B 在创建的时候从
缓存中拿到 A 的原始对象。
但是问题来了，A 完成初始化后，由于有 @Transactional 注解，Spring 会把 A 包装成⼀个代理对象放到容器
中。这样就出现了⼀个很严重的问题：B ⾥⾯持有的是 A 的原始对象，⽽容器中存的是 A 的代理对象，同⼀个 
Bean 居然有两个不同的实例，这肯定是不对的。
三级缓存就是为了解决这个问题⽽设计的。三级缓存⾥⾯存放的不是 Bean 的实例，⽽是⼀个对象⼯⼚，这是⼀个
函数式接⼝。
当 B 需要 A 的时候，会调⽤这个对象⼯⼚的 getObject ⽅法，这个⽅法⾥⾯会判断 A 是否需要被代理。如果需要
代理，就创建 A 的代理对象返回给 B；如果不需要代理，就返回 A 的原始对象。这样就保证了 B 拿到的 A 和最终
放⼊容器的 A 是同⼀个对象。
@Component
public class A {
    @Autowired
    private B b;
    
    @Transactional  // A需要被AOP代理
    public void doSomething() {
        // 业务逻辑
    }
}
@Component
public class B {
    @Autowired
    private A a;
}
// 假设只有两级缓存
Map<String, Object> singletonObjects = new HashMap<>();     // 完整Bean
Map<String, Object> earlySingletonObjects = new HashMap<>(); // 半成品Bean
Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>();
// Spring源码中的逻辑
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object 
bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 87 / 180

---

简单来说，三级缓存的核⼼作⽤就是延迟决策。它让 Spring 在真正需要 Bean 的时候才决定返回原始对象还是代
理对象，这样就避免了对象不⼀致的问题。如果没有三级缓存，Spring 要么⽆法在循环依赖的情况下⽀持 AOP，
要么就会出现同⼀个 Bean 有多个实例的问题，这些都是不可接受的。
                SmartInstantiationAwareBeanPostProcessor ibp = 
(SmartInstantiationAwareBeanPostProcessor) bp;
                // 关键：如果需要代理，这⾥会创建代理对象
                exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
            }
        }
    }
    return exposedObject;
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 88 / 180

---

如果缺少⼆级缓存会有什么问题？
 
⼆级缓存 earlySingletonObjects 主要是⽤来存放那些已经通过三级缓存的对象⼯⼚创建出来的早期 Bean 引⽤。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 89 / 180

---

假设我们有 A、B、C 三个 Bean，A 依赖 B 和 C，B 和 C 都依赖 A，形成了⼀个复杂的循环依赖。在没有⼆级缓存
的情况下，每次 B 或者 C 需要获取 A 的时候，都需要调⽤三级缓存中 A 的 ObjectFactory.getObject() ⽅法。
这意味着如果 A 需要被代理的话，代理对象可能会被重复创建多次，这显然是不合理的。
我举个具体的例⼦。⽐如 A 有 @Transactional 注解需要被 AOP 代理，B 在初始化的时候需要 A，会调⽤⼀次对
象⼯⼚创建 A 的代理对象。接着 C 在初始化的时候也需要 A，⼜会调⽤⼀次对象⼯⼚，可能⼜创建了⼀个 A 的代
理对象。这样 B 和 C 拿到的可能就是两个不同的 A 代理对象，这就违反了单例 Bean 的语义。
// 没有⼆级缓存的伪代码
public Object getSingleton(String beanName) {
    Object singletonObject = singletonObjects.get(beanName);
    
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        // 直接从三级缓存获取
        ObjectFactory<?> singletonFactory = singletonFactories.get(beanName);
        if (singletonFactory != null) {
            return singletonFactory.getObject(); // 每次都会创建新的代理对象！
        }
    }
    return singletonObject;
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 90 / 180

---

⼆级缓存就是为了解决这个问题。当第⼀次通过对象⼯⼚创建了 A 的早期引⽤之后，就把这个引⽤放到⼆级缓存
中，同时从三级缓存中移除对象⼯⼚。
后续如果再有其他 Bean 需要 A，就直接从⼆级缓存中获取，不需要再调⽤对象⼯⼚了。
1. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：循环依赖有了解过吗？出现循环依赖的原因？三⼤
缓存存储内容的区别？如何解决循环依赖？如果缺少第⼆级缓存会有什么问题？
memo：2025 年 7 ⽉ 6 ⽇修改⾄此，今天在星球⾥看到⼀个球友的秋招打卡，已经持续 30 天了，按照他这个节
奏下去，互联⽹⼤⼚的 offer 基本上就算是锁定了。并且还有准备 RAG MCP 的⼋股，很棒。
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
    
    @Transactional  // 需要 AOP 代理
    public void methodA() {
        // 业务逻辑
    }
}
@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;  // 获得代理对象 A1
    
    @Autowired
    private ServiceC serviceC;
}
@Service
public class ServiceC {
    @Autowired
    private ServiceA serviceA;  // 可能获得代理对象 A2
}
// 第⼀次获取 A
ObjectFactory<A> factory = singletonFactories.get("serviceA");
Object proxyA = factory.getObject(); // 创建代理
earlySingletonObjects.put("serviceA", proxyA); // 缓存代理
singletonFactories.remove("serviceA");
// 第⼆次获取 A
Object cachedA = earlySingletonObjects.get("serviceA"); // 直接返回缓存的代理
// proxyA == cachedA  ✓
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 91 / 180

---

AOP
 
20.
说说什么是 AOP？
 
AOP，也就是⾯向切⾯编程，简单点说，AOP 就是把⼀些业务逻辑中的相同代码抽取到⼀个独⽴的模块中，让业
务逻辑更加清爽。
----这部分⾯试中可以不背，⽅便⼤家理解 start----
举个简单的例⼦，假设我们有很多个 Service ⽅法，每个⽅法都需要记录执⾏⽇志、检查权限、管理事务等等。如
果没有 AOP 的话，我们可能需要在每个⽅法⾥都写这样的代码：
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 92 / 180

---

如果每个⽅法都这样写，代码就会变得⾮常臃肿，AOP 就是为了解决这个问题，它可以让我们把这些横切关注点
（如⽇志、权限、事务等）从业务代码中抽取出来。
这样，我们就可以定义⼀个切⾯，在切⾯中统⼀处理这些横切关注点：
然后，业务代码就变得⾮常⼲净了：
public void createUser(User user) {
    log.info("开始执⾏createUser⽅法");
    // 权限检查
    if (!hasPermission()) {
        throw new SecurityException("⽆权限");
    }
    // 开启事务
    transactionManager.begin();
    try {
        // 真正的业务逻辑
        userDao.save(user);
        transactionManager.commit();
        log.info("createUser⽅法执⾏成功");
    } catch (Exception e) {
        transactionManager.rollback();
        log.error("createUser⽅法执⾏失败", e);
        throw e;
    }
}
@Aspect
@Component
public class LoggingAspect {
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        log.info("开始执⾏⽅法: " + joinPoint.getSignature().getName());
    }
    @AfterReturning("execution(* com.example.service.*.*(..))")
    public void logAfterReturning(JoinPoint joinPoint) {
        log.info("⽅法执⾏成功: " + joinPoint.getSignature().getName());
    }
    @AfterThrowing(pointcut = "execution(* com.example.service.*.*(..))",
                   throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        log.error("⽅法执⾏失败: " + joinPoint.getSignature().getName(), ex);
    }
}
public void createUser(User user) {
    // 只需要关注业务逻辑，不需要关⼼⽇志、权限、事务等
    userDao.save(user);
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 93 / 180

---

----⾯试中可以不背，⽅便⼤家理解 end----
从技术实现上来说，AOP 主要是通过动态代理来实现的。如果⽬标类实现了接⼝，就⽤ JDK 动态代理；如果没有
实现接⼝，就⽤ CGLIB 来创建⼦类代理。代理对象会在⽅法执⾏前后插⼊我们定义的切⾯逻辑。
Spring AOP 有哪些核⼼概念？
 
Spring AOP 是 AOP 的⼀个具体实现，我按照在⼯作/学习中理解的重要程度来说⼀下：
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 94 / 180

---

①、切⾯：我们定义的⼀个类，包含了要在什么时候、什么地⽅执⾏什么逻辑。⽐如我们定义⼀个⽇志切⾯，专⻔
负责记录⽅法的执⾏情况。在 Spring 中，我们会⽤ @Aspect 注解来标识⼀个切⾯类。
②、切点：定义了在哪些地⽅应⽤切⾯逻辑。说⽩了就是告诉 Spring，我这个切⾯要在哪些⽅法上⽣效。⽐如我
们可以定义⼀个切点表达式，让它匹配所有 Service 层的⽅法，或者匹配某个特定包下的所有⽅法。在 Spring 中
⽤ @Pointcut 注解来定义，通常会写⼀些表达式，⽐如 execution( com.example.service..*(..)) 这样
的。
③、通知：是切⾯中具体要执⾏的代码逻辑。它有⼏种类型：@Before 是在⽅法执⾏前执⾏，@After 是在⽅法
执⾏后执⾏，@Around 是环绕通知，可以在⽅法执⾏前后都执⾏，@AfterReturning 是在⽅法正常返回后执
⾏，@AfterThrowing 是在⽅法抛出异常后执⾏。我⼀般⽤得最多的是 @Around ，因为它最灵活，可以控制⽅法
是否执⾏，也可以修改参数和返回值。
④、连接点：被拦截到的点，因为 Spring 只⽀持⽅法类型的连接点，所以在 Spring 中，连接点指的是被拦截到的
⽅法，实际上连接点还可以是字段或者构造⽅法。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 95 / 180

---

⑤、织⼊：是把切⾯逻辑应⽤到⽬标对象的过程。Spring AOP 是在运⾏时通过动态代理来实现织⼊的，当我们从 
Spring 容器中获取 Bean 的时候，如果这个 Bean 需要被切⾯处理，Spring 就会返回⼀个代理对象给我们。
⑥、⽬标对象：被切⾯处理的对象，也就是我们平时写的 Service、Controller 等类。Spring AOP 会在⽬标对象上
织⼊切⾯逻辑。
它们之间的逻辑关系图是这样的：
Spring AOP 织⼊有哪⼏种⽅式？
 
织⼊有三种主要⽅式，我按照它们的执⾏时机来说⼀下。
切⾯（Aspect）
    ├── 切⼊点（Pointcut）─── 定义在哪⾥执⾏
    └── 通知（Advice）   ─── 定义何时执⾏什么
            ├── @Before
            ├── @After
            ├── @AfterReturning
            ├── @AfterThrowing
            └── @Around
⽬标对象（Target）──→ 代理对象（Proxy）──→ 织⼊（Weaving）
     ↑                                    ↓
连接点（Join Point）                    客户端调⽤
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 96 / 180

---

编译期织⼊是在编译 Java 源码的时候就把切⾯逻辑织⼊到⽬标类中。这种⽅式最典型的实现就是 AspectJ 编译
器。它会在编译的时候直接修改字节码，把切⾯的逻辑插⼊到⽬标⽅法中。
这样⽣成的 class ⽂件就已经包含了切⾯逻辑，运⾏时不需要额外的代理机制。
// 源代码
@Aspect
public class LoggingAspect {
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("⽅法执⾏前: " + joinPoint.getSignature().getName());
    }
}
@Service
public class UserService {
    public void saveUser(String username) {
        System.out.println("保存⽤户: " + username);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 97 / 180

---

编译期织⼊的优点是性能最好，因为没有代理的开销，但缺点是需要使⽤特殊的编译器，⽽且⽐较复杂，在 
Spring 项⽬中⽤得不多。
类加载期织⼊是在 JVM 加载 class ⽂件的时候进⾏织⼊。这种⽅式通过 Java 的 Instrumentation API 或者⾃定义
的 ClassLoader 来实现，在类被加载到 JVM 之前修改字节码。
AspectJ 的 Load-Time Weaving 就是这种⽅式的典型实现。它⽐编译期织⼊更灵活⼀些，但是配置相对复杂，需
要在 JVM 启动参数中指定 Java agent，在 Spring 中也有⽀持，但⽤得不是特别多。
运⾏时织⼊是我们在 Spring 中最常⻅的⽅式，也就是通过动态代理来实现。Spring AOP 采⽤的就是这种⽅式。当 
Spring 容器启动的时候，如果发现某个 Bean 需要被切⾯处理，就会为这个 Bean 创建⼀个代理对象。如果⽬标类
实现了接⼝，Spring 会使⽤ JDK 的动态代理技术。
// 编译器⾃动⽣成的代码
public class UserService {
    public void saveUser(String username) {
        // 织⼊的切⾯代码
        System.out.println("⽅法执⾏前: saveUser");
        
        // 原始业务代码
        System.out.println("保存⽤户: " + username);
    }
}
public class WeavingClassLoader extends ClassLoader {
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classBytes = loadClassBytes(name);
        
        // 在这⾥进⾏字节码织⼊
        byte[] wovenBytes = weaveAspects(classBytes);
        
        return defineClass(name, wovenBytes, 0, wovenBytes.length);
    }
    
    private byte[] weaveAspects(byte[] classBytes) {
        // 使⽤ ASM 或其他字节码操作库进⾏织⼊
        return classBytes;
    }
}
# JVM 启动参数
java -javaagent:aspectjweaver.jar -jar myapp.jar
// 接⼝
public interface UserService {
    void saveUser(String username);
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 98 / 180

---

如果⽬标类没有实现接⼝，就会使⽤ CGLIB 来创建⼀个⼦类作为代理。运⾏时织⼊的优点是实现简单，不需要特
殊的编译器或 JVM 配置，缺点是有⼀定的性能开销，因为每次⽅法调⽤都要经过代理。
// 实现类
@Service
public class UserServiceImpl implements UserService {
    @Override
    public void saveUser(String username) {
        System.out.println("保存⽤户: " + username);
    }
}
// Spring ⾃动创建的代理（伪代码）
public class UserServiceProxy implements UserService {
    private UserService target;
    private List<Advisor> advisors;
    
    @Override
    public void saveUser(String username) {
        // 执⾏前置通知
        for (Advisor advisor : advisors) {
            if (advisor.getPointcut().matches(this.getClass().getMethod("saveUser", 
String.class))) {
                advisor.getAdvice().before();
            }
        }
        
        // 执⾏⽬标⽅法
        target.saveUser(username);
        
        // 执⾏后置通知
        for (Advisor advisor : advisors) {
            advisor.getAdvice().after();
        }
    }
}
// 没有接⼝的类
@Service
public class OrderService {
    public void createOrder(String orderId) {
        System.out.println("创建订单: " + orderId);
    }
}
// CGLIB ⽣成的代理⼦类（伪代码）
public class OrderService$$EnhancerByCGLIB$$12345 extends OrderService {
    private MethodInterceptor interceptor;
    
    @Override
    public void createOrder(String orderId) {
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 99 / 180

---

Spring AOP 默认的织⼊⽅式就是运⾏时织⼊，使⽤起来⾮常简单，只需要加个 @Aspect 注解和相应的通知注解
就可以了。虽然性能上不如编译期织⼊，但是对于⼤部分业务场景来说，这点性能开销是完全可以接受的。
AspectJ 是什么？
 
AspectJ 是⼀个 AOP 框架，它可以做很多 Spring AOP ⼲不了的事情，⽐如说编译时、编译后和类加载时织⼊切
⾯。并且提供了很多复杂的切点表达式和通知类型。
        // 通过 MethodInterceptor 执⾏切⾯逻辑
        interceptor.intercept(this, getMethod("createOrder"), new Object[]{orderId}, 
                            new MethodProxy() {
                                @Override
                                public Object invokeSuper(Object obj, Object[] args) {
                                    return OrderService.super.createOrder((String) 
args[0]);
                                }
                            });
    }
}
// Spring AOP 的代理创建过程
@Configuration
@EnableAspectJAutoProxy  // 启⽤ AOP ⾃动代理
public class AopConfig {
}
// Spring 内部的代理创建逻辑（简化版）
public class DefaultAopProxyFactory implements AopProxyFactory {
    
    @Override
    public AopProxy createAopProxy(AdvisedSupport config) {
        if (config.isOptimize() || config.isProxyTargetClass() || 
hasNoUserSuppliedProxyInterfaces(config)) {
            // 使⽤ CGLIB 代理
            return new CglibAopProxy(config);
        } else {
            // 使⽤ JDK 动态代理
            return new JdkDynamicAopProxy(config);
        }
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 100 / 180

---

Spring AOP 只⽀持⽅法级别的拦截，⽽且只能拦截 Spring 容器管理的 Bean。但是 AspectJ 可以拦截任何 Java 对
象的⽅法调⽤、字段访问、构造⽅法执⾏、异常处理等等。
Spring AOP 有哪些通知⽅式？
 
Spring AOP 提供了多种通知⽅式，允许我们在⽅法执⾏的不同阶段插⼊逻辑。常⽤的通知⽅式有：
前置通知 (@Before)
返回通知 (@AfterReturning)
异常通知 (@AfterThrowing)
后置通知 (@After)
// Spring AOP 只能做到这些
@Aspect
@Component
public class SpringAopAspect {
    // 
 可以拦截：public ⽅法调⽤
    @Around("execution(public * com.example.service.*.*(..))") 
    public Object aroundPublicMethod(ProceedingJoinPoint pjp) {
        return pjp.proceed();
    }
    
    // 
 ⽆法拦截：字段访问
    // 
 ⽆法拦截：构造函数
    // 
 ⽆法拦截：私有⽅法
    // 
 ⽆法拦截：静态⽅法
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 101 / 180

---

环绕通知 (@Around)
前置通知是在⽬标⽅法执⾏之前执⾏的通知。这种通知⽐较简单，主要⽤来做⼀些准备⼯作，⽐如参数校验、权限
检查、记录⽅法开始执⾏的⽇志等等。前置通知⽆法阻⽌⽬标⽅法的执⾏，也⽆法修改⽅法的参数，它只能在⽅法
执⾏前做⼀些额外的操作。我们在项⽬中经常⽤它来记录操作⽇志，⽐如记录谁在什么时候调⽤了什么⽅法。
后置通知是在⽬标⽅法执⾏完成后执⾏的，不管⽅法是正常返回还是抛出异常都会执⾏。这种通知主要⽤来做⼀些
清理⼯作，⽐如释放资源、记录⽅法执⾏完成的⽇志等等。需要注意的是，后置通知拿不到⽅法的返回值，也捕获
不到异常信息，它就是纯粹的在⽅法执⾏后做⼀些收尾⼯作。
@Aspect
@Component
public class LoggingAspect {
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        // 打印⽅法名和参数
        System.out.println("调⽤⽅法: " + joinPoint.getSignature().getName());
        System.out.println("参数: " + Arrays.toString(joinPoint.getArgs()));
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 102 / 180

---

返回通知是在⽬标⽅法正常返回后执⾏的。这种通知可以获取到⽅法的返回值，我们可以在注解中指定 returning 
参数来接收返回值。返回通知经常⽤来做⼀些基于返回结果的后续处理，⽐如缓存⽅法的返回结果、根据返回值发
送通知等等。如果⽅法抛出异常的话，返回通知是不会执⾏的。
异常通知是在⽬标⽅法抛出异常后执⾏的。我们可以在注解中指定 throwing 参数来接收异常对象。异常通知主要
⽤来做异常处理和记录，⽐如记录错误⽇志、发送告警、异常统计等等。需要注意的是，异常通知不能处理异常，
异常还是会继续向上抛出。
环绕通知是最强⼤也是我们⽤得最多的⼀种通知。它可以在⽅法执⾏前后都执⾏逻辑，⽽且可以控制⽬标⽅法是否
执⾏，还可以修改⽅法的参数和返回值。环绕通知的⽅法必须接收⼀个 ProceedingJoinPoint 参数，通过调⽤其 
proceed() ⽅法来执⾏⽬标⽅法。
技术派 项⽬中就主要是通过环绕通知来实现切⾯。
@Aspect
@Component
public class LoggingAspect {
    @After("execution(* com.example.service.*.*(..))")
    public void logAfter(JoinPoint joinPoint) {
        // 打印⽅法执⾏完成的⽇志
        System.out.println("⽅法执⾏完成: " + joinPoint.getSignature().getName());
    }
}
@Aspect
@Component
public class LoggingAspect {
    @AfterReturning(pointcut = "execution(* com.example.service.*.*(..))", returning = 
"result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        // 打印⽅法执⾏完成的⽇志
        System.out.println("⽅法执⾏完成: " + joinPoint.getSignature().getName());
        // 打印⽅法返回值
        System.out.println("返回值: " + result);
    }
}
@Aspect
@Component
public class LoggingAspect {
    @AfterThrowing(pointcut = "execution(* com.example.service.*.*(..))",
                     throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        // 打印⽅法名和异常信息
        System.out.println("⽅法执⾏异常: " + joinPoint.getSignature().getName());
        System.out.println("异常信息: " + ex.getMessage());
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 103 / 180

---

如果有多个切⾯，还可以通过 @Order 注解指定先后顺序，数字越⼩，优先级越⾼。代码示例如下：
@Aspect
@Component
public class WebLogAspect {
    private final static Logger logger = LoggerFactory.getLogger(WebLogAspect.class);
    @Pointcut("@annotation(cn.fighter3.spring.aop_demo.WebLog)")
    public void webLog() {}
    @Before("webLog()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {
        // 开始打印请求⽇志
        ServletRequestAttributes attributes = (ServletRequestAttributes) 
RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        // 打印请求相关参数
        logger.info("========================================== Start 
==========================================");
        // 打印请求 url
        logger.info("URL            : {}", request.getRequestURL().toString());
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 104 / 180

---

Spring AOP 发⽣在什么时候？
 
Spring AOP 是在 Bean 的初始化阶段发⽣的，具体来说是在 Bean ⽣命周期的后置处理阶段。
在 Bean 实例化完成、属性注⼊完成之后，Spring 会调⽤所有 BeanPostProcessor 的 
postProcessAfterInitialization ⽅法，AOP 代理的创建就是在这个阶段完成的。
        // 打印 Http method
        logger.info("HTTP Method    : {}", request.getMethod());
        // 打印调⽤ controller 的全路径以及执⾏⽅法
        logger.info("Class Method   : {}.{}", 
joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
        // 打印请求的 IP
        logger.info("IP             : {}", request.getRemoteAddr());
        // 打印请求⼊参
        logger.info("Request Args   : {}",new 
ObjectMapper().writeValueAsString(joinPoint.getArgs()));
    }
    @After("webLog()")
    public void doAfter() throws Throwable {
        // 结束后打个分隔线，⽅便查看
        logger.info("=========================================== End 
===========================================");
    }
    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        //开始时间
        long startTime = System.currentTimeMillis();
        Object result = proceedingJoinPoint.proceed();
        // 打印出参
        logger.info("Response Args  : {}", new 
ObjectMapper().writeValueAsString(result));
        // 执⾏耗时
        logger.info("Time-Consuming : {} ms", System.currentTimeMillis() - startTime);
        return result;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 105 / 180

---

简单总结⼀下 AOP
 
AOP，也就是⾯向切⾯编程，是⼀种编程范式，旨在提⾼代码的模块化。⽐如说可以将⽇志记录、事务管理等分离
出来，来提⾼代码的可重⽤性。
AOP 的核⼼概念包括切⾯、连接点、通知、切点和织⼊等。
① 像⽇志打印、事务管理等都可以抽离为切⾯，可以声明在类的⽅法上。像 @Transactional 注解，就是⼀个典
型的 AOP 应⽤，它就是通过 AOP 来实现事务管理的。我们只需要在⽅法上添加 @Transactional 注解，Spring 
就会在⽅法执⾏前后添加事务管理的逻辑。
② Spring AOP 是基于代理的，它默认使⽤ JDK 动态代理和 CGLIB 代理来实现 AOP。
③ Spring AOP 的织⼊⽅式是运⾏时织⼊，⽽ AspectJ ⽀持编译时织⼊、类加载时织⼊。
AOP和 OOP 的关系？
 
AOP 和 OOP 是互补的编程思想：
1. OOP 通过类和对象封装数据和⾏为，专注于核⼼业务逻辑。
2. AOP 提供了解决横切关注点（如⽇志、权限、事务等）的机制，将这些逻辑集中管理。
1. Java ⾯试指南（付费）收录的腾讯 Java 后端实习⼀⾯原题：说说 AOP 的原理。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 106 / 180

---

2. Java ⾯试指南（付费）收录的⼩⽶ 25 届⽇常实习⼀⾯原题：说说你对 AOP 和 IoC 的理解。
3. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下 Spring AOP 的实现
原理
4. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：介绍 Spring IoC 和 AOP?
5. Java ⾯试指南（付费）收录的招商银⾏⾯经同学 6 招银⽹络科技⾯试原题：SpringBoot框架的AOP、
IOC/DI？
6. Java ⾯试指南（付费）收录的美团⾯经同学 4 ⼀⾯⾯试原题：Spring AOP发⽣在什么时候
7. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：Spring AOP的概念了解吗？AOP和 
OOP 的关系？
memo：2025 年 7 ⽉ 7 ⽇修改⾄此，有球友提问要⼀个详细版的学习计划表，我⽤了⼀个早上的时间整理了⼀个
三个⽉的冲刺计划，包括⼋股、算法、项⽬的安排，已经放在了 Java ⾯试指南中，需要的⼩伙伴可以⾃取做个参
考。
21.
AOP的应⽤场景有哪些？
 
答：AOP 在实际⼯作/编码学习中有很多应⽤场景，我按照使⽤频率来说说⼏个主要的。
事务管理是⽤得最多的场景，基本上每个项⽬都会⽤到。只需要在 Service ⽅法上加个 @Transactional 注解，
Spring 就会⾃动帮我们管理事务的开启、提交和回滚。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 107 / 180

---

⽇志记录也是⼀个很常⻅的应⽤。在技术派实战项⽬中，就利⽤了 AOP 来打印接⼝的⼊参和出参⽇志、执⾏时
间，⽅便后期 bug 溯源和性能调优。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 108 / 180

---

----这部分⾯试可以不背，⽅便⼤家理解 start----
第⼀步，定义 @MdcDot 注解：
第⼆步，配置 MdcAspect 切⾯，拦截带有 @MdcDot 注解的⽅法或类，在⽅法执⾏前后进⾏ MDC 操作，记录⽅法
执⾏耗时。
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MdcDot {
    String bizCode() default "";
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 109 / 180

---

第三步，在需要的地⽅加上 @MdcDot 注解。
第四步，当接⼝被调⽤时，就可以看到对应的执⾏⽇志。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 110 / 180

---

----⾯试可以不背，⽅便⼤家理解 end----
除此之外，还有权限控制、性能监控、缓存处理等场景。总的来说，任何需要在多个地⽅重复执⾏的通⽤逻辑，都
可以考虑⽤ AOP 来实现。
1. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：AOP应⽤场景
2. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：AOP的使⽤场景有哪些？
3. Java ⾯试指南（付费）收录的京东⾯经同学 9 ⾯试原题：项⽬中的AOP是怎么⽤到的
memo：2025 年 7 ⽉ 8 ⽇修改⾄此，今天在星球的 VIP 群⾥⼜看到在吹⾯渣逆袭的，球友说美团、⼩红书⼋股都
没问题，看⼆哥的⾜够。
2023-06-16 11:06:13,008 [http-nio-8080-exec-3] INFO 
|00000000.1686884772947.468581113|101|c.g.p.forum.core.mdc.MdcAspect.handle(MdcAspect.jav
a:47) - ⽅法执⾏耗时: 
com.github.paicoding.forum.web.front.article.rest.ArticleRestController#recommend = 47
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 111 / 180

---

22.说说 Spring AOP 和 AspectJ 区别?
 
Spring AOP 只⽀持⽅法级别的织⼊，⽽且只能拦截 Spring 容器管理的 Bean。但是 AspectJ ⼏乎可以织⼊任何地
⽅，包括⽅法、字段、构造⽅法、异常处理等等。
从实现机制上来说，Spring AOP 是基于动态代理实现的，在运⾏时为⽬标对象创建代理，通过代理来执⾏切⾯逻
辑。⽽ AspectJ 是通过字节码织⼊来实现的，它直接修改⽬标类的字节码，把切⾯逻辑编织到⽬标⽅法中。
在实际项⽬中，我们⼤部分时候⽤的都是 Spring AOP，因为它能满⾜绝⼤多数需求，⽽且使⽤简单。只有在遇到 
Spring AOP ⽆法解决的问题时，⽐如需要织⼊第三⽅ jar 包中的⽅法，或者监控字段才会考虑引⼊ AspectJ。
Spring AOP 借鉴了很多 AspectJ 的概念和注解，我们在 Spring 中使⽤的 @Aspect 、@Pointcut 这些注解，其实
都是 AspectJ 定义的。
23.说说 AOP 和反射的区别？（补充）
 
2024 年 7 ⽉ 27 ⽇增补。
反射主要是为了让程序能够检查和操作⾃身的结构，⽐如获取类的信息、调⽤⽅法、访问字段等等。⽽ AOP 则是
为了在不修改业务代码的前提下，动态地为⽅法添加额外的⾏为，⽐如⽇志记录、事务管理等。
从技术实现来说，反射是 Java 语⾔本身提供的功能，通过 java.lang.reflect 包下的 API 来实现。⽽ AOP 通常
需要框架⽀持，⽐如 Spring AOP 是通过动态代理实现的，⽽动态代理⼜是基于反射实现的。
1. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：抛开Spring，讲讲反射和动态代理？那三
种代理模式怎么实现的？
24.
说说JDK动态代理和CGLIB代理的区别？
 
JDK 动态代理和 CGLIB 代理是 Spring AOP ⽤来创建代理对象的两种⽅式。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 112 / 180

---

从使⽤条件来说，JDK 动态代理要求⽬标类必须实现⾄少⼀个接⼝，因为它是基于接⼝来创建代理的。⽽ CGLIB 代
理不需要⽬标类实现接⼝，它是通过继承⽬标类来创建代理的。
这是两者最根本的区别。⽐如我们有⼀个 TransferService 接⼝和 TransferServiceImpl 实现类，如果⽤ JDK 动态
代理，创建的代理对象会实现 TransferService 接⼝；
如果⽤ CGLIB，代理对象会继承 TransferServiceImpl 类。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 113 / 180

---

从实现原理来说，JDK 动态代理是 Java 原⽣⽀持的，它通过反射机制在运⾏时动态创建⼀个实现了指定接⼝的代
理类。当我们调⽤代理对象的⽅法时，会被转发到 InvocationHandler 的 invoke ⽅法中，我们可以在这个⽅法⾥
插⼊切⾯逻辑，然后再通过反射调⽤⽬标对象的真实⽅法。
CGLIB 则是⼀个第三⽅的字节码⽣成库，它通过 ASM 字节码框架动态⽣成⽬标类的⼦类，然后重写⽗类的⽅法来
插⼊切⾯逻辑。
public class JdkProxyExample {
    public static void main(String[] args) {
        UserService target = new UserServiceImpl();
        
        UserService proxy = (UserService) Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            (proxy1, method, args1) -> {
                System.out.println("Before method: " + method.getName());
                Object result = method.invoke(target, args1);
                System.out.println("After method: " + method.getName());
                return result;
            }
        );
        
        proxy.findUser(1L);
    }
}
public class CglibProxyExample {
    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(UserController.class);
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy 
proxy) throws Throwable {
                System.out.println("Before method: " + method.getName());
                Object result = proxy.invokeSuper(obj, args);
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 114 / 180

---

选择 CGLIB 还是 JDK 动态代理？
 
如果⽬标对象没有实现任何接⼝，就只能使⽤ CGLIB 代理，就⽐如说 Controller 层的类。
如果⽬标对象实现了接⼝，通常⾸选 JDK 动态代理，⽐如说 Service 层的类，⼀般都会先定义接⼝，再实现接⼝。
在 Spring Boot 2.0 之后，Spring AOP 默认使⽤ CGLIB 代理。这是因为 Spring Boot 作为⼀个追求“约定优于配置”
的框架，选择 CGLIB，可以简化开发者的⼼智负担，避免因为忘记实现接⼝⽽导致 AOP 不⽣效的问题。
                System.out.println("After method: " + method.getName());
                return result;
            }
        });
        
        UserController proxy = (UserController) enhancer.create();
        proxy.getUser(1L);
    }
}
// 没有实现接⼝的Controller
@RestController
public class ArticleController {
    @MdcDot(bizCode = "article.create")
    public ResponseVo<String> create(@RequestBody ArticleReq req) {
        // 业务逻辑
    }
}
// 接⼝定义
public interface ArticleService {
    void saveArticle(Article article);
}
// 实现类
@Service
public class ArticleServiceImpl implements ArticleService {
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveArticle(Article article) {
        // 业务逻辑
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 115 / 180

---

你会⽤ JDK 动态代理吗？
 
会的。
假设我们有这样⼀个⼩场景，客服中转，解决⽤户问题：
我们可以⽤ JDK 动态代理来实现这个场景。JDK 动态代理的核⼼是通过反射机制在运⾏时创建⼀个实现了指定接⼝
的代理类。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 116 / 180

---

第⼀步，创建接⼝。
第⼆步，实现接⼝。
第三步，使⽤⽤反射⽣成⽬标对象的代理，这⾥⽤了⼀个匿名内部类⽅式重写 InvocationHandler ⽅法。
public interface ISolver {
    void solve();
}
public class Solver implements ISolver {
    @Override
    public void solve() {
        System.out.println("疯狂掉头发解决问题……");
    }
}
public class ProxyFactory {
    // 维护⼀个⽬标对象
    private Object target;
    public ProxyFactory(Object target) {
        this.target = target;
    }
    // 为⽬标对象⽣成代理对象
    public Object getProxyInstance() {
        return Proxy.newProxyInstance(target.getClass().getClassLoader(), 
target.getClass().getInterfaces(),
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 117 / 180

---

第四步，⽣成⼀个代理对象实例，通过代理对象调⽤⽬标对象⽅法。
你会⽤ CGLIB 动态代理吗？
 
会的。
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) 
throws Throwable {
                        System.out.println("请问有什么可以帮到您？");
                        // 调⽤⽬标对象⽅法
                        Object returnValue = method.invoke(target, args);
                        System.out.println("问题已经解决啦！");
                        return null;
                    }
                });
    }
}
public class Client {
    public static void main(String[] args) {
        //⽬标对象:程序员
        ISolver developer = new Solver();
        //代理：客服⼩姐姐
        ISolver csProxy = (ISolver) new ProxyFactory(developer).getProxyInstance();
        //⽬标⽅法：解决问题
        csProxy.solve();
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 118 / 180

---

第⼀步：定义⽬标类 Solver，定义 solve ⽅法，模拟解决问题的⾏为。⽬标类不需要实现任何接⼝，这与 JDK 动态
代理的要求不同。
第⼆步：创建代理⼯⼚ ProxyFactory，使⽤ CGLIB 的 Enhancer 类来⽣成⽬标类的⼦类（代理对象）。CGLIB 允
许我们在运⾏时动态创建⼀个继承⾃⽬标类的代理类，并重写⽬标⽅法。
第三步：创建客户端 Client，获取代理对象并调⽤⽬标⽅法。
public class Solver {
    public void solve() {
        System.out.println("疯狂掉头发解决问题……");
    }
}
public class ProxyFactory implements MethodInterceptor {
    //维护⼀个⽬标对象
    private Object target;
    public ProxyFactory(Object target) {
        this.target = target;
    }
    //为⽬标对象⽣成代理对象
    public Object getProxyInstance() {
        //⼯具类
        Enhancer en = new Enhancer();
        //设置⽗类
        en.setSuperclass(target.getClass());
        //设置回调函数
        en.setCallback(this);
        //创建⼦类对象代理
        return en.create();
    }
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) 
throws Throwable {
        System.out.println("请问有什么可以帮到您？");
        // 执⾏⽬标对象的⽅法
        Object returnValue = method.invoke(target, args);
        System.out.println("问题已经解决啦！");
        return null;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 119 / 180

---

1. Java ⾯试指南（付费）收录的帆软同学 3 Java 后端⼀⾯的原题：cglib 的原理
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：Spring AOP 实现原理
3. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 F ⾯试原题：两种动态代理的区别
4. Java ⾯试指南（付费）收录的字节跳动⾯经同学 8 Java 后端实习⼀⾯⾯试原题：spring的aop是如何实
现的
5. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 20 ⼆⾯⾯试原题：spring aop的底层原理是什么？
6. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：java的反射机制，反射的应
⽤场景AOP的实现原理是什么，与动态代理和反射有什么区别
7. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：代理介绍⼀下，jdk和cglib的区
别
8. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：Spring AOP的实现原理？JDK动态代理和CGLib动
态代理的各⾃实现及其区别？现在需要统计⽅法的具体执⾏时间，说下如何使⽤AOP来实现？
9. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：了解AOP底层是怎么做的吗？
memo：2025 年 7 ⽉ 10 ⽇修改⾄此，今天在给球友修改简历的时候碰到⼀个对星球⾮常认可的球友，他在我的
帮助下也顺利找到了实习，并且⼤家也可以看到，他提到的这些路线规划问题、简历书写问题、秋招准备问题、项
⽬问题，都可以在星球⾥找到答案。
public class Client {
    public static void main(String[] args) {
        //⽬标对象:程序员
        Solver developer = new Solver();
        //代理：客服⼩姐姐
        Solver csProxy = (Solver) new ProxyFactory(developer).getProxyInstance();
        //⽬标⽅法：解决问题
        csProxy.solve();
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 120 / 180

---

事务
 
25.
说说你对Spring事务的理解？
 
Spring 提供了两种事务管理⽅式，编程式事务和声明式事务。编程式事务就是我们要⼿动调⽤事务的开始、提
交、回滚这些操作，虽然灵活但是代码⽐较繁琐。声明式事务只需要在需要事务的⽅法上加上 @Transactional 
注解就好了，Spring 会帮我们⾃动处理事务的整个⽣命周期。
----这部分可以不背，⽅便⼤家理解 start----
编程式事务可以使⽤ TransactionTemplate 和 PlatformTransactionManager 来实现，允许我们在代码中直接控
制事务的边界。
public class AccountService {
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 121 / 180

---

----这部分可以不背，⽅便⼤家理解 end----
Spring 事务的底层实现是通过 AOP 来完成的。当我们在⽅法上加 @Transactional 注解后，Spring 会为这个 
Bean 创建代理对象，在⽅法执⾏前开启事务，⽅法正常返回时提交事务，如果⽅法抛出异常就回滚事务。
声明式事务的优点是不需要在业务逻辑代码中掺杂事务管理的代码，缺点是，最细粒度只能到⽅法级别，⽆法到代
码块级别。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：Spring 事务怎么实现的
2. Java ⾯试指南（付费）收录的农业银⾏⾯经同学 7 Java 后端⾯试原题：Spring 如何保证事务
3. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：Spring的事务⽤过吗，在项⽬⾥
⾯怎么使⽤的
4. Java ⾯试指南（付费）收录的虾⽪⾯经同学 13 ⼀⾯⾯试原题：spring事务
5. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：如何使⽤spring实现事务
    private TransactionTemplate transactionTemplate;
    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }
    public void transfer(final String out, final String in, final Double money) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // 转出
                accountDao.outMoney(out, money);
                // 转⼊
                accountDao.inMoney(in, money);
            }
        });
    }
}
@Service
public class AccountService {
    @Autowired
    private AccountDao accountDao;
    @Transactional
    public void transfer(String out, String in, Double money) {
        // 转出
        accountDao.outMoney(out, money);
        // 转⼊
        accountDao.inMoney(in, money);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 122 / 180

---

memo：2025 年 7 ⽉ 11 ⽇修改⾄此，今天有球友在 VIP 群⾥讲，⾯渣逆袭的 Redis、MySQL、JVM 篇⾮常强；
另外⼀个球友也是继续⼝碑说，⾯过⼏次全包过。
26.声明式事务的实现原理了解吗？
 
Spring 的声明式事务管理是通过 AOP 和代理机制实现的，⼤致可以分为两个阶段。
第⼀个阶段发⽣在 Spring 容器启动时，它会扫描所有的 Bean。如果发现某个 Bean 的⽅法上标注了 
@Transactional 注解，Spring 不会直接返回这个原始的 Bean 实例。⽽是为这个 Bean 创建⼀个代理对象。这
个代理对象拥有和原始对象完全相同的⽅法，但在内部悄悄地包裹了事务处理的逻辑。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 123 / 180

---

第⼆个阶段发⽣在⽅法调⽤的运⾏阶段，当我们的代码调⽤那个被 @Transactional 注解修饰的⽅法时，实际上
调⽤的是 Spring 创建的那个代理对象的⽅法。
事务拦截器会在代理对象执⾏真正的业务逻辑之前，根据 @Transactional 注解的配置获取事务属性，⽐如传播
⾏为、隔离级别等，然后通过事务管理器来开启⼀个新的事务。并从数据库连接池获取⼀个连接，关闭其⾃动提
交。
public class TransactionInterceptor implements MethodInterceptor {
    @Override
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 124 / 180

---

接着，代理对象会调⽤原始 Bean 实例中真正的业务⽅法，如果业务⽅法顺利执⾏完毕，没有抛出任何异常，那么
拦截器就会通过事务管理器提交事务，将之前的所有数据库操作永久保存。
如果业务⽅法抛出了异常，拦截器会捕获到这个异常，并通过事务管理器回滚事务，将之前的所有数据库操作撤
销。
最后，⽆论事务是提交还是回滚，拦截器都会释放数据库连接。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：Spring 事务怎么实现的
memo：2025 年 7 ⽉ 15 ⽇修改⾄此，今天在给球友修改简历时，看到球友说简历修改后拿到了星环科技内推的
实习机会，也学到了很多，并且真诚的感谢了 Java ⽅⾯的⼋股⾯试题对他的帮助。讲真，能看到⼤家最真实的反
馈，我挺开⼼的。
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取事务属性
        TransactionAttribute txAttr = getTransactionAttribute(invocation.getMethod(), 
invocation.getThis().getClass());
        // 开始事务
        TransactionStatus status = transactionManager.getTransaction(txAttr);
        try {
            // 执⾏⽬标⽅法
            Object retVal = invocation.proceed();
            // 提交事务
            transactionManager.commit(status);
            return retVal;
        } catch (Throwable ex) {
            // 回滚事务
            transactionManager.rollback(status);
            throw ex;
        }
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 125 / 180

---

27.@Transactional在哪些情况下会失效？
 
@Transactional 虽然⽤起来很⽅便，但确实有⼀些“坑”，如果使⽤不当是会导致事务失效的。根据我的理解和实
践，主要有以下⼏种常⻅情况：
第⼀种，@Transactional 注解⽤在⾮ public 修饰的⽅法上。
Spring 的 AOP 代理机制决定了它⽆法代理 private ⽅法。因为 private ⽅法在⼦类中是不可⻅的，代理类⽆法覆
盖它。因此，在 private ⽅法上加 @Transactional 注解是完全⽆效的。同理，protected 和 default 权限的⽅法
也应避免使⽤。
第⼆种，⽅法内部调⽤，这也是最容易被忽略的⼀种失效场景。如果在⼀个类的⽅法 A 中，直接调⽤本类的另外⼀
个加了 @Transactional 的⽅法 B，那么⽅法 B 的事务是不会⽣效的。
这是因为⽅法 A 调⽤⽅法 B 时，使⽤的是 this 引⽤，直接访问原始对象的⽅法，绕过了 Spring 的代理对象，也就
导致代理对象中的事务逻辑没有机会执⾏。
protected TransactionAttribute computeTransactionAttribute(Method method,
    Class<?> targetClass) {
        // Don't allow no-public methods as required.
        if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
        return null;
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 126 / 180

---

解决⽅法是把当前类作为⼀个 Bean 注⼊到⾃⼰中，然后通过这个注⼊的 Bean 来调⽤⽅法 B。
第三种，如果在事务⽅法内部⽤ try-catch 捕获了异常，但没有在 catch 块中将异常重新抛出，或者抛出⼀个新的
能触发回滚的异常，那么 Spring 的事务拦截器就⽆法感知到异常的发⽣，也就没办法回滚。
public class UserService {
    @Transactional
    public void createUser(User user) {
        // 直接调⽤本类的另⼀个⽅法，事务不会⽣效
        saveUser(user);
    }
    private void saveUser(User user) {
        // 保存⽤户逻辑
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 127 / 180

---

第四种，Spring 事务默认只对 RuntimeException 和 Error 类型的异常进⾏回滚。如果在代码中抛出的是⼀个
Checked Exception，是 Exception 的⼦类但不是 RuntimeException 的⼦类，⼜没有通过 
@Transactional(rollbackFor = Exception.class) 指定事务回归的异常类型，那么事务同样不会回滚。
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：事务传播，protected 和 private 加事务
会⽣效吗,还有那些不⽣效的情况
memo：2025 年 7 ⽉ 16 ⽇修改⾄此，今天在给球友修改简历时，看到⼀个中国海洋⼤学本，四川⼤学硕的球
友，⾮常优秀，基本上校园经历和荣誉奖项算是拉满了。能帮助到这么多优秀的球友，我也很开⼼。
@Transactional
public void process() {
    try {
        // 业务逻辑
    } catch (Exception e) {
        // 捕获异常但没有重新抛出
        // 事务不会回滚
    }
}
@Transactional
public void process() throws Exception {
    // 抛出⼀个 Checked Exception       
    throw new SQLException("This is a checked exception");
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 128 / 180

---

28.说说Spring事务的隔离级别？
 
事务的隔离级别定义了⼀个事务可以受其他并发事务影响的程度。SQL 标准定义的四个隔离级别，Spring 都⽀
持，定义在 TransactionDefinition 接⼝中。
Spring 在标准的隔离级别上定义了五个隔离级别：
其中 DEFAULT 表示使⽤底层数据库的默认隔离级别。⽐如说对于 MySQL 来说，默认的隔离级别是可重复读，那
就⽤可重复读；对于 Oracle 来说，默认是读已提交，那就⽤读已提交。在实际项⽬中，我们也通常都⽤ 
DEFAULT，让数据库⾃⼰决定合适的隔离级别。
读未提交是最低的隔离级别，允许读取未提交的数据。这种级别会出现脏读问题，也就是⼀个事务可能会读到另⼀
个事务还没提交的数据。⽐如 A 事务修改了⼀条数据但还没提交，B 事务就能读到这个修改后的值，如果 A 事务后
来回滚了，B 事务读到的就是脏数据。这个级别在实际项⽬中基本不会使⽤，因为数据⼀致性⽆法保证。
读已提交解决了脏读问题，但会出现不可重复读问题，也就是在同⼀个事务中多次读取同⼀条数据，可能得到不同
的结果。⽐如 A 事务先读了⼀条数据，然后 B 事务修改并提交了这条数据，A 事务再次读取时就会发现数据变了。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 129 / 180

---

可重复读保证在同⼀个事务中多次读取同⼀条数据的结果是⼀致的，解决了不可重复读问题。但是会出现幻读问
题，也就是在同⼀个事务中多次执⾏同⼀个查询，可能会看到不同数量的记录。⽐如 A 事务查询某个条件的记录数
是 10 条，然后 B 事务插⼊了⼀条符合条件的记录并提交，A 事务再次查询时可能会看到 11 条记录。MySQL 的 
InnoDB 存储引擎通过临键锁在很⼤程度上解决了幻读问题。
串⾏化是最⾼的隔离级别，完全串⾏化执⾏事务，可以解决所有并发问题，包括脏读、不可重复读和幻读。但是性
能是最差的，因为事务基本上是排队执⾏的。在实际项⽬中很少使⽤，除⾮对数据⼀致性有极⾼的要求。
在 Spring 中设置隔离级别也很简单，可以在 @Transactional 注解中通过 isolation 属性来指定。
不过在实际项⽬中，我们很少⼿动设置隔离级别，通常都是使⽤数据库的默认级别，只有在遇到特定的并发问题时
才会考虑调整。
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：Spring 中的事务的隔离级别，事务
的传播⾏为？
2. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 E 第⼆个部⻔ Java 后端技术⼀⾯⾯试原题：spring 的隔离
机制，默认是哪⼀种
memo：2025 年 7 ⽉ 13 ⽇修改⾄此，今天在帮球友修改简历时，碰到⼀个电⼦科技⼤学硕⼠、华中科技⼤学本
的球友，他说到，⾃⼰也在推荐星球给师弟们，真的⾮常欣慰，能有这样的⼝碑，很感动，必须要感谢球友们的⽀
持。
29.
说说Spring的事务传播机制？
 
简单来说，当⼀个事务⽅法 A 调⽤另⼀个事务⽅法 B 时，⽅法 B 的事务应该如何运⾏？是加⼊⽅法 A 的现有事
务，还是开启⼀个新事务，或者以⾮事务⽅式运⾏？这就是事务传播机制要解决的问题。
Spring 定义了七种事务传播⾏为，其中 REQUIRED 是默认的传播⾏为，表示如果当前存在事务，则加⼊该事务；
如果当前没有事务，则创建⼀个新的事务。
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public void someMethod() {
    // 业务逻辑
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 130 / 180

---

⽐如说在技术派实战项⽬中，⼀个⽤户解锁付费⽂章的操作，会涉及到创建⽀付订单、更新订单状态等好⼏个数据
库操作。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 131 / 180

---

这些不同操作的⽅法就可以放在⼀个 @Transactional 注解的⽅法⾥，它们就⾃动在同⼀个事务⾥了，要么⼀起
成功，要么⼀起失败。
当然，还有⼀些特殊情况。⽐如，我们希望记录⼀些操作⽇志，但不想因为主业务失败导致⽇志回滚。这时候 
REQUIRES_NEW 就派上⽤场了。它不管当前有没有事务，都重新开启⼀个全新的、独⽴的事务来执⾏。这样，⽇
志保存的事务和主业务的事务就互不⼲扰，即使主业务失败回滚，⽇志也能妥妥地保存下来。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 132 / 180

---

另外，还有像 SUPPORTS、 NOT_SUPPORTED 这些。SUPPORTS ⽐较佛系，有事务就⽤，没事务就不⽤，适合
⼀些不重要的更新操作。⽽ NOT_SUPPORTED 则更⼲脆，它会把当前的事务挂起，以⾮事务的⽅式去执⾏。⽐如
说我们的事务⾥需要调⽤⼀个第三⽅的、响应很慢的接⼝，如果这个调⽤也包含在事务⾥，就会⻓时间占⽤数据库
连接。把它⽤ NOT_SUPPORTED 包起来，就可以避免这个问题。
最后还有⼀个⽐较特殊的 NESTED，嵌套事务。它有点像 REQUIRES_NEW，但⼜不完全⼀样。NESTED 是⽗事务
的⼀个⼦事务，⽗事务回滚，它肯定也得回滚。但它⾃⼰回滚，却不会影响到⽗事务。这个特性在处理⼀些批量操
作，希望能部分回滚的场景下特别有⽤。不过它需要数据库⽀持 Savepoint 功能，MySQL 就⽀持。
事务能在新线程中传播吗？
 
事务传播机制是通过 ThreadLocal 实现的，所以，如果调⽤的⽅法是在新线程中，事务传播就会失效。
protected 和 private ⽅法加事务会⽣效吗？
 
我的理解是：在 private ⽅法上加事务是肯定不会⽣效的，⽽ protected ⽅法在特定的代理模式下是可能⽣效的，
但这两种⽤法都应该避免，不是推荐的使⽤⽅式。
这背后涉及到 Spring AOP 的代理机制。
我先说⼀下 JDK 动态代理，它要求⽬标类必须实现⼀个或者多个接⼝。也就意味着代理只能拦截接⼝中声明的⽅
法，⽽ protected 和 private ⽅法并不能在接⼝中声明，因此在 JDK 动态代理下，这些⽅法的事务注解是会被直接
忽略的。
那 Spring Boot 2.0 之后，Spring AOP 默认使⽤的是 CGLIB 代理。CGLIB 代理是通过继承⽬标类来创建代理对象
的。
那对于 private ⽅法来说，由于⽆法被⼦类重写，所以 CGLIB 代理也⽆法拦截，事务也就⽆法⽣效。对于 
protected ⽅法来说，因为它可以被⼦类重写，所以理论上事务是⽣效的。
----这部分可以不背，⽅便⼤家理解 start----
我们创建⼀个 protected ⽅法，名为 protectedTransactionalMethod ，它被 @Transactional 注解标记。这
个⽅法会先向数据库中插⼊⼀条记录（⼀个 TestEntity 实例）。紧接着，它会⽴即抛出⼀个 RuntimeException 。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void callExternalApi() {
    // 调⽤第三⽅接⼝
}
@Transactional
public void parentMethod() {
    new Thread(() -> childMethod()).start();
}
public void childMethod() {
    // 这⾥的操作将不会在 parentMethod 的事务范围内执⾏
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 133 / 180

---

如果事务⽣效：当 RuntimeException 抛出时，Spring 的事务管理器会捕获它，并触发事务回滚。这意味
着，之前插⼊数据库的那条记录会被撤销。最终，数据库⾥不会留下这条记录。
如果事务失效：即使 RuntimeException 被抛出，由于没有事务管理，已经执⾏的数据库插⼊操作不会被撤
销。最终，数据库⾥会留下这条记录。
我们创建了⼀个 public ⽅法 testProtectedTransaction ，它通过 this.protectedTransactionalMethod() 
的⽅式直接调⽤了那个 protected ⽅法。接着我们访问 /api/v1/test/transaction/protected 来触发这个调
⽤。
结果：数据库中会留下⼀条名为 'test-protected' 的记录。这证明了由于是内部调⽤，绕过了 Spring AOP 代理，
@Transactional 注解没有⽣效。
我们创建了另⼀个 public ⽅法 testProtectedTransactionWithSelfProxy 。在这个⽅法⾥，我们通过⼀个“⾃
注⼊”的代理对象 self 来调⽤ self.protectedTransactionalMethod() 。接着我们通过访问 
/api/v1/test/transaction/protected/proxy 来触发这个调⽤。
结果：数据库中不会留下名为 'test-protected-proxy' 的记录。这证明通过代理对象的调⽤，Spring AOP 成功拦截
并开启了事务，最终在异常发⽣时正确地回滚了事务。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 134 / 180

---

----这部分可以不背，⽅便⼤家理解 end----
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：事务的传播机制
2. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：事务传播，protected 和 private 加事务
会⽣效吗,还有那些不⽣效的情况
3. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：Spring 中的事务的隔离级别，事务
的传播⾏为？
4. Java ⾯试指南（付费）收录的oppo ⾯经同学 8 后端开发秋招⼀⾯⾯试原题：讲⼀下Spring事务传播机
制
5. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：介绍事务传播模型
memo：2025 年 7 ⽉ 14 ⽇修改⾄此，今天在帮球友修改简历时，⼀个浙江⼤学硕⼠的球友不仅拿到了腾讯 WXG 
的实习 offer，秋招也开始同步进⾏了，我只能说优秀的球友真的赶早不赶晚啊！
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 135 / 180

---

MVC
 
30.Spring MVC 的核⼼组件有哪些？
 
Spring MVC 作为 Spring 框架中处理 Web 请求的核⼼模块，它的设计遵循了经典的 MVC 模式，根据我的理解，
它的核⼼组件主要包括：
前端控制器 DispatcherServlet，这是 Spring MVC 的⼊⼝和核⼼调度器。当⼀个 HTTP 请求到达服务器时，⾸先
由 DispatcherServlet 接收。它负责将请求分发到合适的处理器，也就是 Controller 中的⽅法，并协调其他组件的
⼯作。
在 Spring Boot 项⽬中，DispatcherServlet 的启动是通过⾃动配置完成的。Spring Boot 会⾃动注册⼀个默认的 
DispatcherServlet，并将其映射到 / 。
@Bean
public ServletRegistrationBean<DispatcherServlet> 
dispatcherServletRegistration(DispatcherServlet dispatcherServlet) {
    ServletRegistrationBean<DispatcherServlet> registration = new 
ServletRegistrationBean<>(dispatcherServlet, "/"); // 默认映射路径为 "/"
    registration.setName("dispatcherServlet");
    return registration;
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 136 / 180

---

处理器映射 HandlerMapping，当⼀个请求进来时，前端控制器会询问处理器映射：“这个 URL 应该由哪个 
Controller 的哪个⽅法来处理？”然后它就会根据 @RequestMapping 、@GetMapping 这些注解来匹配请求。
处理器 Handler，实际上就是我们写的 Controller ⽅法，这是真正处理业务逻辑的地⽅。
处理器适配器 HandlerAdapter，负责调⽤该处理器的⽅法，并处理参数绑定、类型转换等。因为处理器可能有不
同的类型，⽐如注解⽅式、实现接⼝⽅式等，处理器适配器就是为了统⼀调⽤⽅式。
视图解析器 ViewResolver，处理完业务逻辑后，如果需要渲染视图，ViewResolver 会根据返回的视图名称解析实
际的视图对象，⽐如 Thymeleaf。在前后端分离的项⽬中，这个组件更多⽤于返回 JSON 数据。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 137 / 180

---

异常处理器 HandlerExceptionResolver，捕获并处理请求处理过程中抛出的异常。通常，我们可以通过 
@ControllerAdvice 和 @ExceptionHandler 来⾃定义异常处理逻辑，确保返回友好的错误响应。
除此之外，还有⽂件上传解析器 MultipartResolver，⽤于处理⽂件上传请求；拦截器 HandlerInterceptor，⽤于
在请求处理前后执⾏⼀些额外的逻辑，⽐如权限校验、⽇志记录等。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 138 / 180

---

memo：2025 年 7 ⽉ 17 ⽇修改⾄此，昨天有球友说⼿写了⼀个 Redis 的轮⼦项⽬，⽤的 Go 语⾔，我今天去看了
⼀下 doc 和代码，写得⾮常好，代码注释很清晰，doc 写得很详细，能看得出球友的⽤⼼。⼿写轮⼦是⾮常考验⼀
个⼈的能⼒的，我看他实现的功能有：字符串和散列的数据类型、RESP 协议解析器、使⽤goroutine 来同时处理
多个连接、持久化 AOF 协议等，⾮常强。
31.
Spring MVC 的⼯作流程了解吗？
 
简单来说，Spring MVC 是⼀个基于 Servlet 的请求处理框架，核⼼流程可以概括为：请求接收 → 路由分发 → 控
制器处理 → 视图解析。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 139 / 180

---

⽤户发起的 HTTP 请求，⾸先会被 DispatcherServlet 捕获，这是 Spring MVC 的“前端控制器”，负责拦截所有请
求，起到统⼀⼊⼝的作⽤。
DispatcherServlet 接收到请求后，会根据 URL、请求⽅法等信息，交给 HandlerMapping 进⾏路由匹配，查找对
应的处理器，也就是 Controller 中的具体⽅法。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 140 / 180

---

找到对应 Controller ⽅法后，DispatcherServlet 会委托给处理器适配器 HandlerAdapter 进⾏调⽤。处理器适配
器负责执⾏⽅法本身，并处理参数绑定、数据类型转换等。在注解驱动开发中，常⽤的是 
RequestMappingHandlerAdapter。这⼀层会把请求参数⾃动注⼊到⽅法形参中，并调⽤ Controller 执⾏实际的
业务逻辑。
Controller ⽅法最终会返回结果，⽐如视图名称、ModelAndView 或直接返回 JSON 数据。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 141 / 180

---

当 Controller ⽅法返回视图名时，DispatcherServlet 会调⽤ ViewResolver 将其解析为实际的 View 对象，⽐如 
Thymeleaf ⻚⾯。在前后端分离的接⼝项⽬中，这⼀步则通常是返回 JSON 数据。
最后，由 View 对象完成渲染，或者将 JSON 结果直接通过 DispatcherServlet 返回给客户端。
为什么还需要 HandlerAdapter？
 
Spring MVC ⽀持多种⻛格的处理器，⽐如基于 @Controller 注解的处理器、实现了 Controller 接⼝的处理器
等。如果没有处理器适配器，DispatcherServlet 就需要硬编码每种处理器的调⽤⽅式，框架就会变得⾮常僵硬
——新增⼀种 Controller 类型，就必须改 DispatcherServlet 的代码。
因此，Spring 引⼊了 HandlerAdapter 作为适配器，屏蔽不同控制器的差异，给 DispatcherServlet 提供⼀个统⼀
的调⽤⼊⼝。
⽐如说，如果是实现了 Controller 接⼝的处理器，DispatcherServlet 会使⽤ SimpleControllerHandlerAdapter 
来适配它。
如果是使⽤ @RequestMapping 注解的处理器，DispatcherServlet 则会使⽤ RequestMappingHandlerAdapter 
来适配。
public class SimpleControllerHandlerAdapter implements HandlerAdapter {
  @Override
  public boolean supports(Object handler) {
    return (handler instanceof Controller);
  }
  @Override
  @Nullable
  public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, 
Object handler)
      throws Exception {
    return ((Controller) handler).handleRequest(request, response);
  }
    // ... 省略⼀个⽆关⽅法 ...
}
public class RequestMappingHandlerAdapter implements HandlerAdapter {
    @Override   
    public boolean supports(Object handler) {
        return (handler instanceof HandlerMethod);
    }
    @Override
    @Nullable
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, 
Object handler)
            throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        // 执⾏⽅法并返回 ModelAndView      
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 142 / 180

---

1. Java ⾯试指南（付费）收录的腾讯 Java 后端实习⼀⾯原题：说说前端发起请求到 SpringMVC 的整个处
理流程。
2. Java ⾯试指南（付费）收录的国企⾯试原题：说说 SpringMVC 的流程吧
3. Java ⾯试指南（付费）收录的⼩公司⾯经同学 5 Java 后端⾯试原题：springMVC ⼯作流程 我⼤概就是
按⾯渣逆袭⾥答的，答到⼀半打断我：然后会有个 Handler，这个 Handler 是什么东⻄啊。前⾯ 
Handler 不是已经知道 controller 了吗，我直接执⾏不就⾏了，为什么还要 Adapter 呢。
4. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：SpringMVC框架 
5. Java ⾯试指南（付费）收录的字节跳动同学 17 后端技术⾯试原题：springmvc执⾏流程
memo：2025 年 7 ⽉ 18 ⽇修改⾄此，今天在帮球友修改简历时，碰到⼀个荣誉奖项基本拉满的球友，国家励志
奖学⾦、省级⽐赛、校级三好学⽣等，那这⾥也是温馨提醒⼀下⼤家，学校的荣誉奖项如果你有能⼒争取，有时间
争取，还是尽量争取⼀下的，尤其是求职央国企的时候，会⾮常有⽤。
32.SpringMVC Restful ⻛格的接⼝流程是什么样的呢？
 
在传统的 MVC 中，Controller ⽅法通常返回⼀个视图名称或者 ModelAndView 对象，然后由视图解析器 
ViewResolver 解析并渲染成 HTML ⻚⾯。但在 RESTful 架构中，通常返回的是 JSON 或 XML，不再是⼀个完整的
⻚⾯。
其中很重要的两个注解：@RestController 相当于 @Controller 和 @ResponseBody 的结合。当在⼀个类上使
⽤ @RestController 时，它会告诉 Spring 这个类中所有⽅法的返回值都应该被直接写⼊ HTTP 响应体中，⽽不
再被解析为视图。
@ResponseBody 可以⽤在⽅法级别，作⽤相同。它标志着该⽅法的返回值将作为响应体内容，Spring 会跳过视图
解析的步骤。
HttpMessageConverter 是实现 RESTful ⻛格的关键。当 Spring 检测到 @ResponseBody 注解时，它会使⽤ 
HttpMessageConverter 来将 Controller ⽅法返回的 Java 对象序列化成指定的格式，如 JSON。
默认情况下，如果类路径下有 Jackson 库，Spring Boot 会⾃动配置 MappingJackson2HttpMessageConverter 
来处理 JSON 的转换。相应的，对于带有 @RequestBody 注解的⽅法参数，它也会⽤这个转换器将请求体中的 
JSON 数据反序列化成 Java 对象。
        return invokeHandlerMethod(handlerMethod, request, response);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 143 / 180

---

所以，RESTful 接⼝的流程可以概括为：请求到达前端控制器 DispatcherServlet → 通过 HandlerMapping 找到对
应的 Controller ⽅法 → 执⾏⽅法并返回数据 → 使⽤ HttpMessageConverter 将数据转换为 JSON 或 XML 格式 → 
直接写⼊ HTTP 响应体。
总结来说，RESTful 接⼝的流程通过 @RestController 和 HttpMessageConverter “抄了近道”，省略了 
ViewResolver 和 View 的渲染过程，直接将数据转换为指定的格式返回，⾮常适合前后端分离的应⽤场景。
memo：2025 年 7 ⽉ 19 ⽇修改⾄此，今天有球友私信我说，拿到了京东的实习 offer，问接下来的秋招该怎么准
备？那 7 ⽉份实习 Offer 确实会⽐较少，但仍然有⼀部分，如果这个阶段还想要冲实习的话，确实可以捡漏。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 144 / 180

---

Spring Boot
 
33.
介绍⼀下 SpringBoot？
 
Spring Boot 可以说是 Spring ⽣态的⼀个重⼤突破，它极⼤地简化了 Spring 应⽤的开发和部署过程。
以前我们⽤ Spring 开发项⽬的时候，需要配置⼀⼤堆 XML ⽂件，包括 Bean 的定义、数据源配置、事务配置等
等，⾮常繁琐。⽽且还要⼿动管理各种 jar 包的依赖关系，很容易出现版本冲突的问题。部署的时候还要单独搭建 
Tomcat 服务器，整个过程很复杂。Spring Boot 就是为了解决这些痛点⽽⽣的。
“约定⼤于配置”是 Spring Boot 最核⼼的理念。它预设了很多默认配置，⽐如默认使⽤内嵌的 Tomcat 服务器，默
认的⽇志框架是 Logback 等等。这样，我们开发者就只需要关注业务逻辑，不⽤再纠结于各种配置细节。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 145 / 180

---

⾃动装配也是 Spring Boot 的⼀⼤特⾊，它会根据项⽬中引⼊的依赖⾃动配置合适的 Bean。⽐如说，我们引⼊了 
Spring Data JPA，Spring Boot 就会⾃动配置数据源；⽐如说，我们引⼊了 Spring Security，Spring Boot 就会⾃
动配置安全相关的 Bean。
Spring Boot 还提供了很多开箱即⽤的功能，⽐如 Actuator 监控、DevTools 开发⼯具、Spring Boot Starter 等
等。Actuator 可以让我们轻松监控应⽤的健康状态、性能指标等；DevTools 可以加快开发效率，⽐如⾃动重启、
热部署等；Spring Boot Starter 则是⼀些预配置好的依赖集合，让我们可以快速引⼊某些常⽤的功能。
Spring Boot常⽤注解有哪些？
 
Spring Boot 的注解很多，我就挑两个说⼀下吧。
@SpringBootApplication ：这是 Spring Boot 的核⼼注解，它是⼀个组合注解，包含了 
@Configuration 、@EnableAutoConfiguration 和 @ComponentScan 。它标志着⼀个 Spring Boot 应⽤
的⼊⼝。
@SpringBootTest ：⽤于测试 Spring Boot 应⽤的注解，它会加载整个 Spring 上下⽂，适合集成测试。
1. Java ⾯试指南（付费）收录的华为 OD ⾯经中出现过该题：讲讲 Spring Boot 的特性。
2. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：SpringBoot基本
原理
3. Java ⾯试指南（付费）收录的国企零碎⾯经同学 9 ⾯试原题：Springboot基于Spring的配置有哪⼏种
4. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：springboot常⽤注解
memo：2025 年 7 ⽉ 20 ⽇修改⾄此，今天⼜有球友发私信说，后悔没有早⼀点加⼊星球，加⼊星球后，才发现
⼤家早早就为⾃⼰的未来去拼搏了。很真实，好吧，这就是星球的价值所在，100 多块钱的⻔票就能提供学校⼏万
学费给你不了的东⻄。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 146 / 180

---

34.
Spring Boot的⾃动装配原理了解吗？
 
在 Spring Boot 中，开启⾃动装配的注解是@EnableAutoConfiguration 。这个注解会告诉 Spring 去扫描所有可
⽤的⾃动配置类。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 147 / 180

---

Spring Boot 为了进⼀步简化，把这个注解包含到了 @SpringBootApplication 注解中。也就是说，当我们在主
类上使⽤ @SpringBootApplication 注解时，实际上就已经开启了⾃动装配。
当 main ⽅法运⾏的时候，Spring 会去类路径下找 spring.factories 这个⽂件，读取⾥⾯配置的⾃动配置类列
表。⽐如在我们的技术派项⽬中，paicoding-core 和 paicoding-service 模块⾥都有 spring.factories，分别注册
了 ForumCoreAutoConfig 和 ServiceAutoConfig，这两个配置类就会在项⽬启动的时候被⾃动加载。
然后每个⾃动配置类内部，通常会有⼀个 @Configuration 注解，同时结合各种 @Conditional 注解来做条件控
制。像技术派的 RabbitMqAutoConfig 类，就⽤了 @ConditionalOnProperty 注解来判断配置⽂件⾥有没有开启 
rabbitmq.switchFlag，来决定是否初始化 RabbitMQ 消费线程。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 148 / 180

---

另外⼀个常⻅的场景是⾃动注⼊ Bean，⽐如技术派的 ServiceAutoConfig 中就⽤了 @ComponentScan 来扫描 
service 包，@MapperScan 扫描 MyBatis 的 mapper 接⼝，实现业务层和 DAO 层的⾃动装配。
具体的执⾏过程可以总结为：Spring Boot 项⽬在启动时加载所有的⾃动配置类，然后逐个检查它们的⽣效条件，
当条件满⾜时就实例化并创建相应的 Bean。
⾃动装配的执⾏时机是在 Spring 容器启动的时候。具体来说是在 ConfigurationClassPostProcessor 这个 
BeanPostProcessor 中处理的，它会解析 @Configuration 类，包括通过 @Import 导⼊的⾃动配置类。
protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata 
annotationMetadata) {
    // 检查⾃动配置是否启⽤。如果@ConditionalOnClass等条件注解使得⾃动配置不适⽤于当前环境，则返回⼀个
空的配置条⽬。
    if (!isEnabled(annotationMetadata)) {
        return EMPTY_ENTRY;
    }
    // 获取启动类上的@EnableAutoConfiguration注解的属性，这可能包括对特定⾃动配置类的排除。
    AnnotationAttributes attributes = getAttributes(annotationMetadata);
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 149 / 180

---

1. Java ⾯试指南（付费）收录的滴滴同学 2 技术⼆⾯的原题：SpringBoot 启动时为什么能够⾃动装配
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：Spring Boot 如何做到启动的
时候注⼊⼀些 bean
3. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 3 Java 技术⼀⾯⾯试原题：说⼀下 Spring Boot 的⾃动
装配原理
4. Java ⾯试指南（付费）收录的农业银⾏同学 1 ⾯试原题：spring boot 的⾃动装配
5. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：SpringBoot如何
实现⾃动装配
6. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：⾃动配置怎么实现的？
memo：2025 年 7 ⽉ 21 ⽇修改⾄此，今天有球友发私信说，拿到了亚信科技+新⽯器⽆⼈⻋的 offer，问我该如
何选择，如果是你，你会如何选择呢？
    // 从spring.factories中获取所有候选的⾃动配置类。这是通过加载META-INF/spring.factories⽂件中对
应的条⽬来实现的。
    List<String> configurations = getCandidateConfigurations(annotationMetadata, 
attributes);
    // 移除配置列表中的重复项，确保每个⾃动配置类只被考虑⼀次。
    configurations = removeDuplicates(configurations);
    // 根据注解属性解析出需要排除的⾃动配置类。
    Set<String> exclusions = getExclusions(annotationMetadata, attributes);
    // 检查排除的类是否存在于候选配置中，如果存在，则抛出异常。
    checkExcludedClasses(configurations, exclusions);
    // 从候选配置中移除排除的类。
    configurations.removeAll(exclusions);
    // 应⽤过滤器进⼀步筛选⾃动配置类。过滤器可能基于条件注解如@ConditionalOnBean等来排除特定的配置
类。
    configurations = getConfigurationClassFilter().filter(configurations);
    // 触发⾃动配置导⼊事件，允许监听器对⾃动配置过程进⾏⼲预。
    fireAutoConfigurationImportEvents(configurations, exclusions);
    // 创建并返回⼀个包含最终确定的⾃动配置类和排除的配置类的AutoConfigurationEntry对象。
    return new AutoConfigurationEntry(configurations, exclusions);
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 150 / 180

---

35.
如何⾃定义⼀个 SpringBoot Starter?
 
第⼀步，SpringBoot 官⽅建议第三⽅ starter 的命名格式是 xxx-spring-boot-starter，所以我们可以创建⼀个名为 
my-spring-boot-starter 的项⽬，⼀共包括两个模块，⼀个是 autoconfigure 模块，包含⾃动配置逻辑；⼀个
是 starter 模块，只包含依赖声明。
第⼆步，创建⼀个⾃动配置类，通常在 autoconfigure 包下，该类的作⽤是根据配置⽂件中的属性来创建和配置 
Bean。
<properties>
    <spring.boot.version>2.3.1.RELEASE</spring.boot.version>
</properties>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
        <version>${spring.boot.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
        <version>${spring.boot.version}</version>
    </dependency>
</dependencies>
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 151 / 180

---

第三步，创建⼀个配置属性类，⽤于读取配置⽂件中的属性。通常使⽤ @ConfigurationProperties 注解来标记
这个类。
第四步，创建⼀个简单的服务类，⽤于提供业务逻辑。
第五步，在 src/main/resources/META-INF ⽬录下创建⼀个名为 spring.factories ⽂件，告诉 SpringBoot 在启
动时要加载我们的⾃动配置类。
第六步，使⽤ Maven 打包这个项⽬。
@Configuration
@EnableConfigurationProperties(MyStarterProperties.class)
public class MyServiceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyStarterProperties properties) {
        return new MyService(properties.getMessage());
    }
}
@ConfigurationProperties(prefix = "mystarter")
public class MyStarterProperties {
    private String message = "⼆哥的 Java 进阶之路不错啊!";
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
public class MyService {
    private final String message;
    public MyService(String message) {
        this.message = message;
    }
    public String getMessage() {
        return message;
    }
}
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.itwanger.mystarter.autoconfigure.MyServiceAutoConfiguration
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 152 / 180

---

第七步，在其他的 Spring Boot 项⽬中，通过 Maven 来添加这个⾃定义的 Starter 依赖，并通过 
application.properties 配置信息：
然后就可以在 Spring Boot 项⽬中注⼊ MyStarterProperties 来使⽤它。
启动项⽬，然后在浏览器中输⼊ localhost:8081/hello ，就可以看到返回的内容是 javabetter.cn ，说明我
们的⾃定义 Starter 已经成功⼯作了。
mvn clean install
mystarter.message=javabetter.cn
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 153 / 180

---

Spring Boot Starter 的原理了解吗？
 
Starter 的核⼼思想是把相关的依赖打包在⼀起，让开发者只需要引⼊⼀个 starter 依赖，就能获得完整的功能模
块。
当我们在 pom.xml 中引⼊⼀个 starter 时，Maven 就会⾃动解析这个 starter 的依赖树，把所有需要的 jar 包都下
载下来。
每个 starter 都会包含对应的⾃动配置类，这些配置类通过条件注解来判断是否应该⽣效。⽐如当我们引⼊了 
spring-boot-starter-web ，它会⾃动配置 Spring MVC、内嵌的 Tomcat 服务器等。
spring.factories ⽂件是 Spring Boot ⾃动装配的核⼼，它位于每个 starter 的 META-INF ⽬录下。这个⽂件列出
了所有的⾃动配置类，Spring Boot 在启动时会读取这个⽂件，加载对应的配置类。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：你封装过 springboot 
starter 吗？
2. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 20 ⼆⾯⾯试原题：Spring Boot Starter 的原理了解
吗？
3. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：为什么使⽤SpringBoot？SpringBoot⾃动装配的
原理及流程？@Import的作⽤？如果想让SpringBoot对⾃定义的jar包进⾏⾃动配置的话，需要怎么
做？
memo：2025 年 7 ⽉ 22 ⽇修改⾄此，今天有球友在 VIP 群⾥聊天，发现两个⼈都在⼩红书，有缘分的很，那能
去⼩红书实习，基本上秋招就算是稳如⽼狗了
，这家独⻆兽的实习含⾦量还是⾮常⾼的。
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.demo.autoconfigure.DemoAutoConfiguration,\
com.example.demo.autoconfigure.AnotherAutoConfiguration
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 154 / 180

---

36.
Spring Boot 启动原理了解吗？
 
Spring Boot 的启动主要围绕两个核⼼展开，⼀个是 @SpringBootApplication 注解，⼀个是 
SpringApplication.run() ⽅法。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 155 / 180

---

我先说⼀下 @SpringBootApplication 注解，它是⼀个组合注解，包含了 @SpringBootConfiguration 、
@EnableAutoConfiguration 和 @ComponentScan ，这三个注解的作⽤分别是：
@SpringBootConfiguration ：标记这个类是⼀个 Spring Boot 配置类，相当于⼀个 Spring 配置⽂件。
@EnableAutoConfiguration ：告诉 Spring Boot 可以进⾏⾃动配置。⽐如说，项⽬引⼊了 Spring MVC 的
依赖，那么 Spring Boot 就会⾃动配置 DispatcherServlet、HandlerMapping 等组件。
@ComponentScan ：扫描当前包及其⼦包下的组件，注册为 Bean。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 156 / 180

---

好，接下来我再说⼀下 SpringApplication.run() ⽅法，它是 Spring Boot 项⽬的启动⼊⼝，内部流程⼤致可
以分为 5 个步骤：
①、创建 SpringApplication 实例，并识别应⽤类型，⽐如说是标准的 Servlet Web 还是响应式的 WebFlux，然后
准备监听器和初始化监听容器。
②、创建并准备 ApplicationContext，将主类作为配置源进⾏加载。
③、刷新 Spring 上下⽂，触发 Bean 的实例化，⽐如说扫描并注册 @ComponentScan 指定路径下的 Bean。
④、触发⾃动配置，在 Spring Boot 2.7 及之前是通过 spring.factories 加载的，3.x 是通过读取 
AutoConfiguration.imports ，并结合 @ConditionalOn 系列注解依据条件注册 Bean。
⑤、如果引⼊了 Web 相关依赖，会创建并启动 Tomcat 容器，完成 HTTP 端⼝监听。
关键的代码逻辑如下：
public ConfigurableApplicationContext run(String... args) {
    // 1. 创建启动时的监听器并触发启动事件
    SpringApplicationRunListeners listeners = getRunListeners(args);
    listeners.starting();
    // 2. 准备运⾏环境
    ConfigurableEnvironment environment = prepareEnvironment(listeners);
    configureIgnoreBeanInfo(environment);
    // 3. 创建上下⽂
    ConfigurableApplicationContext context = createApplicationContext();
    try {
        // 4. 准备上下⽂
        prepareContext(context, environment, listeners, args);
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 157 / 180

---

为什么 Spring Boot 在启动的时候能够找到 main ⽅法上的@SpringBootApplication 注
解？
 
其实 Spring Boot 并不是⾃⼰找到 @SpringBootApplication 注解的，⽽是我们通过程序告诉它的。
我们把 Application.class 作为参数传给了 run ⽅法。这个 Application 类标注了 @SpringBootApplication 
注解，⽤来告诉 Spring Boot：请⽤这个类作为配置类来启动。
然后，SpringApplication 在运⾏时就会把这个类注册到 Spring 容器中。
Spring Boot 默认的包扫描路径是什么？
 
Spring Boot 默认的包扫描路径是主类所在的包及其⼦包。
⽐如说在技术派实战项⽬中，启动类QuickForumApplication 所在的包是
com.github.paicoding.forum.web ，那么 Spring Boot 默认会扫描com.github.paicoding.forum.web 包及
其⼦包下的所有组件。
        // 5. 刷新上下⽂，完成 Bean 初始化和装配
        refreshContext(context);
        // 6. 调⽤运⾏器
        afterRefresh(context, args);
        // 7. 触发启动完成事件
        listeners.started(context);
    } catch (Exception ex) {
        handleRunFailure(context, ex, listeners);
    }
    return context;
}
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 158 / 180

---

1. Java ⾯试指南（付费）收录的滴滴同学 2 技术⼆⾯的原题：为什么 Spring Boot 启动时能找到 Main 类
上⾯的注解
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：Spring Boot 默认的包扫描路
径？
3. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：@SpringBootApplication 注解了
解吗？
4. Java ⾯试指南（付费）收录的国企零碎⾯经同学 9 ⾯试原题：Springboot的⼯作原理？
5. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：SpringBoot启动流程（忘
了）
6. Java ⾯试指南（付费）收录的哔哩哔哩同学 1 ⼆⾯⾯试原题：springBoot启动机制，启动之后做了哪
些步骤
memo：2025 年 8 ⽉ 10 ⽇修改⾄此，今天在修改球友简历的时候，很感动，因为有球友说，他给周围很多⼈安
利了⼆哥的编程星球，并且反向很不错。真的很感谢，球友们的⼝碑，没有⼤家，真⾛不到现在。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 159 / 180

---

37.说⼀下 SpringBoot 和 SpringMVC 的区别？（补充）
 
2024 年 04 ⽉ 04 ⽇增补
SpringMVC 是 Spring 的⼀个模块，专⻔⽤来做 Web 开发，处理 HTTP 请求和响应。⽽Spring Boot 的⽬标是简化 
Spring 应⽤的开发过程，可以通过 starter 的⽅式快速集成 SpringMVC。
传统的 Web 项⽬通常需要⼿动配置很多东⻄，⽐如 DispatcherServlet、ViewResolver、HandlerMapping 等
等。⽽ Spring Boot 则通过⾃动装配的⽅式，帮我们省去了这些繁琐的配置。
Spring Boot 还内置了⼀个嵌⼊式的 Servlet 容器，⽐如 Tomcat，这样我们就不需要像传统的 Web 项⽬那样需要
配置 Tomcat 容器，然后导出 war 包再运⾏。只需要打包成⼀个 JAR ⽂件，就可以直接通过 java -jar 命令运
⾏。
1. Java ⾯试指南（付费）收录的滴滴同学 2 技术⼆⾯的原题：SpringBoot 和 SpringMVC 的区别
2. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：SpringBoot与SpringMVC
区别
38.Spring Boot 和 Spring 有什么区别？（补充）
 
2024 年 07 ⽉ 09 ⽇新增
从定位上来说，Spring 是⼀个完整的应⽤开发框架，提供了 IoC 容器、AOP 等各种功能模块。Spring Boot 不是
⼀个独⽴的框架，⽽是基于 Spring 框架的脚⼿架，它的⽬标是让 Spring 应⽤的开发和部署变得简单⾼效。
Spring 项⽬需要我们⼿动管理每个 jar 包的版本，经常会遇到版本冲突的问题。⽐如我们要⽤ Spring MVC，需要
引⼊ spring-webmvc、jackson-databind、hibernate-validator 等⼀堆依赖，还要确保版本兼容。Spring Boot 
通过 starter 机制解决了这个问题，只需要引⼊ spring-boot-starter-web 这⼀个依赖就可以了，它包含了所有相
关的 jar 包，⽽且版本都是测试过的，可以兼容的。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 160 / 180

---

1. Java ⾯试指南（付费）收录的⼩⽶同学 F ⾯试原题：Spring Boot 和 Spring 的区别
2. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：说⼀下Spring和Springboot之间有什么差
异？
memo：2025 年 8 ⽉ 11 ⽇修改⾄此，今天有球友在 VIP 群⾥交流说，⽤⼆哥的项⽬，轻松过⼤⼚的简历初筛，
包括⼩⽶和美团。
Spring Cloud
 
39.对 SpringCloud 了解多少？
 
Spring Cloud 其实是⼀套基于 Spring Boot 的微服务全家桶，帮我们把分布式系统⾥的基础设施做了⼀个“拿来即
⽤”的封装，⽐如服务注册与发现、配置管理、负载均衡、熔断限流、链路追踪这些。
我⾃⼰⽤得⽐较多的是 Spring Cloud Alibaba 这⼀套，PmHub 这个项⽬就是⼀个例⼦，⽐如：
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 161 / 180

---

我们使⽤ Nacos 做服务注册和配置中⼼，并且将配置信息持久化到了 MySQL 中，这样就可以统⼀管理注册
信息和配置信息，还⽀持动态刷新配置。
使⽤ Gateway 做 API ⽹关，⽀持路由转发、全局过滤器、限流等功能。
使⽤ Sentinel 做熔断、限流、降级策略，结合业务⾃定义规则⽐较⽅便。
使⽤ OpenFeign 做服务间的声明式调⽤，⽐ RestTemplate 更省代码，也更清晰可维护。
使⽤ Seata 处理分布式事务，这个在订单、⽀付、审批流场景中⽤得⽐较多。
我觉得 Spring Cloud 最⼤的价值是统⼀了技术栈和编程模型，不需要我们去⾃⼰从零实现注册中⼼、熔断器这些
基础设施。
什么是微服务？
 
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 162 / 180

---

微服务就是把⼀个⼤的、复杂的单体应⽤，拆成⼀个个围绕业务功能独⽴部署的⼩服务，每个服务维护⾃⼰的数据
和逻辑，服务之间通过轻量级的通信机制（⽐如 gRPC）来协作。
微服务的核⼼价值我认为是：业务之间的边界更清晰了，不同团队可以独⽴开发、部署、扩展某个功能，不会因为
⼀个⼩的改动就要把整套系统重新上线。
像 PmHub 这个项⽬ 就是从单体拆分成微服务的，包括启动⽹关、认证、流程、项⽬管理、代码⽣成等多个服
务。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 163 / 180

---

1. Java ⾯试指南（付费）收录的⽐亚迪同学 1 ⾯试原题：SpringCloud 了解多少？
memo：2025 年 8 ⽉ 12 ⽇修改⾄此，今天帮球友修改简历的时候，碰到⼀个球友说：感谢⼆哥对我简历的修
改，没有⼆哥绝对进不了字节。看完后真的⾮常感动，觉得⾃⼰做的事情确实有意义。
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 164 / 180

---

补充
 
40.SpringTask 了解吗？
 
SpringTask 是 Spring 框架提供的⼀个轻量级的任务调度框架，它允许我们开发者通过简单的注解来配置和管理定
时任务。
使⽤起来也⾮常⽅便，⾸先使⽤ @EnableScheduling 开启定时任务的⽀持。
然后在需要定时任务的⽅法上加上 @Scheduled 注解，⽀持 fixedRate、fixedDelay 和 cron 表达式。技术派实战
项⽬中，就使⽤过 cron 表达式在每天凌晨定时刷新⽂章的 sitemap。
@Scheduled(cron = "0 15 5 * * ?")
public void autoRefreshCache() {
    log.info("开始刷新sitemap.xml的url地址，避免出现数据不⼀致问题!");
    refreshSitemap();
    log.info("刷新完成！");
}
⾯渣逆袭Spring篇V2-让天下所有的⾯渣都能逆袭
No. 165 / 180

---

⽤SpringTask资源占⽤太⾼，有什么其他的⽅式解决？（补充）
 
2024年05⽉27⽇新增
⾸先我们需要分析⼀下 SpringTask 资源占⽤⾼的原因。
默认情况下，SpringTask 会使⽤单线程执⾏所有定时任务，如果某个任务执⾏时间⻓或者任务数量多，就会造成
阻塞。⽽且它是基于内存的，所有任务信息都保存在 JVM 中，应⽤重启后任务状态就丢失了。
那我们可以通过配置线程池来解决这个问题。
另外，就是可以将 SpringTask 迁移到其他的任务调度框架，⽐如 Quartz、XXL-JOB 等。
Quartz 功能更强⼤，⽀持集群、持久化、灵活的调度策略。还可以把任务信息持久化到数据库，⽀持集群部署，
⼀个节点挂了其他节点可以接管任务。
使⽤ XXL-JOB 是分布式场景下更彻底的解决⽅案，有独⽴的调度中⼼，任务配置和执⾏可以分离；⽀持分⽚执⾏，
⼤任务可以拆分成多个⼦任务并⾏处理。
1. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：SpringTask 了解吗？
@Configuration
@EnableScheduling
public class ScheduleConfig implements SchedulingConfigurer {
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(10));
    }
}
/**
    * 2、分⽚⼴播任务
    */
@XxlJob("shardingJobHandler")
public void shardingJobHandler() throws Exception {
    // 分⽚参数
    int shardIndex = com.xxl.job.core.context.XxlJobHelper.getShardIndex();
    int shardTotal = com.xxl.job.core.context.XxlJobHelper.getShardTotal();
    logger.info("分⽚⼴播任务开始执⾏，当前分⽚序号 = {}, 总分⽚数 = {2. Java 面试指南（付费）收录的阿里面经同学 1 闲鱼后端一面的原题：订单超时，用 springtask 资源占用太高，有什么其他的方式解决?

41.Spring Cache 了解吗？

Spring Cache 是 Spring 框架提供的一套缓存抽象，它通过提供统一的接口来支持多种缓存实现，如 Redis、Caffeine 等。

我们只需要在方法上加几个注解，Spring 就会自动处理缓存的存取，这种声明式的缓存使用方式让业务代码和缓存逻辑能够完全分离。
最常用的注解是 @Cacheable ，用来标识方法的返回值需要被缓存。
方法在第一次执行后会把结果缓存起来，后续的调用就直接从缓存中返回，不再执行方法体。
还有 @CacheEvict 注解，用于在方法执行前或执行后清除缓存。

```java
@Cacheable(value = "users", key = "#id")
public User getUserById(Long id) {
    return userDao.findById(id);
}
```

Spring Cache 是基于 AOP 实现的，通过拦截方法调用，在调用前后插入缓存逻辑。需要我们在配置中先启用缓存功能。

Spring Cache 和 Redis 有什么区别？

Spring Cache 和 Redis 的区别其实是抽象层和具体实现的区别。Spring Cache 只是提供了一套统一的接口和注解来管理缓存，本身并不提供缓存能力，而 Redis 是具体的缓存实现。
在使用层面上，Spring Cache 更简单，只需要在方法上添加注解就行，框架会帮我们自动处理。
如果用 Redis 则需要我们手动处理缓存逻辑：

```java
@CacheEvict(value = "users", key = "#id")
public void deleteUserById(Long id) {
    userDao.deleteById(id);
}

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        RedisCacheManager.Builder builder = RedisCacheManager
            .RedisCacheManagerBuilder
            .fromConnectionFactory(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration());
        return builder.build();
    }
}

@Cacheable("users")
public User getUser(Long id) {
    return userDao.findById(id);
}

public User getUser(Long id) {
    String key = "user:" + id;
    User user = (User) redisTemplate.opsForValue().get(key);
    if (user == null) {
        user = userDao.findById(id);
        redisTemplate.opsForValue().set(key, user, 30, TimeUnit.MINUTES);
    }
    return user;
}
```

在实际的项目当中，我通常会选择使用 Spring Cache 来处理一些简单的缓存业务，但对于一些复杂的业务场景，比如分布式锁、计数器、排行榜等，我会直接用 Redis。

有了 Redis 为什么还需要 Spring Cache？

虽然 Redis 非常强大，但 Spring Cache 可以简化缓存的管理。我们直接在方法上加注解就能实现缓存逻辑，减少了手动操作 Redis 的代码量。
此外，Spring Cache 还能灵活切换底层的缓存实现，比如说从 Redis 切换到 Caffeine。

说说 Spring Cache 的底层原理？

Spring Cache 的底层是通过 AOP 实现的。当我们在方法上标注了 @Cacheable 注解时，Spring 会在项目启动的时候扫描这些注解，并创建代理对象。代理对象会拦截所有的方法调用，在方法执行前后插入缓存相关的逻辑。
具体的执行流程是这样的：
当用户调用一个被缓存注解标注的方法时，实际上调用的是代理对象而不是原始对象。
代理对象中的 CacheInterceptor 拦截器会先解析方法上的缓存注解，获取缓存名称、key 生成规则、过期时间这些配置信息。然后根据注解的类型执行不同的缓存策略，比如 @Cacheable 会先去缓存中查找数据，如果找到就直接返回，不执行原方法；如果没找到，就执行原方法获取结果，然后将结果存入缓存再返回。
缓存 key 的生成是通过 KeyGenerator 组件完成的，默认情况下会根据方法的参数来生成 key。如果我们在注解中指定了 key 属性，Spring 会使用 SpEL 表达式引擎来解析这个表达式，结合方法参数、返回值等上下文信息计算出具体的 key 值。
底层的缓存存储是通过 CacheManager 和 Cache 这两个抽象接口来管理的。CacheManager 负责管理多个缓存区域，每个 Cache 实例对应一个具体的缓存区域。
不管我们使用 Redis、Caffeine 还是其他缓存技术，都需要实现这两个接口。这样 Spring Cache 就能以统一的方式操作不同的缓存实现，实现了很好的解耦。

```java
@Cacheable("users")
public User getUser(Long id) {
    return userDao.findById(id);
}
```

1. Java 面试指南（付费）收录的美团同学 9 一面面试原题：介绍一下 springcache 和 redis？Spring cache 和 redis 之间的各应用在什么场景？有了 redis 为什么还要用 springcahe？springcache 底层原理，基于什么实现的？

---

## 图表说明

> [图] 第1页：这张图是《面渣逆袭 Spring 篇》的宣传封面，主要介绍了一本关于 Spring 框架的面试辅导资料，包含 36098 字、186 张手绘图和 41 道面试题，旨在帮助 Java 开发者提升面试能力。技术知识点集中在 Spring 框架的面试核心内容上。

> [图] 第2页：有技术知识点。该图展示了IoC（控制反转）的核心概念，通过框架在运行时自动注入和创建依赖对象，实现对象创建和依赖关系管理的控制权从应用代码转移到框架。

> [图] 第3页：有技术知识点。该图展示了一个Git版本控制界面，内容涉及对`map.md`文件的修改，其中包含了Java学习资源、技术栈（如Spring Boot、MySQL、Go等）、学习路径和面试指南等技术信息，适用于编程学习者参考。

> [图] 第4页：有技术知识点。图中展示了多个与Java技术栈相关的学习资料，包括JVM、并发编程、Spring、Redis、操作系统等主题的PDF和Markdown文档，适合Java开发者进行系统性学习和面试准备。

> [图] 第5页：Logo：Spring框架的标志，代表Java生态系统中的一个开源应用开发框架。

> [图] 第5页：有技术知识点。该图展示了Spring框架中Bean的生命周期流程，包括实例化、属性赋值和初始化三个阶段，并关联了AbstractApplicationContext和AbstractBeanFactory中的关键方法调用关系。

> [图] 第6页：有技术知识点。该图展示了一个Java类 `LoginServiceImpl` 中的 `autoRegisterWxUserInfo` 方法，使用了 `@Transactional` 注解实现事务管理，并通过链式调用构建请求对象，用于微信用户自动注册或获取用户信息。

> [图] 第6页：有技术知识点。该图展示了一个Java项目的代码结构和`LoginServiceImpl.java`文件的部分实现，涉及Spring框架的依赖注入（@Autowired）、服务层设计以及日志记录（@Slf4j），体现了典型的后端服务开发模式。

> [图] 第7页：有技术知识点。该图展示了Spring框架的核心特性，包括IOC和DI支持、AOP编程支持、声明式事务支持、快捷测试支持、快速集成功能以及复杂API模板封装。

> [图] 第8页：有技术知识点。该图展示了一个Java Spring MVC控制器类`ArticleViewController`中的`edit`方法，涉及权限控制、请求参数处理、对象赋值和视图跳转等Web开发常见技术点。

> [图] 第9页：有技术知识点。该图展示了Maven项目中的`pom.xml`文件，包含了对Elasticsearch相关依赖的配置，如`elasticsearch`、`elasticsearch_rest_client`和`elasticsearch_rest_high_level_client`等，版本号为6.8.2，同时引入了Hutool工具库，体现了Java项目中使用Maven进行依赖管理的技术实践。

> [图] 第10页：有技术知识点。该图展示了Java中使用`@MdcDot`注解将方法参数动态注入到MDC（Mapped Diagnostic Context）日志上下文中的实现方式，用于日志追踪和业务操作定位。

> [图] 第10页：有技术知识点。该图展示了Java代码中使用Guava Cache实现分类数据缓存的示例，包含`@PostConstruct`初始化缓存、`CacheBuilder`配置以及通过`load`方法加载`CategoryDTO`对象的逻辑。

> [图] 第11页：有技术知识点。该图展示了Spring框架核心模块的源码解析内容，包括IOC、AOP、事务、Bean生命周期和循环依赖等关键技术点及其相关流程与源码分析。

> [图] 第12页：这张图中没有直接展示技术知识点，但包含一个百度网盘链接，指向《Spring 源码解析手册》的资源，属于技术资料分享。  
一句话描述：该图展示了用户询问《Spring 源码解析手册》是否已上传至知识星球，并提供了百度网盘下载链接。

> [图] 第13页：有技术知识点。该图展示了Spring框架运行时的模块结构，包括核心容器、数据访问/集成、Web、AOP、方面、仪器化、消息传递和测试等主要组件。

> [图] 第14页：有技术知识点。该图展示了一个Java项目中用于数据库初始化的代码片段，涉及Spring Boot、JdbcTemplate、Liquibase和数据库版本控制逻辑。

> [图] 第15页：有技术知识点。这张图展示了一个Java项目的代码结构和`ArticleMapper.java`接口中的方法定义，涉及MyBatis的Mapper接口使用、参数注解（@Param）、分页查询以及根据类别、标签、用户ID等条件查询文章的业务逻辑。

> [图] 第16页：有技术知识点。该图展示了一个Java项目的文件结构和相关技术栈，包括Spring、Spring Boot、MyBatis、Elasticsearch、Redis等主流技术及其官网链接，用于说明项目所使用的技术框架和工具。

> [图] 第17页：有技术知识点。这张图展示了Spring框架中常用的注解分类，包括Web、容器、AOP和事务四大类注解及其具体用途。

> [图] 第18页：有技术知识点。该图展示了Java中一个服务类的实现，使用了Spring框架的依赖注入（@Autowired和@Resource注解）以及事务管理（TransactionTemplate），体现了典型的Spring应用开发模式。

> [图] 第19页：有技术知识点。该图展示了一个基于Spring Boot的Java后端控制器类 `AdminLoginController`，使用了 `@RestController`、`@RequestMapping`、`@Autowired` 等注解实现后台登录功能，涉及RESTful接口设计与依赖注入等核心开发技术。

> [图] 第19页：有技术知识点。该图展示了在Java项目中使用Spring框架的`@Value`注解注入配置属性，以及通过`@Autowired`注入依赖服务的代码实现，体现了Spring中的依赖注入和配置管理机制。

> [图] 第20页：有技术知识点。该图展示了一个使用Spring AOP实现异步执行超时控制的Java切面类，通过@Aspect和@Around注解拦截带有@asyncExecute注解的方法，并利用AsyncUtil.callWithTimeLimit进行超时处理。

> [图] 第21页：有技术知识点。该图展示了Java代码中使用`@PostConstruct`注解进行初始化方法的配置，涉及Spring框架的生命周期管理、缓存机制（LoadingCache）以及异步处理等技术细节。

> [图] 第21页：有技术知识点。该图展示了Spring Boot中使用`@ConditionalOnProperty`注解进行条件化配置的代码，用于在满足特定配置属性时加载数据源配置类。

> [图] 第22页：这张图有技术知识点。  
涉及的技术课程包括数据结构、JavaEE开发进阶、计算机网络技术、软件测试、数据库（MySQL、Oracle）等，体现了计算机相关专业的核心技能要求。

> [图] 第23页：有技术知识点。该图展示了Spring框架中使用到的多种设计模式，包括工厂模式、代理模式、单例模式、模板模式、观察者模式、适配器模式和策略模式。

> [图] 第24页：有技术知识点。该图展示了Spring框架中`@Scope`注解的定义，用于指定组件或Bean的作用域，支持单例、原型、请求、会话等作用域配置。

> [图] 第25页：有技术知识点。该图展示了一段Java代码，用于判断是否需要初始化数据库，涉及JdbcTemplate、Liquibase、数据库表存在性检查和MD5校验等技术细节。

> [图] 第26页：有技术知识点。该图展示了一个Spring Boot应用中配置刷新事件监听器的实现，通过`@Service`和`ApplicationListener`注解监听`ConfigRefreshEvent`事件，并在配置变更时调用`reloadConfig()`方法更新配置。

> [图] 第27页：有技术知识点。这张图展示了Spring框架中`DefaultSingletonBeanRegistry`类的源码，主要涉及单例bean的缓存机制，包括`singletonObjects`、`singletonFactories`、`earlySingletonObjects`等关键集合的定义，用于管理Spring容器中的单例bean实例和创建过程。

> [图] 第28页：有技术知识点。该图展示了Spring框架中`DefaultSingletonBeanRegistry`类的`registerSingleton`方法，用于将单例对象注册到容器的单例缓存中，涉及线程安全、对象注册与异常处理等核心机制。

> [图] 第30页：有技术知识点。该图展示了从直接由HTTP服务器调用业务类（错误方式）到通过Tomcat的Servlet容器加载并处理请求（正确方式）的架构演变，体现了Java Web应用中Servlet容器的作用和工作流程。

> [图] 第31页：有技术知识点。该图展示了“控制反转（Inversion of Control, IoC）”的设计模式，其中框架在运行时负责创建并注入依赖对象，从而将控制权从应用程序代码转移到框架中。

> [图] 第33页：有技术知识点。这张图通过对比形象地说明了引入IOC（控制反转，Inversion of Control）前后的变化：引入IOC之前，需要自己手动完成所有依赖的创建和管理（如买菜、烧火、煮饭），而引入IOC之后，依赖由容器自动注入，开发者只需“张口吃饭”，即专注于业务逻辑，降低了耦合度，提高了开发效率。

> [图] 第33页：这张图包含技术知识点：依赖注入是一种设计模式，通过将对象所需的依赖项作为参数传递给该对象，实现松耦合和可测试性。

> [图] 第34页：有技术知识点。该图展示了Java项目中使用Spring框架的依赖注入（@Autowired、@Resource）和Service层实现类的代码结构，涉及组件注入、事务管理及服务间协作等Spring核心概念。

> [图] 第35页：有技术知识点。该图展示了一个Java项目中的Spring工具类`SpringUtil.java`，实现了`ApplicationContextAware`和`EnvironmentAware`接口，用于在Spring容器启动时自动注入`ApplicationContext`和`Environment`，从而实现全局获取Spring Bean的功能。

> [图] 第37页：这张图没有直接的技术知识点，主要是关于实习选择的建议和讨论。

> [图] 第38页：有技术知识点。该图展示了Spring框架中`DefaultListableBeanFactory`类的源码，重点是`resolveBean`方法的实现，涉及Spring IOC容器中Bean的解析与依赖注入机制。

> [图] 第38页：有技术知识点。该图描述了Spring容器中Bean的生命周期流程，包括Bean定义注册、实例创建、单例模式管理及通过HashMap进行实例缓存的过程。

> [图] 第39页：有技术知识点。该图展示了Spring框架中Bean初始化的完整流程，包括加载Bean定义、后置处理、实例化、依赖注入、调用Setter方法以及通过BeanPostProcessor进行初始化等步骤。

> [图] 第40页：有技术知识点。该图详细描述了Spring框架中`@Autowired`和`@Resource`注解的解析与依赖注入流程，包括注解解析、候选bean查找、排序规则及注入逻辑。

> [图] 第41页：这张图展示了工厂、库房和订单之间的物流与生产流程，涉及生产、入库、调库存等环节，属于供应链管理中的基本业务流程模型。

> [图] 第47页：有技术知识点。图中用人体形象比喻Spring框架中的ApplicationContext和BeanFactory，其中心形代表核心功能，表示ApplicationContext是BeanFactory的扩展，提供了更丰富的功能。

> [图] 第48页：有技术知识点。该图展示了Spring框架中BeanFactory相关接口和类的继承与实现关系，描述了IoC容器的核心组件结构。

> [图] 第49页：有技术知识点。该图展示了Spring框架中ApplicationContext的继承和接口实现关系，描述了其核心组件如BeanFactory、ResourceLoader、MessageSource等的层次结构和功能。

> [图] 第50页：有技术知识点。这张图展示了一个Java类 `DynamicConfigContainer` 的代码，实现了 `EnvironmentAware` 和 `ApplicationContextAware` 接口，用于动态配置管理，包含配置缓存和变更回调机制。

> [图] 第51页：有技术知识点。该图展示了Spring框架中Bean的创建和依赖注入流程，包括实例化、属性注入、Aware接口回调以及BeanPostProcessor的执行顺序等核心机制。

> [图] 第51页：有技术知识点。该图展示了Spring框架中Bean的加载、解析、实例化及管理流程，涉及ResourceLoader、BeanDefinitionReader、ApplicationContext、BeanFactory等核心组件及其协作关系。

> [图] 第53页：这张图中没有明显的技术知识点，主要内容为求职者在社交平台上的求助帖，讨论工作offer的选择问题，涉及公司背景、薪资待遇、技术栈等信息，但未包含具体技术原理或知识点。  
**用一句话描述：** 无明确技术知识点，内容为求职者对两个工作offer的对比分析与选择咨询。

> [图] 第54页：有技术知识点。该图展示了Spring框架中Bean初始化的完整流程，包括加载Bean定义、实例化、依赖注入、调用setter方法以及通过Bean后处理器（BPP）和初始化器完成最终初始化，直至Bean可用。

> [图] 第54页：有技术知识点。该图展示了一个Spring Boot控制器类`ArticleViewController`，使用了`@Controller`和`@RequestMapping`注解，并通过`@Autowired`注入了多个服务（如文章、分类、标签和用户服务），体现了典型的Spring MVC架构模式。

> [图] 第55页：有技术知识点。该图展示了Spring框架中三种常见的Bean定义方式：基于注解（@Component）、基于XML配置和基于Java配置。

> [图] 第56页：有技术知识点。该图展示了一个Java类 `UserPwdEncoder`，用于密码加密和校验，使用了Spring框架的注解（如@Component、@Value）进行依赖注入，并通过盐值（salt）增强密码安全性，体现了常见的密码安全处理实践。

> [图] 第57页：有技术知识点。该图展示了一个Java项目中Elasticsearch的配置类，使用Spring Boot的`@Configuration`和`@Bean`注解创建Elasticsearch客户端，涉及Elasticsearch连接配置、认证设置及REST客户端构建等技术细节。

> [图] 第58页：有技术知识点。该图描述了Spring框架中Bean的生命周期流程，包括实例化、属性设置、初始化、使用和销毁等阶段的关键步骤。

> [图] 第58页：有技术知识点。该图通过“人的一生”类比说明了Java Bean的生命周期，包括实例化、属性赋值、初始化、使用中和销毁五个阶段。

> [图] 第59页：有技术知识点。该图展示了Spring框架中`AbstractAutowireCapableBeanFactory`类的`doCreateBean`方法的源码，涉及Spring Bean的创建流程，包括实例化、缓存处理和后置处理器的应用。

> [图] 第60页：有技术知识点。该图展示了Spring框架中Bean的创建流程，包括从ApplicationContext的refresh方法开始，经过BeanFactory的初始化和getBean调用，最终完成Bean的实例化、属性赋值和初始化三个阶段。

> [图] 第62页：有技术知识点。该图展示了Spring框架中`AbstractApplicationContext`类的`close()`和`doClose()`方法的源码，涉及上下文关闭机制、线程安全、JVM关闭钩子以及资源清理等核心概念。

> [图] 第63页：有技术知识点。该图展示了一个Spring框架中的工具类`SpringUtil.java`，实现了`ApplicationContextAware`和`EnvironmentAware`接口，用于在Spring容器启动时自动注入`ApplicationContext`和`Environment`对象，以便后续通过静态方法获取上下文和环境配置。

> [图] 第66页：有技术知识点。图中展示了Spring框架中`@Autowired`注解的使用说明，包括其在构造函数、字段和方法上的应用规则，以及依赖注入的相关语义。

> [图] 第67页：有技术知识点。这是一段Java代码，展示了使用Spring框架的@Service注解定义一个服务类，并通过构造函数注入依赖的DAO组件，体现了依赖注入（DI）和面向接口编程的设计模式。

> [图] 第67页：有技术知识点。这是一段Java代码，展示了使用`@Resource`注解进行依赖注入的示例，涉及DAO和Service组件的声明与注入。

> [图] 第69页：是的，这张图有技术知识点。它详细描述了Spring框架中Bean的创建和初始化流程，包括单例检查、Bean实例化、属性填充、初始化方法调用以及后置处理器的执行等关键步骤。

> [图] 第70页：有技术知识点。该图展示了Spring框架中自动装配的四种方式：byType、byName、constructor和autodetect。

> [图] 第71页：有技术知识点。该图展示了Spring Boot启动过程中自动配置的流程，包括`@SpringBootApplication`如何通过`@ComponentScan`、`@EnableAutoConfiguration`和`@SpringBootConfiguration`注解触发自动配置机制，并最终加载`spring.factories`文件中的配置类到容器中。

> [图] 第73页：有技术知识点。该图展示了Spring框架中Bean的五种作用域：singleton、prototype、request、session和global-session，用于说明不同作用域下Bean的生命周期和实例化方式。

> [图] 第75页：有技术知识点。该图展示了多线程环境下对共享变量进行自增操作时可能出现的竞态条件问题，正确结果应为 var=1，但实际可能因线程执行顺序不同导致结果不一致。

> [图] 第79页：是的，这张图有技术知识点。它展示了Spring框架中组件（@Component）之间的依赖注入（@Autowired）关系，其中A和B之间存在循环依赖，C存在自依赖，反映了Spring容器在处理依赖注入时可能遇到的循环依赖问题。

> [图] 第80页：有技术知识点。该图展示了Spring框架中三级缓存机制，用于解决单例Bean的循环依赖问题：一级缓存存储完全初始化的Bean，二级缓存存储实例化但未初始化的Bean，三级缓存存储Bean工厂以支持代理对象的创建。

> [图] 第80页：有技术知识点。该图展示了Spring框架中单例对象的三级缓存机制，用于解决循环依赖问题：一级缓存存储完全初始化的对象，二级缓存存储早期暴露的单例对象，三级缓存存储对象工厂，用于创建早期引用。

> [图] 第80页：有技术知识点。该图表示A和B之间存在相互依赖关系，常见于软件系统设计中的模块依赖分析，提示可能存在循环依赖问题，需优化架构以避免。

> [图] 第81页：有技术知识点。该图展示了Spring框架中单例对象的三级缓存机制，用于解决循环依赖问题：一级缓存存储完全初始化的单例对象，二级缓存存储早期暴露的单例对象（未初始化），三级缓存存储单例工厂对象。

> [图] 第82页：有技术知识点。该图展示了Spring框架中单例对象的三级缓存机制，用于解决循环依赖问题，分别是一级缓存（已初始化完成的单例对象）、二级缓存（早期暴露的单例对象）和三级缓存（单例工厂对象）。

> [图] 第83页：有技术知识点。该图展示了在Spring框架中不同依赖注入方式对循环依赖的支持情况，指出构造器注入不支持循环依赖，而setter注入和属性自动注入可以支持。

> [图] 第85页：这张图展示了Spring应用启动失败的错误信息，核心技术知识点是：**Spring容器中存在Bean的循环依赖问题，导致应用无法启动**。

> [图] 第87页：有技术知识点。该图描述了Spring框架中二级缓存（earlySingletonObjects）与代理对象之间的关系，展示了在单例对象创建过程中，代理对象如何覆盖原始实例对象以实现循环依赖的解决机制。

> [图] 第89页：有技术知识点。该图详细描述了Spring框架中循环依赖的三种类型（直接依赖、间接依赖、自我依赖）以及通过三级缓存（一级、二级、三级缓存）解决循环依赖的机制。

> [图] 第90页：有技术知识点。该图展示了Spring框架中三级缓存的实现机制，用于解决单例Bean在创建过程中循环依赖的问题，涉及singletonFactories、二级缓存和三级缓存的协同工作流程。

> [图] 第92页：有技术知识点。内容涉及秋招准备中的技术学习计划，包括热门算法题、大厂笔试题、大模型RAG与MCP技术、Redis分布式锁以及科研论文搜索等。

> [图] 第92页：有技术知识点。该图展示了系统架构从单一业务逻辑模块向分层解耦的演进过程，将业务逻辑、性能监控和事务处理等职责分离，体现了软件设计中的关注点分离原则。

> [图] 第94页：有技术知识点。该图对比了JDK代理和CGLib代理的实现方式：JDK代理基于接口，通过实现接口创建代理；CGLib代理基于子类，通过继承目标类创建代理。

> [图] 第95页：有技术知识点。这张图展示了Spring AOP（面向切面编程）中的核心术语及其相互关系，包括Aspect（切面）、Join Point（连接点）、Advice（通知）、Pointcut（切入点）、Target Object（目标对象）、Weaving（织入）、Introduction（引介）和Interceptor（拦截器）。

> [图] 第97页：有技术知识点。该图展示了AOP（面向切面编程）中的织入（Weaving）方式，包括编译时、类加载时和运行时（Spring的方式），并用代理模式说明了调用者通过代理访问目标对象的过程。

> [图] 第101页：有技术知识点。该图展示了AspectJ的相关信息，包括其作为Java语言的面向切面编程扩展、支持的功能（如错误检查、同步、性能优化等）以及相关资源链接，体现了面向切面编程（AOP）的技术概念。

> [图] 第102页：有技术知识点。该图展示了Spring AOP中不同通知（Advice）的执行顺序，包括@Before、@Around、@After、@AfterReturning和@AfterThrowing的调用流程。

> [图] 第104页：有技术知识点。该图展示了一个基于Spring AOP的Java切面类`DsAspect`，用于在方法执行前后动态切换数据源，通过`@Around`增强实现数据源路由控制。

> [图] 第106页：有技术知识点。该图展示了Spring框架中`BeanPostProcessor`接口的源码，说明了在bean初始化前后进行自定义处理的机制。

> [图] 第107页：有技术知识点。该图展示了一个为期3个月的Java面试冲刺学习计划，涵盖Java基础、集合框架、并发编程、JVM、MySQL等内容，并标注了每天的学习时长、重点知识模块和重要程度，适用于准备Java技术面试的学习者。

> [图] 第108页：有技术知识点。图中展示了Java代码中使用Spring框架的`@Transactional`注解来管理数据库事务，确保支付操作的原子性和一致性。

> [图] 第109页：有技术知识点。该图展示了如何通过AOP（面向切面编程）实现切面日志，结合Spring框架中的AOP机制，讲解了MdcAspect用于方法执行耗时统计的实践应用，并附有日志输出示例。

> [图] 第110页：有技术知识点。该图展示了一个Spring Boot控制器中的REST接口方法，使用了`@RequestMapping`、`@RequestParam`等注解处理HTTP请求，并结合自定义注解`@MdcDot`进行业务逻辑标记，涉及分页参数处理、服务调用和模板渲染。

> [图] 第110页：有技术知识点。该图展示了一个基于Spring AOP的Java类`MdcAspect`，用于实现日志记录和MDC（Mapped Diagnostic Context）上下文管理，通过`@Around`切面拦截带有特定注解的方法执行，并记录执行耗时与清理MDC上下文。

> [图] 第111页：这张图中的聊天内容提到了“设计模式”这一技术知识点，表明在面试或技术讨论中涉及了设计模式相关的问题。

> [图] 第112页：有技术知识点。该图对比了Spring AOP和AspectJ在实现方式、织入时机、支持的编织范围、切入点类型、性能、学习难度及执行机制等方面的差异，属于面向切面编程（AOP）领域的核心技术对比知识。

> [图] 第113页：有技术知识点。该图对比了JDK动态代理（基于接口）和CGLib代理（基于子类）在Spring框架中的实现方式，展示了两种代理模式的结构差异。

> [图] 第113页：有技术知识点。该图展示了代理模式（Proxy Pattern）的实现机制，其中通过 ProxyFactory 创建一个代理对象（Proxy），该代理实现了指定接口，并封装了目标对象（Target），在调用目标方法时可通过处理器（handler）进行拦截和增强处理。

> [图] 第114页：有技术知识点。该图展示了代理模式（Proxy Pattern）的实现过程，其中通过Enhancer生成一个继承原Class的Proxy，Proxy内部包含Handler和Target，实现了对目标对象的增强和拦截。

> [图] 第116页：有技术知识点。该图展示了Spring框架中AOP（面向切面编程）自动配置的代码片段，重点说明了根据`proxy-target-class`属性值选择使用JDK动态代理或Cglib代理的条件配置逻辑。

> [图] 第116页：这张图没有直接的技术知识点，它是一幅带有幽默风格的网络梗图，通过三个角色（动画猫、客服人员、表情包猫）之间的互动，表现了一种“客服”场景的调侃。因此，**无技术知识点**。

> [图] 第117页：是的，这张图有技术知识点。它展示了代理模式（Proxy Pattern）的应用，其中 `ProxyFactory` 为 `Solver` 创建代理实例，客户端通过代理访问目标对象，实现对 `ISolver` 接口的间接调用。

> [图] 第118页：是的，这张图包含技术知识点。它展示了代理模式（Proxy Pattern）的基本结构，其中 `ProxyFactory` 负责创建代理实例，客户端通过代理访问 `Solver` 对象，实现对目标对象的间接控制或增强功能。

> [图] 第121页：有技术知识点。内容涉及简历撰写、实习经历描述、Spring、RocketMQ、计算机网络、算法学习（hot100）、项目经验（技术派+pmhub）、秋招准备及笔试应对策略等技术与求职相关话题。

> [图] 第121页：有技术知识点。该图展示了在Spring框架中使用JPA事务管理的调用流程，包括控制器、事务拦截器、事务管理器、实体管理器和仓库之间的交互，体现了声明式事务管理的执行机制。

> [图] 第123页：有技术知识点。  
图中提到了Redis、MySQL、JVM等技术，涉及数据库和Java虚拟机相关知识，属于计算机后端开发领域的核心技术内容。

> [图] 第124页：有技术知识点。该图展示了Spring框架中事务管理的核心类和接口，包括`TransactionAspectSupport`类及其事务处理方法，以及`TransactionInterceptor`和`MethodInterceptor`接口，体现了AOP（面向切面编程）在事务控制中的应用。

> [图] 第124页：有技术知识点。该图展示了一个Java类 `ArticlePayServiceImpl` 中的事务方法 `toPay`，使用了 `@Transactional` 注解来确保数据库操作的原子性，并在记录不存在时创建新的支付记录，体现了Spring框架中的事务管理和DAO层交互逻辑。

> [图] 第126页：这张图中没有明显的技术知识点，主要是一封关于简历修改的邮件内容，涉及实习经历、技术栈（如Python、Java）和项目经验的讨论。  
**用一句话描述：** 无明确技术知识点，内容为求职者向他人咨询简历优化建议，涉及后端开发、技术栈选择和项目经验描述问题。

> [图] 第127页：有技术知识点。该图展示了一个Java Spring Boot项目中的事务管理示例，重点说明了在同一个类中调用`@Transactional`方法时，直接调用不会触发事务，而通过`@Lazy`注入的`self`代理对象调用则可以正常开启事务。

> [图] 第128页：是的，这张图有技术知识点。它展示了Java中异常（Exception）和错误（Error）的继承层次结构，从Throwable根类出发，分为Error和Exception两大分支，并列举了常见的子类如RuntimeException、IOException等，有助于理解Java异常处理机制。

> [图] 第129页：有技术知识点。该图展示了Spring框架中`TransactionDefinition`接口的源码，定义了事务传播行为、隔离级别等核心事务属性，是理解Spring事务管理机制的关键内容。

> [图] 第131页：有技术知识点。这张图展示了Spring框架中7种事务传播机制的含义和使用场景，是Java开发中处理数据库事务的重要概念。

> [图] 第132页：有技术知识点。该图展示了一段Java代码，涉及事务处理、数据库操作、支付状态判断和异常处理等后端开发技术，主要用于实现文章付费阅读的支付记录创建与状态管理逻辑。

> [图] 第134页：有技术知识点。该代码展示了Spring框架中事务管理的一个常见问题：直接调用本类的事务方法不会触发事务，但通过`@Lazy`注入的`self`代理对象调用则可以正常开启事务。

> [图] 第135页：有技术知识点。该图展示了MySQL数据库中一个名为`test_entity`的表结构及其数据，包含`id`（bigint类型）和`name`（varchar(255)类型）两个字段，显示了两条记录，名称均为“test-protected”，体现了数据库表设计与数据存储的基本概念。

> [图] 第135页：有技术知识点。邮件中提到了“RAG项目”和“业务透视项目”，涉及自然语言处理领域中的检索增强生成（Retrieval-Augmented Generation, RAG）技术，属于当前AI与大模型应用中的关键技术方向之一。

> [图] 第136页：有技术知识点。该图展示了Spring MVC框架的核心组件及其工作流程，包括DispatcherServlet、Handler、HandlerMapping、HandlerInterceptor、HandlerExecutionChain、HandlerAdapter、ModelAndView和ViewResolver等模块的功能与相互关系。

> [图] 第137页：有技术知识点。该图展示了一个Java Spring Boot项目的代码结构和一个REST控制器类 `ArticleListRestController`，其中包含使用Spring注解（如 `@RestController`、`@GetMapping`、`@Autowired`）实现的API接口，用于根据分类ID分页获取文章列表，体现了前后端分离架构中的后端开发实践。

> [图] 第138页：有技术知识点。该图展示了Spring Boot中使用`@GetMapping`、`@PathVariable`、`@RequestParam`等注解实现RESTful接口和页面跳转的代码，涉及DTO封装、Markdown转HTML、权限控制（`@Permission`）及视图渲染逻辑。

> [图] 第138页：有技术知识点。该图展示了一个Spring Boot全局异常处理器的代码片段，使用`@RestControllerAdvice`和`@ExceptionHandler`注解来统一处理特定异常（ForumAdviceException），返回标准化的错误响应。

> [图] 第139页：有技术知识点。该图展示了Spring MVC框架的请求处理流程，包括DispatcherServlet、HandlerMapping、HandlerAdapter、ViewResolver等核心组件的协作过程。

> [图] 第139页：有技术知识点。该图展示了一个用 Go 语言从零实现 Redis 部分功能的开源项目，包含代码示例和项目文档，涉及 Redis 命令处理、集群模式等核心概念。

> [图] 第140页：有技术知识点。该图展示了Spring MVC框架的请求处理流程，包括DispatcherServlet、HandlerMapping、HandlerAdapter、ViewResolver等核心组件的交互过程。

> [图] 第140页：有技术知识点。该图描述了Spring MVC框架中请求处理的完整流程，包括DispatcherServlet的核心作用、处理器映射、适配器执行、拦截器机制以及视图解析等关键组件和步骤。

> [图] 第141页：有技术知识点。该代码展示了Spring MVC中一个带有权限控制的文章编辑页控制器方法，使用了注解如@Permission、@GetMapping和@RequestParam，并通过model.addAttribute传递数据到视图。

> [图] 第141页：有技术知识点。该图展示了Spring框架中`RequestMappingHandlerAdapter`类的部分源码，涉及注解处理、方法过滤器（如`@InitBinder`和`@ModelAttribute`）以及参数解析器的配置，体现了Spring MVC请求处理机制的核心实现。

> [图] 第144页：有技术知识点。该图展示了Spring Boot项目中配置Jackson序列化方式的代码，用于解决长整型数据在JSON返回时精度丢失的问题，通过注册`BigIntToString`模块将Long类型转换为String以避免前端JavaScript精度问题。

> [图] 第144页：有技术知识点。该图展示了Spring MVC框架中请求处理的完整流程，包括DispatcherServlet接收请求、HandlerMapping查找处理器、HandlerAdapter执行控制器方法、返回值处理及响应输出等关键步骤。

> [图] 第148页：有技术知识点。该图展示了Spring Boot中的`@EnableAutoConfiguration`注解的源码，用于启用自动配置功能，是Spring Boot核心机制之一。

> [图] 第148页：有技术知识点。该图展示了Spring Boot项目的`spring.factories`配置文件，用于自动配置`ForumCoreAutoConfig`类，体现了Spring Boot的自动配置机制。

> [图] 第149页：有技术知识点。该图展示了SpringBoot自动配置的原理，包括通过`@SpringBootApplication`注解触发`@EnableAutoConfiguration`，进而利用`ImportSelector`和`AutoConfigurationImportSelector`读取`META-INF/spring.factories`文件加载自动配置类的过程。

> [图] 第149页：有技术知识点。该图展示了一个Spring Boot项目中配置RabbitMQ自动初始化的Java类，使用了`@Configuration`、`@ConditionalOnProperty`和`ApplicationRunner`等注解来实现基于属性条件的RabbitMQ服务启动与消息消费处理。

> [图] 第153页：有技术知识点。这是一张Spring Boot项目在IDE（如IntelliJ IDEA）中的开发界面截图，展示了Java代码、项目结构、控制台日志输出以及Spring Boot应用的启动过程，涉及Spring Boot基础配置、注解使用（如`@SpringBootApplication`、`@RestController`）、依赖注入和嵌入式Tomcat服务器启动等核心技术内容。

> [图] 第154页：有技术知识点。该图显示了一个本地服务器地址（localhost:8081/hello），表明可能是在演示一个基于Java的Web应用或Spring Boot项目，通过浏览器访问本地运行的服务接口。

> [图] 第156页：有技术知识点。该图详细描述了SpringApplication启动流程，包括实例化、初始化、上下文创建、刷新及运行器执行等关键步骤，涵盖了Spring Boot应用启动的核心机制。

> [图] 第157页：有技术知识点。这是一段Spring Boot应用程序的主启动类代码，使用了`@SpringBootApplication`注解来开启Spring Boot的自动配置功能，并通过`SpringApplication.run()`方法启动应用。

> [图] 第159页：有技术知识点。这是一张展示Spring Boot应用主类配置的代码截图，包含@EnableAsync、@EnableScheduling、@SpringBootApplication等注解，以及WebMvcConfigurer接口的实现，用于配置拦截器和异常处理器。

> [图] 第162页：有技术知识点。该图展示了一个基于微服务架构的分布式系统，包含客户端接入、负载均衡、API网关、服务注册与配置中心（Nacos）、容器化部署（Docker）、服务治理（Dubbo、Sentinel）、认证授权（OAuth2）、数据存储（MySQL、Redis、TiDB、ElasticSearch）、日志分析（ELK）、监控告警（Prometheus、Grafana、Zabbix、SkyWalking）以及消息中间件（RocketMQ）等核心技术组件和架构设计。

> [图] 第163页：这张图包含丰富的技术知识点，用一句话描述：**该图是Spring Cloud微服务架构的技术全景图，系统性地展示了服务注册与发现、客户端负载均衡、声明式服务调用、服务容错保护、API网关、分布式配置中心、消息总线和认证与授权等核心组件及其工作原理。**

> [图] 第164页：有技术知识点。该图展示了PmHub系统的完整技术架构，涵盖前端展示、网关控制、服务应用、基础服务、存储技术、支撑服务、运行资源及C/CD流程，涉及SpringCloud、MySQL、Redis、Docker、Jenkins等主流技术栈。

> [图] 第165页：有技术知识点。该图展示了一个基于Spring Boot的Java项目结构和主启动类`QuickForumApplication`，其中使用了多个Spring注解（如`@SpringBootApplication`、`@EnableCaching`等），表明这是一个配置了异步处理、定时任务、缓存支持和Web MVC功能的Spring Boot应用。

> [图] 第167页：有技术知识点。内容涉及RAG（检索增强生成）项目、LangChain4j和Spring AI等AI开发框架的对比与选择问题，属于人工智能与后端开发领域的技术讨论。

> [图] 第168页：有技术知识点。该图展示了一个使用Spring框架的UserService类，通过@Cacheable、@CachePut和@CacheEvict注解实现用户信息的缓存管理，包括查询、更新和删除缓存的操作。

> [图] 第170页：有技术知识点。该图展示了Spring Boot中缓存机制的自动配置流程，特别是Caffeine缓存集成的工作原理，包括依赖关系、配置类、条件判断以及核心组件之间的交互。

> [图] 第172页：有技术知识点。该图展示了多本关于Java技术栈的面试辅导资料，涵盖Spring、Java基础、集合框架、JVM、Redis、MySQL和并发编程等主题，适合准备技术面试的开发者学习使用。

> [图] 第173页：这张图包含技术知识点，主要涉及Java开发相关的面试准备资料，如Redis、MySQL、JVM、Spring、计算机网络、操作系统等核心技术内容。

> [图] 第175页：这张图中提到了“MQ八股”和“面渣”，结合上下文，可以推测其涉及的是消息队列（MQ）相关的面试知识点。因此，技术知识点是：**消息队列（MQ）的常见面试题总结与应对策略**。

> [图] 第176页：这张图没有直接展示技术知识点，主要是关于《面渣》系列书籍2.0版本的讨论，涉及MySQL和Redis相关内容的更新期待。

> [图] 第178页：有技术知识点。图中展示了多个与Java技术相关的学习资料，包括JVM、Spring、MyBatis、Redis、并发编程等主题的PDF和Markdown文档，适合Java开发者进行系统性学习和面试准备。

> [图] 第179页：有技术知识点。该图展示了Spring框架的核心模块及其源码解析内容，包括Spring循环依赖、Bean生命周期、Spring AOP、Spring事务和Spring IOC等关键技术点的结构与关联。