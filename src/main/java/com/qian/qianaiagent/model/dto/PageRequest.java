package com.qian.qianaiagent.model.dto;

import lombok.Data;

/**
 * 分页查询请求参数
 *
 * ===== 🎯 Task 14: 这个文件已生成，你只需要理解它 =====
 * 前后端分页交互的通用 DTO：
 * - page: 第几页（从 1 开始）
 * - size: 每页多少条
 * - keyword: 搜索关键词（可选，用于用户名模糊搜索）
 *
 * 💡 思考：为什么 page 从 1 开始而不是 0？
 *    前端展示"第 1 页"更直观。MyBatis-Plus 的 Page 对象自动处理这个差异。
 */
@Data
public class PageRequest {

    /** 当前页码（从 1 开始） */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;

    /** 搜索关键词（可选） */
    private String keyword;
}
