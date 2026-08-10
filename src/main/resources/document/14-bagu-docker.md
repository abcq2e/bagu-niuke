# Docker与运维 面试真题（牛客面经）

1. 为什么用 Docker 部署不用本地部署？
2. Docker 在项目中是怎么用的？能举几条简单的 Docker 命令吗？
3. 部署过 Docker 容器吗？怎么个部署方式？
4. 如何查看 Docker 容器的状态，对 CPU 内存的一个占用情况？
5. Docker 怎么查看实时日志？
6. Docker 知不知道，常用 Docker 来干嘛？

---

7. Dockerfile 编写过吗？部署过什么？
8. 容器化部署经验讲一下
9. Docker 组件使用情况

---

10. k8s 用过吗？K8S 中 deployment 和 StatefulSet 的区别？为什么不全用 StatefulSet？
11. Docker、K8s 容器化使用情况
12. 实习开发了 CI/CD 插件，主要用的是什么语言？
13. CI/CD 有搭建过这套流程吗？
14. 如果流水线执行失败后自动分析日志并给出修复建议，你会怎么设计这个功能？

---

15. 项目上线之后 CPU 彪高怎么排查？（注：场景分析在 系统设计与场景）
16. 生产环境项目出 bug 该怎么排查？有哪些实时排查命令？
17. 在虚拟机中查看日志报错的命令
18. 用过集群环境下的日志吗（ELK）？如何找日志（ES、唯一 ID）？
19. 如何排查服务器的内存占用情况？讲一下服务器运维的经验
20. 常用的 Git 命令有哪些？如果想撤销某次 commit 的代码，可以怎么做？

---

21. Linux 负载高怎么排查？（注：基础 Linux 命令在操作系统与 Linux）
22. Linux 机器很卡时应该如何排查？使用 top 之后如何进一步定位问题进程？
（注：Linux 进程/端口/文件排查命令在 操作系统与Linux）
23. 怎么查看进程状态是运行、睡眠还是僵尸状态？
24. 怎么查看目录大小？文件权限 rwx 含义？

---

25. Nginx 了解吗？配置过反向代理吗？
26. 监控相关：Redis 内存监控相关做过吗？
27. 线上发现内存不停地缓慢增长，不确定是哪行代码出的问题，你会怎么排查？有办法定位到具体代码吗？
28. MySQL 迁移达梦数据库国产化适配评估思路
29. 如何减少安装包体积？构建产物体积优化怎么做？

---

30. Docker 和虚拟机的区别是什么？容器化的核心技术是什么？
31. Linux namespace 有哪几种？分别隔离什么资源（PID、网络、文件系统等）？
32. cgroup（Control Groups）的作用是什么？能限制哪些资源？
33. Docker 镜像的分层机制（Union FS / OverlayFS）是怎么工作的？
34. 什么是 Copy-on-Write（写时复制）？Docker 容器层如何利用它？
35. Docker 容器重启后数据会丢失吗？Volume 和 bind mount 的区别？
36. Docker 网络模式有哪些（bridge/host/none/overlay）？默认是哪种？
37. bridge 网络模式下，容器如何和宿主机通信？如何互相通信？
38. Dockerfile 中 CMD 和 ENTRYPOINT 的区别？两者可以同时存在吗？
39. 多阶段构建（multi-stage build）解决了什么问题？举一个例子？
40. Docker Compose 是什么？和直接用 docker run 的区别？
41. Docker 镜像过大怎么优化？.dockerignore 的作用？
42. 容器内的进程崩溃了，容器会怎样？如何设置自动重启策略？
43. 如何查看容器的资源占用（CPU/内存）？docker stats 命令的输出含义？
44. Docker 和 K8s 的关系？K8s 解决了 Docker 的什么问题？
