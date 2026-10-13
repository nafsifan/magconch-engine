package com.magconch.engine.util;

import com.magconch.engine.MagconchEngine;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * 配置增量同步工具类
 * <p>
 * 对比 Jar 内置模板与磁盘文件，自动将缺失键及其注释插入至对应层级节点，并清理已废弃的冗余配置块。
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    /**
     * 对目标配置文件执行结构化双向同步（补齐缺失键 + 剔除废弃键）
     *
     * @param plugin       插件主类
     * @param resourcePath Jar 内置模板相对路径
     * @param targetFile   磁盘实际配置文件
     * @return 实际变更的配置节点数量（0 表示完全一致，未写盘）
     */
    public static int update(MagconchEngine plugin, String resourcePath, File targetFile) {
        if (!targetFile.exists()) {
            return 0;
        }

        // 1. 严格语法检查，若服主手动编辑导致缩进/语法损坏，立刻熔断退出，杜绝空配覆写破坏原件
        YamlConfiguration diskConfig = new YamlConfiguration();
        try {
            diskConfig.load(targetFile);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "配置文件 " + targetFile.getName()
                    + " 存在 YAML 语法错乱，已中止增量同步以保护服主原文件: " + e.getMessage());
            return 0;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "读取配置文件 " + targetFile.getName() + " 失败", e);
            return 0;
        }

        // 2. 加载 Jar 内置最新模板
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return 0;
        }

        YamlConfiguration jarConfig = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            jarConfig.load(reader);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "加载内置模板资源 " + resourcePath + " 失败", e);
            return 0;
        }

        // 3. 提取缺失的叶子节点与已废弃的最外层节点路径
        List<String> missingKeys = new ArrayList<>();
        for (String key : jarConfig.getKeys(true)) {
            if (!jarConfig.isConfigurationSection(key) && !diskConfig.contains(key)) {
                missingKeys.add(key);
            }
        }

        List<String> obsoleteKeys = new ArrayList<>();
        for (String key : diskConfig.getKeys(true)) {
            if (!jarConfig.contains(key)) {
                int dotIndex = key.lastIndexOf('.');
                String parent = dotIndex == -1 ? "" : key.substring(0, dotIndex);
                if (parent.isEmpty() || jarConfig.contains(parent)) {
                    obsoleteKeys.add(key);
                }
            }
        }

        if (missingKeys.isEmpty() && obsoleteKeys.isEmpty()) {
            return 0;
        }

        // 4. 解析磁盘文本行与 Jar 模板文本行，执行废弃块清理与缺失键就近插入
        try {
            List<String> diskLines = Files.readAllLines(targetFile.toPath(), StandardCharsets.UTF_8);
            List<String> updatedLines = removeObsoleteKeys(diskLines, obsoleteKeys);

            if (!missingKeys.isEmpty()) {
                List<String> jarLines;
                try (InputStream templateIn = plugin.getResource(resourcePath)) {
                    if (templateIn == null) {
                        return 0;
                    }
                    jarLines = readLines(templateIn);
                }
                updatedLines = mergeMissingKeys(updatedLines, jarLines, missingKeys);
            }

            Files.write(targetFile.toPath(), updatedLines, StandardCharsets.UTF_8);
            return missingKeys.size() + obsoleteKeys.size();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "写入增量同步配置到 " + targetFile.getName() + " 失败", e);
            return 0;
        }
    }

    /**
     * 从磁盘文本行中移除已废弃的配置节点（含同缩进紧邻注释及其下属子行）
     */
    private static List<String> removeObsoleteKeys(List<String> diskLines, List<String> obsoleteKeys) {
        List<String> result = new ArrayList<>(diskLines);

        for (String obsoleteKey : obsoleteKeys) {
            int keyLineIndex = findKeyLineIndex(result, obsoleteKey);
            if (keyLineIndex == -1) {
                continue;
            }

            int keyIndent = getIndent(result.get(keyLineIndex));

            // 向上囊括同缩进级别的紧邻注释行
            int startRemove = keyLineIndex;
            while (startRemove > 0) {
                String prevLine = result.get(startRemove - 1);
                String prevTrimmed = prevLine.trim();
                if (prevTrimmed.startsWith("#") && getIndent(prevLine) == keyIndent) {
                    startRemove--;
                } else {
                    break;
                }
            }

            // 向下囊括所有缩进更深的子行（如列表项或子节点）
            int endRemove = keyLineIndex + 1;
            while (endRemove < result.size()) {
                String line = result.get(endRemove);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    int nextNonEmpty = endRemove + 1;
                    while (nextNonEmpty < result.size() && result.get(nextNonEmpty).trim().isEmpty()) {
                        nextNonEmpty++;
                    }
                    if (nextNonEmpty < result.size() && getIndent(result.get(nextNonEmpty)) > keyIndent) {
                        endRemove = nextNonEmpty;
                        continue;
                    }
                    break;
                }
                if (getIndent(line) > keyIndent) {
                    endRemove++;
                } else {
                    break;
                }
            }

            result.subList(startRemove, endRemove).clear();
        }

        return result;
    }

    /**
     * 按完整路径与缩进层级精准定位配置键所在行号
     */
    private static int findKeyLineIndex(List<String> lines, String keyPath) {
        String[] parts = keyPath.split("\\.");
        int currentSearchIndex = 0;

        for (int i = 0; i < parts.length; i++) {
            int targetIndent = i * 2;
            String targetHeader = parts[i] + ":";
            int foundIndex = -1;

            for (int lineIdx = currentSearchIndex; lineIdx < lines.size(); lineIdx++) {
                String line = lines.get(lineIdx);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int indent = getIndent(line);
                if (indent < targetIndent) {
                    return -1; // 已跳出父级 Section 范围
                }
                if (indent == targetIndent && (trimmed.equals(targetHeader) || trimmed.startsWith(targetHeader + " "))) {
                    foundIndex = lineIdx;
                    break;
                }
            }

            if (foundIndex == -1) {
                return -1;
            }
            if (i == parts.length - 1) {
                return foundIndex;
            }
            currentSearchIndex = foundIndex + 1;
        }

        return -1;
    }

    /**
     * 将缺失的键及模板注释按层级就近插入到磁盘文本行列表中
     */
    private static List<String> mergeMissingKeys(List<String> diskLines, List<String> jarLines, List<String> missingKeys) {
        List<String> result = new ArrayList<>(diskLines);

        for (String missingKey : missingKeys) {
            List<String> snippet = extractKeySnippet(jarLines, missingKey);
            if (snippet.isEmpty()) {
                continue;
            }

            int insertIndex = findInsertionIndex(result, missingKey);
            if (insertIndex >= 0 && insertIndex <= result.size()) {
                result.addAll(insertIndex, snippet);
            } else {
                if (!result.isEmpty() && !result.get(result.size() - 1).trim().isEmpty()) {
                    result.add("");
                }
                result.addAll(snippet);
            }
        }

        return result;
    }

    /**
     * 寻找最佳插入位置：
     * 1. 若同父级 Section 存在，插入在该 Section 最后一个子节点或空行之前；
     * 2. 若父级也不存在，尝试递归匹配更上层 Section；
     * 3. 若为顶层全新模块，返回 -1（表示追加在末尾）。
     */
    private static int findInsertionIndex(List<String> lines, String missingKey) {
        int dotIndex = missingKey.lastIndexOf('.');
        if (dotIndex == -1) {
            return -1; // 全新顶级键
        }

        String parentPath = missingKey.substring(0, dotIndex);
        String[] parts = parentPath.split("\\.");

        int currentSearchIndex = 0;
        int currentIndent = 0;

        for (int i = 0; i < parts.length; i++) {
            String targetHeader = parts[i] + ":";
            int foundSectionIndex = -1;

            for (int lineIdx = currentSearchIndex; lineIdx < lines.size(); lineIdx++) {
                String line = lines.get(lineIdx);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int indent = getIndent(line);
                if (indent == currentIndent && (trimmed.equals(targetHeader) || trimmed.startsWith(targetHeader + " "))) {
                    foundSectionIndex = lineIdx;
                    break;
                }
            }

            if (foundSectionIndex == -1) {
                return -1; // 找不到该层 Section，交由更外层或文件末尾处理
            }

            currentSearchIndex = foundSectionIndex + 1;
            currentIndent += 2; // YAML 标准 2 空格层级
        }

        // 定位到了直接父级 Section 的起始行，向下扫描直到该 Section 结束
        int parentSectionIndent = currentIndent - 2;
        int lastChildIndex = currentSearchIndex;

        for (int lineIdx = currentSearchIndex; lineIdx < lines.size(); lineIdx++) {
            String line = lines.get(lineIdx);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            int indent = getIndent(line);
            // 遇到了同级或更外级的新 Section，说明当前 Section 已结束
            if (indent <= parentSectionIndent && !trimmed.startsWith("#")) {
                return lineIdx;
            }
            lastChildIndex = lineIdx + 1;
        }

        return lastChildIndex;
    }

    /**
     * 从 Jar 模板中提取该 Key 对应的完整代码片段（含其上方紧邻的注释）
     */
    private static List<String> extractKeySnippet(List<String> jarLines, String key) {
        String leafName = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        String matchPrefix = leafName + ":";

        int keyLineIndex = -1;
        for (int i = 0; i < jarLines.size(); i++) {
            String line = jarLines.get(i);
            String trimmed = line.trim();
            if (trimmed.equals(matchPrefix) || trimmed.startsWith(matchPrefix + " ")) {
                keyLineIndex = i;
                break;
            }
        }

        if (keyLineIndex == -1) {
            return Collections.emptyList();
        }

        // 向上查找所有紧邻的注释行
        int startLine = keyLineIndex;
        while (startLine > 0) {
            String prev = jarLines.get(startLine - 1).trim();
            if (prev.startsWith("#")) {
                startLine--;
            } else {
                break;
            }
        }

        // 收集该 Key 的完整行（含注释和可能的多行列表）
        List<String> snippet = new ArrayList<>();
        int baseIndent = getIndent(jarLines.get(keyLineIndex));

        for (int i = startLine; i <= keyLineIndex; i++) {
            snippet.add(jarLines.get(i));
        }

        // 向下收集属于该 Key 的多行内容（如 YAML 列表或多行字符串）
        for (int i = keyLineIndex + 1; i < jarLines.size(); i++) {
            String line = jarLines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                break;
            }
            int indent = getIndent(line);
            if (indent > baseIndent) {
                snippet.add(line);
            } else {
                break;
            }
        }

        return snippet;
    }

    private static int getIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
