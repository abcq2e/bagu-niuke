# 面渣逆袭 —— RocketMQ & RabbitMQ

1. RocketMQ 由哪几个核心组件构成，各自职责是什么？
2. RocketMQ 中 NameServer 的作用是什么？为什么不用 ZooKeeper？
3. RocketMQ 的 Broker 是如何实现高可用的？Master 和 Slave 各自的职责？
4. RocketMQ 消息发送的完整流程是怎样的？Producer 如何选择 MessageQueue？
5. RocketMQ 消息消费的完整流程是怎样的？Push 和 Pull 模式有什么区别？
6. RocketMQ 的存储设计有哪些特点？CommitLog、ConsumeQueue、IndexFile 各自的作用？
7. RocketMQ 为什么使用顺序写磁盘？PageCache 和 mmap 在其中如何发挥作用？
8. RocketMQ 的同步刷盘和异步刷盘有什么区别？如何选择？
9. RocketMQ 如何保证消息不丢失？从生产者、Broker、消费者三个角度分析？
10. RocketMQ 消费者的负载均衡策略是怎样的？RebalanceService 如何触发重平衡？
11. RocketMQ 的延迟消息是如何实现的？支持哪些延迟等级？
12. RocketMQ 的事务消息是如何实现的？半消息的原理是什么？
13. RocketMQ 如何保证消息顺序？全局顺序和局部顺序分别如何实现？
14. RocketMQ 如何保证消息幂等性？消费者端如何去重？
15. RocketMQ 的死信队列是什么？消息什么情况下会进入死信队列？
16. RocketMQ 消息堆积怎么处理？如何快速消费积压的消息？
17. RocketMQ 的消息过滤有哪几种方式？Tag 过滤和 SQL 过滤的区别？
18. RocketMQ 的 Consumer Group 是什么？集群消费和广播消费的区别？
19. RocketMQ 长轮询机制是如何实现的？相比短轮询有什么优势？
20. RocketMQ 的高性能设计有哪些？为什么吞吐量远超 RabbitMQ？

---

21. RabbitMQ 的核心架构是什么？Exchange、Queue、Binding 各自的作用？
22. RabbitMQ 有哪几种 Exchange 类型？Direct、Topic、Fanout、Headers 分别适用什么场景？
23. RabbitMQ 如何保证消息可靠性？生产者确认（Publisher Confirm）和消费者 ACK 如何配合？
24. RabbitMQ 的消息持久化是如何实现的？需要同时开启哪些配置才能真正持久化？
25. RabbitMQ 的死信队列（DLX）是什么？什么情况下消息会变成死信？
26. RabbitMQ 如何实现延迟消息？官方插件和死信队列两种方式各有什么优缺点？
27. RabbitMQ 如何保证消息顺序消费？单 Consumer 和多 Consumer 场景下有何不同？
28. RabbitMQ 消费者幂等性如何保证？有哪些常见方案？
29. RabbitMQ 的 Prefetch Count（预取数量）是什么？如何合理设置？
30. RabbitMQ 和 RocketMQ 在消息可靠性、吞吐量、延迟消息支持上有什么核心区别？
