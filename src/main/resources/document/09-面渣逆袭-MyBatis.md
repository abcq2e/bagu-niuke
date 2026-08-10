# 面渣逆袭 —— MyBatis

> 来源：面渣逆袭 MyBatis.pdf

基础

1. 说说什么是MyBatis?

Mybatis是一个半ORM（对象关系映射）框架，它内部封装了JDBC，开发时只需要关注SQL语句本身，不需要花费精力去处理加载驱动、创建连接、创建statement等繁杂的过程。程序员直接编写原生sql，可以严格控制sql执行性能，灵活度高。MyBatis可以使用XML或注解来配置和映射原生信息，将POJO映射成数据库中的记录，避免了几乎所有的JDBC代码和手动设置参数以及获取结果集。

缺点：
SQL语句的编写工作量较大，尤其当字段多、关联表多时，对开发人员编写SQL语句的功底有一定要求。
SQL语句依赖于数据库，导致数据库移植性差，不能随意更换数据库。

ORM是什么?

ORM（Object Relational Mapping），对象关系映射，是一种为了解决关系型数据库数据与简单Java对象（POJO）的映射关系的技术。简单来说，ORM是通过使用描述对象和数据库之间映射的元数据，将程序中的对象自动持久化到关系型数据库中。

为什么说Mybatis是半自动ORM映射工具？它与全自动的区别在哪里？

Hibernate属于全自动ORM映射工具，使用Hibernate查询关联对象或者关联集合对象时，可以根据对象关系模型直接获取，所以它是全自动的。而MyBatis在查询关联对象或关联集合对象时，需要手动编写SQL来完成，所以，被称之为半自动ORM映射工具。

JDBC编程有哪些不足之处，MyBatis是如何解决的？

1. 数据连接创建、释放频繁造成系统资源浪费从而影响系统性能，在mybatis-config.xml中配置数据链接池，使用连接池统一管理数据库连接。
2. sql语句写在代码中造成代码不易维护，将sql语句配置在XXXXmapper.xml文件中与java代码分离。
3. 向sql语句传参数麻烦，因为sql语句的where条件不一定，可能多也可能少，占位符需要和参数一一对应。MyBatis自动将java对象映射至sql语句。
4. 对结果集解析麻烦，sql变化导致解析代码变化，且解析前需要遍历，如果能将数据库记录封装成pojo对象解析比较方便。MyBatis自动将sql执行结果映射至java对象。

2. Hibernate和MyBatis有什么区别？

相同点：都是对jdbc的封装，都是应用于持久层的框架。

不同点：
1）映射关系
MyBatis是一个半自动映射的框架，配置Java对象与sql语句执行结果的对应关系，多表关联关系配置简单。
Hibernate是一个全表映射的框架，配置Java对象与数据库表的对应关系，多表关联关系配置复杂。
2）SQL优化和移植性
Hibernate对SQL语句封装，提供了日志、缓存、级联（级联比MyBatis强大）等特性，此外还提供HQL（Hibernate Query Language）操作数据库，数据库无关性支持好，但会多消耗性能。如果项目需要支持多种数据库，代码开发量少，但SQL语句优化困难。
MyBatis需要手动编写SQL，支持动态SQL、处理列表、动态生成表名、支持存储过程。开发工作量相对大些。直接使用SQL语句操作数据库，不支持数据库无关性，但sql语句优化容易。
3）MyBatis和Hibernate的适用场景不同
Hibernate是标准的ORM框架，SQL编写量较少，但不够灵活，适合于需求相对稳定，中小型的软件项目，比如：办公自动化系统。
MyBatis是半ORM框架，需要编写较多SQL，但是比较灵活，适合于需求变化频繁，快速迭代的项目，比如：电商网站。

3. MyBatis使用过程？生命周期？

MyBatis基本使用的过程大概可以分为这么几步：
1）创建SqlSessionFactory
可以从配置或者直接编码来创建SqlSessionFactory。
2）通过SqlSessionFactory创建SqlSession
SqlSession（会话）可以理解为程序和数据库之间的桥梁。
3）通过sqlsession执行数据库操作
可以通过SqlSession实例来直接执行已映射的SQL语句：
String resource = "org/mybatis/example/mybatis-config.xml";
InputStream inputStream = Resources.getResourceAsStream(resource);
SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
SqlSession session = sqlSessionFactory.openSession();
更常用的方式是先获取Mapper(映射)，然后再执行SQL语句：
Blog blog = (Blog) session.selectOne("org.mybatis.example.BlogMapper.selectBlog", 101);
BlogMapper mapper = session.getMapper(BlogMapper.class);
Blog blog = mapper.selectBlog(101);
4）调用session.commit()提交事务
如果是更新、删除语句，我们还需要提交一下事务。
5）调用session.close()关闭会话
最后一定要记得关闭会话。

MyBatis生命周期？
上面提到了几个MyBatis的组件，一般说的MyBatis生命周期就是这些组件的生命周期。
SqlSessionFactoryBuilder：一旦创建了SqlSessionFactory，就不再需要它了。因此SqlSessionFactoryBuilder实例的生命周期只存在于方法的内部。
SqlSessionFactory：SqlSessionFactory是用来创建SqlSession的，相当于一个数据库连接池，每次创建SqlSessionFactory都会使用数据库资源，多次创建和销毁是对资源的浪费。所以SqlSessionFactory是应用级的生命周期，而且应该是单例的。
SqlSession：SqlSession相当于JDBC中的Connection，SqlSession的实例不是线程安全的，因此是不能被共享的，所以它的最佳的生命周期是一次请求或一个方法。
Mapper：映射器是一些绑定映射语句的接口。映射器接口的实例是从SqlSession中获得的，它的生命周期在sqlsession事务方法之内，一般会控制在方法级。

4. 在mapper中如何传递多个参数？

方法1：顺序传参法
public User selectUser(String name, int deptId);
<select id="selectUser" resultMap="UserResultMap">
    select * from user where user_name = #{0} and dept_id = #{1}
</select>
#{}里面的数字代表传入参数的顺序。这种方法不建议使用，sql层表达不直观，且一旦顺序调整容易出错。

方法2：@Param注解传参法
public User selectUser(@Param("userName") String name, int @Param("deptId") deptId);
<select id="selectUser" resultMap="UserResultMap">
    select * from user where user_name = #{userName} and dept_id = #{deptId}
</select>
#{}里面的名称对应的是注解@Param括号里面修饰的名称。这种方法在参数不多的情况还是比较直观的，（推荐使用）。

方法3：Map传参法
public User selectUser(Map<String, Object> params);
<select id="selectUser" parameterType="java.util.Map" resultMap="UserResultMap">
    select * from user where user_name = #{userName} and dept_id = #{deptId}
</select>
#{}里面的名称对应的是Map里面的key名称。这种方法适合传递多个参数，且参数易变能灵活传递的情况。

方法4：JavaBean传参法
public User selectUser(User user);
<select id="selectUser" parameterType="com.jourwon.pojo.User" resultMap="UserResultMap">
    select * from user where user_name = #{userName} and dept_id = #{deptId}
</select>
#{}里面的名称对应的是User类里面的成员属性。这种方法直观，需要建一个实体类，扩展不容易，需要加属性，但代码可读性强，业务逻辑处理方便，推荐使用。（推荐使用）。

5. 实体类属性名和表中字段名不一样，怎么办?

第1种：通过在查询的SQL语句中定义字段名的别名，让字段名的别名和实体类的属性名一致。
<select id="getOrder" parameterType="int" resultType="com.jourwon.pojo.Order">
       select order_id id, order_no orderno, order_price price from orders where order_id = #{id};
</select>

第2种：通过resultMap中的<result>来映射字段名和实体类属性名的一一对应的关系。
<select id="getOrder" parameterType="int" resultMap="orderResultMap">
  select * from orders where order_id = #{id}
</select>
<resultMap type="com.jourwon.pojo.Order" id="orderResultMap">
    <id property="id" column="order_id">
    <result property="orderno" column="order_no" />
    <result property="price" column="order_price" />
</resultMap>

6. Mybatis是否可以映射Enum枚举类？

Mybatis当然可以映射枚举类，不单可以映射枚举类，Mybatis可以映射任何对象到表的一列上。映射方式为自定义一个TypeHandler，实现TypeHandler的setParameter()和getResult()接口方法。TypeHandler有两个作用，一是完成从javaType至jdbcType的转换，二是完成jdbcType至javaType的转换，体现为setParameter()和getResult()两个方法，分别代表设置sql问号占位符参数和获取列查询结果。

7. #{}和${}的区别?

#{}是占位符，预编译处理；${}是拼接符，字符串替换，没有预编译处理。Mybatis在处理#{}时，#{}传入参数是以字符串传入，会将SQL中的#{}替换为?号，调用PreparedStatement的set方法来赋值。#{}可以有效的防止SQL注入，提高系统安全性；${}不能防止SQL注入。注⼊
# { } 的变量替换是在D B M S 中；$ { } 的变量替换是在 D B M S 外
8. 模糊查询like语句该怎么写?
 
1 ’ % $ { q u e s t i o n } % ’ 可能引起S Q L 注⼊，不推荐
2 " % " # { q u e s t i o n } " % " 注意：因为# { … } 解析成s q l 语句时候，会在变量外侧⾃动加单引号’ ' ，
所以这⾥ % 需要使⽤双引号" " ，不能使⽤单引号 ’ ' ，不然会查不到任何结果。
3 C O N C AT ( ’ % ’ , # { q u e s t i o n } , ’ % ’ ) 使⽤C O N C AT ( ) 函数，（推荐
）
4 使⽤b i n d 标签（不推荐）
9. Mybatis能执⾏⼀对⼀、⼀对多的关联查询吗？
 
当然可以，不⽌⽀持⼀对⼀、⼀对多的关联查询，还⽀持多对多、多对⼀的关联查询。
< s e l e c t i d = " l i s t U s e r L i k e U s e r n a m e " r e s u l t Ty p e = " c o m . j o u r w o n . p o j o . U s e r " >
& e m s p ; & e m s p ; < b i n d n a m e = " p a t t e r n " v a l u e = " ' % ' + u s e r n a m e + ' % ' " / >
& e m s p ; & e m s p ; s e l e c t i d , s e x , a g e , u s e r n a m e , p a s s w o r d f r o m p e r s o n w h e r e u s e r n a m e L I K E #
{ p a t t e r n }
< / s e l e c t >

---

⼀对⼀< a s s o c i a t i o n >
⽐如订单和⽀付是⼀对⼀的关系，这种关联的实现：
实体类:
结果映射
p u b l i c c l a s s O r d e r {
    p r i v a t e I n t e g e r o r d e r I d ;
    p r i v a t e S t r i n g o r d e r D e s c ;
    / * *
     * ⽀付对象
     * /
    p r i v a t e P a y p a y ;
    / / … …
}

---

查询就是普通的关联查
⼀对多< c o l l e c t i o n >
⽐如商品分类和商品，是⼀对多的关系。
实体类
    
结果映射
< ! - - 订单r e s u l t M a p - - >
< r e s u l t M a p i d = " p e o p l e R e s u l t M a p " t y p e = " c n . f i g h t e r 3 . e n t i t y. O rd e r " >
    < i d p r o p e r t y = " o rd e r I d " c o l u m n = " o rd e r _ i d " / >
    < r e s u l t p r o p e r t y = " o rd e r D e s c " c o l u m n = " o rd e r _ d e s c " / >
    < ! - - ⼀对⼀结果映射- - >
    < a s s o c i a t i o n p r o p e r t y = " p a y " j a v a Ty p e = " c n . f i g h t e r 3 . e n t i t y. P a y " >
        < i d c o l u m n = " p a y I d " p r o p e r t y = " p a y _ i d " / >
        < r e s u l t c o l u m n = " a c c o u n t " p r o p e r t y = " a c c o u n t " / >
    < / a s s o c i a t i o n >
< / r e s u l t M a p >
< s e l e c t i d = " g e t Te a c h e r " r e s u l t M a p = " g e t Te a c h e r M a p " p a r a m e t e r Ty p e = " i n t " >
    s e l e c t * f r o m o r d e r o 
     l e f t j o i n p a y p o n o . o r d e r _ i d = p . o r d e r _ i d
    w h e r e  o . o r d e r _ i d = # { o r d e r I d }
< / s e l e c t >
p u b l i c c l a s s C a t e g o r y {
    p r i v a t e i n t c a t e g o r y I d ;
    p r i v a t e S t r i n g c a t e g o r y N a m e ;
    / * *
    * 商品列表
    * * /
    L i s t < P r o d u c t > p r o d u c t s ;
    / / … …
}

---

查询
查询就是⼀个普通的关联查询
10. Mybatis是否⽀持延迟加载？原理？
 
M y b a t i s ⽀持a s s o c i a t i o n 关联对象和c o l l e c t i o n 关联集合对象的延迟加载，a s s o c i a t i o n 指的就是
⼀对⼀，c o l l e c t i o n 指的就是⼀对多查询。在M y b a t i s 配置⽂件中，可以配置是否启⽤延迟加载
l a z y L o a d i n g E n a b l e d = t r u e | f a l s e 。
它的原理是，使⽤C G L I B 创建⽬标对象的代理对象，当调⽤⽬标⽅法时，进⼊拦截器⽅法，⽐如
调⽤a . g e t B ( ) . g e t N a m e ( ) ，拦截器i n v o k e ( ) ⽅法发现a . g e t B ( ) 是n u l l 值，那么就会单独发送事先保
存好的查询关联B 对象的s q l ，把B 查询上来，然后调⽤a . s e t B ( b ) ，于是a 的对象b 属性就有值了，
接着完成a . g e t B ( ) . g e t N a m e ( ) ⽅法的调⽤。这就是延迟加载的基本原理。
当然了，不光是M y b a t i s ，⼏乎所有的包括H i b e r n a t e ，⽀持延迟加载的原理都是⼀样的。
11. 如何获取⽣成的主键?
 
新增标签中添加：k e y P r o p e r t y = " I D "  即可
< r e s u l t M a p t y p e = " C a t e g o r y " i d = " c a t e g o r y B e a n " >
    < i d c o l u m n = " c a t e g o r y I d " p r o p e r t y = " c a t e g o r y _ i d " / >
    < r e s u l t c o l u m n = " c a t e g o r y N a m e " p r o p e r t y = " c a t e g o r y _ n a m e " / >
    < ! - - ⼀对多的关系 - - >
    < ! - - p r o p e r t y : 指的是集合属性的值, o f Ty p e ：指的是集合中元素的类型 - - >
    < c o l l e c t i o n p r o p e r t y = " p ro d u c t s " o f Ty p e = " P ro d u c t " >
        < i d c o l u m n = " p ro d u c t _ i d " p r o p e r t y = " p ro d u c t I d " / >
        < r e s u l t c o l u m n = " p ro d u c t N a m e " p r o p e r t y = " p ro d u c t N a m e " / >
        < r e s u l t c o l u m n = " p r i c e " p r o p e r t y = " p r i c e " / >
    < / c o l l e c t i o n >
< / r e s u l t M a p >
< ! - - 关联查询分类和产品表 - - >
< s e l e c t i d = " l i s t C a t e g o r y " r e s u l t M a p = " c a t e g o r y B e a n " >
    s e l e c t c . * , p . * f r o m c a t e g o r y _ c l e f t j o i n p r o d u c t _ p o n c . i d = p . c i d
< / s e l e c t >  
那么多对⼀、多对多怎么实现呢？还是利⽤\ < a s s o c i a t i o n > 和\ < c o l l e c t i o n > ，篇幅所限，这⾥就不展开
了。

---

这时候就可以完成回填主键
12. MyBatis⽀持动态SQL吗？
 
M y B a t i s 中有⼀些⽀持动态S Q L 的标签，它们的原理是使⽤O G N L 从S Q L 参数对象中计算表达式的值，
根据表达式的值动态拼接S Q L ，以此来完成动态S Q L 的功能。
i f
根据条件来组成w h e r e ⼦句
< i n s e r t i d = " i n s e r t " u s e G e n e r a t e d K e y s = " t r u e " k e y P r o p e r t y = " u s e r I d " >
    i n s e r t i n t o u s e r ( 
    u s e r _ n a m e , u s e r _ p a s s w o r d , c r e a t e _ t i m e ) 
    v a l u e s ( # { u s e r N a m e } , # { u s e r P a s s w o r d } , # { c r e a t e Ti m e , j d b c Ty p e = T I M E S TA M P } )
< / i n s e r t >
m a p p e r . i n s e r t ( u s e r ) ;
u s e r . g e t I d ;

---

c h o o s e ( w h e n , o t h e r w i s e )
这个和J a v a 中的 s w i t c h 语句有点像
t r i m ( w h e r e , s e t )
< w h e r e > 可以⽤在所有的查询条件都是动态的情况
< s e l e c t i d = " f i n d A c t i v e B l o g Wi t h Ti t l e L i k e "
   r e s u l t Ty p e = " B l o g " >
S E L E C T * F R O M B L O G
W H E R E s t a t e = ‘ A C T I V E ’
< i f t e s t = " t i t l e ! = n u l l " >
  A N D t i t l e l i k e # { t i t l e }
< / i f >
< / s e l e c t >
< s e l e c t i d = " f i n d A c t i v e B l o g L i k e "
   r e s u l t Ty p e = " B l o g " >
S E L E C T * F R O M B L O G W H E R E s t a t e = ‘ A C T I V E ’
< c h o o s e >
  < w h e n t e s t = " t i t l e ! = n u l l " >
    A N D t i t l e l i k e # { t i t l e }
  < / w h e n >
  < w h e n t e s t = " a u t h o r ! = n u l l a n d a u t h o r. n a m e ! = n u l l " >
    A N D a u t h o r _ n a m e l i k e # { a u t h o r . n a m e }
  < / w h e n >
  < o t h e r w i s e >
    A N D f e a t u r e d = 1
  < / o t h e r w i s e >
< / c h o o s e >
< / s e l e c t >
< s e l e c t i d = " f i n d A c t i v e B l o g L i k e "
   r e s u l t Ty p e = " B l o g " >
S E L E C T * F R O M B L O G
< w h e r e >
  < i f t e s t = " s t a t e ! = n u l l " >
       s t a t e = # { s t a t e }
  < / i f >
  < i f t e s t = " t i t l e ! = n u l l " >
      A N D t i t l e l i k e # { t i t l e }
  < / i f >
  < i f t e s t = " a u t h o r ! = n u l l a n d a u t h o r. n a m e ! = n u l l " >

---

< s e t > 可以⽤在动态更新的时候
f o r e a c h
看到名字就知道了，这个是⽤来循环的，可以对集合进⾏遍历
13. MyBatis如何执⾏批量操作？
 
      A N D a u t h o r _ n a m e l i k e # { a u t h o r . n a m e }
  < / i f >
< / w h e r e >
< / s e l e c t >
< u p d a t e i d = " u p d a t e A u t h o r I f N e c e s s a r y " >
  u p d a t e A u t h o r
    < s e t >
      < i f t e s t = " u s e r n a m e ! = n u l l " > u s e r n a m e = # { u s e r n a m e } , < / i f >
      < i f t e s t = " p a s s w o rd ! = n u l l " > p a s s w o r d = # { p a s s w o r d } , < / i f >
      < i f t e s t = " e m a i l ! = n u l l " > e m a i l = # { e m a i l } , < / i f >
      < i f t e s t = " b i o ! = n u l l " > b i o = # { b i o } < / i f >
    < / s e t >
  w h e r e i d = # { i d }
< / u p d a t e >
< s e l e c t i d = " s e l e c t P o s t I n " r e s u l t Ty p e = " d o m a i n . b l o g . P o s t " >
S E L E C T *
F R O M P O S T P
< w h e r e >
  < f o r e a c h i t e m = " i t e m " i n d e x = " i n d e x " c o l l e c t i o n = " l i s t "
      o p e n = " I D i n ( " s e p a r a t o r = " , " c l o s e = " ) " n u l l a b l e = " t r u e " >
        # { i t e m }
  < / f o r e a c h >
< / w h e r e >
< / s e l e c t >

---

第⼀种⽅法：使⽤f o re a c h 标签
f o r e a c h 的主要⽤在构建i n 条件中，它可以在S Q L 语句中进⾏迭代⼀个集合。f o r e a c h 标签的属性主要
有i t e m ，i n d e x ，c o l l e c t i o n ，o p e n ，s e p a r a t o r ，c l o s e 。
i t e m        表⽰集合中每⼀个元素进⾏迭代时的别名，随便起的变量名；
i n d e x      指定⼀个名字，⽤于表⽰在迭代过程中，每次迭代到的位置，不常⽤；
o p e n      表⽰该语句以什么开始，常⽤“ ( ” ；
s e p a r a t o r 表⽰在每次进⾏迭代之间以什么符号作为分隔符，常⽤“ , ” ；
c l o s e      表⽰以什么结束，常⽤“ ) ” 。
在使⽤f o r e a c h 的时候最关键的也是最容易出错的就是c o l l e c t i o n 属性，该属性是必须指定的，但是在
不同情况下，该属性的值是不⼀样的，主要有以下3 种情况：
1 . 如果传⼊的是单参数且参数类型是⼀个L i s t 的时候，c o l l e c t i o n 属性值为l i s t
2 . 如果传⼊的是单参数且参数类型是⼀个a r r a y 数组的时候，c o l l e c t i o n 的属性值为a r r a y
3 . 如果传⼊的参数是多个的时候，我们就需要把它们封装成⼀个M a p 了，当然单参数也可以封装成
m a p ，实际上如果你在传⼊参数的时候，在M y B a t i s ⾥⾯也是会把它封装成⼀个M a p 的，m a p 的
k e y 就是参数名，所以这个时候c o l l e c t i o n 属性值就是传⼊的L i s t 或a r r a y 对象在⾃⼰封装的m a p
⾥⾯的k e y
看看批量保存的两种⽤法：

---

第⼆种⽅法：使⽤E x e c u t o r Ty p e . B AT C H
M y b a t i s 内置的E x e c u t o r Ty p e 有3 种，默认为s i m p l e ，该模式下它为每个语句的执⾏创建⼀个新的
预处理语句，单条提交s q l ；⽽b a t c h 模式重复使⽤已经预处理的语句，并且批量执⾏所有更新语
句，显然b a t c h 性能将更优； 但b a t c h 模式也有⾃⼰的问题，⽐如在I n s e r t 操作时，在事务没有提
交之前，是没有办法获取到⾃增的i d ，在某些情况下不符合业务的需求。
具体⽤法如下：
< ! - - M y S Q L 下批量保存，可以f o r e a c h 遍历 m y s q l ⽀持v a l u e s ( ) , ( ) , ( ) 语法 - - > / / 推荐使⽤
< i n s e r t i d = " a d d E m p s B a t c h " >
    I N S E RT I N TO e m p ( e n a m e , g e n d e r , e m a i l , d i d )
    VA L U E S
    < f o r e a c h c o l l e c t i o n = " e m p s " i t e m = " e m p " s e p a r a t o r = " , " >
        ( # { e m p . e N a m e } , # { e m p . g e n d e r } , # { e m p . e m a i l } , # { e m p . d e p t . i d } )
    < / f o r e a c h >
< / i n s e r t >
< ! - - 这种⽅式需要数据库连接属性a l l o w M u t i Q u e r i e s = t r u e 的⽀持
 如j d b c . u r l = j d b c : m y s q l : / / l o c a l h o s t : 3 3 0 6 / m y b a t i s ? a l l o w M u l t i Q u e r i e s = t r u e - - >  
< i n s e r t i d = " a d d E m p s B a t c h " >
    < f o r e a c h c o l l e c t i o n = " e m p s " i t e m = " e m p " s e p a r a t o r = " ; " >                                 
        I N S E RT I N TO e m p ( e n a m e , g e n d e r , e m a i l , d i d )
        VA L U E S ( # { e m p . e N a m e } , # { e m p . g e n d e r } , # { e m p . e m a i l } , # { e m p . d e p t . i d } )
    < / f o r e a c h >
< / i n s e r t >
/ / 批量保存⽅法测试
@ Te s t  
p u b l i c v o i d t e s t B a t c h ( ) t h r o w s I O E x c e p t i o n {
    S q l S e s s i o n F a c t o r y s q l S e s s i o n F a c t o r y = g e t S q l S e s s i o n F a c t o r y ( ) ;
    / / 可以执⾏批量操作的s q l S e s s i o n
    S q l S e s s i o n o p e n S e s s i o n = s q l S e s s i o n F a c t o r y . o p e n S e s s i o n ( E x e c u t o r Ty p e . B AT C H ) ;
    / / 批量保存执⾏前时间
    l o n g s t a r t = S y s t e m . c u r r e n t Ti m e M i l l i s ( ) ;
    t r y {
        E m p l o y e e M a p p e r m a p p e r = o p e n S e s s i o n . g e t M a p p e r ( E m p l o y e e M a p p e r . c l a s s ) ;
        f o r ( i n t i = 0 ; i < 1 0 0 0 ; i + + ) {
            m a p p e r . a d d E m p ( n e w E m p l o y e e ( U U I D . r a n d o m U U I D ( ) . t o S t r i n g ( ) . s u b s t r i n g ( 0 , 5 ) , " b " , 
" 1 " ) ) ;
        }

---

m a p p e r 和m a p p e r. x m l 如下
14. 说说Mybatis的⼀级、⼆级缓存？
 
1 . ⼀级缓存: 基于 P e r p e t u a l C a c h e 的 H a s h M a p 本地缓存，其存储作⽤域为S q l S e s s i o n ，各个
S q l S e s s i o n 之间的缓存相互隔离，当 S e s s i o n f l u s h 或 c l o s e 之后，该 S q l S e s s i o n 中的所有 
C a c h e 就将清空，M y B a t i s 默认打开⼀级缓存。
        o p e n S e s s i o n . c o m m i t ( ) ;
        l o n g e n d = S y s t e m . c u r r e n t Ti m e M i l l i s ( ) ;
        / / 批量保存执⾏后的时间
        S y s t e m . o u t . p r i n t l n ( " 执⾏时长" + ( e n d - s t a r t ) ) ;
        / / 批量 预编译s q l ⼀次= = 》设置参数= = 》1 0 0 0 0 次= = 》执⾏1 次   6 7 7
        / / ⾮批量  （预编译= 设置参数= 执⾏ ）= = 》1 0 0 0 0 次   11 2 1
    } f i n a l l y {
        o p e n S e s s i o n . c l o s e ( ) ;
    }
}
p u b l i c i n t e r f a c e E m p l o y e e M a p p e r {   
    / / 批量保存员⼯
    L o n g a d d E m p ( E m p l o y e e e m p l o y e e ) ;
}
< m a p p e r n a m e s p a c e = " c o m . j o u r w o n . m a p p e r. E m p l o y e e M a p p e r "
     < ! - - 批量保存员⼯ - - >
    < i n s e r t i d = " a d d E m p " >
        i n s e r t i n t o e m p l o y e e ( l a s t N a m e , e m a i l , g e n d e r )
        v a l u e s ( # { l a s t N a m e } , # { e m a i l } , # { g e n d e r } )
    < / i n s e r t >
< / m a p p e r >

---

2 . ⼆级缓存与⼀级缓存其机制相同，默认也是采⽤ P e r p e t u a l C a c h e ，H a s h M a p 存储，不同之处在
于其存储作⽤域为 M a p p e r ( N a m e s p a c e ) ，可以在多个S q l S e s s i o n 之间共享，并且可⾃定义存储
源，如 E h c a c h e 。默认不打开⼆级缓存，要开启⼆级缓存，使⽤⼆级缓存属性类需要实现
S e r i a l i z a b l e 序列化接⼜( 可⽤来保存对象的状态) , 可在它的映射⽂件中配置。

---

原理
 
15. 能说说MyBatis的⼯作原理吗？
 
我们已经⼤概知道了M y B a t i s 的⼯作流程，按⼯作原理，可以分为两⼤步：⽣成会话⼯⼚、会话运
⾏。
M y B a t i s 是⼀个成熟的框架，篇幅限制，这⾥抓⼤放⼩，来看看它的主要⼯作流程。
构建会话⼯⼚
构造会话⼯⼚也可以分为两步：

---

获取配置
获取配置这⼀步经过了⼏步转化，最终由⽣成了⼀个配置类C o n f i g u r a t i o n 实例，这个配置类实例⾮常
重要，主要作⽤包括：
读取配置⽂件，包括基础配置⽂件和映射⽂件
初始化基础配置，⽐如M y B a t i s 的别名，还有其它的⼀些重要的类对象，像插件、映射器、
O b j e c t F a c t o r y 等等
提供⼀个单例，作为会话⼯⼚构建的重要参数
它的构建过程也会初始化⼀些环境变量，⽐如数据源

---

构建S q l S e s s i o n F a c t o r y
S q l S e s s i o n F a c t o r y 只是⼀个接⼜，构建出来的实际上是它的实现类的实例，⼀般我们⽤的都是它的实
现类D e f a u l t S q l S e s s i o n F a c t o r y，
  
会话运⾏
会话运⾏是M y B a t i s 最复杂的部分，它的运⾏离不开四⼤组件的配合：
p u b l i c S q l S e s s i o n F a c t o r y b u i l d ( R e a d e r r e a d e r , S t r i n g e n v i r o n m e n t , P r o p e r t i e s p r o p e r t i e s ) {
      S q l S e s s i o n F a c t o r y v a r 5 ;
      / / 省略异常处理
          / / x m l 配置构建器
          X M L C o n f i g B u i l d e r p a r s e r = n e w X M L C o n f i g B u i l d e r ( r e a d e r , e n v i r o n m e n t , p r o p e r t i e s ) ;
          / / 通过转化的C o n f i g u r a t i o n 构建S q l S e s s i o n F a c t o r y
          v a r 5 = t h i s . b u i l d ( p a r s e r . p a r s e ( ) ) ;
}
p u b l i c S q l S e s s i o n F a c t o r y b u i l d ( C o n f i g u r a t i o n c o n f i g ) {
    r e t u r n n e w D e f a u l t S q l S e s s i o n F a c t o r y ( c o n f i g ) ;
}

---

E x e c u t o r （执⾏器）
E x e c u t o r 起到了⾄关重要的作⽤，S q l S e s s i o n 只是⼀个门⾯，相当于客服，真正⼲活的是是
E x e c u t o r ，就像是默默⽆闻的⼯程师。它提供了相应的查询和更新⽅法，以及事务⽅法。
S t a t e m e n t H a n d l e r （数据库会话器）
S t a t e m e n t H a n d l e r ，顾名思义，处理数据库会话的。我们以S i m p l e E x e c u t o r 为例，看⼀下它的查询⽅
法，先⽣成了⼀个S t a t e m e n t H a n d l e r 实例，再拿这个h a n d l e r 去执⾏q u e r y 。
再以最常⽤的P r e p a r e d S t a t e m e n t H a n d l e r看⼀下它的q u e r y ⽅法，其实在上⾯的p re p a re S t a t e m e n t 已
经对参数进⾏了预编译处理，到了这⾥，就直接执⾏s q l ，使⽤R e s u l t H a n d l e r 处理返回结果。
E n v i r o n m e n t e n v i r o n m e n t = t h i s . c o n f i g u r a t i o n . g e t E n v i r o n m e n t ( ) ;
Tr a n s a c t i o n F a c t o r y t r a n s a c t i o n F a c t o r y = 
t h i s . g e t Tr a n s a c t i o n F a c t o r y F r o m E n v i r o n m e n t ( e n v i r o n m e n t ) ;
t x = t r a n s a c t i o n F a c t o r y . n e w Tr a n s a c t i o n ( e n v i r o n m e n t . g e t D a t a S o u r c e ( ) , l e v e l , a u t o C o m m i t ) ;
/ / 通过C o n f i g u r a t i o n 创建e x e c u t o r
E x e c u t o r e x e c u t o r = t h i s . c o n f i g u r a t i o n . n e w E x e c u t o r ( t x , e x e c Ty p e ) ;
v a r 8 = n e w D e f a u l t S q l S e s s i o n ( t h i s . c o n f i g u r a t i o n , e x e c u t o r , a u t o C o m m i t ) ;
 p u b l i c < E > L i s t < E > d o Q u e r y ( M a p p e d S t a t e m e n t m s , O b j e c t p a r a m e t e r , R o w B o u n d s 
r o w B o u n d s , R e s u l t H a n d l e r r e s u l t H a n d l e r , B o u n d S q l b o u n d S q l ) t h r o w s S Q L E x c e p t i o n {
    S t a t e m e n t s t m t = n u l l ;
    L i s t v a r 9 ;
    t r y {
        C o n f i g u r a t i o n c o n f i g u r a t i o n = m s . g e t C o n f i g u r a t i o n ( ) ;
        S t a t e m e n t H a n d l e r h a n d l e r = c o n f i g u r a t i o n . n e w S t a t e m e n t H a n d l e r ( t h i s . w r a p p e r , m s , 
p a r a m e t e r , r o w B o u n d s , r e s u l t H a n d l e r , b o u n d S q l ) ;
        s t m t = t h i s . p r e p a r e S t a t e m e n t ( h a n d l e r , m s . g e t S t a t e m e n t L o g ( ) ) ;
        v a r 9 = h a n d l e r . q u e r y ( s t m t , r e s u l t H a n d l e r ) ;
    } f i n a l l y {
        t h i s . c l o s e S t a t e m e n t ( s t m t ) ;
    }
    r e t u r n v a r 9 ;
}

---

P a r a m e t e r H a n d l e r （参数处理器）
P r e p a r e d S t a t e m e n t H a n d l e r⾥对s q l 进⾏了预编译处理
这⾥⽤的就是P a r a m e t e r H a n d l e r ，s e t P a r a m e t e r s 的作⽤就是设置预编译S Q L 语句的参数。
⾥⾯还会⽤到t y p e H a n d l e r 类型处理器，对类型进⾏处理。
R e s u l t S e t H a n d l e r （结果处理器）
我们前⾯也看到了，最后的结果要通过R e s u l t S e t H a n d l e r 来进⾏处理，h a n d l e R e s u l t S e t s 这个⽅
法就是⽤来包装结果集的。M y b a t i s 为我们提供了⼀个D e f a u l t R e s u l t S e t H a n d l e r，通常都是⽤这
个实现类去进⾏结果的处理的。
它会使⽤t y p e H a n d l e 处理类型，然后⽤O b j e c t F a c t o r y 提供的规则组装对象，返回给调⽤者。
整体上总结⼀下会话运⾏：
p u b l i c < E > L i s t < E > q u e r y ( S t a t e m e n t s t a t e m e n t , R e s u l t H a n d l e r r e s u l t H a n d l e r ) t h r o w s 
S Q L E x c e p t i o n {
    P r e p a r e d S t a t e m e n t p s = ( P r e p a r e d S t a t e m e n t ) s t a t e m e n t ;
    p s . e x e c u t e ( ) ;
    r e t u r n t h i s . r e s u l t S e t H a n d l e r . h a n d l e R e s u l t S e t s ( p s ) ;
}
p u b l i c v o i d p a r a m e t e r i z e ( S t a t e m e n t s t a t e m e n t ) t h r o w s S Q L E x c e p t i o n {
    t h i s . p a r a m e t e r H a n d l e r . s e t P a r a m e t e r s ( ( P r e p a r e d S t a t e m e n t ) s t a t e m e n t ) ;
}
p u b l i c i n t e r f a c e P a r a m e t e r H a n d l e r {
    O b j e c t g e t P a r a m e t e r O b j e c t ( ) ;
    v o i d s e t P a r a m e t e r s ( P r e p a r e d S t a t e m e n t v a r 1 ) t h r o w s S Q L E x c e p t i o n ;
}
p u b l i c i n t e r f a c e R e s u l t S e t H a n d l e r {
  < E > L i s t < E > h a n d l e R e s u l t S e t s ( S t a t e m e n t v a r 1 ) t h r o w s S Q L E x c e p t i o n ;
  < E > C u r s o r < E > h a n d l e C u r s o r R e s u l t S e t s ( S t a t e m e n t v a r 1 ) t h r o w s S Q L E x c e p t i o n ;
  v o i d h a n d l e O u t p u t P a r a m e t e r s ( C a l l a b l e S t a t e m e n t v a r 1 ) t h r o w s S Q L E x c e p t i o n ;
}

---

我们最后把整个的⼯作流程串联起来，简单总结⼀下：

---

1 . 读取 M y B a t i s 配置⽂件— — m y b a t i s - c o n f i g . x m l 、加载映射⽂件— — 映射⽂件即 S Q L 映射⽂
件，⽂件中配置了操作数据库的 S Q L 语句。最后⽣成⼀个配置对象。
2 . 构造会话⼯⼚：通过 M y B a t i s 的环境等配置信息构建会话⼯⼚ S q l S e s s i o n F a c t o r y 。
3 . 创建会话对象：由会话⼯⼚创建 S q l S e s s i o n 对象，该对象中包含了执⾏ S Q L 语句的所有⽅法。
4 . E x e c u t o r 执⾏器：M y B a t i s 底层定义了⼀个 E x e c u t o r 接⼜来操作数据库，它将根据 
S q l S e s s i o n 传递的参数动态地⽣成需要执⾏的 S Q L 语句，同时负责查询缓存的维护。

---

5 . S t a t e m e n t H a n d l e r ：数据库会话器，串联起参数映射的处理和运⾏结果映射的处理。
6 . 参数处理：对输⼊参数的类型进⾏处理，并预编译。
7 . 结果处理：对返回结果的类型进⾏处理，根据对象映射规则，返回相应的对象。
16. MyBatis的功能架构是什么样的？
 
我们⼀般把M y b a t i s 的功能架构分为三层：
A P I 接⼜层：提供给外部使⽤的接⼜A P I ，开发⼈员通过这些本地A P I 来操纵数据库。接⼜层⼀接
收到调⽤请求就会调⽤数据处理层来完成具体的数据处理。
数据处理层：负责具体的S Q L 查找、S Q L 解析、S Q L 执⾏和执⾏结果映射处理等。它主要的⽬的
是根据调⽤的请求完成⼀次数据库操作。
基础⽀撑层：负责最基础的功能⽀撑，包括连接管理、事务管理、配置加载和缓存处理，这些都是
共⽤的东西，将他们抽取出来作为最基础的组件。为上层的数据处理层提供最基础的⽀撑。
17. 为什么Mapper接⼜不需要实现类？
 
四个字回答：动态代理，我们来看⼀下获取M a p p e r 的过程：

---

获取M a p p e r
我们都知道定义的M a p p e r 接⼜是没有实现类的，M a p p e r 映射其实是通过动态代理实现的。
七拐⼋绕地进去看⼀下，发现获取M a p p e r 的过程，需要先获取M a p p e r P r o x y F a c t o r y — — M a p p e r代理
⼯⼚。
B l o g M a p p e r m a p p e r = s e s s i o n . g e t M a p p e r ( B l o g M a p p e r . c l a s s ) ;

---

M a p p e r P r o x y F a c t o r y
M a p p e r P r o x y F a c t o r y 的作⽤是⽣成M a p p e r P r o x y （M a p p e r 代理对象）。
这⾥可以看到动态代理对接⼜的绑定，它的作⽤就是⽣成动态代理对象（占位），⽽代理的⽅法被放到
了M a p p e r P r o x y 中。
M a p p e r P r o x y
M a p p e r P r o x y ⾥，通常会⽣成⼀个M a p p e r M e t h o d 对象，它是通过c a c h e d M a p p e r M e t h o d ⽅法对其进
⾏初始化的，然后执⾏e x c u t e ⽅法。
p u b l i c < T > T g e t M a p p e r ( C l a s s < T > t y p e , S q l S e s s i o n s q l S e s s i o n ) {
    M a p p e r P r o x y F a c t o r y < T > m a p p e r P r o x y F a c t o r y = 
( M a p p e r P r o x y F a c t o r y ) t h i s . k n o w n M a p p e r s . g e t ( t y p e ) ;
    i f ( m a p p e r P r o x y F a c t o r y = = n u l l ) {
        t h r o w n e w B i n d i n g E x c e p t i o n ( " Ty p e " + t y p e + " i s n o t k n o w n t o t h e M a p p e r R e g i s t r y. " ) ;
    } e l s e {
        t r y {
            r e t u r n m a p p e r P r o x y F a c t o r y . n e w I n s t a n c e ( s q l S e s s i o n ) ;
        } c a t c h ( E x c e p t i o n v a r 5 ) {
            t h r o w n e w B i n d i n g E x c e p t i o n ( " E r ro r g e t t i n g m a p p e r i n s t a n c e . C a u s e : " + v a r 5 , 
v a r 5 ) ;
        }
    }
}
p u b l i c c l a s s M a p p e r P r o x y F a c t o r y < T > {
  p r i v a t e f i n a l C l a s s < T > m a p p e r I n t e r f a c e ;
  … …
  p r o t e c t e d T n e w I n s t a n c e ( M a p p e r P r o x y < T > m a p p e r P r o x y ) {
      r e t u r n P r o x y . n e w P r o x y I n s t a n c e ( t h i s . m a p p e r I n t e r f a c e . g e t C l a s s L o a d e r ( ) , n e w C l a s s [ ]
{ t h i s . m a p p e r I n t e r f a c e } , m a p p e r P r o x y ) ;
  }
  p u b l i c T n e w I n s t a n c e ( S q l S e s s i o n s q l S e s s i o n ) {
      M a p p e r P r o x y < T > m a p p e r P r o x y = n e w M a p p e r P r o x y ( s q l S e s s i o n , t h i s . m a p p e r I n t e r f a c e , 
t h i s . m e t h o d C a c h e ) ;
      r e t u r n t h i s . n e w I n s t a n c e ( m a p p e r P r o x y ) ;
  }
}

---

M a p p e r M e t h o d 
M a p p e r M e t h o d ⾥的e x c u t e ⽅法，会真正去执⾏s q l 。这⾥⽤到了命令模式，其实绕⼀圈，最终它还是
通过S q l S e s s i o n 的实例去运⾏对象的s q l 。
18.Mybatis都有哪些Executor执⾏器？
 
p u b l i c O b j e c t i n v o k e ( O b j e c t p r o x y , M e t h o d m e t h o d , O b j e c t [ ] a rg s ) t h r o w s T h r o w a b l e {
    t r y {
        r e t u r n O b j e c t . c l a s s . e q u a l s ( m e t h o d . g e t D e c l a r i n g C l a s s ( ) ) ? m e t h o d . i n v o k e ( t h i s , a rg s ) : 
t h i s . c a c h e d I n v o k e r ( m e t h o d ) . i n v o k e ( p r o x y , m e t h o d , a rg s , t h i s . s q l S e s s i o n ) ;
    } c a t c h ( T h r o w a b l e v a r 5 ) {
        t h r o w E x c e p t i o n U t i l . u n w r a p T h r o w a b l e ( v a r 5 ) ;
    }
}
p u b l i c O b j e c t e x e c u t e ( S q l S e s s i o n s q l S e s s i o n , O b j e c t [ ] a rg s ) {
      O b j e c t r e s u l t ;
      O b j e c t p a r a m ;
      … …
      c a s e S E L E C T :
          i f ( t h i s . m e t h o d . r e t u r n s Vo i d ( ) & & t h i s . m e t h o d . h a s R e s u l t H a n d l e r ( ) ) {
              t h i s . e x e c u t e Wi t h R e s u l t H a n d l e r ( s q l S e s s i o n , a rg s ) ;
              r e s u l t = n u l l ;
          } e l s e i f ( t h i s . m e t h o d . r e t u r n s M a n y ( ) ) {
              r e s u l t = t h i s . e x e c u t e F o r M a n y ( s q l S e s s i o n , a rg s ) ;
          } e l s e i f ( t h i s . m e t h o d . r e t u r n s M a p ( ) ) {
              r e s u l t = t h i s . e x e c u t e F o r M a p ( s q l S e s s i o n , a rg s ) ;
          } e l s e i f ( t h i s . m e t h o d . r e t u r n s C u r s o r ( ) ) {
              r e s u l t = t h i s . e x e c u t e F o r C u r s o r ( s q l S e s s i o n , a rg s ) ;
          } e l s e {
              p a r a m = t h i s . m e t h o d . c o n v e r t A rg s To S q l C o m m a n d P a r a m ( a rg s ) ;
              r e s u l t = s q l S e s s i o n . s e l e c t O n eMyBatis有三种基本的Executor执行器，SimpleExecutor、ReuseExecutor、BatchExecutor。
SimpleExecutor：每执行一次update或select，就开启一个Statement对象，用完立刻关闭Statement对象。
ReuseExecutor：执行update或select，以sql作为key查找Statement对象，存在就使用，不存在就创建，用完后，不关闭Statement对象，而是放置于Map<String, Statement>内，供下一次使用。简言之，就是重复使用Statement对象。
BatchExecutor：执行update（没有select，JDBC批处理不支持select），将所有sql都添加到批处理中（addBatch()），等待统一执行（executeBatch()），它缓存了多个Statement对象，每个Statement对象都是addBatch()完毕后，等待逐一执行executeBatch()批处理。与JDBC批处理相同。
作用范围：Executor的这些特点，都严格限制在SqlSession生命周期范围内。
Mybatis中如何指定使用哪一种Executor执行器？
在Mybatis配置文件中，在设置（settings）可以指定默认的ExecutorType执行器类型，也可以手动给DefaultSqlSessionFactory的创建SqlSession的方法传递ExecutorType类型参数，如SqlSession openSession(ExecutorType execType)。
配置默认的执行器。SIMPLE就是普通的执行器；REUSE执行器会重用预处理语句（prepared statements）；BATCH执行器将重用语句并执行批量更新。

插件

19. 说说Mybatis的插件运行原理，如何编写一个插件？

插件的运行原理？
Mybatis会话的运行需要ParameterHandler、ResultSetHandler、StatementHandler、Executor这四大对象的配合，插件的原理就是在这四大对象调度的时候，插入一些我们自己的代码。

Mybatis使用JDK的动态代理，为目标对象生成代理对象。它提供了一个工具类Plugin，实现了InvocationHandler接口。
使用Plugin生成代理对象，代理对象在调用方法的时候，就会进入invoke方法，在invoke方法中，如果存在签名的拦截方法，插件的intercept方法就会在这里被我们调用，然后就返回结果。如果不存在签名方法，那么将直接反射调用我们要执行的方法。
如何编写一个插件？

我们自己编写MyBatis插件，只需要实现拦截器接口Interceptor (org.apache.ibatis.plugin.Interceptor），在实现类中对拦截对象和方法进行处理。
实现Mybatis的Interceptor接口并重写intercept()方法
这里我们只是在目标对象执行目标方法的前后进行了打印；
  
然后再给插件编写注解，确定要拦截的对象，要拦截的方法
public class MyInterceptor implements Interceptor {
    Properties props = null;
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("before……");
        // 如果当前代理的是一个非代理对象，那么就会调用真实拦截对象的方法
        // 如果不是它就会调用下个插件代理对象的invoke方法
        Object obj = invocation.proceed();
        System.out.println("after……");
        return obj;
    }
}
@Intercepts({@Signature(
        type = Executor.class,  // 确定要拦截的对象
        method = "update",        // 确定要拦截的方法
        args = {MappedStatement.class, Object.class}   // 拦截方法的参数
) })
public class MyInterceptor implements Interceptor {
    Properties props = null;
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("before……");
        // 如果当前代理的是一个非代理对象，那么就会调用真实拦截对象的方法
        // 如果不是它就会调用下个插件代理对象的invoke方法
        Object obj = invocation.proceed();
        System.out.println("after……");
        return obj;
    }
}

最后，再MyBatis配置文件里面配置插件

20. MyBatis是如何进行分页的？分页插件的原理是什么？

MyBatis是如何分页的？
MyBatis使用RowBounds对象进行分页，它是针对ResultSet结果集执行的内存分页，而非物理分页。
可以在sql内直接书写带有物理分页的参数来完成物理分页功能，也可以使用分页插件来完成物理分页。
分页插件的原理是什么？
分页插件的基本原理是使用Mybatis提供的插件接口，实现自定义插件，拦截Executor的query方法
在执行查询的时候，拦截待执行的sql，然后重写sql，根据dialect方言，添加对应的物理分页语句和物理分页参数。
举例：select * from student，拦截sql后重写为：select t.* from (select * from student) t limit 0, 10
可以看一下一个大概的MyBatis通用分页拦截器：
<plugins>
    <plugin interceptor="xxx.MyPlugin">
       <property name="dbType", value="mysql" />
    </plugin>
</plugins>

---

## 图表说明

> [图] 第1页：这张图是MyBatis的Logo，MyBatis是一个优秀的持久层框架，它支持定制化SQL、存储过程以及高级映射。

> [图] 第2页：有技术知识点。该图展示了Java实体类与数据库表之间的映射关系，体现了ORM（对象关系映射）的基本思想。

> [图] 第3页：有技术知识点。该图展示了Java中使用JDBC连接MySQL数据库的代码示例，并指出了传统JDBC编程中存在的问题：频繁创建数据库连接消耗资源、SQL语句写在代码中不易维护、参数传递麻烦以及结果集解析复杂。

> [图] 第4页：这张图是一个网络表情包，属于“熊猫头”系列的梗图，通常用于表达一种“显而易见、无需多言”的情绪。图中文字“这还用说？”进一步强化了这种语气。该图本身不包含具体的技术知识点，主要用于网络交流中的情感表达。

**一句话描述：** 无技术知识点，为网络流行表情包，用于表达“显而易见”的态度。

> [图] 第5页：有技术知识点。这张图对比了两个Java持久层框架：MyBatis和Hibernate，常用于讨论两者在ORM（对象关系映射）实现方式、性能、灵活性等方面的差异。

> [图] 第6页：有技术知识点。该图展示了MyBatis框架中数据库操作的基本流程：创建SqlSessionFactory → 创建SqlSession → 获取Mapper → 执行SQL → 提交事务 → 关闭Session。

> [图] 第8页：有技术知识点。该图展示了MyBatis框架中SqlSessionFactory、SqlSession和Mapper之间的关系：SqlSessionFactory作为全局单例，用于创建多个SqlSession；每个SqlSession可执行多个Mapper操作，Mapper相当于执行一条SQL语句，通常在方法内使用。

> [图] 第9页：有技术知识点。这张图展示了在MyBatis中Mapper接口传递多个参数的四种常见方法：顺序传参法、@Param注解传参法、Map传参法和Java Bean传参法。

> [图] 第12页：有技术知识点。图中展示的是模板字符串（Template Literals）的两种常见语法形式，`#{}` 和 `${}`，分别用于不同编程语言或框架中的变量插值，如Ruby和JavaScript。

> [图] 第12页：有技术知识点。该图展示了一个SQL函数 `CONCAT('%', #{question}, '%')`，用于拼接字符串，常用于模糊查询中构建LIKE条件。

> [图] 第13页：有技术知识点。该图展示了MyBatis中级联查询的两种主要方式：使用`<association>`处理一对一和多对一关系，使用`<collection>`处理一对多和多对多关系。

> [图] 第16页：有技术知识点。这张图展示了MyBatis等ORM框架中常用的动态SQL标签，包括 `<if>`、`<where>`、`<foreach>` 和 `<set>`，用于构建条件查询和循环语句。

> [图] 第19页：有技术知识点。该图展示了MyBatis中实现批量操作的两种主要方式：使用`<foreach>`标签和设置`ExecutorType.BATCH`。

> [图] 第22页：有技术知识点。该图展示了MyBatis中SqlSession的本地缓存（LocalCache）机制，每个SqlSession拥有独立的本地缓存，用于减少对数据库的重复访问，提升查询效率。

> [图] 第22页：有技术知识点。该图展示了MyBatis中的一级缓存（LocalCache）和二级缓存（全局Namespace Cache）的架构，描述了用户通过SqlSession访问本地缓存和共享缓存，最终与数据库交互的过程。

> [图] 第23页：有技术知识点。该图展示了MyBatis框架中从创建SqlSessionFactory到执行SQL的典型流程，包括构建会话工厂和会话运行两个阶段，体现了MyBatis的核心工作流程。

> [图] 第23页：这张图主要包含一个二维码和微信公众号的界面截图，用于引导用户关注公众号“沉默王二”以学习Java，其中没有直接的技术知识点。  
**用一句话描述：** 无技术知识点，仅为推广学习Java的公众号关注指引。

> [图] 第24页：有技术知识点。该图展示了MyBatis框架的初始化流程：通过XMLConfigBuilder解析mybatis-config.xml和Mapper XML文件，构建Configuration对象（包含环境、映射器注册等配置），最终生成SqlSessionFactory实例用于创建数据库会话。

> [图] 第25页：有技术知识点。该图展示了MyBatis框架中SqlSession的核心组件，包括Executor、StatementHandler、ParameterHandler和ResultSetHandler，它们分别负责执行SQL、处理语句、参数设置和结果集处理。

> [图] 第28页：有技术知识点。该图描述了MyBatis框架中SQL执行的核心流程，包括Executor调用StatementHandle进行预编译、ParameterHandler设置参数、TypeHandler处理参数类型转化、执行查询或更新操作、ResultSetHandle处理结果以及ObjectFactory进行对象转化的完整过程。

> [图] 第29页：有技术知识点。该图展示了MyBatis框架的核心执行流程，包括配置加载、会话工厂创建、SQL执行及结果处理等关键步骤。

> [图] 第30页：有技术知识点。该图展示了MyBatis框架的架构设计，包括接口层、数据处理层和基础支撑层，涵盖了参数映射、SQL解析与执行、结果处理以及连接管理、事务管理等核心组件和技术流程。

> [图] 第31页：有技术知识点。该图展示了MyBatis中Mapper接口调用的执行流程：从Mapper接口通过MapperProxyFactory生成代理对象，经由MapperProxy和MapperMethod最终执行SQL操作。

> [图] 第34页：有技术知识点。该图展示了三种不同的Executor实现：SimpleExecutor、ReusableExecutor和BatchExecutor，体现了在数据库或任务执行框架中不同执行器的分类与设计模式。

> [图] 第35页：这张图主要是一个微信公众号的推广图，包含二维码和公众号界面截图，用于引导用户关注“沉默王二”公众号学习Java，**无具体技术知识点**。  
**用一句话描述**：通过二维码和公众号界面引导关注“沉默王二”以学习Java。

> [图] 第36页：有技术知识点。该图展示了Java中代理模式的`invoke`方法实现，关键点是通过插件的`intercept`方法拦截并处理方法调用，体现了AOP（面向切面编程）的核心思想。

> [图] 第36页：有技术知识点。该图展示了MyBatis框架中SQL执行流程的核心组件及其交互关系，包括SqlSession、Executor、StatementHandler、ParameterHandler、ResultSetHandler和数据库之间的调用与拦截机制。

> [图] 第38页：这张图包含技术知识点，展示了一段Java代码，实现了MyBatis框架中的分页拦截器（PageInterceptor），用于在执行SQL查询时自动处理分页逻辑，包括统计总记录数和分页查询。

> [图] 第39页：有技术知识点。这是一段Java代码，实现了MyBatis框架中的分页拦截器（PageInterceptor），用于在执行SQL查询时自动添加分页逻辑，包括计算总数和分页查询，涉及AOP、动态SQL、缓存等技术。

> [图] 第40页：这张图展示了Java代码，涉及MyBatis框架中PageInterceptor分页拦截器的实现，主要技术知识点是通过拦截SQL查询方法实现分页功能。

> [图] 第41页：这张图包含Java代码，展示了一个名为`PageInterceptor`的拦截器类，用于实现分页查询功能，涉及SQL语句的动态构建、缓存机制和结果处理等技术知识点。

> [图] 第42页：有技术知识点。这是一段Java代码，实现了MyBatis框架中的分页拦截器（PageInterceptor），用于在执行SQL查询前自动添加分页逻辑，包括统计总数和分页查询，支持缓存和动态参数处理。

> [图] 第42页：这张图主要是一个二维码和微信公众号的截图，用于引导用户关注公众号“沉默王二”以学习Java，**无具体技术知识点**。  
**用途**：推广公众号，提供Java学习资源。