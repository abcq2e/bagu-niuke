# 面渣逆袭 —— Redis

> 来源：面渣逆袭Redis篇V2.0.pdf

基础

1. 说说什么是Redis?

Redis是一种基于键值对的NoSQL数据库。它主要的特点是把数据放在内存当中，相比直接访问磁盘的关系型数据库，读写速度会快很多，基本上能达到微秒级的响应。所以在一些对性能要求很高的场景，比如缓存热点数据、防止接口爆刷，都会用到Redis。不仅如此，Redis还支持持久化，可以将内存中的数据异步落盘，以便服务宕机重启后能恢复数据。

Redis和MySQL的区别？

Redis属于非关系型数据库，数据是通过键值对的形式放在内存当中的；MySQL属于关系型数据库，数据以行和列的形式存储在磁盘当中。实际开发中，会将MySQL作为主存储，Redis作为缓存，通过先查Redis，未命中再查MySQL并写回Redis的方式来提高系统的整体性能。

项目里哪里用到了Redis？

在技术派实战项目当中，有很多地方都用到了Redis，比如说用户活跃排行榜用到了zset，作者白名单用到了set。还有用户登录后的Session、站点地图SiteMap，分别用到了Redis的字符串和哈希表两种数据类型。其中比较有挑战性的一个应用是，通过Lua脚本封装Redis的setnex命令来实现分布式锁，以保证在高并发场景下，热点文章在短时间内的高频访问不会击穿MySQL。

部署过Redis吗？

第一种回答版本：我只在本地部署过单机版，下载Redis的安装包，解压后运行redis-server命令即可。

第二种回答版本：我有在生产环境中部署单机版Redis，从官网下载源码包解压后执行make && make install编译安装。然后编辑redis.conf文件，开启远程访问、设置密码、限制内存、设置内存过期淘汰策略、开启AOF持久化等：

第三种回答版本：我有使用Docker拉取Redis镜像后进行容器化部署。

bind 0.0.0.0        # 允许远程访问
requirepass your_password  # 设置密码
maxmemory 4gb      # 限制内存，避免OOM
maxmemory-policy allkeys-lru  # 内存淘汰策略
appendonly yes     # 开启AOF持久化

docker run -d --name redis -p 6379:6379 redis:7.0-alpine

Redis的高可用方案有部署过吗？

有部署过哨兵机制，这是一个相对成熟的高可用解决方案，我们生产环境部署的是一主两从的Redis实例，再加上三个Sentinel节点监控它们。Sentinel的配置相对简单，主要设置了故障转移的判定条件和超时阈值。

主节点配置：
port 6379
appendonly yes

从节点配置：
replicaof 192.168.1.10 6379

哨兵节点配置：
sentinel monitor mymaster 192.168.1.10 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 60000
sentinel parallel-syncs mymaster 1

当主节点发生故障时，Sentinel能够自动检测并协商选出新的主节点，这个过程大概需要10-15秒。

另一个大型项目中，我们使用了Redis Cluster集群方案。该项目数据量大且增长快，需要水平扩展能力。我们部署了6个主节点，每个主节点配备一个从节点，形成了一个3主3从的初始集群。Redis Cluster的设置比Sentinel复杂一些，需要正确配置集群节点间通信、分片映射等。

redis-server redis-7000.conf
redis-server redis-7001.conf
...
# 使用redis-cli创建集群
# Redis会自动将key哈希到16384个槽位
# 主节点均分槽位，从节点自动跟随
redis-cli --cluster create \
  127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 \
  127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 \
  --cluster-replicas 1

Redis Cluster最大的优势是数据自动分片，我们可以通过简单地增加节点来扩展集群容量。此外，它的故障转移也很快，通常在几秒内就能完成。

对于一些轻量级应用，我也使用过主从复制加手动故障转移的方案。主节点负责读写操作，从节点负责读操作。手动故障转移时，我们会先将从节点提升为主节点，然后重新配置其他从节点。

# 1. 取消从节点身份
redis-cli -h <slave-ip> slaveof no one
# 2. 将其他从节点指向新的主节点
redis-cli -h <other-slave-ip> slaveof <new-master-ip> <port>

2. Redis可以用来干什么？

Redis可以用来做缓存，比如说把高频访问的文章详情、商品信息、用户信息放入Redis当中，并通过设置过期时间来保证数据一致性，这样就可以减轻数据库的访问压力。Redis的Zset还可以用来实现积分榜、热搜榜，通过score字段进行排序，然后取前N个元素，就能实现TOPN的榜单功能。利用Redis的SETNX命令或者Redisson还可以实现分布式锁，确保同一时间只有一个节点可以持有锁；为了防止出现死锁，可以给锁设置一个超时时间，到期后自动释放；并且最好开启一个监听线程，当任务尚未完成时给锁自动续期。如果是秒杀接口，还可以使用Lua脚本来实现令牌桶算法，限制每秒只能处理N个请求。

-- KEYS[1]: 令牌桶的key
-- ARGV[1]: 桶容量
-- ARGV[2]: 令牌生成速率（每秒）
-- ARGV[3]: 当前时间戳（秒）
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(bucket[1]) or ARGV[1]
local last_time = tonumber(bucket[2]) or ARGV[3]
local rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[1])
local now = tonumber(ARGV[3])
-- 计算新令牌数
local delta = math.max(0, now - last_time)
local add_tokens = delta * rate
tokens = math.min(capacity, tokens + add_tokens)
last_time = now
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end
redis.call('HMSET', KEYS[1], 'tokens', tokens, 'timestamp', last_time)
redis.call('EXPIRE', KEYS[1], 3600) -- 过期时间可自定义
return allowed

在Java中调用Lua脚本：

// 令牌桶参数
int capacity = 10; // 桶容量
int rate = 2;      // 每秒2个令牌
long now = System.currentTimeMillis() / 1000;
String key = "token_bucket:user:123";
// 调用Lua脚本，返回1表示通过，0表示被限流
Long allowed = (Long) redis.eval(luaScript, 1, key, String.valueOf(capacity), 
String.valueOf(rate), String.valueOf(now));

3. Redis有哪些数据类型？

Redis支持五种基本数据类型，分别是字符串、列表、哈希、集合和有序集合。还有三种扩展数据类型，分别是用于位级操作的Bitmap、用于基数估算的HyperLogLog、支持存储和查询地理坐标的GEO。

详细介绍下字符串？

字符串是最基本的数据类型，可以存储文本、数字或者二进制数据，最大容量是512 MB。适合缓存单个对象，比如验证码、token、计数器等。

详细介绍下列表？

列表是一个有序的元素集合，支持从头部或尾部插入/删除元素，常用于消息队列或任务列表。

详细介绍下哈希？

哈希是一个键值对集合，适合存储对象，如商品信息、用户信息等。比如说value = {name: '沉默王二', age: 18}。

详细介绍下集合？

集合是无序且不重复的，支持交集、并集操作，查询效率能达到O(1)级别，主要用于去重、标签、共同好友等场景。

详细介绍下有序集合？

有序集合的元素按分数进行排序，支持范围查询，适用于排行榜或优先级队列。

详细介绍下Bitmap？

Bitmap可以把一组二进制位紧凑地存储在一块连续内存中，每一位代表一个对象的状态，比如是否签到、是否活跃等。比如用户0的已签到1、用户1未签到0、用户2已签到，Redis就会把这些状态放进一个连续的二进制串101，1亿用户签到仅需100,000,000 / 8 / 1024 ≈ 12MB的空间，真的省到离谱。

详细介绍下HyperLogLog？

HyperLogLog是一种用于基数统计的概率性数据结构，可以在仅有12KB的内存空间下，统计海量数据集中不重复元素的个数，误差率仅0.81%。底层基于LogLog算法改进，先把每个元素哈希成一个二进制串，然后取前14位进行分组，放到16384个桶中，记录每组最大的前导零数量，最后用一个近似公式推算出总体的基数。

元素    哈希值        前导零个数
userA   000100101…    3
userB   001010011…    2
userC   000000101…    6

2^14个桶，每个桶6 Bit，刚好16384 * 6 /8 / 1024 K = 12KB，8 bit = 1 byte。

举个超简单的例子，假设有一个神奇的哈希函数，可以把元素散列成一个二进制数，比如：可以发现，哈希值越长前导零越多，也就说明集合里的元素越多。

大型网站UV统计系统示例：

public class UVCounter {
    private Jedis jedis;
    
    public void recordVisit(String date, String userId) {
        String key = "uv:" + date;
        jedis.pfadd(key, userId);
    }
    
    public long getUV(String date) {
        return jedis.pfcount("uv:" + date);
    }
    
    public long getUVBetween(String startDate, String endDate) {
        ...
    }
}

详细介绍下GEO？

GEO用于存储和查询地理位置信息，可以用来计算两点之间的距离，查找某位置半径内的其他元素。常见的应用场景包括：附近的人或者商家、计算外卖员和商家的距离、判断用户是否进入某个区域等。底层基于ZSet实现，通过Geohash算法把经纬度编码成score。比如说查询附近的商家时，Redis会根据中心点经纬度反推可能的Geohash范围，在ZSet上做范围查询，拿到候选点后，用Haversine公式精确计算球面距离，筛选出最终符合要求的位置。       List<String> keys = getDateKeys(startDate, endDate);
        return jedis.pfcount(keys.toArray(new String[0]));
    }
}
public class NearbyShopService {
    private Jedis jedis;
    private static final String SHOP_KEY = "shops:geo";
    
    // 添加商铺
    public void addShop(String shopId, double longitude, double latitude) {
        jedis.geoadd(SHOP_KEY, longitude, latitude, shopId);
    }
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 23 / 261

---

为什么使⽤ hash 类型⽽不使⽤ string 类型序列化存储？
 
Hash 可以只读取或者修改某⼀个字段，⽽ String 需要⼀次性把整个对象取出来。
    
    // 查询附近的商铺
    public List<GeoRadiusResponse> getNearbyShops(
            double longitude, 
            double latitude, 
            double radiusKm) {
        return jedis.georadius(SHOP_KEY, 
                             longitude, 
                             latitude, 
                             radiusKm, 
                             GeoUnit.KM, 
                             GeoRadiusParam.geoRadiusParam()
                                         .withCoord()
                                         .withDist()
                                         .sortAscending()
                                         .count(20));
    }
    
    // 计算两个商铺之间的距离
    public double getShopDistance(String shop1Id, String shop2Id) {
        return jedis.geodist(SHOP_KEY, 
                           shop1Id, 
                           shop2Id, 
                           GeoUnit.KILOMETERS);
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 24 / 261

---

⽐如说有⼀个⽤户对象 user = {name: '沉默王⼆', age: 18} ，如果使⽤ Hash 存储，可以直接修改 age 字
段：
如果使⽤ String 存储，需要先取出整个对象，修改后再存回去：
1. Java ⾯试指南（付费）收录的字节跳动商业化⼀⾯的原题：说说 Redis 的 zset，什么是跳表，插⼊⼀个
节点要构建⼏层索引
redis.hset("user:1", "age", 19);
String userJson = redis.get("user:1");
User user = JSON.parseObject(userJson, User.class);
user.setAge(19);
redis.set("user:1", JSON.toJSONString(user));
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 25 / 261

---

2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 9 ⻜书后端技术⼀⾯⾯试原题：Redis 的数据类型，
ZSet 的实现
3. Java ⾯试指南（付费）收录的⼩⽶暑期实习同学 E ⼀⾯⾯试原题：你对 Redis 了解多少，说说常⻅的数
据结构和应⽤场景
4. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：Redis 的数据类型
5. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下 Redis 常⽤的数据
结构
6. Java ⾯试指南（付费）收录的农业银⾏⾯经同学 7 Java 后端⾯试原题：Redis 相关的基础知识
7. Java ⾯试指南（付费）收录的华为⾯经同学 11 ⾯试原题：项⽬中使⽤了 redis，redis 有哪些数据类
型？分别使⽤的场景是什么？什么使⽤ hash 类型⽽不使⽤ string 类型序列化存储？
8. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：Redis常⻅数据结构
9. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：redis的数据结构类型？
10. Java ⾯试指南（付费）收录的阿⾥云⾯经同学 22 ⾯经：redis⾼级数据结构的使⽤场景
11. Java ⾯试指南（付费）收录的腾讯⾯经同学 29 Java 后端⼀⾯原题：Redis保证incr命令原⼦性的原理是
什么？
memo：2025 年 4 ⽉ 29 ⽇修改⾄此，今天有球友发信息说拿到了亚⻢逊的 offer，⼯资还给的很⾼，问我要不要
选？ 真的恭喜了
。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 26 / 261

---

4.
Redis 为什么快呢？
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 27 / 261

---

第⼀，Redis 的所有数据都放在内存中，⽽内存的读写速度本身就⽐磁盘快⼏个数量级。
第⼆，Redis 采⽤了基于 IO 多路复⽤技术的事件驱动模型来处理客户端请求和执⾏ Redis 命令。
其中的 IO 多路复⽤技术可以在只有⼀个线程的情况下，同时监听成千上万个客户端连接，解决传统 IO 模型中每个
连接都需要⼀个独⽴线程带来的性能开销。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 28 / 261

---

IO 多路复⽤会持续监听请求，然后把准备好的请求压⼊到⼀个队列当中，并将其有序地传递给⽂件事件分派器，
最后由事件处理器来执⾏对应的 accept、read 和 write 请求。
Redis 会根据操作系统选择最优的 IO 多路复⽤技术，⽐如 Linux 下使⽤ epoll，macOS 下使⽤ kqueue 等。
// epoll 的创建和使⽤
int epfd = epoll_create(1024); // 创建 epoll 实例
struct epoll_event ev, events[MAX_EVENTS];
// 添加监听事件
ev.events = EPOLLIN;
ev.data.fd = listen_sock;
epoll_ctl(epfd, EPOLL_CTL_ADD, listen_sock, &ev);
// 等待事件发⽣
while (1) {
    int nfds = epoll_wait(epfd, events, MAX_EVENTS, -1);
    for (int i = 0; i < nfds; i++) {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 29 / 261

---

在 Redis 6.0 之前，包括连接建⽴、请求读取、响应发送，以及命令执⾏都是在主线程中顺序执⾏的，这样可以避
免多线程环境下的锁竞争和上下⽂切换，因为 Redis 的绝⼤部分操作都是在内存中进⾏的，性能瓶颈主要是内存操
作和⽹络通信，⽽不是 CPU。
为了进⼀步解决⽹络 IO 的性能瓶颈，Redis 6.0 引⼊了多线程机制，把⽹络 IO 和命令执⾏分开，⽹络 IO 交给线程
池来处理，⽽命令执⾏仍然在主线程中进⾏，这样就可以充分利⽤多核 CPU 的性能。
主线程专注于命令执⾏，⽹络IO 由其他线程分担，在多核 CPU 环境下，Redis 的性能可以得到显著提升。
        // 处理就绪的⽂件描述符
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 30 / 261

---

第三，Redis 对底层数据结构做了极致的优化，⽐如说 String 的底层数据结构动态字符串⽀持动态扩容、预分配冗
余空间，能够减少内存碎⽚和内存分配的开销。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 31 / 261

---

总结：
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 32 / 261

---

1. Java ⾯试指南（付费）收录的腾讯 Java 后端实习⼀⾯原题：Redis 为什么读写性能⾼？
2. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：为什么 redis 快，淘汰策略 持久化
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：单线程的 Redis 为什
么这么快？
4. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：Redis 为什么这么快？
5. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：项⽬中什么地⽅使
⽤了 redis 缓存，redis 为什么快？
6. Java ⾯试指南（付费）收录的得物⾯经同学 8 ⼀⾯⾯试原题：Redis 为什么快
7. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21  抖⾳商城⼀⾯⾯试原题：redis为什么能处理⾼并
发
memo：2025 年 4 ⽉ 30 ⽇修改⾄此，今天有球友发信息说拿到了滴滴的实习 offer，真的恭喜了
。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 33 / 261

---

5.能详细说⼀下IO多路复⽤吗？
 
IO 多路复⽤是⼀种允许单个进程同时监视多个⽂件描述符的技术，使得程序能够⾼效处理多个并发连接⽽⽆需创
建⼤量线程。
IO 多路复⽤的核⼼思想是：让单个线程可以等待多个⽂件描述符就绪，然后对就绪的描述符进⾏操作。这样可以
在不使⽤多线程或多进程的情况下处理并发连接。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 34 / 261

---

主要的实现机制包括 select、poll、epoll、kqueue 和 IOCP 等。
请说说 select、poll、epoll、kqueue 和 IOCP 的区别？
 
select 的缺点是单个进程能监视的⽂件描述符数量有限，⼀般为 1024 个，且每次调⽤都需要将⽂件描述符集合从
⽤户态复制到内核态，然后遍历找出就绪的描述符，性能较差。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 35 / 261

---

poll 的优点是没有最⼤⽂件描述符数量的限制，但是每次调⽤仍然需要将⽂件描述符集合从⽤户态复制到内核态，
依然需要遍历，性能仍然较差。
epoll 是 Linux 特有的 IO 多路复⽤机制，⽀持⼤规模并发连接，使⽤事件驱动模型，性能更⾼。其⼯作原理是将⽂
件描述符注册到内核中，然后通过事件通知机制来处理就绪的⽂件描述符，不需要轮询，也不需要数据拷⻉，更没
有数量限制，所以性能⾮常⾼。
// select 的基本使⽤
int select(int nfds, fd_set *readfds, fd_set *writefds, 
           fd_set *exceptfds, struct timeval *timeout);
// 示例代码
fd_set readfds;
FD_ZERO(&readfds);                // 清空集合
FD_SET(sockfd, &readfds);         // 添加监听套接字
select(sockfd + 1, &readfds, NULL, NULL, NULL);
if (FD_ISSET(sockfd, &readfds)) { // 检查是否就绪
    // 处理读事件
}
// poll 的基本使⽤
int poll(struct pollfd *fds, nfds_t nfds, int timeout);
// 示例代码
struct pollfd fds[MAX_EVENTS];
fds[0].fd = sockfd;
fds[0].events = POLLIN;    // 监听读事件
poll(fds, 1, -1);
if (fds[0].revents & POLLIN) {
    // 处理读事件
}
// epoll 的基本使⽤
int epoll_create(int size);
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
int epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout);
// 示例代码
int epfd = epoll_create(1);
struct epoll_event ev, events[MAX_EVENTS];
ev.events = EPOLLIN;
ev.data.fd = sockfd;
epoll_ctl(epfd, EPOLL_CTL_ADD, sockfd, &ev);
while (1) {
    int nfds = epoll_wait(epfd, events, MAX_EVENTS, -1);
    for (int i = 0; i < nfds; i++) {
        if (events[i].data.fd == sockfd) {
            // 处理读事件
        }
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 36 / 261

---

kqueue 是 BSD/macOS 系统下的 IO 多路复⽤机制，类似于 epoll，⽀持⼤规模并发连接，使⽤事件驱动模型。
IOCP 是 Windows 系统下的 IO 多路复⽤机制，使⽤使⽤完成端⼝模型⽽⾮事件通知。
举个例⼦说⼀下 IO 多路复⽤？
 
⽐如说我是⼀名数学⽼师，上课时提出了⼀个问题：“今天谁来证明⼀下勾股定律？”
同学⼩王举⼿，我就让⼩王回答；⼩李举⼿，我就让⼩李回答；⼩张举⼿，我就让⼩张回答。
这种模式就是 IO 多路复⽤，我只需要在讲台上等，谁举⼿谁回答，不需要⼀个⼀个去问。
Redis 就是使⽤ epoll 这样的 IO 多路复⽤机制，在单线程模型下实现⾼效的⽹络 IO，从⽽⽀持⾼并发的请求处
理。
举例⼦说⼀下阻塞 IO和 IO 多路复⽤的差别？
 
假设我是⼀名⽼师，让学⽣解答⼀道题⽬。
我的第⼀种选择：按顺序逐个检查，先检查 A同学，然后是 B，之后是 C、D。。。这中间如果有⼀个学⽣卡住，
全班都会被耽误。
    }
}
int kqueue(void);
int kevent(int kq, const struct kevent *changelist, int nchanges, struct kevent 
*eventlist, int nevents, const struct timespec *timeout);
HANDLE CreateIoCompletionPort(HANDLE FileHandle, HANDLE ExistingCompletionPort, ULONG_PTR 
CompletionKey, DWORD NumberOfConcurrentThreads);
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 37 / 261

---

这种就是阻塞 IO，不具有并发能⼒。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 38 / 261

---

⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 39 / 261

---

我的第⼆种选择，我站在讲台上等，谁举⼿我去检查谁。C、D 举⼿，我去检查 C、D 的答案，然后继续回到讲台
上等。此时 E、A ⼜举⼿，然后去处理 E 和 A。
select、poll 和 epoll 的实现原理？
 
select 和 poll 都是通过把所有⽂件描述符传递给内核，由内核遍历判断哪些就绪。
select 将⽂件描述符 FD 通过 BitsMap 传⼊内核，轮询所有的 FD，通过调⽤ file->poll 函数查询是否有对应事件，
没有就将 task 加⼊ FD 对应 file 的待唤醒队列，等待事件来临被唤醒。
poll 改进了连接数上限问题，不再⽤ BitsMap 来传⼊ FD，取⽽代之的是动态数组 pollfd，但本质上仍是线性遍
历，性能没有提升太多。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 40 / 261

---

select和poll的模式都是，⼀次将参数拷⻉到内核空间，等有结果了再⼀次拷⻉出去。
epoll 将监听的 FD 注册进内核的红⿊树，由内核在事件触发时将就绪的 FD 放⼊ ready list。应⽤程序通过 
epoll_wait 获取就绪的 FD，从⽽避免遍历所有连接的开销。
epoll 最⼤的优点是：⽀持事件驱动 + 边缘触发，ADD 时拷⻉⼀次，epoll_wait 时利⽤ MMAP 和⽤户共享空间，
直接拷⻉数据到⽤户空间，因此在⾼并发场景下性能远⾼于 select 和 poll。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21  抖⾳商城⼀⾯⾯试原题：io多路复⽤了解吗？
2. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：IO多路复⽤中select/poll/epoll各⾃的实现原理和
区别？
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 41 / 261

---

3. Java ⾯试指南（付费）收录的字节跳动⾯经同学19番茄⼩说⼀⾯⾯试原题：Linux中的IO多路复⽤
memo：2025 年 5 ⽉ 1 ⽇修改⾄此，今天帮球友修改简历时 时，碰到⼀名北京交通⼤学的同学，⼜⼀所 211 院
校，星球真的是⼈才济济，⼤家⼀起加油吧（骄傲）。
6.Redis为什么早期选择单线程？
 
第⼀，单线程模型不需要考虑复杂的锁机制，不存在多线程环境下的死锁、竞态条件等问题，开发起来更快，也更
容易维护。
第⼆，Redis 是IO 密集型⽽⾮ CPU 密集型，主要受内存和⽹络 IO 限制，⽽⾮ CPU 的计算能⼒，单线程可以避免
线程上下⽂切换的开销。
哪怕我们在⼀个普通的 Linux 服务器上启动 Redis 服务，它也能在 1s 内处理 1000000 个⽤户请求。
第三，单线程可以保证命令执⾏的原⼦性，⽆需额外的同步机制。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 42 / 261

---

Redis 虽然最初采⽤了单线程设计，但后续的版本中也在特定⽅⾯引⼊了多线程，⽐如说 Redis 4.0 就引异步多线
程，⽤于清理脏数据、释放⽆⽤连接、删除⼤ Key 等。
 
官⽅解释：https://redis.io/topics/faq
/* 从数据库中删除⼀个键、值以及相关的过期条⽬（如果有的话）。
 * 如果释放值对象需要⼤量的内存分配操作，该对象可能会被放⼊
 * 延迟释放列表中，⽽不是同步释放。延迟释放列表将在
 * bio.c 的另⼀个线程中进⾏回收。 */
#define LAZYFREE_THRESHOLD 64
int dbAsyncDelete(redisDb *db, robj *key) {
    /* 从过期字典中删除条⽬不会释放键的 sds，
     * 因为它与主字典共享。 */
    if (dictSize(db->expires) > 0) dictDelete(db->expires,key->ptr);
    /* 如果值对象只包含少量的内存分配，使⽤延迟释放⽅式
     * 实际上会更慢... 所以在⼀定阈值以下，我们就直接
     * 同步释放对象。 */
    dictEntry *de = dictUnlink(db->dict,key->ptr);
    if (de) {
        robj *val = dictGetVal(de);
        // 计算value的回收收益
        size_t free_effort = lazyfreeGetFreeEffort(val);
        /* 如果释放对象的⼯作量太⼤，就通过将对象添加到延迟释放列表
         * 在后台进⾏处理。
         * 注意，如果对象是共享的，现在就回收它是不可能的。这种情况
         * 很少发⽣，但是有时 Redis 核⼼的某些实现部分可能会调⽤
         * incrRefCount() 来保护对象，然后调⽤ dbDelete()。在这种
         * 情况下，我们会继续执⾏并到达 dictFreeUnlinkedEntry() 
         * 调⽤，这相当于仅仅调⽤ decrRefCount()。 */
        // 只有回收收益超过⼀定值，才会执⾏异步删除，否则还是会退化到同步删除
        if (free_effort > LAZYFREE_THRESHOLD && val->refcount == 1) {
            atomicIncr(lazyfree_objects,1);
            bioCreateBackgroundJob(BIO_LAZY_FREE,val,NULL,NULL);
            dictSetVal(db->dict,de,NULL);
        }
    }
    /* 释放键值对，如果我们将 val 字段设置为 NULL 以便稍后
     * 延迟释放，那么就只释放键。 */
    if (de) {
        dictFreeUnlinkedEntry(db->dict,de);
        if (server.cluster_enabled) slotToKeyDel(key->ptr);
        return 1;
    } else {
        return 0;
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 43 / 261

---

memo：2025 年 5 ⽉ 2 ⽇修改⾄此，今天帮球友修改简历时 时，碰到⼀名同济⼤学的同学，让感觉⾃⼰的付出正
在越来越多被更多⼈看到，真的很开⼼。
7.Redis 6.0 使⽤多线程是怎么回事?
 
Redis 6.0 的多线程仅⽤于处理⽹络 IO，包括⽹络数据的读取、写⼊，以及请求解析。
⽽命令的执⾏依然是单线程，这种设计被称为“IO 线程化”，能够在⾼负载的情况下，最⼤限度地提升 Redis 的响应
速度。
---- 这部分⾯试中可以不背，⽅便⼤家理解 start ----
这⼀变化主要是因为随着⽹络带宽和服务器性能的提升，Redis 的瓶颈从 CPU 逐渐转移到了⽹络 IO：
│ 单线程执⾏命令 │
                  │    ↑    ↓     │
┌─────────┐     ┌─┴────────────┴──┐
│ I/O线程1 │ ←→ │                 │
├─────────┤     │                 │
│ I/O线程2 │ ←→ │    主线程       │
├─────────┤     │                 │
│ I/O线程3 │ ←→ │                 │
└─────────┘     └─────────────────┘
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 44 / 261

---

带宽从 10Gbps 提升到 100Gbps，甚⾄更⾼。
请求的并发数从⼏千到⼏万，甚⾄⼏⼗万。
单线程在⾼负载场景下处理⽹络 IO 出现了明显的性能瓶颈，Redis 的开发团队通过研究发现，在处理⼤数据包
时，单线程 Redis 有超过 80% 的 CPU 时间花在⽹络 IO 上，⽽实际命令执⾏仅占 20% 左右。
Redis 6.0 的多线程 IO 模型主要包含三个核⼼步骤：
仍然由主线程负责接收客户端的连接请求。
主线程将连接请求分发给多个 IO 线程进⾏处理，主线程负责解析和执⾏命令。
命令执⾏完毕后，由多个 IO 线程将结果返回给客户端。
// Redis 主事件循环（简化版）
void beforeSleep(struct aeEventLoop *eventLoop) {
    // 1. 主线程分派读任务给 I/O 线程
    handleClientsWithPendingReadsUsingThreads();
    
    // 2. 等待 I/O 线程完成读取
    waitForIOThreads();
    
    // 3. 主线程处理命令
    processInputBuffer();
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 45 / 261

---

命令
作⽤
示例
SET key value
设置字符串键值
SET name jack
GET key
获取字符串值
GET name
INCR key
数值⾃增 1
INCR count
DECR key
数值⾃减 1
DECR stock
INCRBY key N
增加 N
INCRBY views 10
APPEND key value
追加字符串
APPEND log "done"
GETRANGE key start end
获取⼦串
GETRANGE name 0 3
MSET k1 v1 k2 v2
批量设置多个键值
MSET a 1 b 2
Redis 6.0 默认仍然使⽤单线程模式，但可以通过配置⽂件或命令⾏参数启⽤多线程模式。
建议将 IO 线程数设置为 CPU 核⼼数的⼀半，⼀般不建议超过 8 个。
经过多次测试，Redis 6.0 在处理 1-200 字节的⼩数据包时，性能提升 1.5-2 倍；在处理 1KB 以上的⼤数据包时提
升约 3-5 倍。
----这部分⾯试中可以不背，⽅便⼤家理解 end ----
1. Java ⾯试指南（付费）收录的同学 30 腾讯⾳乐⾯试原题：redis6.0引⼊的多线程⽤作什么地⽅
8.说说 Redis 的常⽤命令（补充）
 
2024 年 04 ⽉ 11 ⽇增补
⼀句话回答（也不⽤全部都背，挑三个就⾏）：
Redis ⽀持多种数据结构，常⽤的命令也⽐较多，⽐如说操作字符串可以⽤ SET/GET/INCR ，操作哈希可以⽤ 
HSET/HGET/HGETALL ，操作列表可以⽤ LPUSH/LPOP/LRANGE ，操作集合可以⽤ SADD/SISMEMBER ，操作有序集
合可以⽤ ZADD/ZRANGE/ZINCRBY 等，通⽤命令有 EXPIRE/DEL/KEYS 等。
----这部分⾯试中可以不背，⽅便⼤家理解 start----
①、操作字符串的命令有：
    
    // 4. 主线程分派写任务给 I/O 线程
    handleClientsWithPendingWritesUsingThreads();
}
# 启⽤多线程模式
io-threads 4
# 启⽤多线程写⼊（Redis 6.0 默认只开启多线程读取）
io-threads-do-reads yes
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 46 / 261

---

②、操作列表的命令有：
LPUSH key value ：将⼀个值插⼊到列表 key 的头部。
RPUSH key value ：将⼀个值插⼊到列表 key 的尾部。
LPOP key ：移除并返回列表 key 的头元素。
RPOP key ：移除并返回列表 key 的尾元素。
LRANGE key start stop ：获取列表 key 中指定范围内的元素。
③、操作集合的命令有：
SADD key member ：向集合 key 添加⼀个元素。
SREM key member ：从集合 key 中移除⼀个元素。
SMEMBERS key ：返回集合 key 中的所有元素。
④、操作有序集合的命令有：
ZADD key score member ：向有序集合 key 添加⼀个成员，或更新其分数。
ZRANGE key start stop [WITHSCORES] ：按照索引区间返回有序集合 key 中的成员，可选 WITHSCORES 
参数返回分数。
ZREVRANGE key start stop [WITHSCORES] ：返回有序集合 key 中，指定区间内的成员，按分数递减。
ZREM key member ：移除有序集合 key 中的⼀个或多个成员。
⑤、操作哈希的命令有：
HSET key field value ：向键为 key 的哈希表中设置字段 field 的值为 value。
HGET key field ：获取键为 key 的哈希表中字段 field 的值。
HGETALL key ：获取键为 key 的哈希表中所有的字段和值。
HDEL key field ：删除键为 key 的哈希表中的⼀个或多个字段。
详细说说 set 命令？
 
SET 命令⽤于设置字符串的 key，⽀持过期时间和条件写⼊，常⽤于设置缓存、实现分布式锁、延⻓ Session 等场
景。
默认情况下，SET 会覆盖键已有的值。
⽀持多种设置过期时间的⽅式，⽐如说 EX 设置秒级过期时间，PX 设置毫秒过期时间。
⽀持条件写⼊，使其可以实现原⼦性操作，⽐如说 NX 仅在键不存在时设置值，XX 仅在键存在时设置值。
SET key value [EX seconds | PX milliseconds | EXAT timestamp | PXAT timestamp-
milliseconds | KEEPTTL] [NX | XX] [GET]
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 47 / 261

---

缓存实现：
实现分布式锁：
存储 Session：
sadd 命令的时间复杂度是多少？
 
SADD ⽀持⼀次添加多个元素，返回值为实际添加成功的元素数量，时间复杂度为 O(N)。
SET user:profile:{userid} {JSON数据} EX 3600  # 存储⽤户资料，并设置1⼩时过期
SET lock:resource_name {random_value} EX 10 NX  # 获取锁，10秒后⾃动释放
SET session:{sessionid} {session_data} EX 1800  # 存储⽤户会话，30分钟过期
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 48 / 261

---

incr命令了解吗？
 
INCR 是⼀个原⼦命令，可以将指定键的值加 1，如果 key 不存在，会先将其设置为 0，再执⾏加 1 操作。
常⽤于⽹站访问量、⽂章点赞数等计数器的实现；结合过期时间实现限流器；⽣成分布式唯⼀ ID；库存扣减等。
1. Java ⾯试指南（付费）收录的京东⾯经同学 1 Java 技术⼀⾯⾯试原题：说说 Redis 常⽤命令
2. Java ⾯试指南（付费）收录的农业银⾏⾯经同学 3 Java 后端⾯试原题：说的那么好，Redis 设置 key 
value 的函数是啥
3. Java ⾯试指南（付费）收录的快⼿⾯经同学 1 部⻔主站技术部⾯试原题：Redis 的 sadd 命令时间复杂
度是多少？
memo：2025 年 5 ⽉ 3 ⽇修改⾄此，今天有球友发信息说拿到了美的的软开暑期实习 offer，虽然他⾃⼰不满
意，但暂时没有其他更好的，我建议他先去试⼀下
。
redis-cli SADD myset "apple" "banana" "orange"
# 限制⽤户每分钟最多访问10次
FUNCTION limit_api_call(user_id)
    current = INCR("rate:"+user_id)
    IF current == 1 THEN
        EXPIRE("rate:"+user_id, 60)
    END
    IF current > 10 THEN
        RETURN false  # 超出限制
    ELSE
        RETURN true   # 允许访问
    END
END
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 49 / 261

---

9.单线程的Redis QPS 能到多少？(补充)
 
2024 年 4 ⽉ 14 ⽇增补
根据官⽅的基准测试，⼀个普通服务器的 Redis 实例通常可以达到每秒⼗万左右的 QPS。
----这部分⾯试中可以不背，⽅便⼤家理解 start ----
Redis 的 QPS（每秒请求数）性能取决于多种因素，包括硬件配置、⽹络延迟、数据结构、命令类型等。
可以通过 redis-benchmark 命令进⾏基准测试：
-h ：指定 Redis 服务器的地址，默认是 127.0.0.1。
redis-benchmark -h 127.0.0.1 -p 6379 -c 50 -n 10000
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 50 / 261

---

-p ：指定 Redis 服务器的端⼝，默认是 6379。
-c ：并发连接数，即同时有多少个客户端在进⾏测试。
-n ：请求总数，即测试过程中总共要执⾏多少个请求。
2023 年前，我⽤的是⼀台 macOS，4 GHz 四核 Intel Core i7，32 GB 1867 MHz DDR3，测试结果如下：
可以看得出，每秒能处理超过 10 万次请求。
延迟也⾮常低，99% 的请求都在 0.3ms 以内完成了。
----这部分⾯试中可以不背，⽅便⼤家理解 end ----
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：单线程 Redis 的 QPS 
是多少？
持久化
 
10.
Redis的持久化⽅式有哪些？
 
主要有两种，RDB 和 AOF。RDB 通过创建时间点快照来实现持久化，AOF 通过记录每个写操作命令来实现持久
化。
QPS = 总请求数 / 总耗时 = 10000 / 0.09 ≈ 111111 QPS
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 51 / 261

---

这两种⽅式可以单独使⽤，也可以同时使⽤。这样就可以保证 Redis 服务器在重启后不丢失数据，通过 RDB 和 
AOF ⽂件来恢复内存中原有的数据。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 52 / 261

---

详细说⼀下 RDB？
 
RDB 持久化机制可以在指定的时间间隔内将 Redis 某⼀时刻的数据保存到磁盘上的 RDB ⽂件中，当 Redis 重启
时，可以通过加载这个 RDB ⽂件来恢复数据。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 53 / 261

---

RDB 持久化可以通过 save 和 bgsave 命令⼿动触发，也可以通过配置⽂件中的 save 指令⾃动触发。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 54 / 261

---

save 命令会阻塞 Redis 进程，直到 RDB ⽂件创建完成。
bgsave 命令会在后台 fork ⼀个⼦进程来执⾏ RDB 持久化操作，主进程不会被阻塞。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 55 / 261

---

什么情况下会⾃动触发 RDB 持久化？
 
第⼀种，在 Redis 配置⽂件中设置 RDB 持久化参数 save <seconds> <changes> ，表示在指定时间间隔内，如
果有指定数量的键发⽣变化，就会⾃动触发 RDB 持久化。
第⼆种，主从复制时，当从节点第⼀次连接到主节点时，主节点会⾃动执⾏ bgsave ⽣成 RDB ⽂件，并将其发送
给从节点。
save 900 1      # 900 秒（15 分钟）内有 1 个 key 发⽣变化，触发快照
save 300 10     # 300 秒（5 分钟）内有 10 个 key 发⽣变化，触发快照
save 60 10000   # 60 秒内有 10000 个 key 发⽣变化，触发快照
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 56 / 261

---

第三种，如果没有开启 AOF，执⾏ shutdown 命令时，Redis 会⾃动保存⼀次 RDB ⽂件，以确保数据不会丢失。
详细说⼀下 AOF？
 
AOF 通过记录每个写操作命令，并将其追加到 AOF ⽂件来实现持久化，Redis 服务器宕机后可以通过重新执⾏这
些命令来恢复数据。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 57 / 261

---

当 Redis 执⾏写操作时，会将写命令追加到 AOF 缓冲区；Redis 会根据同步策略将缓冲区的数据写⼊到 AOF ⽂
件。
当 AOF ⽂件过⼤时，Redis 会⾃动进⾏ AOF 重写，剔除多余的命令，⽐如说多次对同⼀个 key 的 set 和 del，⽣
成⼀个新的 AOF ⽂件；当 Redis 重启时，读取 AOF ⽂件中的命令并重新执⾏，以恢复数据。
AOF 的刷盘策略了解吗？
 
Redis 将 AOF 缓冲区的数据写⼊到 AOF ⽂件时，涉及两个系统调⽤：write 将数据写⼊到操作系统的缓冲区，
fsync 将 OS 缓冲区的数据刷新到磁盘。
这⾥的刷盘涉及到三种策略：always、everysec 和 no。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 58 / 261

---

always：每次写命令执⾏完，⽴即调⽤ fsync 同步到磁盘，这样可以保证数据不丢失，但性能较差。
everysec：每秒调⽤⼀次 fsync，将多条命令⼀次性同步到磁盘，性能较好，数据丢失的时间窗⼝为 1 秒。
no：不主动调⽤ fsync，由操作系统决定，性能最好，但数据丢失的时间窗⼝不确定，依赖于操作系统的缓存
策略，可能会丢失⼤量数据。
可以通过配置⽂件中的 appendfsync 参数进⾏设置。
说说AOF的重写机制？
 
由于 AOF ⽂件会随着写操作的增加⽽不断增⻓，为了解决这个问题， Redis 提供了重写机制来对 AOF ⽂件进⾏压
缩和优化。
appendfsync everysec  # 每秒 fsync ⼀次
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 59 / 261

---

AOF 重写可以通过两种⽅式触发，第⼀种是⼿动执⾏ BGREWRITEAOF 命令，适⽤于需要⽴即减⼩AOF⽂件⼤⼩的
场景。
第⼆种是在 Redis 配置⽂件中设置⾃动重写参数，⽐如说 auto-aof-rewrite-percentage 和 auto-aof-
rewrite-min-size ，表示当 AOF ⽂件⼤⼩超过指定值时，⾃动触发重写。
AOF 重写的具体过程是怎样的？
 
Redis 在收到重写指令后，会创建⼀个⼦进程，并 fork ⼀份与⽗进程完全相同的数据副本，然后遍历内存中的所有
键值对，⽣成重建它们所需的最少命令。
⽐如说多个 RPUSH 命令可以合并为⼀个带有多个参数的 RPUSH；
⽐如说⼀个键被设置后⼜被删除，这个键的所有操作都不会被写⼊新 AOF。
⽐如说使⽤ SADD key member1 member2 member3 代替多个单独的 SADD key memberX 。
auto-aof-rewrite-percentage 100  # 默认值100，表示当前AOF⽂件⼤⼩相⽐上次重写后⼤⼩增⻓了多少百分⽐
时触发重写
auto-aof-rewrite-min-size 64mb  # 默认值64MB，表示AOF⽂件⾄少要达到这个⼤⼩才会考虑重写
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 60 / 261

---

⼦进程在执⾏ AOF 重写的同时，主进程可以继续处理来⾃客户端的命令。
为了保证数据⼀致性，Redis 使⽤了 AOF 重写缓冲区机制，主进程在执⾏写操作时，会将命令同时写⼊旧的 AOF 
⽂件和重写缓冲区。
等⼦进程完成重写后，会向主进程发送⼀个信号，主进程收到后将重写缓冲区中的命令追加到新的 AOF ⽂件中，
然后调⽤操作系统的 rename，将旧的 AOF ⽂件替换为新的 AOF ⽂件。
AOF 重写期间，Redis 服务器会处于特殊状态：
aof_child_pid 不为 0，表示有⼦进程在执⾏ AOF 重写
aof_rewrite_buf_blocks 链表不为空，存储 AOF 重写缓冲区内容
如果在配置⽂件中设置 no-appendfsync-on-rewrite 为 yes，那么重写期间可能会暂停 AOF ⽂件的 fsync 操作。
AOF ⽂件存储的是什么类型的数据？
 
AOF ⽂件存储的是 Redis 服务器接收到的写命令数据，以 Redis 协议格式保存。
这种格式的特点是，每个命令以*开头，后跟参数的数量，每个参数前⽤$ 符号，后跟参数字节⻓度，然后是参数
的实际内容。
主进程（fork）  
   │  
   ├─→ ⼦进程（⽣成新的 AOF ⽂件）  
   │       │  
   │       ├─→ 内存快照  
   │       ├─→ 写⼊临时 AOF ⽂件  
   │       ├─→ 通知主进程完成  
   │  
   ├─→ 主进程（追加缓冲区到新 AOF ⽂件）  
   ├─→ 替换旧 AOF ⽂件  
   ├─→ 重写完成
appendonly yes                # 开启AOF
appendfilename "appendonly.aof"  # AOF⽂件名
appendfsync everysec          # 写⼊磁盘策略
no-appendfsync-on-rewrite no  # 重写期间是否临时关闭fsync
auto-aof-rewrite-percentage 100   # AOF⽂件增⻓到原来多少百分⽐时触发重写
auto-aof-rewrite-min-size 64mb    # AOF⽂件最⼩多⼤时才允许重写
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 61 / 261

---

AOF重写期间命令可能会写⼊两次，会造成什么影响？
 
AOF 重写期间命令会同时写⼊现有AOF⽂件和重写缓冲区，这种机制是有意设计的，并不会导致数据重复或不⼀致
问题。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 62 / 261

---

因为新旧⽂件是分离的，现有命令写⼊当前 AOF ⽂件，重写缓冲区的命令最终写⼊新的 AOF ⽂件，完成后，新⽂
件通过原⼦性的 rename 操作替换旧⽂件。两个⽂件是完全分离的，不会导致同⼀个 AOF ⽂件中出现重复命令。
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：为什么 redis 快，淘汰策略 持久化
2. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下 Redis 的持久化⽅
式
3. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：Redis 的持久化⽅式？RDB 
和 AOF 的区别？Redis 宕机哪种恢复的⽐较快？
4. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：redis 持久化
5. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：redis持久化机制
6. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：Redis持久化⽅案
7. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：Redis的基本数据类型？Redis的持久化
呢？有何优缺点？
8. Java ⾯试指南（付费）收录的滴滴⾯经同学 3 ⽹约⻋后端开发⼀⾯原题：Redis持久化
9. Java ⾯试指南（付费）收录的快⼿⾯经同学 1 部⻔主站技术部⾯试原题：Redis数据的可靠性怎么保
证？AOF重写期间命令可能会写⼊两次，会造成什么影响？
memo：2025 年 5 ⽉ 4 ⽇修改⾄此，今天有球友发信息说把并发编程和 JVM 的⾯渣逆袭都打印成纸质版了，说实
话，这个封⾯的颜值我也很喜欢，哈哈。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 63 / 261

---

11.RDB 和 AOF 各⾃有什么优缺点？
 
RDB 通过 fork ⼦进程在特定时间点对内存数据进⾏全量备份，⽣成⼆进制格式的快照⽂件。其最⼤优势在于备份
恢复效率⾼，⽂件紧凑，恢复速度快，适合⼤规模数据的备份和迁移场景。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 64 / 261

---

对⽐项
RDB（快照）
AOF（命令⽇志）
数据完整性
 可能丢失⼏分钟数据
 最多丢 1 秒数据
恢复速度
 快（直接加载⼆进制快照）
 慢（逐条 replay）
⽂件⼤⼩
 ⼩（压缩后）
 ⼤（命令追加）
性能影响
 低（fork 后保存）
 较⾼（每次写都记录）
写⼊⽅式
定期全量写
每次写命令就记录
适⽤场景
冷备份，灾难恢复
实时持久化，数据安全
默认状态
默认启⽤
Redis 7 默认也启⽤
重写机制
⽆
有（BGREWRITEAOF）
混合⽀持
Redis 4.0 后⽀持结合使⽤（aof-use-rdb-preamble）
 
缺点是可能丢失两次快照期间的所有数据变更。
AOF 会记录每⼀条修改数据的写命令。这种⽇志追加的⽅式让 AOF 能够提供接近实时的数据备份，数据丢失⻛险
可以控制在 1 秒内甚⾄完全避免。
缺点是⽂件体积较⼤，恢复速度慢。
来个表格对⽐⼀下：
1. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：Redis 的持久化⽅式？RDB 
和 AOF 的区别？Redis 宕机哪种恢复的⽐较快？
12.RDB 和 AOF 如何选择？
 
在选择 Redis 持久化⽅案时，我会从业务需求和技术特性两个维度来考虑。
如果是缓存场景，可以接受⼀定程度的数据丢失，我会倾向于选择 RDB 或者完全不使⽤持久化。RDB 的快照⽅式
对性能影响⼩，⽽且恢复速度快，⾮常适合这类场景。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 65 / 261

---

但如果是处理订单或者⽀付这样的核⼼业务，数据丢失将造成严重后果，那么 AOF 就成为必然选择。通过配置每
秒同步⼀次，可以将潜在的数据丢失⻛险限制在可接受范围内。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 66 / 261

---

在实际的项⽬当中，我更偏向于使⽤ RDB + AOF 的混合模式。
1. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：什么时候⽤ rdb 什么时候⽤ aof
13.Redis如何恢复数据？
 
当 Redis 服务重启时，它会优先查找 AOF ⽂件，如果存在就通过重放其中的命令来恢复数据；如果不存在或未启
⽤ AOF，则会尝试加载 RDB ⽂件，直接将⼆进制数据载⼊内存来恢复。
如果 AOF ⽂件损坏的话，Redis 会尝试通过 redis-check-aof ⼯具来修复 AOF ⽂件，或者直接使⽤ --repair 
参数来修复。
虽然 Redis 还提供了 redis-check-rdb ⼯具来检查 RDB ⽂件的完整性，但它并不⽀持修复 RDB ⽂件，只能⽤来
验证⽂件的完整性。
1. Java ⾯试指南（付费）收录的美团⾯经同学 4 ⼀⾯⾯试原题：Redis 内存中数据丢失怎么解决
appendonly yes # 开启 AOF
appendfsync everysec # 每秒刷盘⼀次
aof-use-rdb-preamble yes # 开启混合持久化，重启时优先加载 RDB，RDB 作为冷备，AOF 作为实时同步
redis-check-aof --repair appendonly.aof
redis-check-rdb dump.rdb
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 67 / 261

---

memo：2025 年 5 ⽉ 5 ⽇修改⾄此，今天给球友修改简历时，碰到⼀个华科本硕的球友，985 ⾼校⼜+1，⽬前国
内的 985 ⾼校有 39 所，希望不久的将来，能全部集⻬。
14.
Redis 4.0 的混合持久化了解吗？
 
是的。
混合持久化结合了 RDB 和 AOF 两种⽅式的优点，解决了它们各⾃的不⾜。在 Redis 4.0 之前，我们要么⾯临 RDB 
可能丢失数据的⻛险，要么承受 AOF 恢复慢的问题，很难两全其美。
混合持久化的⼯作原理⾮常巧妙：在 AOF 重写期间，先以 RDB 格式将内存中的数据快照保存到 AOF ⽂件的开
头，再将重写期间的命令以 AOF 格式追加到⽂件末尾。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 68 / 261

---

这样，当需要恢复数据时，Redis 先加载 RDB 格式的数据来快速恢复⼤部分的数据，然后通过重放命令恢复最近
的数据，这样就能在保证数据完整性的同时，提升恢复速度。
如何设置持久化模式？
 
启⽤混合持久化的⽅式⾮常简单，只需要在配置⽂件中设置 aof-use-rdb-preamble yes 就可以了。
你在开发中是怎么配置 RDB 和 AOF 的？
 
对于⼤多数⽣产环境，我倾向于使⽤混合持久化⽅式，结合 RDB 和 AOF 的优点。
对于单纯的缓存场景，或者本地开发，我会只启⽤ RDB，关闭 AOF：
⽽对于⾦融类等⾼⼀致性的系统，我通常会在关键时间窗⼝动态将 appendfsync 设置为 always ：
aof-use-rdb-preamble yes
# 启⽤AOF
appendonly yes
# 使⽤混合持久化
aof-use-rdb-preamble yes
# 每秒同步⼀次AOF，平衡性能和安全性
appendfsync everysec
# AOF重写触发条件：⽂件增⻓100%且⾄少达到64MB
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
# RDB备份策略
save 900 1    # 15分钟内有1个修改
save 300 10   # 5分钟内有10个修改
save 60 10000 # 1分钟内有10000个修改
# 禁⽤AOF
appendonly no
# 较宽松的RDB策略
save 3600 1    # 1⼩时内有1个修改
save 300 100   # 5分钟内有100个修改
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 69 / 261

---

另外，对于⾼并发场景，应该设置no-appendfsync-on-rewrite yes ，避免 AOF 重写影响主进程性能；对于⼤
型实例，也应该设置 rdb-save-incremental-fsync yes 来减少⼤型 RDB 保存对性能的影响。
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：Redis 的持久化机制？
2. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：Redis 宕机哪种恢复的⽐较
快？
3. Java ⾯试指南（付费）收录的美团⾯经同学 18 成都到家⾯试原题：如何设置持久化模式
4. Java ⾯试指南（付费）收录的美团⾯经同学 4 ⼀⾯⾯试原题：业界使⽤哪⼀种数据持久化，两种持久化
⽅法的优缺点
5. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：两种 Redis持久化机制可以混
合使⽤吗
memo：2025 年 5 ⽉ 6 ⽇修改⾄此，今天在修改球友简历时，碰到⼀个东北⼤学硕合肥⼯业⼤学本的球友，真的
⾮常优秀，也希望⼤家能够通过星球这个平台彼此激励，共同进步。
⾼可⽤
 
15.主从复制了解吗？
 
# 启⽤AOF
appendonly yes
# 使⽤混合持久化
aof-use-rdb-preamble yes
# 每个命令都同步（谨慎使⽤，性能影响⼤）
# 通常我会在关键时间窗⼝动态修改为always
appendfsync always
# 更频繁的RDB快照
save 300 1     # 5分钟内有1个修改
save 60 100    # 1分钟内有100个修改
# AOF重写期间不fsync，AOF 重写期间，主进程不会对新写⼊的 AOF 缓冲区执⾏ fsync 操作（即不强制刷盘），⽽
是等重写结束后再统⼀刷盘。
no-appendfsync-on-rewrite yes
# RDB 快照保存时采⽤增量 fsync，即每写⼊⼀定量的数据就执⾏⼀次 fsync，将数据分批同步到磁盘。
rdb-save-incremental-fsync yes
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 70 / 261

---

主从复制允许从节点维护主节点的数据副本。在这种架构中，⼀个主节点可以连接多个从节点，从⽽形成⼀主多从
的结构。主节点负责处理写操作，从节点⾃动同步主节点的数据变更，并处理读请求，从⽽实现读写分离。
主从复制的主要作⽤是什么?
 
第⼀，主节点负责处理写请求，从节点负责处理读请求，从⽽实现读写分离，减轻主节点压⼒的同时提升系统的并
发能⼒。
第⼆，从节点可以作为主节点的数据备份，当主节点发⽣故障时，可以快速将从节点提升为新的主节点，从⽽保证
系统的⾼可⽤性。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 71 / 261

---

什么情况下会出现主从复制数据不⼀致？
 
Redis 的主从复制是异步进⾏的，因此在主节点宕机、⽹络波动或复制延迟较⾼时会出现从节点数据不同步的情
况。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 72 / 261

---

⽐如主节点写⼊数据后宕机，但从节点还未来得及复制，就会出现数据不⼀致。
另⼀个容易被忽视的因素是主节点内存压⼒。当主节点内存接近上限并启⽤了淘汰策略时，某些键可能被⾃动删
除，⽽这些删除操作如果未能及时同步，就会造成从节点保留了主节点已经不存在的数据。
时间线：→
客户端  →  向主节点 SET user:1 ⼆哥     →  主节点处理成功 
                            ↓
                          正准备推送给从节点（异步复制）... 但还没推送完 
                            ↓
                  —— 突然主节点宕机（机器死机、断⽹） 
 ——
                            ↓
          Sentinel 监测到故障，failover：将从节点提升为新主节点 
                            ↓
客户端继续请求：GET user:1 
→ 从节点返回：空 
（数据没同步过来）
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 73 / 261

---

主从复制数据不⼀致的解决⽅案有哪些？
 
⾸先是⽹络层⾯的优化，理想情况下，主从节点应该部署在同⼀个⽹络区域内，避免跨区域的⽹络延迟。
其次是配置层⾯的调整，⽐如说适当增⼤复制积压缓冲区的⼤⼩和存活时间，以便从节点重连后进⾏增量同步⽽不
是全量同步，以最⼤程度减少主从同步的延迟。
第三是引⼊监控和⾃动修复机制，定期检查主从节点的数据⼀致性。
⽐如说通过⽐较主从的 offset 差值判断从库是否落后。⼀旦超过设定阈值，就将从节点剔除，并重新进⾏全量同
步。
repl-backlog-size 1mb  # 默认值 1MB，表示主节点的复制缓冲区⼤⼩
repl-backlog-ttl 3600  # 默认值 3600 秒，表示主节点的复制缓冲区存活时间
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 74 / 261

---

1. Java ⾯试指南（付费）收录的得物⾯经同学 1 ⾯试原题：Redis 分布式，主从，⼀个节点挂掉怎么办
2. Java ⾯试指南（付费）收录的⼩⽶⾯经同学 F ⾯试原题：redis 的主从架构和主从哨兵区别
3. Java ⾯试指南（付费）收录的收钱吧⾯经同学 1 Java 后端⼀⾯⾯试原题：Redis解决单点故障主要靠什
么？主从模式⽤的是异步还是同步？
memo：2025 年 5 ⽉ 7 ⽇修改⾄此，今天在修改球友简历时，收到了球友对简历修改的认可：“现在这份简历应该
⽐较完美了”，完美这个词我觉得褒奖的有点多了，哈哈，不过我还是很开⼼的。
16.Redis主从有⼏种常⻅的拓扑结构？
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 75 / 261

---

主要有三种。
最基础的是⼀主⼀从，这种模式适合⼩型项⽬。⼀个主节点负责写⼊，⼀个从节点负责读和数据备份。这种结构虽
然简单，但维护成本低。
随着业务增⻓，读请求增多，可以考虑扩展为⼀主多从结构。主节点负责写⼊，多个从节点还可以分摊压⼒。
在跨地域部署场景中，树状主从结构可以有效降低主节点负载和需要传送给从节点的数据量。通过引⼊复制中间
层，从节点不仅可以复制主节点数据，同时可以作为其他从节点的主节点继续向下层复制。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 76 / 261

---

17.Redis的主从复制原理了解吗？
 
了解。
Redis 的主从复制是指通过异步复制将主节点的数据变更同步到从节点，从⽽实现数据备份和读写分离。这个过程
⼤致可以分为三个阶段：建⽴连接、同步数据和传播命令。
在建⽴连接阶段，从节点通过执⾏ replicaof 命令连接到主节点。连接建⽴后，从节点向主节点发送 psync 命
令，请求数据同步。这时主节点会为该从节点创建⼀个连接和复制缓冲区。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 77 / 261

---

同步数据阶段分为全量同步和增量同步。当从节点⾸次连接主节点时，会触发全量同步。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 78 / 261

---

在这个过程中，主节点会 fork ⼀个⼦进程⽣成 RDB ⽂件，同时将⽂件⽣成期间收到的写命令缓存到复制缓冲区。
然后将 RDB ⽂件发送给从节点，从节点清空⾃⼰的数据并加载这个 RDB ⽂件。等 RDB 传输完成后，主节点再将
缓存的写命令发送给从节点执⾏，确保数据完全⼀致。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 79 / 261

---

主从完成全量同步后，主要依靠传播命令阶段来保持数据的增量同步。主节点会将每次执⾏的写命令实时发送给所
有从节点。
Redis 2.8 版本后，主节点会为每个从节点维护⼀个复制积压缓冲区，⽤于存储最近的写命令。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 80 / 261

---

增量复制时，主节点会把要同步的写命令暂存⼀份到复制积压缓冲区。这样当从节点和主节点发⽣⽹络断连，从节
点重新连接后，可以从复制积压缓冲区中复制尚未同步的写命令。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 81 / 261

---

memo：2025 年 5 ⽉ 8 ⽇修改⾄此，今天有球友在星球⾥发帖说拿到了腾讯的实习 offer，真的要恭喜了。⾯
经，我看题⽬主要集中在技术派项⽬和MySQL、计算机⽹络的⼋股上。
18.详细说说全量同步和增量同步？
 
全量同步会将主节点的完整数据集传输给从节点，通常发⽣在从节点⾸次连接主节点时。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 82 / 261

---

此时，从节点发送 psync ? -1 命令请求同步。? 表示从节点没有主节点 ID，-1 表示没有偏移量。主节点收到
后会回复 FULLRESYNC 响应从节点。同时也会包含主库 runid 和复制偏移量 offset 两个参数。
然后 fork ⼀个⼦进程⽣成 RDB ⽂件，并将新的写命令存⼊复制缓冲区。
从库收到 RDB ⽂件后，清空旧数据并加载新的 RDB ⽂件。加载完成后，从节点会向主节点回复确认消息，主节点
再将复制缓冲区中的数据发送给从节点，确保从节点的数据与主节点⼀致。
全量同步的代价很⾼，因为完整的 RDB ⽂件在⽣成时会占⽤⼤量的 CPU 和磁盘 IO；在⽹络传输时还会消耗掉不
少带宽。
于是 Redis 在 2.8 版本后引⼊了增量同步的概念，⽬的是在断线重连后避免全量同步。
增量依赖三个关键要素：
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 83 / 261

---

①、复制偏移量：主从节点分别维护⼀个复制偏移量，记录传输的字节数。主节点每传输 N 个字节数据，⾃身的
复制偏移量就会增加 N；从节点每收到 N 个字节数据，也会相应增加⾃⼰的偏移量。
②、主节点 ID：每个主节点都有⼀个唯⼀ ID，即复制 ID，⽤于标识主节点的数据版本。当主节点发⽣重启或者⻆
⾊变化时，ID 会改变。
③、复制积压缓冲区：主节点维护的⼀个固定⻓度的先进先出队列，默认⼤⼩为 1M。主节点在向从节点发送命令
的同时，也会将命令写⼊这个缓冲区。
当从节点与主节点断开重连后，会发送 psync{runId}{offset} 命令，带上之前记录的主节点 ID 和复制偏移
量。
主节点收到这个命令后，会检查 runId 和 offset：
如果主节点 ID 与从节点提供的 runId 不匹配，说明主节点已经变化，必须进⾏全量同步。
如果 ID 匹配，主节点会查找从节点请求的偏移量之后的数据是否还在复制积压缓冲区。
如果在，只发送从该偏移量开始的增量数据，这就是增量同步；否则说明断线时间太⻓，积压缓冲区已经覆盖了这
部分数据，需要全量同步。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 84 / 261

---

增量同步的优势显⽽易⻅：只传输断线期间的命令数据，⼤⼤减少了⽹络传输量和主从节点的负载，从节点也不需
要清空重载数据，能更快地跟上主节点状态。
对于写⼊频繁或⽹络不稳定的环境，应该增⼤复制积压缓冲区的⼤⼩，确保短时间断线后能进⾏增量同步⽽不是全
量同步。
memo：2025 年 5 ⽉ 9 ⽇修改⾄此，今天在修改球友简历时，碰到⼀个河北⼤学硕东华理⼯⼤学本的球友，希望
这个⼤家庭能给⼤家带来更多的帮助和⽀持。
19.主从复制存在哪些问题呢？
 
Redis 主从复制的最⼤挑战来⾃于它的异步特性，主节点处理完写命令后会⽴即响应客户端，⽽不会等待从节点确
认，这就导致在某些情况下可能出现数据不⼀致。
repl-backlog-size 1mb  # 默认值 1MB，表示主节点的复制缓冲区⼤⼩
repl-backlog-ttl 3600  # 默认值 3600 秒，表示主节点的复制缓冲区存活时间
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 85 / 261

---

另⼀个常⻅问题是全量同步对系统的冲击。全量同步会占⽤⼤量的 CPU 和 IO 资源，尤其是在⼤数据量的情况下，
会导致主节点的性能下降。
脑裂问题了解吗？
 
在 Redis 的哨兵架构中，脑裂的典型表现为：主节点与哨兵、从节点之间的⽹络发⽣故障了，但与客户端的连接是
正常的，就会出现两个“主节点”同时对外提供服务。
哨兵认为主节点已经下线了，于是会将⼀个从节点选举为新的主节点。但原主节点并不知情，仍然在继续处理客户
端的请求。
等主节点⽹络恢复正常了，发现已经有新的主节点了，于是原主节点会⾃动降级为从节点。在降级过程中，它需要
与新主节点进⾏全量同步，此时原主节点的数据会被清空。导致客户端在原主节点故障期间写⼊的数据全部丢失。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 86 / 261

---

为了防⽌这种数据丢失，Redis 提供了 min-slaves-to-write 和 min-slaves-max-lag 参数。
这两个参数可以设置最少需要多少个从节点在线，以及从节点的最⼤延迟时间。
设置这两个参数后，如果主节点连接不到指定数量的从节点，或者从节点响应超时，主节点会拒绝写⼊请求，从⽽
避免脑裂期间的数据冲突。
具体来说，当⽹络分区发⽣，主节点与从节点、哨兵之间的连接断开，但主节点与客户端的连接正常时，由于主节
点⽆法再连接到任何从节点，或者延迟超过了设定值，⽐如说配置了min-slaves-to-write 1 ，主节点就会⾃动
拒绝所有写请求。
同时在⽹络的另⼀侧，哨兵会检测到主节点"下线"，选举⼀个从节点成为新的主节点。由于原主节点已经停⽌接受
写⼊，所以不会产⽣新的数据变更，等⽹络恢复后，即使原主节点降级为从节点并进⾏全量同步，也不会丢失⽹络
分区期间的写⼊数据，因为根本就没有新的写⼊发⽣。
1. Java ⾯试指南（付费）收录的同学 30 腾讯⾳乐⾯试原题：主从复制有什么缺点呢？redis的脑裂问题
memo：2025 年 5 ⽉ 10 ⽇今天把新项⽬的前置环境也配的七七⼋⼋了，还差⼀个 Kafka 的安装教程。⽇拱⼀
卒，争取秋招前给⼤家球友们⻅⾯。
# 设置主节点能进⾏数据同步的最少从节点数量
min-slaves-to-write 1
# 设置主从节点间进⾏数据同步时，从节点给主节点发送 ACK 消息的最⼤延迟（以秒为单位）
min-slaves-max-lag 10
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 87 / 261

---

20.Redis哨兵机制了解吗？
 
Redis 中的哨兵⽤于监控主从集群的运⾏状态，并在主节点故障时⾃动进⾏故障转移。
核⼼功能包括监控、通知和⾃动故障转移。哨兵会定期检查主从节点是否按预期⼯作，当检测到主节点故障时，就
在从节点中选举出⼀个新的主节点，并通知客户端连接到新的主节点。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 88 / 261

---

1. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 1 ⾯试原题：Redis 的哨兵机制了解吗？
21.Redis哨兵的⼯作原理知道吗？
 
哨兵的⼯作原理可以概括为 4 个关键步骤：定时监控、主观下线、领导者选举和故障转移。
⾸先，哨兵会定期向所有 Redis 节点发送 PING 命令来检测它们是否可达。如果在指定时间内没有收到回复，哨兵
会将该节点标记为“主观下线”。
当⼀个哨兵判断主节点主观下线后，会询问其他哨兵的意⻅，如果达到配置的法定⼈数，主节点会被标记为“客观
下线”。
# 监控的主节点信息 + 多少个哨兵同意才算宕机
sentinel monitor mymaster 127.0.0.1 6379 2
# 多久不响应就标记为“主观下线”
sentinel down-after-milliseconds mymaster 5000
# 故障转移超时时间
sentinel failover-timeout mymaster 60000
# 同时允许多少个从节点同步新主节点数据
sentinel parallel-syncs mymaster 1
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 89 / 261

---

然后开始故障转移，这个过程中，哨兵会先选举出⼀个领导者，领导者再从从节点中选择⼀个最适合的节点作为新
的主节点，选择标准包括复制偏移量、优先级等因素。
确定新主节点后，哨兵会向其发送 SLAVEOF NO ONE 命令使其升级为主节点，然后向其他从节点发送 SLAVEOF 命
令指向新主节点，最后通过发布/订阅机制通知客户端主节点已经发⽣变化。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 90 / 261

---

在实际部署中，为了保证哨兵机制的可靠性，通常建议⾄少部署三个哨兵节点，并且这些节点应分布在不同的物理
机器上，降低单点故障⻛险。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 91 / 261

---

同时，法定⼈数的设置也⾮常关键，⼀般建议设置为哨兵数量的⼀半加⼀，既能确保在少数哨兵故障时系统仍能正
常⼯作，⼜能避免⽹络分区导致的脑裂问题。
1. Java ⾯试指南（付费）收录的 OPPO ⾯经同学 1 ⾯试原题：Redis的Sentinel和Cluster怎么理解？说⼀
下⼤概原理
memo：贴⼀个读者对 Java 进阶之路的美赞吧，我也是⼈，也需要⼤家的情绪共鸣，哈哈，就让赞美多⼀点吧
22.Redis领导者选举了解吗？
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 92 / 261

---

Redis 使⽤ Raft 算法实现领导者选举，⽬的是在主节点故障时，选出⼀个哨兵来负责执⾏故障转移操作。
选举过程是这样的：
①、当⼀个哨兵确认主节点客观下线后，会向其他哨兵节点发送请求，表明希望由⾃⼰来执⾏主从切换，并让所有
其他哨兵进⾏投票。候选者会先给⾃⼰先投 1 票，然后等待其他哨兵节点的投票结果。
// sentinel.c中的sentinelAskMasterStateToOtherSentinels函数
void sentinelAskMasterStateToOtherSentinels(sentinelRedisInstance *master) {
    dictIterator *di;
    dictEntry *de;
    di = dictGetIterator(master->sentinels);
    while((de = dictNext(di)) != NULL) {
        sentinelRedisInstance *sentinel = dictGetVal(de);
        int retval;
        
        // 只有在进⼊领导者选举阶段才发送投票请求
        if (master->failover_state == SENTINEL_FAILOVER_STATE_SELECT_LEADER) {
            // 发送特殊的is-master-down-by-addr命令请求投票
            retval = redisAsyncCommand(sentinel->cc,
                sentinelReceiveVoteFromSentinel, sentinel,
                "SENTINEL is-master-down-by-addr %s %d %llu %s",
                master->addr->ip, master->addr->port,
                (unsigned long long)master->failover_epoch,
                // 这⾥发送⾃⼰的runid请求投票
                sentinelGetMyRunID());
        } else {
            // 否则只询问主节点状态，不请求投票
            retval = redisAsyncCommand(sentinel->cc,
                sentinelReceiveIsMasterDownReply, sentinel,
                "SENTINEL is-master-down-by-addr %s %d %llu *",
                master->addr->ip, master->addr->port,
                (unsigned long long)0);
        }
    }
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 93 / 261

---

②、收到请求的哨兵节点进⾏判断，如果候选者的⽇志和⾃⼰的⼀样新，任期号也⼩于⾃⼰，且之前没有投票过，
就会投同意票 Y。否则回复 N。
    dictReleaseIterator(di);
}
// sentinel.c中的sentinelCommand函数部分(处理SENTINEL命令)
// 处理is-master-down-by-addr命令
else if (!strcasecmp(c->argv[1]->ptr,"is-master-down-by-addr")) {
    /* SENTINEL IS-MASTER-DOWN-BY-ADDR <ip> <port> <current-epoch> <runid> */
    sentinelRedisInstance *ri;
    char *master_ip = c->argv[2]->ptr;
    int master_port = atoi(c->argv[3]->ptr);
    long long req_epoch = strtoull(c->argv[4]->ptr,NULL,10);
    char *req_runid = c->argv[5]->ptr;
    int isdown = 0;
    char *leader = "*";
    long long leader_epoch = -1;
    
    ri = sentinelGetMasterByAddress(master_ip, master_port);
    if (ri) {
        isdown = ri->flags & SRI_S_DOWN;
        
        // 判断是否是投票请求
        if (req_runid[0] != '*') {
            // 检查是否已经在当前配置纪元中投过票
            if (req_epoch > sentinel.current_epoch) {
                // 更新⾃⼰的配置纪元
                sentinel.current_epoch = req_epoch;
            }
            
            // 如果我们觉得主节点下线了，且在这个epoch还没投过票，则投票
            if (isdown && sentinel.current_epoch == req_epoch &&
                sentinel.leader_epoch < req_epoch)
            {
                // 记录投票信息
                sentinel.leader_epoch = req_epoch;
                sentinel.leader = sdsnew(req_runid);
                leader = req_runid;
                leader_epoch = req_epoch;
            }
        }
    }
    
    // 返回投票结果
    addReplyMultiBulkLen(c,3);
    addReplyLongLong(c, isdown);
    addReplyBulkCString(c, leader);
    addReplyLongLong(c, leader_epoch);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 94 / 261

---

③、候选者收到投票后会统计⾃⼰的得票数，如果获得了集群中超过半数节点的投票，它就会当选为领导者。
④、如果没有哨兵在这⼀轮投票中获得超过半数的选票，这次选举就会失败，然后进⾏下⼀轮的选举。为了防⽌⽆
限制的选举失败，每个哨兵都会有⼀个选举超时时间，且是随机的。
// sentinel.c中的sentinelReceiveVoteFromSentinel函数
void sentinelReceiveVoteFromSentinel(redisAsyncContext *c, void *reply, void *privdata) {
    sentinelRedisInstance *sentinel = privdata;
    sentinelRedisInstance *master = sentinel->master;
    redisReply *r = reply;
    char *leader = NULL;
    
    // 处理回复
    if (r->type == REDIS_REPLY_ARRAY && r->elements == 3) {
        // 解析回复中的leader信息
        if (r->element[1]->type == REDIS_REPLY_STRING)
            leader = r->element[1]->str;
        
        // 检查是否投给了我们
        if (leader && strcmp(leader, sentinelGetMyRunID()) == 0) {
            // 记录获得⼀票
            dictAdd(master->sentinels_voted, sdsnew(sentinel->runid), sentinel);
        }
    }
    
    // 检查是否获得多数票
    if (master->failover_state == SENTINEL_FAILOVER_STATE_SELECT_LEADER) {
        int voters = dictSize(master->sentinels) + 1; // +1是因为包括⾃⼰
        int votes = dictSize(master->sentinels_voted) + 1; // ⾃⼰也算⼀票
        
        // 如果获得多数票(⼤于⼀半)
        if (votes >= voters/2+1) {
            // 成为领导者，开始执⾏故障转移
            sentinelEvent(LL_WARNING, "+elected-leader", master, "%@");
            master->failover_state = SENTINEL_FAILOVER_STATE_FAILOVER_IN_PROGRESS;
            sentinelFailoverSelectSlave(master);
        }
    }
}
// sentinel.c中的sentinelFailoverSelectLeader函数
void sentinelFailoverSelectLeader(sentinelRedisInstance *master) {
    // 检查选举是否超时
    mstime_t election_timeout = SENTINEL_ELECTION_TIMEOUT * 2;
    if (mstime() - master->failover_start_time > election_timeout) {
        // 选举超时，重置状态
        sentinelEvent(LL_WARNING, "-failover-abort-timeout", master, "%@");
        sentinelAbortFailover(master);
        return;
    }
    
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 95 / 261

---

这⾥ SENTINEL_ELECTION_TIMEOUT_MIN 通常为 0，SENTINEL_ELECTION_TIMEOUT_MAX 通常为 2000 毫秒。
这样每个哨兵会在 0-2 秒的随机时间后开始选举，减少选举冲突。
推荐阅读：Raft算法的选主过程详解
1. Java ⾯试指南（付费）收录的8 后端开发秋招⼀⾯⾯试原题：raft主节点挂了怎么选从节点
memo：2025 年 5 ⽉ 12 ⽇修改⾄此，今天有球友发微信说拿到了三个⼤⼚的 offer，分别是蚂蚁、美团和腾讯，
真的是太优秀了呀。
    // ... 其他选举逻辑 ...
    
    // 如果没有⾜够票数且未超时，则继续等待
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 96 / 261

---

23.新的主节点是怎样被挑选出来的？
 
哨兵在挑选新的主节点时，⾮常精细化。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 97 / 261

---

⾸先，哨兵会对所有从节点进⾏⼀轮基础筛选，排除那些不满⾜基本条件的节点。⽐如说已下线的节点、⽹络连接
不稳定的节点，以及优先级设为 0 明确不参与挑选的节点。
// 第⼀轮筛选：排除不满⾜基本条件的从节点
for (int i = 0; i < numslaves; i++) {
    sentinelRedisInstance *slave = slaves[i];
    
    // 排除已下线的从节点
    if (slave->flags & (SRI_S_DOWN|SRI_O_DOWN)) continue;
    // 排除断开连接的从节点
    if (slave->link->disconnected) continue;
    // 排除近期（5秒内）断过连的从节点
    if (mstime() - slave->link->last_avail_time > 5000) continue;
    // 排除未建⽴主从复制的节点
    if (slave->slave_priority == 0) continue;
    
    // 找到第⼀个满⾜条件的从节点
    selected = i;
    break;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 98 / 261

---

然后，哨兵会对剩下的从节点进⾏排序，选出最合适的主节点。
排序的标准有三个：
①、从节点优先级： slave-priority 的值越⼩优先级越⾼，优先级为 0 的从节点不会被选中。
②、复制偏移量： 偏移量越⼤意味着从节点的数据越新，复制的越完整。
③、运⾏ ID： 如果优先级和偏移量都相同，就⽐较运⾏ ID 的字典序，字典序⼩的优先。
选出新主节点后，哨兵会向其发送 SLAVEOF NO ONE 命令将其提升为主节点。
之后，哨兵会等待新主节点的⻆⾊转换完成，通过发送 INFO 命令检查其⻆⾊是否已变为 master 来确认。确认成
功后，会更新所有从节点的复制⽬标，指向新的主节点。
memo：2025 年 5 ⽉ 13 ⽇，今天有球友发微信说拿到了携程的 offer，携程现在也是第⼆梯队的互联⽹⼤⼚了，
值得⼀⼿恭喜啊。
// sentinel.c中的compareSlaves函数
int compareSlaves(sentinelRedisInstance *a, sentinelRedisInstance *b) {
    // 1. ⾸先⽐较⽤户设置的优先级，值越⼩优先级越⾼
    if (a->slave_priority != b->slave_priority)
        return (a->slave_priority < b->slave_priority) ? 1 : 2;
        
    // 2. 如果优先级相同，⽐较复制偏移量，偏移量越⼤数据越新
    if (a->slave_repl_offset > b->slave_repl_offset) return 1;
    else if (a->slave_repl_offset < b->slave_repl_offset) return 2;
    
    // 3. 如果复制偏移量也相同，⽐较运⾏ID的字典序
    return (strcmp(a->runid, b->runid) < 0) ? 1 : 2;
}
// sentinel.c中的sentinelFailoverPromoteSlave函数
void sentinelFailoverPromoteSlave(sentinelRedisInstance *master) {
    // ... 选择最佳从节点的逻辑 ...
    
    // 向选中的从节点发送SLAVEOF NO ONE命令，使其成为主节点
    retval = redisAsyncCommand(slave->link->cc,
        sentinelReceivePromotionResponseFromSlave, master,
        "SLAVEOF NO ONE");
        
    // 更新状态
    master->promoted_slave = slave;
    slave->flags |= SRI_PROMOTED;
    
    // 记录⽇志
    sentinelEvent(LL_WARNING, "+promoted-slave", slave, "%@");
    sentinelEvent(LL_WARNING, "+failover-state-wait-promotion", master, "%@");
}
SLAVEOF new-master-ip new-master-port
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 99 / 261

---

24.Redis集群了解吗？
 
主从复制实现了读写分离和数据备份，哨兵机制实现了主节点故障时⾃动进⾏故障转移。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 100 / 261

---

集群架构是对前两种⽅案的进⼀步扩展和完善，通过数据分⽚解决 Redis 单机内存⼤⼩的限制，当⽤户基数从百万
增⻓到千万级别时，我们只需简单地向集群中添加节点，就能轻松应对不断增⻓的数据量和访问压⼒。
⽐如说我们可以将单实例模式下的数据平均分为 5 份，然后启动 5 个 Redis 实例，每个实例保存 5G 的数据，从⽽
实现集群化。
25.请详细说⼀说Redis Cluster？（补充）
 
2024 年 04 ⽉ 26 ⽇新增
Redis Cluster 是 Redis 官⽅提供的⼀种分布式集群解决⽅案。其核⼼理念是去中⼼化，采⽤ P2P 模式，没有中⼼
节点的概念。每个节点都保存着数据和整个集群的状态，节点之间通过 gossip 协议交换信息。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 101 / 261

---

在数据分⽚⽅⾯，Redis Cluster 使⽤哈希槽机制将整个集群划分为 16384 个单元。
例如，如果我们有 4 个 Redis 实例，那么每个实例会负责 4000 多个哈希槽。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 102 / 261

---

在计算哈希槽编号时，Redis Cluster 会通过 CRC16 算法先计算出键的哈希值，再对这个哈希值进⾏取模运算，得
到⼀个 0 到 16383 之间的整数。
这种⽅式可以将数据均匀地分布到各个节点上，避免数据倾斜的问题。
当需要存储或查询⼀个键值对时，Redis Cluster 会先计算这个键的哈希槽编号，然后根据哈希槽编号找到对应的
节点进⾏操作。
推荐阅读：Redis Cluster
1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：Redis 切⽚集群？数据
和实例之间的如何进⾏映射？
2. Java ⾯试指南（付费）收录的快⼿⾯经同学 1 部⻔主站技术部⾯试原题：Redis 的 cluster 集群如何实
现？
memo：2025 年 5 ⽉ 14 ⽇，今天有球友发微信说拿到了百度和美团的暑期实习 offer，果然五⽉也是⼀个开花结
果的季节。
slot = CRC16(key) mod 16384
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 103 / 261

---

26.集群中数据如何分区？
 
常⻅的数据分区有三种：节点取余、⼀致性哈希和哈希槽。
节点取余分区简单明了，通过计算键的哈希值，然后对节点数量取余，结果就是⽬标节点的索引。
target_node = hash(key) % N  // N为节点数量
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 104 / 261

---

缺点是增加⼀个新节点后，节点数量从 N 变为 N+1，⼏乎所有的取余结果都会改变，导致⼤部分缓存失效。
为了解决节点变化导致的⼤规模数据迁移问题，⼀致性哈希分区出现了：它将整个哈希值空间想象成⼀个环，节点
和数据都映射到这个环上。数据被分配到顺时针⽅向上遇到的第⼀个节点。
这种设计的巧妙之处在于，当节点数量变化时，只有部分数据需要重新分配。⽐如说我们从 5 个节点扩容到 8 个节
点，理论上只有约 3/8 的数据需要迁移，⼤⼤减轻了扩容时的系统压⼒。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 105 / 261

---

但⼀致性哈希仍然有⼀个问题：数据分布不均匀。⽐如说在上⾯的例⼦中，节点 1 和节点 2 的数据量差不多，但节
点 3 的数据量却远远⼩于它们。
Redis Cluster 的哈希槽分区在⼀致性哈希和节点取余的基础上，做了⼀些改进。
它将整个哈希值空间划分为 16384 个槽位，每个节点负责⼀部分槽，数据通过 CRC16 算法计算后对 16384 取
模，确定它属于哪个槽。
slot = CRC16(key) % 16384
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 106 / 261

---

假设系统中有 4 个节点，为其分配了 16 个槽(0-15)；
槽 0-3 位于节点 node1；
槽 4-7 位于节点 node2；
槽 8-11 位于节点 node3；
槽 12-15 位于节点 node4。
如果此时删除 node2 ，只需要将槽 4-7 重新分配即可，例如将槽 4-5 分配给 node1 ，槽 6 分配给 node3 ，槽 7 
分配给 node4 ，数据在节点上的分布仍然较为均衡。
如果此时增加 node5，也只需要将⼀部分槽分配给 node5 即可，⽐如说将槽 3、槽 7、槽 11、槽 15 迁移给 
node5，节点上的其他槽位保留。
因为槽的个数刚好是 2 的 14 次⽅，和 HashMap 中数组的⻓度必须是 2 的幂次⽅有着异曲同⼯之妙。它能保证扩
容后，⼤部分数据停留在扩容前的位置，只有少部分数据需要迁移到新的槽上。
1. Java ⾯试指南（付费）收录的⼩⽶暑期实习同学 E ⼀⾯⾯试原题：你知道 Redis 的⼀致性 hash 吗
2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 Java 后端技术⼀⾯⾯试原题：Redis 扩容之后，哈希
槽的位置是否发⽣变化？
3. Java ⾯试指南（付费）收录的字节跳动⾯经同学 8 Java 后端实习⼀⾯⾯试原题：redis 分⽚集群，如何
分⽚的，有什么好处
memo：2025 年 5 ⽉ 15 ⽇，今天有球友发微信说加了星球后，算⼀算，踩着点拿到了滴滴的实习 offer，我看了
⼀下时间线，也就⼀个⽉时间不到，真的太强了。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 107 / 261

---

⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 108 / 261

---

27.能说说 Redis 集群的原理吗？
 
Redis 集群的搭建始于节点的添加和握⼿。每个节点通过设置 cluster-enabled yes 来开启集群模式。然后通过 
CLUSTER MEET 进⾏握⼿，将对⽅添加到各⾃的节点列表中。
这个过程设计的⾮常精巧：节点 A 发送 MEET 消息，节点 B 回复 PONG 并发送 PING，节点 A 回复 PONG，于是
双向的通信链路就建⽴完成了。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 109 / 261

---

有趣的是，由于采⽤了 Gossip 协议，我们不需要让每对节点都执⾏握⼿。在⼀个多节点集群的部署中，仅需要让
第⼀个节点与其他节点握⼿，其余节点就能通过信息传播⾃动发现并连接彼此。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 110 / 261

---

握⼿完成后，可以通过 CLUSTER ADDSLOTS 命令为主节点分配哈希槽。当 16384 个槽全部分配完毕，集群正式进
⼊就绪状态。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 111 / 261

---

故障检测和恢复是保障 Redis 集群⾼可⽤的关键。每秒钟，节点会向⼀定数量的随机节点发送 PING 消息，当发现
某个节点⻓时间未响应 PING 消息，就会将其标记为主观下线。
当半数以上的主节点都认为某节点主观下线时，这个节点就会被标记为“客观下线”。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 112 / 261

---

如果下线的是主节点，它的从节点之⼀将被选举为新的主节点，接管原主节点负责的哈希槽。
部署 Redis 集群⾄少需要⼏个物理节点？
 
部署⼀个⽣产环境可⽤的 Redis 集群，从技术⻆度来说，⾄少需要 3 个物理节点。
这个最⼩节点数的设定并⾮ Redis 技术上的硬性要求，⽽是基于⾼可⽤原则的实践考量。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 113 / 261

---

从实践⻆度看，最经典的 Redis 集群配置是 3 主 3 从，共 6 个 Redis 实例。考虑到需要 3 个主节点和 3 个从节
点，并且每对主从不能在同⼀物理机上，那么⾄少需要 3 个物理节点，每个物理节点上运⾏ 1 个主节点和另⼀个主
节点的从节点。
物理节点1：主节点A + 从节点B'
物理节点2：主节点B + 从节点C'
物理节点3：主节点C + 从节点A'
这种交错部署⽅式可以确保任何⼀个物理节点故障时，最多只影响⼀个主节点和⼀个不同主节点的从节点。
memo：2025 年 5 ⽉ 16 ⽇，今天在修改简历的时候，碰到⼀个河南理⼯本科，郑州⼤学硕⼠的球友，也是希望
这个社群能够帮助到更多的同学，⽆论来⾃哪⾥，都能在这⾥找回那个渴望进步，渴望拿到优质 offer 的⾃⼰。
28.说说Redis集群的动态伸缩？
 
Redis 集群动态伸缩的核⼼机制是通过重新分配哈希槽实现的。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 114 / 261

---

当需要扩容时，⾸先通过 CLUSTER MEET 命令将新节点加⼊集群；然后使⽤ reshard 命令将部分哈希槽重新分配
给新节点。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 115 / 261

---

----这部分⾯试中可以不背start----
准备新的节点：
然后启动新的节点：
接下来，使⽤ CLUSTER MEET 命令将新节点加⼊集群：
检查新节点是否加⼊：
然后，重新分配哈希槽：
# redis.conf
port 6382
cluster-enabled yes
cluster-config-file nodes-6382.conf
cluster-node-timeout 5000
appendonly yes
redis-server /path/to/redis-6382.conf
redis-cli -p 6379 cluster meet 127.0.0.1 6382
redis-cli -p 6379 cluster nodes
redis-cli --cluster reshard 127.0.0.1:6379
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 116 / 261

---

在提示中输⼊要迁移的哈希槽范围。
检查检查槽分配情况：
验证集群的状态：
也可以直接⼀步到位：
----这部分⾯试中可以不背end----
缩容则是反向操作：先将要下线节点负责的所有槽迁移到其他节点，再通过 CLUSTER FORGET 命令将节点从集群
中移除。
整个伸缩过程⽀持在线操作，⽆需停机，得益于 Redis 集群的 MOVED 和 ASK 重定向机制。当客户端访问的键不
在当前节点时，会收到重定向响应，指引它连接到正确的节点。
MOVED 和 ASK 重定向的区别？
 
MOVED 重定向反映的是哈希槽的永久性变更。当客户端请求⼀个键，但键所在的槽不在当前节点时，节点会返回 
MOVED 响应，告诉客户端这个槽现在归属于哪个节点。通常发⽣在集群完成重新分⽚后，槽的分配关系已经稳
定。
# 输⼊要迁移的槽数量，⽐如 4096（平均分配的话，16384/4=4096）。
How many slots do you want to move (from 16384 total slots)? 4096
# 输⼊ 6382 节点的 ID（可通过 cluster nodes 命令查到）。
What is the receiving node ID? <6382的节点ID>
# 输⼊ all（表示从所有节点平均迁移）。
Source node IDs? all
# 输⼊ yes（表示确认迁移）。
Do you want to proceed with the proposed reshard plan (yes/no)? yes
redis-cli -p 6379 cluster slots
redis-cli -p 6382 cluster info
redis-cli --cluster reshard 127.0.0.1:6379 --cluster-from all --cluster-to <6382的节点ID> 
--cluster-slots 4096 --cluster-yes
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 117 / 261

---

⽐如说某个槽从节点 A 移动到节点 B 后，如果客户端仍向节点 A 请求该槽中的键，会收到 MOVED 响应，提示应
该连接节点 B。
ASK 重定向出现在槽迁移过程中，表示请求的键可能已经从源节点迁移到了⽬标节点，但迁移尚未完成。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 118 / 261

---

memo：2025 年 5 ⽉ 17 ⽇，今天有球友发微信说拿到了⼀个国企⼦公司的 Java 后端开发和⼀个⼩⽶安卓的 
offer，问我该怎么选择？
缓存设计
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 119 / 261

---

29.
什么是缓存击穿？
 
缓存击穿是指某个热点数据缓存过期时，⼤量请求就会穿透缓存直接访问数据库，导致数据库瞬间承受的压⼒巨
⼤。
解决缓存击穿有两种常⽤的策略：
第⼀种是加互斥锁。当缓存失效时，第⼀个访问的线程先获取锁并负责重建缓存，其他线程等待或重试。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 120 / 261

---

这种策略虽然会导致部分请求延迟，但实现起来相对简单。在技术派实战项⽬中，我们就使⽤了 Redisson 的分布
式锁来确保只有⼀个服务实例能更新缓存。
第⼆种是永不过期策略。缓存项本身不设置过期时间，也就是永不过期，但在缓存值中维护⼀个逻辑过期时间。当
缓存逻辑上过期时，返回旧值的同时，异步启动⼀个线程去更新缓存。
String cacheKey = "product::" + productId;
RLock lock = redissonClient.getLock("lock::" + productId);
if (lock.tryLock(10, TimeUnit.SECONDS)) {
    try {
        String result = cache.get(cacheKey);
        if (result == null) {
            result = database.queryProductById(productId);
            cache.set(cacheKey, result, 60 * 1000); // 设置缓存
        }
    } finally {
        lock.unlock();
    }
}
public String getData(String key) {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 121 / 261

---

memo：2025 年 5 ⽉ 18 ⽇修改⾄此，今天给球友改简历时，碰到⼀个⻄北⼯业⼤学的球友，这⼜是⼀所 985 院
校，希望这个社群能把所有的 985 院校集⻬，也希望去帮助到更多院校的同学，希望⼤家都能拿到⼀个满意的 
offer。
什么是缓存穿透？
 
缓存穿透是指查询的数据在缓存中没有命中，因为数据压根不存在，所以请求会直接落到数据库上。如果这种查询
⾮常频繁，就会给数据库造成很⼤的压⼒。
    CacheItem item = cache.get(key);
    
    if (item == null) {
        // 缓存不存在，同步加载
        String data = db.query(key);
        cache.set(key, new CacheItem(data, System.currentTimeMillis() + expireTime));
        return data;
    } else if (item.isLogicalExpired()) {
        // 逻辑过期，异步刷新
        asyncRefresh(key);
        // 返回旧数据
        return item.getData();
    }
    
    return item.getData();
}
// 异步刷新缓存
private void asyncRefresh(final String key) {
    threadPool.execute(() -> {
        // 重新查询数据库
        String newData = db.query(key);
        // 更新缓存
        cache.set(key, new CacheItem(newData, System.currentTimeMillis() + expireTime));
    });
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 122 / 261

---

缓存击穿是因为单个热点数据缓存失效导致的，⽽缓存穿透是因为查询的数据不存在，原因可能是⾃身的业务代码
有问题，或者是恶意攻击造成的，⽐如爬⾍。
常⽤的解决⽅案有两种：第⼀种是布隆过滤器，它是⼀种空间效率很⾼的数据结构，可以⽤来判断⼀个元素是否在
集合中。
我们可以将所有可能存在的数据哈希到布隆过滤器中，查询时先检查布隆过滤器，如果布隆过滤器认为该数据不存
在，就直接返回空；否则再去查询缓存，这样就可以避免⽆效的缓存查询。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 123 / 261

---

代码示例：
public String getData(String key) {
    // 缓存中不存在该key
    String cacheResult = cache.get(key);
    if (cacheResult != null) {
        return cacheResult;
    }
    
    // 布隆过滤器判断key是否可能存在
    if (!bloomFilter.mightContain(key)) {
        return null; // ⼀定不存在，直接返回
    }
    
    // 可能存在，查询数据库
    String dbResult = db.query(key);
    
    // 将结果放⼊缓存，包括空值
    cache.set(key, dbResult != null ? dbResult : "", expireTime);
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 124 / 261

---

布隆过滤器存在误判，即可能会认为某个数据存在，但实际上并不存在。但绝不会漏判，即如果布隆过滤器认为某
个数据不存在，那它⼀定不存在。因此它可以有效拦截不存在的数据查询，减轻数据库压⼒。
第⼆种是缓存空值。对于不存在的数据，我们将空值写⼊缓存，并设置⼀个合理的过期时间。这样下次相同的查询
就能直接从缓存返回，⽽不再访问数据库。
代码示例：
    
    return dbResult;
}
public String getData(String key) {
    String cacheResult = cache.get(key);
    
    // 缓存命中，包括空值
    if (cacheResult != null) {
        // 特殊值表示空结果
        if (cacheResult.equals("")) {
            return null;
        }
        return cacheResult;
    }
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 125 / 261

---

缓存空值的⽅法实现起来⽐较简单，但需要给空值设置⼀个合理的过期时间，以免数据库中新增了这些数据后，缓
存仍然返回空值。
在实际的项⽬当中，还需要在接⼝层⾯做⼀些处理，⽐如说对参数进⾏校验，拦截明显不合理的请求；或者对疑似
攻击的 IP 进⾏限流和封禁。
memo：2025 年 5 ⽉ 19 ⽇，今天有球友发微信说拿到了滴滴的测开实习 offer，⽬前还想继续找，问我该继续学
点什么，我的回复说，暑期能拿到 offer，秋招继续就⾏了，加上滴滴的实习经历就很硬核了。⼤家在准备暑期和
秋招的时候，也不要太焦虑，保持⼀个好的学习习惯，秋招没问题的。
    
    // 缓存未命中，查询数据库
    String dbResult = db.query(key);
    
    // 写⼊缓存，空值也缓存，但设置较短的过期时间
    int expireTime = dbResult == null ? EMPTY_EXPIRE_TIME : NORMAL_EXPIRE_TIME;
    cache.set(key, dbResult != null ? dbResult : "", expireTime);
    
    return dbResult;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 126 / 261

---

什么是缓存雪崩？
 
缓存雪崩是指在某⼀时间段，⼤量缓存同时失效或者缓存服务突然宕机了，导致⼤量请求直接涌向数据库，导致数
据库压⼒剧增，甚⾄引发系统崩溃的现象。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 127 / 261

---

缓存击穿是单个热点数据失效导致的，缓存穿透是因为请求不存在的数据，⽽缓存雪崩是因为⼤范围的缓存失效。
缓存雪崩主要有三种成因和应对策略。
第⼀种，⼤量缓存同时过期，解决⽅法是添加随机过期时间。
第⼆种，缓存服务崩溃，解决⽅法是使⽤⾼可⽤的缓存集群。
⽐如说使⽤ Redis Cluster 构建多节点集群，确保数据在多个节点上有备份，并且⽀持⾃动故障转移。
public void setCache(String key, String value) {
    // 基础过期时间，例如30分钟
    int baseExpireSeconds = 1800;
    // 增加随机过期时间，范围0-300秒
    int randomSeconds = new Random().nextInt(300);
    // 最终过期时间为基础时间加随机时间
    cache.set(key, value, baseExpireSeconds + randomSeconds);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 128 / 261

---

对于⼀些⾼频关键数据，可以配置本地缓存作为⼆级缓存，缓解 Redis 的压⼒。在技术派实战项⽬中，我们就采⽤
了多级缓存的策略，其中就包括使⽤本地缓存 Caffeine 来作为⼆级缓存，当 Redis 出现问题时⾃动切换到本地缓
存。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 129 / 261

---

这个过程称为“缓存降级”，保证 Redis 发⽣故障时，系统能够继续提供服务。
第三种，缓存服务正常但并发请求量超过了缓存服务的承载能⼒，这种情况下可以采⽤限流和降级措施。
1. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：缓存雪崩，如何解决
2. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下 缓存穿透、缓存击
穿、缓存雪崩
3. Java ⾯试指南（付费）收录的字节跳动同学 7 Java 后端实习⼀⾯的原题：Redis 宕机会不会对权限系统
有影响？
LoadingCache<String, UserPermissions> permissionsCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build(this::loadPermissionsFromRedis);
public UserPermissions loadPermissionsFromRedis(String userId) {
    try {
        return redisClient.getPermissions(userId);
    } catch (Exception ex) {
        // Redis 异常处理，尝试从本地缓存获取
        return permissionsCache.getIfPresent(userId);
    }
}
public String getData(String key) {
    try {
        // 尝试从缓存获取数据
        return cache.get(key);
    } catch (Exception e) {
        // 缓存服务异常，触发熔断
        if (circuitBreaker.shouldTrip()) {
            // 直接从数据库获取，并进⼊降级模式
            circuitBreaker.trip();
            return getFromDbDirectly(key);
        }
        throw e;
    }
}
private String getFromDbDirectly(String key) {
    // 实施限流保护
    if (!rateLimit.tryAcquire()) {
        // 超过限流阈值，返回兜底数据或默认值
        return getDefaultValue(key);
    }
    
    // 限流通过，从数据库查询
    return db.query(key);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 130 / 261

---

4. Java ⾯试指南（付费）收录的字节跳动同学 7 Java 后端实习⼀⾯的原题：说⼀下 Redis 雪崩、穿透、
击穿等场景的解决⽅案
5. Java ⾯试指南（付费）收录的⼩⽶同学 F ⾯试原题：缓存常⻅问题和解决⽅案（引申到多级缓存），多
级缓存（redis，nginx，本地缓存）的实现思路
6. Java ⾯试指南（付费）收录的TP联洲同学 5 Java 后端⼀⾯的原题：如何解决缓存穿透
7. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：如何理解缓存雪崩、缓存击穿和缓存
穿透？
memo：2025 年 5 ⽉ 20 ⽇，今天有球友发微信说项⽬⽤的技术派，⼋股背的⾯渣，春招拿到了四个 offer，其中
包括泰隆银⾏和交通银⾏，问我该怎么选择，说实话我看完后觉得挺难选的，
不过还是值得恭喜⼀⼿。⼤家在
准备春招的时候也不要着急，付出总会有回报的。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 131 / 261

---

30.
能说说布隆过滤器吗？
 
布隆过滤器是⼀种空间效率极⾼的概率性数据结构，⽤于快速判断⼀个元素是否在⼀个集合中。它的特点是能够以
极⼩的内存消耗，判断⼀个元素“⼀定不在集合中”或“可能在集合中”，常⽤来解决 Redis 缓存穿透的问题。
----这部分⾯试中可以不背start----
布隆过滤器的核⼼由⼀个很⻓的⼆进制向量和⼀系列哈希函数组成。
初始化的时候，创建⼀个⻓度为 m 的位数组，初始值全为 0，同时选择 k 个不同的哈希函数
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 132 / 261

---

当添加⼀个元素时，⽤ k 个哈希函数计算出 k 个哈希值，然后对 m 取模，得到 k 个位置，将这些位置的⼆进
制位都设为 1
当需要判断⼀个元素是否在集合中时，同样⽤ k 个哈希函数计算出 k 个位置，如果这些位置的⼆进制位有任
何⼀个为 0，该元素⼀定不在集合中；如果全部为 1，则该元素可能在集合中
----这部分⾯试中可以不背end----
布隆过滤器存在误判吗？
 
是的，布隆过滤器存在误判。它可能会错误地认为某个元素在集合中，⽽元素实际上并不在集合中。
public class BloomFilter<T> {
    private BitSet bitSet;
    private int bitSetSize;
    private int numberOfHashFunctions;
    
    public BloomFilter(double falsePositiveProbability, int expectedNumberOfElements) {
        // 根据预期元素数量和期望的误判率，计算最优的位数组⼤⼩和哈希函数个数
        this.bitSetSize = calculateOptimalBitSetSize(expectedNumberOfElements, 
falsePositiveProbability);
        this.numberOfHashFunctions = 
calculateOptimalNumberOfHashFunctions(expectedNumberOfElements, bitSetSize);
        this.bitSet = new BitSet(bitSetSize);
    }
    
    public void add(T element) {
        int[] hashes = createHashes(element);
        for (int hash : hashes) {
            bitSet.set(Math.abs(hash % bitSetSize), true);
        }
    }
    
    public boolean mightContain(T element) {
        int[] hashes = createHashes(element);
        for (int hash : hashes) {
            if (!bitSet.get(Math.abs(hash % bitSetSize))) {
                return false; // 如果任何⼀位为0，元素⼀定不存在
            }
        }
        return true; // 所有位都为1，元素可能存在
    }
    
    // 其他辅助⽅法，如计算哈希值，计算最优参数等
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 133 / 261

---

但如果布隆过滤器认为某个元素不存在于集合中，那么它⼀定不存在。
误判产⽣的原因是因为哈希冲突。在布隆过滤器中，多个不同的元素可能映射到相同的位置。随着向布隆过滤器中
添加的元素越来越多，位数组中的 1 也越来越多，发⽣哈希冲突的概率随之增加，误判率也就随之上升。
误判率取决于以下 3 个因素：
1. 位数组的⼤⼩（m）：m 决定了可以存储的标志位数量。如果位数组过⼩，那么哈希碰撞的⼏率就会增加，
从⽽导致更⾼的误判率。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 134 / 261

---

2. 哈希函数的数量（k）：k 决定了每个元素在位数组中标记的位数。哈希函数越多，碰撞的概率也会相应变
化。如果哈希函数太少，过滤器很快会变得不精确；如果太多，误判率也会升⾼，效率下降。
3. 存⼊的元素数量（n）：n 越多，哈希碰撞的⼏率越⼤，从⽽导致更⾼的误判率。
 
要降低误判率，可以增加位数组的⼤⼩或者减少插⼊的元素数量。
要彻底解决布隆过滤器的误判问题，可以在布隆过滤器返回"可能存在"时，再通过数据库进⾏⼆次确认。
布隆过滤器⽀持删除吗？
 
布隆过滤器并不⽀持删除操作，这是它的⼀个重要限制。
当我们添加⼀个元素时，会将位数组中的 k 个位置设置为 1。由于多个不同元素可能共享相同的位，如果我们尝试
删除⼀个元素，将其对应的 k 个位重置为 0，可能会错误地影响到其他元素的判断结果。
例如，元素 A 和元素 B 都将位置 5 设为 1，如果删除元素 A 时将位置 5 重置为 0，那么对元素 B 的查询就会产⽣
错误的"不存在"结果，这违背了布隆过滤器的基本特性。
如果想要实现删除操作，可以使⽤计数布隆过滤器，它在每个位置上存储⼀个计数器⽽不是单⼀的位。这样可以通
过减少计数器的值来实现删除操作，但会增加内存开销。
public class CountingBloomFilter<T> {
    private int[] counters;
    private int size;
    private int hashFunctions;
    
    public CountingBloomFilter(int size, int hashFunctions) {
        this.size = size;
        this.hashFunctions = hashFunctions;
        this.counters = new int[size];
    }
    
    public void add(T element) {
        int[] positions = getHashPositions(element);
        for (int position : positions) {
            counters[position]++;
        }
    }
    
    public void remove(T element) {
        int[] positions = getHashPositions(element);
        for (int position : positions) {
            if (counters[position] > 0) {
                counters[position]--;
            }
        }
    }
    
    public boolean mightContain(T element) {
        int[] positions = getHashPositions(element);
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 135 / 261

---

为什么不能⽤哈希表⽽是⽤布隆过滤器？
 
布隆过滤器最突出的优势是内存效率。
假如我们要判断 10 亿个⽤户 ID 是否曾经访问过特定⻚⾯，使⽤哈希表⾄少需要 10G 内存（每个 ID ⾄少需要8字
节），⽽使⽤布隆过滤器只需要 1.2G 内存。
1. Java ⾯试指南（付费）收录的字节跳动同学 7 Java 后端实习⼀⾯的原题：有了解过布隆过滤器吗？
2. Java ⾯试指南（付费）收录的TP联洲同学 5 Java 后端⼀⾯的原题：布隆过滤器原理，这种⽅式下5%的
错误率可接受？
3. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：布隆过滤器？布隆过滤器优点？为什么不能⽤
哈希表要⽤布隆过滤器？
4. Java ⾯试指南（付费）收录的理想汽⻋⾯经同学 2 ⼀⾯⾯试原题：追问：说明⼀下布隆过滤器
memo：2025 年 5 ⽉ 20 ⽇，今天有球友发贴说拿到了滴滴的暑期 offer，特意来感谢了⼀下⾯渣逆袭。
        for (int position : positions) {
            if (counters[position] == 0) {
                return false;
            }
        }
        return true;
    }
    
    private int[] getHashPositions(T element) {
        // 计算哈希位置的代码
    }
}
m ≈ -n*ln(p)/ln(2)² ≈ -10⁹*ln(0.01)/ln(2)² ≈ 9.6 billion bits ≈ 1.2GB
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 136 / 261

---

31.
如何保证缓存和数据库的数据⼀致性？
 
在技术派实战项⽬中，对于⽂章标签这种允许短暂不⼀致的数据，我会采⽤ Cache Aside + TTL 过期机制来保证缓
存和数据库的⼀致性。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 137 / 261

---

具体做法是读取时先查 Redis，未命中再查 MySQL，同时为缓存设置⼀个合理的过期时间；更新时先更新 
MySQL，再删除 Redis。
// 读取逻辑
public UserInfo getUser(String userId) {
    // 先查缓存
    UserInfo user = cache.get("user:" + userId);
    if (user != null) {
        return user;
    }
    
    // 缓存未命中，查数据库
    user = database.selectUser(userId);
    if (user != null) {
        // 放⼊缓存，设置合理的过期时间
        cache.set("user:" + userId, user, 3600);
    }
    
    return user;
}
// 更新逻辑
public void updateUser(UserInfo user) {
    // 先更新数据库
    database.updateUser(user);
    
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 138 / 261

---

这种⽅式简单有效，适⽤于读多写少的场景。TTL 过期时间也能够保证即使更新操作失败，未能及时删除缓存，过
期时间也能确保数据最终⼀致。
那再来说说为什么要删除缓存⽽不是更新缓存？
 
最初设计缓存策略时，我也考虑过直接更新缓存，但通过实践发现，删除缓存是更优的选择。
最主要的原因是在并发环境下，假设我们有两个并发的更新操作，如果采⽤更新缓存的策略，就可能出现这样的时
序问题：
操作 A 和操作 B 同时发⽣，A 先更新 MySQL 将值改为 10，B 后更新 MySQL 将值改为 11。但在缓存更新
时，可能 B 先执⾏将缓存设为 11，然后 A 才执⾏将缓存设为10。这样就会造成 MySQL 是 11 但 Redis 是 10 
的不⼀致状态。
⽽采⽤删除策略，⽆论 A 和 B 谁先删除缓存，后续的读取操作都会从 MySQL 获取最新值。
另外，相对⽽⾔，删除缓存的速度⽐更新缓存的速度快得多。
    // 删除缓存
    cache.delete("user:" + user.getId());
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 139 / 261

---

因为删除操作只是简单的 DEL 命令，⽽更新可能需要重新序列化整个对象再写⼊缓存。
那再说说为什么要先更新数据库，再删除缓存？
 
这个操作顺序的选择也是我在实际项⽬中踩过坑才深刻理解的。假设我们采⽤先删缓存再更新数据库的策略，在⾼
并发场景下就可能出现这样的问题：
线程 A 要更新⽤户信息，先删除了缓存
线程 B 恰好此时要读取该⽤户信息，发现缓存为空，于是查询数据库，此时还是旧值
线程 B 将查到的旧值重新放⼊缓存
线程 A 完成数据库更新
结果就是数据库是新的值，但缓存中还是旧值。
⽽采⽤先更新数据库再删缓存的策略，即使出现类似的并发情况，最坏的情况也只是短暂地从缓存中读取到了旧
值，但缓存删除后的请求会直接从数据库中获取最新值。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 140 / 261

---

另外，如果先删缓存再更新数据库，当数据库更新失败时，缓存已经被删除了。这会导致短期内所有读请求都会穿
透到数据库，对数据库造成额外的压⼒。
⽽先更新数据库再删缓存，如果数据库更新失败，缓存保持原状，系统仍然能继续正常提供服务。
memo：2025 年 5 ⽉ 22 ⽇，今天给球友修改简历时，碰到⼀个⻄北⼯业⼤学本、电⼦科技⼤学硕的球友，⼀下
⼦ 985 ⾼校⼜集⻬了两所。如果球友们在星球⾥有所收获，也请给学弟学妹们⼀个⼝碑，让⼤家都能因此受益，
拿到更好的 offer。
public void updateUser(User user) {
    try {
        // 先更新数据库
        database.updateUser(user);
        
        // 再删除缓存
        cache.delete("user:" + user.getId());
    } catch (DatabaseException e) {
        // 数据库更新失败，缓存保持原状，系统仍可正常提供服务
        log.error("Database update failed", e);
        throw e;
    } catch (CacheException e) {
        // 缓存删除失败，数据库已更新，数据会在TTL后⾃动⼀致
        log.warn("Cache deletion failed, will be eventually consistent", e);
        // 可以选择不抛异常，因为有TTL兜底
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 141 / 261

---

那假如对缓存数据库⼀致性要求很⾼，该怎么办呢？
 
当业务对缓存与数据库的⼀致性要求很⾼时，⽐如⽀付系统、库存管理等场景，我会采⽤多种策略来保证强⼀致
性。
第⼀种，引⼊消息队列来保证缓存最终被删除，⽐如说在数据库更新的事务中插⼊⼀条本地消息记录，事务提交后
异步发送给 MQ 进⾏缓存删除。
即使缓存删除失败，消息队列的重试机制也能保证最终⼀致性。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 142 / 261

---

第⼆种，使⽤ Canal 监听 MySQL 的 binlog，在数据更新时，将数据变更记录到消息队列中，消费者消息监听到变
更后去删除缓存。
这种⽅案的优势是完全解耦了业务代码和缓存维护逻辑。
@Transactional
public void updateUser(UserInfo user) {
    // 在事务中更新数据库
    database.updateUser(user);
    
    // 在同⼀事务中记录需要删除的缓存信息
    LocalMessage message = new LocalMessage("CACHE_DELETE", "user:" + user.getId());
    database.insertLocalMessage(message);
    // 显式发布事件，供监听器捕获
    eventPublisher.publishEvent(new UserUpdateEvent(this, "user:" + user.getId()));
}
// 事务提交后发送MQ消息
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void sendCacheDeleteMessage(UserUpdateEvent event) {
    messageQueue.send("cache-delete-topic", event.getCacheKey());
}
@CanalListener
public class CacheUpdateListener {
    
    @EventHandler
    public void handleUserUpdate(UserUpdateEvent event) {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 143 / 261

---

当然了，如果说业务⽐较简单，不需要上消息队列，可以通过延迟双删策略降低缓存和数据库不⼀致的时间窗⼝，
在第⼀次删除缓存之后，过⼀段时间之后，再次尝试删除缓存。
        // 从binlog事件中提取变更信息
        String userId = event.getUserId();
        
        // 发送缓存删除消息
        CacheDeleteMessage message = new CacheDeleteMessage();
        message.setCacheKey("user:" + userId);
        messageQueue.send("cache-delete-topic", message);
    }
}
// 消费者监听消息队列
@KafkaListener(topics = "cache-delete-topic")
public void handleCacheDeleteMessage(CacheDeleteMessage message) {
    // 删除缓存
    cache.delete(message.getCacheKey());
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 144 / 261

---

这种⽅式主要针对缓存不存在，但写⼊了脏数据的情况。
public void updateUser(UserInfo user) {
    // 第⼀次删除缓存，减少不⼀致时间窗⼝
    cache.delete("user:" + user.getId());
    
    // 更新数据库
    database.updateUser(user);
    
    // ⽴即删除缓存
    cache.delete("user:" + user.getId());
    
    // 延时删除，应对可能的并发读取
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 145 / 261

---

最后，⽆论采⽤哪种策略，最好为缓存设置⼀个合理的过期时间作为最后的保障。即使所有的主动删除机制都失败
了，TTL 也能确保数据最终达到⼀致：
这种⽅式虽然简单，但能确保即使出现极端情况，数据不⼀致的影响也是可控的。
1. Java ⾯试指南（付费）收录的华为⾯经同学 8 技术⼆⾯⾯试原题：怎样保证数据的最终⼀致性？
2. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：数据⼀致性问题
3. Java ⾯试指南（付费）收录的微众银⾏同学 1 Java 后端⼀⾯的原题：MySQL 和缓存⼀致性问题了解
吗？
4. Java ⾯试指南（付费）收录的美团⾯经同学 3 Java 后端技术⼀⾯⾯试原题：如何保证 redis 缓存与数据
库的⼀致性，为什么这么设计
5. Java ⾯试指南（付费）收录的⽐亚迪⾯经同学 12 Java 技术⾯试原题：怎么解决redis和mysql的缓存⼀
致性问题
6. Java ⾯试指南（付费）收录的字节跳动同学 17 后端技术⾯试原题：双写⼀致性怎么解决的
7. Java ⾯试指南（付费）收录的京东⾯经同学 9 ⾯试原题：redis的数据和缓存不⼀致应该处理
memo：2025 年 5 ⽉ 23 ⽇修改⾄此，今天在修改球友简历时，看到⼀条⾮常温暖的感谢信，球友说改完后的简
历，每⼀句都⽐之前的好很多，真的很欣慰，感觉⾃⼰的付出得到了回报。
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(1000); // 延时时间根据主从同步延迟调整
            cache.delete("user:" + user.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
}
// 根据数据的重要程度设置不同的TTL
public void setCache(String key, Object value, DataImportance importance) {
    int ttl;
    switch (importance) {
        case HIGH:      // 关键数据，短TTL
            ttl = 300;  // 5分钟
            break;
        case MEDIUM:    // ⼀般数据
            ttl = 1800; // 30分钟
            break;
        case LOW:       // 不太重要的数据
            ttl = 3600; // 1⼩时
            break;
    }
    
    cache.setWithTTL(key, value, ttl);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 146 / 261

---

32.如何保证本地缓存和分布式缓存的⼀致？
 
在技术派实战项⽬中，为了减轻 Redis 的负载压⼒，我⼜追加了⼀层本地缓存 Caffeine。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 147 / 261

---

为了保证 Caffeine 和 Redis 缓存的⼀致性，我采⽤的策略是当数据更新时，通过 Redis 的 pub/sub 机制向所有应
⽤实例发送缓存更新通知，收到通知后的实例⽴即更新或者删除本地缓存。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 148 / 261

---

@Service
public class CacheService {
    
    private final RedisTemplate redisTemplate;
    private final CaffeineCache localCache;
    
    public void updateData(String key, Object value) {
        // 更新数据库
        database.update(key, value);
        
        // 更新分布式缓存
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
        
        // 发送缓存更新通知
        CacheUpdateMessage message = new CacheUpdateMessage(key, "UPDATE", value);
        redisTemplate.convertAndSend("cache-update-channel", message);
    }
    
    @EventListener
    public void handleCacheUpdate(CacheUpdateMessage message) {
        if ("UPDATE".equals(message.getAction())) {
            localCache.put(message.getKey(), message.getValue());
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 149 / 261

---

考虑到消息可能丢失，我还会引⼊版本号机制作为补充。每次从 Redis 获取数据时添加⼀个最新的版本号。从本地
缓存获取数据前，先检查⾃⼰的版本号是否是最新的，如果发现版本落后，就主动从 Redis 中获取最新数据。
        } else if ("DELETE".equals(message.getAction())) {
            localCache.invalidate(message.getKey());
        }
    }
}
@Component
public class VersionBasedCacheManager {
    @Autowired
    private StringRedisTemplate redisTemplate;
    // 使⽤ Caffeine 构建本地缓存：最多 1000 项，写⼊后 10 分钟过期
    private final Cache<String, VersionedData> localCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
    /**
     * 获取缓存数据，优先使⽤本地缓存，必要时从 Redis 加载
     */
    public Object get(String key) {
        VersionedData cached = localCache.getIfPresent(key); // 从本地缓存取出
        // 从 Redis 获取版本号
        String versionStr = redisTemplate.opsForValue().get(key + ":version");
        // 如果 Redis 中没找到版本号，说明可能数据已失效，强制刷新
        if (versionStr == null) {
            return loadAndCache(key);
        }
        long remoteVersion = Long.parseLong(versionStr);
        // 如果本地没有缓存，或版本落后于 Redis，强制刷新
        if (cached == null || cached.getVersion() < remoteVersion) {
            return loadAndCache(key);
        }
        // 命中本地缓存且版本最新，直接返回
        return cached.getData();
    }
    /**
     * 从 Redis 加载数据和版本，并写⼊本地缓存
     */
    private Object loadAndCache(String key) {
        Object data = redisTemplate.opsForValue().get(key);
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 150 / 261

---

如果在项⽬中多个地⽅都要使⽤到⼆级缓存的逻辑，如何设计这⼀块？
 
我的思路是将⼆级缓存抽象成⼀个统⼀的组件。设计⼀个 CacheManager 作为核⼼⼊⼝，提供 get、put、evict 等
基本操作，执⾏先查本地缓存，再查分布式缓存，最后查数据库的完整流程。
        String versionStr = redisTemplate.opsForValue().get(key + ":version");
        if (data != null && versionStr != null) {
            long version = Long.parseLong(versionStr);
            localCache.put(key, new VersionedData(data, version));
        }
        return data;
    }
}
public class CacheManager {
    private final LocalCache localCache;
    private final RedisCache redisCache;
    private final Database database;
    public CacheManager(LocalCache localCache, RedisCache redisCache, Database database) 
{
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.database = database;
    }
    public Object get(String key) {
        // 先查本地缓存
        Object value = localCache.get(key);
        if (value != null) {
            return value;
        }
        // 再查分布式缓存
        value = redisCache.get(key);
        if (value != null) {
            // 更新本地缓存
            localCache.put(key, value);
            return value;
        }
        // 最后查数据库
        value = database.get(key);
        if (value != null) {
            // 更新分布式缓存和本地缓存
            redisCache.put(key, value);
            localCache.put(key, value);
        }
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 151 / 261

---

本地缓存和 Redis 的区别了解吗？
 
Redis 可以部署在多个节点上，⽀持数据分⽚、主从复制和集群。⽽本地缓存只能在单个服务器上使⽤。
对于读取频率极⾼、数据相对稳定、允许短暂不⼀致的数据，我优先选择本地缓存。⽐如系统配置信息、⽤户权限
数据、商品分类信息等。
⽽对于需要实时同步、数据变化频繁、多个服务需要共享的数据，我会选择 Redis。⽐如⽤户会话信息、购物⻋数
据、实时统计信息等。
1. Java ⾯试指南（付费）收录的字节跳动同学 7 Java 后端实习⼀⾯的原题：怎么保证⼆级缓存和 Redis 
缓存的数据⼀致性？
2. Java ⾯试指南（付费）收录的华为⾯经同学 11 ⾯试原题：使⽤的 guava cache 和 redis 是如何组合使
⽤的？如果在项⽬中多个地⽅都要使⽤到⼆级缓存的逻辑，如何设计这⼀块？
3. Java ⾯试指南（付费）收录的去哪⼉同学 1 技术⼆⾯的原题：redis 和本地缓存的区别，哪个效率⾼
4. Java ⾯试指南（付费）收录的拼多多⾯经同学 8 ⼀⾯⾯试原题：缓存⼀致性如何保证
33.什么是热Key？
 
所谓的热 Key，就是指在很短时间内被频繁访问的键。⽐如电商⼤促期间爆款商品的详情信息，流量明星爆⽠时的
个⼈资料、热⻔话题等，都可能成为热Key。
由于 Redis 是单线程模型，⼤量请求集中到同⼀个键会导致该 Redis 节点的 CPU 使⽤率飙升，响应时间变⻓。
在 Redis 集群环境下，热Key 还会导致数据分布不均衡，某个节点承受的压⼒过⼤⽽其他节点相对空闲。
        
        return value;
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 152 / 261

---

更严重的情况是，当热Key 过期或被误删时，会引发缓存击穿问题。
那怎么监控热Key 呢？
 
临时的⽅案可以使⽤ redis-cli --hotkeys 命令来监控 Redis 中的热 Key。
redis-cli -h <address> -p <port> -a<password> — hotkey
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 153 / 261

---

或者在访问缓存时，在本地维护⼀个计数器，当某个键的访问次数在⼀分钟内超过设定阈值，就将其标记为热
Key。
34.那怎么处理热Key 呢？
 
最有效的解决⽅法是增加本地缓存，将热 Key 缓存到本地内存中，这样请求就不需要访问 Redis 了。
@Component
public class HotKeyDetector {
    private final ConcurrentHashMap<String, AtomicLong> accessCounter = new 
ConcurrentHashMap<>();
    private final int HOT_KEY_THRESHOLD = 1000;
    
    public boolean isHotKey(String key) {
        long count = accessCounter.computeIfAbsent(key, k -> new AtomicLong(0))
                                  .incrementAndGet();
        return count > HOT_KEY_THRESHOLD;
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 154 / 261

---

对于⼀些特别热的 Key，可以将其拆分成多个⼦ Key，然后随机分布到不同的 Redis 节点上。⽐如将 
hot_product:12345 拆分成 hot_product:12345:1 、hot_product:12345:2 等多个副本，读取时随机选择其
中⼀个。
public String getHotData(String key) {
    if (isHotKey(key)) {
        // 随机选择⼀个副本
        int replica = ThreadLocalRandom.current().nextInt(HOT_KEY_REPLICAS);
        return redis.get(key + ":" + replica);
    }
    return redis.get(key);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 155 / 261

---

35.怎么处理⼤ Key 呢？
 
⼤Key 是指占⽤内存空间较⼤的缓存键，⽐如超过 10M 的键值对。常⻅的⼤Key 类型包括：包含⼤量元素的 
List、Set、Hash 结构，存储⼤⽂件的 String 类型，以及包含复杂嵌套对象的 JSON 数据等。
在内存有限的情况下，可能导致 Redis 内存不⾜。另外，⼤Key 还会导致主从复制同步延迟，甚⾄引发⽹络拥塞。
可以通过 redis-cli --bigkeys 命令来监控 Redis 中的⼤ Key。
或者编写脚本进⾏全量扫描：
@Component
public class BigKeyScanner {
    
    private final RedisTemplate redisTemplate;
    private final int BIG_KEY_THRESHOLD = 1024 * 1024; // 1MB
    
    public List<BigKeyInfo> scanBigKeys() {
        List<BigKeyInfo> bigKeys = new ArrayList<>();
        
        // 使⽤SCAN命令遍历所有键
        ScanOptions options = ScanOptions.scanOptions().count(1000).build();
        Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
            connection -> connection.scan(options)
        );
        
        while (cursor.hasNext()) {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 156 / 261

---

对于⼤ Key 问题，最根本的解决⽅案是拆分⼤ Key，将其拆分成多个⼩ Key 存储。⽐如将⼀个包含⼤量⽤户信息
的 Hash 拆分成多个⼩ Hash。
            String key = new String(cursor.next());
            long memory = getKeyMemoryUsage(key);
            
            if (memory > BIG_KEY_THRESHOLD) {
                bigKeys.add(new BigKeyInfo(key, memory, getKeyType(key)));
            }
        }
        
        return bigKeys;
    }
    
    private long getKeyMemoryUsage(String key) {
        // 使⽤MEMORY USAGE命令获取键的内存占⽤
        return redisTemplate.execute((RedisCallback<Long>) connection -> 
            connection.memoryUsage(key.getBytes())
        );
    }
}  
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 157 / 261

---

另外，对于 JSON 数据，可以进⾏ Gzip 压缩后再存储，虽然会增加⼀些 CPU 开销，但在内存敏感的场景在是值得
的。
推荐阅读：
阿⾥：发现并处理 Redis 的⼤ Key 和热 Key
董宗磊：Redis 热 Key 发现以及解决办法
1. Java ⾯试指南（付费）收录的华为 OD 的⾯试中出现过该题：讲⼀讲 Redis 的热 Key 和⼤ Key
memo：2025 年 5 ⽉ 24 ⽇，今天球友发私信说，拿到了荣耀通软的实习 offer，恭喜他！
public void splitBigKey(String bigKey) {
    Map<String, String> bigData = redisTemplate.opsForHash().entries(bigKey);
    
    // 将⼤ Key 拆分成多个⼩ Key
    for (Map.Entry<String, String> entry : bigData.entrySet()) {
        String smallKey = bigKey + ":" + entry.getKey();
        redisTemplate.opsForValue().set(smallKey, entry.getValue());
    }
    
    // 删除原始⼤ Key
    redisTemplate.delete(bigKey);
}
public void setCompressedData(String key, Object data) {
    try {
        String json = objectMapper.writeValueAsString(data);
        byte[] compressed = compress(json.getBytes());
        redisTemplate.opsForValue().set(key, compressed);
    } catch (Exception e) {
        log.error("Failed to compress data", e);
    }
}
private byte[] compress(byte[] data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
        gzip.write(data);
    }
    return out.toByteArray();
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 158 / 261

---

36.缓存预热怎么做呢？
 
缓存预热是指在系统启动或者特定时间点，提前将热点数据加载到缓存中，避免冷启动时⼤量请求直接打到数据
库。
缓存预热的⽅法有多种，在技术派实战项⽬中，我会在项⽬启动时将热⻔⽂章提前加载到 Redis 中，在每天凌晨定
时将最新的站点地图更新到 Redis中，以确保⽤户在第⼀次访问时就能获取到缓存数据，从⽽减轻数据库的压⼒。
/**
 * 采⽤定时器⽅案，每天5:15分刷新站点地图，确保数据的⼀致性
 */
@Scheduled(cron = "0 15 5 * * ?")
public void autoRefreshCache() {
    log.info("开始刷新sitemap.xml的url地址，避免出现数据不⼀致问题!");
    refreshSitemap();
    log.info("刷新完成！");
}
@Override
public void refreshSitemap() {
    initSiteMap();
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 159 / 261

---

1. Java ⾯试指南（付费）收录的字节跳动⾯经同学 1 技术⼆⾯⾯试原题：什么是缓存预热？如何解决？
37.⽆底洞问题听说过吗？如何解决？
 
⽆底洞问题的核⼼在于，随着缓存节点数量的增加，虽然总的存储容量和理论吞吐量都在增⻓，但是单个请求的响
应时间反⽽变⻓了。
这个问题的根本原因是⽹络通信开销的增加。当节点数量从⼏⼗个增⻓到⼏千个时，客户端需要与更多的节点进⾏
通信。
其次就是数据分布的碎⽚化。随着节点增多，数据分散得更加细碎，原本可以在⼀个节点获取的相关数据，现在可
能分散在多个节点上。
针对这个问题，可以采取以下⼏种解决⽅案：
第⼀，可以将同⼀节点的多个请求合并成⼀个批量请求，减少⽹络往返次数。
}
private synchronized void initSiteMap() {
    long lastId = 0L;
    RedisClient.del(SITE_MAP_CACHE_KEY);
    while (true) {
        List<SimpleArticleDTO> list = 
articleDao.getBaseMapper().listArticlesOrderById(lastId, SCAN_SIZE);
        // 刷新站点地图信息，放到 Redis 当中
        Map<String, Long> map = list.stream().collect(Collectors.toMap(s -> 
String.valueOf(s.getId()), s -> s.getCreateTime().getTime(), (a, b) -> a));
        RedisClient.hMSet(SITE_MAP_CACHE_KEY, map);
        if (list.size() < SCAN_SIZE) {
            break;
        }
        lastId = list.get(list.size() - 1).getId();
    }
}
public Map<String, Object> batchGet(List<String> keys) {
    // 按节点分组keys
    Map<String, List<String>> nodeKeysMap = groupKeysByNode(keys);
    Map<String, Object> results = new ConcurrentHashMap<>();
    
    // 并发访问各个节点
    List<CompletableFuture<Void>> futures = nodeKeysMap.entrySet().stream()
        .map(entry -> CompletableFuture.runAsync(() -> {
            String node = entry.getKey();
            List<String> nodeKeys = entry.getValue();
            
            // 批量获取该节点的数据
            Map<String, Object> nodeResults = getFromNode(node, nodeKeys);
            results.putAll(nodeResults);
        }))
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 160 / 261

---

第⼆，可以使⽤⼀致性哈希算法来优化数据分布，减少数据迁移和重分布的开销。
Redis 运维
 
38.Redis 报内存不⾜怎么处理？
 
Redis 报内存不⾜时，通常是因为 Redis 占⽤的物理内存已经接近或者超过了配置的最⼤内存限制。这时可以采取
以下⼏种步骤来处理：
第⼀，使⽤ INFO memory 命令查看 Redis 的内存使⽤情况，看看是否真的达到了最⼤内存限制。
        .collect(Collectors.toList());
    
    // 等待所有请求完成
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    return results;
}
public class LocalityAwareSharding {
    
    public String getNodeForKey(String key, String category) {
        // 相同类别的数据尽量分配到相同节点
        String shardKey = category + ":" + (key.hashCode() % SHARDS_PER_CATEGORY);
        return consistentHash.getNode(shardKey);
    }
    
    // ⽤户相关数据尽量在同⼀个节点
    public String getUserDataNode(String userId) {
        return "user_cluster_" + (userId.hashCode() % USER_CLUSTERS);
    }
}
redis-cli INFO memory
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 161 / 261

---

第⼆，如果服务器还有可⽤内存的话，修改 redis.conf 中的 maxmemory 参数，增加 Redis 的最⼤内存限制。
⽐如将最⼤内存设置为 8GB：
第三，修改 maxmemory-policy 参数来调整内存淘汰策略。⽐如可以选择 allkeys-lru 策略，让 Redis ⾃动删
除最近最少使⽤的键。
memo：2025 年 5 ⽉ 25 ⽇修改⾄此，今天在修改球友简历时，碰到⼀个⻄安交通⼤学本、上海交通⼤学硕的球
友，985 本硕学历真的⾮常顶了，我会竭尽所能去帮助他，在秋招中斩获⼀个 SSP offer，冲！
maxmemory 8gb
maxmemory-policy allkeys-lru
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 162 / 261

---

39.Redis key过期策略有哪些？
 
Redis 主要采⽤了两种过期删除策略来保证过期的 key 能够被及时删除，包括惰性删除和定期删除。
惰性删除是最基本的策略，当客户端访问⼀个 key 时，Redis 会检查该 key 是否已过期，如果过期就会⽴即删除并
返回 nil。
这种策略的优点是不会有额外的 CPU 开销，只在访问 key 时才检查。但问题是如果⼀个过期的 key 永远不被访
问，它就会⼀直占⽤内存。
// 模拟惰性删除的逻辑
public Object get(String key) {
    RedisKey redisKey = getKeyFromMemory(key);
    
    if (redisKey != null && isExpired(redisKey)) {
        // key已过期，删除并返回null
        deleteKey(key);
        return null;
    }
    
    return redisKey != null ? redisKey.getValue() : null;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 163 / 261

---

于是就有了定期删除策略，Redis 会定期随机选择⼀些设置了过期时间的 key 进⾏检查，删除其中已过期的 key。
这个过程默认每秒执⾏ 10 次，每次随机选择 20 个 key 进⾏检查。
----这部分⾯试中可以不背 start----
可以通过 config get hz 命令查看 Redis 内部定时任务的频率。
hz 的值为“10”意味着 Redis 每秒执⾏ 10 次定时任务 。可以通过 CONFIG SET hz 20 进⾏调整。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 164 / 261

---

----这部分⾯试中可以不背 end----
1. Java ⾯试指南（付费）收录的腾讯⾯经同学 22 暑期实习⼀⾯⾯试原题：Redis key 删除策略
2. Java ⾯试指南（付费）收录的去哪⼉⾯经同学 1 技术 2 ⾯⾯试原题：redis 内存淘汰和过期策略
3. Java ⾯试指南（付费）收录的京东⾯经同学 5 Java 后端技术⼀⾯⾯试原题：redis key过期策略
40.
Redis有哪些内存淘汰策略？
 
当内存使⽤接近 maxmemory 限制时，Redis 会依据内存淘汰策略来决定删除哪些 key 以缓解内存压⼒。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 165 / 261

---

常⽤的内存淘汰策略有⼋种，分别是默认的 noeviction，内存不⾜时不会删除任何 key，直接返回错误信息，⽣产
环境下基本上不会使⽤。
然后是针对所有 key 的 allkeys-lru、allkeys-lfu 和 allkeys-random。lru 会删除最近最少使⽤的 key，在纯缓存场
景中最常⽤，能⾃动保留热点数据；lfu 会删除访问频率最低的 key，更适合⻓期运⾏的系统；random 会随机删
除⼀些 key，⼀般不推荐使⽤。
其次是针对设置了过期时间的 key，有 volatile-lru、volatile-lfu、volatile-ttl 和 volatile-random。
lru 在混合存储场景中经常使⽤。
lfu 适合需要保护某些重要数据不被淘汰的场景；ttl 优先删除即将过期的 key，在⽤户会话管理系统中推荐使⽤；
random 仍然很少⽤。
1. Java ⾯试指南（付费）收录的⼩⽶春招同学 K ⼀⾯⾯试原题：为什么 redis 快，淘汰策略 持久化
2. Java ⾯试指南（付费）收录的去哪⼉⾯经同学 1 技术 2 ⾯⾯试原题：redis 内存淘汰和过期策略
3. Java ⾯试指南（付费）收录的作业帮⾯经同学 1 Java 后端⼀⾯⾯试原题：redis内存淘汰策略
41.LRU 和 LFU 的区别是什么？
 
LRU 是 Least Recently Used 的缩写，基于时间维度，淘汰最近最少访问的键。
LFU 是 Least Frequently Used 的缩写，基于次数维度，淘汰访问频率最低的键。
假设缓存中有三个数据 A、B、C，在 LRU 场景下，如果访问顺序是 A→B→C→A，那么此时的 LRU 顺序是
B→C→A，如果需要淘汰，会先删除 B。
但在 LFU 场景下，如果 A 被访问了 5 次，B 被访问了 2 次，C 被访问了 1 次，那么⽆论最近的访问顺序如何，都
会优先淘汰 C，因为它的访问频率最低。
LRU 更适合有明显时间局部性的场景，⽐如在新闻⽹站中，⽤户更关⼼最新的新闻，⽽昨天的新闻访问量会急剧下
降。这种情况下，LRU 能很好地保留⽤户当前关⼼的热点内容。
LFU 则更适合有⻓期访问模式的场景，更强调“热度”，⽐如在电商平台中，某些商品可能⻓期保持热销状态，即使
它们的访问时间间隔较⻓，但由于访问频率⾼，LFU 会优先保留这些商品的信息。
@Service
public class HybridStorageService {
    
    // 重要数据不设置过期时间，临时数据设置过期时间
    public void storeData(String key, Object data, DataImportance importance) {
        if (importance == DataImportance.HIGH) {
            // 重要数据不设置过期时间，在volatile-*策略下不会被淘汰
            redisTemplate.opsForValue().set(key, data);
        } else {
            // 临时数据设置过期时间，可以被volatile-*策略淘汰
            redisTemplate.opsForValue().set(key, data, Duration.ofHours(1));
        }
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 166 / 261

---

1. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：redis内存淘汰机制  延伸到LRU   
LFU
memo：2025 年 5 ⽉ 27 ⽇，今天球友发私信说，拿到了哈啰和得物的实习 offer，恭喜他！
 还特意感谢了⼀
下之前对他简历的修改和学习上的建议。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 167 / 261

---

42.Redis发⽣阻塞了怎么解决？
 
Redis 发⽣阻塞在⽣产环境中是⽐较严重的问题，当发现 Redis 变慢时，我会先通过 monitor 命令查看当前正在执
⾏的命令，或者使⽤ slowlog 命令查看慢查询⽇志。
通常情况下，⼤Key 是导致 Redis 阻塞的主要原因之⼀。⽐如说直接 DEL ⼀个包含⼏百万个元素的 Set，就会导致 
Redis 阻塞⼏秒钟甚⾄更久。
这时候可以⽤ UNLINK 命令替代 DEL 来异步删除，避免阻塞主线程。
对于⾮常⼤的集合，可以使⽤ SCAN 命令分批删除。
# 查看当前正在执⾏的命令
redis-cli MONITOR
# 查看慢查询⽇志
redis-cli SLOWLOG GET 10
# 检查客户端连接状况
redis-cli CLIENT LIST
# 使⽤ UNLINK 异步删除⼤ Key
redis-cli UNLINK big_key
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 168 / 261

---

另外，当 Redis 使⽤的内存超过物理内存时，操作系统会将部分内存交换到磁盘，这时候会导致 Redis 响应变慢。
我的处理⽅式是：
使⽤ free -h 检查内存的使⽤情况 ；确认 Redis 的 maxmemory 设置是否合理；如果发⽣了内存交换，⽴即调
整 maxmemory 并清理⼀些不重要的数据。
⼤量的客户端连接也可能会导致阻塞，这时候最好检查⼀下连接池的配置。
Redis 应⽤
 
43.Redis如何实现异步消息队列？
 
Redis 实现异步消息队列是⼀个很实⽤的技术⽅案，最简单的⽅式是使⽤ List 配合 LPUSH 和 RPOP 命令。
public void safeBatchProcess(String key) {
    ScanOptions options = ScanOptions.scanOptions().count(1000).build();
    Cursor<String> cursor = redisTemplate.opsForSet().scan(key, options);
    
    while (cursor.hasNext()) {
        String member = cursor.next();
        // 分批处理，避免阻塞
        processElement(member);
    }
}
@Configuration
public class RedisConnectionConfig {
    
    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);        // 最⼤连接数
        poolConfig.setMaxIdle(50);          // 最⼤空闲连接
        poolConfig.setMinIdle(10);          // 最⼩空闲连接
        poolConfig.setMaxWaitMillis(3000);  // 获取连接最⼤等待时间
        poolConfig.setTestOnBorrow(true);   // 获取连接时检测有效性
        
        return new JedisConnectionFactory(poolConfig);
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 169 / 261

---

另外就是⽤ Redis 的 Pub/Sub 来实现简单的消息⼴播和订阅。
@Service
public class SimpleRedisQueue {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // ⽣产者：向队列发送消息
    public void sendMessage(String queueName, Object message) {
        redisTemplate.opsForList().leftPush(queueName, message);
    }
    
    // 消费者：从队列获取消息
    public Object receiveMessage(String queueName) {
        return redisTemplate.opsForList().rightPop(queueName);
    }
    
    // 阻塞式消费，避免轮询
    public Object blockingReceive(String queueName, int timeoutSeconds) {
        List<Object> result = redisTemplate.opsForList()
            .rightPop(queueName, timeoutSeconds, TimeUnit.SECONDS);
        return result != null && !result.isEmpty() ? result.get(0) : null;
    }
}
@Service
public class RedisPubSubService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 发布消息到指定频道
    public void publish(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }
    
    // 订阅频道
    @PostConstruct
    public void subscribe() {
        redisTemplate.setMessageListener((message, pattern) -> {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 170 / 261

---

发布者将消息发布到指定的频道，订阅该频道的客户端就能收到消息。
但是这两种⽅式都是不可靠的，因为没有 ACK 机制所以不能保证订阅者⼀定能收到消息，也不⽀持消息持久化。
44.Redis如何实现延时消息队列?
 
延时消息队列在实际业务中很常⻅，⽐如订单超时取消、定时提醒等场景。Redis 虽然不是专业的消息队列，但可
以很好地实现延时队列功能。
核⼼思路是利⽤ ZSet 的有序特性，将消息作为 member，把消息的执⾏时间作为 score。这样消息就会按照执⾏
时间⾃动排序，我们只需要定期扫描当前时间之前的消息进⾏处理就可以了。
            System.out.println("Received message: " + message);
        });
        redisTemplate.getConnectionFactory().getConnection().subscribe(
            new ChannelTopic("myChannel").getTopic().getBytes()
        );
    }
}
@Service
public class DelayedMessageQueue {
    
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 171 / 261

---

具体实现上，我会在⽣产者发送延时消息时，计算消息应该执⾏的时间戳，然后⽤ ZADD 命令将消息添加到 ZSet 
中。
消费者通过定时任务，使⽤ ZRANGEBYSCORE 命令获取当前时间之前的所有消息。
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 发送延时消息
    public void sendDelayedMessage(String queueName, Object message, long delaySeconds) {
        // 计算消息的执⾏时间
        long executeTime = System.currentTimeMillis() + (delaySeconds * 1000);
        
        // 将消息加⼊ZSet，以执⾏时间作为score
        redisTemplate.opsForZSet().add(queueName, message, executeTime);
        
        log.info("发送延时消息: {}, 延时: {}秒", message, delaySeconds);
    }
    
    // 消费延时消息
    @Scheduled(fixedDelay = 1000) // 每秒扫描⼀次
    public void consumeDelayedMessages() {
        String queueName = "delayed:queue";
        long currentTime = System.currentTimeMillis();
        
        // 获取已到期的消息（score <= 当前时间）
        Set<Object> messages = redisTemplate.opsForZSet()
            .rangeByScore(queueName, 0, currentTime);
        
        for (Object message : messages) {
            try {
                // 处理消息
                processMessage(message);
                
                // 处理成功后从队列中移除
                redisTemplate.opsForZSet().remove(queueName, message);
                
                log.info("处理延时消息成功: {}", message);
            } catch (Exception e) {
                log.error("处理延时消息失败: {}", message, e);
                // 可以实现重试机制
                handleFailedMessage(queueName, message);
            }
        }
    }
}
ZADD delay_queue 1617024000 task1
ZREMRANGEBYSCORE delay_queue -inf 1617024000
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 172 / 261

---

处理完成后再⽤ ZREM 删除消息。
在技术派实战项⽬中，我就⽤这种⽅式实现了⽂章定时发布的功能。作者在发布⽂章时，可以选择⼀个未来的时间
节点，⽐如说 30 分钟后，系统就会向延时队列发送⼀条延时消息，然后定时任务就会在 30 分钟后将这条消息从
延时队列中取出并发布⽂章。
1. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：Redis 实现延迟队列
2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 8 Java 后端实习⼀⾯⾯试原题：redis 数据结构，⽤什
么结构实现延迟消息队列
memo：2025 年 5 ⽉ 28 ⽇修改⾄此，今天有球友在 VIP群⾥发消息说拿到了荣耀的暑期实习 offer，虽然时间节
点已经不早了，但越是到这个时候，确实容易捡漏。
ZREM delay_queue task1
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 173 / 261

---

45.
Redis⽀持事务吗？
 
是的，Redis ⽀持简单的事务，可以将 multi、exec、discard 和 watch 命令打包，然后⼀次性的按顺序执⾏。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 174 / 261

---

基本流程是⽤ multi 开启事务，然后执⾏⼀系列命令，最后⽤ exec 提交。这些命令会被放⼊队列，在 exec 时批量
执⾏。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 175 / 261

---

当客户端处于⾮事务状态时，所有发送给 Redis 服务的命令都会⽴即执⾏；但当客户端进⼊事务状态之后，这些命
令会被放⼊⼀个事务队列中，然后⽴即返回 QUEUED，表示命令已⼊队。
当 exec 命令执⾏时，Redis 会将事务队列中的所有命令按先进先出的顺序执⾏。当事务队列⾥的命令全部执⾏完
毕后，Redis 会返回⼀个数组，包含每个命令的执⾏结果。
discard 命令⽤于取消⼀个事务，它会清空事务队列并退出事务状态。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 176 / 261

---

watch 命令⽤于监视⼀个或者多个 key，如果这个 key 在事务执⾏之前 被其他命令改动，那么事务将会被打断。
但 Redis 的事务与 MySQL 的有很⼤不同，它并不⽀持回滚，也不⽀持隔离级别。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 177 / 261

---

说⼀下 Redis 事务的原理？
 
Redis 事务的原理并不复杂，核⼼就是⼀个"先排队，后执⾏"的机制。
当执⾏ MULTI 命令时，Redis 会给这个客户端打⼀个事务的标记，表示这个客户端后⾯发送的命令不会被⽴即执
⾏，⽽是被放到⼀个队列⾥排队等着。
当 Redis 收到 EXEC 命令时，它会把队列⾥的命令⼀个个拿出来执⾏。因为 Redis 是单线程的，所以这个过程不会
被其他命令打断，这就保证了Redis 事务的原⼦性。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 178 / 261

---

当执⾏ WATCH 命令时，Redis 会将 key 添加到全局监视字典中；只要这些 key 在 EXEC 前被其他客户端修改，
Redis 就会给相关客户端打上脏标记，EXEC 时发现事务已被⼲扰就会直接取消整个事务。
DISCARD 做的事情很简单直接，⾸先检查客户端是否真的在事务状态，如果不在就报错；如果在事务状态，就清
空事务队列并退出事务状态。
// 全局监视字典
dict *watched_keys;
typedef struct watchedKey {
    robj *key;
    redisDb *db;
} watchedKey;
void discardCommand(client *c) {
    if (!(c->flags & CLIENT_MULTI)) {
        addReplyError(c,"DISCARD without MULTI");
        return;
    }
    discardTransaction(c);
    addReply(c,shared.ok);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 179 / 261

---

Redis 事务有哪些注意点？
 
最重要的的⼀点是，Redis 事务不⽀持回滚，⼀旦 EXEC 命令被调⽤，所有命令都会被执⾏，即使有些命令可能执
⾏失败。
Redis事务为什么不⽀持回滚？
 
Redis 的核⼼设计理念是简单、⾼效，⽽不是完整的 ACID 特性。⽽实现回滚需要在执⾏过程中保存⼤量的状态信
息，并在发⽣错误时逆向执⾏命令以恢复原始状态。这会增加 Redis 的复杂性和性能开销。
Redis事务满⾜原⼦性吗？要怎么改进？
 
Redis 的事务不能满⾜标准的原⼦性，因为它不⽀持事务回滚，也就是说，假如某个命令执⾏失败，整个事务并不
会⾃动回滚到初始状态。
可以使⽤ Lua 脚本来替代事务，脚本运⾏期间，Redis 不会处理其他命令，并且我们可以在脚本中处理整个业务逻
辑，包括条件检查和错误处理，保证要么执⾏成功，要么保持最初的状态，不会出现⼀个命令执⾏失败、其他命令
执⾏成功的情况。
// ⼀个转账事务
redisTemplate.multi();
redisTemplate.opsForValue().decrement("user:1:balance", 100); // 成功
redisTemplate.opsForList().leftPush("user:1:balance", "log");  // 类型错误，失败
redisTemplate.opsForValue().increment("user:2:balance", 100);  // 还是会执⾏
List<Object> results = redisTemplate.exec();
// 结果：⽤户1被扣了钱，⽤户2也收到了钱，但中间的⽇志操作失败了
// 这符合Redis的原⼦性定义，但不符合业务期望
@Service
public class ImprovedTransactionService {
    
    public boolean atomicTransfer(String fromUser, String toUser, int amount) {
        String luaScript = 
            "local from_key = KEYS[1] " +
            "local to_key = KEYS[2] " +
            "local amount = tonumber(ARGV[1]) " +
            
            // 检查转出账户余额
            "local from_balance = redis.call('GET', from_key) " +
            "if not from_balance then return -1 end " +
            
            "from_balance = tonumber(from_balance) " +
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 180 / 261

---

Redis 事务的 ACID 特性如何体现？
 
单个 Redis 命令的执⾏是原⼦性的，但 Redis 没有在事务上增加任何维持原⼦性的机制，所以 Redis 事务在执⾏过
程中如果某个命令失败了，其他命令还是会继续执⾏，不会回滚。
            "if from_balance < amount then return -2 end " +
            
            // 检查转⼊账户是否存在
            "if redis.call('EXISTS', to_key) == 0 then return -3 end " +
            
            // 所有检查通过，执⾏转账
            "redis.call('DECRBY', from_key, amount) " +
            "redis.call('INCRBY', to_key, amount) " +
            
            // 记录转账⽇志
            "local log = from_key .. ':' .. to_key .. ':' .. amount " +
            "redis.call('LPUSH', 'transfer:log', log) " +
            
            "return 1";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);
        
        Long result = redisTemplate.execute(script, 
            Arrays.asList("user:" + fromUser + ":balance", "user:" + toUser + 
":balance"),
            amount);
        
        return result != null && result == 1;
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 181 / 261

---

⼀致性指的是，如果数据在执⾏事务之前是⼀致的，那么在事务执⾏之后，⽆论事务是否执⾏成功，数据也应该是
⼀致的。但 Redis 事务并不保证⼀致性，因为如果事务中的某个命令失败了，其他命令仍然会执⾏，就会出现数据
不⼀致的情况。
Redis 是单线程执⾏事务的，并且不会中断，直到执⾏完所有事务队列中的命令为⽌。因此，我认为 Redis 的事务
具有隔离性的特征。
Redis 事务的持久性完全依赖于 Redis 本身的持久化机制，如果开启了 AOF，那么事务中的命令会作为⼀个整体记
录到 AOF ⽂件中，当然也要看 AOF 的 fsync 策略。
如果只开启了 RDB，事务中的命令可能会在下次快照前丢失。如果两个都没有开启，肯定是不满⾜持久性的。
1. Java ⾯试指南（付费）收录的华为⼀⾯原题：说下 Redis 事务
2. ⼆哥编程星球球友枕云眠美团 AI ⾯试原题：什么是 redis 的事务，它的 ACID 属性如何体现
3. Java ⾯试指南（付费）收录的快⼿同学 4 ⼀⾯原题：Redis事务满⾜原⼦性吗？要怎么改进？
memo：2025 年 5 ⽉ 29 ⽇，今天给球友修改简历时，碰到⼀个东南⼤学本硕博 3 985 的球友，这也是我已知信
息中学历最⾼的球友了。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 182 / 261

---

46.有Lua脚本操作Redis的经验吗？
 
Lua 脚本是处理 Redis 复杂操作的⾸选⽅案，⽐如说原⼦扣减库存、分布式锁、限流等业务场景，都可以通过 Lua 
脚本来实现。
在秒杀场景下，可以⽤ Lua 脚本把所有检查逻辑都写在⼀起：先看库存够不够，再看⽤户有没有买过，所有条件都
满⾜才扣减库存。因为整个脚本是原⼦执⾏的，Redis 在执⾏期间不会处理其他命令，所以可以彻底解决超卖问
题。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 183 / 261

---

在分布式锁场景下，我⼀开始⽤的 SETNX 命令来实现，结果发现如果程序异常退出，锁就死掉了。后来加了过期
时间，但⼜发现可能误删其他线程的锁。最后还是⽤ Lua 脚本彻底解决了这个问题，确保只有锁的持有者才能释放
锁。
甚⾄还可以⽤ Lua脚本实现滑动窗⼝限流器，⼀次性完成过期数据清理、计数检查、新记录添加三个操作，⽽且完
全原⼦化。
memo：2025 年 5 ⽉ 30 ⽇，今天有球友在星球⾥发消息说拿到了⾦⼭办公的 offer，问我该选 cpp 还是go，我的
建议⼤家可以看看符合是否合理，不管如何选择，真的恭喜球友！
// 这个秒杀脚本救了我的命
String luaScript = 
    "local stock = redis.call('GET', KEYS[1]) " +
    "if not stock or tonumber(stock) < tonumber(ARGV[2]) then " +
    "    return -1 " +  // 库存不⾜
    "end " +
    "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then " +
    "    return -2 " +  // 重复购买
    "end " +
    "redis.call('DECRBY', KEYS[1], ARGV[2]) " +
    "redis.call('SADD', KEYS[2], ARGV[1]) " +
    "return 1";
// 解锁脚本特别重要，必须验证是⾃⼰的锁才能删
private final String UNLOCK_SCRIPT = 
    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
    "    return redis.call('DEL', KEYS[1]) " +
    "else " +
    "    return 0 " +
    "end";
// 滑动窗⼝限流，逻辑清晰，性能还好
String luaScript = 
    "local key = KEYS[1] " +
    "local now = tonumber(ARGV[1]) " +
    "local window = tonumber(ARGV[2]) " +
    "local limit = tonumber(ARGV[3]) " +
    
    // 先清理过期记录
    "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
    
    // 检查当前请求数
    "local current = redis.call('ZCARD', key) " +
    "if current < limit then " +
    "    redis.call('ZADD', key, now, now) " +
    "    return 1 " +
    "else " +
    "    return 0 " +
    "end"; 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 184 / 261

---

47.Redis的管道Pipeline了解吗？
 
了解，Pipeline 允许客户端⼀次性向 Redis 服务器发送多个命令，⽽不必等待⼀个命令响应后才能发送下⼀个。
Redis 服务器会按照命令的顺序依次执⾏，并将所有结果打包返回给客户端。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 185 / 261

---

正常情况下，每执⾏⼀个 Redis 命令都需要⼀次⽹络往返：发送命令 -> 等待响应 -> 发送下⼀个命令。
如果⼤量请求依次发送，⽹络延迟会显著增加请求的总执⾏时间，假如⼀次 RTT 的时间是 1 毫秒，3 个就是 3 毫
秒。有了 Pipeline 后，可以⼀次性发送 3 个命令，总时间就只需要 1 毫秒。
客户端                    Redis服务器
  |                           |
  |------- SET key1 val1 ---->|
  |<------ OK ---------------|
  |------- SET key2 val2 ---->|
  |<------ OK ---------------|
  |------- GET key1 -------->|
  |<------ val1 -------------|
@Service
public class RedisBatchService {
    
    public void batchInsertUsers(List<User> users) {
        // 不⽤Pipeline的错误做法 - 很慢
        // for (User user : users) {
        //     redisTemplate.opsForValue().set("user:" + user.getId(), user);
        // }
        
        // 使⽤Pipeline的正确做法
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 186 / 261

---

当然了，Pipeline 不是越⼤越好，太⼤会占⽤过多内存，通常建议每个 Pipeline 包含 1000 到 5000 个命令。可以
根据实际情况调整。
什么场景下适合使⽤ Pipeline呢？
 
需要批量插⼊、更新或删除数据，或者需要执⾏⼤量相似的命令时。⽐如：系统启动时的缓存预热 -> 批量加载热
点数据；⽐如统计数据的批量更新；⽐如⼤批量数据的导⼊导出；⽐如批量删除过期或⽆效的缓存。
有了解过 Pipeline 的底层原理吗？
 
有，其实就是缓冲的思想。在技术派实战项⽬中，我就在 RedisClient 类中封装了⼀个 PipelineAction 内部类，⽤
来缓存命令。
            public Object doInRedis(RedisConnection connection) throws 
DataAccessException {
                for (User user : users) {
                    String key = "user:" + user.getId();
                    byte[] keyBytes = key.getBytes();
                    byte[] valueBytes = serialize(user);
                    
                    connection.set(keyBytes, valueBytes);
                }
                return null; // Pipeline不需要返回值
            }
        });
    }
}
public void smartBatchInsert(List<String> data) {
    int batchSize = 1000; // 经验值，根据数据⼤⼩调整
    
    for (int i = 0; i < data.size(); i += batchSize) {
        List<String> batch = data.subList(i, Math.min(i + batchSize, data.size()));
        
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws 
DataAccessException {
                for (String item : batch) {
                    connection.set(item.getBytes(), item.getBytes());
                }
                return null;
            }
        });
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 187 / 261

---

add ⽅法将命令包装成 Runnable 对象，放⼊ List 中。当执⾏ execute ⽅法时，再调⽤ RedisTemplate 的 
executePipelined ⽅法开启管道模式将多个命令发送到 Redis 服务端。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 188 / 261

---

Redis 服务端从输⼊缓冲区读到命令后，会按照 RESP 协议进⾏命令拆解，再依次执⾏这些命令。执⾏结果会写⼊
到输出缓冲区，最后再将所有结果⼀次性返回给客户端。
1. Java ⾯试指南（付费）收录的京东⾯经同学 8 ⾯试原题：对pipeline的理解，什么场景适合使⽤
pipeline？有了解过pipeline的底层？
memo：2025 年 6 ⽉ 1 ⽇，今天有球友在星球⾥发消息说拿到了百得思维的offer，他是⺠办⼆本，对这个结果很
满意，也很感谢⾯渣逆袭和星球的实战项⽬，让他摆脱了浑浑噩噩的⽇⼦。恭喜他！
typedef struct client {
    sds querybuf;           // 输⼊缓冲区
    list *reply;            // 输出缓冲区链表
    unsigned long reply_bytes; // 输出缓冲区⼤⼩
} client;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 189 / 261

---

48.
Redis能实现分布式锁吗？
 
分布式锁是⼀种⽤于控制多个不同进程在分布式系统中访问共享资源的锁机制。它能确保在同⼀时刻，只有⼀个节
点可以对资源进⾏访问，从⽽避免分布式场景下的并发问题。
可以使⽤ Redis 的 SETNX 命令实现简单的分布式锁。⽐如 SET key value NX PX 3000 就创建了⼀个锁名为 
key 的分布式锁，锁的持有者为 value 。NX 保证只有在 key 不存在时才能创建成功，EX 设置过期时间⽤以防⽌
死锁。
Redis如何保证 SETNX 不会发⽣冲突？
 
当我们使⽤ SET key value NX EX 30 这个命令进⾏加锁时，Redis 会把整个操作当作⼀个原⼦指令来执⾏。因
为 Redis 的命令处理是单线程的，所以在同⼀时刻只能有⼀个命令在执⾏。
⽐如说两个客户端 A 和 B 同时请求同⼀个锁：
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 190 / 261

---

虽然这两个请求可能⼏乎同时到达 Redis 服务器，但 Redis 会严格按照到达的先后顺序来处理。假设 A 的请求先
到，Redis 会先执⾏ A 的 SET 命令，这时 lock_key 被设置为 uuid_a。
当处理 B 的请求时，因为 lock_key 已经存在了，NX 条件不满⾜，所以 B 的 SET 命令会失败，返回 NULL。这样
就保证了只有 A 能获取到锁。
关键点在于 NX 的语义：NOT EXISTS ，只有在 key 不存在的时候才会设置成功。Redis 在执⾏这个命令时，会先
检查 key 是否存在，如果不存在才会设置值，这整个过程是原⼦的，不会被其他命令打断。
SETNX有什么问题，如何解决？
 
使⽤ SETNX 创建分布式锁时，虽然可以通过设置过期时间来避免死锁，但会误删锁。⽐如线程 A 获取锁后，业务
执⾏时间⽐较⻓，锁过期了。这时线程 B 获取到锁，但线程 A 执⾏完业务逻辑后，会尝试删除锁，这时候删掉的
其实是线程 B 的锁。
客户端A: SET lock_key uuid_a NX EX 30
客户端B: SET lock_key uuid_b NX EX 30
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 191 / 261

---

可以通过锁的⾃动续期机制来解决锁过期的问题，⽐如 Redisson 的看⻔狗机制，在后台启动⼀个定时任务，每隔
⼀段时间就检查锁是否还被当前线程持有，如果是就⾃动延⻓过期时间。这样既避免了死锁，⼜防⽌了锁被提前释
放。
memo：2025 年 6 ⽉ 2 ⽇修改⾄此，今天在帮⼀个学院本球友分析 offer 选择后，他⼜回复说多亏了星球才能⼀
路⾛到现在，很满⾜这个结果。看多了拿⼤⼚ offer 球友的感谢，看到学院本也能取得满意的成绩，我也很开⼼。
Redisson了解多少？
 
Redisson 是⼀个基于 Redis 的 Java 客户端，它不只是对 Redis 的操作进⾏简单地封装，还提供了很多分布式的数
据结构和服务，⽐如最常⽤的分布式锁。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 192 / 261

---

Redisson 的分布式锁⽐ SETNX 完善的得多，它的看⻔狗机制可以让我们在获取锁的时候省去⼿动设置过期时间的
步骤，它在内部封装了⼀个定时任务，每隔 10 秒会检查⼀次，如果当前线程还持有锁就⾃动续期 30 秒。
另外，Redisson 还提供了分布式限流器 RRateLimiter，基于令牌桶算法实现，⽤于控制分布式环境下的访问频
率。
RLock lock = redisson.getLock("lock");
lock.lock();
try {
    // do something
} finally {
    lock.unlock();
}
private Long tryAcquire(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
    return get(tryAcquireAsync(waitTime, leaseTime, unit, threadId));
}
private <T> RFuture<Long> tryAcquireAsync(long waitTime, long leaseTime, TimeUnit unit, 
long threadId) {
    RFuture<Long> ttlRemainingFuture;
    if (leaseTime != -1) {
        // ⼿动设置过期时间
        ttlRemainingFuture = tryLockInnerAsync(waitTime, leaseTime, unit, threadId, 
RedisCommands.EVAL_LONG);
    } else {
        // 启⽤看⻔狗机制，使⽤默认的30秒过期时间
        ttlRemainingFuture = tryLockInnerAsync(waitTime, internalLockLeaseTime,
                TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
    }
    
    // 处理获取锁成功的情况
    ttlRemainingFuture.onComplete((ttlRemaining, e) -> {
        if (e != null) {
            return;
        }
        // 如果获取锁成功且启⽤看⻔狗机制
        if (ttlRemaining == null) {
            if (leaseTime != -1) {
                internalLockLeaseTime = unit.toMillis(leaseTime);
            } else {
                scheduleExpirationRenewal(threadId); // 启动看⻔狗
            }
        }
    });
    return ttlRemainingFuture;
}
// API 接⼝限流
@RestController
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 193 / 261

---

详细说说Redisson的看⻔狗机制？
 
Redisson 的看⻔狗机制是⼀种⾃动续期机制，⽤于解决分布式锁的过期问题。
基本原理是这样的：当调⽤ lock() ⽅法加锁时，如果没有显式设置过期时间，Redisson 会默认给锁加⼀个 30 
秒的过期时间，同时启⽤⼀个名为“看⻔狗”的定时任务，每隔 10 秒（默认是过期时间的 1/3），去检查⼀次锁是否
还被当前线程持有，如果是，就⾃动续期，将过期时间延⻓到 30 秒。
public class ApiController {
    
    @Autowired
    private RedissonClient redissonClient;
    
    @GetMapping("/api/data")
    public ResponseEntity<?> getData() {
        RRateLimiter limiter = redissonClient.getRateLimiter("api.data");
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);
        
        if (limiter.tryAcquire()) {
            // 处理请求
            return ResponseEntity.ok(processData());
        } else {
            // 限流触发
            return ResponseEntity.status(429).body("Rate limit exceeded");
        }
    }
}
// 伪代码展示核⼼逻辑
private void renewExpiration() {
    Timeout task = commandExecutor.getConnectionManager()
        .newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                // ⽤ Lua 脚本检查并续期
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 194 / 261

---

续期的 Lua 脚本会检查锁的 value 是否匹配当前线程，如果匹配就延⻓过期时间。这样就能保证只有锁的真正持有
者才能续期。
当调⽤ unlock() ⽅法时，看⻔狗任务会被取消。或者如果业务逻辑执⾏完但忘记 unlock 了，看⻔狗也会帮我们
⾃动检查锁，如果锁已经不属于当前线程了，也会⾃动停⽌续期。
这样我们就不⽤担⼼业务执⾏时间过⻓导致锁被提前释放，也避免了⼿动估算过期时间的麻烦，同时也解决了分布
式环境下的死锁问题。
看⻔狗机制中的检查锁过程是原⼦操作吗？
 
是的，Redisson 使⽤了 Lua 脚本来保证锁检查的原⼦性。
                if (redis.call("get", lockKey) == currentThreadId) {
                    redis.call("expire", lockKey, 30);
                    // 递归调⽤，继续下⼀次续期
                    renewExpiration();
                }
            }
        }, 10, TimeUnit.SECONDS);
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 195 / 261

---

Redis 在执⾏ Lua 脚本时，会把整个脚本当作⼀个命令来处理，期间不会执⾏其他命令。所以 hexists 检查和 
expire 续期是原⼦执⾏的。
Redlock你了解多少？
 
Redlock 是 Redis 作者 antirez 提出的⼀种分布式锁算法，⽤于解决单个 Redis 实例作为分布式锁时存在的单点故
障问题。
Redlock 的核⼼思想是通过在多个完全独⽴的 Redis 实例上同时获取锁来实现容错。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 196 / 261

---

minLocksAmount ⽅法返回的 locks.size()/2 + 1 ，正是 Redlock 算法要求的少数服从多数原则。
failedLocksLimit ⽅法会计算允许失败的锁数量，确保即使部分实例失败，只要成功的实例数量超过⼀半就认为获
取锁成功。
红锁会尝试依次向所有 Redis 实例获取锁，并记录成功获取的锁数量，当数量达到 minLocksAmount 时就认为获
取成功，否则释放已获取的锁并返回失败。
虽然 Redlock 存在⼀些争议，⽐如说时钟漂移问题、⽹络分区导致的脑裂问题，但它仍然是⼀个相对成熟的分布式
锁解决⽅案。
红锁能不能保证百分百上锁？
 
不能，Redlock ⽆法保证百分百上锁成功，这是由分布式系统的本质特性决定的。
当有⽹络分区时，客户端可能⽆法与⾜够数量的 Redis 实例通信。⽐如在 5 个 Redis 实例的部署中，如果⽹络分区
导致客户端只能访问到 2 个实例，那么⽆论如何都⽆法满⾜红锁要求的少数服从多数原则，获取锁的时候必然失
败。
public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws 
InterruptedException {
    // ...
    for (ListIterator<RLock> iterator = locks.listIterator(); iterator.hasNext();) {
        RLock lock = iterator.next();
        boolean lockAcquired;
        try {
            lockAcquired = lock.tryLock(awaitTime, newLeaseTime, TimeUnit.MILLISECONDS);
        } catch (RedisResponseTimeoutException e) {
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 197 / 261

---

时钟漂移也会影响成功率。即使所有实例都可达，如果各个 Redis 实例之间存在明显的时钟漂移，或者客户端在获
取锁的过程中耗时过⻓，⽐如⽹络延迟、GC 停顿等，都可能会导致锁在获取完成前就过期，从⽽获取失败。
在实际应⽤中，可以通过重试机制来提⾼锁的成功率。
项⽬中有⽤到分布式锁吗？
 
在PmHub项⽬中，我有使⽤ Redission 的分布式锁来确保流程状态的更新按顺序执⾏，且不被其他流程服务⼲
扰。
            lockAcquired = false; // ⽹络超时导致失败
        } catch (Exception e) {
            lockAcquired = false; // 其他异常导致失败
        }
        
        // 如果剩余可尝试的实例数量不⾜以达到多数派，直接退出
        if (locks.size() - acquiredLocks.size() == failedLocksLimit()) {
            break;
        }
    }
    
    // 检查是否达到多数派要求
    if (acquiredLocks.size() >= minLocksAmount(locks)) {
        return true;
    } else {
        unlockInner(acquiredLocks);
        return false; // 未达到多数派，获取失败
    }
}
for (int i = 0; i < maxRetries; i++) {
    if (redLock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS)) {
        return true;
    }
    Thread.sleep(retryDelay);
}
return false;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 198 / 261

---

1. Java ⾯试指南（付费）收录的腾讯 Java 后端实习⼀⾯原题：分布式锁⽤了 Redis 的什么数据结构
2. Java ⾯试指南（付费）收录的⼩公司⾯经合集同学 1 Java 后端⾯试原题：Redisson 的底层原理？以及
与 SETNX 的区别？
3. Java ⾯试指南（付费）收录的百度⾯经同学 1 ⽂⼼⼀⾔ 25 实习 Java 后端⾯试原题：redis 分布式锁的
实现原理？setnx？
4. Java ⾯试指南（付费）收录的⼩⽶同学 F ⾯试原题：⾃⼰实现 redis 分布式锁的坑（主动提了 
Redission）
5. Java ⾯试指南（付费）收录的腾讯云智⾯经同学 20 ⼆⾯⾯试原题：redission 的原理是什么？ setnx + 
lua 脚本？
6. Java ⾯试指南（付费）收录的收钱吧⾯经同学 1 Java 后端⼀⾯⾯试原题：系统⾥⾯分布式锁是怎么做
的？你提到了redlock，那它机制是怎么样的？红锁能不能保证百分百上锁？
7. Java ⾯试指南（付费）收录的字节跳动⾯经同学 21  抖⾳商城⼀⾯⾯试原题：加分布式锁时redis如何
保证不会发⽣冲突？分布式锁过期怎么办？
8. Java ⾯试指南（付费）收录的拼多多⾯经同学 8 ⼀⾯⾯试原题：Redis分布式锁如何实现的
9. Java ⾯试指南（付费）收录的百度同学 4 ⾯试原题：Setnx,知道吗? ⽤这个加锁有什么问题吗?怎么解
决?
10. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：分布式锁⽤redis实现思路
11. Java ⾯试指南（付费）收录的京东⾯经同学 9 ⾯试原题：redis的分布式锁有了解过吗
12. Java ⾯试指南（付费）收录的同学 30 腾讯⾳乐⾯试原题：redis锁有⼏种实现⽅式
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 199 / 261

---

memo：2025 年 6 ⽉ 3 ⽇修改⾄此，今天在修改球友的简历时，碰到⼀个 211 本科、北京⼤学软微学院的球友，
我只能说，星球的球友真是⼈才济济啊！祝⼤家都有⼀个美好的未来。
底层结构
 
49.
Redis都有哪些底层数据结构？
 
Redis 之所以快，除了基于内存读写之外，还有很重要的⼀点就是它精⼼设计的底层数据结构。Redis 总共有 8 种
核⼼的底层数据结构，我按照重要程度来说⼀下。
⾸先是 SDS，这是 Redis ⾃⼰实现的动态字符串，它保留了 C 语⾔原⽣的字符串⻓度，所以获取⻓度的时间复杂
度是 O(1) ，在此基础上还⽀持动态扩容，以及存储⼆进制数据。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 200 / 261

---

然后是字典，更底层是⽤数组+链表实现的哈希表。它的设计很巧妙，⽤了两个哈希表，平时⽤第⼀个，rehash 的
时候⽤第⼆个，这样可以渐进式地进⾏扩容，不会阻塞太久。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 201 / 261

---

接下来压缩列表 ziplist，这个设计很有意思。Redis 为了节省内存，设计了这种紧凑型的数据结构，把所有元素连
续存储在⼀块内存⾥。但是它有个致命问题叫"连锁更新"，就是当我们修改⼀个元素的时候，可能会导致后⾯所有
的元素都要重新编码，性能会急剧下降。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 202 / 261

---

为了解决压缩列表的问题，Redis 后来设计了 quicklist。这个设计思路很聪明，它把 ziplist 拆分成⼩块，然后⽤双
向链表把这些⼩块串起来。这样既保持了 ziplist 节省内存的优势，⼜避免了连锁更新的问题，因为每个⼩块的 
ziplist 都不会太⼤。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 203 / 261

---

再后来，Redis ⼜设计了 listpack，这个可以说是 ziplist 的完美替代品。它最⼤的特点是每个元素只记录⾃⼰的⻓
度，不记录前⼀个元素的⻓度，这样就彻底解决了连锁更新的问题。Redis 5.0 已经⽤ listpack 替换了 ziplist。
跳表skiplist 主要⽤在 ZSet 中。它的设计很巧妙，通过多层指针来实现快速查找，平均时间复杂度是 O(log N) 。
相⽐红⿊树，跳表的实现更简单，⽽且⽀持范围查询，这对 Redis 的有序集合来说很重要。
还有整数集合intset，当 Set 中都是整数且元素数量较少时使⽤，内部是⼀个有序数组，查找⽤的⼆分法。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 204 / 261

---

最后是双向链表LinkedList，早期版本的 Redis 会在 List 中⽤到，但 Redis 3.2 后就被 quicklist 替代了，因为纯链
表的问题是内存不连续，影响 CPU 缓存性能。
memo：2025 年 6 ⽉ 4 ⽇，今天有球友发喜报说拿到了京东零售的实习 offer，并且部⻔和业务还是挺不错的，
恭喜他！6 ⽉份还有机会，冲。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 205 / 261

---

简单介绍下链表？
 
Redis 的 linkedlist 是⼀个双向⽆环链表结构，和 Java 中的 LinkedList 类似。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 206 / 261

---

节点由 listNode 表示，每个节点都有指向其前置节点和后置节点的指针，头节点的前置和尾节点的后置均指向 
null。
关于整数集合，能再详细说说吗？
 
整数集合是 Redis 中⼀个⾮常精巧的数据结构，当⼀个 Set 只包含整数元素，并且数量不多时，默认不超过 512 
个，Redis 就会⽤ intset 来存储这些数据。
intset 最有意思的地⽅是类型升级机制。它有三种编码⽅式：16位、32位和 64位，会根据存储的整数⼤⼩动态调
整。⽐如原来存的都是⼩整数，⽤ 16 位编码就够了，但突然插⼊了⼀个很⼤的数，超出了 16 位的范围，这时整
个数组会升级到 32 位编码。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 207 / 261

---

当然了，这种升级是有代价的，因为需要重新分配内存并复制数据，并且是不可逆的，但它的好处是可以节省内存
空间，特别是在存储⼤量⼩整数时。
另外，所有元素在数组中按照从⼩到⼤的顺序排列，这样就可以使⽤⼆分查找来定位元素，时间复杂度为 O(log 
N) 。
说⼀下zset 的底层原理？
 
ZSet 是 Redis 最复杂的数据类型，它有两种底层实现⽅式：压缩列表和跳表。
当保存的元素数量少于 128 个，且保存的所有元素⼤⼩都⼩于 64 字节时，Redis 会采⽤压缩列表的编码⽅式；否
则就⽤跳表。
当然，这两个条件都可以通过参数进⾏调整。
选择压缩列表作为底层实现时，每个元素会使⽤两个紧挨在⼀起的节点来保存：第⼀个节点保存元素的成员，第⼆
个节点保存元素的分值。
typedef struct intset {
    uint32_t encoding;   // 编码⽅式：16位、32位或64位
    uint32_t length;     // 元素数量
    int8_t contents[];   // 保存元素的数组
} intset;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 208 / 261

---

所有元素按分值从⼩到⼤有序排列，⼩的放在靠近表头的位置，⼤的放在靠近表尾的位置。
但跳表的缺点是查找只能按顺序进⾏，时间复杂度为 O(N) ，⽽且在最坏的情况下，插⼊和删除操作还可能会引起
连锁更新。
当元素数量较多或元素较⼤时，Redis 会使⽤ skiplist 的编码⽅式；这个设计⾮常的巧妙，同时使⽤了两种数据结
构：
跳表按分数有序保存所有元素，且⽀持范围查询（如 ZRANGE 、ZRANGEBYSCORE ），平均时间复杂度为 O(log 
N) 。⽽哈希表则⽤来存储成员和分值的映射关系，查找时间复杂度为 O(1) 。
typedef struct zset {
    zskiplist *zsl;  // 跳跃表
    dict *dict;      // 字典
} zset;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 209 / 261

---

虽然同时使⽤两种结构，但它们会通过指针来共享相同元素的成员和分值，因此不会浪费额外的内存。
你知道为什么Redis 7.0要⽤listpack来替代ziplist吗？
 
答：主要是为了解决压缩列表的⼀个核⼼问题——连锁更新。在压缩列表中，每个节点都需要记录前⼀个节点的⻓
度信息。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 210 / 261

---

当插⼊或删除⼀个节点时，如果这个操作导致某个节点的⻓度发⽣了变化，那么后续的节点可能都需要更新它们存
储的"前⼀个节点⻓度"字段。最坏的情况下，⼀次操作可能触发整个链表的更新，时间复杂度会从 O(1) 退化到 
O(n²) 。
⽽ listpack 的设计理念完全不同。它让每个节点只记录⾃⼰的⻓度信息，不再依赖前⼀个节点的⻓度。这样就从根
本上避免了连锁更新的问题。
listpack 中的节点不再保存其前⼀个节点的⻓度，⽽是保存当前节点的编码类型、数据和⻓度。
连锁更新是怎么发⽣的？
 
⽐如说我们有⼀个压缩列表，其中有⼏个节点的⻓度都是 253 个字节。在 ziplist 的编码中，如果前⼀个节点的⻓
度⼩于 254 字节，我们只需要 1 个字节来存储这个⻓度信息。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 211 / 261

---

但如果在这些节点前⾯插⼊⼀个⻓度为 254 字节的节点，那么原来只需要 1 个字节存储⻓度的节点现在需要 5 个
字节来存储⻓度信息。这就会导致后续所有节点的⻓度信息都需要更新。
1. Java ⾯试指南（付费）收录的字节跳动商业化⼀⾯的原题：说说 Redis 的 zset，什么是跳表，插⼊⼀个
节点要构建⼏层索引
2. Java ⾯试指南（付费）收录的字节跳动⾯经同学 9 ⻜书后端技术⼀⾯⾯试原题：Redis 的数据类型，
ZSet 的实现
3. Java ⾯试指南（付费）收录的⼩⽶暑期实习同学 E ⼀⾯⾯试原题：你知道 Redis 的 zset 底层实现吗
4. Java ⾯试指南（付费）收录的腾讯⾯经同学 23 QQ 后台技术⼀⾯⾯试原题：zset 的底层原理
5. Java ⾯试指南（付费）收录的快⼿⾯经同学 7 Java 后端技术⼀⾯⾯试原题：说⼀下 ZSet 底层结构
6. Java ⾯试指南（付费）收录的美团同学 9 ⼀⾯⾯试原题：redis的数据结构底层原理？
7. Java ⾯试指南（付费）收录的腾讯⾯经同学 27 云后台技术⼀⾯⾯试原题：Zset的底层实现？
8. Java ⾯试指南（付费）收录的得物⾯经同学 9 ⾯试题⽬原题：Zset的底层如何实现？
memo：2025 年 6 ⽉ 5 ⽇，今天有球友在VIP群⾥咨询 offer 的选择，⼀个拼多多，⼀个快⼿，真让⼈羡慕的要死
啊，
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 212 / 261

---

50.Redis 为什么不⽤ C 语⾔的原⽣字符串？
 
第⼀，C 语⾔的字符串其实就是字符数组，以 \0 结尾，这意味着如果数据本身包含 \0 字节，就会被误认为字符
串结束。但 Redis 需要存储各种类型的数据，包括图⽚、序列化对象等⼆进制数据，这些数据中很可能包含 \0 。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 213 / 261

---

第⼆，如果需要获取字符串⻓度，C 语⾔只能调⽤ strlen() 函数，时间复杂度是 O(N) ，因为要遍历整个字符串
直到遇到 \0 。
第三，C 语⾔的字符串不会⾃动检查边界，如果往⼀个字符数组⾥写⼊超过其容量的数据，就会出现缓冲区溢出。
第四，C 语⾔的字符串不⽀持动态扩容，如果需要修改内容，就必须重新分配内存并复制数据，开销很⼤。
Redis 设计的 SDS 完美解决了这些问题，获取⻓度可以直接通过 len 字段，时间复杂度为 O(1) ；free 字段会
记录剩余空间，因此 Redis 可以根据预分配策略动态扩容，不⽤在追加数据时重新分配内存；并且不依赖于 \0 结
尾，可以存储任意⼆进制数据。
51.你研究过 Redis 的字典源码吗？
 
是的，有研究过。Redis 的字典分为三层，最外层是⼀个 dict 结构，包含两个哈希表 ht[0] 和 ht[1] ，⽤于存储
键值对。每个哈希表由⼀个数组和链表组成，数组⽤于快速定位，链表⽤于解决哈希冲突。
struct sds {
    int len;        // 字符串⻓度
    int free;       // 剩余空间
    char buf[];     // 字符数组
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 214 / 261

---

字典最核⼼的特点是渐进式 rehash，这是我觉得最精彩的部分。传统的哈希表扩容都是⼀次性完成的，但 Redis 
不是这样的。
当负载因⼦触发 rehash 条件时，Redis 会为哈希表1 分配新的空间，通常是哈希表 0 的两倍⼤⼩，然后将 
rehashidx 设置为 0。
接下来的关键是，Redis 不会⼀次性把所有数据从哈希表0 迁移到哈希表1，⽽是每次操作字典时，顺便迁移哈希表
0 中 rehashidx 位置上的所有键值对。迁移完⼀个槽位后，rehashidx 递增，直到整个哈希表0 迁移完毕。
// 最外层的字典结构
typedef struct dict {
    dictht ht[2];       // 两个哈希表！这是关键
    long rehashidx;     // rehash索引，-1表示没有进⾏rehash
    // ...
} dict;
// 哈希表结构
typedef struct dictht {
    dictEntry **table;  // 哈希表数组
    unsigned long size; // 哈希表⼤⼩
    unsigned long sizemask; // 哈希表⼤⼩掩码，⽤于计算索引值
    unsigned long used; // 该哈希表已有节点的数量
} dictht;
// 哈希表节点
typedef struct dictEntry {
    void *key;              // 键
    v;                 // 值
    struct dictEntry *next; // 指向下个哈希表节点，形成链表
} dictEntry;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 215 / 261

---

这种设计的巧妙之处在于把 rehash 的开销分摊到了每次操作中。假设有⼀个⼏百万键的哈希表，如果⼀次性 
rehash 可能需要⼏百毫秒，这对单线程的 Redis 来说是灾难性的。但通过渐进式 rehash，每次操作只增加很少的
额外开销，⽤户基本感觉不到延迟。
在 rehash 期间，查找操作会先查 哈希表 0，没找到再查哈希表 1；但是新插⼊的数据只会放到哈希表 1 中。这样
既可以保证数据的完整性，⼜能避免数据的重复。
遇到哈希冲突怎么办？
 
Redis 是通过链地址法来解决哈希冲突的，每个哈希表的槽位实际上是⼀个链表的头指针，当多个键的哈希值映射
到同⼀个槽位时，这些键会以链表的形式串联起来。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 216 / 261

---

具体实现上，Redis 会通过哈希表节点的 next 指针，指向下⼀个具有相同哈希值的节点。当发⽣冲突时，新的键
值对会插⼊到链表的头部，时间复杂度是 O(1) 。查找时需要遍历整个链表，最坏的情况下时间复杂度为 O(n) ，
但通常链表都⽐较短。
另外，Redis 设计的哈希函数在分布上也⽐较均匀，能够有效减少哈希冲突的发⽣。
/* MurmurHash2, by Austin Appleby
 * Note - This code makes a few assumptions about how your machine behaves -
 * 1. We can read a 4-byte value from any address without crashing
 * 2. sizeof(int) == 4
 *
 * And it has a few limitations -
 *
 * 1. It will not work incrementally.
 * 2. It will not produce the same results on little-endian and big-endian
 *    machines.
 */
unsigned int dictGenHashFunction(const void *key, int len) {
    /* 'm' and 'r' are mixing constants generated offline.
       They're not really 'magic', they just happen to work well.  */
    uint32_t seed = dict_hash_function_seed;
    const uint32_t m = 0x5bd1e995;
    const int r = 24;
    /* Initialize the hash to a 'random' value */
    uint32_t h = seed ^ len;
    /* Mix 4 bytes at a time into the hash */
    const unsigned char *data = (const unsigned char *)key;
    while(len >= 4) {
        uint32_t k = *(uint32_t*)data;
        k *= m;
        k ^= k >> r;
        k *= m;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 217 / 261

---

memo：2025 年 6 ⽉ 6 ⽇，今天有球友咨询去⾦⼭办公暑期实习，要提前学点什么？⼜⼀个凭借 Java 这个载体拿
到 Go offer 的球友，说明⼤家在求职 Go 岗的时候，也不⽤说⾮要提前刻意去学习 Go，当然有⼀些基础是最好
的，我之前也整理过 Go 的学习路线在 Java 进阶之路上。
        h *= m;
        h ^= k;
        data += 4;
        len -= 4;
    }
    /* Handle the last few bytes of the input array  */
    switch(len) {
    case 3: h ^= data[2] << 16;
    case 2: h ^= data[1] << 8;
    case 1: h ^= data[0]; h *= m;
    };
    /* Do a few final mixes of the hash to ensure the last few
       * bytes are well-incorporated. */
    h ^= h >> 13;
    h *= m;
    h ^= h >> 15;
    return (unsigned int)h;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 218 / 261

---

52.
你了解跳表吗？
 
跳表是⼀种⾮常巧妙的数据结构，它在有序链表的基础上建⽴了多层索引，最底层包含所有数据，每往上⼀层，节
点数量就减少⼀半。
它的核⼼思想是"⽤空间换时间"，通过多层索引来跳过⼤量节点，从⽽提⾼查找效率。
每个节点有 50% 的概率只在第 1 层出现，25% 的概率在第 2 层出现，依此类推。查找的时候从最⾼层开始⽔平移
动，当下⼀个节点值⼤于⽬标时，就向下跳⼀层，直到找到⽬标节点。
怎么往跳表插⼊节点呢？
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 219 / 261

---

⾸先是找到插⼊位置，从最⾼层的头节点开始，在每⼀层都找到应该插⼊位置的前驱节点，⽤⼀个 update 数组把
这些前驱节点记录下来。这个查找过程和普通查找⼀样，在每层向右移动直到下个节点的值⼤于要插⼊的值，然后
下降到下⼀层。
接下来随机⽣成新节点的层数。通常⽤⼀个循环，每次有 50% 的概率继续往上，直到随机失败或达到最⼤层数限
制。
创建新节点后，从底层开始到新节点的最⾼层，在每⼀层都进⾏标准的链表插⼊操作。这⼀步要利⽤之前记录的 
update 数组，将新节点插⼊到正确位置，然后更新前后指针的连接关系。
// 记录每层的插⼊位置
zskiplistNode *update[ZSKIPLIST_MAXLEVEL];
zskiplistNode *x;
int i, level;
// 从最⾼层开始查找
x = zsl->header;
for (i = zsl->level-1; i >= 0; i--) {
    // 在当前层⽔平移动，找到插⼊位置
    while (x->level[i].forward &&
           (x->level[i].forward->score < score ||
            (x->level[i].forward->score == score &&
             sdscmp(x->level[i].forward->ele, ele) < 0)))
    {
        x = x->level[i].forward;
    }
    update[i] = x;  // 记录每层的前驱节点
}
// Redis 中的随机层数⽣成
int zslRandomLevel(void) {
    int level = 1;
    while ((random()&0xFFFF) < (ZSKIPLIST_P * 0xFFFF))
        level += 1;
    return (level < ZSKIPLIST_MAXLEVEL) ? level : ZSKIPLIST_MAXLEVEL;
}
// ⽣成新节点的层数
level = zslRandomLevel();
// 更新前进指针
for (i = 0; i < level; i++) {
    x->level[i].forward = update[i]->level[i].forward;
    update[i]->level[i].forward = x;
    
    // 更新跨度信息
    x->level[i].span = update[i]->level[i].span - (rank[0] - rank[i]);
    update[i]->level[i].span = (rank[0] - rank[i]) + 1;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 220 / 261

---

我们来模拟⼀个跳表的插⼊过程，假设插⼊的数据依次是 22、19、7、3、37、11、26。
// 更新未涉及层的跨度
for (i = level; i < zsl->level; i++) {
    update[i]->level[i].span++;
}
// 更新后退指针
x->backward = (update[0] == zsl->header) ? NULL : update[0];
if (x->level[0].forward)
    x->level[0].forward->backward = x;
else
    zsl->tail = x;
// 更新跳表⻓度
zsl->length++;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 221 / 261

---

那假如我们在⼀个已经分布了 1、14、27、31、44、56、63、70、80、91 的跳表中插⼊⼀个 67 的节点，插⼊过
程是这样的：
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 222 / 261

---

zset为什么要使⽤跳表呢？
 
第⼀，跳表天然就是有序的数据结构，查找、插⼊和删除都能保持 O(log n) 的时间复杂度。
第⼆，跳表⽀持范围查询，找到起始位置后可以直接沿着底层链表顺序遍历，满⾜ ZRANGE 按排名获取元素，或
者 ZRANGEBYSCORE 按分值范围获取元素。
memo：2025 年 6 ⽉ 7 ⽇，今天给⼀个学院本球友修改简历的时候，他提到实习的同事，都拿到了 20k 以上的 
offer，甚⾄还有 25k 携程 offer 的，⾃⼰并不⽐他们差，问在实习、项⽬和能⼒上还能怎么提⾼？
我想说的是，这就是为什么很多⼈选择跑来卷互联⽹开发的原因啊，上线⽐其他⾏业⾼太多了，虽然互联⽹开发的
⼯作强度也⼤，但最起码能劳有所获。
跳表是如何定义的呢？
 
跳表本质上是⼀个多层链表，底层是⼀个包含所有元素的有序链表，上⼀层作为索引层，包含了下⼀层的部分节
点；层数通过随机算法确定，理论上可以⽆限⾼。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 223 / 261

---

跳表节点包含分值 score、成员对象 obj、⼀个后退指针 backward，以及⼀个层级数组 level。每个层级包含 
forward 前进指针和 span 跨度信息。
跳表本身包含头尾节点指针、节点总数 length 和当前最⼤层数 level。
span 跨度有什么⽤？
 
span 记录了当前节点到下⼀节点之间，底层到底跨越了⼏个节点，它的主要作⽤是快速找到 ZSet 中某个分值的排
名。
typedef struct skiplistNode {
    double score;                    // 分值（⽤于排序）
    robj *obj;                      // 数据对象
    struct skiplistNode *backward;   // 后退指针
    struct skiplistLevel {
        struct skiplistNode *forward; // 前进指针
        unsigned int span;           // 跨度（到下个节点的距离）
    } level[];                      // 层级数组
} skiplistNode;
typedef struct skiplist {
    struct skiplistNode *header, *tail; // 头尾节点
    unsigned long length;               // 节点数量
    int level;                         // 最⼤层数
} skiplist;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 224 / 261

---

⽐如说我们执⾏ ZRANK 命令时，如果没有 span，就需要从头节点开始遍历每个节点，直到找到⽬标分值，这样时
间复杂度是 O(n) 。
但有了 span，我们在从⾼层往低层搜索的时候，可以直接跳过⼀些节点，快速定位到⽬标分值所在的范围。这样
就能把时间复杂度降到 O(log n) 。
// 没有span的排名查询 - O(n)
int getRankWithoutSpan(skiplist *zsl, double score, robj *obj) {
    skiplistNode *x = zsl->header->level[0].forward;
    int rank = 0;
    
    while (x) {
        if (x->score == score && equalStringObjects(x->obj, obj)) {
            return rank + 1;  // 排名从1开始
        }
        rank++;
        x = x->level[0].forward;
    }
    return 0;
}
long skiplistGetRank(skiplist *zsl, double score, robj *obj) {
    skiplistNode *x = zsl->header;
    unsigned long rank = 0;
    
    // 从最⾼层开始查找
    for (int i = zsl->level - 1; i >= 0; i--) {
        while (x->level[i].forward &&
               (x->level[i].forward->score < score ||
                (x->level[i].forward->score == score &&
                 compareStringObjects(x->level[i].forward->obj, obj) < 0))) {
            
            rank += x->level[i].span;  // 累加跨度
            x = x->level[i].forward;
        }
        
        // 找到⽬标节点
        if (x->level[i].forward &&
            x->level[i].forward->score == score &&
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 225 / 261

---

为什么跳表的范围查询效率⽐字典⾼？
 
字典是通过哈希函数将键值对分散存储的，元素在内存中是⽆序分布的，没有任何顺序关系。⽽跳表本身就是有序
的数据结构，所有元素按照分值从⼩到⼤排列。
当需要进⾏范围查询时，字典必须遍历所有元素，逐个检查每个元素是否在指定范围内，时间复杂度是 O(n) 。⽐
如要找分值在 60 到 80 之间的所有元素，字典只能把整个哈希表扫描⼀遍，因为它⽆法知道符合条件的元素在哪
⾥。
⽽跳表的范围查询就⾼效多了。⾸先⽤ O(log n) 时间找到范围的起始位置，然后沿着底层的有序链表顺序遍
历，直到超出范围为⽌。总时间复杂度是 O(log n + k) ，其中 k 是结果集的⼤⼩。这种效率差异在数据量⼤的
时候⾮常明显。
            equalStringObjects(x->level[i].forward->obj, obj)) {
            rank += x->level[i].span;
            return rank;
        }
    }
    
    return 0;
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 226 / 261

---

这也是为什么 Redis 的 zset 要⽤跳表⽽不是纯哈希表的重要原因，因为 zset 经常需要 ZRANGE、
ZRANGEBYSCORE 这类范围操作。实际上 Redis 的 zset 是跳表和哈希表的组合：跳表保证有序性⽀持范围查询，
哈希表保证 O(1) 的单点查找效率，两者互补。
1. Java ⾯试指南（付费）收录的⼩⽶暑期实习同学 E ⼀⾯⾯试原题：为什么 hash 表范围查询效率⽐跳表
低
2. Java ⾯试指南（付费）收录的得物⾯经同学 8 ⼀⾯⾯试原题：跳表的结构
3. Java ⾯试指南（付费）收录的美团⾯经同学 4 ⼀⾯⾯试原题：Redis 跳表
4. Java ⾯试指南（付费）收录的阿⾥系⾯经同学 19 饿了么⾯试原题：跳表了解吗
memo：2025 年 6 ⽉ 8 ⽇，今天有球友发信息称赞 Java 进阶之路的内容写得好，说实话，我是有这个⾃信的，基
本上所写的内容也都是我这些年从读到的所有书籍、视频、教程中提炼到的精华，把⼀些难懂晦涩的知识都⽤通俗
易懂的语⾔表达出来，配合⼿绘图，能让⼈更容易理解。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 227 / 261

---

53.压缩列表了解吗？
 
答：压缩列表是 Redis 为了节省内存⽽设计的⼀种紧凑型数据结构，它会把所有数据连续存储在⼀块内存当中。
整个结构包含头部信息，如总的字节数、尾部偏移量、节点数量，以及连续的节点数据。
当 list、hash 和 set 的数据量较⼩且值都不⼤时，底层会使⽤压缩列表来实现。
通常情况在，每个节点包含三个部分：前⼀个节点的⻓度、编码类型和实际的数据。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 228 / 261

---

前⼀个节点的⻓度是为了⽀持从后往前遍历；当前⼀个节点的⻓度⼩于 254 字节时，使⽤ 1 字节存储；否则⽤ 5 
字节存储，第⼀个字节设置为 254，后四个字节存储实际⻓度。
编码类型会根据数据的实际情况选择最紧凑的存储⽅式。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 229 / 261

---

但压缩列表有个致命问题，就是连锁更新。当插⼊或删除节点导致某个节点⻓度发⽣变化时，可能会影响后续所有
节点存储的“前⼀个节点⻓度”字段，最坏情况下时间复杂度会退化到 O(n²) 。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 230 / 261

---

编码
⻓度
描述
1字节
int16_t类型整数，2 字节
1字节
int32_t类型整数，4 字节
1字节
int64_t类型整数，8 字节
1字节
24位有符号整数 ，3 字节
1111xxxx
1字节
数据范围在[0-12]，数据包含在编码中
ziplist 的节点数量会超过 65535 吗？
 
不会。
Zllen 字段的类型是 uint16_t ，最⼤值为 65535，也就是 2 的 16次⽅，所以压缩列表的节点数量不会超过 
65535。
当节点数量⼩于 65535 时，该字段会存储实际的数量；否则该字段就固定为 65535，实际存储的数量需要逐个遍
历节点来计算。
ziplist 的编码类型了解多少？
 
ziplist 的编码类型设计得很精巧，主要分为字符串编码和整数编码两⼤类，⽬的是⽤最少的字节存储数据。
⽐如 0 到 12 这些⼩整数直接编码在 type 字段中，只需要 1 个字节。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 231 / 261

---

编码
⻓度
描述
00pppppp
1字节
0-63 字节的字符串
01pppppp qqqqqqqq
2字节
64-16383字节的字符串
10__ qqqqqqqq rrrrrrrr ssssssss tttttttt
5字节
16384-4294967295字节的字符串
对于字符串编码，根据字符串⻓度有三种格式。⻓度⼩于 63 字节的⽤ 00 开头的单字节编码，剩余 6 位存储⻓
度。⻓度在 63 到 16383 之间的⽤ 01 开头的双字节编码，剩余 14 位存储⻓度。超过 16383 字节的⽤ 10 开头，
后⾯跟 4 字节存储⻓度。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 232 / 261

---

1. Java ⾯试指南（付费）收录的同学 30 腾讯⾳乐⾯试原题：什么情况下使⽤压缩列表
memo：2025 年 6 ⽉ 9 ⽇修改⾄此，今天有球友特意发私信，感谢⾯渣逆袭对他的帮助。对，这么棒的内容，我
依然选择了免费，因为我相信知识是有价值的，只有诚恳的分享出来才能让更多⼈受益。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 233 / 261

---

54.quicklist 了解吗？
 
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 234 / 261

---

quicklist 是 Redis 在 3.2 版本时引⼊的，专⻔⽤于 List 的底层实现，它实际上是⼀个混合型数据结构，结合了压缩
列表和双向链表的优点。
在早期的版本中，List 会根据元素的数量和⼤⼩采⽤两种不同的底层数据结构，当元素较少或者较⼩时，会使⽤压
缩列表；否则⽤双向链表。
但这种设计有个问题，就是当 List 中的元素数量较多时，压缩列表会因为连锁更新导致性能下降，⽽双向链表⼜会
占⽤更多内存。
quicklist 通过将 List 拆分为多个⼩的 ziplist，再通过指针链接成⼀个双向链表，巧妙的解决了这个问题。
默认情况下，每个 ziplist 可以存储 8KB 的数据，假如每个元素的⼤⼩恰好是 1KB，那么⼀个 quicklist 就可以存储 
8 个元素。80 个这样的元素就会被分成 10 个 ziplist。
这样既保留了压缩列表的内存紧凑性，⼜减少了双向链表指针的数量，进⼀步降低了内存开销。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 235 / 261

---

除此之外，quicklist 还有⼀个重要的特性，就是它的可配置性，可以通过填充因⼦控制每个 ziplist 节点的⼤⼩。
当填充因⼦为正数时，它还可以限制每个 ziplist 最多包含的元素数量。
如果想进⼀步节省内存，quicklist 还⽀持对中间节点进⾏ LZF 压缩，压缩深度为 1 时，表示除了⾸尾各 1 个节点
不压缩外，其他节点都压缩。
# 填充因⼦，默认 -2（8KB）
list-max-ziplist-size 10
# 压缩深度，默认 0（不压缩）
list-compress-depth 1
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 236 / 261

---

LZF 压缩算法了解吗？
 
LZF 是⼀种快速的⽆损压缩算法，主要⽤于减少数据存储空间。它的核⼼思想是通过查找重复数据来实现压缩，通
过⼀个滑动窗⼝来查找重复的字节序列，并将这些序列替换为更短的引⽤。
memo：2025 年 6 ⽉ 10 ⽇，今天有球友发信息说找我修改了简历后，⼜按照星球的学习资料好好学了⼀下之
后，拿到了字节跳动的 offer，并特意发了⼀个⼤红包来感谢。这种被认可被需要的感觉，真好！
输⼊数据: "hello world hello redis"
步骤1: 处理 "hello world "
- 建⽴字典，记录字节序列位置
步骤2: 遇到重复的 "hello"
- 在字典中找到之前的 "hello" 位置
- ⽤ (距离, ⻓度) 对替换: (12, 5)
输出: "hello world " + (12,5) + " redis"
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 237 / 261

---

⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 238 / 261

---

补充
 
55.假如 Redis ⾥⾯有 1 亿个 key，其中有 10w 个 key 是以某个固定的已
知的前缀开头的，如何将它们全部找出来？
 
我会使⽤ SCAN 命令配合 MATCH 参数来解决。
⽐如要找以 user: 开头的 key，可以执⾏ SCAN 0 MATCH user:* COUNT 1000 。
SCAN 的优势在于它是基于游标的增量迭代，每次只返回⼀⼩批结果，不会阻塞服务器。可以从游标 0 开始，每次
处理返回的 key 列表，然后⽤返回的下⼀个游标继续扫描，直到游标回到 0 表示扫描完成。
使⽤ Spring Data Redis 的代码示例：
千万不要⽤ KEYS 命令，因为 KEYS 会阻塞 Redis 服务器直到遍历完所有 key，在⽣产环境中对 1 亿个 key 执⾏ 
KEYS 是⾮常危险的。
memo：2025 年 6 ⽉ 11 ⽇修改⾄此，今天有读者留⾔说，找实习的时候背了⼀个⽉的⾯渣逆袭，然后快⼿和美
团都拿到 offer 了。能帮助到⼤家，也是我做技术博主最开⼼的⼀件事情了，也感谢读者给的⼝碑。
@Service
public class RedisKeyService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public List<String> scanKeysByPrefix(String prefix, int batchSize) {
        List<String> keys = new ArrayList<>();
        
        ScanOptions options = ScanOptions.scanOptions()
                .match(prefix + "*")
                .count(batchSize)
                .build();
        
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        
        return keys;
    }
}
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 239 / 261

---

56.Redis在秒杀场景下可以扮演什么⻆⾊？
 
秒杀是⼀种⾮常特殊的业务场景，它的特点是在极短时间内会有⼤量⽤户涌⼊系统，对系统的并发处理能⼒、响应
速度和数据⼀致性都提出了极⾼的要求。在这种场景下，Redis 作为⼀种⾼性能的内存数据库，能够发挥多⽅⾯的
关键作⽤。
⽐如说在秒杀开始前，我们可以将商品信息、库存数据等预先加载到 Redis 中，这样⼤量的⽤户读请求就可以直接
从 Redis 中获取响应，⽽不必每次都去访问数据库，这样就能⼤⼤减轻数据库的访问压⼒。
其次，Redis 在库存控制⽅⾯具有得天独厚的优势。秒杀最核⼼的问题之⼀就是容易发⽣超卖。Redis 提供的原⼦
操作如 DECR、DECRBY 等命令，可以确保在⾼并发环境下库存计数的准确性。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 240 / 261

---

更复杂的逻辑，可以通过 Lua 脚本来实现，因为 Lua 脚本在 Redis 中是原⼦执⾏的，所以可以包含复杂的判断和
操作逻辑，⽐如先检查库存是否充⾜，再进⾏扣减，这整个过程是不会被其他操作打断的。
第三点，Redis 的分布式锁可以确保多个⽤户同时抢购同⼀件商品时的操作是互斥的，保证数据⼀致性的同时，还
可以⽤来防⽌⽤户重复下单。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 241 / 261

---

第四点，限流削峰。秒杀开始的瞬间，可能会有成千上万的请求同时到达，如果不加控制，很容易导致系统崩溃。
Redis 可以实现多种限流算法，⽐如简单的计数器限流、令牌桶或漏桶算法等。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 242 / 261

---

通过限流算法我们可以控制单位时间内系统能够处理的请求数量，超出部分可以排队或者直接拒绝，从⽽保护系统
的稳定运⾏。
memo：2025 年 6 ⽉ 12 ⽇修改⾄此，今天有球友发信息说，⼤⼆就拿下了美团的实习 offer，特意发来感谢，说
我的付出对他有着巨⼤的帮助，真的很感动，每⼀个懂得感恩的球友，你们也是我坚持下去的最强动⼒。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 243 / 261

---

Redis具体如何实现削峰呢？
 
削峰的本质是将瞬时的⾼流量请求缓冲起来，通过排队、限流等机制，使系统以⼀个可承受的速度来处理请求。
那第⼀步就是缓存预热。在秒杀活动开始前，先把商品信息这些热点数据提前加载到 Redis 中。这样⽤户访问商品
⻚⾯时，可以直接从 Redis 读取，数据库基本上不会有压⼒。
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 244 / 261

---

第⼆步是引⼊消息队列，特别是下单这种写操作，不能让⽤户等太久，但后端处理订单、扣库存这些操作⼜⽐较
重。所以可以⽤ Redis 的 List 做了个队列，或者直接⽤ RocketMQ 这种标准的消息中间件，⽤户下单后⽴即返
回"订单提交成功"，然后把订单数据丢到队列⾥，后台服务慢慢消费。这样既保证了⽤户体验，⼜避免了系统被瞬
时写请求压垮。
第三步，可以在秒杀活动中加⼊答题环节，只有答对题⽬的⽤户才能参与秒杀活动，这样可以最⼤程度减少⽆效请
求。
⼀个⽐较完整的秒杀削峰处理⽅案：
@Service
public class SeckillServiceImpl implements SeckillService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 245 / 261

---

private CommodityService commodityService;
    
    /**
     * 秒杀请求⼊⼝
     */
    public Result seckill(Long userId, Long commodityId) {
        // 1. ⽤户请求频率限制
        if (!countRateLimit("user:" + userId, 5, 60)) {
            return Result.error("请求过于频繁");
        }
        
        // 2. 商品是否在秒杀时间内
        if (!isInSeckillTime(commodityId)) {
            return Result.error("秒杀未开始或已结束");
        }
        
        // 3. 是否还有库存(快速失败)
        String stockKey = "seckill:stock:" + commodityId;
        Integer stock = Integer.valueOf(redisTemplate.opsForValue().get(stockKey));
        if (stock != null && stock <= 0) {
            return Result.error("商品已售罄");
        }
        
        // 4. 全局限流
        if (!acquireToken("global", 1000, 100)) {
            // 系统负载过⾼，将请求放⼊队列延迟处理
            enqueueDelayedRequest(userId, commodityId);
            return Result.success("秒杀请求已受理，排队处理中");
        }
        
        // 5. 检查⽤户是否已购买
        if (hasUserBought(userId, commodityId)) {
            return Result.error("您已经购买过该商品");
        }
        
        // 6. 将请求放⼊队列，返回排队状态
        String requestId = generateRequestId(userId, commodityId);
        enqueueRequest(userId, commodityId, requestId);
        
        return Result.success("秒杀请求已提交，请等待结果", requestId);
    }
    
    /**
     * 异步处理秒杀请求
     */
    @Scheduled(fixedRate = 50) // 每50ms处理⼀批
    public void processSeckillQueue() {
        String queueKey = "seckill:queue";
        
        // 批量处理，控制处理速度
        for (int i = 0; i < 10; i++) {
            String requestJson = redisTemplate.opsForList().leftPop(queueKey);
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 246 / 261

---

if (requestJson == null) {
                break;
            }
            
            SeckillRequest request = JSON.parseObject(requestJson, SeckillRequest.class);
            try {
                // 执⾏秒杀核⼼逻辑
                boolean success = doSeckill(request.getUserId(), 
request.getCommodityId());
                
                // 更新请求状态，便于⽤户查询
                String statusKey = "seckill:status:" + request.getRequestId();
                redisTemplate.opsForValue().set(statusKey, success ? "SUCCESS" : 
"FAILED", 1, TimeUnit.HOURS);
                
            } catch (Exception e) {
                log.error("处理秒杀请求失败", e);
                // 记录失败状态
                String statusKey = "seckill:status:" + request.getRequestId();
                redisTemplate.opsForValue().set(statusKey, "ERROR", 1, TimeUnit.HOURS);
            }
        }
    }
    
    /**
     * 秒杀核⼼逻辑
     */
    private boolean doSeckill(Long userId, Long commodityId) {
        // 使⽤Lua脚本保证原⼦性操作
        String script = 
            "-- 检查库存\n" +
            "local stockKey = KEYS[1]\n" +
            "local stock = tonumber(redis.call('get', stockKey))\n" +
            "if stock == nil or stock <= 0 then\n" +
            "    return 0\n" +
            "end\n" +
            "\n" +
            "-- 检查是否重复购买\n" +
            "local boughtKey = KEYS[2]\n" +
            "local hasBought = redis.call('sismember', boughtKey, ARGV[1])\n" +
            "if hasBought == 1 then\n" +
            "    return -1\n" +
            "end\n" +
            "\n" +
            "-- 扣减库存并记录购买\n" +
            "redis.call('decr', stockKey)\n" +
            "redis.call('sadd', boughtKey, ARGV[1])\n" +
            "\n" +
            "-- 返回成功\n" +
            "return 1";
        
        String stockKey = "seckill:stock:" + commodityId;
⾯渣逆袭 Redis篇第⼆版-让天下所有的⾯渣都能逆袭
No. 247 / 261

---

Redis如何做限流呢？
 
限流是为了控制系统的请求速率，防⽌系统被过多的请求压垮。
Redis 实现限流最简单的⽅法是基于计数器的固定窗⼝限流。⽐如限制⽤户每分钟最多访问 100 次，我们就⽤ 
INCR 命令给每个⽤户设个计数器，key 是 rate_limit:⽤户ID:分钟时间戳，每次请求就加 1，同时设置 60 秒过
期。如果计数超过 100 就拒绝请求。
这种⽅法简单粗暴，但有个问题就是临界时间会有突刺，⽐如⽤户在第 59 秒访问了 100 次，第 61 秒⼜访问 100 
次，相当于 2 秒内访问了 200 次。
第⼆种就是滑动窗⼝限流，通过 Redis 的 ZSET 来实现，把每次请求的时间戳作为 score 存进去，然后⽤ 
ZREMRANGEBYSCORE 删除窗⼝外的旧数据，再⽤ ZCARD 统计当前窗⼝内的请求数。这样限流就⽐较均匀了。
        String boughtKey = "seckill:bought:" + commodityId;
        
        Long result = (Long) redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Arrays.asList(stockKey, boughtKey),
            userId.toString()
        );
        
        if (result == 1) {
            // 创建订单(可以进⼀步异步化)
            createOrder(userId, commodityId);
            return true;
        }
       return false;
}

// 其他辅助方法...

// 伪代码
String key = "rate_limit:" + userId;
Long count = redis.get(key);
if (count == null) {
    redis.setex(key, 60, "1");
    return true;
}
if (count < maxRequests) {
    redis.incr(key);
    return true;
}
return false;

在实际开发中，通常会采用令牌桶算法，它就像在帝都/魔都买车，摇到号才有资格，没摇到就只能等下一次。可以在 Redis 里存两个值，一个是令牌数量，一个是上次更新时间。每次请求时用 Lua 脚本计算应该补充多少令牌，然后判断是否有足够的令牌。

// 伪代码
String key = "sliding_window:" + userId;
long now = System.currentTimeMillis();
redis.zadd(key, now, String.valueOf(now));
redis.zremrangeByScore(key, 0, now - windowSize);
redis.expire(key, windowSize / 1000 + 1);
Long count = redis.zcard(key);
return count <= maxRequests;

-- Redis Lua脚本实现令牌桶算法
local key = KEYS[1]
local max_permits = tonumber(ARGV[1])
local permits_per_second = tonumber(ARGV[2])
local required_permits = tonumber(ARGV[3])
local time = redis.call('time')
local now_micros = tonumber(time[1]) * 1000000 + tonumber(time[2])
local last_micros = tonumber(redis.call('hget', key, 'last_micros') or 0)
local stored_permits = tonumber(redis.call('hget', key, 'stored_permits') or 0)
local interval_micros = now_micros - last_micros
local new_permits = interval_micros * permits_per_second / 1000000
stored_permits = math.min(max_permits, stored_permits + new_permits)
local result = 0
if stored_permits >= required_permits then
    stored_permits = stored_permits - required_permits
    result = 1
end
redis.call('hset', key, 'last_micros', now_micros)
redis.call('hset', key, 'stored_permits', stored_permits)
redis.call('expire', key, 10)
return result

# 针对低延迟场景，设置为60秒，表示每60秒发送一次keepalive探测
config set tcp-keepalive 60

当客户端与服务器在指定时间内没有任何数据交互时，Redis 服务器会发送 TCP ACK 探测包，如果连续多次没有收到响应，TCP 协议栈会通知 Redis 服务端连接已断开，之后，Redis 服务端会清理相关的连接资源，释放连接。另外还有一个 timeout 参数，用来控制客户端连接的空闲超时时间。默认值为 0，表示永不断开连接；当设置为非零值时，如果客户端在指定时间内没有发送任何命令，服务端会主动断开连接。Redis 服务器会定期检查空闲连接是否超时，检查频率由 hz 参数控制；这将有助于释放那些客户端异常退出但 TCP 连接未正常关闭的资源。不同的连接池也会有自己的连接检测机制，比如 Jedis 连接池可以通过设置 testOnBorrow 和 testWhileIdle 来启用连接检测。

# 表示600秒内没有任何命令则断开连接
config set timeout 600
# 是否启用连接池
spring.redis.jedis.pool.enabled=true
# 连接池最大连接数（使用负值表示没有限制）
spring.redis.jedis.pool.max-active=200
# 连接池最大空闲连接数
spring.redis.jedis.pool.max-idle=200
# 连接池最小空闲连接数
spring.redis.jedis.pool.min-idle=50
# 连接池最大阻塞等待时间（使用负值表示没有限制）
spring.redis.jedis.pool.max-wait=3000
# 空闲连接检查间隔（毫秒）
spring.redis.jedis.pool.time-between-eviction-runs=60000

---

## 图表说明

> [图] 第1页：有技术知识点。该图是《面渣逆袭 Redis 篇》的宣传封面，内容涉及 Redis 相关的面试题和技术解析，包含 57 道面试常见问题和 286 张手绘图，旨在帮助学习者掌握 Redis 核心知识以应对技术面试。

> [图] 第2页：有技术知识点。该图展示了PmHub分布式锁的业务流程，涉及项目服务与流程服务之间的交互，包括新建项目、任务审批、状态通知和回调等环节，体现了分布式系统中锁机制的应用场景。

> [图] 第3页：有技术知识点。该图展示了Redis中listpack替代ziplist的原因，主要为解决压缩列表的连锁更新问题，listpack通过每个节点独立记录长度信息，避免了依赖前一个节点长度导致的性能退化。

> [图] 第3页：这张图是二维码，中心嵌入了一张人物照片，属于装饰性二维码。技术知识点：二维码用于存储信息并可通过扫描设备读取，中心添加图片不影响其基本功能，但可能影响扫描成功率。

> [图] 第4页：有技术知识点。图中展示了多个与Java技术相关的学习资料，包括JVM、Spring、MyBatis、Redis、并发编程等主题的PDF和Markdown文档，适合Java开发者进阶学习使用。

> [图] 第5页：有技术知识点。该图展示了在Redis集群环境中热Key（热点键）导致的数据分布不均衡问题，左侧为正常情况，右侧表示某个缓存节点因热Key被频繁访问而过载，其他节点相对空闲，体现了热Key对系统性能和资源利用率的影响。

> [图] 第6页：这张图展示了 Redis 服务器启动时的控制台输出信息，包含版本、运行模式、端口、PID 等关键配置信息，属于典型的系统日志输出，具有技术知识点。  
**一句话描述：Redis 7.2.5 服务启动日志，显示其在独立模式下运行于 6379 端口，PID 为 67692，并提示未指定配置文件和 TCP backlog 警告。**

> [图] 第7页：有技术知识点。该图展示了典型的缓存读取流程：Web服务先查询Redis缓存，命中则直接返回，未命中则查询MySQL数据库并写入缓存后返回。

> [图] 第8页：有技术知识点。该图展示了使用Redis的zset数据结构实现用户活跃排行榜的技术方案，包括活跃积分更新策略、榜单评分机制及具体实现步骤。

> [图] 第9页：有技术知识点。该图展示了一个关于Spring Boot中集成Redis缓存的技术文档，内容包括Redis的配置、使用RedisTemplate执行操作、缓存session和数据结构应用等实际开发中的技术细节。

> [图] 第10页：有技术知识点。该图展示了Redis分布式锁的业务逻辑流程，重点说明了在锁过期释放时如何避免误删他人锁的问题，核心逻辑在于判断当前持有锁的线程是否为自身，从而决定是否删除锁。

> [图] 第13页：有技术知识点。该图展示了典型的Web服务缓存架构，用户请求先访问缓存层（Redis），若命中则直接返回，未命中则查询存储层（MySQL）并写入缓存，实现读写分离和性能优化。

> [图] 第15页：有技术知识点。该图展示了PmHub系统中通过Redis分布式锁保障流程状态更新的业务流程，涉及项目服务与流程服务之间的协同，以及在任务审批设置时对流程状态的加锁与释放机制。

> [图] 第17页：是的，这张图有技术知识点。它展示了Redis中一个key可以对应五种不同的数据结构：字符串、哈希、列表、集合和有序集合。

> [图] 第18页：有技术知识点。该图展示了不同数据类型在内存或存储中的表示方式，包括字符串、JSON对象、数值和位数组，体现了数据结构与数据序列化的基本概念。

> [图] 第18页：有，这张图展示了Redis中列表（List）数据结构的操作，包括从左侧（lpush/lpop）和右侧（rpush/rpop）添加或移除元素，以及获取指定范围的元素（lrange），并标明了列表长度（llen）。

> [图] 第19页：有技术知识点。该图展示了Redis中String和Hash两种数据结构的存储方式对比：String以键值对形式分别存储用户属性，而Hash将多个字段聚合在一个键下，更高效地存储对象数据。

> [图] 第20页：有技术知识点。该图展示了用户排名数据的结构，表示通过键 `user:ranking` 可以查询到用户对应的排名信息，属于Redis等键值存储系统中常见的数据模型应用。

> [图] 第20页：有技术知识点。该图展示了键值对（Key-Value）数据结构，其中“user:1:tom”作为键，对应一个包含多个标签（如IT、Music、Sport等）的值，常用于数据库或缓存系统中表示用户兴趣标签。

> [图] 第21页：有技术知识点。该图展示了 Redis 中字符串对象的内存结构，包括 RedisObject 指向 SDS（Simple Dynamic String）结构，SDS 包含 free、len 和 buf 字段，其中 buf 存储实际字符串数据并以 '\0' 结尾。

> [图] 第21页：这张图展示了哈希表中基于8位哈希值的桶（Bucket）分配和计数机制，通过前3位确定桶号，后5位统计0的数量，用于数据分布分析。

> [图] 第22页：有技术知识点。该图展示了一个用于计算HLL（HyperLogLog）算法中估计值的公式，其中DV_HLL表示去重计数的估计值，公式涉及桶的数量m和每个桶的估计值的调和平均数。

> [图] 第23页：有技术知识点。该图展示了 Redis 中 Geo 功能的底层实现原理：使用有序集合（zset）作为底层数据结构，通过跳表（skip list）存储地理位置信息，并以 geohash 字符串的十进制值作为 score 进行排序。

> [图] 第25页：有技术知识点。该图展示了在Redis中使用字符串类型和Hash类型存储用户信息的两种方式，对比了键值对结构与哈希结构在数据组织上的差异。

> [图] 第28页：有技术知识点。该图展示了Redis服务器基于I/O多路复用的事件处理机制，包括客户端连接、事件监听、消息队列和文件事件分派器如何协同工作，体现了Redis单线程模型的核心架构。

> [图] 第28页：有技术知识点。该图展示了Redis服务器的事件处理流程，包括socket连接、I/O多路复用、事件分发以及命令请求与回复的处理机制。

> [图] 第28页：这张图展示了不同数据访问和操作的延迟层级，从纳秒级的CPU缓存到秒级的网络和系统操作，体现了计算机系统中性能差异的技术知识点。

> [图] 第29页：有技术知识点。该图展示了Redis服务器基于epoll的事件驱动模型，描述了客户端连接、事件分发、命令处理和数据发送的完整流程。

> [图] 第30页：有技术知识点。该图展示了Redis 6中多线程处理网络I/O与单线程串行处理计算的架构，通过epoll并发处理客户端连接，并利用多个IO线程进行读写操作，主进程负责命令执行，实现高性能的并发处理。

> [图] 第30页：有技术知识点。该图展示了Redis 6之前版本的单线程模型，说明了其通过epoll并发处理socket连接，而IO操作由主线程串行处理，分析了其优缺点：优势在于无锁和避免线程切换开销，劣势是无法利用多核CPU且存在阻塞风险。

> [图] 第30页：有技术知识点。该图描述了Redis中主线程与IO多线程协作处理客户端请求的流程，包括socket读取、命令解析、执行Redis命令、结果写回等步骤，体现了IO多线程模型在提升并发处理能力中的应用。

> [图] 第31页：有技术知识点。该图展示了Redis中主线程与IO多线程协作处理客户端请求的流程，包括socket读取、命令解析、执行Redis命令、结果写回等步骤，体现了IO多线程模型在提升并发处理能力中的应用。

> [图] 第32页：有技术知识点。该图对比了Redis 3.0与最新版本中各种数据类型（String、List、Hash、Set、ZSet）所使用的底层数据结构，展示了从双向链表、压缩列表到quicklist、listpack等结构的演进。

> [图] 第33页：有技术知识点。该图解释了Redis为何如此快速，主要归因于其基于内存（RAM）的存储、I/O多路复用与单线程读写模型，以及高效的数据结构（如SDS、跳表等）。

> [图] 第34页：有技术知识点。该图展示了I/O多路复用模型的工作流程，描述了应用程序通过select系统调用等待多个文件描述符的数据就绪，内核在数据准备就绪后通知应用，随后应用执行read操作将数据从内核复制到用户空间的过程。

> [图] 第35页：有技术知识点。该图展示了多线程服务器和Reactor模式的架构，分别描述了用户请求如何通过独立线程处理以及客户端连接如何通过Reactor、acceptor和线程池进行异步事件处理和任务分发。

> [图] 第37页：有技术知识点。该图展示了Redis的I/O多路复用模型，描述了如何通过单线程处理多个客户端连接的事件，包括文件描述符监听、事件分派和各类处理器的协作流程。

> [图] 第39页：有技术知识点。该图展示了阻塞I/O模型和I/O多路复用模型的工作流程，对比了用户线程与内核在数据读取过程中的交互方式，体现了不同I/O模型的处理机制差异。

> [图] 第40页：是的，这张图有技术知识点。它展示了Linux内核中`select`系统调用的执行流程，从用户空间的`select`函数调用开始，经过内核中的`SYSCALL_DEFINES`、`kern_select`、`core_sys_select`等关键函数，最终涉及`do_select`的内部实现细节，包括超时设置、等待队列初始化和文件操作的轮询机制。

> [图] 第41页：是的，这张图有技术知识点。它展示了Linux内核中`poll`系统调用的执行流程，从用户空间的`poll()`函数调用开始，经过内核的`do_sys_poll`、`do_poll`等函数，最终到达文件操作的`poll`方法，体现了I/O多路复用的实现机制。

> [图] 第41页：有技术知识点。该图展示了Linux内核中epoll机制的工作流程，包括epoll_create、epoll_ctl和epoll_wait等系统调用的内部实现逻辑，涉及事件注册、就绪列表管理以及等待唤醒机制。

> [图] 第42页：有技术知识点。Redis虽然是单线程的，但可以通过部署多个实例或利用Redis 4.0及以上版本的部分多线程特性来充分利用多核CPU。

> [图] 第42页：有技术知识点。该图展示了Redis文件事件处理器的架构，包括套接字、I/O多路复用程序（使用epoll）、套接字队列、文件事件分发器以及各类事件处理器（如命令请求、回复和连接应答处理器）的工作流程。

> [图] 第44页：有技术知识点。该图展示了Redis的并发处理机制：通过epoll处理多个客户端的socket连接，主进程串行执行命令（如set、get、incr），并通过队列将读写事件分发给多个IO线程进行网络I/O操作，实现单线程命令执行与多线程网络IO的结合。

> [图] 第45页：有技术知识点。该图展示了基于Reactor模式的多线程网络服务器架构，包含主线程中的Main Reactor负责接收连接，子线程中的Sub Reactor处理具体请求，通过IO多路复用和Dispatcher分发任务，实现高并发处理。

> [图] 第48页：有技术知识点。这是一组 Redis 命令的交互记录，展示了键值设置、过期时间控制、条件写入等核心操作。

> [图] 第49页：有技术知识点。该图展示了Redis命令行工具（redis-cli）中使用SET和INCR命令操作计数器的示例，体现了Redis的基本数据操作功能。

> [图] 第50页：这张图展示了每秒请求数量随时间变化的趋势，反映了系统负载或性能的动态变化。

> [图] 第51页：有技术知识点。这是一张Redis性能测试结果的截图，展示了使用redis-benchmark工具对本地Redis服务器进行压力测试的详细数据，包括请求完成时间、并发客户端数、延迟分布等关键性能指标。

> [图] 第52页：有技术知识点。该图展示了Redis持久化的两种方式：RDB（快照）和AOF（追加日志），分别将数据保存到磁盘中以实现数据持久化。

> [图] 第53页：有技术知识点。该图对比了Redis中AOF（Append-Only File）和RDB（Redis DB）两种持久化机制的工作流程：AOF通过记录每个写命令到磁盘实现数据持久化，而RDB通过fork子进程在内存中创建数据快照并保存到磁盘。

> [图] 第54页：是的，这张图有技术知识点。它展示了Redis的RDB持久化机制：在崩溃前将内存中的数据快照保存到磁盘上的RDB文件中，崩溃后通过读取RDB文件恢复数据。

> [图] 第55页：是的，这张图有技术知识点。它展示了Redis中`save`和`bgsave`两种持久化方式的区别：`save`是阻塞式的，主线程执行保存操作；而`bgsave`通过`fork`创建子进程异步执行保存，避免阻塞主线程。

> [图] 第55页：有技术知识点。图中展示了Redis数据库的保存命令操作，`save` 是同步保存数据到磁盘，`bgsave` 是异步后台保存，用于持久化数据。

> [图] 第56页：有技术知识点。该图描述了Redis中BGSAVE命令的执行流程：父进程在收到bgsave指令后，若无其他子进程正在运行，则 fork 一个子进程，由子进程生成RDB文件并写入磁盘，同时父进程继续响应其他命令，子进程完成保存后通过信号通知父进程。

> [图] 第57页：有技术知识点。该图描述了Redis主从复制过程中，主节点（master）与从节点（slave）之间的同步流程，包括生成RDB快照、发送RDB文件、接收并应用积压命令等步骤。

> [图] 第58页：是的，这张图有技术知识点。它展示了Redis中命令处理流程和持久化机制，包括命令执行、内存状态更新以及将命令写入AOF（Append-Only File）文件的过程。

> [图] 第58页：有技术知识点。该图描述了Redis AOF（Append Only File）持久化机制的工作流程，包括命令写入、追加到AOF缓冲区、同步到AOF文件、重写优化以及重启时加载的过程。

> [图] 第59页：有技术知识点。该图展示了Redis在不同持久化策略（Always、EverySec、No）下，大键对持久化过程的影响，包括主线程写入内存、系统调用写入内核缓冲区以及最终同步到磁盘的流程差异。

> [图] 第60页：有技术知识点。该图展示了Redis AOF（Append Only File）重写机制，通过将多个操作命令优化为一条命令来减少日志大小并提升恢复效率。

> [图] 第60页：有技术知识点。该图描述了Redis中AOF（Append Only File）持久化机制的重写过程，包括主进程与子进程协作完成AOF文件的优化与更新流程。

> [图] 第62页：是的，这张图包含技术知识点。它展示了 Redis 的 RESP（Redis Serialization Protocol）协议格式，用于表示客户端与服务器之间的通信数据，包括字符串、整数、数组等类型的数据结构。

> [图] 第63页：有技术知识点。该图展示了Redis AOF（Append Only File）持久化机制中的重写过程，主进程通过fork创建bgrewriteaof子进程，子进程基于当前内存数据生成新的AOF日志文件，以减少日志体积并提升恢复效率。

> [图] 第64页：这张图中的书籍《面渣逆袭》系列包含并发编程和JVM等Java技术知识点，适合准备技术面试的开发者学习。

> [图] 第65页：有技术知识点。该图展示了Redis的持久化机制，包括RDB快照和AOF（Append Only File）两种方式，以及在实例故障后如何通过持久化数据恢复服务。

> [图] 第66页：有技术知识点。该图展示了在高并发场景下通过Redis保证库存扣减的原子性，避免超卖问题，后续订单处理则由数据库完成。

> [图] 第66页：有技术知识点。该图展示了基于Redis的缓存读取流程：当Tomcat接收到请求时，先查询Redis缓存，若命中则直接返回数据；若未命中（缓存缺失），则从MySQL数据库读取数据并返回。

> [图] 第67页：有技术知识点。该图描述了Redis启动时根据AOF和RDB持久化机制的存在情况来决定加载方式的流程，体现了Redis的数据恢复逻辑。

> [图] 第68页：是的，这张图有技术知识点。它展示了Redis持久化机制中RDB（快照）和AOF（追加日志）的工作流程：在执行命令后，Redis会先对内存数据进行快照并保存到磁盘的RDB文件中，同时将后续命令追加到AOF文件中，以实现数据持久化和故障恢复。

> [图] 第68页：有技术知识点。该图展示了Redis中RDB快照与AOF日志的结合使用机制，其中RDB快照作为基础数据备份，AOF日志记录后续的增量操作，通过snapshot index和current index标识两者之间的关系。

> [图] 第71页：有技术知识点。该图展示了Redis主从复制的架构，其中Master节点将数据复制到多个Slave节点，实现数据冗余和读写分离。

> [图] 第71页：有技术知识点。该图展示了数据库主从复制架构，客户端写操作仅发送至主库，读操作可分发至多个从库，主库将写操作同步至所有从库以保持数据一致性。

> [图] 第72页：有技术知识点。该图展示了Redis Sentinel高可用架构，其中三个Sentinel节点相互监控并共同监控主从复制的Redis集群（master和slave），实现故障检测与自动故障转移。

> [图] 第73页：有技术知识点。该图描述了Redis主从复制中，客户端发送SET命令后，Master执行命令并异步将命令复制到Slave的流程。

> [图] 第74页：有技术知识点。该图描述了主从复制（Master-Slave Replication）中数据同步的时间窗口问题，展示了Master和Slave的有效时间区间以及可能存在的不安全时间段。

> [图] 第75页：有技术知识点。该图描述了数据库主从复制监控流程，通过比较主库和从库的复制偏移量（repl_offset）来判断从库复制延迟是否超过阈值，并在超时时移除其连接信息。

> [图] 第76页：有技术知识点。该图展示了一种Redis主从复制架构，即“一主一从”结构，其中Redis-A为主节点，Redis-B为从节点，用于实现数据冗余和读写分离。

> [图] 第76页：有技术知识点。该图展示了一种Redis的“一主多从（星形）结构”，即一个主节点（Redis-A）连接多个从节点（Redis-B、Redis-C、Redis-D、Redis-E），用于实现数据复制和读写分离。

> [图] 第77页：有技术知识点。该图展示了Redis主从复制的完整流程，包括建立连接、全量同步（RDB文件传输）和增量同步（repl buffer发送）三个阶段。

> [图] 第77页：有技术知识点。该图展示了一种Redis的树状主从复制结构，其中Redis-A作为主节点，向下级联多个从节点（Redis-B、Redis-C等），形成层次化的数据复制拓扑。

> [图] 第78页：有技术知识点。该图展示了一种主从架构的通信模型，其中主进程通过缓冲区（buffer）将数据分发给多个从进程（Slave），体现了典型的生产者-消费者模式或多线程任务分发机制。

> [图] 第79页：有技术知识点。该图描述了Redis主从复制中Slave节点执行SLAVEOF命令后，根据是否为首次复制决定发送PSYNC命令的流程，进而判断是进行完整同步还是部分同步的过程。

> [图] 第80页：有技术知识点。该图描述了Redis主从复制的同步过程，包括全量同步（RDB文件传输）和增量同步（replication_buffer和repl_backlog_buffer机制），展示了主实例与多个从实例之间的数据复制流程。

> [图] 第80页：有技术知识点。该图描述了Redis主从复制过程中的全量同步流程，包括Slave向Master发送SYNC命令、Master生成RDB文件并传输给Slave、Slave加载RDB文件以及后续的增量命令传播等步骤。

> [图] 第81页：有技术知识点。该图展示了主从架构（Master-Slave）中主进程通过一个中间组件与从节点通信的典型模式。

> [图] 第82页：有技术知识点。该图描述了Redis主从复制过程中网络断开与恢复时的同步机制，包括主从库通过`psync runid offset`进行偏移量匹配、使用`repl_backlog_buffer`缓冲写命令，并在网络恢复后继续同步的过程。

> [图] 第83页：有技术知识点。该图描述了Redis主从复制过程中Slave节点与Master节点进行全量同步（FULLRESYNC）的完整流程，包括psync请求、RDB文件传输、AOF配置判断等关键步骤。

> [图] 第84页：有技术知识点。该图描述了Redis主从复制中，从节点（slave）在连接丢失后重新连接主节点（master）并进行数据同步的过程，包括连接恢复、PSYNC命令发送、主节点返回CONTINUE以及部分数据传输等步骤。

> [图] 第85页：有技术知识点。该图描述了Redis主从复制中，当Slave与Master断开连接后重新连接时的恢复流程，包括通过repl_backlog环形缓冲区和replication buffer实现增量同步的过程。

> [图] 第86页：有技术知识点。该图描述了Redis主从复制过程中RDB持久化与数据同步的流程，包括Master生成RDB文件、Slave接收并应用RDB文件以及后续命令重放的过程。

> [图] 第86页：有技术知识点。该图描述了在主从复制架构中，因网络故障导致脑裂（Split-Brain）问题的场景，展示了主节点与从节点在通信中断后各自独立处理写操作，造成数据丢失和重复写入的风险，以及哨兵机制如何触发主从切换的过程。

> [图] 第87页：是的，这张图包含技术知识点。它描述了数据库主从复制架构中因主库阻塞导致的主从切换过程，以及在此过程中可能引发的数据丢失问题。

> [图] 第88页：有技术知识点。该图展示了Redis Sentinel（哨兵）架构，用于实现Redis主从复制的高可用性，通过多个Sentinel节点监控Master和Slave节点，实现故障检测与自动故障转移。

> [图] 第88页：有技术知识点。该图展示了如何在 MinIO 中创建 Bucket，包括设置环境变量、启动 MinIO 服务以及通过控制台创建名为 "uploads" 的存储桶，属于对象存储配置的基础操作。

> [图] 第89页：有技术知识点。该图展示了Redis Sentinel高可用架构，其中多个Sentinel节点监控Master和Slave节点，实现故障检测与自动故障转移，并通知客户端。

> [图] 第90页：是的，这张图有技术知识点。它描述了Redis Sentinel集群中Sentinel节点之间的选举过程（Raft算法变种），展示了各Sentinel节点如何通过投票机制选出主Sentinel节点，用于故障转移和主从切换。

> [图] 第90页：有技术知识点。该图展示了Redis Sentinel高可用架构在主从切换前后的状态变化，包括Sentinel集群监控Redis主从节点、客户端读写操作以及故障转移后主从角色的切换过程。

> [图] 第90页：有技术知识点。该图描述了Redis Sentinel（哨兵）系统中主节点故障检测与切换的流程，包括主观下线、客观下线判断以及通过多数派（quorum）机制确认主节点失效的过程。

> [图] 第91页：有技术知识点。该图展示了Redis Sentinel高可用架构，包括主从复制、Sentinel监控与故障转移机制。

> [图] 第92页：有技术知识点。该图详细描述了Redis Sentinel故障转移的原理，包括定时任务、主观/客观下线判断、leader选举和failover流程等核心机制。

> [图] 第92页：这张图没有技术知识点。  
内容为用户对Java学习资源的评价和互动，属于社交评论性质，不包含具体的技术细节或代码知识。  
装饰/Logo/二维码：无

> [图] 第93页：有技术知识点。该图描述了分布式系统中基于投票机制的领导选举过程，展示了节点S1、S2、S3在不同时间点的投票行为和结果，体现了Raft或类似一致性算法的核心思想。

> [图] 第96页：有技术知识点。涉及的技术包括Java、Spring、Redis、Kafka、HBase、MySQL、消息队列、高可用架构设计等，主要集中在分布式系统、高并发处理和大数据存储方面。

> [图] 第97页：这张图中包含了一些技术知识点，主要涉及岗位职责和技术栈。例如提到了“Java、Go”、“分布式系统”、“高并发”、“微服务架构”等技术关键词，以及“蚂蚁集团”和“鹅厂”（腾讯）的技术团队背景。用一句话描述：该图展示了求职者在选择offer时对不同公司技术岗位的讨论，涉及Java、Go、分布式系统等技术栈。

> [图] 第98页：是的，这张图包含技术知识点。它描述了Redis主从复制中选举新主节点（master）的流程，主要依据节点的slave-priority、复制偏移量和runid进行决策。

> [图] 第101页：有技术知识点。该图展示了Redis集群的架构，多个Redis节点通过相互连接形成分布式集群，客户端可连接任意节点进行数据读写操作，体现了Redis集群的高可用性和负载均衡特性。

> [图] 第101页：有技术知识点。该图展示了数据切片（Sharding）技术，将25GB数据从单实例架构分散到5个实例的切片集群中，每个实例处理5GB数据，以提升系统扩展性和性能。

> [图] 第102页：有技术知识点。该图展示了Redis集群的架构，包括主从复制（replication）、客户端读写操作以及节点间的Gossip协议通信机制。

> [图] 第102页：有技术知识点。该图展示了基于哈希槽（Hash Slots）的分布式数据分片（Sharding）机制，通过CRC16哈希和取模运算将键“foo”映射到特定分片（Shard 2），实现数据在多个节点间的分布。

> [图] 第103页：有技术知识点。该图展示了分布式系统中槽（Slots）与节点（Node）的映射关系，用于数据分片或负载均衡，常见于Redis集群等分布式架构中。

> [图] 第103页：有技术知识点。该图展示了基于CRC16哈希算法和槽位映射的分布式数据存储机制，通过将key经过CRC16计算后取模16383得到槽位索引，再将槽位分配给不同节点（Node1、Node2）实现数据分片与负载均衡。

> [图] 第104页：这张图没有直接的技术知识点，主要是关于实习选择的讨论。但其中提到“百度不是标准的 Java 厂”，暗示了对不同公司技术栈和开发规范的差异性认知，可理解为一种隐含的技术文化对比。  
一句话描述：无明确技术知识点，但涉及企业技术文化差异的讨论。

> [图] 第105页：是的，这张图有技术知识点。它展示了一个分布式哈希表（DHT）中的环形结构，用于说明一致性哈希算法中节点与键的映射关系，常见于P2P网络或分布式系统中。

> [图] 第105页：有技术知识点。该图展示了基于哈希取模（hash%3）的分布式系统节点分配策略，将数据根据哈希值映射到三个节点（Node1、Node2、Node3）中的一个，常用于负载均衡或一致性哈希场景。

> [图] 第106页：有。该图展示了Redis集群的架构，包括客户端与主从节点之间的读写操作分布，以及数据通过哈希槽（hash slots）在不同分片（shard）中的划分方式。

> [图] 第107页：是的，这张图有技术知识点。它展示了一种基于CRC哈希函数和取模运算的哈希表数据结构设计，用于将键（Key）映射到特定的槽（Slot）中，实现数据的快速存储与检索。

> [图] 第109页：有技术知识点。该图展示了多个节点（编号6379至6384）通过“节点握手”过程形成一个集群（cluster）的架构，体现了分布式系统中节点间建立连接并组成集群的基本概念。

> [图] 第110页：是的，这张图有技术知识点。它描述了分布式系统中两个节点（A 和 B）通过 Gossip 协议进行发现、连接和状态同步的过程，涉及节点间握手、链接建立、信息交换及槽位（slots）更新等关键步骤。

> [图] 第111页：有技术知识点。该图展示了Redis主从复制架构，包括三个主节点（M1、M2、M3）和三个从节点（S1、S2、S3），通过gossip协议进行节点间通信，并说明了客户端读写操作分别访问主节点和从节点的机制。

> [图] 第112页：是的，这张图有技术知识点。它展示了Redis集群中槽（slot）的分配机制，具体说明了通过`cluster addslots`命令将哈希槽（0..5461）分配给不同的节点（如6379和6380），体现了Redis集群的数据分片原理。

> [图] 第112页：有技术知识点。该图展示了网络通信中的“ping-pong”机制，表示两个端口（6379 和 6384）之间的双向心跳检测或消息响应过程，常用于分布式系统或集群中节点间的健康检查与通信。

> [图] 第113页：是的，这张图有技术知识点。它描述了Redis哨兵（Sentinel）机制中节点故障检测与主客观下线判断的过程：当多个哨兵节点检测到主节点（如6384）不可达时，通过ping/pong通信和pfail状态上报，若超过半数哨兵认为主节点失效，则触发主观下线并尝试客观下线决策。

> [图] 第113页：有技术知识点。该图展示了分布式系统中主节点选举的机制，当某个主节点（如master b）失效时，其他主节点通过投票选举新的主节点，需获得超过N/2+1张选票（此处为3张）才能成功替换，体现了Raft或类似一致性算法的核心思想。

> [图] 第115页：有技术知识点。该图展示了集群节点的上下线过程，表示节点6381和6384从集群中下线，而节点6385和其他节点加入集群，体现了分布式系统中节点动态管理的概念。

> [图] 第116页：有技术知识点。该图展示了分布式系统中槽（slot）与节点之间的映射关系及槽迁移过程，体现了一致性哈希或类似分布式数据分片机制中的负载均衡和节点扩容/缩容逻辑。

> [图] 第118页：有技术知识点。该图描述了Redis客户端在集群模式下执行命令时的键命令处理流程，包括发送命令、计算Slot槽确定目标节点、判断当前节点是否为目标节点，以及重定向或执行命令的过程。

> [图] 第119页：有技术知识点。该图展示了Redis集群中槽（Slot）迁移过程中客户端与源节点、目标节点之间的交互流程，包括查询重定向和ASK命令的使用。

> [图] 第119页：这张图没有技术知识点。

> [图] 第120页：有技术知识点。该图展示了缓存穿透问题：当热点数据在缓存中失效时，大量请求直接打到数据库，导致数据库压力激增。

> [图] 第121页：有技术知识点。该图描述了缓存击穿问题，即大量线程在缓存key过期时同时访问数据库，可能引发数据库压力骤增，需通过synchronized或ReentrantLock等机制加锁来解决。

> [图] 第123页：有技术知识点。该图展示了缓存穿透问题：当缓存中不存在数据且数据库中也无对应数据时，大量请求直接打到数据库，导致数据库压力剧增。

> [图] 第124页：有技术知识点。该图描述了一种基于Bloom Filter和Redis的流量拦截与数据库查询优化流程，用于在访问MySQL前通过Bloom Filter快速判断数据是否存在，减少不必要的数据库查询。

> [图] 第125页：有技术知识点。该图描述了缓存穿透问题：当应用系统第一次请求某个key时，缓存中无数据，从数据库查询返回null并写入缓存；第二次请求相同key时，缓存中存储的是null值，导致后续请求仍会访问数据库，造成缓存穿透。

> [图] 第127页：有技术知识点。内容涉及Java四大件、MySQL（mydb）、后端开发、测开（测试开发）岗位要求以及实习经历对求职的影响。

> [图] 第128页：有技术知识点。该图描述了“缓存雪崩”现象：当缓存中的大量数据同时过期或缓存服务突然宕机时，大量请求直接打到数据库，导致数据库压力剧增甚至崩溃。

> [图] 第129页：有技术知识点。该图展示了Redis集群的架构，包括主从复制（replication）、客户端读写操作以及节点间的gossip协议通信机制。

> [图] 第129页：有技术知识点。该图展示了如何在项目中整合Caffeine作为本地缓存，使用@Cacheable注解实现缓存功能，并提供了配置和代码示例。

> [图] 第131页：这张图中没有直接的技术知识点，主要是一段关于求职选择的对话，涉及个人背景（单2硕计算机）、面试经历和offer抉择问题。其中提到“简历项目也是用的技术派，面试看的面渣”，表明其技术背景和求职困境，但未具体展开技术细节。

**一句话描述：** 无具体技术知识点，仅提及求职者的技术背景与面试表现。

> [图] 第132页：有技术知识点。该图展示了布隆过滤器（Bloom Filter）的工作原理，通过多个哈希函数将键映射到位数组中的不同位置。

> [图] 第132页：这张图中没有明确的技术知识点，主要是一段关于求职选择的对话内容，涉及个人背景、offer抉择和职业建议等话题。因此，技术知识点为：无。

> [图] 第134页：有技术知识点。该图展示了布隆过滤器（Bloom Filter）的工作原理，通过三个哈希函数将元素X和Y映射到bit数组的特定位置，并设置对应位为1。

> [图] 第134页：这张图展示了布隆过滤器中不同哈希函数数量（k）对误判率（False positive probability）的影响，随着位数组大小（m）的增加，误判率显著降低。

> [图] 第137页：有技术知识点。该图中提到了算法题的准备，如“数组链表二叉树的高频题”，以及面试中手撕代码的经历，例如“手撕hashmap”，涉及数据结构与算法的实际应用。

> [图] 第138页：有技术知识点。该图描述了在MySQL和Redis缓存一致性场景下，“先写MySQL，再删除Redis”方案的工作流程及可能产生的数据不一致问题。

> [图] 第139页：有技术知识点。该图展示了在高并发场景下，请求A和请求B对MySQL和Redis进行更新操作时可能出现的数据不一致问题，体现了缓存与数据库双写一致性的问题。

> [图] 第140页：有技术知识点。该图展示了在分布式系统中，请求A和请求B并发操作Redis缓存和MySQL数据库时可能发生的缓存与数据库数据不一致问题，具体表现为：请求B在查询缓存未命中后从MySQL读取数据（值为10），但此时请求A已将MySQL更新为11，而请求B仍回写旧值10到缓存，导致缓存数据滞后于数据库。

> [图] 第140页：有技术知识点。该图描述了缓存更新的两种常见操作：删除缓存和更新缓存，通常用于数据库与缓存系统（如Redis）之间的数据一致性处理。

> [图] 第141页：有技术知识点。该图展示了缓存与数据库更新时序中“先更新数据库后删除缓存”的操作流程，避免了脏数据的产生。

> [图] 第142页：有技术知识点。该图展示了缓存一致性问题的四种解决方案：通过消息队列保证缓存被删除、数据库订阅结合消息队列、延时双删防止脏数据、以及设置缓存过期时间兜底。

> [图] 第142页：有技术知识点。该图描述了在数据库更新时，通过消息队列异步删除缓存的流程，避免缓存与数据库数据不一致的问题。

> [图] 第143页：有技术知识点。该图描述了基于binlog监听实现数据库与缓存一致性的一种架构方案，通过监听数据库的binlog日志，异步删除缓存中的对应数据，确保数据一致性。

> [图] 第145页：是的，这张图有技术知识点。它描述了多线程环境下缓存与数据库一致性问题中的“缓存穿透”或“缓存失效”场景，展示了线程A在更新数据库时未及时清除缓存，导致线程B读取到旧数据的并发问题。

> [图] 第148页：有技术知识点。该图展示了一个典型的分布式系统架构，包含负载均衡、多节点本地缓存、中间件（如Redis）和后端数据库的层次结构。

> [图] 第149页：有技术知识点。该图展示了分布式系统中基于本地缓存、发布订阅模式和消息队列的缓存一致性方案，通过负载均衡分发请求，并利用消息队列同步缓存删除操作以保证数据一致性。

> [图] 第153页：有技术知识点。该图展示了缓存读取和缓存击穿两种场景：左侧为正常缓存命中，请求通过服务层从缓存获取数据；右侧为缓存击穿，缓存失效后直接访问数据库，可能导致数据库压力增大。

> [图] 第154页：有技术知识点。该图描述了热点数据发现的流程，包括通过读请求统计Key的访问频率，定位访问频率最高的Top n个Key作为热点Key，并在反馈阶段对热点数据进行处理和反馈。

> [图] 第155页：有技术知识点。该图展示了分布式系统中多个客户端并发查询同一键（key3）时，数据在不同节点上的分布与访问情况，体现了分布式缓存或数据库中的数据分片与并发读取机制。

> [图] 第155页：有技术知识点。该图描述了热key处理的架构流程，包括监控客户端、代理端和服务器端对热key的识别，并通过keys打散和二级缓存进行处理，以缓解热点数据访问压力。

> [图] 第156页：有技术知识点。该图展示了使用 `redis-cli --bigkeys` 命令扫描 Redis 数据库中最大键及其类型和大小的输出结果，用于分析 Redis 内存使用情况和优化性能。

> [图] 第157页：有。这张图展示了大key处理的两种策略：可删除时采用渐进式删除（<4.0版本）或惰性删除（>4.0版本），不可删除时则通过value压缩或value拆分来优化存储。

> [图] 第159页：有技术知识点。该图展示了在系统设计中冷缓存（Cold Cache）和热缓存（Warm Cache）的工作原理，通过对比缓存命中（CH）和缓存未命中（CM）的情况，说明了数据在主内存、缓存和处理器之间的交互过程。

> [图] 第162页：有技术知识点。该图展示了Redis内存使用情况的详细信息，包括已用内存、峰值内存、内存开销及系统总内存等指标，可用于监控和优化Redis实例的内存性能。

> [图] 第163页：有技术知识点。主修课程包括高等数学、线性代数、概率统计、程序设计基础、数据结构、信号处理、矩阵理论、导波光学、光纤传感原理与应用、数字光纤通信等，涉及计算机科学与工程、通信工程和光学工程领域的核心课程。

> [图] 第163页：有技术知识点。该图展示了Redis过期数据回收的两种策略：惰性删除（访问时发现过期则删除）和定期删除（定时随机检测并删除过期key）。

> [图] 第164页：有技术知识点。该图展示了Redis中三种关键的键过期与内存管理机制：定期删除、惰性删除和内存淘汰策略，涉及定时任务、过期检查、客户端交互及多种淘汰算法（如LRU、TTL等）。

> [图] 第164页：有技术知识点：该图展示了在 Redis 命令行客户端中查询配置参数 `hz` 的值，显示当前 Redis 实例的内部事件循环频率为 10 次每秒。

> [图] 第165页：有。这张图展示了Redis内存淘汰策略的技术知识点，包括不淘汰策略和多种淘汰数据策略（如LRU、LFU、随机删除等），用于在内存不足时决定如何移除键以腾出空间。

> [图] 第165页：有技术知识点。图中展示了 Redis 配置文件中的 `hz` 参数设置及其作用，解释了该参数控制 Redis 执行后台任务的频率，影响系统响应性和 CPU 使用率。

> [图] 第170页：有技术知识点。该图展示了消息队列的基本工作原理：生产者通过push将消息（message）加入队列，消费者通过rpop从队列末尾取出消息进行消费，实现消息的循环处理。

> [图] 第171页：是的，这张图包含技术知识点。它展示了Redis中ZSet（有序集合）的数据结构操作流程，具体表现为通过`zadd`命令插入元素并按分数排序，再通过`zrangebyscore`命令根据分数范围查询元素。

> [图] 第171页：有技术知识点。该图展示了发布-订阅（Publish-Subscribe）模式的基本架构，其中消息通过通道（channel）发布给多个订阅者（subscriber）。

> [图] 第175页：有技术知识点。该图描述了Redis服务器在接收到客户端命令时，根据客户端是否处于事务状态以及命令类型来决定是将命令入队还是立即执行的流程逻辑。

> [图] 第176页：有技术知识点。该图展示了Redis中使用WATCH命令实现乐观锁的典型操作流程，包括设置键值、监视键、事务执行和递增操作。

> [图] 第176页：有技术知识点。该图描述了服务器在接收到客户端命令后，根据客户端是否处于事务状态来决定是将命令入队还是立即执行的处理流程。

> [图] 第177页：有技术知识点。该图展示了Redis中事务的使用，通过`multi`开启事务，执行`decr`命令后使用`discard`放弃事务，最终键值未发生改变，体现了Redis事务的原子性和可取消性。

> [图] 第177页：有技术知识点。该图展示了Redis中使用WATCH命令实现乐观锁的机制，当客户端A在事务执行前监控的键被其他客户端（如B）修改时，事务将被放弃，确保数据一致性。

> [图] 第178页：有技术知识点。该图展示了Redis事务的内部结构，包括multiState、multiCmd数组以及对应的命令参数和对象（robj），说明了Redis如何存储和处理事务中的多个命令。

> [图] 第178页：有技术知识点。该图展示了Redis事务的工作流程：通过MULTI命令开启事务，后续命令按FIFO顺序排队执行，期间其他命令无法插入，确保事务的原子性。

> [图] 第179页：有技术知识点。该图展示了Redis中watch机制的工作原理：被监视的key（如"fanone"、"number"）与监视这些key的客户端（c1、c2、c3）之间的映射关系，用于实现乐观锁和事务监听。

> [图] 第180页：有技术知识点。Redis 不支持事务回滚，因为实现回滚会显著影响其简洁性和性能。

> [图] 第181页：有技术知识点。该图展示了命令执行的三种状态：全部成功、部分失败和全部失败，用于说明批量命令执行的结果处理逻辑。

> [图] 第182页：有技术知识点。该图展示了基于 epoll 的多客户端并发处理模型，描述了客户端通过 socket 连接服务器，epoll 监听事件（accept、read、write），并进行连接接受、命令处理和数据写入发送队列的完整流程。

> [图] 第183页：有技术知识点。该图展示了Redis中Lua脚本执行的流程：当客户端发送Lua脚本请求时，Redis服务器会阻塞其他所有请求直到脚本执行完毕，确保原子性操作。

> [图] 第185页：有技术知识点。该图展示了关于C++和Go语言在职业发展中的对比讨论，涉及两者在客户端开发（如WPS、Qt）与云相关开发中的应用趋势，指出Go语言在云和AI智能化方向更具上升空间。

> [图] 第186页：有技术知识点。该图展示了Redis中Pipeline的工作原理：客户端发送一组命令（cmd1到cmd4）给Redis服务器，Redis以单线程方式依次执行这些命令，并将结果（res1到res4）按顺序返回给客户端，从而提升网络通信效率。

> [图] 第188页：有技术知识点。该图展示了一段Java代码，实现了Redis客户端的管道（Pipeline）操作封装，通过`PipelineAction`类将多个Redis命令批量执行，提升性能。

> [图] 第189页：有技术知识点。该图展示了Spring Data Redis中`RedisTemplate`类的`executePipelined`方法的实现，涉及Redis管道操作的执行流程，包括打开管道、执行回调、关闭管道以及异常处理等核心逻辑。

> [图] 第190页：有技术知识点。该图展示了多个客户端通过Redis实现分布式锁的场景，利用SET命令的NX（仅在键不存在时设置）和EX/PX（设置过期时间）选项来实现互斥访问，防止竞争条件。

> [图] 第191页：有技术知识点。该图描述了分布式锁在过期释放时可能引发的“锁误释放”问题：线程A获取锁并执行业务，锁过期后被自动释放，此时线程B获取了锁；但线程A继续执行并尝试释放锁，却误释放了线程B持有的锁，导致并发安全问题。

> [图] 第192页：有技术知识点。该图展示了Redisson分布式锁的处理逻辑，包括加锁、续期、释放锁以及自旋锁机制。

> [图] 第194页：有技术知识点。该图描述了基于Redis的分布式锁机制，包括线程竞争获取锁、加锁成功后通过Watch Dog机制延长锁的生存时间，并利用Lua脚本在Master节点上执行操作，确保锁的可靠性和一致性。

> [图] 第196页：有技术知识点。图中展示了Redisson分布式锁的`tryLockInnerAsync`方法的源码，涉及Redis Lua脚本实现的加锁逻辑，包括检查键是否存在、递增计数器、设置过期时间等操作。

> [图] 第197页：有技术知识点。该图展示了Redisson中RedLock分布式锁算法的实现，通过多个RLock对象协同工作，确保在分布式环境下的锁安全性与一致性。

> [图] 第199页：有技术知识点。该图展示了PmHub系统中集成Redis分布式锁的业务流程，用于保障多节点环境下流程状态更新的一致性和线程安全。

> [图] 第200页：有技术知识点。该图展示了Redis对象的内部结构，包括redisObject的组成（如类型、编码方式、底层数据结构指针等）以及其对应的底层数据结构和编码方式，同时列出了Redis支持的对象类型（string、hash、list、set、zset）。

> [图] 第201页：是的，这张图有技术知识点。它展示了Redis中SDS（Simple Dynamic String）数据结构的内存布局，包括`len`（已使用字节数）、`free`（空闲字节数）和`buf[]`（字符数组）字段，以及一个实际字符串"Redis"的存储示例。

> [图] 第202页：有技术知识点。该图展示了Redis中哈希表（dict）的数据结构设计，包括字典（dict）、哈希表（dictht）和哈希表节点（dictEntry）的结构及其相互关系，体现了哈希表的存储机制与扩展策略。

> [图] 第203页：有技术知识点。该图展示了Redis中quicklist的数据结构，由多个ziplist组成的双向链表，每个quickListNode包含prev、ziplist和next指针，用于高效存储和访问数据。

> [图] 第203页：有技术知识点。该图展示了Redis中ziplist（压缩列表）的数据结构布局，包括zbytes、zltail、zllen等字段以及Entry项的存储方式。

> [图] 第204页：有技术知识点。该图展示了Redis中listpack数据结构的实现细节，包括总字节长度、元素数量、各元素的编码类型、数据和长度信息，以及结束标志。

> [图] 第204页：有技术知识点。该图展示了跳表（Skip List）的数据结构，包括节点结构、层级关系和指针连接方式，用于高效地实现有序数据的插入、删除和查找操作。

> [图] 第205页：有。该图展示了Redis中对象机制与底层数据结构的映射关系，包括不同数据类型（如String、List、Hash等）对应的编码类型及其底层实现（如SDS、QuickList、ZipList等），是理解Redis内部数据存储和优化策略的重要技术知识点。

> [图] 第205页：有。这张图展示了Redis中intset（整数集合）的数据结构及其在添加元素时的编码变化过程，体现了其动态调整编码类型以优化存储的机制。

> [图] 第206页：这张图中没有明显的技术知识点，主要内容为求职者与他人关于京东offer的沟通对话，涉及部门了解、面试反馈和职业选择建议。技术内容仅限于“推荐算法后台开发”这一岗位方向，但未展开具体技术细节。

一句话描述：无明确技术知识点，仅提及推荐算法后台开发岗位。

> [图] 第207页：有技术知识点。该图展示了Redis中整数集合（intset）的数据结构，包括编码方式、元素数量和存储内容的数组，用于高效存储整数集合。

> [图] 第207页：有技术知识点。该图展示了链表的数据结构，包括链表节点（listNode）和链表类型（list）的定义，以及它们之间的关系，涉及双向链表的头尾指针、节点值、复制、释放和匹配函数等核心概念。

> [图] 第208页：有技术知识点。该图展示了Redis中有序集合（REDIS_ZSET）的两种底层数据结构实现：一种是压缩列表（ziplist），另一种是跳跃表（skiplist），其中跳跃表进一步依赖于字典（dict）和跳跃表结构（zskiplist）。

> [图] 第209页：有技术知识点。该图展示了有序集合在压缩列表中的存储结构，元素按分值从小到大排列，体现了Redis中有序集合的底层实现原理。

> [图] 第210页：有技术知识点。该图展示了Redis中ziplist（压缩列表）的数据结构布局，包括头部信息（如总长度、元素数量、尾部偏移量）和每个条目的编码与数据存储方式。

> [图] 第210页：有技术知识点。该图展示了Redis中有序集合（zset）的数据结构，结合了哈希表（dict）和跳跃表（zskipList）来实现高效查找和排序功能。

> [图] 第211页：有技术知识点。该图展示了Redis中listpack数据结构的entry组成，包括编码类型、实际数据和总长度字段，用于高效存储小数据。

> [图] 第211页：有技术知识点。该图展示了Redis中listpack数据结构的内存布局，包括头部信息（总字节数和元素数量）、多个listpack entry以及末尾标识。

> [图] 第212页：是的，这张图有技术知识点。它展示了Redis中ziplist（压缩列表）在插入新元素时的内存布局变化，具体描述了当新元素大小超过原有entry的预分配空间时，如何扩展并重新调整后续元素的编码长度字段（prelen），以适应新的数据结构。

> [图] 第213页：这张图没有直接的技术知识点，主要为群聊内容截图，涉及公司对比和工作选择讨论。

> [图] 第214页：是的，这张图有技术知识点。它展示了一个字符串数据结构的内存布局，其中包含一个结构体 `sdshdr`，记录了字符串的长度（len）、空闲空间（free）以及指向实际字符缓冲区（buf[]）的指针，缓冲区中存储了字符串 "RedIs"（以 '\0' 结尾）。这通常是 Redis 中 SDS（Simple Dynamic String）数据结构的示意图。

> [图] 第214页：有技术知识点。该图展示了C语言中字符串的存储方式，说明字符串常量"Hello"在内存中以字符数组形式存储，并以空字符'\0'结尾。

> [图] 第215页：有技术知识点。该图展示了哈希表（dict）的内部结构，包括哈希算法、链地址法解决冲突以及字典的扩容机制。

> [图] 第216页：有。这张图展示了哈希表（字典）在进行 rehash（重新哈希）过程中的数据结构变化，包括旧表和新表的渐进式迁移过程，体现了哈希表扩容与数据迁移的技术细节。

> [图] 第217页：有技术知识点。该图展示了哈希表（Hash Table）的实现结构，其中使用数组存储桶（bucket），每个桶指向一个链表，链表节点（dictEntry）包含键值对和指向下一个节点的指针，体现了“数组+链表”的哈希冲突解决方式。

> [图] 第218页：有技术知识点。Go语言服务端开发与云原生技术密切相关，通常在实际项目中会结合使用。

> [图] 第219页：有技术知识点。该图展示了Redis中跳表（skiplist）的数据结构，包括头节点、尾节点、层级信息以及节点间的多级指针连接关系。

> [图] 第219页：有技术知识点。该图展示了一个双向链表结构，其中包含不同颜色的节点，表示数据元素（如3、7、11、19、22、26、37）通过指针相互连接，最终指向NULL，体现了链表的基本组织方式和遍历逻辑。

> [图] 第219页：有，这张图展示了在跳表（Skip List）数据结构中查找元素70的过程，体现了跳表的多层索引和快速查找特性。

> [图] 第222页：有技术知识点。该图展示了跳表（Skip List）数据结构的构建过程，通过逐步插入元素并随机决定层数来演示其多层链表的特性。

> [图] 第223页：有，这张图展示了在跳表（Skip List）数据结构中插入元素67的过程，通过多层链表实现快速查找和插入。

> [图] 第224页：有，这张图展示了跳表（Skip List）的数据结构，用于高效地实现有序集合的插入、删除和查找操作。

> [图] 第225页：是的，这张图展示了跳表（Skip List）的数据结构。它通过多级链表实现快速查找，每个节点在不同层级中具有不同的“秩”（Rank）和“跨度”（Span），从而支持高效的插入、删除和搜索操作。

> [图] 第226页：有技术知识点。该图展示了一个跳表（Skip List）的数据结构，按照score值升序排列存储，具有多层链表结构以实现快速查找。

> [图] 第227页：有技术知识点。该图展示了Redis中有序集合（Sorted Set）的内部数据结构，包括跳表（skiplist）和哈希表（dict）的组织方式，用于存储元素及其分数。

> [图] 第228页：是的，这张图有技术知识点。它展示了Redis中ziplist（压缩列表）的数据结构布局，包括zbytes、zltail、zllen等字段以及多个entry和zland标记，用于高效存储小数据集。

> [图] 第228页：有技术知识点。该图展示了Redis中ziplist数据结构在不同数据类型（列表、哈希、有序集合）下的配置选项，包括最大条目数和最大值大小的限制，用于优化内存使用和性能。

> [图] 第228页：这张图没有直接展示技术知识点，但对话内容提到了Java泛型相关的学习体验。用一句话描述：用户称赞对方总结的Java泛型知识清晰易懂，避免了传统教材带来的困惑。

> [图] 第229页：是的，这张图有技术知识点。它展示了一个Redis压缩列表（ziplist）的内存布局结构，包括zbytes、ztail、zllen等字段以及多个entry和zland的存储位置，用于描述数据在内存中的组织方式。

> [图] 第229页：是的，这张图有技术知识点。它描述了Redis中压缩列表（ziplist）的编码方式，展示了当entry数据长度在0~253和254及以上时，prevlen字段的不同编码规则。

> [图] 第229页：有技术知识点。该图展示了数据结构中“entry”条目的组成，每个entry包含prevlen、encoding和entry-data三个部分，用于描述一种链式存储结构（如Redis的ziplist）中元素的布局方式。

> [图] 第230页：是的，这张图有技术知识点。它描述了Redis中ziplist（压缩列表）在插入大长度元素时触发的连锁更新机制，即当新插入的节点长度超过253字节时，由于前驱节点的prevlen字段不足以存储新的长度信息，需要逐级向前扩展节点，直到所有受影响的节点都完成更新。

> [图] 第232页：是的，这张图有技术知识点。它展示了不同整数类型（int16、int32、int64）在数据编码中的二进制格式结构，以及一种变长编码方式（如类似VarInt的编码），用于高效表示整数值，常见于序列化协议或压缩算法中。

> [图] 第233页：是的，这张图有技术知识点。它描述了一种变长编码格式（类似Varint）的数据结构，用于高效编码不同长度的条目数据，通过前缀位标识编码方式和长度字段大小，支持1字节、2字节和5字节三种编码形式，适用于序列化或网络传输场景。

> [图] 第234页：这张图没有直接展示技术知识点，主要呈现的是一个社群（星球）的界面和用户之间的对话内容。对话中提到了“面试题”和“Java进阶之路”，间接涉及Java学习和面试准备相关主题，但图中未具体展示任何技术细节或代码。因此，**无明确技术知识点**。

> [图] 第235页：有技术知识点。该图展示了Redis中quicklist数据结构的组成，包括quicklist头节点、quicklistNode节点及其内部结构，以及压缩列表（ziplist）的存储方式。

> [图] 第235页：有技术知识点。该图展示了Redis中quicklist的数据结构，由多个quicklistNode组成双向链表，每个节点包含一个ziplist，用于高效存储和操作列表数据。

> [图] 第236页：有技术知识点。该图展示了Redis中quicklist的数据结构，由多个ziplist组成的双向链表，每个节点包含prev、next指针和ziplist数据，用于高效存储和访问列表元素。

> [图] 第237页：有，这张图展示了Redis中QuickList的数据结构，它由多个QuicklistNode组成，每个节点指向一个Ziplist或LZF压缩数据，用于高效存储和管理列表元素。

> [图] 第240页：有技术知识点。该图展示了秒杀系统的架构设计，包括静态数据通过CDN缓存、动态数据通过本地缓存和多系统协同处理（如商品库存、订单、支付），并涉及库存热点迁移和分布式事务处理等关键技术。

> [图] 第241页：有技术知识点。该图展示了一种高并发场景下的库存扣减架构，通过Redis缓存库存数量、防止超卖、异步写入任务库并由任务处理引擎最终落库，实现抗高并发的库存控制方案。

> [图] 第242页：有技术知识点。该图描述了分布式锁的实现流程，包括加锁、创建守护线程、业务执行、延长锁过期时间、释放锁和关闭守护线程等步骤，体现了防止锁过期导致的并发问题的机制。

> [图] 第243页：有技术知识点。该图展示了令牌桶限流算法的工作原理：请求到达后，拦截器检查令牌桶中是否有可用令牌，若有的话则允许请求继续发送，否则丢弃请求；令牌桶以固定速率（1/r秒）生成令牌，存储量为b，用于控制请求的处理速度。

> [图] 第244页：有技术知识点。该图展示了典型的缓存架构：应用程序先访问Redis缓存，若命中则直接返回数据，未命中时再访问数据库，实现读取性能优化。

> [图] 第245页：有技术知识点。该图展示了一个秒杀系统的典型流程，涉及商品详情、交易系统、库存缓存和支付系统的协同工作，重点体现了库存校验在高并发场景下的关键作用。

> [图] 第245页：有技术知识点。该图展示了从紧耦合系统到松耦合系统的架构演进，通过引入消息服务队列实现服务间的解耦。

> [图] 第249页：有技术知识点。该图描述了令牌桶算法（Token Bucket Algorithm）的流量控制机制，用于限制请求速率：令牌以固定速率加入桶中，客户端请求时需获取令牌，成功则处理请求，失败则拒绝。

> [图] 第250页：有技术知识点。该图列出了计算机相关课程和技术领域，包括数据库系统、操作系统、计算机组成原理、数据结构与算法、计算机安全（CS 161）和计算机网络（CS 168）等。

> [图] 第251页：有技术知识点。该图展示了通过 Redis CLI 查询配置参数 `tcp-keepalive` 和 `timeout` 的命令及其返回结果，显示了 Redis 服务器的 TCP 保活时间为 300 秒，连接超时时间为 0（表示无超时限制）。

> [图] 第252页：这张图没有直接展示技术知识点，主要是一段群聊对话内容，讨论的是求职面试情况（如快手、京东、夸克等公司），其中包含对面试结果的感慨和情绪表达。因此，**无明确技术知识点**。

> [图] 第253页：有技术知识点。该图展示了多本关于Java技术栈的电子书封面，涵盖Redis、MySQL、JVM、并发编程、集合框架和Java基础等核心技术主题，适合准备技术面试或深入学习Java开发的读者。

> [图] 第256页：这张图没有直接展示技术知识点，但提到了“MQ八股”和“面渣”，暗示了与消息队列（MQ）相关的面试经验分享，可能涉及常见面试题或技术要点。用一句话描述：**聊天内容提及MQ相关面试经验，暗示有技术知识点分享。**

> [图] 第257页：这张图没有直接展示技术知识点，主要是用户之间的聊天记录，表达了对某位作者（“二哥”）编写的面试资料《面渣》2.0版本的期待和好评，提及了MySQL和Redis相关内容的更新计划。虽然提到了MySQL和Redis，但并未涉及具体的技术细节。  
**一句话描述：** 无直接技术知识点，仅表达对技术面试资料更新的期待。

> [图] 第259页：有技术知识点。图中展示了多个与Java技术相关的学习资料，包括JVM、并发编程、Spring、Redis等主题的PDF和Markdown文档，适合Java开发者深入学习和面试准备。

> [图] 第260页：有技术知识点。该图展示了Redis在6.0版本前后的架构演变，重点说明了从单线程事件循环到引入多线程处理网络IO的性能优化机制，涉及epoll、事件分发、命令执行流程等核心技术细节。