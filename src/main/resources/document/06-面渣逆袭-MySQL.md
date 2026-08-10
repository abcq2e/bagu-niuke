# 面渣逆袭 —— MySQL

> 来源：面渣逆袭MySQL篇V2.2.pdf

MySQL 基础

0. 什么是 MySQL？

MySQL 是一个开源的关系型数据库，现在隶属于 Oracle 公司。是我们国内使用频率最高的一种数据库，我在本地安装的是最新的 8.3 版本。

怎么删除/创建一张表？

可以使用 DROP TABLE 来删除表，使用 CREATE TABLE 来创建表。创建表的时候，可以通过 PRIMARY KEY 设定主键。

请写一个升序/降序的 SQL 语句？

在 SQL 中，可以使用 ORDER BY 子句来对查询结果进行升序或者降序。默认情况下，查询结果是升序的，如果需要降序，可以通过 DESC 关键字来实现。比如说在员工表中，我们要按工资降序，就可以使用 ORDER BY salary DESC 来完成：

SELECT id, name, salary FROM employees ORDER BY salary DESC;

如果需对多个字段进行排序，例如按工资降序，按名字升序，就可以 ORDER BY salary DESC, name ASC 来完成：

SELECT id, name, salary FROM employees ORDER BY salary DESC, name ASC;

MySQL出现性能差的原因有哪些？

可能是 SQL 查询使用了全表扫描，也可能是查询语句过于复杂，如多表 JOIN 或嵌套子查询。也有可能是单表数据量过大。通常情况下，添加索引就能解决大部分性能问题。对于一些热点数据，还可以通过增加 Redis 缓存，来减轻数据库的访问压力。

1. 两张表怎么进行连接？

可以通过内连接 inner join 、外连接 outer join 、交叉连接 cross join 来合并多个表的查询结果。

什么是内连接？

内连接用于返回两个表中有匹配关系的行。假设有两张表，用户表和订单表，想查询有订单的用户，就可以使用内连接 users INNER JOIN orders ，按照用户 ID 关联就行了。只有那些在两个表中都存在 user_id 的记录才会出现在查询结果中。

CREATE TABLE users ( id INT AUTO_INCREMENT, name VARCHAR(100) NOT NULL, email VARCHAR(100), PRIMARY KEY (id) );

SELECT users.name, orders.order_id FROM users INNER JOIN orders ON users.id = orders.user_id;

什么是外连接？

和内连接不同，外连接不仅返回两个表中匹配的行，还返回没有匹配的行，用 null 来填充。外连接又分为左外连接 left join 和右外连接 right join 。left join 会保留左表中符合条件的所有记录，如果右表中有匹配的记录，就返回匹配的记录，否则就用 null 填充，常用于某表中有，但另外一张表中可能没有的数据的查询场景。假设要查询所有用户及他们的订单，即使用户没有下单，就可以使用左连接：

SELECT users.id, users.name, orders.order_id FROM users LEFT JOIN orders ON users.id = orders.user_id;

右连接就是左连接的镜像，right join 会保留右表中符合条件的所有记录，如果左表中有匹配的记录，就返回匹配的记录，否则就用 null 填充。

什么是交叉连接？

交叉连接会返回两张表的笛卡尔积，也就是将左表的每一行与右表的每一行进行组合，返回的行数是两张表行数的乘积。假设有 A 表和 B 表，A 表有 2 行数据，B 表有 3 行数据，那么交叉连接的结果就是 2 * 3 = 6 行。笛卡尔积是数学中的一个概念，例如集合 A={a,b} ，集合 B={0,1,2} ，那么 A*B= {<a,0>,<a,1>,<a,2>,<b,0>,<b,1>,<b,2>,} 。

SELECT A.id, B.id FROM A CROSS JOIN B;

2. 内连接、左连接、右连接有什么区别？

MySQL 的连接主要分为内连接和外连接，外连接又可以分为左连接和右连接。内连接可以用来找出两个表中共同的记录，相当于两个数据集的交集。左连接和右连接可以用来找出两个表中不同的记录，相当于两个数据集的并集。两者的区别是，左连接会保留左表中符合条件的所有记录，右连接则刚好相反。

拿技术派实战项目的表为例来详细验证下。有三张表，一张文章表 article，主要存文章标题 title， 一张文章详情表 article_detail，主要存文章的内容 content，一张文章评论表 comment，主要存评论 content，三个表通过文章 id 关联。先来看内连接：

SELECT LEFT(a.title, 20) AS ArticleTitle, LEFT(c.content, 20) AS CommentContent FROM article a INNER JOIN comment c ON a.id = c.article_id LIMIT 2;

返回至少有一条评论的文章标题和评论内容（前 20 个字符），只返回符合条件的前 2 条记录。再来看左连接：

SELECT LEFT(a.title, 20) AS ArticleTitle, LEFT(c.content, 20) AS CommentContent FROM article a LEFT JOIN comment c ON a.id = c.article_id LIMIT 2;

返回所有文章的标题和文章评论，即使某些文章没有评论（填充为 NULL）。最后来看右连接：

SELECT LEFT(a.title, 20) AS ArticleTitle, LEFT(c.content, 20) AS CommentContent FROM comment c RIGHT JOIN article a ON a.id = c.article_id LIMIT 2;

3. 说一下数据库的三大范式？

第一范式，确保表的每一列都是不可分割的基本数据单元，比如说用户地址，应该拆分成省、市、区、详细地址等 4 个字段。第二范式，要求表中的每一列都和主键直接相关。比如在订单表中，商品名称、单位、商品价格等字段应该拆分到商品表中。然后新建一个订单商品关联表，用订单编号和商品编号进行关联就好了。第三范式，非主键列应该只依赖于主键列。比如说在设计订单信息表的时候，可以把客户名称、所属公司、联系方式等信息拆分到客户信息表中，然后在订单信息表中用客户编号进行关联。

建表的时候需要考虑哪些问题？

首先需要考虑表是否符合数据库的三大范式，确保字段不可再分，消除非主键依赖，确保字段仅依赖于主键等。然后在选择字段类型时，应该尽量选择合适的数据类型。在字符集上，尽量选择 utf8mb4，这样不仅可以支持中文和英文，还可以支持表情符号等。当数据量较大时，比如上千万行数据，需要考虑分表。比如订单表，可以采用水平分表的方式来分散单表存储压力。

4. varchar 与 char 的区别？

varchar 是可变长度的字符类型，原则上最多可以容纳 65535 个字符，但考虑字符集，以及 MySQL 需要 1 到 2 个字节来表示字符串长度，所以实际上最大可以设置到 65533。latin1 字符集，且列属性定义为 NOT NULL。char 是固定长度的字符类型，当定义一个 CHAR(10) 字段时，不管实际存储的字符长度是多少，都只会占用 10 个字符的空间。如果插入的数据小于 10 个字符，剩余的部分会用空格填充。

5. blob 和 text 有什么区别？

blob 用于存储二进制数据，比如图片、音频、视频、文件等；但实际开发中，我们都会把这些文件存储到 OSS 或者文件服务器上，然后在数据库中存储文件的 URL。text 用于存储文本数据，比如文章、评论、日志等。

6. DATETIME 和 TIMESTAMP 有什么区别？

DATETIME 直接存储日期和时间的完整值，与时区无关。TIMESTAMP 存储的是 Unix 时间戳，1970-01-01 00:00:01 UTC 以来的秒数，受时区影响。另外，DATETIME 的默认值为 null，占用 8 个字节；TIMESTAMP 的默认值为当前时间——CURRENT_TIMESTAMP，占 4 个字节，实际开发中更常用，因为可以自动更新。

7. in和exists的区别？

当使用 IN 时，MySQL 会首先执行子查询，然后将子查询的结果集用于外部查询的条件。这意味着子查询的结果集需要全部加载到内存中。而 EXISTS 会对外部查询的每一行，执行一次子查询。如果子查询返回任何行，则 EXISTS 条件为真。EXISTS 关注的是子查询是否返回行，而不是返回的具体值。IN 适用于子查询结果集较小的情况。如果子查询返回大量数据，IN 的性能可能会下降，因为它需要将整个结果集加载到内存。而 EXISTS 适用于子查询结果集可能很大的情况。由于 EXISTS 只需要判断子查询是否返回行，而不需要加载整个结果集，因此在某些情况下性能更好，特别是当子查询可以使用索引时。

NULL值陷阱了解吗？

IN : 如果子查询的结果集中包含 NULL 值，可能会导致意外的结果。例如，WHERE column IN (subquery) ，如果 subquery 返回 NULL ，则 column IN (subquery) 永远不会为真，除非 column 本身也为 NULL 。EXISTS : 对 NULL 值的处理更加直接。EXISTS 只是检查子查询是否返回行，不关心行的具体值，因此不受 NULL 值的影响。

8. 记录货币用什么类型比较好？

如果是电商、交易、账单等涉及货币的场景，建议使用 DECIMAL 类型，因为 DECIMAL 类型是精确数值类型，不会出现浮点数计算误差。例如，DECIMAL(19,4) 可以存储最多 19 位数字，其中 4 位是小数。如果是银行，涉及到支付的场景，建议使用 BIGINT 类型。可以将货币金额乘以一个固定因子，比如 100，表示以“分”为单位，然后存储为 BIGINT 。这种方式既避免了浮点数问题，同时也提供了不错的性能。但在展示的时候需要除以相应的因子。

为什么不推荐使用 FLOAT 或 DOUBLE？

因为 FLOAT 和 DOUBLE 都是浮点数类型，会存在精度问题。在许多编程语言中，0.1 + 0.2 的结果会是类似 0.30000000000000004 的值，而不是预期的 0.3 。

9. 怎么存储 emoji?

因为 emoji（😊）是 4 个字节的 UTF-8 字符，而 MySQL 的 utf8 字符集只支持最多 3 个字节的 UTF-8 字符，所以在 MySQL 中存储 emoji 时，需要使用 utf8mb4 字符集。MySQL 8.0 已经默认支持 utf8mb4 字符集，可以通过 SHOW VARIABLES WHERE Variable_name LIKE 'character\_set\_%' OR Variable_name LIKE 'collation%'; 查看。

ALTER TABLE mytable CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

10. drop、delete 与 truncate 的区别？

DROP 是物理删除，用来删除整张表，包括表结构，且不能回滚。DELETE 支持行级删除，可以带 WHERE 条件，可以回滚。TRUNCATE 用于清空表中的所有数据，但会保留表结构，不能回滚。

11. UNION 与UNION ALL 的区别？
 
UNION 会⾃动去除合并后结果集中的重复⾏。UNION ALL 不会去重，会将所有结果集合并起来。
12.count(1)、count(*) 与 count(列名) 的区别？
 
在 InnoDB 引擎中，COUNT(1) 和 COUNT(*) 没有区别，都是⽤来统计所有⾏，包括 NULL。
如果表有索引，COUNT(*) 会直接⽤索引统计，⽽不是全表扫描，⽽ COUNT(1) 也会被 MySQL 优化为 
COUNT(*) 。
COUNT(列名) 只统计列名不为 NULL 的⾏数。
-- 假设 users 表：
+----+-------+------------+
| id | name  | email      |
+----+-------+------------+
| 1  | 张三  | zhang@xx.com |
| 2  | 李四  | NULL       |
| 3  | 王⼆  | wang@xx.com |
+----+-------+------------+
-- COUNT(*)
SELECT COUNT(*) FROM users;
-- 结果：3  （统计所有⾏）
-- COUNT(1)
SELECT COUNT(1) FROM users;
-- 结果：3  （统计所有⾏）
-- COUNT(email)
SELECT COUNT(email) FROM users;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 18 / 302

---

这⾥解释⼀下，假设有这样⼀张表：
插⼊的数据为：
因为 id 列没有索引，所以 select count(*) 是全表扫描。
然后我们给 id 列加上索引。
-- 结果：2  （NULL 不计⼊统计）
CREATE TABLE t1 (
    id INT,
    name VARCHAR(50),
    value INT
);
INSERT INTO t1 VALUES 
    (1, 'A', 10),
    (2, 'B', NULL),  -- NULL in value column
    (3, 'C', 30),
    (4, NULL, 40),   -- NULL in name column
    (5, 'E', NULL);  -- NULL in value column
alter table t1 add primary key (id);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 19 / 302

---

再来看⼀下 select count(*) ，发现⽤了索引（MySQL 默认为给主键添加索引）。
另外，MySQL 8.0 官⽅⼿册有明确说明，InnoDB 引擎对 SELECT COUNT(*) 和 SELECT COUNT(1) 的处理⽅式完
全⼀致，性能并⽆差异。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 20 / 302

---

memo：2025 年 3 ⽉ 5 ⽇修改⾄此。再晒⼀个喜报给正在刷⼋股的你，⼀位球友拿到了咪咕的⼤模型应⽤开发，
很不错的⽅向，恭喜了！给你也加加好运
buff，你也加把劲。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 21 / 302

---

13.SQL 查询语句的执⾏顺序了解吗？
 
了解。先执⾏ FROM 确定主表，再执⾏ JOIN 连接，然后 WHERE 进⾏过滤，接着 GROUP BY 进⾏分组，HAVING 
过滤聚合结果，SELECT 选择最终列，ORDER BY 排序，最后 LIMIT 限制返回⾏数。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 22 / 302

---

WHERE 先执⾏是为了减少数据量，HAVING 只能过滤聚合数据，ORDER BY 必须在 SELECT 之后排序最终结果，
LIMIT 最后执⾏以减少数据传输。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 23 / 302

---

执⾏顺序
SQL 关键字
作⽤
①
FROM
确定主表，准备数据
②
ON
连接多个表的条件
③
JOIN
执⾏ INNER JOIN / LEFT JOIN 等
④
WHERE
过滤⾏数据（提⾼效率）
⑤
GROUP BY
进⾏分组
⑥
HAVING
过滤聚合后的数据
⑦
SELECT
选择最终返回的列
⑧
DISTINCT
进⾏去重
⑨
ORDER BY
对最终结果排序
⑩
LIMIT
限制返回⾏数
这个执⾏顺序与编写 SQL 语句的顺序不同，这也是为什么有时候在 SELECT ⼦句中定义的别名不能在 WHERE ⼦句
中使⽤得原因，因为 WHERE 是在 SELECT 之前执⾏的。
LIMIT 为什么在最后执⾏？
 
因为 LIMIT 是在最终结果集上执⾏的，如果在 WHERE 之前执⾏ LIMIT，那么就会先返回所有⾏，然后再进⾏ 
LIMIT 限制，这样会增加数据传输的开销。
ORDER BY 为什么在 SELECT 之后执⾏？
 
因为排序需要基于最终返回的列，如果 ORDER BY 早于 SELECT 执⾏，计算 COUNT(*) 之类的聚合函数就会出问
题。
14.介绍⼀下 MySQL 的常⽤命令（补充）
 
2024 年 03 ⽉ 13 ⽇增补。
SELECT name, COUNT(*) AS order_count
FROM orders
GROUP BY name
ORDER BY order_count DESC;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 24 / 302

---

MySQL 的常⽤命令主要包括数据库操作命令、表操作命令、⾏数据 CRUD 命令、索引和约束的创建修改命令、⽤
户和权限管理的命令、事务控制的命令等。
说说数据库操作命令？
 
CREATE DATABASE database_name; ⽤于创建数据库；DROP DATABASE database_name; ⽤于删除数据库；
SHOW DATABASES; ⽤于显示所有数据库；USE database_name; ⽤于切换数据库。
说说表操作命令？
 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 25 / 302

---

CREATE TABLE table_name (列名1 数据类型1, 列名2 数据类型2,...); ⽤于创建表；DROP TABLE 
table_name; ⽤于删除表；SHOW TABLES; ⽤于显示所有表；DESCRIBE table_name; ⽤于查看表结构；ALTER 
TABLE table_name ADD column_name datatype; ⽤于修改表。
说说⾏数据的 CRUD 命令？
 
INSERT INTO table_name (column1, column2, ...) VALUES (value1, value2, ...); ⽤于插⼊数据；
SELECT column_names FROM table_name WHERE condition; ⽤于查询数据；UPDATE table_name SET 
column1 = value1, column2 = value2 WHERE condition; ⽤于更新数据；DELETE FROM table_name 
WHERE condition; ⽤于删除数据。
说说索引和约束的创建修改命令？
 
CREATE INDEX index_name ON table_name (column_name); ⽤于创建索引；ALTER TABLE table_name ADD 
PRIMARY KEY (column_name); ⽤于添加主键；ALTER TABLE table_name ADD CONSTRAINT fk_name 
FOREIGN KEY (column_name) REFERENCES parent_table (parent_column_name); ⽤于添加外键。
说说⽤户和权限管理的命令？
 
CREATE USER 'username'@'host' IDENTIFIED BY 'password'; ⽤于创建⽤户；GRANT ALL PRIVILEGES ON 
database_name.table_name TO 'username'@'host'; ⽤于授予权限；REVOKE ALL PRIVILEGES ON 
database_name.table_name FROM 'username'@'host'; ⽤于撤销权限；DROP USER 'username'@'host'; 
⽤于删除⽤户。
说说事务控制的命令？
 
START TRANSACTION; ⽤于开始事务；COMMIT; ⽤于提交事务；ROLLBACK; ⽤于回滚事务。
1. Java ⾯试指南（付费）收录的⽤友⾦融⼀⾯原题：介绍⼀下 MySQL 的常⽤命令
15.MySQL bin ⽬录下的可执⾏⽂件了解吗（补充）
 
2024 年 03 ⽉ 13 ⽇增补
推荐阅读：MySQL bin ⽬录下的⼀些可执⾏⽂件
了解的。MySQL 的 bin ⽬录下有很多可执⾏⽂件，主要⽤于管理 MySQL 服务器、数据库、表、数据等。⽐如
说：
mysql：⽤于连接 MySQL 服务器
mysqldump：⽤于数据库备份，对数据备份、迁移或恢复时⾮常有⽤
mysqladmin：⽤来执⾏⼀些管理操作，⽐如说创建数据库、删除数据库、查看 MySQL 服务器的状态等。
mysqlcheck：⽤于检查、修复、分析和优化数据库表，对数据库的维护和性能优化⾮常有⽤。
mysqlimport：⽤于从⽂本⽂件中导⼊数据到数据库表中，适合批量数据导⼊。
mysqlshow：⽤于显示 MySQL 数据库服务器中的数据库、表、列等信息。
mysqlbinlog：⽤于查看 MySQL ⼆进制⽇志⽂件的内容，可以⽤于恢复数据、查看数据变更等。
16.MySQL 第 3-10 条记录怎么查？（补充）
 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 26 / 302

---

2024 年 03 ⽉ 30 ⽇增补
可以使⽤ limit 语句，结合偏移量和⾏数来实现。
limit 语句⽤于限制查询结果的数量，偏移量表示从哪条记录开始，⾏数表示返回的记录数量。
2：偏移量，表示跳过前两条记录，从第三条记录开始。
8：⾏数，表示从偏移量开始，返回 8 条记录。
偏移量是从 0 开始的，即第⼀条记录的偏移量是 0；如果想从第 3 条记录开始，偏移量就应该是 2。
1. Java ⾯试指南（付费）收录的美团⾯经同学 16 暑期实习⼀⾯⾯试原题：MySQL 第 3-10 条记录怎么
查？
17.⽤过哪些 MySQL 函数？（补充）
 
2024 年 04 ⽉ 12 ⽇增补
⽤过挺多的，⽐如说处理字符串的函数：
CONCAT() : ⽤于连接两个或多个字符串。
LENGTH() : ⽤于返回字符串的⻓度。
SUBSTRING() : 从字符串中提取⼦字符串。
REPLACE() : 替换字符串中的某部分。
TRIM() : 去除字符串两侧的空格或其他指定字符。
实测数据：
处理数字的函数：
ABS() : 返回⼀个数的绝对值。
ROUND() : 四舍五⼊到指定的⼩数位数。
SELECT * FROM table_name LIMIT 2, 8;
-- 连接字符串
SELECT CONCAT('沉默', ' ', '王⼆') AS concatenated_string;
-- 获取字符串⻓度
SELECT LENGTH('沉默 王⼆') AS string_length;
-- 提取⼦字符串
SELECT SUBSTRING('沉默 王⼆', 1, 5) AS substring;
-- 替换字符串内容
SELECT REPLACE('沉默 王⼆', '王⼆', 'MySQL') AS replaced_string;
-- 去除字符串两侧的空格
SELECT TRIM('  沉默 王⼆  ') AS trimmed_string;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 27 / 302

---

MOD() : 返回除法操作的余数。
实测数据：
⽇期和时间处理函数：
NOW() : 返回当前的⽇期和时间。
CURDATE() : 返回当前的⽇期。
实测数据：
汇总函数：
SUM() : 计算数值列的总和。
AVG() : 计算数值列的平均值。
COUNT() : 计算某列的⾏数。
实测数据：
-- 返回绝对值
SELECT ABS(-123) AS absolute_value;
-- 四舍五⼊
SELECT ROUND(123.4567, 2) AS rounded_value;
-- 余数
SELECT MOD(10, 3) AS modulus;
-- 返回当前⽇期和时间
SELECT NOW() AS current_date_time;
-- 返回当前⽇期
SELECT CURDATE() AS current_date;
-- 创建⼀个表并插⼊数据进⾏聚合查询
CREATE TABLE sales (
    product_id INT,
    sales_amount DECIMAL(10, 2)
);
INSERT INTO sales (product_id, sales_amount) VALUES (1, 100.00);
INSERT INTO sales (product_id, sales_amount) VALUES (1, 150.00);
INSERT INTO sales (product_id, sales_amount) VALUES (2, 200.00);
-- 计算总和
SELECT SUM(sales_amount) AS total_sales FROM sales;
-- 计算平均值
SELECT AVG(sales_amount) AS average_sales FROM sales;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 28 / 302

---

逻辑函数：
IF() : 如果条件为真，则返回⼀个值；否则返回另⼀个值。
CASE : 根据⼀系列条件返回值。
1. Java ⾯试指南（付费）收录的华为 OD ⾯经同学 1 ⼀⾯⾯试原题：⽤过哪些 MySQL 函数？
2. Java ⾯试指南（付费）收录的 ⼩公司⾯经合集好未来测开⾯经同学 3 测开⼀⾯⾯试原题：知道 MySQL 
的哪些函数，如 order by count()
18.说说 SQL 的隐式数据类型转换？（补充）
 
2024 年 04 ⽉ 25 ⽇增补
当⼀个整数和⼀个浮点数相加时，整数会被转换为浮点数。
当⼀个字符串和⼀个整数相加时，字符串会被转换为整数。
隐式转换会导致意想不到的结果，最好通过显式转换来规避。
实际验证结果：
-- 计算总⾏数
SELECT COUNT(*) AS total_entries FROM sales;
-- IF函数
SELECT IF(1 > 0, 'True', 'False') AS simple_if;
-- CASE表达式
SELECT CASE WHEN 1 > 0 THEN 'True' ELSE 'False' END AS case_expression;
SELECT 1 + 1.0; -- 结果为 2.0
SELECT '1' + 1; -- 结果为 2
SELECT CAST('1' AS SIGNED INTEGER) + 1; -- 结果为 2
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 29 / 302

---

1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：说说 SQL 的隐式数据类型转
换？
memo：2025 年 3 ⽉ 6 ⽇修改⾄此。
19. 说说 SQL 的语法树解析？（补充）
 
2024 年 09 ⽉ 19 ⽇增补
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 30 / 302

---

SQL 语法树解析是将 SQL 查询语句转换成抽象语法树 —— AST 的过程，是数据库引擎处理查询的第⼀步，也是防
⽌ SQL 注⼊的重要⼿段。
通常分为 3 个阶段。
第⼀个阶段，词法分析：拆解 SQL 语句，识别关键字、表名、列名等。
---这部分是帮助⼤家理解 start，⾯试中可不背---
⽐如说：
将会被拆解为：
---这部分是帮助⼤家理解 end，⾯试中可不背---
第⼆个阶段，语法分析：检查 SQL 是否符合语法规则，并构建抽象语法树。
---这部分是帮助⼤家理解 start，⾯试中可不背---
⽐如说上⾯的语句会被构建成如下的语法树：
或者这样表示：
---这部分是帮助⼤家理解 end，⾯试中可不背---
第三个阶段，语义分析：检查表、列是否存在，进⾏权限验证等。
---这部分是帮助⼤家理解 start，⾯试中可不背---
⽐如说执⾏：
SELECT id, name FROM users WHERE age > 18;
[SELECT] [id] [,] [name] [FROM] [users] [WHERE] [age] [>] [18] [;]
          SELECT
         /      \
     Columns     FROM
    /      \      |
  id      name  users
               |
             WHERE
               |
            age > 18
SELECT
 ├── COLUMNS: id, name
 ├── FROM: users
 ├── WHERE
 │    ├── CONDITION: age > 18
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 31 / 302

---

会报错：
---这部分是帮助⼤家理解 end，⾯试中可不背---
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21 抖⾳商城⼀⾯⾯试原题：sql的语法树解析
memo：2025 年 3 ⽉ 7 ⽇ 修改⾄此。再晒⼀个 offer，⼀位球友拿到了经纬恒润的实习 offer，并且直⾔⾯试了很
多场，我说超过 5 次的题⽬基本上都碰到了，啥都别说了，⾯渣逆袭 YYDS。
SELECT id, name FROM users WHERE age > 'eighteen';
ERROR: Column 'age' is INT, but 'eighteen' is STRING.
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 32 / 302

---

数据库架构
 
20.说说 MySQL 的基础架构？
 
MySQL 采⽤分层架构，主要包括连接层、服务层、和存储引擎层。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 33 / 302

---

①、连接层主要负责客户端连接的管理，包括验证⽤户身份、权限校验、连接管理等。可以通过数据库连接池来提
升连接的处理效率。
②、服务层是 MySQL 的核⼼，主要负责查询解析、优化、执⾏等操作。在这⼀层，SQL 语句会经过解析、优化器
优化，然后转发到存储引擎执⾏，并返回结果。这⼀层包含查询解析器、优化器、执⾏计划⽣成器、⽇志模块等。
③、存储引擎层负责数据的实际存储和提取。MySQL ⽀持多种存储引擎，如 InnoDB、MyISAM、Memory 等。
binlog写⼊在哪⼀层？
 
binlog 在服务层，负责记录 SQL 语句的变化。它记录了所有对数据库进⾏更改的操作，⽤于数据恢复、主从复制
等。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21  抖⾳商城⼀⾯⾯试原题：mysql分为⼏层？binlog
写⼊在哪⼀层
21.
⼀条查询语句是如何执⾏的？
 
当我们执⾏⼀条 SELECT 语句时，MySQL 并不会直接去磁盘读取数据，⽽是经过 6 个步骤来解析、优化、执⾏，
然后再返回结果。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 34 / 302

---

第⼀步，客户端发送 SQL 查询语句到 MySQL 服务器。
第⼆步，MySQL 服务器的连接器开始处理这个请求，跟客户端建⽴连接、获取权限、管理连接。
第三步，解析器对 SQL 语句进⾏解析，检查语句是否符合 SQL 语法规则，确保数据库、表和列都是存在的，并处
理 SQL 语句中的名称解析和权限验证。
第四步，优化器负责确定 SQL 语句的执⾏计划，这包括选择使⽤哪些索引，以及决定表之间的连接顺序等。
第五步，执⾏器会调⽤存储引擎的 API 来进⾏数据的读写。
第六步，存储引擎负责查询数据，并将执⾏结果返回给客户端。客户端接收到查询结果，完成这次查询请求。
1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：MySQL 执⾏语句的整个过
程了解吗？
2. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：mysql⼀条数据的查询过程
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 35 / 302

---

操作
id
旧值
新值
update
2
N
1
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：MySQL中⼀条SQL的执⾏
流程
memo：2025 年 3 ⽉ 8 ⽇修改⾄此。
22.⼀条更新语句是如何执⾏的？
 
总的来说，⼀条 UPDATE 语句的执⾏过程包括读取数据⻚、加锁解锁、事务提交、⽇志记录等多个步骤。
拿 update test set a=1 where id=2 举例来说：
在事务开始前，MySQL 需要记录undo log，⽤于事务回滚。
除了记录 undo log，存储引擎还会将更新操作写⼊ redo log，状态标记为 prepare，并确保 redo log 持久化到磁
盘。这⼀步可以保证即使系统崩溃，数据也能通过 redo log 恢复到⼀致状态。
写完 redo log 后，MySQL 会获取⾏锁，将 a 的值修改为 1，标记为脏⻚，此时数据仍然在内存的 buffer pool 
中，不会⽴即写⼊磁盘。后台线程会在适当的时候将脏⻚刷盘，以提⾼性能。
最后提交事务，redo log 中的记录被标记为 committed，⾏锁释放。
如果 MySQL 开启了 binlog，还会将更新操作记录到 binlog 中，主要⽤于主从复制。
以及数据恢复，可以结合 redo log 进⾏点对点的恢复。binlog 的写⼊通常发⽣在事务提交时，与 redo log 共同构
成“两阶段提交”，确保两者的⼀致性。
注意，redo log 的写⼊有两个阶段的提交，⼀是 binlog 写⼊之前prepare 状态的写⼊，⼆是 binlog 写⼊之后 
commit 状态的写⼊。
memo：2025 年 3 ⽉ 9 ⽇修改⾄此。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 36 / 302

---

23.说说 MySQL 的段区⻚⾏（补充）
 
2024 年 04 ⽉ 26 ⽇增补
推荐阅读：了解 MySQL的数据⾏、⾏溢出机制吗？
MySQL 是以表的形式存储数据的，⽽表空间的结构则由段、区、⻚、⾏组成。
①、段：表空间由多个段组成，常⻅的段有数据段、索引段、回滚段等。
创建索引时会创建两个段，数据段和索引段，数据段⽤来存储叶⼦节点中的数据；索引段⽤来存储⾮叶⼦节点的数
据。
回滚段包含了事务执⾏过程中⽤于数据回滚的旧数据。
②、区：段由⼀个或多个区组成，区是⼀组连续的⻚，通常包含 64 个连续的⻚，也就是 1M 的数据。
使⽤区⽽⾮单独的⻚进⾏数据分配可以优化磁盘操作，减少磁盘寻道时间，特别是在⼤量数据进⾏读写时。
③、⻚：⻚是 InnoDB 存储数据的基本单元，标准⼤⼩为 16 KB，索引树上的⼀个节点就是⼀个⻚。
也就意味着数据库每次读写都是以 16 KB 为单位的，⼀次最少从磁盘中读取 16KB 的数据到内存，⼀次最少写⼊ 
16KB 的数据到磁盘。
④、⾏：InnoDB 采⽤⾏存储⽅式，意味着数据按照⾏进⾏组织和管理，⾏数据可能有多个格式，⽐如说 
COMPACT、REDUNDANT、DYNAMIC 等。
MySQL 8.0 默认的⾏格式是 DYNAMIC，由COMPACT 演变⽽来，意味着这些数据如果超过了⻚内联存储的限制，
则会被存储在溢出⻚中。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 37 / 302

---

可以通过 show table status like '%article%' 查看⾏格式。
    
存储引擎
 
24.
MySQL 有哪些常⻅存储引擎？
 
MySQL ⽀持多种存储引擎，常⻅的有 MyISAM、InnoDB、MEMORY 等。
---这部分是帮助⼤家理解 start，⾯试中可不背---
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 38 / 302

---

功能
InnoDB
MyISAM
MEMORY
⽀持事务
Yes
No
No
⽀持全⽂索引
Yes
Yes
No
⽀持 B+树索引
Yes
Yes
Yes
⽀持哈希索引
Yes
No
Yes
⽀持外键
Yes
No
No
我来做⼀个表格对⽐：
---这部分是帮助⼤家理解 end，⾯试中可不背---
除此之外，我还了解到：
①、MySQL 5.5 之前，默认存储引擎是 MyISAM，5.5 之后是 InnoDB。
②、InnoDB ⽀持的哈希索引是⾃适应的，不能⼈为⼲预。
③、InnoDB 从 MySQL 5.6 开始，⽀持全⽂索引。
④、InnoDB 的最⼩表空间略⼩于 10M，最⼤表空间取决于⻚⾯⼤⼩。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 39 / 302

---

如何切换 MySQL 的数据引擎？
 
可以通过 alter table 语句来切换 MySQL 的数据引擎。
不过不建议，应该提前设计好到底⽤哪⼀种存储引擎。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：MySQL ⽀持哪些存储
引擎?
2. Java ⾯试指南（付费）收录的⽤友⾯试原题：innodb 引擎和 hash 引擎有什么区别
3. Java ⾯试指南（付费）收录的国企零碎⾯经同学 9 ⾯试原题：MySQL 的存储引擎
4. Java ⾯试指南（付费）收录的京东同学 4 云实习⾯试原题：mysql的数据引擎有哪些, 区别
(innodb,MyISAM,Memory)
5. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：存储引擎介绍
memo：2025 年 3 ⽉ 10 ⽇修改⾄此。
25.存储引擎应该怎么选择？
 
⼤多数情况下，使⽤默认的 InnoDB 就可以了，InnoDB 可以提供事务、⾏级锁、外键、B+ 树索引等能⼒。
MyISAM 适合读多写少的场景。
MEMORY 适合临时表，数据量不⼤的情况。因为数据都存放在内存，所以速度⾮常快。
1. Java ⾯试指南（付费）收录的快⼿同学 2 ⼀⾯⾯试原题：MySQL的InnoDB特点？为什么⽤B+树？⽽不
是B树，区别？
26.InnoDB 和 MyISAM 主要有什么区别？
 
ALTER TABLE your_table_name ENGINE=InnoDB;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 40 / 302

---

InnoDB 和 MyISAM 的最⼤区别在于事务⽀持和锁机制。InnoDB ⽀持事务、⾏级锁，适合⼤多数业务系统；⽽ 
MyISAM 不⽀持事务，⽤的是表锁，查询快但写⼊性能差，适合读多写少的场景。
另外，从存储结构上来说，MyISAM ⽤三种格式的⽂件来存储，.frm ⽂件存储表的定义；.MYD 存储数据；.MYI 存
储索引；⽽ InnoDB ⽤两种格式的⽂件来存储，.frm ⽂件存储表的定义；.ibd 存储数据和索引。
从索引类型上来说，MyISAM 为⾮聚簇索引，索引和数据分开存储，索引保存的是数据⽂件的指针。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 41 / 302

---

InnoDB 为聚簇索引，索引和数据不分开。
更细微的层⾯上来讲，MyISAM 不⽀持外键，可以没有主键，表的具体⾏数存储在表的属性中，查询时可以直接返
回；InnoDB ⽀持外键，必须有主键，具体⾏数需要扫描整个表才能返回，有索引的情况下会扫描索引。
InnoDB的内存结构了解吗？
 
2025 年 04 ⽉ 04 ⽇增补
InnoDB 的内存区域主要有两块，buffer pool 和 log buffer。
buffer pool ⽤于缓存数据⻚和索引⻚，提升读写性能；log buffer ⽤于缓存 redo log，提升写⼊性能。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 42 / 302

---

数据⻚的结构了解吗？
 
InnoDB 的数据⻚由 7 部分组成，其中⽂件头、⻚头和⽂件尾的⼤⼩是固定的，分别为 38、56 和 8 个字节，⽤来
标记该⻚的⼀些信息。⾏记录、空闲空间和⻚⽬录的⼤⼩是动态的，为实际的⾏记录存储空间。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 43 / 302

---

名称
中⽂名
⼤⼩（单位：B）
描述
File Header
⽂件头部
38
⻚的⼀些通⽤信息
Page Header
⻚⾯头部
56
数据⻚专有的⼀些信息
Infimum + Supermum
最⼩记录和最⼤记录
26
两个虚拟的⾏记录
User Records
⽤户真实记录
不确定
实际存储的⾏记录内容
Free Space
空闲空间
不确定
⻚中尚未使⽤的空间
Page Directory
⻚⾯⽬录
不确定
⻚中的某些记录的相对位置
File Trailer
⽂件尾部
8
校验⻚是否完整
来个表格总结下：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 44 / 302

---

真实的记录会按照指定的⾏格式存储到 User Records 中。
每个数据⻚的 File Header
都有⼀个上⼀⻚和下⼀⻚的编号，所有的数据⻚会形成⼀个双向链表。
在 InnoDB 中，默认的⻚⼤⼩是 16KB。可以通过 show variables like 'innodb_page_size'; 查看。
推荐阅读：MySQL之数据⻚结构
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：MyISAM 和 InnoDB 
的区别有哪些？
2. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：mysql存储的数据都是什么样的？
memo：2025 年 3 ⽉ 11 ⽇修改⾄此。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 45 / 302

---

27. InnoDB 的 Buffer Pool了解吗？（补充）
 
2024 年 11 ⽉ 04 ⽇增补
Buffer Pool 是 InnoDB 存储引擎中的⼀个内存缓冲区，它会将经常使⽤的数据⻚、索引⻚加载进内存，读的时候
先查询 Buffer Pool，如果命中就不⽤访问磁盘了。
如果没有命中，就从磁盘读取，并加载到 Buffer Pool，此时可能会触发⻚淘汰，将不常⽤的⻚移出 Buffer Pool。
写操作时不会直接写⼊磁盘，⽽是先修改内存中的⻚，此时⻚被标记为脏⻚，后台线程会定期将脏⻚刷新到磁盘。
Buffer Pool 可以显著减少磁盘的读写次数，从⽽提升 MySQL 的读写性能。
Buffer Pool 的默认⼤⼩是多少？
 
我本机上 InnoDB 的 Buffer Pool 默认⼤⼩是 128MB。
SHOW VARIABLES LIKE 'innodb_buffer_pool_size';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 46 / 302

---

另外，在具有 1GB-4GB RAM 的系统上，默认值为系统 RAM 的 25%；在具有超过 4GB RAM 的系统上，默认值为
系统 RAM 的 50%，但不超过 4GB。
InnoDB 对 LRU 算法的优化了解吗？
 
了解，InnoDB 对 LRU 算法进⾏了改良，最近访问的数据并不直接放到 LRU 链表的头部，⽽是放在⼀个叫 
midpoiont 的位置。默认情况下，midpoint 位于 LRU 列表的 5/8 处。
⽐如 Buffer Pool 有 100 ⻚，新⻚插⼊的位置⼤概是在第 80 ⻚；当⻚数据被频繁访问后，再将其移动到 young 
区，这样做的好处是热点⻚能⻓时间保留在内存中，不容易被挤出去。
----这部分是帮助⼤家理解 start，⾯试中可不背----
可以通过 innodb_old_blocks_pct 参数来调整 Buffer Pool 中 old 和 young 区的⽐例；通过 
innodb_old_blocks_time 参数来调整⻚在 young 区的停留时间。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 47 / 302

---

默认情况下，LRU 链表中 old 区占 37%；同⼀⻚再次访问提升的最⼩时间间隔是 1000 毫秒。
也就是说，如果某⻚在 1 秒内被多次访问，只会计算⼀次，不会⽴刻升级为热点⻚，防⽌短时间批量访问导致缓存
污染。
----这部分是帮助⼤家理解 end，⾯试中可不背----
1. Java ⾯试指南（付费）收录的美团⾯经同学 15 点评后端技术⾯试原题：说说 bufferpool
memo：2025 年 3 ⽉ 12 ⽇修改⾄此。继续给⼤家⼀个喜报，今天有球友报喜说社招拿到了京东和美团的 offer，
后续补充说滴滴也过了，我只能说太强了呀。
⽇志
 
28.
MySQL ⽇志⽂件有哪些？
 
有 6 ⼤类，其中错误⽇志⽤于问题诊断，慢查询⽇志⽤于 SQL 性能分析，general log ⽤于记录所有的 SQL 语
句，binlog ⽤于主从复制和数据恢复，redo log ⽤于保证事务持久性，undo log ⽤于事务回滚和 MVCC。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 48 / 302

---

----这部分是帮助⼤家理解 start，⾯试中可不背----
①、错误⽇志（Error Log）：记录 MySQL 服务器启动、运⾏或停⽌时出现的问题。
②、慢查询⽇志（Slow Query Log）：记录执⾏时间超过 long_query_time 值的所有 SQL 语句。这个时间值是可
配置的，默认情况下，慢查询⽇志功能是关闭的。
③、⼀般查询⽇志（General Query Log）：记录 MySQL 服务器的启动关闭信息，客户端的连接信息，以及更
新、查询的 SQL 语句等。
④、⼆进制⽇志（Binary Log）：记录所有修改数据库状态的 SQL 语句，以及每个语句的执⾏时间，如 INSERT、
UPDATE、DELETE 等，但不包括 SELECT 和 SHOW 这类的操作。
⑤、重做⽇志（Redo Log）：记录对于 InnoDB 表的每个写操作，不是 SQL 级别的，⽽是物理级别的，主要⽤于
崩溃恢复。
⑥、回滚⽇志（Undo Log，或者叫事务⽇志）：记录数据被修改前的值，⽤于事务的回滚。
----这部分是帮助⼤家理解 end，⾯试中可不背----
请重点说说 binlog？
 
推荐阅读：带你了解 MySQL Binlog 不为⼈知的秘密
binlog 是⼀种物理⽇志，会在磁盘上记录数据库的所有修改操作。
如果误删了数据，就可以使⽤ binlog 进⾏回退到误删之前的状态。
如果要搭建主从复制，就可以让从库定时读取主库的 binlog。
# 步骤1：恢复全量备份
mysql -u root -p < full_backup.sql
# 步骤2：应⽤Binlog到指定时间点
mysqlbinlog --start-datetime="2025-03-13 14:00:00" --stop-datetime="2025-03-13 15:00:00" 
binlog.000001 | mysql -u root -p
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 49 / 302

---

MySQL 提供了三种格式的 binlog：Statement、Row 和 Mixed，分别对应 SQL 语句级别、⾏级别和混合级别，默
认为⾏级别。
从后缀名上来看，binlog ⽂件分为两类：以 .index 结尾的索引⽂件，以 .00000* 结尾的⼆进制⽇志⽂件。
binlog 默认是没有启⽤的。
⽣产环境中是⼀定要启⽤的，可以通过在 my.cnf ⽂件中配置 log_bin 参数，以启⽤ binlog。
binlog 的配置参数都了解哪些？
 
log_bin = mysql-bin ⽤于启⽤ binlog，这样就可以在 MySQL 的数据⽬录中找到 db-bin.000001、db-
bin.000002 等⽇志⽂件。
log_bin = mysql-bin #开启binlog
#mysql-bin.*⽇志⽂件最⼤字节（单位：字节）
#设置最⼤100MB
max_binlog_size=104857600
#设置了只保留7天BINLOG（单位：天）
expire_logs_days = 7
#binlog⽇志只记录指定库的更新
#binlog-do-db=db_name
#binlog⽇志不记录指定库的更新
#binlog-ignore-db=db_name
#写缓冲多少次，刷⼀次磁盘，默认0
sync_binlog=0
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 50 / 302

---

max_binlog_size=104857600 ⽤于设置每个 binlog ⽂件的⼤⼩，不建议设置太⼤，⽹络传送起来⽐较麻烦。
当 binlog ⽂件达到 max_binlog_size 时，MySQL 会关闭当前⽂件并创建⼀个新的 binlog ⽂件。
expire_logs_days = 7 ⽤于设置 binlog ⽂件的⾃动过期时间为 7 天。过期的 binlog ⽂件会被⾃动删除。防⽌
⻓时间累积的 binlog ⽂件占⽤过多存储空间，技术派实战项⽬所在的项⽬是丐版服务器，所以这个配置很重要。
binlog-do-db=db_name ，指定哪些数据库表的更新应该被记录。
binlog-ignore-db=db_name ，指定忽略哪些数据库表的更新。
sync_binlog=0 ，设置每多少次 binlog 写操作会触发⼀次磁盘同步操作。默认值为 0，表示 MySQL 不会主动触
发同步操作，⽽是依赖操作系统的磁盘缓存策略。
即当执⾏写操作时，数据会先写⼊缓存，当缓存区满了再由操作系统将数据⼀次性刷⼊磁盘。
如果设置为 1，表示每次 binlog 写操作后都会同步到磁盘，虽然可以保证数据能够及时写⼊磁盘，但会降低性
能。
可以通过 show variables like '%log_bin%'; 查看 binlog 是否开启。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 51 / 302

---

有了binlog为什么还要undolog redolog？
 
binlog 属于 Server 层，与存储引擎⽆关，⽆法直接操作物理数据⻚。⽽ redo log 和 undo log 是 InnoDB 存储引
擎实现 ACID 的基⽯。
binlog 关注的是逻辑变更的全局记录；redo log ⽤于确保物理变更的持久性，确保事务最终能够刷盘成功；undo 
log 是逻辑逆向操作⽇志，记录的是旧值，⽅便恢复到事务开始前的状态。
另外⼀种回答⽅式。
binlog 会记录整个 SQL 或⾏变化；redo log 是为了恢复“已提交但未刷盘”的数据，undo log 是为了撤销未提交的
事务。
以⼀次事务更新为例：
事务开始的时候会⽣成 undo log，记录更新前的数据，⽐如原值是 18：
修改数据的时候，会将数据写⼊到 redo log。
⽐如数据⻚ page_id=123 上，id=1 的⽤户被更新为 age=26：
等事务提交的时候，redo log 刷盘，binlog 刷盘。
binlog 写完之后，redo log 的状态会变为 commit：
binlog 如果是 Statement 格式，会记录⼀条 SQL 语句：
binlog 如果是 Row 格式，会记录：
# 开启事务
BEGIN;
# 更新数据
UPDATE users SET age = age + 1 WHERE id = 1;
# 提交事务
COMMIT;
undo log: id=1, age=18
redo log (prepare):
page_id=123, offset=0x40, before=18, after=26
redo log (commit):
page_id=123, offset=0x40, before=18, after=26
UPDATE users SET age = age + 1 WHERE id = 1;
表：users
before: id=1, age=18
after:  id=1, age=26
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 52 / 302

---

随后，后台线程会将 redo log 中的变更异步刷新到磁盘。
memo：2025 年 3 ⽉ 13 ⽇修改⾄此。有球友报喜，字节⼆⾯过了，找暑期顺利的不可思议，⼋股直接吟唱⾯
渣。
说说 redo log 的⼯作机制？
 
当事务启动时，MySQL 会为该事务分配⼀个唯⼀标识符。
在事务执⾏过程中，每次对数据进⾏修改，MySQL 都会⽣成⼀条 Redo Log，记录修改前后的数据状态。
这些 Redo Log ⾸先会被写⼊内存中的 Redo Log Buffer。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 53 / 302

---

当事务提交时，MySQL 再将 Redo Log Buffer 中的记录刷新到磁盘上的 Redo Log ⽂件中。
只有当 Redo Log 成功写⼊磁盘，事务才算真正提交成功。
当 MySQL 崩溃重启时，会先检查 Redo Log。对于已提交的事务，MySQL 会重放 Redo Log 中的记录。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 54 / 302

---

对于未提交的事务，MySQL 会通过 Undo Log 回滚这些修改，确保数据恢复到崩溃前的⼀致性状态。
Redo Log 是循环使⽤的，当⽂件写满后会覆盖最早的记录。
为避免覆盖未持久化的记录，MySQL 会定期执⾏ CheckPoint 操作，将内存中的数据⻚刷新到磁盘，并记录 
CheckPoint 点。
重启时，MySQL 只会重放 CheckPoint 之后的 Redo Log，从⽽提⾼恢复效率。
redo log ⽂件的⼤⼩是固定的吗？
 
redo log ⽂件是固定⼤⼩的，通常配置为⼀组⽂件，使⽤环形⽅式写⼊，旧的⽇志会在空间需要时被覆盖。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 55 / 302

---

命名⽅式为 ib_logfile0、iblogfile1、、、iblogfilen 。默认 2 个⽂件，每个⽂件⼤⼩为 48MB。
可以通过 show variables like 'innodb_log_file_size'; 查看 redo log ⽂件的⼤⼩；通过 show 
variables like 'innodb_log_files_in_group'; 查看 redo log ⽂件的数量。
说说 WAL？
 
WAL——Write-Ahead Logging。
预写⽇志是 InnoDB 实现事务持久化的核⼼机制，它的思想是：先写⽇志再刷磁盘。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 56 / 302

---

即在修改数据⻚之前，先将修改记录写⼊ Redo Log。
这样的话，即使数据⻚尚未写⼊磁盘，系统崩溃时也能通过 Redo Log 恢复数据。
----这部分是帮助⼤家理解 start，⾯试中可不背----
解释⼀下为什么需要 WAL：
数据最终是要写⼊磁盘的，但磁盘 IO 很慢；
如果每次更新都⽴刻把数据⻚刷盘，性能很差；
如果还没写⼊磁盘就宕机，事务会丢失。
WAL 的好处是更新时不直接写数据⻚，⽽是先写⼀份变更记录到 redo log，后台再慢慢把真正的数据⻚刷盘，⼀
举多得。
----这部分是帮助⼤家理解 end，⾯试中可不背----
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：MySQL 中的 bin log 的作⽤是什
么？
2. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：说说 MySQL 的三⼤⽇志？
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 57 / 302

---

3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21  抖⾳商城⼀⾯⾯试原题：redolog undolog 
binlog，有了binlog为什么还要undolog redolog，redolog的⼯作机制，说说 WAL
memo：2025 年 3 ⽉ 14 ⽇修改⾄此。今天修改简历的时候，碰到⼀位⽐赛经历⾮常丰富的球友，⼤家在校期间
如果有时间，也可以冲⼀下。
29.binlog 和 redo log 有什么区别？
 
binlog 由 MySQL 的 Server 层实现，与存储引擎⽆关；redo log 由 InnoDB 存储引擎实现。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 58 / 302

---

binlog 记录的是逻辑⽇志，包括原始的 SQL 语句或者⾏数据变化，例如“将 id=2 这⾏数据的 age 字段+1”。
redo log 记录物理⽇志，即数据⻚的具体修改，例如“将 page_id=123 上 offset=0x40 的数据从 18 修改为 26”。
binlog 是追加写⼊的，⽂件写满后会新建⽂件继续写⼊，不会覆盖历史⽇志，保存的是全量操作记录；redo log 
是循环写⼊的，空间是固定的，写满后会覆盖旧的⽇志，仅保存未刷盘的脏⻚⽇志，已持久化的数据会被清除。
另外，为保证两种⽇志的⼀致性，innodb 采⽤了两阶段提交策略，redo log 在事务执⾏过程中持续写⼊，并在事
务提交前进⼊ prepare 状态；binlog 在事务提交的最后阶段写⼊，之后 redo log 会被标记为 commit 状态。
可以通过回放 binlog 实现数据同步或者恢复到指定时间点；redo log ⽤来确保事务提交后即使系统宕机，数据仍
然可以通过重放 redo log 恢复。
1. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：redo log、bin log
30.
为什么要两阶段提交呢？
 
为了保证 redo log 和 binlog 中的数据⼀致性，防⽌主从复制和事务状态不⼀致。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 59 / 302

---

为什么 2PC 能保证 redo log 和 binlog 的强⼀致性？
 
假如 MySQL 在预写 redo log 之后、写⼊ binlog 之前崩溃。那么 MySQL 重启后 InnoDB 会回滚该事务，因为 
redo log 不是提交状态。并且由于 binlog 中没有写⼊数据，所以从库也不会有该事务的数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 60 / 302

---

假如 MySQL 在写⼊ binlog 之后、redo log 提交之前崩溃。那么 MySQL 重启后 InnoDB 会提交该事务，因为 
redo log 是完整的 prepare 状态。并且由于 binlog 中有写⼊数据，所以从库也会同步到该事务的数据。
伪代码如下所示：
// 事务开始
begin;
// try
{
    // 执⾏ SQL
    execute SQL;
    // 写⼊ redo log 并标记为 prepare
    write redo log prepare xid;
    // 写⼊ binlog
    write binlog xid sql;
    // 提交 redo log
    commit redo log xid;
}
// catch
{
    // 回滚 redo log
    innodb rollback redo log xid;
}
// 事务结束
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 61 / 302

---

XID 了解吗？
 
XID 是 binlog 中⽤来标识事务提交的唯⼀标识符。
在事务提交时，会写⼊⼀个 XID_EVENT 到 binlog，表示这个事务真正完成了。
它不仅⽤于主从复制中事务完整性的判断，也在崩溃恢复中对 redo log 和 binlog 的⼀致性校验起到关键作⽤。
XID 可以帮助 MySQL 判断哪些 redo log 是已提交的，哪些是未提交需要回滚的，是两阶段提交机制中⾮常关键的
⼀环。
memo：2025 年 3 ⽉ 16 ⽇修改⾄此。
31.
redo log 的写⼊过程了解吗？
 
InnoDB 会先将 Redo Log 写⼊内存中的 Redo Log Buffer，之后再以⼀定的频率刷⼊到磁盘的 Redo Log File 中。
end;
  Log_name         | Pos  | Event_type     | Server_id | End_log_pos | Info      
| mysql-bin.000003 | 2005 | Gtid           |   1013307 |        2070 | SET 
@@SESSION.GTID_NEXT= 'f971d5f1-d450-11ec-9e7b-5254000a56df:11'                 |
| mysql-bin.000003 | 2070 | Query          |   1013307 |        2142 | BEGIN             
                                                                 |
| mysql-bin.000003 | 2142 | Table_map      |   1013307 |        2187 | table_id: 109 
(test.t1)                                                            |
| mysql-bin.000003 | 2187 | Write_rows     |   1013307 |        2227 | table_id: 109 
flags: STMT_END_F                                                    |
| mysql-bin.000003 | 2227 | Xid            |   1013307 |        2258 | COMMIT /* xid=121 
*/      
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 62 / 302

---

哪些场景会触发 redo log 的刷盘动作？
 
⽐如说 Redo Log Buffer 的空间不⾜时，事务提交时，触发 Checkpoint 时，后台线程定期刷盘时。
不过，Redo Log Buffer 刷盘到 Redo Log File 还会涉及到操作系统的磁盘缓存策略，可能不会⽴即刷盘，⽽是等
待⼀定时间后才刷盘。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 63 / 302

---

innodb_flush_log_at_trx_commit 参数你了解多少？
 
innodb_flush_log_at_trx_commit 参数是⽤来控制事务提交时，Redo Log 的刷盘策略，⼀共有三种。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 64 / 302

---

0 表示事务提交时不刷盘，⽽是交给后台线程每隔 1 秒执⾏⼀次。这种⽅式性能最好，但是在 MySQL 宕机时可能
会丢失⼀秒内的事务。
1 表示事务提交时会⽴即刷盘，确保事务提交后数据就持久化到磁盘。这种⽅式是最安全的，也是 InnoDB 的默认
值。
2 表示事务提交时只把 Redo Log Buffer 写⼊到 Page Cache，由操作系统决定什么时候刷盘。操作系统宕机时，
可能会丢失⼀部分数据。
⼀个没有提交事务的 redo log，会不会刷盘？
 
InnoDB 有⼀个后台线程，每隔 1 秒会把 Redo Log Buffer 中的⽇志写⼊到⽂件系统的缓存中，然后调⽤刷盘操
作。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 65 / 302

---

因此，⼀个没有提交事务的 Redo Log 也可能会被刷新到磁盘中。
另外，如果当 Redo Log Buffer 占⽤的空间即将达到 innodb_log_buffer_size 的⼀半时，也会触发刷盘操作。
memo：2025 年 3 ⽉ 17 ⽇修改⾄此。已经有球友发来喜报，暑期实习拿到恒⽣电⼦的暑期实习了。
Redo Log Buffer 是顺序写还是随机写？
 
MySQL 在启动后会向操作系统申请⼀块连续的内存空间作为 Redo Log Buffer，并将其分为若⼲个连续的 Redo 
Log Block。
那为了提⾼写⼊效率，Redo Log Buffer 采⽤了顺序写⼊的⽅式，会先往前⾯的 Redo Log Block 中写⼊，当写满
后再往后⾯的 Block 中写⼊。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 66 / 302

---

于此同时，InnoDB 还提供了⼀个全局变量 buf_free，来控制后续的 redo log 记录应该写⼊到 block 中的哪个位
置。
buf_next_to_write 了解吗？
 
buf_next_to_write 指向 Redo Log Buffer 中下⼀次需要写⼊硬盘的起始位置。
⽽ buf_free 指向的是 Redo Log Buffer 中空闲区域的起始位置。
了解 MTR 吗？
 
Mini Transaction 是 InnoDB 内部⽤于操作数据⻚的原⼦操作单元。
mtr_t mtr;
mtr_start(&mtr);
// 1. 加锁
// 对待访问的index加锁
mtr_s_lock(rw_lock_t, mtr);
mtr_x_lock(rw_lock_t, mtr);
// 对待读写的page加锁
mtr_memo_push(mtr, buf_block_t, MTR_MEMO_PAGE_S_FIX);
mtr_memo_push(mtr, buf_block_t, MTR_MEMO_PAGE_X_FIX);
// 2. 访问或修改page
btr_cur_search_to_nth_level
btr_cur_optimistic_insert
// 3. 为修改操作⽣成redo
mlog_open
mlog_write_initial_log_record_fast
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 67 / 302

---

多个事务的 Redo Log 会以 MTR 为单位交替写⼊到 Redo Log Buffer 中，假如事务 1 和事务 2 均有两个 MTR，⼀
旦某个 MTR 结束，就会将其⽣成的若⼲条 Redo Log 记录顺序写⼊到 Redo Log Buffer 中。
也就是说，⼀个 MTR 会包含⼀组 Redo Log 记录，是 MySQL 崩溃后恢复事务的最⼩执⾏单元。
mlog_close
// 4. 持久化redo，解锁
mtr_commit(&mtr);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 68 / 302

---

Redo Log Block 的结构了解吗？
 
Redo Log Block 由⽇志头、⽇志体和⽇志尾组成，⼀共占⽤ 512 个字节，其中⽇志头占⽤ 12 个字节，⽇志尾占
⽤ 4 个字节，剩余的 496 个字节⽤于存储⽇志体。
⽇志头包含了当前 Block 的序列号、第⼀条⽇志的序列号、类型等信息。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 69 / 302

---

字段
作⽤
LOG_BLOCK_HDR_NO
当前 Block 的序号，假如把 Redo Log Buffer 看成⼀个数组，那么
LOG_BLOCK_HDR_NO 就相当于 Block 在 Buffer 中的下标。
LOG_BLOCK_HDR_DATA_LEN
Block 已使⽤的字节数，初始值为 12，也就是⽇志头的⻓度；如果⽇志
体被写满，值增⻓为 512。
LOG_BLOCK_FIRST_REC_GROUP
该 Block 中第⼀个 MTR 起始处的偏移量
LOG_BLOCK_CHECKPOINT_NO
Block 最后被写⼊时的checkpoint
⽇志尾主要存储的是 LOG_BLOCK_CHECKSUM，也就是 Block 的校验和，主要⽤于判断 Block 是否完整。
Redo Log Block 为什么设计成 512 字节？
 
因为机械硬盘的物理扇区⼤⼩通常为 512 字节，Redo Log Block 也设计为同样的⼤⼩，就可以确保每次写⼊都是
整数个扇区，减少对⻬开销。
⽐如说操作系统的⻚缓存默认为 4KB，8 个 Redo Log Block 就可以组合成⼀个⻚缓存单元，从⽽提升 Redo Log 
Buffer 的写⼊效率。
memo：2025 年 3 ⽉ 18 ⽇修改⾄此。
LSN 了解吗？
 
Log Sequence Number 是⼀个 8 字节的单调递增整数，⽤来标识事务写⼊ redo log 的字节总量，存在于 redo 
log、数据⻚头部和 checkpoint 中。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 70 / 302

---

----这部分是帮助⼤家理解 start，⾯试中可不背----
MySQL 在第⼀次启动时，LSN 的初始值并不为 0，⽽是 8704；当 MySQL 再次启动时，会继续使⽤上⼀次服务停
⽌时的 LSN。
在计算 LSN 的增量时，不仅需要考虑 log block body 的⼤⼩，还需要考虑 log block header 和 log block tail 中部
分字节数。
⽐如说在上图中，事务 3 的 MTR 总量为 300 字节，那么写⼊到 Redo Log Buffer 中的 LSN 会增⻓为 8704 + 300 
+ 12 = 9016。
假如事务 4 的 MTR 总量为 900 字节，那么再次写⼊到 Redo Log Buffer 中的 LSN 会增⻓为 9016 + 900 + 12*2 + 
4*2 = 9948。
2 个 12 字节的 log block header + 2 个 4 字节的 log block tail。
----这部分是帮助⼤家理解 end，⾯试中可不背----
核⼼作⽤有三个：
第⼀，redo log 按照 LSN 递增顺序记录所有数据的修改操作。LSN 的递增量等于每次写⼊⽇志的字节数。
第⼆，InnoDB 的每个数据⻚头部中，都会记录该⻚最后⼀次刷新到磁盘时的 LSN。如果数据⻚的 LSN ⼩于 redo 
log 的 LSN，说明该⻚需要从⽇志中恢复；否则说明该⻚已更新。
第三，checkpoint 通过 LSN 记录已刷新到磁盘的数据⻚位置，减少恢复时需要处理的⽇志。
----这部分是帮助⼤家理解 start，⾯试中可不背----
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 71 / 302

---

场景
LSN 的作⽤
 redo log 记录
每条 redo log 对应⼀个唯⼀的 LSN
 数据⻚刷盘
每个数据⻚会记录当前刷盘时的 LSN（FIL_PAGE_LSN）
 Checkpoint
表示“脏⻚已经刷盘，可以释放 redo”的安全点
 崩溃恢复
重启时从 checkpoint LSN 开始重放 redo log
可以通过 show engine innodb status; 查看当前的 LSN 信息。
Log sequence number：当前系统最⼤ LSN（已⽣成的⽇志总量）。
Log flushed up to：已写⼊磁盘的 redo log LSN。
Pages flushed up to：已刷新到数据⻚的 LSN。
Last checkpoint at：最后⼀次检查点的 LSN，表示已持久化的数据状态。
----这部分是帮助⼤家理解 end，⾯试中可不背----
memo：2025 年 3 ⽉ 19 ⽇修改⾄此。今天有读者问怎么付费购买纸质版⾯渣逆袭，说看到⽹友有这个，好羡慕
啊。说实话，第⼀眼看到这个封⾯，真的觉得挺惊艳（虽然是我设计的）。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 72 / 302

---

Checkpoint 了解多少？
 
Checkpoint 是 InnoDB 为了保证事务持久性和回收 redo log 空间的⼀种机制。
它的作⽤是在合适的时机将部分脏⻚刷⼊磁盘，⽐如说 buffer pool 的容量不⾜时。并记录当前 LSN 为 
Checkpoint LSN，表示这个位置之前的 redo log file 已经安全，可以被覆盖了。
MySQL 崩溃恢复时只需要从 Checkpoint 之后开始恢复 redo log 就可以了，这样可以最⼤程度减少恢复所花费的
时间。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 73 / 302

---

redo log file 的写⼊是循环的，其中有两个标记位置⾮常重要，也就是 Checkpoint 和 write pos。
write pos 是 redo log 当前写⼊的位置，Checkpoint 是可以被覆盖的位置。
当 write pos 追上 Checkpoint 时，表示 redo log ⽇志已经写满。这时候就要暂停写⼊并强制刷盘，释放可覆写的
⽇志空间。
关于redo log 的调优参数了解多少？
 
如果是⾼并发写⼊的电商系统，可以最⼤化写⼊吞吐量，容忍秒级数据丢失的⻛险。
如果是⾦融交易系统，需要保证数据零丢失，接受较低的吞吐量。
innodb_flush_log_at_trx_commit = 2
sync_binlog = 1000
innodb_redo_log_capacity = 64G
innodb_io_capacity = 5000
innodb_lru_scan_depth = 512
innodb_log_buffer_size = 256M
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 74 / 302

---

参数名
控制内容
影响点
innodb_log_file_size
每个 redo log ⽂件⼤⼩
总 redo 空间、恢复时间
innodb_log_files_in_group
redo log ⽂件个数
配合⽂件⼤⼩决定总容量
innodb_log_buffer_size
redo log buffer 缓冲区⼤⼩
是否频繁刷盘、写⼊性能
innodb_flush_log_at_trx_commit
redo 刷盘策略
安全性 vs TPS
innodb_max_dirty_pages_pct
脏⻚⽐例阈值
何时触发刷盘 / Checkpoint
innodb_io_capacity
后台刷盘速度
限制 checkpoint 刷盘压⼒
核⼼参数⼀览表：
总结：
对数据⼀致性要求⾼的场景，如⾦融交易使⽤innodb_flush_log_at_trx_commit=1 ，对写⼊吞吐量敏感的
场景，如⽇志采集可以使⽤ =2 或 =0，需要结合 sync_binlog 参数
sync_binlog 参数控制 binlog 的刷盘策略，可以设置为 0、1、N，0 表示依赖系统刷盘，1 表示每次事务提交
都刷盘（推荐与 innodb_flush_log_at_trx_commit=1 搭配），N=1000 表示累计 1000 次事务后刷盘
innodb_redo_log_capacity 动态调整 Redo Log 总容量，可以根据业务负载情况调整，建议设置为 1 ⼩时写
⼊量的峰值（如每秒 10MB 写⼊则设为 36GB）
innodb_io_capacity 定义 InnoDB 后台线程的每秒 I/O 操作上限，直接影响脏⻚刷新速率；机械硬盘建议 
200-500，SSD 建议 1000-2000，NVMe SSD 可设为 5000+
innodb_lru_scan_depth 控制每个缓冲池实例中 LRU 列表的扫描深度，决定每秒可刷新的脏⻚数量，默认值 
1024 适⽤于多数场景，I/O 密集型负载可适当降低（如 512），减少 CPU 开销。
memo：2025 年 3 ⽉ 20 ⽇修改⾄此。有球友报喜说拿到了滴滴的测开实习 offer，恭喜恭喜！
SQL 优化
 
innodb_flush_log_at_trx_commit = 1
sync_binlog = 1
innodb_redo_log_capacity = 32G
innodb_io_capacity = 2000
innodb_lru_scan_depth = 1024
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 75 / 302

---

32.
什么是慢 SQL？
 
推荐阅读：慢 SQL 优化⼀点⼩思路
MySQL 中有⼀个叫 long_query_time 的参数，原则上执⾏时间超过该参数值的 SQL 就是慢 SQL，会被记录到慢查
询⽇志中。
----这部分是帮助⼤家理解 start，⾯试中可不背----
可通过 show variables like 'long_query_time'; 查看当前的 long_query_time 的参数值。
----这部分是帮助⼤家理解 end，⾯试中可不背----
SQL 的执⾏过程了解吗？
 
了解。
SQL 的执⾏过程⼤致可以分为六个阶段：连接管理、语法解析、语义分析、查询优化、执⾏器调度、存储引擎读写
等。Server 层负责理解和规划 SQL 怎么执⾏，存储引擎层负责数据的真正读写。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 76 / 302

---

----这部分是帮助⼤家理解 start，⾯试中可不背----
来详细拆解⼀下：
1. 客户端发送 SQL 语句给 MySQL 服务器。
2. 如果查询缓存打开则会优先查询缓存，缓存中有对应的结果就直接返回。不过，MySQL 8.0 已经移除了查询
缓存。这部分的功能正在被 Redis 等缓存中间件取代。
3. 分析器对 SQL 语句进⾏语法分析，判断是否有语法错误。
4. 搞清楚 SQL 语句要⼲嘛后，MySQL 会通过优化器⽣成执⾏计划。
5. 执⾏器调⽤存储引擎的接⼝，执⾏ SQL 语句。
SQL 执⾏过程中，优化器通过成本计算预估出执⾏效率最⾼的⽅式，基本的预估维度为：
IO 成本：从磁盘读取数据到内存的开销。
CPU 成本：CPU 处理内存中数据的开销。
基于这两个维度，可以得出影响 SQL 执⾏效率的因素有：
①、IO 成本，数据量越⼤，IO 成本越⾼。所以要尽量查询必要的字段；尽量分⻚查询；尽量通过索引加快查询。
②、CPU 成本，尽量避免复杂的查询条件，如有必要，考虑对⼦查询结果进⾏过滤。
----这部分是帮助⼤家理解 end，⾯试中可不背----
如何优化慢 SQL 呢？
 
⾸先，需要找到那些⽐较慢的 SQL，可以通过启⽤慢查询⽇志，记录那些超过指定执⾏时间的 SQL 查询。
也可以使⽤ show processlist; 命令查看当前正在执⾏的 SQL 语句，找出执⾏时间较⻓的 SQL。
或者在业务基建中加⼊对慢 SQL 的监控，常⻅的⽅案有字节码插桩、连接池扩展、ORM 框架扩展等。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 77 / 302

---

然后，使⽤ EXPLAIN 查看慢 SQL 的执⾏计划，看看有没有⽤索引，⼤部分情况下，慢 SQL 的原因都是因为没有⽤
到索引。
最后，根据分析结果，通过添加索引、优化查询条件、减少返回字段等⽅式进⾏优化。
慢sql⽇志怎么开启？
 
编辑 MySQL 的配置⽂件 my.cnf，设置 slow_query_log 参数为 1。
然后重启 MySQL 就好了。
也可以通过 set global 命令动态设置。
1. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 16 ⼀⾯⾯试原题：场景题：sql 查询很慢怎么排查
2. Java ⾯试指南（付费）收录的快⼿⾯经同学 5 ⾯试原题：慢sql⽇志怎么开启？
3. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：如何判断sql的效率，怎样
排查效率⽐较低的sql
4. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：mysql中如何定位慢查询
5. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：慢查询怎么分析
6. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：如何优化慢查询语句？
7. Java ⾯试指南（付费）收录的虾⽪⾯经同学 13 ⼀⾯⾯试原题：mysql慢查询
memo：2025 年 3 ⽉ 21 ⽇修改⾄此。今天有球友报喜说拿到了 wxg 的实习 offer，阿⾥云和美团也在进⾏当中，
真的 tql。
EXPLAIN SELECT * FROM your_table WHERE conditions;
[mysqld]
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2  # 记录执⾏时间超过2秒的查询
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL slow_query_log_file = '/var/log/mysql/slow.log';
SET GLOBAL long_query_time = 2;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 78 / 302

---

33.
你知道哪些⽅法来优化 SQL？
 
SQL 优化的⽅法⾮常多，但本质上就⼀句话：尽可能少地扫描、尽快地返回结果。
最常⻅的做法就是加索引、改写 SQL 让它⽤上索引，⽐如说使⽤覆盖索引、让联合索引遵守最左前缀原则等。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 79 / 302

---

如何利⽤覆盖索引？
 
覆盖索引的核⼼是“查询所需的字段都在同⼀个索引⾥”，这样 MySQL 就不需要回表，直接从索引中返回结果。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 80 / 302

---

实际使⽤中，我会优先考虑把 WHERE 和 SELECT 涉及的字段⼀起建联合索引，并通过 EXPLAIN 观察结果是否有 
Using index，确认命中索引。
----这部分是帮助⼤家理解 start，⾯试中可不背----
举个例⼦，现在要从 test 表中查询 city 为上海的 name 字段。
如果仅在 city 字段上添加索引，那么这条查询语句会先通过索引找到 city 为上海的⾏，然后再回表查询 name 字
段。
为了避免回表查询，可以在 city 和 name 字段上建⽴联合索引，这样查询结果就可以直接从索引中获取。
----这部分是帮助⼤家理解 end，⾯试中可不背----
如何正确使⽤联合索引？
 
使⽤联合索引最重要的⼀条是遵守最左前缀原则，也就是查询条件需要从索引的左侧字段开始。
----这部分是帮助⼤家理解 start，⾯试中可不背----
⽐如说我们创建了⼀个三列的联合索引。
我们来看⼀下什么样的查询条件可以⽤到这个索引：
select name from test where city='上海'
alter table test add index index1(city,name);
CREATE INDEX idx_name_age_sex ON user(name, age, sex);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 81 / 302

---

查询条件
能否⽤上
idx_name_age_sex？
说明
WHERE name = 'itwanger'
 可以
匹配第⼀列，命中索引
WHERE name = 'itwanger' AND
age=20
 可以
匹配前两列，命中索引
WHERE age = 20
 不⾏
第⼀列没⽤上，索引失效
WHERE name='itwanger' AND
sex='⼥'
 部分可⽤（只⽤前⼀列）
age 被跳过，后⾯的列⽆法使
⽤
WHERE name LIKE 'it%'
 可以（前缀匹配）
name 是前缀匹配，不影响使
⽤
WHERE name LIKE '%wanger%'
 不⾏
通配符在前，不能⽤索引
----这部分是帮助⼤家理解 end，⾯试中可不背----
如何进⾏分⻚优化？
 
分⻚优化的核⼼是避免深度偏移带来的全表扫描，可以通过两种⽅式来优化：延迟关联和添加书签。
延迟关联适⽤于需要从多个表中获取数据且主表⾏数较多的情况。它⾸先从索引表中检索出需要的⾏ ID，然后再
根据这些 ID 去关联其他的表获取详细信息。
延迟关联后，第⼀步只查主键，速度快，第⼆步只处理 20 条数据，效率⾼。
添加书签的⽅式是通过记住上⼀次查询返回的最后⼀⾏主键值，然后在下⼀次查询的时候从这个值开始，从⽽跳过
偏移量计算，仅扫描⽬标数据，适合翻⻚、资讯流等场景。
假设需要对⽤户表进⾏分⻚。
SELECT e.id, e.name, d.details
FROM employees e
JOIN department d ON e.department_id = d.id
ORDER BY e.id
LIMIT 1000, 20;
SELECT e.id, e.name, d.details
FROM (
    SELECT id
    FROM employees
    ORDER BY id
    LIMIT 1000, 20
) AS sub
JOIN employees e ON sub.id = e.id
JOIN department d ON e.department_id = d.id;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 82 / 302

---

通过添加书签来优化后，查询不再使⽤OFFSET ，⽽是从上⼀⻚最后⼀个⽤户的 ID 开始查询。这种⽅法可以有效避
免不必要的数据扫描，提⾼了分⻚查询的效率。
为什么分⻚会变慢？
 
分⻚查询的效率问题主要是由于 OFFSET 的存在，OFFSET 会导致 MySQL 必须扫描和跳过 offset + limit 条数据，
这个过程是⾮常耗时的。
⽐如说，我们要查询第 100000 条数据，那么 MySQL 就必须扫描 100000 条数据，然后再返回 10 条数据。
数据越多、偏移越⼤，就越慢！
memo：2025 年 3 ⽉ 22 ⽇修改⾄此。今天有球友说等腾讯云的 HR ⾯，很着急，但我赌他⼀定能拿到 offer，等
⼀个后续哈。
SELECT id, name
FROM users
ORDER BY id
LIMIT 1000, 20;
SELECT id, name
FROM users
WHERE id > last_max_id  -- 假设last_max_id是上⼀⻚最后⼀⾏的ID
ORDER BY id
LIMIT 20;
SELECT * FROM user ORDER BY id LIMIT 100000, 10;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 83 / 302

---

JOIN 代替⼦查询有什么好处？
 
第⼀，JOIN 的 ON 条件能更直接地触发索引，⽽⼦查询可能因嵌套导致索引失效。
第⼆，JOIN 的⼀次连接操作替代了⼦查询的多次重复执⾏，尤其在⼤数据量的情况下性能差异明显。
----这部分是帮助⼤家理解 start，⾯试中可不背----
⽐如说我们有两个表 orders 和 customers。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 84 / 302

---

对
⽐
项
⼦查询
JOIN
索
引
使
⽤
内层⼦查询 WHERE c.customer_id =
o.customer_id 每次执⾏时，可能⽆法直接
利⽤ orders 表的 customer_id 索引。
JOIN 的 ON 条件 o.customer_id =
c.customer_id 可以直接利⽤ orders 的
idx_customer_id 索引，加速连接过程。
执
⾏
计
划
⼦查询会被重复执⾏（每次外层 orders ⾏都
会触发⼀次⼦查询），导致全表扫描。
优化器可能选择通过索引快速关联两张表，减少数据
扫描量。例如，先通过 orders 的索引找到
customer_id，再与 customers 主键快速匹配。
性
能
表
现
当 orders 表数据量⼤时，⼦查询可能因重复
执⾏导致性能急剧下降。
JOIN 的⼀次连接操作通常更⾼效，尤其在⼤数据量
时。
⼦查询的写法：
JOIN 的写法：
对于⼦查询，执⾏流程是这样的：
外层 orders 表的每⼀⾏都会触发⼀次⼦查询。
如果 orders 表有 1000 条记录，则⼦查询会执⾏ 1000 次。
CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    amount DECIMAL(10,2),
    INDEX idx_customer_id (customer_id)  -- customer_id字段有索引
);
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    name VARCHAR(100)
);
SELECT o.order_id, o.amount, 
       (SELECT c.name 
        FROM customers c 
        WHERE c.customer_id = o.customer_id) AS customer_name
FROM orders o;
SELECT o.order_id, o.amount, c.name AS customer_name
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 85 / 302

---

每次⼦查询都需要单独查询 customers 表（即使 customer_id 相同）。
⽽ JOIN 的执⾏流程是这样的：
数据库优化器会将两张表的连接操作合并为⼀次执⾏。
通过索引（如 orders.customer_id 和 customers.customer_id）快速关联数据。
仅执⾏⼀次关联操作，⽽⾮多次⼦查询。
来看⼀下⼦查询的执⾏计划：
⼦查询（DEPENDENT SUBQUERY）类型表明其依赖外层查询的每⼀⾏，导致重复执⾏。
再对⽐看⼀下 JOIN 的执⾏计划：
EXPLAIN SELECT o.order_id, 
               (SELECT c.name FROM customers c WHERE c.customer_id = o.customer_id) 
        FROM orders o;
EXPLAIN SELECT o.order_id, 
               (SELECT c.name FROM customers c WHERE c.customer_id = o.customer_id) 
        FROM orders o;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 86 / 302

---

JOIN 通过 eq_ref 类型直接利⽤主键（customers.customer_id）快速关联，减少扫描次数。
----这部分是帮助⼤家理解 end，⾯试中可不背----
memo：2025 年 3 ⽉ 23 ⽇修改⾄此，今天有球友说，通过⼀晚上的时间，就在星球⾥学到很多知识，让他这个 7 
年经验的 CRUD Boy 受益匪浅。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 87 / 302

---

JOIN操作为什么要⼩表驱动⼤表？
 
第⼀，如果⼤表的 JOIN 字段有索引，那么⼩表的每⼀⾏都可以通过索引快速匹配⼤表。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 88 / 302

---

时间复杂度为⼩表⾏数 N 乘以⼤表索引查找复杂度 log(⼤表⾏数 M)，总复杂度为 N*log(M)。
显然⼩表做驱动表⽐⼤表做驱动表的时间复杂度 M*log(N) 更低。
第⼆，如果⼤表没有索引，需要将⼩表的数据加载到内存，再全表扫描⼤表进⾏匹配。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 89 / 302

---

时间复杂度为⼩表分段数 K 乘以⼤表⾏数 M，其中 K = ⼩表⾏数 N / 内存⼤⼩ join_buffer_size。
显然⼩表做驱动表的时候 K 的值更⼩，⼤表做驱动表的时候需要多次分段。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 90 / 302

---

1. 当使⽤ left join 时，左表是驱动表，右表是被驱动表。
2. 当使⽤ right join 时，刚好相反。
3. 当使⽤ join 时，MySQL 会选择数据量⽐较⼩的表作为驱动表，⼤表作为被驱动表。
----这部分是帮助⼤家理解 start，⾯试中可不背----
为了验证这⼀点，我特意新建了两个表 departments 和 employees。
-- ⼩表驱动（⾼效）
SELECT * FROM small_table s
JOIN large_table l ON s.id = l.id;  -- l.id有索引
-- ⼤表驱动（低效）
SELECT * FROM large_table l
JOIN small_table s ON l.id = s.id;  -- s.id⽆索引
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 91 / 302

---

插⼊测试数据：
然后⽤ explain 查看执⾏计划：
-- 插⼊测试数据
INSERT INTO departments VALUES
    (1, '研发部'),
    (2, '市场部'),
    (3, '⼈事部');
-- 插⼊更多数据到员⼯表
INSERT INTO employees VALUES
    (1, '张三', 1),
    (2, '李四', 1),
    (3, '王⼆', 2),
    (4, '赵六', 2),
    (5, '钱七', 3),
    (6, '孙⼋', NULL),
    (7, '周九', 1),
    (8, '吴⼗', 2);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 92 / 302

---

当使⽤ left join 的时候，第⼀⾏是 employees 表，说明左表是驱动表；当使⽤ right join 的时候，第⼀⾏是 
departments 表，说明右表是驱动表；当使⽤ join 的时候，第⼀⾏是 departments 表，说明 MySQL 默认选择了
⼩表作为驱动表。
----这部分是帮助⼤家理解 end，⾯试中可不背----
这⾥的⼩表指实际参与 JOIN 的数据量，⽽不是表的总⾏数。⼤表经过 where 条件过滤后也可能成为逻辑⼩表。
也可以强制通过 STRAIGHT_JOIN 提示 MySQL 使⽤指定的驱动表。
为什么要避免使⽤ JOIN 关联太多的表？
 
第⼀，多表 JOIN 的执⾏路径会随着表的数量呈现指数级增⻓，优化器需要估算所有路径的成本，有可能会导致出
现⼤表驱动⼩表的情况。
第⼆，多表 JOIN 需要缓存中间结果集，可能超出 join_buffer_size，这种情况下内存临时表就会转为磁盘临时表，
性能也会急剧下降。
-- 实际参与JOIN的数据量决定⼩表
SELECT * FROM large_table l
JOIN small_table s ON l.id = s.id
WHERE l.created_at > '2025-01-01';  -- l经过过滤后可能成为⼩表
explain select table_1.col1, table_2.col2, table_3.col2
from table_1
straight_join table_2 on table_1.col1=table_2.col1
straight_join table_3 on table_1.col1 = table_3.col1;
explain select straight_join table_1.col1, table_2.col2, table_3.col2
from table_1
join table_2 on table_1.col1=table_2.col1
join table_3 on table_1.col1 = table_3.col1;
SELECT * FROM A
JOIN B ON A.id = B.a_id
JOIN C ON B.id = C.b_id
JOIN D ON C.id = D.c_id
JOIN E ON D.id = E.d_id;  -- 5 个表，优化器需评估 5! = 120 种顺序
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 93 / 302

---

《阿⾥巴巴 Java 开发⼿册》上就规定，不要使⽤ join 关联太多的表，最多不要超过 3 张表。
习 offer。
如何进⾏排序优化？
 
第⼀，对 ORDER BY 涉及的字段创建索引，避免 filesort。
如果是多个字段，联合索引需要保证 ORDER BY 的列是索引的最左前缀。
第⼆，可以适当调整排序参数，如增⼤ sort_buffer_size、max_length_for_sort_data 等，让排序在内存中完成。
----这部分是帮助⼤家理解 start，⾯试中可不背----
-- 优化前（可能触发 filesort）
SELECT * FROM users ORDER BY age DESC;
-- 优化后（添加索引）
ALTER TABLE users ADD INDEX idx_age (age);
-- 联合索引需与 ORDER BY 顺序⼀致（age 在前，name 在后）
ALTER TABLE users ADD INDEX idx_age_name (age, name);
-- 有效利⽤索引的查询
SELECT * FROM users ORDER BY age, name;
-- ⽆效案例（索引失效，因 name 在索引中排在 age 之后）
SELECT * FROM users ORDER BY name, age;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 94 / 302

---

sort_buffer_size：⽤于控制排序缓冲区的⼤⼩，默认为 256KB。也就是说，如果排序的数据量⼩于 256KB，
MySQL 会在内存中直接排序；否则就要在磁盘上进⾏ filesort。
max_length_for_sort_data：单⾏数据的最⼤⻓度，会影响排序算法选择。如果单⾏数据超过该值，MySQL 
会使⽤双路排序，否则使⽤单路排序。
max_sort_length：限制字符串排序时⽐较的前缀⻓度。当 MySQL 不得不对 text、blob 字段进⾏排序时，会
截取前 max_sort_length 个字符进⾏⽐较。
----这部分是帮助⼤家理解 end，⾯试中可不背----
第三，可以通过 where 和 limit 限制待排序的数据量，减少排序的开销。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 95 / 302

---

什么是 filesort？
 
推荐阅读：MySQL 如何执⾏ ORDER BY
当不能使⽤索引⽣成排序结果的时候，MySQL 需要⾃⼰进⾏排序，如果数据量⽐较⼩，会在内存中进⾏；如果数
据量⽐较⼤就需要写临时⽂件到磁盘再排序，我们将这个过程称为⽂件排序。
----这部分是帮助⼤家理解 start，⾯试中可不背----
好，让我们来验证⼀下 filesort 的情况，建表、插⼊数据。
-- 优化前
SELECT * FROM users ORDER BY age LIMIT 100;
-- 优化后（减少数据传输和排序开销）
SELECT id, name, age FROM users ORDER BY age LIMIT 100;
-- 深度分⻚优化（避免 OFFSET 扫描全表）
SELECT * FROM users ORDER BY age LIMIT 10000, 20;  -- 低效
SELECT * FROM users WHERE age > last_age ORDER BY age LIMIT 20;  -- ⾼效（记录上⼀⻚最后⼀条的 
age 值）
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 96 / 302

---

执⾏ explain 查看执⾏计划。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 97 / 302

---

能够看得出来，当 order by id 也就是主键的时候，没有触发 filesort；当 order by age 的时候，由于没有索引，
就触发了 filesort。
----这部分是帮助⼤家理解 end，⾯试中可不背----
memo：2025 年 3 ⽉ 26 ⽇修改⾄此，今天有球友说，拿到了阿⾥云的实习 offer，真的 tql。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 98 / 302

---

全字段排序和 rowid 排序了解多少？
 
当排序字段是索引字段且满⾜最左前缀原则时，MySQL 可以直接利⽤索引的有序性完成排序。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 99 / 302

---

当⽆法使⽤索引排序时，MySQL 需要在内存或磁盘中进⾏排序操作，分为全字段排序和 rowid 排序两种算法。
全字段排序会⼀次性取出满⾜条件⾏的所有字段，然后在 sort buffer 中进⾏排序，排序后直接返回结果，⽆需回
表。
以 SELECT * FROM user WHERE name = "王⼆" ORDER BY age 为例：
从 name 索引中找到第⼀个满⾜ name='张三' 的主键 id；
根据主键 id 取出整⾏所有的字段，存⼊ sort buffer；
重复上述过程直到处理完所有满⾜条件的⾏
对 sort buffer 中的数据按 age 排序，返回结果。
优点是仅需要⼀次磁盘 IO，缺点是内存占⽤⼤，如果数量超过 sort buffer 的话，需要分⽚读取并借助临时⽂件合
并排序，IO 次数反⽽会增加。
也⽆法处理包含 text 和 blob 类型的字段。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 100 / 302

---

rowid 排序分为两个阶段：
第⼀阶段：根据查询条件取出排序字段和主键 ID，存⼊ sort buffer 进⾏排序；
第⼆阶段：根据排序后的主键 ID 回表取出其他需要的字段。
同样以 SELECT * FROM user WHERE name = "王⼆" ORDER BY age 为例：
从 name 索引中找到第⼀个满⾜ name='张三' 的主键 id；
根据主键 id 取出排序字段 age，连同主键 id ⼀起存⼊ sort buffer；
重复上述过程直到处理完所有满⾜条件的⾏
对 sort buffer 中的数据按 age 排序；
遍历排序后的主键 id，回表取出其他所需字段，返回结果。
优点是内存占⽤较少，适合字段多或者数据量⼤的场景，缺点是需要两次磁盘 IO。
MySQL 会根据系统变量 max_length_for_sort_data 和查询字段的总⼤⼩来决定使⽤全字段排序还是 rowid 排序。
如果查询字段总⻓度 <= max_length_for_sort_data，MySQL 会使⽤全字段排序；否则会使⽤ rowid 排序。
你对 Sort_merge_passes 参数了解吗？
 
推荐阅读：深⼊了解 MySQL Order By ⽂件排序
Sort_merge_passes 是⼀个状态变量，⽤于统计 MySQL 在执⾏排序操作时进⾏归并排序的次数。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 101 / 302

---

当 MySQL 需要进⾏排序但排序数据⽆法完全放⼊ sort_buffer_size 定义的内存缓冲区时，就会使⽤临时⽂件进⾏
外部排序，这时就会产⽣ Sort_merge_passes。
如果 Sort_merge_passes 在短时间内快速激增，说明排序操作的数据量较⼤，需要调整 sort_buffer_size 或者优
化查询语句。
MySQL 在执⾏排序操作时，会经历两个过程：
内存排序阶段，MySQL ⾸先尝试在 sort buffer 中进⾏排序。如果数据量⼩于 sort_buffer_size 缓冲区⼤⼩，
会完全在内存中完成快速排序。
外部排序阶段，如果数据量超过 sort_buffer_size，MySQL 会将数据分成多个块，每块单独排序后写⼊临时
⽂件，然后对这些已排序的块进⾏归并排序。每次归并操作都会增加 Sort_merge_passes 的计数。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 102 / 302

---

memo：2025 年 3 ⽉ 27 ⽇修改⾄此，今天有球友说，通过了快⼿⼆⾯，并且 HR ⾯是不排序的，已经确定了⼊职
时间，恭喜啊。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 103 / 302

---

条件下推你了解多少？
 
条件下推的核⼼思想是将外层的过滤条件，⽐如说 where、join 等，尽可能地下推到查询计划的更底层，⽐如说⼦
查询、连接操作之前，从⽽减少中间结果的数据量。
⽐如说原始查询是：
就可以将条件下推到⼦查询：
这样就可以减少查询返回的数据量，避免外层再过滤。
再⽐如说 union 中的原始查询是：
就可以将条件下推到每个⼦查询：
每个⼦查询仅返回前 10 条数据，减少临时表的数据量。
再⽐如说连接查询 join 中的原始查询是：
SELECT * FROM (
  SELECT * FROM orders WHERE total > 100
) AS subquery
WHERE subquery.status = 'shipped';
SELECT * FROM (
  SELECT * FROM orders WHERE total > 100 AND status = 'shipped'
) AS subquery;
(SELECT * FROM t1) 
UNION ALL 
(SELECT * FROM t2)
ORDER BY col LIMIT 10;
(SELECT * FROM t1 ORDER BY col LIMIT 10)
UNION ALL 
(SELECT * FROM t2 ORDER BY col LIMIT 10);
SELECT * FROM orders
JOIN customers ON orders.customer_id = customers.id
WHERE customers.country = 'china';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 104 / 302

---

就可以将条件下推到表扫描的时候：
先过滤 customers 表，减少 join 时的数据量。
为什么要尽量避免使⽤ select *？
 
SELECT * 会强制 MySQL 读取表中所有字段的数据，包括应⽤程序可能并不需要的，⽐如 text、blob 类型的⼤字
段。
加载冗余数据会占⽤更多的缓存空间，从⽽挤占其他重要数据的缓存资源，降低整体系统的吞吐量。
也会增加⽹络传输的开销，尤其是在⼤字段的情况下。
最重要的是，SELECT * 可能会导致覆盖索引失效，本来可以⾛索引的查询最后变成了全表扫描。
你还知道哪些 SQL 优化⽅法？
 
①、避免使⽤ != 或者 <> 操作符
!= 或者 <> 操作符会导致 MySQL ⽆法使⽤索引，从⽽导致全表扫描。
可以把column<>'aaa' ，改成column>'aaa' or column<'aaa' 。
②、使⽤前缀索引
⽐如，邮箱的后缀⼀般都是固定的@xxx.com ，那么类似这种后⾯⼏位为固定值的字段就⾮常适合定义为前缀索
引：
需要注意的是，MySQL ⽆法利⽤前缀索引做 order by 和 group by 操作。
③、避免在列上使⽤函数
在 where ⼦句中直接对列使⽤函数会导致索引失效，因为 MySQL 需要对每⾏的列应⽤函数后再进⾏⽐较。
可以改成：
SELECT * FROM orders
JOIN (
  SELECT * FROM customers WHERE country = 'china'
) AS filtered_customers
ON orders.customer_id = filtered_customers.id;
-- 使⽤覆盖索引（假设索引为 idx_country）
SELECT id, country FROM users WHERE country = 'china';  -- 可能仅扫描索引
-- 使⽤ SELECT *
SELECT * FROM users WHERE country = 'china';            -- 需回表读取所有列
alter table test add index index2(email(6));
select name from test where date_format(create_time,'%Y-%m-%d')='2021-01-01';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 105 / 302

---

通过⽇期的范围查询，⽽不是在列上使⽤函数，可以利⽤ create_time 上的索引。
memo：2025 年 3 ⽉ 28 ⽇修改⾄此，今天有球友说，字节跳动和腾讯的暑期实习都 OC 了，很感谢当时加了⼆
哥的编程星球，看球友们⽇常的学习分享，以及⼆哥推荐的轮⼦。
select name from test where create_time>='2021-01-01 00:00:00' and create_time<'2021-01-
02 00:00:00';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 106 / 302

---

⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 107 / 302

---

1. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：查询优化、联合索引、覆盖索
引
2. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：说说 SQL 该如何优化
3. Java ⾯试指南（付费）收录的华为⾯经同学 6 Java 通⽤软件开发⼀⾯⾯试原题：说说 SQL 该如何优化
4. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：MySQL 索引如何优化？
5. Java ⾯试指南（付费）收录的携程⾯经同学 10 Java 暑期实习⼀⾯⾯试原题：讲⼀讲 MySQL 的索引，
如何优化 SQL？
6. Java ⾯试指南（付费）收录的⽤友⾯试原题：了解 mysql 怎么优化吗
7. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：查询如何优化  
34.
explain平常有⽤过吗？
 
经常⽤，explain 是 MySQL 提供的⼀个⽤于查看 SQL 执⾏计划的⼯具，可以帮助我们分析查询语句的性能问题。
⼀共有 10 来个输出参数。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 108 / 302

---

⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 109 / 302

---

⽐如说 type=ALL,key=NULL 表示 SQL 正在全表扫描，可以考虑为 where 字段添加索引进⾏优化；Extra=Using 
filesort 表示 SQL 正在⽂件排序，可以考虑为 order by 字段添加索引。
使⽤⽅式也⾮常简单，直接在 select 前加上 explain 关键字就可以了。
更⾼级的⽤法可以配合 format=json 参数，将 explain 的输出结果以 JSON 格式返回。
explain select * from students where name='王⼆';
explain format=json select * from students where name='王⼆';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 110 / 302

---

explain 输出结果中常⻅的字段含义理解吗？
 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 111 / 302

---

字段
值
含义与优化指导
id
1
查询序列号。
select_type
SIMPLE
简单查询（⽆⼦查询或 UNION）。复杂场景还有 PRIMARY、
SUBQUERY、DERIVED 等。
table
orders
当前步骤操作的表名。
partitions
NULL
涉及的分区。
type
ref
访问类型：关键性能指标，常⻅类型：
- system/const：唯⼀值匹配（性能最佳）
- eq_ref：主键/唯⼀索引连接
- ref：⾮唯⼀索引匹配
- range：索引范围扫描
- index：全索引扫描
- ALL：全表扫描（需优化）
possible_keys
idx_user_id
可能使⽤的索引。若为空，说明⽆合适索引。
key
idx_user_id
实际选择的索引。若为 NULL，表示未使⽤索引。
key_len
4
索引使⽤的字节数，可判断是否使⽤完整索引。例如，联合索引 (a,b)，若
key_len=4 可能只⽤到了 a 列。
ref
const
与索引⽐较的列或常量（如 WHERE user_id=100 中的 100）。
rows
50
预估扫描⾏数。数值越⼩越好，若与实际差距⼤，可能统计信息过期（需
ANALYZE TABLE）。
filtered
100.00
查询条件过滤后剩余⾏的百分⽐。例如 rows=1000 且 filtered=10%，则
最终返回约 100 ⾏。
Extra
Using
where
附加信息：
- Using index：覆盖索引（⽆需回表）
- Using temporary：使⽤临时表
- Using filesort：⽂件排序
在 EXPLAIN 输出结果中我最关注的字段是 type、key、rows 和 Extra。
我会通过它们判断 SQL 有没有⾛索引、是否全表扫描、预估扫描⾏数是否太⼤，以及是否触发了 filesort 或临时
表。⼀旦发现问题，⽐如 type=ALL 或者 Extra=Using filesort，我会考虑建索引、改写 SQL 或控制查询结果集来
做优化。
----这部分是帮助⼤家理解 start，⾯试中可不背----
以 EXPLAIN SELECT * FROM orders WHERE user_id = 100 的输出为例：
⾮表格版本：
①、id 列：查询的执⾏顺序编号。id 相同：同⼀执⾏层级，按 table 列从上到下顺序执⾏（如多表 JOIN）；id 递
增：嵌套⼦查询，数值越⼤优先级越⾼，越先执⾏。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 112 / 302

---

t2 ⼦查询的 id=2，优先执⾏。
②、select_type 列：查询的类型。常⻅的类型有：
SIMPLE：简单查询，不包含⼦查询或者 UNION。
PRIMARY：查询中如果包含⼦查询，则最外层查询被标记为 PRIMARY。需要关注⼦查询或派⽣表性能。
SUBQUERY：⼦查询；需要避免多层嵌套，尽量改写为 JOIN。
DERIVED：派⽣表（FROM ⼦句中的⼦查询）。需要减少派⽣表数据量，或物化为临时表。
③、table 列：查的哪个表。
derivedN：表示派⽣表（N 对应 id）。
unionNM,N：表示 UNION 合并的结果（M、N 为参与 UNION 的 id）。
④、type 列：表示 MySQL 在表中找到所需⾏的⽅式。
system，表仅有⼀⾏（系统表或衍⽣表），⽆需优化。
const：通过主键或唯⼀索引找到⼀⾏（如 WHERE id = 1）。理想情况。
eq_ref：对主键/唯⼀索引 JOIN 匹配（如 A JOIN B ON A.id = B.id ）。确保 JOIN 字段有索引。
ref：⾮唯⼀索引匹配（如 WHERE name = '王⼆' ，name 有普通索引）。
range：只检索给定范围的⾏，使⽤索引来检索。在where 语句中使⽤ bettween...and 、< 、> 、<= 、
in 等条件查询 type 都是 range 。
index：全索引扫描，如果不需要回表，可接受；否则考虑覆盖索引。
ALL：全表扫描，效率最低。
⑤、possible_keys 列：可能会⽤到的索引，但并不⼀定实际被使⽤。
⑥、key 列：实际使⽤的索引。如果为 NULL，则没有使⽤索引。如果为 PRIMARY，则使⽤了主键索引。
⑦、key_len 列：使⽤的索引字节数，反映索引列的利⽤率。使⽤联合索引 (a, b)，key_len 是 a 和 b 的字节总和
（仅当查询条件⽤到 a 或 a+b 时有效）。
key_len = 4（INT） + 20*3（utf8） + 2 = 66 字节。
⑧、ref 列：与索引列⽐较的值或列。
const：常量。例如 WHERE column = 'value' 。
func：函数。例如 WHERE column = func(column) 。
⑨、rows 列：优化器估算的需要扫描的⾏数。数值越⼩越好，若与实际差距⼤，可能统计信息过期（需 ANALYZE 
TABLE）。结合 filtered 字段可以计算最终返回⾏数（rows × filtered）。
⑩、Extra 列：附加信息。
EXPLAIN SELECT * FROM t1 JOIN (SELECT * FROM t2 WHERE id = 1) AS sub;
-- 表结构：CREATE TABLE t (a INT, b VARCHAR(20), INDEX idx_a_b (a, b));
EXPLAIN SELECT * FROM t WHERE a = 1 AND b = 'test';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 113 / 302

---

Using index：覆盖索引，⽆需回表。
Using where：存储引擎返回结果后，Server 层需要再次过滤（条件未完全下推）。
Using temporary ：使⽤临时表（常⻅于 GROUP BY、DISTINCT）。
Using filesort：⽂件排序（常⻅于 ORDER BY）。考虑为 ORDER BY 字段添加索引。
Select tables optimized away：优化器已优化（如 COUNT(*) 通过索引直接统计）。
Using join buffer：使⽤连接缓冲区（Block Nested Loop 或 Hash Join）。考虑增⼤ join_buffer_size。
示例：
----这部分是帮助⼤家理解 end，⾯试中可不背----
type的执⾏效率等级，达到什么级别⽐较合适？
 
从⾼到低的效率排序是 system、const、eq_ref、ref、range、index 和 ALL。
⼀般情况下，建议 type 值达到 const、eq_ref 或 ref，因为这些类型表明查询使⽤了索引，效率较⾼。
如果是范围查询，range 类型也是可以接受的。
ALL 类型表示全表扫描，性能最差，往往不可接受，需要优化。
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：怎么看⾛没⾛索引，如何分析 SQL
2. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：key-len和key没什么区别，什
么时候会⽤到key-len，你还会查看explain中的哪些字段，extra有哪些类型
3. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：explain分析后， type的执⾏效
率等级，达到什么级别⽐较合适
memo：2025 年 3 ⽉ 29 ⽇修改⾄此，今天有球友说美团的 offer ⼝头 OC 了，真的太棒了。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 114 / 302

---

索引
 
35.
索引为什么能提⾼MySQL查询效率？
 
索引就像⼀本书的⽬录，能让 MySQL 快速定位数据，避免全表扫描。
它⼀般是 B+ 树结构，查找效率是 O(log n)，⽐从头到尾扫⼀遍数据要快得多。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 115 / 302

---

除了查得快，索引还能加速排序、分组、连接等操作。
可以通过 create index 创建索引，⽐如：
----这部分是帮助⼤家理解 start，⾯试中可不背----
我们通过 wrap 的 agent 验证⼀下有没有索引的查询效率。
先上结果，有索引的查询时间是 0.007 秒，没有索引的查询时间是 0.036 秒。
create index idx_name on students(name);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 116 / 302

---

创建数据库和表。
插⼊ 10 万条数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 117 / 302

---

然后依次执⾏ explain 查看没有索引和有索引时的执⾏计划。
----这部分是帮助⼤家理解 end，⾯试中可不背----
1. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：MySQL 索引，为什么⽤ 
B+树
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 118 / 302

---

2. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 E 第⼆个部⻔ Java 后端技术⼀⾯⾯试原题：为什么需要索
引
3. Java ⾯试指南（付费）收录的⼩公司⾯经同学 5 Java 后端⾯试原题：数据库索引讲⼀下，然后为什么
会加快查询速度
4. Java ⾯试指南（付费）收录的去哪⼉⾯经同学 1 技术⼆⾯⾯试原题：mysql为什么⽤索引
5. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：对MySQL索引的理解
6. Java ⾯试指南（付费）收录的vivo ⾯经同学 10 技术⼀⾯⾯试原题：索引，为什么使⽤索引更快
7. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：介绍下索引？底层是啥？
memo：2025 年 3 ⽉ 30 ⽇修改⾄此，之前有球友说拿到了淘天搜索的暑期实习，真的恭喜了。
36.
能简单说⼀下索引的分类吗？
 
从功能上分类的话，有主键索引、唯⼀索引、全⽂索引；从数据结构上分类的话，有 B+ 树索引、哈希索引；从存
储内容上分类的话，有聚簇索引、⾮聚簇索引。
你对主键索引了解多少？
 
主键索引⽤于唯⼀标识表中的每条记录，其列值必须唯⼀且⾮空。创建主键时，MySQL 会⾃动⽣成对应的唯⼀索
引。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 119 / 302

---

每个表只能有⼀个主键索引，⼀般是表中的⾃增 id 字段。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
如果创建表的时候没有指定主键，MySQL 的 InnoDB 存储引擎会优先选择⼀个⾮空的唯⼀索引作为主键；如果没
有符合条件的索引，MySQL 会⾃动⽣成⼀个隐藏的 _rowid 列作为主键。
可以通过 show index from table_name 查看索引信息：
CREATE TABLE emp6 (emp_id INT PRIMARY KEY, name VARCHAR(50));  -- 单列主键
CREATE TABLE CountryLanguage (
    CountryCode CHAR(3),
    Language VARCHAR(30),
    PRIMARY KEY (CountryCode, Language)  -- 复合主键
);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 120 / 302

---

Table 当前索引所属的表名。
Non_unique 是否唯⼀索引，0 表示唯⼀索引（如主键），1 表示⾮唯⼀。
Key_name 主键索引默认叫 PRIMARY；普通索引为⾃定义名。
Seq_in_index 索引中的列顺序，在联合索引中这个字段表示第⼏列（第 1 个）。
Column_name 当前索引中包含的字段名。
Collation A 表示升序（Ascend）；D 表示降序。
Cardinality 索引的基数，即不重复的索引值的数量。越⾼说明区分度越好（影响优化器是否⽤此索引）。
Sub_part 前缀索引的⻓度。
Packed 是否压缩存储索引；⼀般不⽤，默认为 NULL。
Null 字段是否允许为 NULL；主键字段不允许为 NULL。
Index_type 索引底层结构，InnoDB 默认是 B+ 树（BTREE）。
Comment 索引的注释。
Visible 是否可⻅；MySQL 8.0+ 可隐藏索引。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
唯⼀索引和主键索引有什么区别？
 
主键索引=唯⼀索引+⾮空。每个表只能有⼀个主键索引，但可以有多个唯⼀索引。
-- 在 email 列上添加唯⼀索引
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 121 / 302

---

主键索引不允许插⼊ NULL 值，尝试插⼊ NULL 会报错；唯⼀索引允许插⼊多个 NULL 值。
unique key 和 unique index 有什么区别？
 
创建唯⼀键时，MySQL 会⾃动⽣成⼀个同名的唯⼀索引；反之，创建唯⼀索引也会隐式添加唯⼀性约束。
可通过 UNIQUE KEY uk_name 定义或者 CONSTRAINT uk_name UNIQUE 定义唯⼀键。
    UNIQUE KEY uk_email (email)  -- 唯⼀索引
);
-- 复合唯⼀索引（保证 user_id 和 role 组合唯⼀）
CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role)
);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 122 / 302

---

可通过 CREATE UNIQUE INDEX 创建唯⼀索引。
通过 SHOW CREATE TABLE table_name 查看表结构时，结果都是⼀样的。
普通索引和唯⼀索引有什么区别？
 
普通索引仅⽤于加速查询，不限制字段值的唯⼀性；适⽤于⾼频写⼊的字段、范围查询的字段。
CREATE TABLE users (
    id INT PRIMARY KEY,
    email VARCHAR(100),
    -- 显式命名唯⼀键
    CONSTRAINT uk_email UNIQUE (email)
);
CREATE TABLE users3 (
     id INT PRIMARY KEY,
    email VARCHAR(100),
    UNIQUE KEY uk_email (email)  -- 唯⼀索引
);
CREATE TABLE users (
    id INT PRIMARY KEY,
    email VARCHAR(100)
);
-- ⼿动创建唯⼀索引
CREATE UNIQUE INDEX uk_email ON users(email);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 123 / 302

---

唯⼀索引强制字段值的唯⼀性，插⼊或更新时会触发唯⼀性检查；适⽤于业务唯⼀性约束的字段、防⽌数据重复插
⼊的字段。
memo：2025 年 3 ⽉ 31 ⽇修改⾄此，今天有球友说，拿到了淘天暑期实习的 offer，并且再次强调背⾯渣逆袭的
重要性，哈哈。
你对全⽂索引了解多少？
 
-- ⽇志时间戳允许重复，⽆需唯⼀性检查
CREATE INDEX idx_log_time ON access_logs(access_time);
-- 订单状态允许重复，但需频繁按状态过滤数据
CREATE INDEX idx_order_status ON orders(status);
-- ⽤户邮箱必须唯⼀
CREATE UNIQUE INDEX uk_email ON users(email);
-- 确保同⼀⽤户对同⼀商品只能有⼀条未⽀付订单
CREATE UNIQUE INDEX uk_user_product ON orders(user_id, product_id) WHERE status = 
'unpaid';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 124 / 302

---

全⽂索引是 MySQL ⼀种优化⽂本数据检索的特殊类型索引，适⽤于 CHAR、VARCHAR 和 TEXT 等字段。
MySQL 5.7 及以上版本内置了 ngram 解析器，可处理中⽂、⽇⽂和韩⽂等分词。
建表时通过 FULLTEXT (title, body) 来定义。通过 MATCH(col1, col2) AGAINST('keyword') 进⾏检索，
默认按照降序返回结果，⽀持布尔模式查询。
+ 表示必须包含；
- 表示排除；
* 表示通配符；
底层使⽤倒排索引将字段中的⽂本内容进⾏分词，然后建⽴⼀个倒排表。性能⽐ LIKE '%keyword%' ⾼很多。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
倒排索引通过⼀个辅助表存储单词与单词⾃身在⼀个或多个⽂档中所在位置之间的映射，通常采⽤关联数组实现。
有两种表现形式：inverted file index（{单词，单词所在⽂档的ID}）和full inverted index（{单词，(单词所在⽂档
的ID，在具体⽂档中的位置)}）
⽐如有这样⼀个⽂档：
inverted file index 的关联数组存储形式为：
full inverted index 更加详细：
-- 建表时创建全⽂索引（⽀持中⽂）
CREATE TABLE articles (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    content TEXT,
    FULLTEXT(title, content) WITH PARSER ngram
) ENGINE=InnoDB;
-- 使⽤布尔模式查询
SELECT * FROM articles 
WHERE MATCH(title, content) AGAINST('+MySQL -Oracle' IN BOOLEAN MODE);
 DocumentId  Text  
1          Pease porridge hot, pease porridge cold  
2          Pease porridge in the pot  
3          Nine days old  
4          Some like it hot, some like it cold  
5          Some like it in the pot  
6          Nine days old
days → 3,6  
old → 3,6  
pease → 1,2  
porridge → 1,2  
...
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 125 / 302

---

full inverted index 不仅存储了⽂档 ID，还存储了单词在⽂档中的具体位置。
InnoDB 采⽤的是 full inverted index 的⽅式实现全⽂索引。
如果需要处理中⽂分词的话，⼀定要记得加上 WITH PARSER ngram ，否则可能查不出来数据。
不过，对于复杂的中⽂场景，建议使⽤ Elasticsearch 等专业搜索引擎替代，技术派项⽬中就⽤了这种⽅案。
days → (3:5),(6:5)  
old → (3:11),(6:11)  
pease → (1:1),(1:7),(2:1)  
porridge → (1:7),(2:7)  
...
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 126 / 302

---

---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
memo：2025 年 4 ⽉ 1 ⽇修改⾄此，今天有球友说，拿到了美团的实习 offer，真的太棒了，18 号⼀⾯、25 号⼆
⾯、30 号 OC，4 ⽉ 1 发邮件 offer，节奏拉满了
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 127 / 302

---

1. Java ⾯试指南（付费）收录的科⼤讯⻜⾮凡计划研发类⾯经原题：聊聊 MySQL 的索引
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：MySQL 索引，为什么⽤ 
B+树
3. Java ⾯试指南（付费）收录的携程⾯经同学 10 Java 暑期实习⼀⾯⾯试原题：讲⼀讲 MySQL 的索引，
如何优化 SQL？
4. Java ⾯试指南（付费）收录的阿⾥⾯经同学 5 阿⾥妈妈 Java 后端技术⼀⾯⾯试原题：索引的分类，创
建索引的最佳实践
5. Java ⾯试指南（付费）收录的 360 ⾯经同学 3 Java 后端技术⼀⾯⾯试原题：mysql 的索引⽤过哪些
6. Java ⾯试指南（付费）收录的⽤友⾯试原题：索引是什么？有哪些索引
7. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：普通索引的叶⼦节点存储的
是什么
8. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：innodb底层有哪些数据结构
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 128 / 302

---

9. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：索引有哪些，区别是什么
37.
创建索引有哪些注意点？
 
第⼀，选择合适的字段
⽐如说频繁出现在 WHERE、JOIN、ORDER BY、GROUP BY 中的字段。
优先选择区分度⾼的字段，⽐如⽤户 ID、⼿机号等唯⼀值多的，⽽不是性别、状态等区分度极低的字段，如
果真的需要，可以考虑联合索引。
第⼆，要控制索引的数量，避免过度索引，每个索引都要占⽤存储空间，单表的索引数量不建议超过 5 个。
要定期通过 SHOW INDEX FROM table_name 查看索引的使⽤情况，删除不必要的索引。⽐如说已经有联合索引 
(a, b)，单索引（a）就是冗余的。
第三，联合索引的时候要遵循最左前缀原则，即在查询条件中使⽤联合索引的第⼀个字段，才能充分利⽤索引。
⽐如说联合索引 (A, B, C) 可⽀持 A、A+B、A+B+C 的查询，但⽆法⽀持 B 或 C 的单独查询。
区分度⾼的字段放在左侧，等值查询的字段优先于范围查询的字段。例如 WHERE A=1 AND B>10 AND C=2 ，优先 
(A, C, B) 。
如果联合索引包含查询的所需字段，还可以避免回表，提⾼查询效率。
1. Java ⾯试指南（付费）收录的⽤友⾦融⼀⾯原题：索引的作⽤，加索引需要注意什么
2. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：查询和更新都频繁的字段是否适合创
建索引，为什么
3. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：索引怎么设计才是最好的
4. Java ⾯试指南（付费）收录的京东⾯经同学 1 Java 技术⼀⾯⾯试原题：MySQL 索引结构，建⽴索引的
策略
5. Java ⾯试指南（付费）收录的阿⾥⾯经同学 5 阿⾥妈妈 Java 后端技术⼀⾯⾯试原题：索引的分类，创
建索引的最佳实践
6. Java ⾯试指南（付费）收录的oppo ⾯经同学 8 后端开发秋招⼀⾯⾯试原题：建索引的时候应该注意什
么
7. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：建⽴索引考虑哪些问题
38.
索引哪些情况下会失效呢？
 
简版：⽐如索引列使⽤了函数、使⽤了通配符开头的模糊查询、联合索引不满⾜最左前缀原则，或者使⽤ or 的时
候部分字段⽆索引等。
第⼀，对索引列使⽤函数或表达式会导致索引失效。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 129 / 302

---

第⼆，LIKE 模糊查询以通配符开头会导致索引失效。
第三，联合索引违反了最左前缀原则，索引会失效。
联合索引，但 WHERE 不满⾜最左前缀原则，索引⽆法起效。例如：SELECT * FROM table WHERE 
column2 = 2 ，联合索引为 (column1, column2) 。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
第四，使⽤ OR 连接⾮索引列条件，会导致索引失效。
第五，使⽤ != 或 <> 不等值查询会导致索引失效。
-- 索引失效
SELECT * FROM users WHERE YEAR(create_time) = 2023;
SELECT * FROM products WHERE price*2 > 100;
-- 优化⽅案（使⽤范围查询）
SELECT * FROM users WHERE create_time BETWEEN '2023-01-01' AND '2023-12-31';
SELECT * FROM products WHERE price > 50;
-- 索引失效
SELECT * FROM articles WHERE title LIKE '%数据库%';
-- 可以使⽤索引（但范围有限）
SELECT * FROM articles WHERE title LIKE '数据库%';
-- 解决⽅案：考虑全⽂索引或搜索引擎
SELECT * FROM articles WHERE MATCH(title) AGAINST('数据库');
-- 假设有联合索引 (a, b, c)
SELECT * FROM table WHERE b = 2 AND c = 3;  -- 索引失效
SELECT * FROM table WHERE a = 1 AND c = 3;  -- 只使⽤a列索引
-- 正确使⽤联合索引
SELECT * FROM table WHERE a = 1 AND b = 2 AND c = 3;
-- 假设name有索引但age没有
SELECT * FROM users WHERE name = '张三' OR age = 25;  -- 全表扫描
-- 优化⽅案1：使⽤UNION ALL
SELECT * FROM users WHERE name = '张三'
UNION ALL
SELECT * FROM users WHERE age = 25 AND name != '张三';
-- 优化⽅案2：考虑为age添加索引
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 130 / 302

---

---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
什么情况下模糊查询不⾛索引？
 
模糊查询主要使⽤ LIKE 语句，结合通配符来实现。
%（代表任意多个字符）和 _（代表单个字符）
这个查询会返回所有 column 列中包含 xxx 的记录。
但是，如果模糊查询的通配符 % 出现在搜索字符串的开始位置，如 LIKE '%xxx' ，MySQL 将⽆法使⽤索引，因
为数据库必须扫描全表以匹配任意位置的字符串。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：where b =5 是否⼀定
会命中索引？（索引失效场景）
2. Java ⾯试指南（付费）收录的京东⾯经同学 1 Java 技术⼀⾯⾯试原题：索引失效的情况
3. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：编写 SQL 语句哪些情况会导
致索引失效？
4. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：索引失效场景
5. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：什么情况下索引失效？
6. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：索引失效情况
7. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：什么操作会导致索引失效？
39.索引不适合哪些场景呢？
 
第⼀，区分度低的列，可以和其他⾼区分度的列组成联合索引。
第⼆，频繁更新的列，索引会增加更新的成本。
第三，TEXT、BLOB 等⼤对象类型的字段，可以使⽤前缀索引、全⽂索引替代。
第四，当表的数据量很⼩的时候，不超过 1000 ⾏，全表扫描可能⽐使⽤索引更快。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
为了验证第四条，我们创建了⼀个⼩表，然后分别执⾏全表扫描和索引查询。
SELECT * FROM user WHERE status != 1;  -- 若⼤部分⾏ `status=1`，可能全表扫描
-- 优化⽅案：使⽤范围查询
SELECT * FROM user WHERE status < 1 OR status > 1;
SELECT * FROM table WHERE column LIKE '%xxx%';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 131 / 302

---

得出的结论的确是这样的，全表扫描更快⼀些。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 132 / 302

---

原因时当数据量很⼩时，全表扫描的成本很低，因为所有的数据可能都加载到内存中了，使⽤索引反⽽需要先查找
索引，再通过索引去找到实际的数据⾏，增加了额外的 I/O 寻址时间。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
性别字段要建⽴索引吗？
 
性别字段不适合建⽴单独索引。因为性别字段的区分度很低。
如果性别字段确实经常⽤于查询条件，数据规模也⽐较⼤，可以将性别字段作为联合索引的⼀部分，与区分度⾼的
字段⼀起，效果会好很多。
什么是区分度？
 
区分度是衡量⼀个字段在 MySQL 表中唯⼀值的⽐例。
区分度 = 字段的唯⼀值数量 / 字段的总记录数；越接近 1，就越适合作为索引。因为索引可以更有效地缩⼩查询范
围。
例如，⼀个表中有 1000 条记录，其中性别字段只有两个值（男、⼥），那么性别字段的区分度只有 0.002，就不
适合建⽴索引。
可以通过 COUNT(DISTINCT column_name) 和 COUNT(*) 的⽐值来计算字段的区分度。例如：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 133 / 302

---

什么样的字段适合加索引？
 
⼀句话回答：
⼀般来说，主键、唯⼀键、以及经常作为查询条件的字段最适合加索引。除此之外，字段的区分度要⾼，这样索引
才能起到过滤作⽤；如果字段经常⽤于表连接、排序或分组，也建议加索引。同时如果多个字段经常⼀起出现在查
询条件中，也可以建⽴联合索引来提升性能。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
查询条件中的⾼频字段，⽐如说WHERE⼦句中频繁⽤于等值查询、范围查询或者 IN 列表的字段。
多表连接时的关联字段，⽐如说 user.id 和 order.user_id。
参与排序或者分组的字段，可以直接利⽤索引的有序性，避免⽂件排序。
需要利⽤覆盖索引的字段，可以避免回表操作。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 技术⼆⾯⾯试原题：性别字段要建⽴索引吗？为什
么？什么是区分度？MySQL查看字段区分度的命令？
2. Java ⾯试指南（付费）收录的快⼿同学 2 ⼀⾯⾯试原题：什么样的字段适合加索引？什么不适合？
40.索引是不是建的越多越好？
 
索引不是越多越好。虽然索引可以加快查询，但也会带来写⼊变慢、占⽤更多存储空间、甚⾄让优化器选错索引的
⻛险。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
每次数据写⼊（INSERT/UPDATE/DELETE）时，MySQL 都需同步更新所有相关索引，索引越多，维护成本越⾼。
假如某表有 10 个索引，插⼊⼀⾏数据需更新 10 个 B+树结构，导致写⼊延迟增加 5~10 倍。
SELECT 
    COUNT(DISTINCT gender) / COUNT(*) AS gender_selectivity
FROM 
    users;
SELECT * FROM orders WHERE status = 'PAID' AND create_time > '2023-01-01';
-- 若`status`和`create_time`常组合查询，建联合索引`(status, create_time)`
SELECT * FROM user u JOIN order o ON u.id = o.user_id;  -- `user_id`需索引
SELECT * FROM product ORDER BY price DESC;       -- 单字段排序
SELECT category, COUNT(*) FROM product GROUP BY category;  -- 分组统计
-- 创建联合索引`(user_id, create_time)`
SELECT user_id, create_time FROM orders WHERE user_id = 100;  -- 覆盖索引⽣效
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 134 / 302

---

假如某表数据量 100GB，若建 5 个索引，总存储可能达到 200GB+。
索引过多时，优化器需评估更多可能的执⾏路径，可能导致选择困难症，优化器也会选错索引。
再⽐如说，已有联合索引 (A, B, C)，再单独建 (A) 或 (A, B) 索引即为冗余。
单表索引数量建议不超过 5 个，MySQL 官⽅建议单表索引总字段数 ≤ 表字段数的 30%。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
说说索引优化的思路？
 
⼀句话回答：
先通过慢查询⽇志找出性能瓶颈，然后⽤ EXPLAIN 分析执⾏计划，判断是否⾛了索引、是否回表、是否排序。接
着根据字段特性设计合适的索引，如选择区分度⾼的字段，使⽤联合索引和覆盖索引，避免索引失效的写法，最后
通过实测来验证优化效果。
1. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：索引优化的思路
memo：2025 年 4 ⽉ 2 ⽇修改⾄此，今天有球友说，拿到了百度的实习 offer，仅⽤了⼀个⽉的时间，可太强
了。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 135 / 302

---

41.
为什么 InnoDB 要使⽤ B+树作为索引？
 
⼀句话总结：
因为 B+ 树是⼀种⾼度平衡的多路查找树，能有效降低磁盘的 IO 次数，并且⽀持有序遍历和范围查询。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 136 / 302

---

查询性能⾮常⾼，其结构也适合 MySQL 按照⻚为单位在磁盘上存储。
像其他选项，⽐如说哈希表不⽀持范围查询，⼆叉树层级太深，B 树⼜不⽅便范围扫描，所以最终选择了 B+ 树。
再换⼀种回答：
相⽐哈希表：B+ 树⽀持范围查询和排序
相⽐⼆叉树和红⿊树：B+ 树更“矮胖”，层级更少，磁盘 IO 次数更少
相⽐ B 树：B+ 树的⾮叶⼦节点只存储键值，叶⼦节点存储数据并通过链表连接，⽀持范围查询
另外⼀种回答版本：
B+树是⼀种⾃平衡的多路查找树，和红⿊树、⼆叉平衡树不同，B+树的每个节点可以有 m 个⼦节点，⽽红⿊树和
⼆叉平衡树都只有 2 个。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 137 / 302

---

另外，和 B 树不同，B+树的⾮叶⼦节点只存储键值，不存储数据，⽽叶⼦节点存储了所有的数据，并且构成了⼀
个有序链表。
这样做的好处是，⾮叶⼦节点上由于没有存储数据，就可以存储更多的键值对，再加上叶⼦节点构成了⼀个有序链
表，范围查询时就可以直接通过叶⼦节点间的指针顺序访问整个查询范围内的所有记录，⽽⽆需对树进⾏多次遍
历。查询的效率⽐ B 树更⾼。
1. 推荐阅读：终于把 B 树搞明⽩了
2. 推荐阅读：⼀篇⽂章讲透 MySQL 为什么要⽤ B+树实现索引
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
先说说 B 树。
B 树是⼀种⾃平衡的多路查找树，和红⿊树、⼆叉平衡树不同，B 树的每个节点可以有 m 个⼦节点，⽽红⿊树和
⼆叉平衡树都只有 2 个。
换句话说，红⿊树、⼆叉平衡树是细⾼个，⽽ B 树是矮胖⼦。
再来说说内存和磁盘的 IO 读写。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 138 / 302

---

为了提⾼读写效率，从磁盘往内存中读数据的时候，⼀次会读取⾄少⼀⻚的数据，如果不满⼀⻚，会再多读点。
⽐如说查询只需要读取 2KB 的数据，但 MySQL 实际上会读取 4KB 的数据，以装满整⻚。⻚是 MySQL 进⾏内存
和磁盘交互的最⼩逻辑单元。
再⽐如说需要读取 5KB 的数据，实际上 MySQL 会读取 8KB 的数据，刚好两⻚。
因为读的次数越多，效率就越低。就好⽐我们在⼯地上搬砖，⼀次搬 10 块砖肯定⽐⼀次搬 1 块砖的效率要⾼，反
正我每次都搬 10 块（
）。
对于红⿊树、⼆叉平衡树这种细⾼个来说，每次搬的砖少，因为⼒⽓不够嘛，那来回跑的次数就越多。
通常 B+ 树⾼度为 3-4 层即可⽀持 TB 级数据，⽽每次查询只需 2-4 次磁盘 I/O，远低于⼆叉树或红⿊树的 
O(log2N) 复杂度
树越⾼，意味着查找数据时就需要更多的磁盘 IO，因为每⼀层都可能需要从磁盘加载新的节点。
B 树的节点通常与⻚的⼤⼩对⻬，这样每次从磁盘加载⼀个节点时，正好就是⼀⻚的⼤⼩。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 139 / 302

---

B 树的⼀个节点通常包括三个部分：
键值：即表中的主键
指针：存储⼦节点的信息
数据：除主键外的⾏数据
正所谓“祸兮福所倚，福兮祸所伏”，因为 B 树的每个节点上都存储了数据，就导致每个节点能存储的键值和指针变
少了，因为每⼀个节点的⼤⼩是固定的，对吧？
于是 B+树就来了，B+树的⾮叶⼦节点只存储键值，不存储数据，⽽叶⼦节点会存储所有的⾏数据，并且构成⼀个
有序链表。
这样做的好处是，⾮叶⼦节点由于没有存储数据，就可以存储更多的键值对，树就变得更加矮胖了，于是就更有劲
了，每次搬的砖也就更多了（
）。
相⽐ B 树，B+ 树的⾮叶⼦节点可容纳的键值更多，⼀个 16KB 的节点可存储约 1200 个键值，⼤幅降低树的
⾼度。
由此⼀来，查找数据进⾏的磁盘 IO 就更少了，查询的效率也就更⾼了。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 140 / 302

---

再加上叶⼦节点构成了⼀个有序链表，范围查询时就可以直接通过叶⼦节点间的指针顺序访问整个查询范围内的所
有记录，⽽⽆需对树进⾏多次遍历。
B 树就做不到这⼀点。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
B+树的叶⼦节点是单向链表还是双向链表？如果从⼤值向⼩值检索，如何操作？
 
B+树的叶⼦节点是通过双向链表连接的，这样可以⽅便范围查询和反向遍历。
当执⾏范围查询时，可以从范围的开始点或结束点开始，向前或向后遍历。
在需要对数据进⾏逆序处理时，双向链表⾮常有⽤。
如果需要在 B+树中从⼤值向⼩值进⾏检索，可以先定位到最右侧节点，找到包含最⼤值的叶⼦节点。从根节点开
始向右遍历树的⽅式实现。
定位到最右侧的叶⼦节点后，再利⽤叶节点间的双向链表向左遍历就好了。
为什么 MongoDB 的索引⽤ B树，⽽ MySQL ⽤ B+ 树？
 
MongoDB 通常以 JSON 格式存储⽂档，查询以单键查询（如 find({_id: 123}) ）为主。B 树的“节点既存键⼜
存数据”的特性允许查询在⾮叶⼦节点提前终⽌，从⽽减少 I/O 次数。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 141 / 302

---

特性
MongoDB (B树)
MySQL InnoDB (B+树)
数据模型
⽂档型数据库
关系型数据库
存储⽅式
数据⽂件+索引⽂件分离
聚簇索引数据与主键绑定存储
查询模式
侧重单⽂档查询
侧重范围查询和复杂连接
数据访问模式
随机访问为主
顺序访问更频繁
索引存储内容
⾮叶节点存储数据指针
只有叶节点存储数据
范围查询效率
需要多次树遍历
通过叶节点链表⾼效遍历
内存利⽤率
单个查询路径缓存更有效
适合批量扫描缓存
MySQL 的查询通常涉及范围（WHERE id > 100 ）、排序（ORDER BY ）、连接（JOIN ）等操作。B+ 树的叶⼦
节点是链表结构，天然⽀持顺序遍历，⽆需回溯⾄根节点或中序遍历，效率远⾼于 B 树。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
推荐阅读：为什么 MongoDB 索引⽤ B树，⽽ MySQL ⽤ B+ 树？
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的字节跳动商业化⼀⾯的原题：说说 B+树，为什么 3 层容纳 2000W 条，
为什么 2000w 条数据查的快
2. Java ⾯试指南（付费）收录的国企⾯试原题：说说 MySQL 的底层数据结构，B 树和 B+树的区别
3. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：MySQL 为什么选⽤ B+树
4. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 E 第⼆个部⻔ Java 后端技术⼀⾯⾯试原题：说⼀说 mysql 
索引的底层机制
5. Java ⾯试指南（付费）收录的京东⾯经同学 1 Java 技术⼀⾯⾯试原题：MySQL 索引结构，建⽴索引的
策略
6. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 16 ⼀⾯⾯试原题：MySQL 索引结构，为什么⽤ 
B+树？
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 142 / 302

---

7. Java ⾯试指南（付费）收录的⼩公司⾯经同学 5 Java 后端⾯试原题：数据库索引讲⼀下，然后为什么
会加快查询速度，我讲到了 B+树，然后问了 B 数与 B+树区别
8. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：B+树的⻚是单向
链表还是双向链表？如果从⼤值向⼩值检索，如何操作？
9. Java ⾯试指南（付费）收录的得物⾯经同学 1 ⾯试原题：项⽬索引，MySQL索引，mongoDB为什么⽤
的B树，⼆者⽐较
10. Java ⾯试指南（付费）收录的oppo ⾯经同学 8 后端开发秋招⼀⾯⾯试原题：Mysql索引的数据结构，
为什么选择这样的数据结构
11. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：索引
12. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：B+树？
13. Java ⾯试指南（付费）收录的美团⾯经同学 15 点评后端技术⾯试原题：索引的数据结构
14. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：B+树了解吗？底层呢？为什么这么⽤？
15. Java ⾯试指南（付费）收录的滴滴⾯经同学 3 ⽹约⻋后端开发⼀⾯原题：MySQL索引原理，B+树更扁 
有什么好处
memo：2025 年 4 ⽉ 3 ⽇修改⾄此，今天有球友说，拿到了美团的实习 offer，恭喜啊。
42.
⼀棵B+树能存储多少条数据呢？
 
⼀句话回复：
⼀棵 B+ 树能存多少数据，取决于它的分⽀因⼦和⾼度。在 InnoDB 中，⻚的默认⼤⼩为 16KB，当主键为 bigint 
时，3 层 B+ 树通常可以存储约 2000 万条数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 143 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
先来看⼀下计算公式：
再来看⼀下关键参数：
①、⻚⼤⼩，默认 16KB
②、主键⼤⼩，假设是 bigint 类型，那么它的⼤⼩就是 8 个字节。
③、⻚指针⼤⼩，InnoDB 源码中设置为 6 字节，4 字节⻚号 + 2 字节⻚内偏移。
所以⾮叶⼦节点可以存储 16384/14(键值+指针)=1170 个这样的单元。
当层⾼为 2 时，根节点可以存储 1170 个指针，指向 1170 个叶⼦节点，所以总数据量为 1170×16 =18720 条。
当层⾼为 3 时，根节点指向 1170 个⾮叶⼦节点，每个⾮叶⼦节点再指向 1170 个叶⼦节点，所以总数据量为 
1170×1170×16≈21,902,400 条（约2,190万条）记录。
推荐阅读：清幽之地：InnoDB ⼀棵 B+树可以存放多少⾏数据？
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
最⼤记录数 = (分⽀因⼦)^(树⾼度-1) × 叶⼦节点容量
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 144 / 302

---

现在有⼀张表 2kw 数据，我这个 b+树的⾼度有⼏层？
 
对于 2KW 条数据来说，B+树的⾼度为 3 层就够了。
推荐阅读：Innodb 引擎中 B+树⼀般有⼏层？能容纳多少数据量？
每个叶⼦节点能存放多少条数据？
 
如果单⾏数据⼤⼩为 1KB，那么每⻚可存储约 16 ⾏（16KB/1KB）数据。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
假设有这样⼀个表结构：
那么⼀⾏数据的⼤⼩为：8 + 50 + 1 + 30 = 89 字节。
⾏格式的开销为：⾏头 5 字节+指针 6 字节+可变⻓度字段开销 2 字节（name 和 email 各占 1 字节）+ NULL 位图 
1 字节 = 14 字节。
所以每⾏数据的实际⼤⼩为：89 + 14 = 103 字节。
每⻚⼤⼩默认为 16KB，那么每⻚最多可以存储 16384 / 103 ≈ 158 ⾏数据。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的字节跳动商业化⼀⾯的原题：说说 B+树，为什么 3 层容纳 2000W 条，
为什么 2000w 条数据查的快
2. Java ⾯试指南（付费）收录的奇安信⾯经同学 1 Java 技术⼀⾯⾯试原题：innodb 使⽤数据⻚存储数
据？默认数据⻚⼤⼩ 16K，我现在有⼀张表，有 2kw 数据，我这个 b+树的⾼度有⼏层？
3. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：⼀张表最多存多少数据（我答得
2kw，根据b+树的三层⾼度计算）
4. Java ⾯试指南（付费）收录的得物⾯经同学 1 ⾯试原题：MySQL B+树的度数越⼤越好吗，⼀般设多少
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY,        -- 8字节
  `name` VARCHAR(255) NOT NULL,   -- 实际⻓度50字节（UTF8MB4，每个字符最多4字节）
  `age` TINYINT,                  -- 1字节
  `email` VARCHAR(255)            -- 实际⻓度30字节，可为NULL
) ROW_FORMAT=COMPACT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 145 / 302

---

5. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：InnoDB中⼀个三层的B+树能存多
少数据？每个叶⼦节点能存放多少条数据？
memo：2025 年 4 ⽉ 4 ⽇修改⾄此，今天有球友问，有没有英⽂版的⾯渣逆袭，他⼈在国外留学，国外也开始卷
⼋股了吗，真的离谱。
43.索引为什么⽤ B+树不⽤普通⼆叉树？
 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 146 / 302

---

普通⼆叉树的每个节点最多有两个⼦节点。当数据按顺序递增插⼊时，⼆叉树会退化成链表，导致树的⾼度等于数
据量。
此时查找 id=7 就需要 7 次 I/O 操作，相当于全表扫描。⽽ B+ 树作为多叉平衡树，能将数亿级的数据量控制在 3-4 
层的树⾼，能极⼤减少磁盘的 I/O 次数。
为什么不⽤平衡⼆叉树呢？
 
平衡⼆叉树虽然解决了普通⼆叉树的退化问题，但每个节点最多只有两个⼦节点的问题依然存在。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 147 / 302

---

1. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：MySQL 索引为什
么使⽤ B+树⽽不是⽤别的数据结构？
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：为什么不⽤⼆叉树？为什么
不⽤AVL树？
44.
为什么⽤ B+ 树⽽不⽤ B 树呢？
 
B+ 树相⽐ B 树有 3 个显著优势：
第⼀，B 树的每个节点既存储键值，⼜存储数据和指针，导致单节点存储的键值数量较少。
⼀个 16KB 的 InnoDB ⻚，如果数据较⼤，B 树的⾮叶⼦节点只能容纳⼏⼗个键值，⽽ B+ 树的⾮叶⼦节点可以容
纳上千个键值。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 148 / 302

---

第⼆，B 树的范围查询需要通过中序遍历逐层回溯；⽽ B+ 树的叶⼦节点通过双向链表顺序连接，范围查询只需定
位起始点后顺序遍历链表即可，没有回溯开销。
第三，B 树的数据可能存储在任意节点，假如⽬标数据恰好位于根节点或上层节点，查询仅需 1-2 次 I/O；但如果
数据位于底层节点，则需多次 I/O，导致查询时间波动较⼤。
⽽ B+ 树的所有数据都存储在叶⼦节点，查询路径的⻓度是固定的，时间稳定为 O(logN)，对 MySQL 在⾼并发场
景下的稳定性⾄关重要。
想要了解 B 树和 B+树的更多区别，推荐阅读：
GitHub：B 树和 B+树详解
思否：⾯试官问你 B 树和 B+树，就把这篇⽂章丢给他
极客时间：为什么⽤ B+树来做索引？
⼀颗剽悍的种⼦：⽤ 16 张图就给你讲明⽩ MySQL 为什么要⽤ B+树做索引
B+树的时间复杂度是多少？
 
O(logN)。
树的⾼度 h 为：
 
其中 N 是数据总量，m 是阶数。每层需要做⼀次⼆分查找，复杂度为 $O(\log m)$。
总复杂度为：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 149 / 302

---

为什么⽤ B+树不⽤跳表呢？
 
跳表本质上还是链表结构，只不过把某些节点抽到上层做了索引。
⼀条数据⼀个节点，如果需要存放 2000 万条数据，且每次查询都要能达到⼆分查找的效果，那么跳表的⾼度⼤约
为 24 层（2 的 24 次⽅）。
在最坏的情况下，这 24 层数据分散在不同的数据⻚，查找⼀次数据就需要 24 次磁盘 I/O。
⽽ 2000 万条数据在 B+树中只需要 3 层就可以了。
B+树的范围查找怎么做的？
 
⼀句话回答：
先通过索引路径定位到第⼀个满⾜条件的叶⼦节点，然后顺着叶⼦节点之间的链表向右/向左扫描，直到超过范
围。
详细版：
B+ 树索引的范围查找主要依赖叶⼦节点之间的双向链表来完成。
第⼀步，从 B+ 树的根节点开始，通过索引键值逐层向下，找到第⼀个满⾜条件的叶⼦节点。
第⼆步，利⽤叶⼦节点之间的双向链表，从起始节点开始，依次向后遍历每个节点。当索引值超过查询范围，或者
遍历到链表末尾时，终⽌查询。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
⽐如说在下⾯这棵 B+ 树上查找 45。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 150 / 302

---

第⼀步，从根节点开始，因为⽐ 25 ⼤，所以从右⼦树开始。因为 45 ⽐ 35⼤，所以和右边的索引⽐较，右侧的索
引也是 45，所以继续往右⼦树查找。
第⼆步，从叶⼦节点 45 开始，依次遍历，找到 45。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 151 / 302

---

---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
了解快排吗？
 
快速排序使⽤分治法将⼀个序列分为较⼩和较⼤的 2 个⼦序列，然后递归排序两个⼦序列，由东尼·霍尔在 1960 年
提出。
其核⼼思想是：
1. 选择⼀个基准值。
2. 将数组分为两部分，左边⼩于基准值，右边⼤于或等于基准值。
3. 对左右两部分递归排序，最终合并。
public static void quickSort(int[] arr, int low, int high) {
    if (low < high) {
        int pivotIndex = partition(arr, low, high);
        quickSort(arr, low, pivotIndex - 1);
        quickSort(arr, pivotIndex + 1, high);
    }
}
private static int partition(int[] arr, int low, int high) {
    int pivot = arr[high];
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 152 / 302

---

推荐链接：快速排序
1. Java ⾯试指南（付费）收录的⽀付宝⾯经同学 2 春招技术⼀⾯⾯试原题：聚簇索引和⾮聚簇索引的区
别？B+树叶⼦节点除了存数据还有什么？
2. Java ⾯试指南（付费）收录的奇安信⾯经同学 1 Java 技术⼀⾯⾯试原题：b 树和 b+树有什么区别
3. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：MySQL 索引为什
么使⽤ B+树⽽不是⽤别的数据结构？
4. Java ⾯试指南（付费）收录的字节跳动⾯经同学 8 Java 后端实习⼀⾯⾯试原题：mysql b+树和b树的区
别
5. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：B+树有哪些优点
6. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：为什么⽤b+树不⽤b树
7. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：索引为什么⽤B+树不⽤B树 时间复
杂度深究  b+树 快速排序...
45.B+树索引和 Hash 索引有什么区别？
 
简版回答：
B+ 树索引⽀持范围查询、有序扫描，是 InnoDB 的默认索引结构。
    int i = low - 1;
    for (int j = low; j < high; j++) {
        if (arr[j] <= pivot) {
            i++;
            swap(arr, i, j);
        }
    }
    swap(arr, i + 1, high);
    return i + 1;
}
private static void swap(int[] arr, int i, int j) {
    int temp = arr[i];
    arr[i] = arr[j];
    arr[j] = temp;
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 153 / 302

---

Hash 索引只⽀持等值查找，速度快但功能弱，常⻅于 Memory 引擎。
稍微详细⼀点的回答：
B+ 树索引是⼀种平衡多路搜索树，所有数据存储在叶⼦节点上，⾮叶⼦节点仅存储索引键。叶⼦节点通过指针连
接形成有序链表，天然⽀持排序。
并且⽀持范围查询、模糊查询，是 InnoDB 默认的索引结构。
Hash 索引基于哈希函数将键值映射到固定⻓度的哈希值，通过哈希值定位数据存储的位置。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 154 / 302

---

完全⽆序，只⽀持等值查询，常⻅于 Memory 引擎。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
因为 B+ 树是 InnoDB 的默认索引类型，所以创建 B+ 树的时候不需要指定索引类型。
可以通过 UNIQUE HASH 创建哈希索引：
InnoDB 并不提供直接创建哈希索引的选项，因为 B+ 树索引能够很好地⽀持范围查询和等值查询，满⾜了⼤多数
数据库操作的需要。
不过，InnoDB 内部使⽤了⼀种名为“⾃适应哈希索引”（Adaptive Hash Index, AHI）的技术，当某些索引值频繁访
问时，InnoDB 会在 B+ 树基础上⾃动创建哈希索引，兼具两者的优点。
可通过 SHOW VARIABLES LIKE 'innodb_adaptive_hash_index'; 查看⾃适应哈希索引的状态。
如果返回的值是 ON，说明⾃适应哈希索引是开启的。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：为什么不⽤hash索引
46.
聚族索引和⾮聚族索引有什么区别？
 
聚簇索引的叶⼦节点存储了完整的数据⾏，数据和索引是在⼀起的。InnoDB 的主键索引就是聚簇索引，叶⼦节点
不仅存储了主键值，还存储了其他列的值，因此按照主键进⾏查询的速度会⾮常快。
CREATE TABLE example_btree (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    INDEX name_index (name)
) ENGINE=InnoDB;
CREATE TABLE example_hash (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    UNIQUE HASH (name)
) ENGINE=MEMORY;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 155 / 302

---

每个表只能有⼀个聚簇索引，通常由主键定义。如果没有显式指定主键，InnoDB 会隐式创建⼀个隐藏的主键索引 
row_id。
⾮聚簇索引的叶⼦节点只包含了主键值，需要通过回表按照主键去聚簇索引查找其他列的值，唯⼀索引、普通索引
等⾮主键索引都是⾮聚簇索引。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 156 / 302

---

每个表都可以创建多个⾮聚簇索引，如果不想回表的话，可以通过覆盖索引把要查询的字段也放到索引中。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
⼀张表只能有⼀个聚簇索引。
主键 id 是聚簇索引，B+ 树的叶⼦节点直接存储了 (id, name, age)。
⼀张表可以有多个⾮聚簇索引。
idx_name 是⾮聚簇索引，叶⼦节点存的是 name -> id，查整⾏数据要回表。
idx_age 也是⾮聚簇索引，叶⼦节点存的是 age -> id，查整⾏数据也要回表。
CREATE TABLE user (
  id INT PRIMARY KEY,
  name VARCHAR(100),
  age INT
);
CREATE INDEX idx_name ON user(name);
CREATE INDEX idx_age ON user(age);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 157 / 302

---

想要了解更多聚簇索引和⾮聚簇索引，推荐阅读：
磊哥：聚簇索引和⾮聚簇索引有什么区别？
浅谈聚簇索引与⾮聚簇索引
聚簇索引、⾮聚簇索引、联合索引、唯⼀索引
松哥：再聊 MySQL 聚簇索引
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：mysql：聚簇索引和⾮聚簇索引区别
2. Java ⾯试指南（付费）收录的⽀付宝⾯经同学 2 春招技术⼀⾯⾯试原题：聚簇索引和⾮聚簇索引的区
别？B+树叶⼦节点除了存数据还有什么？
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：聚簇索引是什么？⾮
聚簇索引是什么？
4. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 16 ⼀⾯⾯试原题：聚簇索引和⾮聚簇索引的区别？
5. Java ⾯试指南（付费）收录的快⼿⾯经同学 1 部⻔主站技术部⾯试原题：Mysql 的聚簇索引和⾮聚簇索
引的区别是什么?
6. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：mysql的聚簇索引是什么
7. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：MySQL的索引怎么存储的？每个索
引⼀个B+树，还是多个索引放⼀个B+树？叶⼦节点中存的是什么数据？
memo：2025 年 4 ⽉ 5 ⽇修改⾄此，今天有拿到美团暑期实习的球友说，简历找⼆哥修改了两次，基本上不卡第
⼀学历的都有⾯，很棒。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 158 / 302

---

47.
回表了解吗？
 
当使⽤⾮聚簇索引进⾏查询时，MySQL 需要先通过⾮聚簇索引找到主键值，然后再根据主键值回到聚簇索引中查
找完整数据⾏，这个过程称为回表。
假设现在有⼀张⽤户表 users：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 159 / 302

---

执⾏查询：
查询过程如下：
第⼀步，MySQL 使⽤ name 列上的⾮聚簇索引查找所有 name = '王⼆' 的主键 id。
第⼆步，使⽤主键 id 到聚簇索引中查找完整记录。
回表的代价是什么？
 
回表通常需要访问额外的数据⻚，如果数据不在内存中，还需要从磁盘读取，增加 I/O 开销。
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    age INT,
    email VARCHAR(50),
    INDEX (name)
);
SELECT * FROM users WHERE name = '王⼆';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 160 / 302

---

可通过覆盖索引或者联合索引来避免回表。
什么情况下会触发回表？
 
-- 原表结构
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    age INT,
    INDEX idx_name (name)
);
-- 需要查询name和age
SELECT name, age FROM users WHERE name = '张三';
-- 这会回表，因为age不在idx_name索引中
-- 优化⽅案1：创建包含age的联合索引
ALTER TABLE users ADD INDEX idx_name_age (name, age);
-- 现在同样的查询不需要回表
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 161 / 302

---

第⼀，当查询字段不在⾮聚簇索引中时，必须回表到主键索引获取数据。
第⼆，查询字段包含⾮索引列（如 SELECT *），必然触发回表。
回表记录越多好吗？
 
回表记录越多，通常代表性能越差，因为每条记录都需要通过主键再查询⼀次完整数据。这个过程涉及内存访问或
磁盘 IO，尤其当缓存命中率不⾼时，回表会严重影响查询效率。
了解 MRR 吗？
 
MRR 是 InnoDB 为了解决回表带来的⼤量随机 IO 问题⽽引⼊的⼀种优化策略。
它会先把⾮聚簇索引查到的主键值列表进⾏排序，再按顺序去主键索引中批量回表，将随机 I/O 转换为顺序 I/O，
以减少磁盘寻道时间。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
可通过 SHOW VARIABLES LIKE 'optimizer_switch'; 查看 MRR 是否启⽤。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 162 / 302

---

其中 mrr=on 表示启⽤ MRR，mrr_cost_based=on 表示基于成本决定使⽤ MRR。
另外可以通过 show variables like 'read_rnd_buffer_size'; 查看 MRR 的缓冲区⼤⼩，默认是 256KB。
我们来创建⼀个表，插⼊⼀些数据，然后执⾏⼀个查询来演示 MRR 的效果。
查看 MRR 开启和关闭时的性能数据：
CREATE DATABASE IF NOT EXISTS mrr_test; 
USE mrr_test; 
CREATE TABLE IF NOT EXISTS orders (id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, 
order_date DATE, amount DECIMAL(10,2), status VARCHAR(20), INDEX idx_user_date(user_id, 
order_date));
DELIMITER //
CREATE PROCEDURE generate_test_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100000 DO
        INSERT INTO orders (user_id, order_date, amount, status)
        VALUES (
            FLOOR(1 + RAND() * 1000),  -- Random user_id between 1 and 1000
            DATE_ADD('2023-01-01', INTERVAL FLOOR(RAND() * 365) DAY),  -- Random date in 
            ROUND(10 + RAND() * 990, 2),  -- Random amount between 10 and 1000
            ELT(1 + FLOOR(RAND() * 3), 'completed', 'pending', 'cancelled')  -- Random 
status
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;
CALL generate_test_data();
DROP PROCEDURE generate_test_data;"
-- 确保MRR开启并设置⾜够⼤的缓冲区
SET SESSION optimizer_switch='mrr=on,mrr_cost_based=off';
SET SESSION read_rnd_buffer_size = 16*1024*1024;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 163 / 302

---

可以看到 MRR 开启时的结果对⽐：
-- 清理缓存和状态
FLUSH STATUS;
FLUSH TABLES;
-- 强制使⽤⼆级索引并回表查询（通过选择未被索引的列）
SELECT 'Raw data access pattern with MRR ON' as test_case;
SELECT /*+ MRR(orders_mrr_test) */ id, shipping_address, customer_name
FROM orders_mrr_test FORCE INDEX(idx_user_date)
WHERE user_id IN (100,200,300,400,500,600,700,800,900,1000)
AND order_date BETWEEN '2023-03-01' AND '2023-04-01'
LIMIT 15;
-- 显示处理器状态
SHOW STATUS LIKE 'Handler_%';
SHOW STATUS LIKE '%mrr%';
-- 对⽐：关闭MRR
SET SESSION optimizer_switch='mrr=off,mrr_cost_based=off';
FLUSH STATUS;
FLUSH TABLES;
SELECT 'Raw data access pattern with MRR OFF' as test_case;
SELECT id, shipping_address, customer_name
FROM orders_mrr_test FORCE INDEX(idx_user_date)
WHERE user_id IN (100,200,300,400,500,600,700,800,900,1000)
AND order_date BETWEEN '2023-03-01' AND '2023-04-01'
LIMIT 15;
-- 显示处理器状态
SHOW STATUS LIKE 'Handler_%';
SHOW STATUS LIKE '%mrr%';
-- 显示详细的执⾏计划
EXPLAIN FORMAT=TREE
SELECT /*+ MRR(orders_mrr_test) */ id, shipping_address, customer_name
FROM orders_mrr_test FORCE INDEX(idx_user_date)
WHERE user_id IN (100,200,300,400,500,600,700,800,900,1000)
AND order_date BETWEEN '2023-03-01' AND '2023-04-01';"
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 164 / 302

---

Wrap 也给出了对应的结果说明：
也可以在 explain 中确认 MRR 的使⽤情况。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 165 / 302

---

---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：使⽤⾮聚簇索引如何
查找数据？
2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 技术⼆⾯⾯试原题：回表记录越多好吗？（回表的代
价）
memo：2025 年 4 ⽉ 6 ⽇修改⾄此，今天帮球友修改简历的时候，看到有球友写技术派到简历上，很不错，推荐
给⼤家。
48.
联合索引了解吗？（补充）
 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 166 / 302

---

2024 年 11 ⽉ 22 ⽇增补
联合索引就是把多个字段放在⼀个索引⾥，但必须遵守“最左前缀”原则，只有从第⼀个字段开始连续使⽤，索引才
会⽣效。
联合索引会按字段顺序构建B+树。例如（age, name）索引会先按照 age 排序，age 相同则按照 name 排序，若
两者都相同则按主键排序，确保叶⼦节点⽆重复索引项。
创建(A,B,C) 联合索引相当于同时创建了(A) 、(A,B) 和(A,B,C) 三个索引。
联合索引底层的存储结构是怎样的？
 
联合索引在底层采⽤ B+ 树结构进⾏存储，这⼀点与单列索引相同。
-- 创建联合索引
CREATE INDEX idx_order_user_product ON orders(user_id, product_id, create_time)
-- ⾼效查询
SELECT * FROM orders 
WHERE user_id=1001 AND product_id=2002
ORDER BY create_time DESC
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 167 / 302

---

与单列索引不同的是，联合索引的每个节点会存储所有索引列的值，⽽不仅仅是第⼀列的值。例如，对于联合索引
(a,b,c) ，每个节点都包含 a、b、c 三列的值。
联合索引的叶⼦节点存的什么内容?
 
联合索引属于⾮聚簇索引，叶⼦节点存储的是联合索引各列的值和对应⾏的主键值，⽽不是完整的数据⾏。查询⾮
索引字段时，需要通过主键值回表到聚簇索引获取完整数据。
⾮叶⼦节点示例：  
[(a=1, b=2, c=3) → ⼦节点1, (a=5, b=3, c=1) → ⼦节点2]
叶⼦节点示例（InnoDB）：  
(a=1, b=2, c=3) → PK=100 | (a=1, b=2, c=4) → PK=101  
（通过指针连接形成双向链表）
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 168 / 302

---

例如索引(a, b) 的叶⼦节点会完整存储(a, b) 的值，并按字段顺序排序（如 a 优先，a 相同则按 b 排序）。如
果主键是 id，叶⼦节点会存储 (a, b, id) 的组合。
1. Java ⾯试指南（付费）收录的百度同学 4 ⾯试原题：联合索引底层存储结构(和其他种类的索引存储结
构有什么区别?)联合索引的叶⼦节点存的什么内容?
了腾讯天美的 offer，真的太强了。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 169 / 302

---

49.
覆盖索引了解吗？
 
覆盖索引指的是：查询所需的字段全部都在索引中，不需要回表，从索引⻚就能直接返回结果。
empname 和 job 两个字段是⼀个联合索引，⽽查询也恰好是这两个字段，这时候单次查询就可以达到⽬
的，不需要回表。
可以将⾼频查询的字段（如 WHERE 条件和 SELECT 列）组合为联合索引，实现覆盖索引。
例如：
这样查询的时候就可以⾛索引：
CREATE INDEX idx_empname_job ON employee(empname, job);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 170 / 302

---

普通索引只⽤于加速查询条件的匹配，⽽覆盖索引还能直接提供查询结果。
⼀个表（name, sex,age,id），select age,id,name from tblname where 
name='paicoding';怎么建索引
 
由于查询条件有 name 字段，所以最少应该为 name 字段添加⼀个索引。、
查询结果中还需要 age 、id 字段，可以为这三个字段创建⼀个联合索引，利⽤覆盖索引，直接从索引中获取数
据，减少回表。
1. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：了解覆盖索引吗
2. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：覆盖索引，回表？
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 13 Java 后端⼆⾯⾯试原题：⼀个表（name, 
sex,age,id），select age,id,name from tblname where name='paicoding';怎么建索引
50.
什么是最左前缀原则？
 
最左前缀原则指的是：MySQL 使⽤联合索引时，必须从最左边的字段开始匹配，才能命中索引。
假设有⼀个联合索引 (A, B, C) ，其⽣效条件如下：
SELECT empname, job FROM employee WHERE empname = '王⼆' AND job = '程序员';
CREATE INDEX idx_name ON tblname(name);
CREATE INDEX idx_name_age_id ON tblname (name, age, id);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 171 / 302

---

查询条件
是否触发索引？
说明
WHERE A = 1
 是
使⽤索引的第⼀列
WHERE A = 1 AND B = 2
 是
使⽤索引的前两列
WHERE A = 1 AND B = 2 AND C = 3
 是
使⽤索引的全部列
WHERE B = 2
 否
跳过左侧列 A，索引失效
WHERE B = 2 AND C = 3
 否
⽆左侧列，索引失效
WHERE A = 1 AND C = 3
 部分⽣效
仅⽤ A 列，C 列⽆法利⽤索引优化
WHERE A = 1
 是
使⽤索引的第⼀列
WHERE A = 1 AND B = 2
 是
使⽤索引的前两列
WHERE A = 1 AND B = 2 AND C = 3
 是
使⽤索引全部列（最理想情况）
WHERE B = 2
 否
跳过左侧列 A，索引失效
WHERE B = 2 AND C = 3
 否
⽆左侧列，索引失效
WHERE A = 1 AND C = 3
 部分⽣效
仅⽤ A 列，C 列⽆法利⽤索引优化
如果排序或分组的列是最左前缀的⼀部分，索引还可以加速操作。
范围查询后的列还能⽤索引吗？
 
范围查询只能应⽤于最左前缀的最后⼀列。范围查询之后的列⽆法使⽤索引。
为什么不从最左开始查，就⽆法匹配呢？
 
⼀句话回答：
因为联合索引在 B+ 树中是按照最左字段优先排序构建的，如果跳过最左字段，MySQL ⽆法判断查找范围从哪⾥
开始，⾃然也就⽆法使⽤索引。
SQL
-- 索引(a,b)
SELECT * FROM table WHERE a = 1 ORDER BY b; -- 可以利⽤索引排序
SQL
-- 索引(a,b,c)
SELECT * FROM table WHERE a = 1 AND b > 2 AND c = 3; 
-- 只能使⽤a和b，c⽆法使⽤索引
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 172 / 302

---

⽐如有⼀个 user 表，我们给 name 和 age 建⽴了⼀个联合索引 (name, age) 。
联合索引在 B+ 树中按照从左到右的顺序依次建⽴搜索树，name 在左，age 在右。
当我们使⽤ where name= '王⼆' and age = '20' 去查询的时候， B+ 树会优先⽐较 name 来确定下⼀步应该
搜索的⽅向，往左还是往右。
如果 name 相同的时候再⽐较 age。
但如果查询条件没有 name，就不知道应该怎么查了，因为 name 是 B+树中的前置条件，没有 name，索引就派
不上⽤场了。
联合索引 (a, b)，where a = 1 和 where b = 1，效果是⼀样的吗
 
不⼀样。
WHERE a = 1 能命中联合索引，因为 a 是联合索引的第⼀个字段，符合最左前缀匹配原则。⽽ WHERE b = 1 ⽆
法命中联合索引，因为缺少 a 的匹配条件，MySQL 会全表扫描。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
我们来验证⼀下，假设有⼀个 ab 表，建⽴了联合索引 (a, b) ：
插⼊数据：
执⾏查询：
ALTER TABLE user add INDEX comidx_name_phone (name,age);
CREATE TABLE ab (
    a INT,
    b INT,
    INDEX ab_index (a, b)
);
INSERT INTO ab (a, b) VALUES (1, 2), (1, 3), (2, 1), (3, 3), (2, 2);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 173 / 302

---

通过 explain 可以看到，WHERE a = 1 使⽤了联合索引，⽽ WHERE b = 1 需要全表扫描，依次检查每⼀⾏。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
假如有联合索引 abc，下⾯的 sql 怎么⾛的联合索引？
 
第⼀条 SQL 语句包含条件 a = 2 和 b = 2，刚好符合联合索引的前两列。
第⼆条 SQL 语句由于未使⽤最左前缀中的 a，会触发全表扫描。
select * from t where a = 2 and b = 2;
select * from t where b = 2 and c = 2;
select * from t where a > 2 and b = 2;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 174 / 302

---

第三条 SQL 语句在范围条件 a > 2 之后，索引后会停⽌匹配，b = 2 的条件需要额外过滤。
(A,B,C) 联合索引 select * from tbn where a=? and b in (?,?) and c>? 会
⾛索引吗？
 
2024 年 03 ⽉ 15 ⽇增补。
这个查询会命中联合索引，因为 a 是等值匹配，b 是 IN 等值多匹配，c 是 b 之后的范围条件，符合最左前缀原
则。
1. 对于 a=? ：这是⼀个精确匹配，并且是联合索引的第⼀个字段，所以⼀定会命中索引。
2. 对于 b IN (?, ?) ：等价于 b=? OR b=?，属于多值匹配，并且是联合索引的第⼆个字段，所以也会命中索
引。
3. 对于 c>? ：这是⼀个范围条件，属于联合索引的第三个字段，也会命中索引。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 175 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
来验证⼀下。
第⼀步，建表。
第⼆步，创建索引。
第三步，插⼊数据。
第四步，执⾏查询。
从 EXPLAIN 输出结果来看，我们可以得到 MySQL 是如何执⾏查询的⼀些关键信息：
type: 查询类型，这⾥是 range ，表示 MySQL 使⽤了范围查找，这是因为查询条件包含了 > 操作符。
possible_keys: 可能被⽤来执⾏查询的索引，这⾥是 idx_abc ，表示 MySQL 认为 idx_abc 索引会⽤于查
询优化。
key: 实际⽤来执⾏查询的索引，也是 idx_abc ，这确定这条查询命中了联合索引。
CREATE TABLE tbn (A INT, B INT, C INT, D TEXT);
CREATE INDEX idx_abc ON tbn (A, B, C);
INSERT INTO tbn VALUES (1, 2, 3, 'First');
INSERT INTO tbn VALUES (1, 2, 4, 'Second');
INSERT INTO tbn VALUES (1, 3, 5, 'Third');
INSERT INTO tbn VALUES (2, 2, 3, 'Fourth');
INSERT INTO tbn VALUES (2, 3, 4, 'Fifth');
EXPLAIN SELECT * FROM tbn WHERE A=1 AND B IN (2, 3) AND C>3\G
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 176 / 302

---

Extra: 提供了关于查询执⾏的额外信息。Using index condition 表示 MySQL 使⽤了索引下推（Index 
Condition Pushdown，ICP），这是 MySQL 的⼀个优化⽅式，它允许在索引层⾯过滤数据。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
联合索引的⼀个场景题：(a,b,c)联合索引，(b,c)是否会⾛索引吗？
 
2024 年 04 ⽉ 06 ⽇增补
根据最左前缀原则，(b,c) 查询不会⾛索引。
因为联合索引 (a,b,c) 中，a 是最左边的列，联合索引在创建索引树的时候需要先有 a，然后才会有 b 和 c。⽽查询
条件中没有包含 a，所以 MySQL ⽆法利⽤这个索引。
建⽴联合索引(a,b,c)，where c = 5 是否会⽤到索引？为什么？
 
2024 年 04 ⽉ 08 ⽇增补
不会。只有索引的第三列 c 被⽤作查询条件，⽽前两列 a 和 b 都没有被使⽤。这不符合最左前缀原则。
EXPLAIN SELECT * FROM tbn WHERE B=1 AND C=1\G
EXPLAIN SELECT * FROM tbn WHERE C=5\G
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 177 / 302

---

sql中使⽤like，如果遵循最左前缀匹配，查询是不是⼀定会⽤到索引？
 
2024 年 11 ⽉ 04 ⽇增补
如果查询模式是后缀通配符 LIKE 'prefix%' ，且该字段有索引，优化器通常会使⽤索引。否则即便是遵循最左
前缀匹配，LIKE 字段也⽆法命中索引。
如 age = 18 and name LIKE '%xxx' ，MySQL 会先使⽤联合索引 age_name 找到 age 符合条件的所有⾏，然
后再全表扫描进⾏ name 字段的过滤。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 178 / 302

---

type: ref 表示使⽤索引查找匹配某个值的所有⾏。
如果是后缀通配符，如 age = 18 and name LIKE 'xxx%' ，MySQL 会直接使⽤联合索引 age_name 找到所有符
合条件的⾏。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 179 / 302

---

type 为 range，表示 MySQL 使⽤了索引范围扫描，filtered 为 100.00% ，表示在扫描的⾏中，所有的⾏都满
⾜ WHERE 条件。
1. Java ⾯试指南（付费）收录的字节跳动商业化⼀⾯的原题：(A,B,C) 联合索引 select * from tbn 
where a=? and b in (?,?) and c>? 会⾛索引吗？
2. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：联合索引 abc，
a=1,c=1/b=1,c=1/a=1,c=1,b=1 ⾛不⾛索引
3. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：联合索引的⼀个场景题：
(a,b,c)联合索引，(b,c)是否会⾛索引
4. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：建⽴联合索引(a,b,c)，
where c = 5 是否会⽤到索引？为什么？
5. Java ⾯试指南（付费）收录的美团⾯经同学 15 点评后端技术⾯试原题：sql中使⽤like，如果遵循最左
前缀匹配，查询是不是⼀定会⽤到索引？
6. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 3 Java 技术⼀⾯⾯试原题：说⼀下数据库索引，最左匹
配原则和索引的结构
7. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 16 ⼀⾯⾯试原题：说说最左前缀原则
8. Java ⾯试指南（付费）收录的招银⽹络科技⾯经同学 9 Java 后端技术⼀⾯⾯试原题：Mysql联合索引的
设计原则
9. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：联合索引 (a, b)，where a = 1 
和 where b = 1，效果是⼀样的吗
10. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：（联合索引）下⾯怎么⾛的
索引？
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 180 / 302

---

11. Java ⾯试指南（付费）收录的滴滴⾯经同学 3 ⽹约⻋后端开发⼀⾯原题：联合索引 (a, b, c)，where b 
= 1，能⾛吗，where a = 1，能⾛吗
51.
什么是索引下推？
 
索引下推是指：MySQL 把 WHERE 条件尽可能“下推”到索引扫描阶段，在存储引擎层提前过滤掉不符合条件的记
录。
当查询条件包含索引列但未完全匹配时，ICP 会在存储引擎层过滤⾮索引列条件，以减少回表次数。
传统的查询流程是，储引擎通过联合索引定位到符合最左前缀条件的主键 ID；回表读取完整数据⾏并返回给 
Server 层；Server 层对所有返回的⾏进⾏ WHERE 条件过滤。
有了 ICP 后，存储引擎在索引层直接过滤可下推的条件，仅对符合索引条件的记录回表读取数据，再返回给 
Server 层进⾏剩余条件过滤。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 181 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
例如有⼀张 user 表，建了⼀个联合索引（name, age），查询语句：select * from user where name like 
'张%' and age=10; ，没有索引下推优化的情况下：
MySQL 会使⽤索引 name 找到所有 name like '张%' 的主键，根据这些主键，⼀条条回表查询整⾏数据，并在 
Server 层过滤掉不符合 age=10 的数据⾏。
启⽤ ICP 后，InnoDB 会通过联合索引直接筛选出符合条件的主键 ID（name like '张%' and age=10 ），然后
再回表查询整⾏数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 182 / 302

---

换句话说，假设 name like '张%' 找到 10000 ⾏数据，age=10 只有其中 10 ⾏，没有索引下推的情况下，
MySQL 会回表 10000 次，读取 10000 ⾏数据，然后在 Server 层过滤掉 9990 ⾏。
⽽有了索引下推后，MySQL 只会回表 10 次，读取 10 ⾏数据。
我们来验证⼀下。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 183 / 302

---

从结果中我们可以清楚地看到 ICP 的效果。ICP 开启时，Extra 列显示"Using index condition"，表明过滤条件被
下推到存储引擎层。
ICP关闭时，Extra 列仅显示"Using where"，表明过滤条件在服务器层执⾏。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 184 / 302

---

-- 开启ICP
SET optimizer_switch='index_condition_pushdown=on';
-- 清理状态
FLUSH STATUS;
SELECT 'Performance test with ICP ON' as test_case;
-- 执⾏查询并分析性能
EXPLAIN ANALYZE
SELECT /*+ ICP_ON */ *
FROM orders_mrr_test
WHERE user_id BETWEEN 100 AND 200
  AND order_date >= '2023-01-01'
  AND order_date < '2023-02-01'
  AND order_date NOT LIKE '2023-01-15%';
-- 显示处理器状态
SHOW STATUS LIKE 'Handler_read%';
-- 关闭ICP
SET optimizer_switch='index_condition_pushdown=off';
-- 清理状态
FLUSH STATUS;
SELECT 'Performance test with ICP OFF' as test_case;
-- 执⾏相同的查询
EXPLAIN ANALYZE
SELECT *
FROM orders_mrr_test
WHERE user_id BETWEEN 100 AND 200
  AND order_date >= '2023-01-01'
  AND order_date < '2023-02-01'
  AND order_date NOT LIKE '2023-01-15%';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 185 / 302

---

实际的性能差距也很⼤。ICP 开启时，实际扫描⾏数：1,649 ⾏，执⾏时间：约12.3 毫秒。关闭时，实际扫描⾏
数：19,959 ⾏，执⾏时间：约 32.1 毫秒。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：索引下推
52.如何查看是否⽤到了索引？（补充）
 
2024 年 03 ⽉ 15 ⽇增补。
可以通过 EXPLAIN 关键字来查看是否使⽤了索引。
如果使⽤了索引，结果中的 key 值会显示索引的名称。
联合索引 abc，a=1,c=1/b=1,c=1/a=1,c=1,b=1 ⾛不⾛索引？
 
2024 年 03 ⽉ 19 ⽇增补
ac 能⽤上索引，条件 a=1 符合最左前缀原则，触发索引的第⼀列 a；由于跳过了中间列 b，c=1 ⽆法直接利⽤索引
的有序性优化，但可通过索引下推在存储引擎层过滤 c 的条件，减少回表次数。
-- 显示处理器状态
SHOW STATUS LIKE 'Handler_read%';"
EXPLAIN SELECT * FROM table WHERE column = 'value';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 186 / 302

---

bc ⽆法使⽤索引，只能全表扫描，因为不符合最左前缀原则；acb 虽然顺序是乱的，但 MySQL 优化器会⾃动重排
为 abc，所以能命中索引。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
我们通过实际的 SQL 来验证⼀下。
示例 1（a=1,c=1）：
key 是 idx_abc，表明 a=1,c=1 会使⽤联合索引。Extra: Using index condition 表示 ICP ⽣效。
示例 2（b=1,c=1）：
EXPLAIN SELECT * FROM tbn WHERE A=1 AND C=1\G
EXPLAIN SELECT * FROM tbn WHERE B=1 AND C=1\G
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 187 / 302

---

key 是 NULL，表明 b=1,c=1 不会使⽤联合索引。这是因为查询条件没有遵循最左前缀原则。
示例 3（a=1,c=1,b=1）：
优化器会⾃动调整条件顺序为 a=1 AND b=1 AND c=1。
key 是 idx_abc，表明 a=1,c=1,b=1 会使⽤联合索引。
并且 rows=1，因为 MySQL 优化器会⾃动重排查询条件，以满⾜最左前缀原则，直接使⽤联合索引找出 a=1 AND 
b=1 AND c=1 的⾏。
真的恭喜，⼜是⼀个值得晒成绩的⽇⼦，哈哈。
EXPLAIN SELECT * FROM tbn WHERE A=1 AND C=1 AND B=1\G
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 188 / 302

---

锁
 
53.
MySQL 中有哪⼏种锁？
 
MySQL 中有多种类型的锁，可以从不同维度来分类，按锁粒度划分的话，有表锁、⾏锁。
按照加锁机制划分的话，有乐观锁和悲观锁。按照兼容性划分的话，有共享锁和排他锁。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 189 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
表锁：锁定整个表，资源开销⼩，加锁快，但并发度低，不会出现死锁；适合查询为主、少量更新的场景（如 
MyISAM 引擎）。
再细分的话，有表共享读锁（S锁）：允许多个事务同时读，但阻塞写操作；表独占写锁（X锁）：独占表，阻塞其
他事务的读写。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 190 / 302

---

⾏锁：锁定单⾏或多⾏，开销⼤、加锁慢，可能出现死锁，但并发度⾼（InnoDB 默认⽀持）。
再细分的话，有记录锁（Record Lock）：锁定索引中的具体记录；间隙锁（Gap Lock）：锁定索引记录之间的间
隙，防⽌幻读；临键锁（Next-Key Lock）：结合记录锁和间隙锁，锁定⼀个左开右闭的区间（如 (5, 10] ）。
共享锁（S锁/读锁），允许多个事务同时读取数据，但阻塞写操作。语法：SELECT ... LOCK IN SHARE MODE
排他锁（X锁/写锁），独占数据，阻塞其他事务的读写。语法：SELECT ... FOR UPDATE 。
乐观锁假设冲突少，通过版本号或 CAS 机制检测冲突（如 UPDATE SET version=version+1 WHERE 
version=old_version ）。
悲观锁假设并发冲突频繁，先加锁再操作SELECT FOR UPDATE 。
---- 这部分是帮助⼤家理解 end，⾯试中可不背
1. Java ⾯试指南（付费）收录的京东同学 4 云实习⾯试原题：mysql⼀共有哪些锁
2. Java ⾯试指南（付费）收录的美团⾯经同学 15 点评后端技术⾯试原题：问了⼀下mysql的锁和MVCC
3. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：MySQL锁
54.全局锁了解吗？（补充）
 
2024 年 07 ⽉ 15 ⽇增补。
全局锁就是对整个数据库实例进⾏加锁，当执⾏全局锁定操作时，整个数据库将会处于只读状态，所有写操作都会
被阻塞，直到全局锁被释放。
在进⾏全库备份，或者数据迁移时，可以使⽤全局锁来保证数据的⼀致性。
在 MySQL 中，可以使⽤ FLUSH TABLES WITH READ LOCK 命令来获取全局锁。
执⾏该命令后，所有表将被锁定为只读状态。记得在完成备份或迁移后，使⽤ UNLOCK TABLES 命令释放全局锁。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 191 / 302

---

表锁了解吗？
 
了解。
表锁常⻅于 MyISAM 引擎，InnoDB 也可以⼿动通过 LOCK TABLES 加锁。
适合读多写少、全表扫描或者表结构变更的场景⽤。
表锁⼜可以细分为共享锁和排他锁。共享锁允许多个事务同时读表，但不允许写操作。
排他锁只允许⼀个事务进⾏写操作，其他事务不能读也不能写。
MyISAM 在执⾏ SELECT 时会⾃动加读锁，执⾏ INSERT/UPDATE/DELETE 时会加写锁。
对于 InnoDB 引擎，⽆索引的 UPDATE/DELETE 可能会导致锁升级为表锁。
-- 锁定整个数据库
FLUSH TABLES WITH READ LOCK;
-- 执⾏备份操作
-- 例如使⽤ mysqldump 进⾏备份
! mysqldump -u username -p database_name > backup.sql
-- 释放全局锁定
UNLOCK TABLES;
LOCK TABLES table_name READ;  -- 显式加读锁
SELECT * FROM table_name;     -- 其他会话可读，不可写
UNLOCK TABLES;                -- 释放锁
LOCK TABLES table_name WRITE; -- 显式加写锁
INSERT/UPDATE/DELETE table_name; -- 其他会话读写均阻塞
UNLOCK TABLES;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 192 / 302

---

执⾏ ALTER TABLE 时会⾃动加表锁，阻塞所有读写操作。
1. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：数据库中的全局锁 表锁 ⾏
级锁  每种锁的应⽤场景有哪些
2. Java ⾯试指南（付费）收录的同学 30 腾讯⾳乐⾯试原题：mysql的表级锁有⼏种
55.
说说 MySQL 的⾏锁？
 
⾏锁是 InnoDB 存储引擎中最细粒度的锁，它锁定表中的⼀⾏记录，允许其他事务访问表中的其他⾏。
底层是通过给索引加锁实现的，这就意味着只有通过索引条件检索数据时，InnoDB 才能使⽤⾏级锁，否则会退化
为表锁。
⾏锁⼜可以细分为记录锁、间隙锁和临键锁三种形式。通过 SELECT ... FOR UPDATE 可以加排他锁。
UPDATE innodb_table SET name='new' WHERE name='old'; -- 全表扫描，退化为表锁
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 193 / 302

---

通过 SELECT ...LOCK IN SHARE MODE 可以加共享锁。
select for update 有什么需要注意的？
 
第⼀，必须在事务中使⽤，否则锁会⽴即释放。
第⼆，使⽤时必须注意是否命中索引，否则可能锁全表。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
假设有⼀张名为 orders 的表，包含以下数据：
表中的数据是这样的：
START TRANSACTION;
-- 加排他锁，锁定某⼀⾏
SELECT * FROM your_table WHERE id = 1 FOR UPDATE;
-- 对该⾏进⾏操作
UPDATE your_table SET column1 = 'new_value' WHERE id = 1;
COMMIT;
START TRANSACTION;
-- 加共享锁，锁定某⼀⾏
SELECT * FROM your_table WHERE id = 1 LOCK IN SHARE MODE;
-- 只能读取该⾏，不能修改
COMMIT;
START TRANSACTION;
SELECT * FROM your_table WHERE id = 1 FOR UPDATE;
-- 对该⾏进⾏操作
COMMIT;
-- name 没有索引，会退化为表锁
SELECT * FROM user WHERE name = '王⼆' FOR UPDATE;
CREATE TABLE orders (
    id INT PRIMARY KEY,
    order_no VARCHAR(255),
    amount DECIMAL(10,2),
    status VARCHAR(50),
    INDEX (order_no)  -- order_no 上有索引
);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 194 / 302

---

id
order_no
amount
status
1
50.00
pending
2
75.00
pending
3
100.00
pending
4
150.00
completed
5
200.00
pending
如果我们通过主键索引执⾏ SELECT FOR UPDATE ，确实只会锁定特定的⾏：
由于 id 是主键，所以只会锁定 id=1 这⾏，不会影响其他⾏的操作。其他事务依然可以对 id = 2, 3, 4, 5 等⾏执⾏
更新操作，因为它们没有被锁定。
如果使⽤ order_no 这个普通索引执⾏ SELECT FOR UPDATE ，也只会锁定特定的⾏：
因为 order_no 是唯⼀索引，所以只会锁定 order_no=10001 这⾏，不会影响其他⾏的操作。
但如果 WHERE 条件是 status='pending' ，⽽ status 上没有索引：
就会退化为表锁，因为在这种情况下，MySQL 需要全表扫描检查每⼀⾏的 status。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
袭，⼝碑+1。
START TRANSACTION;
SELECT * FROM orders WHERE id = 1 FOR UPDATE;
-- 对 id=1 的⾏进⾏操作
COMMIT;
START TRANSACTION;
SELECT * FROM orders WHERE order_no = '10001' FOR UPDATE;
-- 对 order_no=10001 的⾏进⾏操作
COMMIT;
START TRANSACTION;
SELECT * FROM orders WHERE status = 'pending' FOR UPDATE;
-- 对 status=pending 的⾏进⾏操作
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 195 / 302

---

说说记录锁吧？
 
记录锁是⾏锁最基本的表现形式，当我们使⽤唯⼀索引或者主键索引进⾏等值查询时，MySQL 会为该记录⾃动添
加排他锁，禁⽌其他事务读取或者修改锁定记录。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 196 / 302

---

例如：
间隙锁了解吗？（补充）
 
2024 年 12 ⽉ 15 ⽇增补。
间隙锁⽤于在范围查询时锁定记录之间的“间隙”，防⽌其他事务在该范围内插⼊新记录。仅在可重复读及以上的隔
离级别下⽣效，主要⽤于防⽌幻读。
SELECT * FROM table WHERE id = 1 FOR UPDATE;  -- 加X锁
UPDATE table SET name = '王⼆' WHERE id = 1; -- 隐式加X锁
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 197 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
例如事务 A 锁定了 (1000,2000) 区间，会阻⽌事务 B 在此区间插⼊新记录：
假设表 test_gaplock 有 id、age、name 三个字段，其中 id 是主键，age 上有索引，并插⼊了 4 条数据。
间隙锁会锁住：
-- 事务A
BEGIN;
SELECT * FROM orders WHERE amount BETWEEN 1000 AND 2000 FOR UPDATE;
-- 事务B尝试插⼊会被阻塞
INSERT INTO orders VALUES(null,1500,'pending');  -- 阻塞
CREATE TABLE `test_gaplock` (
  `id` int(11) NOT NULL,
  `age` int(11) DEFAULT NULL,
  `name` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `age` (`age`)
) ENGINE=InnoDB;
insert into test_gaplock values(1,1,'张三'),(6,6,'吴⽼⼆'),(8,8,'赵四'),(12,12,'熊⼤');
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 198 / 302

---

(−∞, 1)：最⼩记录之前的间隙。
(1, 6)、(6, 8)、(8, 12) ：记录之间的间隙。
(12, +∞)：最⼤记录之后的间隙。
假设有两个事务，T1 执⾏以下语句：
T2 执⾏以下语句：
T1 会锁住 (6, 8) 的间隙，防⽌其他事务在这个范围内插⼊新记录。
T2 在插⼊ (7, 7, '王五') 时，会被阻塞，可以在另外⼀个会话中执⾏ SHOW ENGINE INNODB STATUS 查看到间
隙锁的信息。
推荐阅读：六个案例搞懂间隙锁、MySQL中间隙锁的加锁机制
START TRANSACTION;
SELECT * FROM test_gaplock WHERE age > 5 FOR UPDATE;
START TRANSACTION;
INSERT INTO test_gaplock VALUES (7, 7, '王五');
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 199 / 302

---

执⾏什么命令会加上间隙锁？
 
在可重复读隔离级别下，执⾏ FOR UPDATE / LOCK IN SHARE MODE 等加锁语句，且查询条件是范围查询时，就
会⾃动加上间隙锁。
1. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：mysql的什么命令会加上间隙锁
56.临键锁了解吗？
 
临键锁是记录锁和间隙锁的结合体，锁住的是索引记录和索引记录之间的间隙。
和间隙锁不同，临键锁的间隙是⼀个左开右闭区间。例如 (1,3] 表示锁定⼤于 1 且⼩于等于 3 的所有记录。
当 InnoDB 执⾏⼀个范围查询时，会使⽤临键锁来锁定满⾜条件的⾏数据以及该范围内的间隙。
-- SELECT ... FOR UPDATE + 范围查询
SELECT * FROM user WHERE score > 100 FOR UPDATE;
-- SELECT ... LOCK IN SHARE MODE + 范围查询
SELECT * FROM user WHERE id BETWEEN 10 AND 20 LOCK IN SHARE MODE;
-- UPDATE/DELETE + 范围查询
DELETE FROM user WHERE score < 50;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 200 / 302

---

⽐如说下⾯这条语句会锁定 id 在 5 到 10 之间的所有记录，以及这些记录之间的间隙。
MySQL 默认的⾏锁类型就是临键锁。当使⽤唯⼀索引的等值查询匹配到⼀条记录时，临键锁会退化成记录锁；如
果没有匹配到任何记录，会退化成间隙锁。
了。
57.意向锁是什么知道吗？
 
SELECT * FROM table WHERE id BETWEEN 5 AND 10 FOR UPDATE;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 201 / 302

---

兼容关系
意向共享锁
意向排他锁
共享锁(表级)
拍他锁(表级)
意向共享锁
兼容
兼容
兼容
冲突
意向排他锁
兼容
兼容
冲突
冲突
S锁
兼容
冲突
兼容
冲突
X锁
冲突
冲突
冲突
冲突
意向锁是⼀种表级锁，表示事务打算对表中的某些⾏数据加锁，但不会直接锁定数据⾏本身。
由 InnoDB ⾃动管理，当事务需要添加⾏锁时，会先在表上添加意向锁。这样当要添加表锁的时候，可以通过查看
表上的意向锁，快速判断是否有冲突，⽽⽆需逐⾏检查，从⽽提⾼加锁效率。
当执⾏ SELECT ... LOCK IN SHARE MODE 时，会⾃动加意向共享锁；当执⾏ SELECT ... FOR UPDATE 时，会
⾃动加意向排他锁。
意向锁之间互相兼容，也不会与⾏锁冲突。
意向锁的意义是什么？
 
在没有意向锁的情况下，当事务 A 持有某表的⾏锁时，如果事务 B 想添加表锁，InnoDB 必须检查表中每⼀⾏数据
是否被加锁，这种全表扫描的⽅式效率极低。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 202 / 302

---

有了意向锁之后，事务在加⾏锁前，先在表上加对应的意向锁；其他事务加表锁时，只需检查表上的意向锁，⽆需
逐⾏检查。
不，⼝碑就来了。
-- 事务A获取某⾏的排他锁
BEGIN;
SELECT * FROM users WHERE id = 6 FOR UPDATE;  -- ⾃动加IX锁和⾏X锁
-- 事务B尝试加表锁
LOCK TABLES users READ;  -- 发现表上有IX锁，与S锁冲突，直接阻塞⽽⽆需扫描全表
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 203 / 302

---

58.
MySQL的乐观锁和悲观锁了解吗？
 
悲观锁是⼀种"先上锁再操作"的保守策略，它假设数据被外界访问时必然会产⽣冲突，因此在数据处理过程中全程
加锁，保证同⼀时间只有⼀个线程可以访问数据。
MySQL 中的⾏锁和表锁都是悲观锁。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 204 / 302

---

乐观锁会假设并发操作不会总发⽣冲突，属于⼩概率事件，因此不会在读取数据时加锁，⽽是在提交更新时才检查
数据是否被其他事务修改过。
乐观锁并不是 MySQL 内置的锁机制，⽽是通过程序逻辑实现的，常⻅的实现⽅式有版本号机制和时间戳机制。通
过在表中增加 version 字段或者 timestamp 字段来实现。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
当事务 A 已经上锁后，事务 B 会⼀直等待事务 A 释放锁；如果事务 A ⻓时间不释放锁，事务 B 就会报错 Lock 
wait timeout exceeded; try restarting transaction 。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 205 / 302

---

事务 A 和事务 B 同时读取同⼀个主键 ID 的数据，版本号为 0；事务 A 将版本号（version=1）作为条件进⾏数据
更新，同时版本号 +1；事务 B 也将 version=1 作为更新条件，发现版本号不匹配，更新失败。
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
如何通过悲观锁和乐观锁解决库存超卖问题？
 
悲观锁通过 SELECT ... FOR UPDATE 在查询时直接锁定记录，确保其他事务必须等待当前事务完成才能操作该
⾏数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 206 / 302

---

乐观锁通过在表中增加 version 字段作为判断条件。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
库存超卖是⼀个⾮常经典的问题：
事务A查询商品库存，得到库存值为1
事务B也查询同⼀商品库存，同样得到库存值为1
事务A基于查询结果执⾏库存扣减，将库存更新为0
事务B也执⾏库存扣减，将库存更新为-1
悲观锁的关键点：
必须在⼀个事务中执⾏；
通过 SELECT ... FOR UPDATE 锁定⾏，确保其他事务必须等待当前事务完成才能操作该⾏数据；
记得给查询条件加索引，避免全表扫描导致锁升级为表锁。
乐观锁的关键点：
在表中增加 version 字段；
查询时获取当前版本号；
更新时检查版本号是否发⽣了变化。
Java 程序的完整代码示例：
BEGIN;
-- 对id=1的商品记录加排他锁
SELECT stock FROM products WHERE id=1 FOR UPDATE;
-- ⽣成订单
INSERT INTO orders (user_id, product_id) VALUES (123, 1);
-- 扣减库存
UPDATE products SET stock=stock-1 WHERE id=1;
COMMIT;
-- 查询商品信息，获取版本号
SELECT stock, version FROM products WHERE id=1;
-- 更新库存时检查版本号
UPDATE products 
SET stock=stock-1, version=version+1 
WHERE id=1 AND version=旧版本号;
@Service
public class ProductService {
    @Autowired
    private ProductMapper productMapper;
    
    @Transactional
    public boolean purchaseWithOptimisticLock(Long productId, int quantity) {
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 207 / 302

---

对应的 mapper：
时间戳机制实现的乐观锁：
这两种⽅式都需要保证操作的原⼦性，需要将多个 SQL 放在同⼀个事务中执⾏。
推荐阅读：牧⼩农：悲观锁和乐观锁
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：乐观锁和悲观锁，库存的超
卖问题的原因和解决⽅案？
2. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：乐观锁与悲观锁
监，等⼀⼿他的好消息。当然了，仍然不忘感谢⼀下⾯渣逆袭对他的帮助，哈哈。
        int retryCount = 0;
        while(retryCount < 3) { // 最⼤重试次数
            Product product = productMapper.selectById(productId);
            if(product.getStock() < quantity) {
                return false; // 库存不⾜
            }
            
            int updated = productMapper.reduceStockWithVersion(
                productId, quantity, product.getVersion());
                
            if(updated > 0) {
                return true; // 更新成功
            }
            retryCount++;
        }
        return false; // 更新失败
    }
}
@Update("UPDATE products SET stock=stock-#{quantity}, version=version+1 " +
        "WHERE id=#{productId} AND version=#{version}")
int reduceStockWithVersion(@Param("productId") Long productId, 
                          @Param("quantity") int quantity,
                          @Param("version") int version);
UPDATE products SET stock=stock-1, update_time=NOW() 
WHERE id=1 AND update_time=旧时间戳;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 208 / 302

---

59.遇到过MySQL死锁问题吗，你是如何解决的？
 
遇到过。MySQL 的死锁是由于多个事务持有资源并相互等待引起的。我通过 SHOW ENGINE INNODB STATUS 查看
死锁信息，定位到是加锁顺序不⼀致导致的，最后通过调整加锁顺序解决了这个问题。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 209 / 302

---

⽐如说技术派项⽬中，两个事务分别更新两张表，但是更新顺序不⼀致。
访问相同的资源，但顺序不同，就会导致死锁。
解决办法也很简单，先使⽤ SHOW ENGINE INNODB STATUS\G; 确认死锁的具体信息，然后调整资源的访问顺序。
-- 创建表/插⼊数据
CREATE TABLE account (
    id INT AUTO_INCREMENT PRIMARY KEY,
    balance INT NOT NULL
);
INSERT INTO account (balance) VALUES (100), (200);
-- 事务 1
START TRANSACTION;
-- 锁住 id=1 的⾏
UPDATE account SET balance = balance - 10 WHERE id = 1;
-- 等待锁住 id=2 的⾏（事务 2 已锁住）
UPDATE account SET balance = balance + 10 WHERE id = 2;
-- 事务 2
START TRANSACTION;
-- 锁住 id=2 的⾏
UPDATE account SET balance = balance - 10 WHERE id = 2;
-- 等待锁住 id=1 的⾏（事务 1 已锁住）
UPDATE account SET balance = balance + 10 WHERE id = 1;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 210 / 302

---

1. Java ⾯试指南（付费）收录的虾⽪⾯经同学 13 ⼀⾯⾯试原题：遇到过mysql死锁或者数据不安全吗
事务
 
60.
MySQL事务的四⼤特性说⼀下？
 
事务是⼀条或多条 SQL 语句组成的执⾏单元。四个特性分别是原⼦性、⼀致性、隔离性和持久性。原⼦性保证事
务中的操作要么全部执⾏、要么全部失败；⼀致性保证数据从事务开始前的⼀个⼀致状态转移到结束后的另外⼀个
⼀致状态；隔离性保证并发事务之间互不⼲扰；持久性保证事务提交后数据不会丢失。
详细说⼀下原⼦性？
 
原⼦性意味着事务中的所有操作要么全部完成，要么全部不完成，它是不可分割的单位。如果事务中的任何⼀个操
作失败了，整个事务都会回滚到事务开始之前的状态，如同这些操作从未被执⾏过⼀样。
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE user_id = 1;
UPDATE accounts SET balance = balance + 100 WHERE user_id = 2;
-- 如果第⼆条语句失败，第⼀条也会回滚
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 211 / 302

---

简短回答：原⼦性要求事务的所有操作要么全部提交成功，要么全部失败回滚，对于⼀个事务中的操作不能只执⾏
其中⼀部分。
详细说⼀下⼀致性？
 
⼀致性确保事务从⼀个⼀致的状态转换到另⼀个⼀致的状态。
⽐如在银⾏转账事务中，⽆论发⽣什么，转账前后两个账户的总⾦额应保持不变。假如 A 账户（100 块）给 B 账
户（10 块）转了 10 块钱，不管成功与否，A 和 B 的总⾦额都是 110 块。
简短回答：⼀致性确保数据的状态从⼀个⼀致状态转变为另⼀个⼀致状态。⼀致性与业务规则有关，⽐如银⾏转
账，不论事务成功还是失败，转账双⽅的总⾦额应该是不变的。
详细说⼀下隔离性？
 
隔离性意味着并发执⾏的事务是彼此隔离的，⼀个事务的执⾏不会被其他事务⼲扰。事务之间是井⽔不犯河⽔的。
隔离性主要是为了解决事务并发执⾏时可能出现的脏读、不可重复读、幻读等问题。
---- 这部分是帮助⼤家理解 start，⾯试中可不背 ----
⽐如说在读未提交的隔离级别下，会出现脏读现象：⼀个事务C 读取了事务B 尚未提交的修改数据。如果事务B 最
终回滚，事务C 读取的数据就是⽆效的“脏数据”。
-- 假设 A 账户余额为 100，B 账户余额为 10
-- 转账前状态
SELECT balance FROM accounts WHERE user_id = 'A'; -- 100
SELECT balance FROM accounts WHERE user_id = 'B'; -- 10
-- 转账操作
START TRANSACTION;
UPDATE accounts SET balance = balance - 10 WHERE user_id = 'A';
UPDATE accounts SET balance = balance + 10 WHERE user_id = 'B';
COMMIT;
-- 转账后状态
SELECT balance FROM accounts WHERE user_id = 'A'; -- 90
SELECT balance FROM accounts WHERE user_id = 'B'; -- 20`
-- 总⾦额仍然是 110
-- 会话 A
-- 创建模拟并发的测试表
DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50),
    balance DECIMAL(10,2)
);
-- 插⼊测试数据
INSERT INTO accounts (name, balance) VALUES
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 212 / 302

---

('王⼆', 1000.00),
('张三', 2000.00),
('李四', 3000.00);
-- 会话B 中，设置隔离级别为读未提交
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
START TRANSACTION;
-- 在会话 B 中更新数据但不提交
UPDATE accounts SET balance = balance - 500 WHERE name='王⼆';
-- 会话C 是读为提交级别，读取数据，得到 500
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SELECT * FROM accounts WHERE name='王⼆';
-- 继续别的操作，基于 500
-- 会话 B 的事务回滚，导致会话 A 读到的数据其实是脏数据
ROLLBACK;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 213 / 302

---

通过升级隔离级别为读已提交可以解决脏读的问题。
-- 会话 B 修改为读已提交
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 执⾏第⼀次查询 1000
SELECT * FROM accounts WHERE name='王⼆';
-- 会话 C 中，设置隔离级别为读已提交
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 在会话 C 中更新数据但不提交
START TRANSACTION;
UPDATE accounts SET balance = balance + 200 WHERE name='王⼆';
-- 会话 B 中再次读取数据，结果仍然为 1000
SELECT * FROM accounts WHERE name='王⼆';
-- 会话 C 中回滚事务
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 214 / 302

---

但会出现不可重复读的问题：事务B 第⼀次读取某⾏数据值为X，期间事务C修改该数据为Y并提交，事务B 再次读
取时发现值变为Y，导致两次读取结果不⼀致。
ROLLBACK;
-- 会话 B 中再次读取数据，结果仍然为 1000
SELECT * FROM accounts WHERE name='王⼆';
-- 会话 B 修改为读已提交
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 215 / 302

---

-- 执⾏第⼀次查询 1000
START TRANSACTION;
SELECT * FROM accounts WHERE name='王⼆';
-- 会话 C 中，设置隔离级别为读已提交
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- 在会话 C 中更新数据并提交
START TRANSACTION;
UPDATE accounts SET balance = balance + 200 WHERE name='王⼆';
-- 会话 C 提交事务
COMMIT;
-- 会话 B 中再次读取数据，结果仍然为 1200
SELECT * FROM accounts WHERE name='王⼆';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 216 / 302

---

可以通过升级隔离级别为可重复读来解决不可重复读的问题。
-- 会话 B 修改为可重复读
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- 开始事务并执⾏第⼀次查询 1000
START TRANSACTION;
SELECT * FROM accounts WHERE name='王⼆';
-- 会话 C 中，设置隔离级别为可重复读
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- 在会话 C 中更新数据并提交
START TRANSACTION;
UPDATE accounts SET balance = balance + 200 WHERE name='王⼆';
-- 会话 C 提交事务
COMMIT;
-- 会话 B 中再次读取数据，结果仍然为 1000
SELECT * FROM accounts WHERE name='王⼆';
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 217 / 302

---

但可重复读级别下仍然会出现幻读的问题：事务B 第⼀次查询获得 2条数据，事务C 新增 1条数据并提交后，事务B 
再次查询时仍然为 2 条数据，但可以更新新增的数据，再次查询时就发现有 3 条数据了。
-- 会话 B 修改为可重复读
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- 执⾏第⼀次查询，查到 2 条记录
START TRANSACTION;
SELECT * FROM accounts WHERE balance > 1000;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 218 / 302

---

-- 会话 C 中，设置隔离级别为可重复读
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- 在会话 C 中新增数据并提交
START TRANSACTION;
INSERT INTO accounts (name, balance) VALUES ('王五', 4000);
-- 会话 C 提交事务
COMMIT;
-- 会话 B 中再次读取数据，结果仍然为 2 条
SELECT * FROM accounts WHERE balance > 1000;
-- 会话 B 中尝试更新王五的余额为 5000，竟然成功了
UPDATE accounts SET balance = 5000 WHERE name='王五';
-- 会话 B 中再次读取数据，发现 3 条记录
SELECT * FROM accounts WHERE balance > 1000;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 219 / 302

---

可以通过升级隔离级别为串⾏化来解决幻读的问题。
-- 会话 B 修改为可串⾏化
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- 执⾏第⼀次查询，查到 2 条记录
START TRANSACTION;
SELECT * FROM accounts WHERE balance > 1000;
-- 会话 C 中，设置隔离级别为可串⾏化
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- 在会话 C 中新增数据，会卡住
START TRANSACTION;
INSERT INTO accounts (name, balance) VALUES ('王五', 4000);
-- 只有等会话 B 提交事务后会话 C 才会继续执⾏并提交事务
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 220 / 302

---

⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 221 / 302

---

隔离级别
是否会脏读
是否会不可重复读
是否会幻读
Read Uncommitted（读未提交）
 可能
 可能
 可能
Read Committed（读已提交）
 不会
 可能
 可能
Repeatable Read（可重复读）
 不会
 不会
 可能（但 InnoDB 已解决）
Serializable（可串⾏化）
 不会
 不会
 不会
---- 这部分是帮助⼤家理解 end，⾯试中可不背 ----
简短回答：多个并发事务之间需要相互隔离，即⼀个事务的执⾏不能被其他事务⼲扰。
详细说⼀下持久性？
 
持久性确保事务⼀旦提交，它对数据所做的更改就是永久性的，即使系统发⽣崩溃，数据也能恢复到最近⼀次提交
的状态。
MySQL 的持久性是通过 InnoDB 引擎的 redo log 实现的。在事务提交时，InnoDB 会先将修改操作写⼊ redo 
log，并刷盘持久化。崩溃后，InnoDB 会通过 redo log 恢复数据，从⽽保证事务提交成功的数据不会丢失。
简短回答：⼀旦事务提交，则其所做的修改将永久保存到 MySQL 中。即使发⽣系统崩溃，修改的数据也不会丢
失。
1. Java ⾯试指南（付费）收录的京东同学 10 后端实习⼀⾯的原题：事务的四个特性，怎么理解事务⼀致
性
2. Java ⾯试指南（付费）收录的美团⾯经同学 16 暑期实习⼀⾯⾯试原题：MySQL 事务是什么，默认隔
离级别，什么是可重复读？
3. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：MySQL 事务，隔离级别
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 222 / 302

---

4. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：什么是数据库事务？
事务的作⽤是什么？
5. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：对MySQL事务的理解
6. Java ⾯试指南（付费）收录的vivo ⾯经同学 10 技术⼀⾯⾯试原题：事务的概念
7. Java ⾯试指南（付费）收录的同学 1 ⻉壳找房后端技术⼀⾯⾯试原题：事务ACID
8. Java ⾯试指南（付费）收录的字节跳动同学 17 后端技术⾯试原题：什么是事务 事务为什么要有隔离级
别 幻读是什么 什么时候要解决幻读 什么时候不⽤解决
9. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：MySQL中的事务
memo：2025 年 4 ⽉ 13 ⽇修改⾄此，今天给球友改简历的时候，发现这种彩虹屁真的离谱了，直接配享太庙
了，但有⼀说⼀这位球友的话是真的甜，简历写的确实也很⽤⼼了，确定⼀个 offer 收割机。
61.ACID 靠什么保证的呢？
 
⼀句话总结：
ACID 中的原⼦性主要通过 Undo Log 来实现，持久性通过 Redo Log 来实现，隔离性由 MVCC 和锁机制来实现，
⼀致性则由其他三⼤特性共同保证。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 223 / 302

---

详细说说如何保证原⼦性？
 
事务对数据进⾏修改前，会记录⼀份快照到 Undo Log，如果事务中有任何⼀步执⾏失败，系统会读取 Undo Log 
将所有操作回滚，恢复到事务开始前的状态，从⽽保证事务要么全部成功，要么全部失败。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 224 / 302

---

推荐阅读：庖丁解InnoDB之UNDO LOG
详细说说如何保证持久性？
 
MySQL 的持久性主要由预写 Redo Log、双写机制、两阶段提交以及 Checkpoint 刷盘机制共同保证。
当事务提交时，MySQL 会先将事务的修改操作写⼊ Redo Log，并强制刷盘，然后再将内存中的数据⻚刷⼊磁盘。
这样即使系统崩溃，重启后也能通过 Redo Log 重放恢复数据。
1）BEGIN;
2）UPDATE user SET balance = balance - 100 WHERE id = 1;
   => 写⼊ Undo Log：记录 id=1 的原始余额 500
3）UPDATE user SET balance = balance + 100 WHERE id = 2;
   => 写⼊ Undo Log：记录 id=2 的原始余额 300
4）COMMIT;
   => 清空 Undo Log，事务成功
如果失败：
   => 执⾏ ROLLBACK：根据 Undo Log 把数据还原！
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 225 / 302

---

在将数据⻚写⼊到磁盘时，如果发⽣崩溃，可能会导致数据⻚不完整。InnoDB 的数据⻚⼤⼩为16KB，通常⼤于操
作系统的 4KB⻚⼤⼩。
为了解决只写⼊部分的问题，MySQL 采⽤了双写机制，脏盘刷⻚时，先将数据⻚写⼊到⼀个双写缓冲区中，2M 
的连续空间，然后再将其写⼊到磁盘的实际位置。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 226 / 302

---

崩溃恢复时，如果发现数据⻚不完整，会从双写缓冲区中恢复副本，确保数据⻚的完整性。
在涉及主从复制时，MySQL 通过两阶段提交保证 Redo Log 和 Binlog 的⼀致性：第⼀阶段，写⼊ Redo Log 并标
记为 prepare 状态；第⼆阶段，写⼊ Binlog 再提交 Redo Log 为 commit 状态。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 227 / 302

---

崩溃恢复时，如果发现 Redo Log 是 prepare 但 Binlog 完整，则会提交事务；反之会回滚，避免主从不⼀致。
另外，由于 Redo Log 的容量有限，Checkpoint 机制会定期将内存中的脏⻚刷到磁盘，这样能减少崩溃恢复时需
要处理的 Redo Log 数量。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 228 / 302

---

推荐阅读：深⼊解析MySQL双写缓冲区、MySQL 事务⼆阶段提交
详细说说如何保证隔离性？
 
隔离性主要通过锁机制和 MVCC 来实现。
⽐如说⼀个事务正在修改某条数据时，MySQL 会通过临键锁来防⽌其他事务同时进⾏修改，避免数据冲突。
同时，临键锁可以防⽌幻读现象的发⽣。⽐如事务 A 查询 id > 10 的记录，那么临键锁不仅会锁住 id=10 的⾏，
还会锁住 10 后⾯的“间隙”，防⽌其他事务插⼊ id=15 的数据。
假如表中的主键有 id: 5, 10, 15, 20, 25 ，那么 InnoDB 会对以下区间和记录加锁：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 229 / 302

---

加锁对象
类型
锁定含义
(10, 15]
临键锁
锁住 id=15 和前间隙，防⽌插⼊11~14
(15, 20]
临键锁
锁住了 id=20 和前间隙
(20, 25]
临键锁
锁住了 id=25 和前间隙
(25, +∞)
间隙锁
锁住尾部防⽌插⼊30等
MVCC 主要⽤来优化读操作，通过保存数据的历史版本，让读操作不需要加锁就能直接读取快照，提⾼读的并发性
能。
不同的隔离级别对应不同的实现策略，⽐如说在可重复读隔离级别下，事务第⼀次查询时会⽣成⼀个 Read View，
之后所有读操作都复⽤这个视图，保证多次读取的结果⼀致。
如何保证⼀致性呢？
 
MySQL 的⼀致性并不是靠某⼀个机制单独保证的，⽽是原⼦性、隔离性和持久性协同作⽤的结果。
事务会不会⾃动提交？
 
是的，MySQL 默认开启了事务⾃动提交模式。
每条单独的 SQL 语句都会被视为⼀个独⽴的事务处理单元；SQL 语句执⾏成功后会⾃动执⾏ COMMIT；执⾏失败
时会⾃动 ROLLBACK。
可通过 SELECT @@autocommit; 查看当前会话的⾃动提交状态。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 230 / 302

---

如果需要执⾏多条 SQL 语句，可以将它们放在⼀个事务中，使⽤ START TRANSACTION 开启事务，执⾏完所有 
SQL 语句后⼿动提交。
1. Java ⾯试指南（付费）收录的美团同学 2 优选物流调度技术 2 ⾯⾯试原题：MySQL ACID 哪些机制来
保证
2. Java ⾯试指南（付费）收录的百度同学 4 ⾯试原题：事务会不会⾃动提交?
memo：2025 年 4 ⽉ 14 ⽇修改⾄此，昨天有球友发帖说拿到了字节、美团的暑期实习 offer，双⾮本末 9 硕，全
⽂很硬，强烈推荐⼤家看看，差不多有 3000 多字，详细剖析了⾃⼰的准备过程。
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE user_id = 1;
UPDATE accounts SET balance = balance + 100 WHERE user_id = 2;
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 231 / 302

---

62.
事务的隔离级别有哪些？
 
隔离级别定义了⼀个事务可能受其他事务影响的程度，MySQL ⽀持四种隔离级别，分别是：读未提交、读已提
交、可重复读和串⾏化。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 232 / 302

---

读未提交会出现脏读，读已提交会出现不可重复读，可重复读是 InnoDB 默认的隔离级别，可以避免脏读和不可重
复读，但会出现幻读。不过通过 MVCC 和临键锁，能够防⽌⼤多数并发问题。
串⾏化最安全，但性能较差，通常不推荐使⽤。
详细说说读未提交？
 
事务可以读取其他未提交事务修改的数据。也就是说，如果未提交的事务⼀旦回滚，读取到的数据就会变成了“脏
数据”，通常不会使⽤。
什么是读已提交？
 
读已提交避免了脏读，但可能会出现不可重复读，即同⼀事务内多次读取同⼀数据结果会不同，因为其他事务提交
的修改，对当前事务是可⻅的。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 233 / 302

---

是 Oracle、SQL Server 等数据库的默认隔离级别。
什么是可重复读？
 
可重复读能确保同⼀事务内多次读取相同数据的结果⼀致，即使其他事务已提交修改。
是 MySQL 默认的隔离级别，避免了“脏读”和“不可重复读”，通过 MVCC 和临键锁也能在⼀定程度上避免幻读。
-- Session A:
START TRANSACTION;
SELECT balance FROM accounts WHERE id=1; --返回500
-- Session B:
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 234 / 302

---

什么是串⾏化？
 
串⾏化是最⾼的隔离级别，通过强制事务串⾏执⾏来解决“幻读”问题。
但会导致⼤量的锁竞争问题，实际应⽤中很少⽤。
A 事务未提交，B 事务上查询到的是旧值还是新值？
 
如果 B 是普通的 SELECT，也就是快照读，它读的是旧值，即事务 A 修改前的快照，并且不会阻塞；如果 B 是当前
读，⽐如 SELECT … FOR UPDATE ，它会被阻塞直到事务 A 提交或回滚。
UPDATE accounts SET balance = balance +100 WHERE id=1;
COMMIT;
-- Session A再次查询:
SELECT balance FROM accounts WHERE id=1; --仍返回500(可重复读)
-- Session A更新后查询:
UPDATE accounts SET balance = balance +50 WHERE id=1; --基于最新值550更新为600 
SELECT balance FROM accounts WHERE id=1; --返回600 
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 235 / 302

---

怎么更改事务的隔离级别？
 
MySQL ⽀持通过 SET 语句修改事务隔离级别，包括全局级别、当前会话，但⼀般不建议在⽣产环境中随意修改隔
离级别。
-- 会话 A 中，更新王⼆的余额
START TRANSACTION;
UPDATE accounts SET balance = 8000 WHERE name = '王⼆';
-- 此时并没有 COMMIT
-- 会话 B 中查询王⼆的余额
SELECT * FROM accounts WHERE name = '王⼆';
-- 会话 B 会读取到 旧值 1000
-- 会话 C 中使⽤当前读查询王⼆的余额
SELECT * FROM accounts WHERE name = '王⼆' FOR UPDATE;
-- 会话 C 会被阻塞，直到会话 A 提交或回滚
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 236 / 302

---

测试环境下可以使⽤ SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; 可以修改当前会话的隔
离级别。
使⽤ SET GLOBAL TRANSACTION ISOLATION LEVEL READ COMMITTED; 可以修改全局隔离级别，影响新的连接，
但不会改变现有会话。
1. Java ⾯试指南（付费）收录的美团⾯经同学 16 暑期实习⼀⾯⾯试原题：MySQL 事务是什么，默认隔
离级别，什么是可重复读？
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：MySQL 事务，隔离级别
3. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下事务的四⼤隔离级
别，分别解决什么问题
4. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：MySQL 默认隔离级
别？
5. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：说说 MySQL 事务的隔离级
别，如何实现？
6. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：MySQL 的四个隔离级别以及
默认隔离级别？
7. Java ⾯试指南（付费）收录的 360 ⾯经同学 3 Java 后端技术⼀⾯⾯试原题：数据库隔离级别有哪些？
mysql 是属于哪个隔离级别
8. Java ⾯试指南（付费）收录的联想⾯经同学 7 ⾯试原题：Mysql 四个隔离级别，MVCC 实现
9. Java ⾯试指南（付费）收录的oppo ⾯经同学 8 后端开发秋招⼀⾯⾯试原题：讲讲Mysql的四个隔离级
别
10. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：mysql的隔离级别有哪些
11. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：事务的隔离级别？这些隔离
级别是怎么保证数据的⼀致性的？默认的事务隔离级别是啥？（MVCC）怎么更改事务的隔离级别？
12. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：事务隔离级别，哪个是默认
的，特点
13. Java ⾯试指南（付费）收录的虾⽪⾯经同学 13 ⼀⾯⾯试原题：mysql事务隔离级别
63.事务的隔离级别是如何实现的？
 
读未提交通过⾏锁共享锁确保⼀个事务在更新⾏数据但没有提交的情况下，其他事务不能更新该⾏数据，但不会阻
⽌脏读，意味着事务2 可以在事务1 提交之前读取到事务1 修改的数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 237 / 302

---

读已提交会在更新数据前加⾏级排他锁，不允许其他事务写⼊或者读取未提交的数据，也就意味着事务2 不能在事
务 1 提交之前读取到事务1 修改的数据，从⽽解决脏读的问题。
另外，读已提交会在每次读取数据前都⽣成⼀个新的 ReadView，所以会出现不可重复读的问题。
可重复读只在第⼀次读操作时⽣成 ReadView，后续读操作都会使⽤这个 ReadView，从⽽避免不可重复读的问
题。
另外，对于当前读操作，可重复读会通过临键锁来锁住当前⾏和前间隙，防⽌其他事务在这个范围内插⼊数据，从
⽽避免幻读的问题。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 238 / 302

---

串⾏化级别下，事务在读操作时，会先加表级共享锁；在写操作时，会先加表级排他锁。
直到事务结束后才释放锁，这样就能确保事务之间不会相互⼲扰。
1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：说说 MySQL 事务的隔离级
别，如何实现？
2. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：Mysql隔离机制有哪些？怎么实现的？可
串⾏化是怎么避免的三个事务问题？
3. Java ⾯试指南（付费）收录的滴滴⾯经同学 3 ⽹约⻋后端开发⼀⾯原题：可重复读级别是怎么实现的
memo：2025 年 4 ⽉ 17 ⽇修改⾄此，今天有球友发微信说拿到京东的暑期实习 offer，并且今天 VIP 9 群有球友
说今天是晒 offer ⽇，因为 9 群有好⼏个球友拿到了⼤⼚ offer，后⾯我再同步给⼤家。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 239 / 302

---

64.
请详细说说幻读呢？
 
幻读是指在同⼀个事务中，多次执⾏相同的范围查询，结果却不同。这种现象通常发⽣在其他事务在两次查询之间
插⼊或删除了符合当前查询条件的数据。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 240 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可以不背 ----
⽐如说事务 A 在第⼀次查询某个条件范围的数据⾏后，事务 B 插⼊了⼀条新数据且符合条件范围，事务 A 再次查
询时，发现多了⼀条数据。
我们来验证⼀下，先创建测试表，插⼊测试数据。
然后我们在事务 A 中执⾏查询 SELECT * FROM user_info WHERE id > 1; ，在事务 B 中插⼊数据 INSERT 
INTO user_info (name, gender, email) VALUES ('wanger', '⼥', 'wanger@163.com'); ，再在事务 A 中
修改刚刚插⼊的数据 update user_info set gender='男' where id = 4; ，最后在事务 A 中再次查询 
SELECT * FROM user_info WHERE id > 1; 。
CREATE TABLE `user_info` (
  `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '姓名',
  `gender` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '性别',
  `email` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '邮箱',
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='⽤户信息表';
-- 插⼊测试数据
INSERT INTO `user_info` (`id`, `name`, `gender`, `email`) VALUES 
  (1, 'Curry', '男', 'curry@163.com'),
  (2, 'Wade', '男', 'wade@163.com'),
  (3, 'James', '男', 'james@163.com');
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 241 / 302

---

---- 这部分是帮助⼤家理解 end，⾯试中可以不背 ----
如何避免幻读？
 
MySQL 在可重复读隔离级别下，通过 MVCC 和临键锁可以在⼀定程度上避免幻读。
⽐如说在查询时显示加锁，利⽤临键锁锁定查询范围，防⽌其他事务插⼊新的数据。
其他事务在插⼊数据时，会被阻塞，直到当前事务提交或回滚。
START TRANSACTION;
SELECT * FROM user_info WHERE id > 1 FOR UPDATE; -- 加临键锁
COMMIT;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 242 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可以不背 ----
解释⼀下。
如果查询语句中包含显式加锁（如 FOR UPDATE ），InnoDB 会使⽤当前读，直接读取最新的数据，并加锁。
在范围查询时，InnoDB 不仅会对符合条件的记录加⾏锁，还会对相邻的索引间隙加间隙锁，从⽽形成临键锁。
临键锁可以防⽌其他事务在间隙中插⼊新数据，从⽽避免幻读。
---- 这部分是帮助⼤家理解 end，⾯试中可以不背 ----
⽐如说在执⾏查询的事务中，不要尝试去更新其他事务插⼊/删除的数据，利⽤快照读来避免幻读。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 243 / 302

---

---- 这部分是帮助⼤家理解 start，⾯试中可以不背 ----
使⽤ SELECT  查询时，如果没有显式加锁，InnoDB 会使⽤ MVCC 提供⼀致性视图。
每个事务在启动时都会⽣成⼀个 Read View，⽤来确定哪些数据对当前事务可⻅。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 244 / 302

---

其他事务在当前事务启动后插⼊的新数据不会被当前事务看到，因此不会出现幻读。
---- 这部分是帮助⼤家理解 end，⾯试中可以不背 ----
什么是当前读呢？
 
当前读是指读取记录的最新已提交版本，并且在读取时对记录加锁，确保其他并发事务不能修改当前记录。
⽐如 SELECT ... LOCK IN SHARE MODE 、SELECT ... FOR UPDATE ，以及 UPDATE、DELETE，都属于当前
读。
为什么 UPDATE 和 DELETE 也属于当前读？
 
因为更新、删除这些操作，本质上不仅是写操作，还需要在写之前读取数据，然后才能修改或删除。为了保证修改
的是最新的数据，并防⽌并发冲突，InnoDB 必须读取最新版本的数据并加锁，因此 UPDATE 和 DELETE 也属于当
前读。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 245 / 302

---

SQL语句
是否当前读
是否加锁
SELECT * FROM user WHERE id=1
 否
 否
SELECT * FROM user WHERE id=1 FOR UPDATE
 是
 加排他锁
SELECT * FROM user WHERE id=1 LOCK IN SHARE MODE
 是
 加共享锁
UPDATE user SET ... WHERE id=1
 是
 加排他锁
DELETE FROM user WHERE id=1
 是
 加排他锁
什么是快照读呢？
 
快照读是 InnoDB 通过 MVCC 实现的⼀种⾮阻塞读⽅式。当事务执⾏ SELECT 查询时，InnoDB 并不会直接读当前
最新的数据，⽽是根据事务开始时⽣成的 Read View 去判断每条记录的可⻅性，从⽽读取符合条件的历史版本。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 246 / 302

---

SQL
是否快照读？
说明
SELECT * FROM t WHERE id=1
 是
快照读
SELECT * FROM t WHERE id=1 FOR UPDATE
 否
当前读，读取最新版本并加锁
UPDATE / DELETE
 否
当前读，必须读取当前版本并加锁
INSERT
 否
写操作，不存在历史版本
1. Java ⾯试指南（付费）收录的京东⾯经同学 7  京东到家⾯试原题：mysql事务隔离级别，默认隔离级
别，如何避免幻读
2. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：事务隔离级别，幻读和脏读的区别，如何防⽌
幻读，事务的mvcc机制
memo：2025 年 4 ⽉ 18 ⽇修改⾄此，今天有球友发帖说拿到了蚂蚁的暑期实习 offer，问前景怎么样，我只能说
蚂蚁作为阿⾥的嫡⻓⼦，⼀直处在战略发展的核⼼位置，肯定是值得去的。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 247 / 302

---

65.
MVCC 了解吗？
 
MVCC 指的是多版本并发控制，每次修改数据时，都会⽣成⼀个新的版本，⽽不是直接在原有数据上进⾏修改。并
且每个事务只能看到在它开始之前已经提交的数据版本。
这样的话，读操作就不会阻塞写操作，写操作也不会阻塞读操作，从⽽避免加锁带来的性能损耗。
其底层实现主要依赖于 Undo Log 和 Read View。
每次修改数据前，先将记录拷⻉到Undo Log，并且每条记录会包含三个隐藏列，DB_TRX_ID ⽤来记录修改该⾏的
事务 ID，DB_ROLL_PTR ⽤来指向 Undo Log 中的前⼀个版本，DB_ROW_ID ⽤来唯⼀标识该⾏数据（仅⽆主键时
⽣成）。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 248 / 302

---

每次读取数据时，都会⽣成⼀个 ReadView，其中记录了当前活跃事务的 ID 集合、最⼩事务 ID、最⼤事务 ID 等信
息，通过与 DB_TRX_ID 进⾏对⽐，判断当前事务是否可以看到该数据版本。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 249 / 302

---

请详细说说什么是版本链？
 
版本链是指 InnoDB 中同⼀条记录的多个历史版本，通过 DB_ROLL_PTR 字段将它们像链表⼀样串起来，⽤来⽀持 
MVCC 的快照读。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 250 / 302

---

假设有⼀张hero 表，表中有这样⼀⾏记录，name 为张三，city 为帝都，插⼊这⾏记录的事务 id 是 80。
此时，DB_TRX_ID 的值就是 80，DB_ROLL_PTR 的值就是指向这条 insert undo ⽇志的指针。
接下来，如果有两个DB_TRX_ID 分别为100 、200 的事务对这条记录进⾏了update 操作，那么这条记录的版本链
就会变成下⾯这样：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 251 / 302

---

也就是说，当更新⼀⾏数据时，InnoDB 不会直接覆盖原有数据，⽽是创建⼀个新的数据版本，并更新 DB_TRX_ID 
和 DB_ROLL_PTR，使它们指向前⼀个版本和相关的 undo ⽇志。
这样，⽼版本的数据就不会丢失，可以通过版本链找到。
由于 undo ⽇志会记录每⼀次的 update，并且新插⼊的⾏数据会记录上⼀条 undo ⽇志的指针，所以可以通过 
DB_ROLL_PTR 这个指针找到上⼀条记录，这样就形成了⼀个版本链。
请详细说说什么是ReadView？
 
ReadView 是 InnoDB 为每个事务创建的⼀份“可⻅性视图”，⽤于判断在执⾏快照读时，哪些数据版本是当前这个
事务可以看到的，哪些不能看到。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 252 / 302

---

当事务开始执⾏时，InnoDB 会为该事务创建⼀个 ReadView，这个 ReadView 会记录 4 个重要的信息：
creator_trx_id：创建该 ReadView 的事务 ID。
m_ids：所有活跃事务的 ID 列表，活跃事务是指那些已经开始但尚未提交的事务。
min_trx_id：所有活跃事务中最⼩的事务 ID。它是 m_ids 数组中最⼩的事务 ID。
max_trx_id ：事务 ID 的最⼤值加⼀。换句话说，它是下⼀个将要⽣成的事务 ID。
ReadView 是如何判断记录的某个版本是否可⻅的？
 
会通过三个步骤来判断：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 253 / 302

---

①、如果某个数据版本的 DB_TRX_ID ⼩于 min_trx_id，则该数据版本在⽣成 ReadView 之前就已经提交，因此对
当前事务是可⻅的。
②、如果 DB_TRX_ID ⼤于 max_trx_id，则表示创建该数据版本的事务在⽣成 ReadView 之后开始，因此对当前事
务不可⻅。
③、如果 DB_TRX_ID 在 min_trx_id 和 max_trx_id 之间，需要判断 DB_TRX_ID 是否在 m_ids 列表中：
不在，表示创建该数据版本的事务在⽣成 ReadView 之后已经提交，因此对当前事务也是可⻅的。
在，表示事务仍然活跃，或者在当前事务⽣成 ReadView 之后才开始，因此是不可⻅的。
举个实际的例⼦。
读事务开启了⼀个 ReadView，这个 ReadView ⾥⾯记录了当前活跃事务的 ID 列表（444、555、665），以及最
⼩事务 ID（444）和最⼤事务 ID（666）。当然还有⾃⼰的事务 ID 520，也就是 creator_trx_id。
它要读的这⾏数据的写事务 ID 是 x，也就是 DB_TRX_ID。
如果 x = 110，显然在 ReadView ⽣成之前就提交了，所以这⾏数据是可⻅的。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 254 / 302

---

如果 x = 667，显然是未知世界，所以这⾏数据对读操作是不可⻅的。
如果 x = 519，虽然 519 ⼤于 444 ⼩于 666，但是 519 不在活跃事务列表⾥，所以这⾏数据是可⻅的。因为 
519 是在 520 ⽣成 ReadView 之前就提交了。
如果 x = 555，虽然 555 ⼤于 444 ⼩于 666，但是 555 在活跃事务列表⾥，所以这⾏数据是不可⻅的。因为 
555 不确定有没有提交。
可重复读和读已提交在 ReadView 上的区别是什么？
 
可重复读：在第⼀次读取数据时⽣成⼀个 ReadView，这个 ReadView 会⼀直保持到事务结束，这样可以保证在事
务中多次读取同⼀⾏数据时，读取到的数据是⼀致的。
读已提交：每次读取数据前都⽣成⼀个 ReadView，这样就能保证每次读取的数据都是最新的。
推荐阅读：搞懂Mysql之InnoDB MVCC
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 255 / 302

---

如果两个 AB 事务并发修改⼀个变量，那么 A 读到的值是什么，怎么分析。
 
事务 A 在读取时是否能读到事务 B 的修改，取决于 A 是快照读还是当前读。如果是快照读，InnoDB 会使⽤ 
MVCC 的 ReadView 判断记录版本是否可⻅，若事务 B 尚未提交或在 A 的视图不可⻅，则 A 会读到旧值；如果是
当前读，则需要加锁，若 B 已提交可直接读取，否则 A 会阻塞直到 B 结束。
1. Java ⾯试指南（付费）收录的美团⾯经同学 2 Java 后端技术⼀⾯⾯试原题：说说 MVCC，解决了什么
问题？
2. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：了解的 MVCC 吗？
3. Java ⾯试指南（付费）收录的联想⾯经同学 7 ⾯试原题：Mysql 四个隔离级别，MVCC 实现，如果两个
AB事务并发修改⼀个变量，那么A读到的值是什么，怎么分析，快照读的原理，读已提交和可重复读区
别，具体原理是什么。
4. Java ⾯试指南（付费）收录的oppo ⾯经同学 8 后端开发秋招⼀⾯⾯试原题：讲讲Mysql的MVCC机制
5. Java ⾯试指南（付费）收录的快⼿同学 2 ⼀⾯⾯试原题：事务隔离级别？MVCC机制介绍下？（版本
链）版本链通过什么控制
6. Java ⾯试指南（付费）收录的美团⾯经同学 15 点评后端技术⾯试原题：问了⼀下mysql的锁和MVCC
memo：2025 年 4 ⽉ 19 ⽇修改⾄此，今天有球友发帖说拿到了拼多多的 offer，这下真的是圆满了。
⾼可⽤
 
66.MySQL数据库读写分离了解吗？
 
读写分离就是把“写操作”交给主库处理，“读操作”分给多个从库处理，从⽽提升系统并发性能。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 256 / 302

---

应⽤层通过中间件（如 MyCat、ShardingSphere）⾃动路由请求，将 INSERT / UPDATE / DELETE 等写操作发送
给主库，将 SELECT 查询操作发送给从库。
// 示例：Java中通过不同数据源切换
@Transactional
public void updateOrder(Order order) {
    masterDataSource.update(order); // 写操作⾛主库
}
public Order getOrderById(Long id) {
    return slaveDataSource.query(id); // 读操作⾛从库
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 257 / 302

---

主库将数据变更通过 binlog 同步到从库，从⽽保持数据⼀致性。
主库 dump_thread 线程通过 TCP 将 binlog 推送给从库，从库 io_thread 线程，接收主库 binlog，写⼊ relay 
log，从库 sql_thread 线程读取 relay log，并顺序执⾏ SQL 语句，更新从库数据。
67.读写分离的实现⽅式有哪些？
 
实现读写分离有三种⽅式：最简单的是在应⽤层⼿动控制主从数据源，适⽤于⼩型项⽬；
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 258 / 302

---

中等项⽬是通过 Spring + 多数据源插件、AOP 注解⾃动路由；
⼤型系统通常使⽤中间件，如 ShardingSphere、MyCat，⽀持⾃动路由、负载均衡、故障转移等功能。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 259 / 302

---

Mycat 的读写分离功能依赖于 MySQL 的主从复制架构：
writeHost: 表示主节点，负责处理所有的 DML SQL 语句，如 INSERT、UPDATE 和 DELETE。
readHost: 表示从节点，负责处理查询 SQL 语句（如 SELECT），以实现读写分离。
正常情况下，Mycat 会将第⼀个配置的 writeHost 作为默认的写节点。所有的 DML SQL 语句会被发送到此默认写
节点执⾏。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 260 / 302

---

写节点完成数据写⼊后，通过 MySQL 的主从复制机制，将数据同步到所有从节点，确保主从数据⼀致性。
68.主从复制原理了解吗？
 
MySQL 的主从复制是⼀种数据同步机制，⽤于将数据从主数据库复制到⼀个或多个从数据库。
主库执⾏事务提交时，将数据变更以事件形式记录到 Binlog。从库通过 I/O 线程从主库的 Binlog 中读取变更事
件，并将这些事件写⼊到本地的中继⽇志⽂件中，SQL 线程会实时监控中继⽇志的内容，按顺序读取并执⾏这些事
件，从⽽保证从库与主库数据⼀致。
1. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 16 ⼀⾯⾯试原题：MySQL 的主从复制过程
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 261 / 302

---

69.主从同步延迟怎么处理？
 
主从同步延迟是因为从库需要先接收 binlog，再执⾏ SQL 才能同步主库数据，在⾼并发写或⽹络抖动时容易出现
延迟，导致读写不⼀致。
第⼀种解决⽅案：对⼀致性要求⾼的查询（如⽀付结果查询）可以直接⾛主库。
第⼆种解决⽅案：对于⾮关键业务允许短暂数据不⼀致，可以提示⽤户“数据同步中，请稍后刷新”，然后借助异步
通知机制替代实时查询。
第三种解决⽅案：采⽤半同步复制，主库在事务提交时，要等⾄少⼀个从库确认收到 binlog（但不要求执⾏完
成），才算提交成功。
// 伪代码示例
public Object query(String sql) {
    if(isWriteQuery(sql) || needStrongConsistency(sql)) {
        return masterDataSource.query(sql);
    } else {
        return slaveDataSource.query(sql);
    }
}
// 伪代码示例
public Object query(String sql) {
    if(isWriteQuery(sql)) {
        return masterDataSource.query(sql);
    } else {
        // 异步通知⽤户数据已更新
        notifyUser("数据同步中，请稍后刷新");
        return slaveDataSource.query(sql);
    }
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 262 / 302

---

请说说半同步复制的流程？
 
第⼀步，主库安装半同步插件：
第⼆步，主库启⽤半同步复制并设置超时时间：
主库 my.cnf 配置示例：
第三步，从库安装半同步插件：
第四步，从库启⽤半同步复制：
INSTALL PLUGIN rpl_semi_sync_master SONAME 'semisync_master.so';
SET GLOBAL rpl_semi_sync_master_enabled = 1;
SET GLOBAL rpl_semi_sync_master_timeout = 10000;
[mysqld]
plugin-load = "rpl_semi_sync_master=semisync_master.so"
rpl_semi_sync_master_enabled = 1
rpl_semi_sync_master_timeout = 10000
# MySQL 5.7+建议使⽤⽆损模式
rpl_semi_sync_master_wait_point = AFTER_SYNC
INSTALL PLUGIN rpl_semi_sync_slave SONAME 'semisync_slave.so';
SET GLOBAL rpl_semi_sync_slave_enabled = 1;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 263 / 302

---

从库 my.cnf 配置示例：
memo：2025 年 4 ⽉ 20 ⽇修改⾄此，今天给球友修改简历时，碰到有球友说，把星球安利给了同⻔，进来后都
说好，⼝碑+1，很欣慰呢。
70.
你们⼀般是怎么分库的呢？
 
分库的策略有两种，第⼀种是垂直分库：按照业务模块将不同的表拆分到不同的库中，⽐如说⽤户、登录、权限等
表放在⽤户库中，商品、分类、库存放在商品库中，优惠券、满减、秒杀放在活动库中。
[mysqld]
plugin-load = "rpl_semi_sync_slave=semisync_slave.so"
rpl_semi_sync_slave_enabled = 1
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 264 / 302

---

第⼆种是⽔平分库：按照⼀定的策略将⼀个表中的数据拆分到多个库中，⽐如哈希分⽚和范围分⽚，对⽤户 id 进
⾏取模运算或者范围划分，将数据分散到不同的库中。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 265 / 302

---

贴⼀段使⽤ ShardingSphere 的 inline 算法定义分⽚规则：
rules:
- !SHARDING
  tables:
    order:
      actualDataNodes: db_${0..3}.order_${0..15}
      databaseStrategy:
        standard:
          shardingColumn: user_id
          shardingAlgorithmName: db_hash_mod
      tableStrategy:
        standard:
          shardingColumn: order_time
          shardingAlgorithmName: table_interval_yearly
  shardingAlgorithms:
    db_hash_mod:
      type: HASH_MOD
      props:
        sharding-count: 4
    table_interval_yearly:
      type: INTERVAL
      props:
        datetime-pattern: 'yyyy-MM-dd HH:mm:ss'
        datetime-lower: '2024-01-01 00:00:00'
        datetime-upper: '2025-01-01 00:00:00'
        sharding-suffix-pattern: 'yyyy'
        datetime-interval-amount: 1
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 266 / 302

---

1. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：分库分表了解吗
2. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：说说分库分表的准则
71.
那你们是怎么分表的？
 
当单表超过 500 万条数据，就可以考虑⽔平分表了。⽐如说我们可以将⽂章表拆分成多个表，如 article_0、
article_9999、article_19999 等。
在技术派实战项⽬中，我们将⽂章的基本信息和内容详情做了垂直分表处理，因为⽂章的内容会占⽤⽐较⼤的空
间，在只需要查看⽂章基本信息时把⽂章详情也带出来的话，就会占⽤更多的⽹络 IO 和内存导致查询变慢；⽽⽂
章的基本信息，如标题、作者、状态等信息占⽤的空间较⼩，很适合不需要查询⽂章详情的场景。
        datetime-interval-unit: 'Years'
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 267 / 302

---

1. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：分库分表了解吗
2. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：说说分库分表的准则
3. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：表存满了之后怎么扩表？
72.⽔平分库分表的分⽚策略有哪⼏种？
 
常⻅的分⽚策略有三种，范围分⽚、Hash 分⽚和路由分⽚。
范围分⽚是根据某个字段的值范围进⾏⽔平拆分。适⽤于分⽚键具有连续性的场景。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 268 / 302

---

⽐如说将 user_id 作为分⽚键：
1 ~ 10000 → db1.user_1
10001 ~ 20000 → db2.user_2
Hash 分⽚是指通过对分⽚键的值进⾏哈希取模，将数据均匀分布到多个库表中，适⽤于分⽚键具有离散性的场
景。
⽐如说我们⼀开始规划好了 4 个表，那么就可以简单地通过取模来实现分表：
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 269 / 302

---

order_id
table_id
xxxx
table_1
yyyy
table_2
zzzz
table_3
路由分⽚是通过路由配置来确定数据应该存储在哪个库表，适⽤于分⽚键不规律的场景。
⽐如说我们可以通过 order_router 表来确定订单数据存储在哪个表中：
1. Java ⾯试指南（付费）收录的腾讯⾯经同学 24 ⾯试原题：项⽬中的⽔平分表是怎么做的？分⽚键具体
是怎么设置的？
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：分库分表具体的分⽚策略是怎么做
的？
memo：2025 年 4 ⽉ 22 ⽇修改⾄此，今天给拿到蚂蚁暑期实习的球友发了个红包，感谢他在星球的经验贴，这
些精华帖也会帮助到下⼀届的球友们，真的⾮常感谢。
public String getTableNameByHash(long userId) {
    int tableIndex = (int) (userId % 4);
    return "user_" + tableIndex;
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 270 / 302

---

⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 271 / 302

---

73.不停机扩容怎么实现？
 
第⼀个阶段：新旧库同时写⼊，确保数据实时同步；可以借助消息队列实现异步补偿，幂等避免重复写⼊。读操作
仍然⾛旧库。
代码参考：
第⼆个阶段，通过 Canal 或者⾃研脚本将旧库的历史数据同步到新库。关键业务在查询时同时查询新旧库，进⾏数
据校验，确保⼀致性。
@Transactional
public void createOrder(Order order) {
    oldDB.insert(order);  // 写⼊旧库
    newDB.insert(order);  // 写⼊新扩容节点
    kafka.send("data_sync", order);  // 异步补偿通道
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 272 / 302

---

第三个阶段，在确认新库数据⼀致性后，逐步将读请求切换到新库，然后下线旧库。
74.常⽤的分库分表中间件有哪些？
 
常⽤的分库分表中间件有 ShardingSphere 和 Mycat。
①、ShardingSphere 最初由当当开源，后来贡献给了 Apache，其⼦项⽬ Sharding-JDBC 主要在 Java 的 JDBC 层
提供额外的服务。⽆需额外部署和依赖，可理解为增强版的 JDBC 驱动，完全兼容 JDBC 和各种 ORM 框架。
public List<Order> getOrders(Long userId) {
    List<Order> orders = newDB.getOrders(userId);
    List<Order> oldOrders = oldDB.getOrders(userId);
    if (!orders.equals(oldOrders)) {
        // 数据不⼀致，进⾏补偿
        kafka.send("data_sync", oldOrders);
    }
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 273 / 302

---

②、Mycat 是由阿⾥巴巴的⼀款产品 Cobar 衍⽣⽽来，可以把它看作⼀个数据库代理。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 274 / 302

---

推荐阅读：mycat 介绍
75.你觉得分库分表会带来什么问题呢？
 
第⼀，跨库事务⽆法依赖单机 MySQL 的 ACID 特性，需要使⽤分布式事务解决⽅案，如 Seata 的 AT 模式、TCC 
模式等。
第⼆，跨库后⽆法使⽤ JOIN 联表查询。可以在业务层进⾏拼接，或者把需要联表查询的数据放到 ES 中。
第三，⾃增 ID 在分⽚场景下容易冲突，需要使⽤全局唯⼀⽅案。
数据库表被切分后，不能再依赖数据库⾃身的主键⽣成机制，所以需要⼀些⼿段来保证全局主键唯⼀。⽐如说雪花
算法、京东的 JD-hotkey。
// Java 代码示例
User user = userService.getUserById(1);
List<Order> orders = orderService.getOrdersByUserId(1);
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 275 / 302

---

你们项⽬中的分布式主键 id 是怎么⽣成的？
 
在技术派项⽬中，我们在雪花算法的基础上实现了⼀套⾃定义的 ID ⽣成⽅案，通过更改时间戳单位、ID ⻓度、
workId 与 dataCenterId 的分配⽐例，ID ⽣成的延迟降低了 20%；满⾜了分布式环境下 ID 的唯⼀性。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 276 / 302

---

雪花算法具体是怎么实现的？
 
雪花算法是 Twitter 开源的分布式 ID ⽣成算法，其核⼼思想是：使⽤⼀个 64 位的数字来作为全局唯⼀ ID。
第 1 位是符号位，永远是 0，表示正数。
接下来的 41 位是时间戳，记录的是当前时间戳减去⼀个固定的开始时间戳，可以使⽤ 69 年。
然后是 10 位的⼯作机器 ID。
最后是 12 位的序列号，每毫秒最多可⽣成 4096 个 ID。
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 277 / 302

---

⼤致的实现代码如下所示：
public class SnowflakeIdGenerator {
    private long datacenterId = 1L; // 数据中⼼ID
    private long machineId = 1L; // 机器ID
    private long sequence = 0L; // 序列号
    private long lastTimestamp = -1L;
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095;
            if (sequence == 0) {
                while (timestamp == lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 278 / 302

---

1. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：id是怎么⽣成的？（分布式⾃增主
键）
的需要恭喜啦
。
        return ((timestamp - 1609459200000L) << 22) | (datacenterId << 17) | (machineId 
<< 12) | sequence;
    }
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 279 / 302

---

运维
 
76.百万级别以上的数据如何删除？
 
在处理百万级别的数据删除时，⼤范围的 DELETE 语句往往会造成锁表时间⻓、事务⽇志膨胀等问题。
可以采⽤批量删除的⽅案，将删除操作分成多个⼩批次进⾏处理。
也可以采⽤创建新表替换原表的⽅式，把需要保留的数据迁移到新表中，然后删除旧表。
简单的⽅案：
public void batchDelete(String tableName, String condition, int batchSize) {
    // 1. 创建线程池
    int threadCount = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    // 2. 获取总记录数
    long totalCount = getTotalCount(tableName, condition);
    
    // 3. 计算每个线程处理的数据量
    long perThreadCount = totalCount / threadCount;
    
    // 4. 分配任务给线程池
    for (int i = 0; i < threadCount; i++) {
        long startId = i * perThreadCount;
        long endId = (i == threadCount - 1) ? totalCount : (startId + perThreadCount);
        
        executor.execute(() -> {
            try {
                // 分批次删除数据
                for (long j = startId; j < endId; j += batchSize) {
                    String deleteSql = String.format(
                        "DELETE FROM %s WHERE %s LIMIT %d",
                        tableName, condition, batchSize
                    );
                    // 执⾏删除
                    jdbcTemplate.update(deleteSql);
                }
            } finally {
                latch.countDown();
            }
        });
    }
    
    // 5. 等待所有线程完成
    latch.await();
    executor.shutdown();
}
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 280 / 302

---

加⼊检查表空间、分批导⼊数据、验证数据⼀致性等步骤：
-- 1. 创建新表结构(包含索引)
CREATE TABLE new_table LIKE large_table;
-- 2. 插⼊需要保留的数据
INSERT INTO new_table 
SELECT * FROM large_table WHERE condition;
-- 3. 重命名表
RENAME TABLE large_table TO old_table, new_table TO large_table;
-- 4. 删除旧表
DROP TABLE old_table;
-- 1. 在执⾏之前先检查空间是否⾜够
SELECT table_schema, 
       table_name, 
       round(((data_length + index_length) / 1024 / 1024), 2) "Size in MB"
FROM information_schema.TABLES 
WHERE table_schema = DATABASE()
AND table_name = 'large_table';
-- 2. 创建新表
CREATE TABLE new_table LIKE large_table;
-- 3. 分批导⼊数据（避免⼀次性导⼊过多数据）
SET @batch = 1;
SET @batch_size = 10000;
SET @total = (SELECT COUNT(*) FROM large_table WHERE condition);
REPEAT
    INSERT INTO new_table 
    SELECT * FROM large_table 
    WHERE condition
    LIMIT @batch_size;
    
    SET @batch = @batch + 1;
UNTIL @batch * @batch_size > @total END REPEAT;
-- 4. 验证数据⼀致性
SELECT COUNT(*) FROM new_table;
SELECT COUNT(*) FROM large_table WHERE condition;
-- 5. 在业务低峰期执⾏表切换
RENAME TABLE large_table TO old_table, 
             new_table TO large_table;
-- 6. 确认⽆误后再删除旧表（建议不要⽴即删除）
-- DROP TABLE old_table;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 281 / 302

---

77.千万级⼤表如何添加字段？
 
在低版本的 MySQL 中，千万级数据量的表中添加字段时，直接使⽤ ALTER TABLE 命令会导致⻓时间锁表、甚⾄
数据库崩溃等。
可以使⽤ Percona Toolkit 的 pt-online-schema-change 来完成，它通过创建临时表、逐步同步数据并使⽤触发器
捕获变更来实现。
对于 MySQL 8.0+ 版本，可以直接通过 ALTER TABLE 来完成，因为加⼊了 INSTAN 算法，添加列并不会⻓时间锁
表。
如果没有指定 ALGORITHM=INSTANT 算法，MySQL 会先尝试 INSTANT 算法；如果⽆法完成，会切换到 INPLACE 
算法；如果仍然⽆法完成，会尝试 COPY 算法。
78.MySQL 导致 cpu 飙升的话，要怎么处理呢？
 
我通常先通过 top 命令确认是否是 mysqld 的进程占⽤。
pt-online-schema-change --alter "ADD COLUMN new_column datatype" D=database,t=your_table 
--execute
ALTER TABLE your_table ADD COLUMN new_column datatype;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 282 / 302

---

然后通过 SHOW PROCESSLIST 和慢查询⽇志定位是否存在耗时 SQL，再配合 explain 和 performance_schema 
分析 SQL 是否命中索引，是否存在临时表和排序。
最终通过 SQL 优化、加索引、分批操作等⼿段逐步优化。
。
SQL 题
 
-- 使⽤ EXPLAIN 分析SQL执⾏计划
EXPLAIN SELECT * FROM large_table WHERE condition;
-- 查看表的索引使⽤情况
SHOW INDEX FROM table_name;
-- 查看InnoDB状态
SHOW ENGINE INNODB STATUS;
-- 查看表的统计信息
ANALYZE TABLE table_name;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 283 / 302

---

79.⼀张表：id，name，age，sex，class，sql 语句：所有年龄为 18 的⼈
的名字？找到每个班年龄⼤于 18 有多少⼈？找到每个班年龄排前两名的
⼈？（补充）
 
建议⼤家在本地建表，实操⼀下。 2024 年 04 ⽉ 11 ⽇增补。
第⼀步，建表：
第⼆步，插⼊数据：
所有年龄为 18 的⼈的名字？
 
这条 SQL 语句从表中选择age 等于 18 的所有记录，并返回这些记录的name 字段。
如果可以的话，可以给 age 字段加上索引。
CREATE TABLE students (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50),
    age INT,
    sex CHAR(1),
    class VARCHAR(50)
);
INSERT INTO students (name, age, sex, class) VALUES
('沉默王⼆', 18, '⼥', '三年⼆班'),
('沉默王⼀', 20, '男', '三年⼆班'),
('沉默王三', 19, '男', '三年三班'),
('沉默王四', 17, '男', '三年三班'),
('沉默王五', 20, '⼥', '三年四班'),
('沉默王六', 21, '男', '三年四班'),
('沉默王七', 18, '⼥', '三年四班');
SELECT name FROM students WHERE age = 18;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 284 / 302

---

找到每个班年龄⼤于 18 有多少⼈?
 
这条 SQL 语句先筛选出年龄⼤于 18 的记录，然后按class 分组，并通过 count 统计每个班的学⽣数。
找到每个班年龄排前两名的⼈？
 
这个查询稍微复杂⼀些，需要使⽤⼦查询和去重 DISTINCT 。
这条 SQL 语句⾸先从students 表中选择class 、name 和age 字段，然后使⽤⼦查询计算每个班级中年龄排前两
名的学⽣。
ALTER TABLE students ADD INDEX age_index (age);
SELECT class, COUNT(*) AS number_of_students
FROM students
WHERE age > 18
GROUP BY class;
SELECT a.class, a.name, a.age
FROM students a
WHERE (
    SELECT COUNT(DISTINCT b.age)
    FROM students b
    WHERE b.class = a.class AND b.age > a.age
) < 2
ORDER BY a.class, a.age DESC;
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 285 / 302

---

1. Java ⾯试指南（付费）收录的奇安信⾯经同学 1 Java 技术⼀⾯⾯试原题：⼀张表：id，name，age，
sex，class，sql 语句：所有年龄为 18 的⼈的名字？找到每个班年龄⼤于 18 有多少⼈？找到每个班年
龄排前两名的⼈？
80.有⼀个查询需求，MySQL 中有两个表，⼀个表 1000W 数据，另⼀个表
只有⼏千数据，要做⼀个关联查询，如何优化
 
第⼀步，为关联字段建⽴索引，确保 on 连接的字段都有索引。
第⼆步，⼩表驱动⼤表，将⼩表放在 JOIN 的左边（驱动表），⼤表放在右边。
1. Java ⾯试指南（付费）收录的京东⾯经同学 1 Java 技术⼀⾯⾯试原题：有⼀个查询需求，MySQL 中有
两个表，⼀个表 1000W 数据，另⼀个表只有⼏千数据，要做⼀个关联查询，如何优化
ALTER TABLE big_table ADD INDEX idx_small_id(small_id);
SELECT ... FROM small_table s 
JOIN big_table b ON s.id = b.small_id
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 286 / 302

---

81.新建⼀个表结构，创建索引，将百万或千万级的数据使⽤ insert 导⼊该
表，新建⼀个表结构，将百万或千万级的数据使⽤ isnert 导⼊该表，再创建
索引，这两种效率哪个⾼呢？或者说⽤时短呢？
 
先说结论：
在⼤数据量导⼊场景下，先导⼊数据，后建索引的效率显著⾼于先建索引，后导⼊数据的效率。
来，实操。
先创建⼀个表，然后创建索引，执⾏插⼊语句，来看看执⾏时间（100 万数据在我本机上执⾏时间⽐较⻓，我们就
⽤ 10 万条数据来测试）。
总的时间 13.93+0.01+0.01+0.01=13.96 秒。
接下来，我们再创建⼀个表，执⾏插⼊操作，然后创建索引。
CREATE TABLE test_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_name ON test_table(name);
DELIMITER //
CREATE PROCEDURE insert_data()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 1000000 DO
        INSERT INTO test_table(name, email, created_at)
        VALUES (CONCAT('wanger',i), CONCAT('email', i, '@example.com'), NOW());
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_data();
⾯渣逆袭MySQL篇V2-让天下所有的⾯渣都能逆袭
No. 287 / 302

---

来看⼀下总的时间，0.01+0.00+13.08+0.18=13.27 秒。
先插⼊数据再创建索引的⽅式⽐先创建索引再插⼊数据要快⼀点。
然后时间差距很微⼩，主要是因为我们插⼊的数据少。说⼀下差别。
先插⼊数据再创建索引：在没有索引的情况下插⼊数据，数据库不需要在每次插⼊时更新索引。
先创建索引再插⼊数据：数据库需要在每次插⼊新记录时维护索引结构，随着数据量的增加，索引的维护会
导致额外的性能开销。
MySQL是先建⽴索引好还是先插⼊数据好？
 
如果是⼩批量插⼊，可以先建索引；但在⼤数据量数据导⼊场景下，推荐先插⼊数据再建索引。
因为索引是基于 B+ 树的，⼤量插⼊时如果提前建索引，会频繁触发⻚分裂和索引结构调整，影响性能。
插⼊完成后统⼀构建索引，MySQL 会按顺序批量⽣成索引结构，速度更快、资源消耗更低。
CREATE TABLE test_table_no_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL
);
DELIMITER //
CREATE PROCEDURE insert_data_no_index()
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < 1000000 DO
        INSERT INTO test_table_no_index(name, email, created_at)
        VALUES (CONCAT('wanger', i), CONCAT('email', i, '@example.com'), NOW());
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;
CALL insert_data_no_index();
CREATE INDEX idx_name_no_index ON test_table_no_index(name);

82.什么是深分页，select * from tbn limit 1000000000 这个有什么问题，如果表大或者表小分别什么问题

深分页是指在 MySQL 中获取比较靠后的数据页，比如第 1000 页、第 10000 页等。特别是使用 LIMIT offset,count 这种方式，当 offset 特别大，就会带来严重的性能问题。
对于 SELECT * FROM tbn LIMIT 1000000,10 ，这样的查询语句来说，MySQL 会：
从表中读取第一条记录，判断是否满足 where 条件；如果满足，计数器+1；否则直到 计数器累计到 1000000 时才开始真正取数据
再继续获取 10 条数据，返回
性能会非常差，因为需要从头扫描，无法利用索引优化，并且需要抛弃大量不需要的数据，占用大量的内存和 CPU 资源。
可以借助主键索引分页进行优化：
或者记住上次分页的最大 ID，然后再查询：
SELECT * FROM tbn
WHERE id > (SELECT id FROM tbn ORDER BY id LIMIT 1000000, 1)
LIMIT 10
SELECT * FROM tbn
WHERE id > last_page_max_id
LIMIT 10

83.SQL 题：一个学生成绩表，字段有学生姓名、班级、成绩，求各班前十名

第一步，建表：
第二步，插入数据：
CREATE TABLE student_scores (
    student_name VARCHAR(100),
    class VARCHAR(50),
    score INT
);
INSERT INTO student_scores (student_name, class, score) VALUES
('沉默王二', '三年二班', 88),
('沉默王三', '三年二班', 92),
('沉默王四', '三年二班', 87),
('沉默王五', '三年二班', 85),
('沉默王六', '三年二班', 90),
('沉默王七', '三年二班', 95),
('沉默王八', '三年二班', 82),
('沉默王九', '三年二班', 78),

步骤
解释
@cur_class 变量
记录当前正在处理的班级
@cur_rank 变量
记录当前班级的排名，默认 0
IF(@cur_class = class, @cur_rank + 1, 1)
如果班级没变，就排名 +1；如果换了新班级，排名从 1 重新开始
@cur_class := class
更新当前班级变量，保持班级变化跟踪
ORDER BY class, score DESC
必须先按班级升序、成绩降序排好，才能保证变量正确打排名
外层 WHERE rank <= 10
只取每班前十名

第三步，查询各班前十名。如果 MySQL 是 8.0 以下版本，不支持窗口函数，可以通过在查询中维护班级当前处理状态和排名，实现分组内按成绩排序并打标号，再取前十名。
('沉默王十', '三年二班', 91),
('沉默王十一', '三年二班', 79),
('沉默王十二', '三年三班', 84),
('沉默王十三', '三年三班', 81),
('沉默王十四', '三年三班', 90),
('沉默王十五', '三年三班', 88),
('沉默王十六', '三年三班', 87),
('沉默王十七', '三年三班', 93),
('沉默王十八', '三年三班', 89),
('沉默王十九', '三年三班', 85),
('沉默王二十', '三年三班', 92),
('沉默王二十一', '三年三班', 84);
SET @cur_class = NULL, @cur_rank = 0;
SELECT student_name, class, score
FROM (
    SELECT
        student_name,
        class,
        score,
        @cur_rank := IF(@cur_class = class, @cur_rank + 1, 1) AS rank,
        @cur_class := class
    FROM student_scores
    ORDER BY class, score DESC
) AS ranked
WHERE ranked.rank <= 10;

如果是 MySQL 8.0+ 版本，可以使用窗口函数来完成：
SELECT student_name, class, score
FROM (
    SELECT 
        student_name, 
        class, 
        score,
        ROW_NUMBER() OVER (PARTITION BY class ORDER BY score DESC) AS rn
    FROM student_scores
) AS tmp
WHERE rn <= 10;

SQL 用到的技术
说明
ROW_NUMBER() OVER (PARTITION BY class ORDER BY score DESC)
给每个班独立打排名，从 1 开始
子查询 tmp
用来临时生成带有 rn（排名）的数据集
外层 WHERE rn <= 10
选出每个班排名前 10 的学生
ORDER BY score DESC
成绩高排前面，符合常规排名逻辑

---

## 图表说明

> [图] 第1页：有技术知识点。该图宣传的是《面渣逆袭 MySQL 篇》学习资料，内容涵盖 MySQL 相关的面试知识点，包含 83 道常见面试题及详细解析，适合准备 Java 后端开发岗位面试的技术人员学习使用。

> [图] 第2页：有技术知识点。该图展示了MySQL InnoDB存储引擎中B+树索引的结构，说明了主键索引和非主键索引的组织方式，以及如何通过索引快速查找数据，是理解MySQL索引机制的重要内容。

> [图] 第3页：有技术知识点。该图展示了MySQL数据页结构的相关内容，包括InnoDB中数据页的大小、双链表连接方式以及推荐的参考资料，涉及数据库存储引擎和页面管理机制。

> [图] 第4页：有技术知识点。图中展示了多个与Java技术相关的学习资料，包括JVM、Spring、Redis、MyBatis、并发编程等主题的PDF和Markdown文档，适合Java开发者深入学习和面试准备。

> [图] 第5页：有技术知识点。该图描述了MySQL中事务可见性判断规则，基于事务ID（DB_TRX_ID）与min_trx_id、max_trx_id及m_ids列表的关系来决定数据版本对当前事务是否可见。

> [图] 第5页：有技术知识点：该图显示了在 macOS 系统上通过 Homebrew 安装的 MySQL 8.3.0 版本信息，运行于 arm64 架构。

> [图] 第8页：有技术知识点。该图展示了SQL中不同类型的JOIN操作（如INNER JOIN、LEFT JOIN、RIGHT JOIN、FULL OUTER JOIN等）的逻辑关系，通过Venn图直观地说明了各JOIN类型返回的数据范围。

> [图] 第9页：有技术知识点。该图展示了一条MySQL查询语句，使用LEFT JOIN连接文章表和评论表，并通过LEFT函数截取字段内容，返回文章标题和对应评论内容的前20个字符，体现了SQL中表连接、字段截取和LIMIT限制结果集的基本用法。

> [图] 第9页：有技术知识点。该图展示了一条MySQL查询语句，通过INNER JOIN连接文章和评论表，并使用LEFT函数截取标题和评论内容的前20个字符，返回前两条记录。

> [图] 第10页：有技术知识点。该图展示了一条MySQL查询语句，使用了RIGHT JOIN连接文章和评论表，并通过LEFT函数截取标题和评论内容的前20个字符，结果中包含NULL值，体现了外连接的特性。

> [图] 第10页：有技术知识点。该图是一篇关于小赢科技面试经验的分享，包含Java内存模型、垃圾回收、Redis和MySQL等技术问题，涉及JVM、多线程、数据库和数据结构等核心计算机技术内容。

> [图] 第11页：有技术知识点。该图展示了数据库规范化中的三个范式（第一范式、第二范式、第三范式）的包含关系，表示第三范式是第二范式的子集，第二范式是第一范式的子集。

> [图] 第12页：有，这张图展示了一个订单明细表，包含订单编号、商品编号和数量，体现了数据库中常见的“一对多”关系模型，可用于理解数据表结构与关联查询。

> [图] 第13页：有技术知识点。该图展示了 `char` 和 `varchar` 两种字符串类型在存储上的区别：`char` 固定长度，会填充空格；`varchar` 可变长度，只占用实际字符所需空间。

> [图] 第14页：这张图没有技术知识点。

> [图] 第15页：有技术知识点。代码展示了在邮件发送成功后更新记录的通知时间、通知次数和更新时间的操作，涉及Java中Date对象的使用和实体类属性的设置。

> [图] 第15页：有技术知识点。该图展示了数据库设计中与时间字段相关的四个关键考虑因素：存储空间、日期范围、默认值和时区相关性。

> [图] 第17页：有技术知识点。该图展示了MySQL中服务器字符集的配置，显示当前`character_set_server`变量的值为`utf8mb4`，表示数据库服务器使用UTF-8编码支持完整Unicode字符集。

> [图] 第18页：这张图无技术知识点。

> [图] 第19页：有技术知识点。该图展示了MySQL中`EXPLAIN`命令的执行计划输出，用于分析查询性能，显示了查询类型、扫描行数、索引使用情况等关键信息。

> [图] 第20页：有技术知识点。该图展示了MySQL中使用`EXPLAIN`命令分析查询执行计划的结果，表明对表`t1`的`COUNT(*)`查询通过主键索引（PRIMARY）进行扫描，利用了索引优化，属于数据库性能调优中的典型SQL执行分析内容。

> [图] 第20页：有技术知识点。该图展示了MySQL中表`t1`的结构，包含字段名、数据类型、是否允许为空、主键信息、默认值等，用于描述数据库表的元数据信息。

> [图] 第21页：有技术知识点。该图描述了MySQL中`SELECT COUNT(*)`的执行机制，特别是在InnoDB存储引擎下的行为、性能优化以及与`COUNT(1)`的等效性。

> [图] 第22页：这张图没有直接的技术知识点，主要是一段聊天记录和一张表情包图片，用于表达情感或态度。其中“咪咕公司的大模型应用开发”涉及大模型应用开发这一技术方向，但图中并未展示具体技术内容。  
一句话描述：无明确技术知识点，仅提及大模型应用开发方向。

> [图] 第23页：有技术知识点。该图展示了SQL查询语句的执行流程和各子句的逻辑顺序，包括from、join、where、group by、having、select、distinct、order by和limit等关键组件的处理顺序。

> [图] 第25页：有技术知识点。该图展示了MySQL的常用命令分类，包括数据库、表、数据操作、索引和约束、用户权限管理以及事务管理等核心操作命令。

> [图] 第29页：有技术知识点。MySQL中不同类型数据在加法运算时的隐式类型转换规则，字符串与数字相加会尝试将字符串转为数字，无法转换的部分视为0，且可能产生警告。

> [图] 第30页：有技术知识点。这张图展示了MySQL中不同数据类型在进行加法运算时的行为，包括数值、字符串和混合类型之间的隐式转换及结果，体现了MySQL的类型转换规则和运算优先级。

> [图] 第34页：有技术知识点。该图展示了MySQL数据库的架构，包括客户端与服务器之间的交互、连接处理、查询缓存、解析器、优化器以及存储引擎（如InnoDB、MyISAM、MEMORY）的层次结构。

> [图] 第35页：有技术知识点。该图展示了MySQL数据库处理SQL语句的执行流程，包括客户端发送SQL语句后，经过MySQL服务器的连接器、分析器、优化器和执行器，最终通过存储引擎（如InnoDB或MyISAM）完成数据操作的全过程。

> [图] 第36页：有技术知识点。该图展示了MySQL中InnoDB引擎执行UPDATE语句时的流程，包括从服务层到存储引擎的交互、内存与磁盘的数据读取、redo log和binlog的写入，以及两阶段提交机制。

> [图] 第37页：有技术知识点。该图展示了数据库存储结构的层次关系，从表空间到段、区、页和记录的组织方式，涉及InnoDB存储引擎中的数据存储逻辑。

> [图] 第38页：有技术知识点。该图展示了MySQL数据库表`column_article`的结构信息，其中“Row_format: Dynamic”表明该表使用动态行格式存储数据，适用于包含可变长度字段（如VARCHAR、TEXT）的表，有助于优化存储空间。

> [图] 第39页：有技术知识点。该图展示了MySQL中三种常见存储引擎（InnoDB、MyISAM、MEMORY）的主要特性对比，包括是否支持事务、锁机制、外键以及存储方式等关键差异。

> [图] 第40页：有，这张图展示了InnoDB不同页大小对应的表空间最大容量，体现了MySQL存储引擎的配置与性能优化相关技术知识点。

> [图] 第41页：是的，这张图有技术知识点。它展示了一个MyISAM存储引擎的B+树索引结构，其中节点包含键值和指向数据记录的指针，右侧表格表示实际数据文件（user_myisam.MYD）中的记录内容，体现了索引与数据之间的映射关系。

> [图] 第41页：有技术知识点。该图展示了MySQL中MyISAM和InnoDB两种存储引擎在存储结构、事务支持、最小锁粒度、索引类型、表行数、外键支持和主键必需性等方面的区别。

> [图] 第42页：有技术知识点。该图展示了数据库B+树索引结构，说明了不同页中记录类型（如目录项和用户记录）的组织方式及索引列、非索引列的存储布局。

> [图] 第43页：有技术知识点。该图展示了InnoDB存储引擎的内存结构与磁盘结构，包括缓冲池、日志缓冲、系统表空间、重做日志、撤销日志等关键组件及其交互关系。

> [图] 第44页：有技术知识点。这张图展示了InnoDB存储引擎中数据页的内部结构，包括文件头、页头、最小和最大记录、用户记录、空闲空间、页目录和文件尾等组成部分及其大小。

> [图] 第45页：有技术知识点。该图展示了MySQL中InnoDB存储引擎的页面大小配置，显示当前`innodb_page_size`为16384字节（即16KB），这是InnoDB默认的页大小，影响数据存储和I/O性能。

> [图] 第45页：有技术知识点。该图展示了数据库存储引擎中数据页从空闲状态到逐步插入记录直至填满的结构变化过程，涉及File Header、Page Header、Infimum+Supremum、User Records、Free Space、Page Directory和File Trailer等关键组成部分。

> [图] 第45页：有技术知识点。该图展示了数据库存储结构中数据页的组成和链式连接方式，包括File Header、Page Header、Infimum + Supremum、User Records、Free Space、Page Directory 和 File Trailer 等部分。

> [图] 第46页：有。这张图展示了MySQL中InnoDB存储引擎的架构，包括内存中的缓冲池、自适应哈希索引、日志缓冲区以及磁盘上的表空间和重做日志文件等核心组件及其交互关系。

> [图] 第46页：有技术知识点。该图展示了操作系统中页面置换算法（如LRU）的双链表结构，分为new区域和old区域，用于管理内存页的访问顺序和淘汰策略。

> [图] 第47页：有技术知识点。该图展示了Java虚拟机（JVM）垃圾回收中的堆内存结构，分为新生代和老年代，其中新生代用橙色表示，老年代用绿色表示，midpoint标记了两者之间的分界点。

> [图] 第47页：有技术知识点。该图展示了MySQL中InnoDB存储引擎的缓冲池大小配置，`innodb_buffer_pool_size`的值为134217728字节（即128MB），用于控制InnoDB缓冲池的内存使用量，影响数据库的读写性能。

> [图] 第48页：有技术知识点。该图展示了MySQL中InnoDB存储引擎的两个与缓冲池相关的配置参数：`innodb_old_blocks_pct` 设置为37，表示旧区块占缓冲池的比例；`innodb_old_blocks_time` 设置为1000，表示数据页在缓冲池中停留的时间（毫秒），用于优化查询性能和缓存策略。

> [图] 第49页：有技术知识点。该图展示了MySQL数据库中常见的几种日志类型，包括错误日志、慢查询日志、一般查询日志、二进制日志（binlog）、重做日志（redo log）和回滚日志（undo log），用于记录数据库运行状态、查询性能及事务操作等信息。

> [图] 第50页：有技术知识点。该图展示了MySQL中二进制日志格式（binlog_format）的配置，当前值为ROW，表示以行级格式记录二进制日志，适用于主从复制和数据恢复等场景。

> [图] 第51页：有技术知识点。图中展示了MySQL数据目录下的文件列表，其中高亮的`mysql-bin.000001`和`mysql-bin.index`是MySQL二进制日志文件，用于记录数据库的变更操作，支持数据恢复和主从复制。

> [图] 第51页：有技术知识点。该图展示了MySQL中与二进制日志（binlog）相关的配置变量，其中`log_bin`为OFF表示二进制日志功能未启用，而`sql_log_bin`为ON表示当前会话的SQL语句会被记录到二进制日志中，两者状态不一致可能影响主从复制等场景。

> [图] 第54页：有技术知识点。该图展示了InnoDB存储引擎中数据更新的流程：磁盘数据加载到缓冲池，直接更新缓冲池数据并记录更新信息到重做日志缓存，最后将重做日志缓存刷盘到redo日志文件中。

> [图] 第54页：有技术知识点。该图展示了MySQL中`innodb_log_buffer_size`变量的当前值为16777216字节（即16MB），用于控制InnoDB存储引擎的日志缓冲区大小，影响事务提交性能和日志写入效率。

> [图] 第55页：有。该图展示了数据库事务处理中LSN（日志序列号）在不同阶段（如数据页、重做日志、检查点）的变化过程，体现了redo日志写入、刷盘和checkpoint机制的时间线关系。

> [图] 第55页：有技术知识点。该图描述了MySQL在实例崩溃或宕机后，通过InnoDB存储引擎的Redo Log（redo.file）进行数据恢复的过程，体现了数据库的故障恢复机制。

> [图] 第56页：有技术知识点。该图展示了MySQL InnoDB存储引擎中redo日志文件组的循环写入结构，多个ib_logfile文件按顺序循环使用，用于实现事务的持久性和崩溃恢复。

> [图] 第56页：有技术知识点。该图展示了MySQL中InnoDB存储引擎的日志文件配置，具体包括日志文件组的数量（innodb_log_files_in_group为2）和每个日志文件的大小（innodb_log_file_size为48MB）。

> [图] 第56页：这张图展示了MySQL的InnoDB存储引擎日志文件（ib_logfile0和ib_logfile1）的文件大小和权限信息，提示日志文件已提前开辟空间但实际数据量较小，属于数据库运维中的常见现象。

> [图] 第57页：有技术知识点。该图展示了MySQL InnoDB引擎中SQL执行、缓冲池（Buffer Pool）管理、脏页刷盘、redo日志写入及持久化的过程，涉及数据库的内存管理与事务日志机制。

> [图] 第59页：有技术知识点。该图展示了MySQL的分层架构及redo log和binlog的日志生成机制：Server层负责生成binlog日志，存储在data目录下；而InnoDB存储引擎层生成redo log日志，用于事务持久化和崩溃恢复。

> [图] 第60页：有技术知识点。该图描述了数据库事务提交过程中的一阶段提交和二阶段提交流程，涉及写入redo日志（prepare阶段）和binlog，以及最终提交事务的步骤，并标注了可能在中间阶段发生崩溃的情况。

> [图] 第61页：有。该图描述了数据库事务恢复过程中基于redo log和binlog的一致性检查流程：若redo log中有完整的committed数据则提交事务；若有完整的prepare数据但无完整binlog数据则回滚事务；若有完整binlog数据则提交事务。

> [图] 第62页：是的，这张图有技术知识点。它展示了MySQL二进制日志中事务ID（Xid）相关事件的类继承关系，具体描述了`Xid_log_event`如何分别派生出用于写入二进制日志的`binary_log::Xid_event`和用于应用日志的`Xid_apply_log_event`，体现了MySQL日志事件处理机制中的面向对象设计。

> [图] 第63页：有技术知识点。该图展示了数据库中 redo log 的写入流程：内存中的 redo log buffer 通过 fsync 操作同步到磁盘上的 redo log file，用于保证数据的持久性和事务的可靠性。

> [图] 第64页：有技术知识点。该图展示了Linux操作系统中文件系统的层次结构，包括从应用程序到本地磁盘设备或网络存储的完整数据访问路径，涉及用户空间、内核空间、系统调用、虚拟文件系统（VFS）、具体文件系统（如Ext、XFS、NFS）以及缓冲区和缓存机制。

> [图] 第65页：有技术知识点。该图展示了InnoDB存储引擎中缓冲池、重做日志缓存、文件系统缓存与磁盘之间的数据写入流程，以及后台线程每1秒执行一次fsync操作将redo日志刷新到磁盘的机制。

> [图] 第65页：有技术知识点。该图描述了MySQL InnoDB存储引擎中事务日志（redo log）的写入流程，展示了不同`innodb_flush_log_at_trx_commit`参数值对日志缓冲区、文件系统缓冲区和物理日志文件（ib_logfile）之间数据同步的影响。

> [图] 第65页：有技术知识点。该图显示了MySQL中`innodb_flush_log_at_trx_commit`变量的值为1，表示InnoDB存储引擎在事务提交时会立即将日志写入磁盘，确保数据的高持久性但可能影响性能。

> [图] 第66页：有技术知识点。该图展示了redo log buffer的结构，每个日志块由头部（header）、主体（body）和尾部（trailer）三部分组成，用于数据库事务日志的缓冲和写入管理。

> [图] 第66页：有技术知识点。该图展示了数据库日志缓冲区（Log Buffer）的结构，说明了日志块（Log Block）由头部、主体和尾部组成，并通过全局变量 `Buf_Free` 指向当前可写位置，用于管理 Redo 日志的写入与空间分配。

> [图] 第67页：有技术知识点。该图展示了Redo Log Buffer的结构，包括已写入硬盘、未写入硬盘和空闲区域，并用指针buf_next_to_write和buf_free标识当前写入位置和可用空间。

> [图] 第68页：有技术知识点。该图展示了MySQL InnoDB存储引擎中Redo Log Buffer的结构，包括多个日志块（Block）的组成，每个块包含日志头（log block header）、日志体（log block body）和日志尾（log block trailer），并说明了事务和Mini-Transaction（MTR）的redo log记录如何在缓冲区中组织，以及全局变量buf_free指向当前可用空间的位置。

> [图] 第69页：有技术知识点。该图展示了redo log block的结构，包括12字节的log block header、496字节的log block body和4字节的log block trailer，总共512字节。

> [图] 第69页：有技术知识点。该图展示了事务中SQL语句与MTR（Mini-Transaction Record）以及redo log记录之间的对应关系，说明一个事务包含多个SQL语句，每个SQL语句可能生成多个MTR，而每个MTR会对应一条或多条redo log记录，用于数据库的持久化和恢复机制。

> [图] 第70页：有技术知识点。该图展示了MySQL InnoDB存储引擎中redo log buffer的结构和工作原理，包括其默认大小为16MB、由多个redo log block组成，每个block包含Header、Body和Trailer部分，事务持续生成redo log数据并写入buffer，当buffer使用达到一半或事务提交时触发持久化到SSD。

> [图] 第71页：有技术知识点。该图展示了数据库重做日志缓冲区（Redo Log Buffer）的结构，包括日志块头、日志块体和日志块尾的布局，并通过LSN（日志序列号）计算说明了日志记录的顺序与位置关系。

> [图] 第72页：有技术知识点。该图显示了数据库日志系统的状态信息，包括日志序列号、日志缓冲区状态、脏页刷新情况以及检查点位置等，用于监控数据库的事务日志和恢复机制。

> [图] 第73页：这张图展示了两本名为《面渣逆袭》的Java学习资料封面，分别是“Java基础篇”和“JVM篇”，属于技术类学习书籍，内容涉及Java编程基础和JVM（Java虚拟机）相关知识点。  
**一句话描述：** 该图展示的是关于Java基础和JVM的纸质学习资料封面，具有明确的技术学习价值。

> [图] 第74页：有。该图展示了环形缓冲区（Ring Buffer）的工作原理，通过write pos和checkpoint指针的相对位置表示数据写入与已用空间的状态，当write pos追上checkpoint时，表示缓冲区已满，无可用空间。

> [图] 第74页：有技术知识点。该图展示了日志文件（logfile）的写入机制，其中“checkpoint”表示已持久化的位置，“write pos”表示当前写入位置，两者之间的区域为可写入的日志空间，体现了日志系统中写入与检查点管理的基本原理。

> [图] 第76页：有技术知识点。该图展示了数据库查询处理流程，包括连接器、缓存机制、分析器、优化器、执行器和存储引擎等组件的协作过程，体现了SQL查询的执行路径与缓存命中逻辑。

> [图] 第76页：有技术知识点。该图显示了MySQL中`long_query_time`变量的值为10秒，表示执行时间超过10秒的查询将被记录到慢查询日志中，用于性能监控和优化。

> [图] 第77页：有技术知识点。该图展示了MySQL数据库的进程列表（`show processlist`），显示了当前连接的线程状态、执行时间、命令类型等信息，可用于监控数据库性能和排查连接问题。

> [图] 第78页：有技术知识点。这是一段Java应用的日志输出，包含时间戳、线程信息、执行耗时和异常提示，反映了系统中SSE（Server-Sent Events）连接超时中断以及接口调用性能监控的情况。

> [图] 第79页：这张图没有技术知识点，内容为个人求职经历分享和offer选择困惑，属于职场经验交流类信息。

> [图] 第80页：有技术知识点。这张图总结了SQL优化的多个关键方面，包括避免不必要的列、分页优化、索引优化、JOIN优化、排序优化和UNION优化，涵盖了实际开发中提升数据库查询性能的重要策略。

> [图] 第81页：有技术知识点。该图展示了数据库中聚集索引（主键建立的索引）与非聚集索引（非主键建立的索引）的结构差异，说明了两种索引在数据存储和查询时的不同方式。

> [图] 第86页：有技术知识点。该图展示了MySQL中一个包含子查询的SQL语句的执行计划（EXPLAIN输出），说明了主查询和依赖子查询如何通过索引高效地访问数据，涉及索引使用、表连接方式等数据库优化概念。

> [图] 第87页：有技术知识点。该图展示了MySQL中`EXPLAIN`命令的执行计划输出，用于分析SQL查询的性能，涉及索引使用、表连接方式和查询优化等数据库核心技术。

> [图] 第88页：这张图没有直接展示技术知识点，主要为聊天记录内容，涉及学习、面试准备和对个人技术能力的反思。

> [图] 第89页：有技术知识点。该图展示了数据库表 t1 与 t2 通过索引 a 和 id 进行连接查询的过程，最终生成结果集，体现了数据库中索引和关联查询的原理。

> [图] 第90页：有技术知识点。该图描述了数据库中表连接（JOIN）操作的执行过程，展示了如何通过 join_buffer 将两个表 t1 和 t2 的数据进行匹配并生成结果集。

> [图] 第90页：有技术知识点。该图显示了MySQL中`join_buffer_size`变量的当前值为262144字节（即256KB），用于控制连接操作时缓冲区的大小，影响查询性能。

> [图] 第91页：有技术知识点。该图讨论了数据库连接（JOIN）操作中驱动表选择对性能的影响，重点在于小表做驱动表时效率更高，因为可以减少IO操作和内存占用，尽管理论时间复杂度公式相同，但实际执行效率存在显著差异。

> [图] 第92页：有技术知识点。该图展示了MySQL中两个表（departments和employees）的结构，包括字段名、数据类型、是否允许为空、主键信息等，体现了数据库表设计的基本概念。

> [图] 第93页：有技术知识点。该图展示了MySQL中不同JOIN操作（LEFT JOIN、RIGHT JOIN、INNER JOIN）的执行计划（EXPLAIN输出），揭示了查询优化器如何处理表连接，包括访问类型、索引使用情况和行数估计等关键性能信息。

> [图] 第94页：有技术知识点。  
该图内容涉及数据库SQL查询优化规范，强调多表JOIN时的限制、字段数据类型一致性、索引必要性及性能注意事项。

> [图] 第95页：有技术知识点。该图展示了MySQL中与排序相关的三个系统变量的值：`sort_buffer_size`、`max_length_for_sort_data` 和 `max_sort_length`，用于控制排序操作的内存使用和数据长度限制。

> [图] 第96页：有技术知识点。该图展示了多表连接（tbl1、tbl2、tbl3）后将结果写入临时表，再通过 filesort 进行排序，最终输出有序结果的过程，体现了数据库查询中排序与临时表的使用机制。

> [图] 第97页：有技术知识点。该图展示了MySQL数据库中创建表、插入数据以及使用EXPLAIN分析排序查询的SQL语句，重点说明了使用有索引字段排序不会产生filesort，而使用无索引字段排序会产生filesort，涉及数据库索引和查询优化的知识。

> [图] 第98页：有技术知识点。该图展示了MySQL中`EXPLAIN`命令的执行计划，对比了按主键（id）和非索引列（age）排序时的查询性能差异，说明了使用索引可避免文件排序（Using filesort），提升查询效率。

> [图] 第99页：有技术知识点。该图展示了多表连接查询中有序索引扫描和无连接缓冲的执行过程，说明了在特定条件下如何实现有序输出。

> [图] 第99页：这张图没有明显的技术知识点，主要为聊天记录截图，内容涉及阿里云意向书的沟通，无具体技术细节。

> [图] 第100页：有技术知识点。该图展示了数据库查询中基于索引的排序过程：通过 city 索引定位数据，从主键索引获取记录，将结果存入 sort_buffer 并按 name 字段排序，最终返回有序结果集。

> [图] 第101页：有技术知识点。该图展示了数据库查询中基于索引的排序过程，具体描述了通过 city 索引查找数据后，利用 sort_buffer 对 name 字段进行排序，并最终获取结果集的流程。

> [图] 第102页：有技术知识点。该图展示了MySQL中`Sort_merge_passes`状态变量的值为0，表示排序操作未触发合并排序阶段，通常意味着查询的排序可以在内存中完成，无需磁盘临时文件，是数据库性能优化的一个参考指标。

> [图] 第103页：有技术知识点。该图描述了数据库排序过程中基于外部排序的多路归并排序流程，包括从存储引擎读取数据、使用排序缓冲区生成有序数据块、写入临时文件，最后通过多轮归并排序生成最终有序结果的过程。

> [图] 第104页：这张图没有直接展示技术知识点，但其中提到了“大模型微调”和“AIGC应用开发”，涉及的技术方向是：**基于大模型的微调技术在AIGC（人工智能生成内容）业务中的应用开发**。

> [图] 第109页：有技术知识点。这张图展示了MySQL中`EXPLAIN`命令的执行计划输出字段及其含义，用于分析SQL查询的执行效率。

> [图] 第111页：有技术知识点。该图展示了一段数据库查询执行计划的JSON格式输出，包含查询成本、排序操作、表访问方式及使用的列等信息，用于分析SQL查询性能。

> [图] 第114页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的输出结果，说明查询通过主键索引快速定位到单行数据，体现了索引优化和查询性能分析的技术内容。

> [图] 第115页：有技术知识点。该图展示了数据库中索引与表之间关系，表示索引通过指向表中的数据行来加快查询速度。

> [图] 第116页：是的，这张图有技术知识点。它展示了一个B+树索引结构，用于数据库中高效地存储和检索数据，其中根节点、中间节点和叶节点分别表示索引的层级结构，通过CustId进行数据查找和定位。

> [图] 第117页：有技术知识点。该命令用于在MySQL中创建一个名为`demo_performance`的数据库（如果不存在），并创建一个名为`test_performance`的表，包含id、name、age和creation_date字段，常用于数据库性能测试或初始化环境。

> [图] 第117页：有技术知识点。该图展示了数据库查询在有无索引情况下的性能对比，说明了索引能显著提升查询效率，减少扫描行数和执行时间。

> [图] 第118页：有技术知识点。该图展示了一段MySQL存储过程代码，用于向名为`test_performance`的表中插入10万条测试数据，涉及变量声明、循环、随机数生成和日期计算等数据库操作技术。

> [图] 第118页：有技术知识点。该图展示了在MySQL中为表创建索引并使用EXPLAIN和profiling分析查询性能的过程，涉及数据库索引优化和执行计划分析。

> [图] 第119页：有技术知识点。该图展示了数据库索引的分类方式，从功能、数据结构和存储位置三个维度对索引类型进行了归纳，包括主键索引、唯一索引、B+树索引、聚簇索引等常见概念。

> [图] 第119页：这张图没有技术知识点，内容为暑期实习选择的个人记录，涉及岗位信息和地点，属于求职相关的信息分享。

> [图] 第120页：有技术知识点。  
如果表中未定义主键，MySQL 会自动选择第一个所有列非 NULL 的唯一索引作为聚簇索引，若不存在则创建名为 GEN_CLUST_INDEX 的隐藏聚簇索引。

> [图] 第120页：有技术知识点。该图展示了InnoDB存储引擎中B+树索引的数据结构，说明了主键索引如何组织数据页和索引页，以及数据的存储与查找方式。

> [图] 第121页：有技术知识点。该图展示了MySQL中`users`表的索引信息，显示其主键索引基于`id`列，使用BTREE索引类型，且为唯一索引。

> [图] 第122页：有技术知识点。该图展示了MySQL中唯一约束（UNIQUE）对NULL值的处理方式：允许在具有唯一约束的列中插入多个NULL值，但主键列不能为NULL。

> [图] 第123页：有技术知识点。这是一段MySQL的建表语句，展示了如何创建名为`users`、`users1`和`users3`的数据库表，包含主键、唯一索引、字符集和排序规则等数据库设计要素。

> [图] 第126页：有技术知识点。该图展示了MySQL中全文索引的创建、使用和优化过程，包括创建包含全文索引的表、插入数据、执行全文搜索查询以及使用ngram解析器进行中文全文检索的示例。

> [图] 第127页：有技术知识点。该图展示了一篇关于在SpringBoot中使用RestHighLevelClient实现Elasticsearch查询的技术文章，内容涉及ES与SpringBoot的整合、客户端构造及依赖引入等。

> [图] 第132页：有技术知识点。该图展示了一段SQL脚本，用于对比在MySQL数据库中，对`users`表的`age`字段使用索引（WITH INDEX）和不使用索引（WITHOUT INDEX）时查询性能的差异，通过重复执行相同查询并记录时间戳来评估索引对查询效率的影响。

> [图] 第133页：有技术知识点。该图展示了在小数据集（约20条记录）场景下，MySQL查询执行计划的对比分析，揭示了全表扫描在某些情况下可能比使用索引更快，验证了索引在数据量较小时未必能提升性能甚至可能因额外开销而降低性能的技术结论。

> [图] 第136页：这张图没有直接展示技术知识点，主要内容是群聊中关于某人通过百度HR面试并获得口头offer的祝贺信息。因此，**无技术知识点**。

> [图] 第137页：有，这张图展示了B+树的结构和索引机制，用于数据库和文件系统中的高效数据检索。

> [图] 第138页：有技术知识点。该图展示了MySQL InnoDB存储引擎中B+树的结构，用于说明索引的组织方式和数据检索路径。

> [图] 第138页：这张图展示了一棵二叉树结构，节点包含编号，体现了树形数据结构的基本组织方式。

> [图] 第139页：有技术知识点。该图展示了计算机系统中CPU、内存和磁盘之间的数据交互关系，体现了计算机存储层次结构的基本原理。

> [图] 第139页：这张图展示了二叉树的结构，具有n层深度，体现了树形数据结构的基本特征。

> [图] 第140页：有技术知识点。这是一棵B树的结构示意图，展示了B树的节点组织方式和键值分布，常用于数据库和文件系统的索引结构。

> [图] 第140页：这张图展示了B树（或B+树）的数据结构，用于数据库和文件系统中的高效数据存储与检索。

> [图] 第141页：有技术知识点。该图展示了一种B+树结构在磁盘上的存储布局，用于数据库或文件系统中的索引组织，其中键值用于排序和查找，指针指向子节点，数据存储实际记录的地址信息。

> [图] 第141页：有技术知识点。这是一棵简化的B-树结构图，展示了B-树的节点组织方式和数据存储逻辑，常用于数据库和文件系统中的索引结构。

> [图] 第142页：有技术知识点。这是一张简化B+树的结构图，展示了B+树的层级结构和数据存储方式，常用于数据库和文件系统中的索引结构。

> [图] 第144页：有技术知识点。该图展示了B+树的结构和存储计算，说明了在特定参数下（如键值+指针为14字节，页大小16KB），非叶子节点可容纳1170个单元，树深度为2时最多可存储约2190万条记录，且查询只需3次I/O操作。

> [图] 第144页：有技术知识点。该图展示了InnoDB存储引擎中FIL_ADDR_SIZE定义为6字节，用于表示页地址大小，并计算FLST_BASE_NODE_SIZE的结构布局，包含前后页节点指针和偏移量，是B+树索引页管理的基础数据结构。

> [图] 第145页：有技术知识点。该图展示了一个B+树结构，根节点包含1170个指针，指向多个叶节点，每个叶节点可存储16条1k大小的记录，每页16k，体现了数据库索引的层级存储机制。

> [图] 第146页：有技术知识点。  
对话中提到的“CI/CD”是持续集成/持续部署（Continuous Integration/Continuous Deployment）的缩写，属于软件开发中的自动化流程技术，用于提高代码交付效率和质量。

> [图] 第147页：有技术知识点。该图展示了一个退化的哈希表结构，所有元素都链在同一个桶中，形成一个单向链表，这种情况通常称为“链表退化”，可能导致哈希表性能下降至O(n)。

> [图] 第148页：是的，这张图有技术知识点。它展示了一棵B树（或B+树）的数据结构，用于数据库和文件系统中的高效数据检索，其中节点包含键值和指向子节点的指针，磁盘块表示存储单元。

> [图] 第148页：是的，这张图有技术知识点。它展示了一棵加权二叉树，常用于哈夫曼编码（Huffman Coding）中，节点上的数字表示权重，路径上的数字表示编码位，用于数据压缩。

> [图] 第149页：这张图展示了B树（B-Tree）的数据结构，用于数据库和文件系统中的高效数据存储与检索。

> [图] 第150页：有，这张图展示了跳表（Skip List）的数据结构，通过一级索引加速对原始链表的查找操作。

> [图] 第151页：是的，这张图展示了B树（B-Tree）的数据结构。它是一种自平衡的树结构，常用于数据库和文件系统中，以实现高效的查找、插入和删除操作。

> [图] 第151页：是的，这张图展示了B树（B-Tree）的数据结构，用于高效地存储和检索数据。

> [图] 第152页：是的，这张图展示了B树（B-Tree）的数据结构。它是一种自平衡的树结构，常用于数据库和文件系统中，以实现高效的数据存储和检索。

> [图] 第152页：这张图展示了多个垂直条形和黑色点的分布，可能表示某种数据可视化或信号强度图，具有技术知识点。  
用一句话描述：该图可能表示信号强度或数据分布的可视化图表，通过竖条高度和点的位置反映数值变化。

> [图] 第154页：有。这是一张B+树结构图，展示了根节点、子节点和叶子节点的组织方式，常用于数据库和文件系统的索引结构。

> [图] 第154页：有技术知识点。该图展示了哈希表的实现原理，包括键值通过散列函数映射到哈希表中的位置，并使用链地址法处理冲突，最终指向行记录数据。

> [图] 第155页：有技术知识点。该图展示了MySQL中InnoDB存储引擎的自适应哈希索引（adaptive hash index）功能当前处于开启状态（ON），用于提升热点数据的查询性能。

> [图] 第156页：有技术知识点。该图展示了一种B树（或B+树）结构，用于数据库索引，其中页1为根节点，包含指向页10、页11和页12的指针，每个叶子节点存储实际数据记录，支持高效的数据查找。

> [图] 第157页：有技术知识点。该图展示了一个B+树结构，用于数据库索引，其中页1为根节点，包含指向页10、页11和页12的指针，每个叶子节点存储具体的age和id数据，体现了B+树的层次结构和有序性。

> [图] 第159页：有技术知识点。该图展示了数据库中聚集索引（主键建立的索引）与非聚集索引（非主键建立的索引）的结构差异，说明了数据存储和查询方式的不同：聚集索引的叶子节点包含整行数据，而非聚集索引的叶子节点包含索引列数据和主键值，需回表查询获取完整数据。

> [图] 第161页：有技术知识点。该图展示了数据库中二级索引和主键索引的检索过程，通过B+树结构说明了如何根据键值进行数据查找，体现了索引在提高查询效率中的作用。

> [图] 第162页：有技术知识点。该图展示了MySQL中使用索引a进行查询时，通过read_rnd_buffer缓存和排序操作，最终根据主键id获取结果集的过程，体现了“文件排序”（filesort）的执行流程。

> [图] 第162页：有技术知识点。该图展示了MySQL优化器开关（optimizer_switch）的配置，其中`mrr=on`和`mrr_cost_based=on`被高亮，表示启用了多范围读取（Multi-Range Read）优化功能，用于提升范围查询性能。

> [图] 第163页：有技术知识点。该图显示了MySQL中`read_rnd_buffer_size`变量的当前值为262144字节（即256KB），用于控制MySQL在执行排序操作后读取行数据时使用的缓冲区大小，影响查询性能。

> [图] 第165页：有技术知识点。该图展示了在MySQL中启用（MRR ON）和禁用（MRR OFF）“Multi-Range Read”优化器功能时，数据访问顺序的差异，体现了MRR对查询性能的影响。

> [图] 第165页：有技术知识点。该图解释了MySQL中MRR（Multi-Range Read）机制的工作原理，通过对比MRR开启和关闭时的访问模式，说明MRR如何优化查询性能：开启时会先收集并排序索引记录，再按主键顺序回表查询，从而实现更有序的访问；关闭时则直接按索引顺序访问，导致随机性更高的访问模式。

> [图] 第166页：有技术知识点。该图展示了MySQL中使用索引优化查询的场景，特别是通过`EXPLAIN`命令分析查询执行计划时，`Using index condition`和`Using MRR`（Multi-Range Read）的提示，说明了查询利用复合索引进行范围扫描并优化数据读取顺序的技术细节。

> [图] 第166页：有技术知识点。该图展示了开发者技术社区项目的开发经历，涉及Spring Boot、MyBatis-Plus、ElasticSearch、RabbitMQ、Docker等技术栈，并描述了通过缓存优化、AOP监控、并行加载等手段提升系统性能的具体实践。

> [图] 第167页：有技术知识点。该图展示了B+树索引结构在数据库中的应用，用于高效查询数据，其中节点存储键值并指向对应的数据行或子节点。

> [图] 第168页：有技术知识点。该图展示了数据库索引结构（如B+树）的组织方式，通过页号、列值和指针关系说明了数据的存储与查找机制。

> [图] 第169页：是的，这张图有技术知识点。它展示了一个基于B+树结构的非聚集索引组织方式，用于数据库中对表记录的高效查找，其中根节点和内部节点存储索引键和指向子节点的指针，叶节点包含实际数据记录的主键和行地址。

> [图] 第170页：有技术知识点。该图展示了数据库中二级索引的检索过程，通过B+树结构实现对数据的高效查找，其中包含键值、指针和数据的存储与关联关系。

> [图] 第170页：这张图没有直接展示技术知识点，主要内容为一位学生向导师咨询暑期实习选择的私信内容，涉及职业规划和公司选择，无装饰、Logo或二维码。

> [图] 第173页：有技术知识点。该图展示了数据库中基于 (name, age) 组合索引的查询结构，通过索引快速定位到对应的数据记录（如 ID:5、ID:8 等），体现了组合索引在加速多字段查询中的作用。

> [图] 第174页：有技术知识点。该图展示了MySQL中使用`EXPLAIN`命令分析两个查询语句的执行计划，对比了在不同列（a和b）上进行等值查询时索引的使用情况，体现了复合索引的最左前缀原则。

> [图] 第174页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的输出，说明查询使用了名为`abc_index`的索引进行高效查找，且通过复合索引对字段a和b进行了精确匹配，属于数据库性能优化中的索引使用分析。

> [图] 第175页：有技术知识点。该图展示了MySQL中使用`EXPLAIN`命令分析查询执行计划的结果，说明了查询如何利用索引（abc_index）进行范围扫描，并结合WHERE条件过滤数据。

> [图] 第175页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的输出，说明查询使用了索引 `abc_index`，预计扫描10行数据，并通过WHERE条件进一步过滤，涉及索引优化和查询性能分析。

> [图] 第176页：有技术知识点。该图展示了MySQL中使用`EXPLAIN`命令分析查询执行计划的结果，说明了查询如何利用索引（idx_abc）进行范围扫描，并使用索引条件优化查询性能。

> [图] 第177页：有技术知识点。该图展示了MySQL中`EXPLAIN`命令的执行结果，用于分析查询语句的执行计划，显示了查询未使用索引（key: NULL），而是进行了全表扫描（type: ALL），提示可能需要优化索引以提高查询性能。

> [图] 第178页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的结果，表明查询未使用索引（key: NULL），进行了全表扫描（type: ALL），提示应为字段C创建索引以优化性能。

> [图] 第179页：有技术知识点。该图展示了MySQL中使用复合索引进行查询优化的示例，通过EXPLAIN分析查询执行计划，说明了当查询条件包含前缀匹配（LIKE '%王二1'）时，尽管使用了复合索引，但索引的后半部分无法有效利用，导致需要额外的过滤操作。

> [图] 第179页：有技术知识点。该图展示了一条MySQL插入语句，向名为`test_table`的表中批量插入6条包含年龄和姓名的数据记录，涉及SQL语法和数据库操作。

> [图] 第180页：有技术知识点。该图展示了MySQL中使用复合索引进行查询优化的示例，通过EXPLAIN命令分析了查询执行计划，表明查询利用了`age_name`复合索引，提升了查询效率。

> [图] 第181页：有技术知识点。该图展示了数据库查询中“回表”和“索引下推”（Index Condition Pushdown）的概念，说明了在使用组合索引时如何通过索引下推优化查询性能，避免不必要的回表操作。

> [图] 第182页：有技术知识点。该图展示了数据库查询中未使用ICP（Index Condition Pushdown）时，通过复合索引进行查询的过程，包括根据条件筛选索引记录、回表获取完整数据等步骤。

> [图] 第183页：有技术知识点。该图展示了数据库查询中使用索引（如聚簇索引和非聚簇索引）进行数据检索的过程，具体演示了通过复合索引 `(name, age)` 进行查询条件筛选并回表获取完整数据的机制。

> [图] 第184页：有技术知识点。该图展示了如何在MySQL中通过`EXPLAIN`分析查询执行计划，并演示了索引条件下推（Index Condition Pushdown, ICP）功能的开启与关闭对查询优化的影响。

> [图] 第185页：有技术知识点。该图展示了MySQL查询执行计划（EXPLAIN）中索引条件下推（Index Condition Pushdown, ICP）的启用与禁用对查询性能的影响，具体表现为“Using index condition”和“Using where”的区别。

> [图] 第186页：有技术知识点。该图展示了MySQL中使用`EXPLAIN`命令分析查询执行计划的结果，说明了通过主键（PRIMARY）进行精确匹配时，查询优化器选择常量查找（const）方式，效率很高。

> [图] 第186页：这张图包含技术知识点，展示的是数据库查询执行计划（EXPLAIN输出），涉及索引扫描、过滤条件和执行成本分析。

> [图] 第187页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的输出结果，说明查询使用了索引 `idx_abc` 进行优化，并通过“Using index condition”提示表明使用了索引条件推送（Index Condition Pushdown）技术来提升查询性能。

> [图] 第188页：有技术知识点。该图展示了MySQL中使用EXPLAIN分析查询执行计划的结果，显示查询未使用索引（key为NULL），进行了全表扫描（type为ALL），并提示了WHERE条件过滤情况，可用于优化SQL查询性能。

> [图] 第188页：有技术知识点。该图展示了MySQL执行计划（EXPLAIN）的结果，说明查询使用了复合索引 `idx_abc` 进行高效检索，且查询条件中的字段顺序与索引一致，实现了最优的索引利用。

> [图] 第189页：这张图中没有明显的技术知识点，主要为求职者与导师之间的对话内容，涉及实习offer选择、公司技术栈（如Java、C++）、业务场景等信息，但未展示具体技术细节或代码。  
**一句话描述**：无明确技术知识点，内容为实习offer对比和职业发展咨询。

> [图] 第190页：有技术知识点。该图展示了MySQL中事务与锁机制的交互过程：用户A对表执行读操作并加读锁，用户B尝试修改表结构（ALTER TABLE）时因读锁而被阻塞，直到用户A提交事务后，用户B的ALTER操作才得以执行，体现了MySQL中元数据锁（MDL）对并发操作的影响。

> [图] 第190页：有技术知识点。该图展示了MySQL锁的相关概念，包括加锁机制（乐观锁、悲观锁）、锁粒度（表锁、页锁、行锁）、锁模式（记录锁、间隙锁、next-key锁、意向锁、插入意向锁）以及锁的兼容性（共享锁、排他锁）。

> [图] 第191页：有技术知识点。该图展示了数据库中共享锁（S lock）和排他锁（X lock）的兼容性矩阵：共享锁之间可以共存，但共享锁与排他锁、排他锁与排他锁之间不兼容。

> [图] 第192页：有技术知识点。该图描述了MySQL中表级锁的场景：Session 1执行LOCK TABLES锁定表后，Session 2的SELECT语句因表被锁定而处于等待状态。

> [图] 第193页：是的，这张图有技术知识点。它展示了数据库索引结构中的二级索引与主键索引（聚簇索引）的关系，以及它们如何通过主键值进行关联和数据查找。

> [图] 第197页：有技术知识点。该图展示了在MySQL中使用不同索引（主键和唯一索引）执行`SELECT ... FOR UPDATE`时的行锁（Record Lock）机制，说明了锁是如何根据索引类型作用于具体记录的。

> [图] 第198页：有技术知识点。该图展示了InnoDB在RR（可重复读）隔离级别下，通过Gap Lock（间隙锁）防止幻读的机制，具体说明了`SELECT * FROM a WHERE c <= 7 FOR UPDATE`语句如何加锁以阻止其他会话插入满足条件的新记录。

> [图] 第199页：有技术知识点。该图展示了MySQL数据库中事务锁等待的详细信息，包括事务ID、锁类型、等待时间及SQL语句，用于分析死锁或锁争用问题。

> [图] 第199页：这张图展示了区间划分与对应人员的映射关系，属于数据结构中“区间查询”或“区间分配”的技术知识点。

> [图] 第200页：有技术知识点。该图展示了数据库中的锁机制，具体说明了间隙锁（gap lock）、记录锁（record lock）和临键锁（next-key lock）的概念及其在索引范围内的应用。

> [图] 第201页：有技术知识点。该图展示了MySQL中事务并发控制的锁机制，具体说明了事务A通过范围查询对id=1到id=10之间的记录和间隙加锁后，事务B在尝试插入id=7的记录时被阻塞，直到事务A提交释放锁，体现了InnoDB存储引擎的行级锁和间隙锁（Gap Lock）行为。

> [图] 第202页：有技术知识点。该图展示了数据库事务中的锁机制，具体是“意向锁”与“表锁”的关系：事务A对表加了意向锁，事务B试图加表锁时被阻塞，体现了多事务并发访问时的锁冲突与等待机制。

> [图] 第203页：有技术知识点。该图展示了MySQL中事务并发控制的锁机制：用户A对表的部分行加IX锁后，用户B尝试对整表加写锁失败，待用户A提交事务释放IX锁后，用户B才成功加写锁，体现了MySQL的行级锁与表级锁的互斥关系。

> [图] 第204页：有技术知识点。该图展示了数据库中的“悲观锁”概念，即用户1在事务1中对数据加锁后，其他用户（事务2、3、4）需等待锁释放才能访问，体现了并发控制中防止数据冲突的机制。

> [图] 第204页：这张图没有直接的技术知识点，主要为聊天记录内容，涉及面试准备和“八股文”复习策略的讨论，其中提到MQ（消息队列）相关面试题的准备情况。  
一句话描述：聊天记录中讨论了通过结合多个“八股文”知识点准备MQ面试的经历。

> [图] 第205页：有技术知识点。该图展示了乐观锁的工作原理：多个线程并发读取共享数据，提交时通过版本号检测是否发生冲突，若版本未变则提交成功，否则需重新读取并重试。

> [图] 第205页：有技术知识点。该图展示了悲观锁的工作机制：当线程1尝试对共享数据加排他锁时，线程2被阻塞；线程1提交事务并释放锁后，线程2成功获取锁并执行更新操作。

> [图] 第206页：有技术知识点。该图展示了数据库中乐观锁机制的实现过程，通过版本号（version）控制并发更新，避免脏写问题。

> [图] 第206页：有技术知识点。该图展示了数据库事务中的悲观锁机制，当事务A对id=2的数据加锁后，事务B无法再对该数据进行加锁操作，体现了并发控制中锁的排他性。

> [图] 第209页：是的，这张图有技术知识点。它展示了数据库中两个事务（SESSION 1 和 SESSION 2）因相互等待对方释放锁而导致死锁（Deadlock）的典型场景。

> [图] 第210页：有技术知识点。该图展示了MySQL中事务处理和行锁冲突的场景，当两个事务试图同时更新不同行但存在锁等待时，可能导致锁等待超时错误（ERROR 1205）。

> [图] 第211页：有技术知识点。该图展示了MySQL数据库中事务锁等待的详细信息，包括事务ID、锁类型、等待时间以及正在执行的SQL语句，用于分析和解决数据库死锁或锁等待问题。

> [图] 第211页：有技术知识点。该图展示了数据库事务的ACID特性，即原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）和持久性（Durability），是数据库系统设计中的核心概念。

> [图] 第213页：有技术知识点。该图展示了MySQL中事务隔离级别为“READ UNCOMMITTED”时，多个会话对同一数据进行修改和查询的场景，演示了脏读（Dirty Read）现象：会话C读取了会话B未提交的更新数据。

> [图] 第214页：有技术知识点。该图展示了MySQL中事务隔离级别为“READ UNCOMMITTED”时，多个会话对同一数据进行更新和查询的操作过程，体现了脏读（Dirty Read）现象：会话C读取了会话B未提交的事务数据，即使该事务最终被回滚，读取的结果仍受影响。

> [图] 第215页：有技术知识点。该图展示了MySQL中事务隔离级别为“READ COMMITTED”时，多个会话对同一数据进行查询和更新操作的行为，体现了事务的隔离性和一致性特性。

> [图] 第216页：有技术知识点。该图展示了MySQL中事务隔离级别为“读已提交”（READ COMMITTED）时，多个会话对同一数据进行查询和更新的操作过程，体现了事务的隔离性和一致性特性。

> [图] 第217页：有技术知识点。该图展示了MySQL中事务隔离级别“可重复读”（REPEATABLE READ）的特性，通过会话B和会话C的交互演示了在可重复读隔离级别下，一个事务内多次查询同一数据结果保持一致，即使其他事务已提交更新，体现了可重复读防止“不可重复读”问题的机制。

> [图] 第217页：有技术知识点。该图展示了MySQL中事务隔离级别为“读已提交”（READ COMMITTED）时，多个会话对同一数据进行查询和更新的并发行为，体现了事务的隔离性特性。

> [图] 第218页：有技术知识点。该图展示了MySQL中事务隔离级别“可重复读”（REPEATABLE READ）的特性，通过会话B和会话C的操作演示了在可重复读隔离级别下，一个事务内多次查询同一数据结果一致，即使其他事务已提交更新，体现了可重复读防止“不可重复读”问题的技术原理。

> [图] 第219页：有技术知识点。该图展示了MySQL中可重复读（Repeatable Read）隔离级别下的事务行为，说明了在该隔离级别下，会话B在事务中两次查询同一条件的数据，第一次查到2条记录，第二次查到3条记录，体现了“幻读”现象的规避与数据一致性保证。

> [图] 第220页：有技术知识点。该图展示了MySQL中事务隔离级别为“可重复读”（REPEATABLE READ）时，会话B在事务中查询数据、会话C插入新数据并提交后，会话B仍能成功更新新插入的数据，并在后续查询中看到新增记录的现象，体现了MySQL在可重复读隔离级别下对幻读的处理机制。

> [图] 第221页：有技术知识点。该图展示了MySQL中事务隔离级别设置为SERIALIZABLE时，多个会话并发操作数据表的场景，涉及事务的开启、查询、插入和提交，用于说明可串行化隔离级别的行为特性。

> [图] 第222页：有技术知识点。该图描述了数据库系统中“持久性（Durability）”的实现原理：通过事务日志（Transaction Log）将状态变更记录在非易失性存储中，即使系统崩溃后重启，也能利用日志重建并恢复到一致状态。

> [图] 第224页：有技术知识点。该图展示了数据库事务的ACID特性及其对应的实现机制：原子性（A）由undo log保证，一致性（C）由其他三个特性共同保证，隔离性（I）由MVCC保证，持久性（D）由redo log保证。

> [图] 第225页：有技术知识点。该图描述了数据库中SQL执行时，数据页从磁盘加载到Buffer Pool，修改前写入undo log，最终写入磁盘的流程。

> [图] 第226页：有技术知识点。该图展示了MySQL InnoDB引擎中数据写入流程，包括SQL执行、缓冲池（Buffer Pool）管理、脏页刷新、redo日志记录与刷盘等核心机制。

> [图] 第227页：有技术知识点。该图描述了InnoDB存储引擎中double write机制的工作流程，包括脏页从缓冲池写入double write buffer、顺序写入共享表空间、再离散写入数据文件的过程，以及在崩溃恢复时如何利用double write和redo log保证数据一致性。

> [图] 第227页：有技术知识点。该图描述了MySQL中二阶段提交（Two-Phase Commit）的详细流程，包括prepare、flush、sync和commit四个子阶段，涉及事务状态修改、Redo日志刷盘、binlog写入与同步、锁机制及线程协作等核心数据库事务处理技术。

> [图] 第228页：有技术知识点。该图描述了MySQL中二阶段提交（Two-Phase Commit）的完整流程，包括prepare、flush、sync和commit四个子阶段，涉及事务状态修改、Redo日志刷盘、binlog写入与同步、锁机制以及leader和follower线程的协作，是理解MySQL事务一致性与高可用性的重要内容。

> [图] 第229页：是的，这张图有技术知识点。它展示了日志文件（log file）在循环写入过程中的状态变化，涉及写指针（write ps）、头尾指针（head/tail）和检查点（checkpoint）的移动，常用于数据库或系统日志的环形缓冲区管理机制。

> [图] 第229页：有技术知识点。该图展示了数据库中临键锁（Next-Key Lock）的原理，通过标记不同区间的记录状态（阻塞或不阻塞），说明了在特定索引范围内事务操作时的锁机制行为。

> [图] 第230页：有技术知识点。该图展示了MySQL InnoDB存储引擎中MVCC（多版本并发控制）的实现原理，通过Undo Log维护数据历史版本，并结合ReadView判断可见性，实现事务隔离。

> [图] 第231页：有技术知识点。该图展示了MySQL中自动提交（autocommit）状态的查询结果，显示当前会话和全局变量的autocommit设置均为开启状态（值为1或ON），说明每条SQL语句执行后会自动提交事务。

> [图] 第233页：有技术知识点。该图展示了数据库事务中的“脏读”问题，即一个事务读取了另一个未提交事务的修改数据，可能导致数据不一致。

> [图] 第233页：有技术知识点。该图展示了数据库事务隔离级别的四种模式（READ UNCOMMITTED、READ COMMITTED、REPEATABLE READ、SERIALIZABLE）对三种并发问题（脏读、不可重复读、幻读）的防护能力，用勾叉表示是否允许发生。

> [图] 第234页：是的，这张图有技术知识点。它展示了数据库事务隔离级别中的“可重复读”（Repeatable Read）隔离级别下可能出现的“幻读”（Phantom Read）问题，说明在事务2中多次查询同一数据时，虽然读到的数据一致，但在更新时因数据已变更而无法成功，体现了并发控制中的典型问题。

> [图] 第234页：有技术知识点。该图展示了数据库事务中的“不可重复读”问题：在事务2中多次读取同一数据时，由于事务1的更新和提交，导致读取结果不一致，体现了事务隔离级别中的读一致性问题。

> [图] 第235页：有技术知识点。该图展示了数据库事务的串行化执行顺序，说明了两个事务在并发执行时通过串行化保证数据一致性的过程。

> [图] 第236页：有技术知识点。该图展示了MySQL事务处理中的并发问题，包括未提交的修改、快照读和当前读导致的锁等待超时，体现了事务隔离级别和锁机制的实际应用。

> [图] 第238页：是的，这张图有技术知识点。它描述了数据库事务中的并发控制问题，具体展示了两个事务 T1 和 T2 对同一行数据进行更新时可能发生的冲突，其中 T1 的更新操作在 T2 尝试更新时被阻止（标记为红色叉号），体现了数据库的锁机制或并发控制策略。

> [图] 第238页：有技术知识点。该图描述了数据库事务中并发控制的问题，具体展示了事务T1对某行数据进行UPDATE并提交后，事务T2的SELECT操作因数据已变更而无法读取到预期结果，体现了事务隔离级别中的“不可重复读”或“幻读”现象。

> [图] 第239页：有技术知识点。该图描述了数据库中两个事务 T1 和 T2 对同一行数据进行操作时可能发生的并发问题，其中 T1 执行 SELECT 操作，T2 执行 UPDATE 操作，可能导致数据不一致或丢失更新的问题。

> [图] 第241页：有技术知识点。该图展示了数据库事务中的“不可重复读”问题，即事务A在两次查询之间，事务B插入了新数据，导致事务A的查询结果不一致。

> [图] 第242页：有技术知识点。该图展示了MySQL中事务的并发操作，体现了事务的隔离性特性，即事务A在事务B执行插入和更新操作后仍能读取到最新的数据，说明数据库支持读已提交（Read Committed）或更高隔离级别下的数据一致性。

> [图] 第243页：有技术知识点。该图展示了MySQL中事务与锁机制的应用，具体演示了在事务A中使用`SELECT ... FOR UPDATE`对数据加锁后，事务B尝试插入数据时因锁等待超时而被阻塞的现象，体现了数据库并发控制中的行级锁和事务隔离机制。

> [图] 第243页：有技术知识点。该图展示了数据库索引中记录（Record）、间隙（Gap）和临键（Next-Key）的概念，用于说明MySQL InnoDB存储引擎中的行锁机制和事务隔离级别下的锁范围划分。

> [图] 第244页：有技术知识点。该图展示了MySQL中快照读（Snapshot Read）的特性，即在事务中多次查询同一数据时，即使其他事务插入了新数据，当前事务仍能看到一致的数据视图，体现了数据库的隔离级别和一致性读机制。

> [图] 第245页：有技术知识点。该图描述了数据库中Read View的四个关键组成部分：创建事务ID、活跃事务ID列表、最小事务ID和最大事务ID，用于实现多版本并发控制（MVCC）中的一致性读。

> [图] 第246页：有技术知识点。该图展示了MySQL中执行UPDATE语句时，MySQL Server与InnoDB存储引擎之间的交互流程，包括读取、加锁、更新行等步骤。

> [图] 第247页：有技术知识点。该图展示了数据库中基于 undo 日志的版本链结构，用于实现多版本并发控制（MVCC），通过回滚指针连接不同事务版本的数据记录，支持事务的隔离性和一致性。

> [图] 第248页：有技术知识点。该图展示了数据库中基于版本链的多版本并发控制（MVCC）机制，通过ReadView和Undo日志实现事务的可见性判断。

> [图] 第249页：有技术知识点。该图展示了数据库中事务更新操作时，数据行的修改过程以及 undo log 的记录机制，体现了 MVCC（多版本并发控制）和回滚指针的使用。

> [图] 第250页：有技术知识点。该图展示了数据库事务中事务ID的划分，包括已提交、未提交和未开始的事务，以及当前事务的范围（min_trx_id 到 max_trx_id）和活跃事务（m_ids）的管理机制。

> [图] 第251页：有技术知识点。该图展示了MySQL InnoDB存储引擎中行记录的隐藏字段结构，包括事务ID（DB_TRX_ID）和回滚指针（DB_ROLL_PTR），以及指向insert undo日志的链接，用于实现多版本并发控制（MVCC）。

> [图] 第251页：有技术知识点。该图展示了MySQL InnoDB存储引擎中表的隐藏列，包括事务ID（DB_TRX_ID）和undo日志记录指针（DB_ROLL_PTR），用于实现多版本并发控制（MVCC）。

> [图] 第251页：有技术知识点。该图展示了两个事务（TRX 100 和 TRX 200）对同一数据行进行并发更新的操作序列，体现了数据库事务的并发控制与潜在的脏写问题。

> [图] 第252页：是的，这张图有技术知识点。它展示了数据库中MVCC（多版本并发控制）机制下的undo日志和版本链结构，通过DB_TRX_ID和DB_ROLL_PTR字段形成一条记录的版本历史链，用于实现事务的隔离性和一致性。

> [图] 第253页：有技术知识点。该图展示了 MySQL 中事务隔离级别相关的 ReadView 读视图机制，说明了事务 520 在判断是否能读取某行数据时，会依据 ReadView 中的 m_ids、min_trx_id、max_trx_id 和 creator_trx_id 等字段进行可见性判断。

> [图] 第254页：是的，这张图有技术知识点。它描述了数据库事务中基于事务ID（trx_id）的可见性规则，特别是在MVCC（多版本并发控制）机制下，不同事务ID范围对应的数据读取状态：小于min_trx_id的可以读，处于min_trx_id和max_trx_id之间的不确定是否可读，大于max_trx_id的属于未知世界不可读。

> [图] 第254页：有技术知识点。该图描述了在数据库事务中，根据事务ID（trx_id）与当前读视图（ReadView）的min_trx_id和max_trx_id的比较关系，判断数据版本是否可读的规则，属于MySQL中多版本并发控制（MVCC）的核心机制。

> [图] 第255页：有技术知识点。该图展示了数据库事务隔离级别中“读已提交（Read Committed）”和“可重复读（Repeated Read）”两种模式下，查询语句生成ReadView的差异：前者每次查询都会创建新的ReadView，后者在事务内所有查询共享同一个ReadView。

> [图] 第257页：有技术知识点。该图展示了数据库主从复制架构，业务服务器将写操作发送到主库，读操作分发到多个从库，从库通过复制机制同步主库数据，实现读写分离和高可用性。

> [图] 第258页：有技术知识点。该图描述了MySQL主从复制的流程，包括主库（master A）的事务处理、日志写入（undo log、redo log、binlog）以及从库（slave B）通过IO线程和SQL线程进行数据同步的过程。

> [图] 第259页：有技术知识点。该图展示了数据库主从复制架构，业务服务器通过数据访问层将写操作发送至主库，读操作分发至多个从库，从库通过复制机制同步主库数据，实现读写分离和高可用性。

> [图] 第260页：有技术知识点。该图展示了数据库主从复制架构，业务服务器通过数据库中间件进行读写分离，写操作发送至主库，读操作分发至从库，从库通过复制机制同步主库数据。

> [图] 第261页：有技术知识点。该图展示了MySQL主从复制的工作流程，包括主库写入数据、更新binlog、通过log dump线程推送binlog到从库，从库的IO线程接收并写入relay log，SQL线程读取执行并更新从库binlog的过程。

> [图] 第261页：有技术知识点。该图展示了Mycat作为数据库中间件，通过DML SQL写入MySQL主库（writeHost），并通过主从复制（binlog）同步到从库（readHost），同时Mycat将Select SQL路由到从库，并对主从节点进行心跳检测的架构设计。

> [图] 第263页：有技术知识点。该图描述了MySQL半同步复制的工作流程，即主库（master）等待至少一个从库（slave）写完relay log并返回ACK确认后，才认为事务提交成功。

> [图] 第265页：有技术知识点。该图展示了数据分库分表（或数据拆分）的架构设计，将原本集中的用户、商品、订单数据按业务类型拆分为独立的数据存储单元，以提升系统性能和可维护性。

> [图] 第266页：有技术知识点。该图展示了数据库分库（水平拆分）的概念，将一个大型用户表按ID范围拆分为两个独立的数据库，以提升系统性能和可扩展性。

> [图] 第267页：是的，这张图有技术知识点。它展示了数据库表的水平拆分和垂直拆分两种数据分片方式：水平拆分是按行（如ID范围）将数据分布到不同表或库中，垂直拆分是按列（如字段）将表拆分为多个子表。

> [图] 第268页：有技术知识点。该图展示了MySQL数据库中两个表（article和article_detail）的建表语句，包含字段定义、数据类型、索引、主键、默认值及注释等数据库设计内容。

> [图] 第269页：有技术知识点。该图展示了基于哈希取模（hash%4）的分库分表路由策略，将原始大表的数据根据哈希值均匀分布到四个分表中。

> [图] 第269页：有技术知识点。该图展示了数据库分表中的“范围路由”策略，即根据数据的数值范围将原始大表拆分为多个分表，例如将1~10000W、100001~20000W、200001~30000W的数据分别存储到不同的分表中，以提升查询效率和系统性能。

> [图] 第270页：有技术知识点。该图展示了数据库分表路由的配置逻辑，通过路由配置表将原始大表的数据根据规则分发到多个分表中。

> [图] 第272页：有技术知识点。该图描述了一种数据库迁移架构：服务读写旧库，同时通过中间件将数据写入新库1和新库2，并通过数据同步和校验机制确保新旧库数据一致性。

> [图] 第273页：有技术知识点。该图描述了一种数据库迁移架构，服务通过中间件实现对旧库和新库（多个）的读写操作，体现的是读写分离或数据双写、平滑迁移的典型模式。

> [图] 第274页：有技术知识点。该图展示了一个基于Nginx负载均衡、MyCat分库分表的分布式用户系统架构，实现了用户数据的水平拆分与高可用部署。

> [图] 第274页：有技术知识点。该图展示了基于 ShardingSphere-JDBC 的分布式数据库分片架构，Java 应用通过 ShardingSphere-JDBC 连接多个数据库实例，并通过 RegisterCenter 进行注册与协调。

> [图] 第275页：有技术知识点。该图展示了Seata分布式事务框架的整体机制，包括AT、TCC和Saga三种模式的描述、优点和缺点，用于保证分布式系统中任务批状态的一致性。

> [图] 第276页：有技术知识点。该图描述了一个基于ETCD集群和分布式Worker的热key探测中间件架构，用于实时检测并缓存热点数据以缓解存储层压力。

> [图] 第277页：有技术知识点。该图展示了一个基于雪花算法（Snowflake）的ID生成器实现，包含线程池、阻塞队列和定时任务等并发编程技术，用于高并发场景下的分布式唯一ID生成。

> [图] 第278页：有技术知识点。该图展示了雪花算法（Snowflake）的ID生成结构，说明了64位ID由最高位（1 bit）、时间戳（41 bit）、机器标识（10 bit）和自增序列（12 bit）组成，用于生成全局唯一ID。

> [图] 第279页：这张图没有直接展示技术知识点，主要为群聊内容，涉及vivo暑期实习offer的讨论。

> [图] 第282页：有技术知识点。该图介绍了MySQL 8.0中引入的即时DDL（Instant DDL）功能，特别是即时添加列（INSTANT ADD COLUMN）的实现背景和技术改进，指出其得益于MySQL 8.0的新事务性数据字典，以及该功能由腾讯游戏DBA团队贡献。

> [图] 第283页：有技术知识点。该图展示了 macOS 系统中 `top` 命令的输出，显示了系统资源使用情况（如 CPU、内存、网络和磁盘 I/O）以及进程信息，其中 MySQL 进程（mysqld）正在运行且占用约 454M 内存，CPU 使用率较低。

> [图] 第284页：有技术知识点。该图展示了一条MySQL查询语句，用于从`students`表中筛选年龄为18的学生姓名，结果返回了两条记录，体现了SQL的基本查询语法和数据库操作。

> [图] 第285页：有技术知识点。该图展示了一条SQL查询语句，用于统计年龄大于18岁的学生在不同班级中的人数分布，涉及SELECT、WHERE、GROUP BY等SQL核心语法。

> [图] 第286页：有技术知识点。该图展示了一条MySQL查询语句，用于找出每个班级中年龄大于该学生的人数少于2人的学生，并按班级和年龄降序排列。

> [图] 第287页：有技术知识点。该图展示了数据库操作的执行过程，包括创建表、创建索引、创建存储过程以及调用存储过程，反映了SQL语句的执行结果和耗时情况，可用于分析数据库性能。

> [图] 第288页：有技术知识点。该图展示了在MySQL中创建无索引表、存储过程、插入数据以及添加索引的执行过程和耗时，说明了索引对数据库性能的影响。

> [图] 第292页：有技术知识点。该图展示了一段MySQL查询语句，使用用户变量实现按班级分组的学生成绩排名，并筛选出每个班级前10名的学生。

> [图] 第293页：有技术知识点。该图展示了一条MySQL查询语句，使用窗口函数`ROW_NUMBER()`按班级分组并按成绩降序排名，筛选出每个班级前10名学生的信息。

> [图] 第294页：有技术知识点。图中展示的书籍封面涉及Java开发相关技术内容，包括MySQL、并发编程、JVM、集合框架和Java基础等，属于计算机编程领域的技术学习资料。

> [图] 第295页：这张图没有直接的技术知识点，主要展示的是一个微信群聊的截图，内容为群成员之间的互动和表情包使用，属于社交交流场景。

> [图] 第297页：这张图中提到了“MQ八股”和“面渣”，结合上下文可以推断出这是在讨论技术面试中的常见问题，尤其是与消息队列（MQ）相关的知识点。因此，该图包含的技术知识点是：**消息队列（MQ）的常见面试题及其应对策略**。

> [图] 第298页：这张图没有直接展示技术知识点，主要是用户之间的聊天记录，表达了对某位作者（“二哥”）编写的面试资料（如《面渣》系列）的期待和好评，提及了MySQL和Redis相关内容的更新计划。  
**一句话描述：** 聊天记录中表达了对《面渣》系列2.0版本（特别是MySQL和Redis部分）的期待与认可。

> [图] 第300页：有技术知识点。图中展示了多个与Java技术栈相关的学习资料文件，包括JVM、Spring、MyBatis、Redis、并发编程等主题的PDF和Markdown文档，属于Java开发进阶学习资源。

> [图] 第301页：有技术知识点。该图详细对比了MySQL中MyISAM和InnoDB存储引擎的索引结构与内存架构，包括MyISAM的非聚集索引、InnoDB的聚集索引以及InnoDB的buffer pool和log buffer等核心内存组件。