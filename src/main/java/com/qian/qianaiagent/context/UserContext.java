package com.qian.qianaiagent.context;

/**
 * 当前登录用户上下文
 * <p>
 * 用 ThreadLocal 存储当前请求的用户信息。
 * JwtAuthFilter 在验证 Token 后把用户 ID 放进来，
 * 后续的 Controller/Service 通过 getCurrentUserId() 获取。
 * <p>
 * <b>为什么用 ThreadLocal？</b>
 * 每个 HTTP 请求在服务端都对应一个线程，
 * ThreadLocal 可以绑定数据到当前线程，线程之间互不干扰，
 * 天然就是"请求级"的隔离。
 * <p>
 * <b>注意事项：</b>
 * 请求处理完后必须调用 remove() 清除，否则会导致内存泄漏。
 * 这个清除操作在 JwtAuthFilter 的 finally 块中完成。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /** 设置当前请求的用户 ID */
    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /** 获取当前请求的用户 ID */
    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    /** 清除（防止内存泄漏，必须在请求结束时调用） */
    public static void remove() {
        USER_ID_HOLDER.remove();
    }
}
