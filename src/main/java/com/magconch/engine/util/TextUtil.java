package com.magconch.engine.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本渲染与颜色格式化工具
 * <p>
 * 提供对标准 &amp; 颜色代码以及 &#RRGGBB 十六进制 Hex 渐变色的统一转换，
 * 采用原生字符序列构建，完全解耦废弃的 BungeeCord Chat API。
 */
public final class TextUtil {

    // ==========================================
    // 常量与匹配模式
    // ==========================================

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private TextUtil() {
        // 工具类私有构造
    }

    // ==========================================
    // 核心转换方法
    // ==========================================

    /**
     * 将包含 &amp; 和 &#RRGGBB 的文本转换为带 Minecraft 样式码的文本
     *
     * @param text 原始文本
     * @return 格式化后的彩色文本
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. 处理 Hex 十六进制颜色代码 (&#RRGGBB -> §x§r§r§g§g§b§b)
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        // 2. 处理原版标准样式代码 (& -> §)
        return COLOR_PATTERN.matcher(buffer.toString()).replaceAll("§$1");
    }

    /**
     * 批量转换多行文本的颜色代码
     *
     * @param lines 原始文本列表
     * @return 格式化后的彩色文本列表
     */
    public static List<String> colorize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }
}
