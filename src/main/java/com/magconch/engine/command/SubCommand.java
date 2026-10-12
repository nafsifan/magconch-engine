package com.magconch.engine.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 引擎子命令定义契约
 * <p>
 * 规约各子命令的基础参数、权限校验节点以及自动补全逻辑。
 */
public interface SubCommand {

    // ==========================================
    // 命令元数据
    // ==========================================

    /**
     * 子命令名称（小写触发词，如 "help", "reload"）
     *
     * @return 子命令名称
     */
    String getName();

    /**
     * 语言包中的描述键或默认描述说明
     *
     * @return 描述标识
     */
    String getDescription();

    /**
     * 命令语法用法（例如 "reload", "give <player> <item>"）
     *
     * @return 用法语义
     */
    String getUsage();

    /**
     * 执行该子命令所需的权限节点（返回 null 或空字符串表示无权限限制）
     *
     * @return 权限节点
     */
    String getPermission();

    // ==========================================
    // 执行与补全
    // ==========================================

    /**
     * 执行子命令核心逻辑
     *
     * @param sender 发送者
     * @param args   子命令后的附加参数数组
     * @return 执行是否成功
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * 获取 Tab 键自动补全候选列表
     *
     * @param sender 发送者
     * @param args   当前输入的参数
     * @return 补全选项列表
     */
    List<String> tabComplete(CommandSender sender, String[] args);
}
