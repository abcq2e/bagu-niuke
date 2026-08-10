# Java并发编程 面试真题（牛客面经）

1. Java 里怎么创建线程？创建线程有几种方式？
2. 继承 Thread 和实现 Runnable / Callable 有什么区别？
3. start() 和 run() 的区别
4. Java 线程有几种状态？wait 之后处于什么状态？sleep 呢？遇到 synchronized 获取锁阻塞时是什么状态？
5. 阻塞和就绪有什么区别？
6. 线程共享的资源和不共享的资源分别有什么？
7. Java 进程之间怎么通信？
8. 如何实现一个简单的生产者消费者模式？
9. 多线程执行 a++ 是否线程安全？为什么？怎么解决？

---

10. 线程池的核心参数有哪些？分别有什么作用？
11. 线程池的工作流程：核心线程怎么创建、什么情况创建非核心线程、什么时候进队列？
12. 线程池 core size 为 4，一次提交 8 个任务，线程池会怎么处理？队列满了以后会发生什么？非核心线程用完了怎么办？
13. 线程池的四种拒绝策略分别是什么？哪一种是默认的？
14. 线程池核心线程数怎么设置？IO 密集型和 CPU 密集型任务分别怎么配？
15. RPC 调用情况下你会怎么配置线程池？
16. 核心线程数是在加载的时候创建还是在执行的时候创建？
17. 线程池自动回收原理
18. 为什么要使用自定义线程池？
19. 创建线程池的几种方法
20. 频繁创建回收线程浪费什么资源？
21. 用过 ForkJoinPool 吗？多个线程池如何确定和 CPU 核数的关系？
22. CompletableFuture 了解吗？
23. 一个线程等待多个线程使用什么来实现？CountDownLatch 和 CyclicBarrier 区别？
24. 三个线程同时处理一个数从 1 加到 100，如何保证数据的可见性和有序性？
25. 多线程同步执行应该怎么做？Java 怎么控制并发？如何保证线程安全，有哪些方式？

---

26. synchronized 和 ReentrantLock 的区别？什么场景下用哪个？都是可重入的吗？
27. synchronized 底层实现原理 / 加锁流程？它如何保证多线程并发时能拦住其他线程？
28. synchronized 锁升级过程详细讲一下
29. 对象头里的 Mark Word 是怎么区分不同锁级别的？对象头内存结构？
30. Monitor 依赖操作系统底层怎么实现？
31. synchronized 与自旋锁的区别
32. AQS 原理了解吗？AQS 队列结构原理？
33. CAS 是什么？底层原理？两个线程真实交互的过程是什么样子的？
34. CAS 的 ABA 问题如何解决？长时间自旋怎么解决？
35. Java 原子类实现原理
36. volatile 关键字是做什么用的？使用场景？怎么实现可见性的？底层原理？
37. volatile 能不能保证原子性？内存屏障有几种？
38. 乐观锁和悲观锁的区别？分别在什么场景下使用？
39. 说一下你知道的锁
40. Java 实现一个死锁？死锁产生的条件？

---

41. ThreadLocal 怎么用的？原理是什么？
42. ThreadLocal 会导致内存泄漏吗？为什么？怎么解决？
43. ThreadLocal 的 key 为什么被设计为弱引用，value 为强引用？
44. 为什么不用线程 id 去作为 key？
45. 为什么要用 ThreadLocal？不直接用 Map 去存？
46. ThreadLocal 在线程池中除了手动 remove，还能怎么防止内存泄漏？
47. ThreadLocal 怎么解决异步任务（父子线程传递）？
48. ThreadLocal 用于什么场景比较合适？

---

49. ConcurrentHashMap 用过吗？怎么保证线程安全的？
50. ConcurrentHashMap 底层的读写是怎么处理并发冲突的？
51. JDK8 的 ConcurrentHashMap 中 synchronized 锁应用在什么地方？JDK8 和 JDK7 的实现区别？
52. 为什么用 synchronized 而不是 CAS 更新 ConcurrentHashMap？
53. ConcurrentHashMap 的锁粒度
54. ConcurrentHashMap 为什么比 Hashtable 快？
55. 有哪些线程安全的集合？List 集合里有没有线程安全的集合？线程安全的 Map 有哪些？
56. 说一下 CopyOnWriteArrayList
57. Java 的 JMM 内存模型（并发语义层面）讲一下
58. 同步和异步的本质区别是什么？
59. 多核处理器在访问同一资源时可能出现什么问题，怎么预防？
60. CPU 里的各级存储的速度
