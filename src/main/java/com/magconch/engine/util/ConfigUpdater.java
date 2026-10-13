package com.magconch.engine.util;

import com.magconch.engine.MagconchEngine;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 配置增量同步工具类
 * <p>
 * 对比 Jar 内置模板与磁盘文件，自动将缺失键及其注释完整插入至对应层级节点，并清理已废弃的冗余配置块。
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

        // 3. 提取不相交的最浅缺失根路径（避免子节点重复插入并保障层级树完整性）
        List<String> rootMissingKeys = new ArrayList<>();
        int totalMissingLeaves = 0;
        for (String key : jarConfig.getKeys(true)) {
            if (!diskConfig.contains(key)) {
                if (!jarConfig.isConfigurationSection(key)) {
                    totalMissingLeaves++;
                }
                boolean coveredByAncestor = false;
                for (String existing : rootMissingKeys) {
                    if (key.startsWith(existing + ".")) {
                        coveredByAncestor = true;
                        break;
                    }
                }
                if (!coveredByAncestor) {
                    rootMissingKeys.add(key);
                }
            }
        }

        // 4. 提取已废弃节点
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

        if (rootMissingKeys.isEmpty() && obsoleteKeys.isEmpty()) {
            return 0;
        }

        // 5. 解析磁盘文本行与 Jar 模板文本行，执行废弃块清理与缺失键就近插入
        try {
            List<String> diskLines = Files.readAllLines(targetFile.toPath(), StandardCharsets.UTF_8);
            List<String> updatedLines = removeObsoleteKeys(diskLines, obsoleteKeys);

            if (!rootMissingKeys.isEmpty()) {
                List<String> jarLines;
                try (InputStream templateIn = plugin.getResource(resourcePath)) {
                    if (templateIn == null) {
                        return 0;
                    }
                    jarLines = readLines(templateIn);
                }
                updatedLines = mergeMissingKeys(updatedLines, jarLines, rootMissingKeys);
            }

            Files.write(targetFile.toPath(), updatedLines, StandardCharsets.UTF_8);
            return totalMissingLeaves + obsoleteKeys.size();
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
     *
     * @param lines   文本行列表
     * @param keyPath 点号分隔的配置路径（如 "commands.trash.description"）
     * @return 目标键所在行的索引；若未找到则返回 -1
     */
    private static int findKeyLineIndex(List<String> lines, String keyPath) {
        String[] parts = keyPath.split("\\.");
        int currentSearchIndex = 0;
        int minAllowedIndent = 0;

        for (int i = 0; i < parts.length; i++) {
            String targetHeader = parts[i] + ":";
            int foundIndex = -1;
            int foundIndent = -1;

            for (int lineIdx = currentSearchIndex; lineIdx < lines.size(); lineIdx++) {
                String line = lines.get(lineIdx);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int indent = getIndent(line);
                if (i > 0 && indent <= minAllowedIndent) {
                    // 已退出当前父级 Section 的缩进范围
                    return -1;
                }
                if (i == 0 && indent != 0) {
                    // 根节点缩进必须为 0
                    continue;
                }

                if (trimmed.equals(targetHeader) || trimmed.startsWith(targetHeader + " ")) {
                    if (i == 0 || indent > minAllowedIndent) {
                        foundIndex = lineIdx;
                        foundIndent = indent;
                        break;
                    }
                }
            }

            if (foundIndex == -1) {
                return -1;
            }
            if (i == parts.length - 1) {
                return foundIndex;
            }
            currentSearchIndex = foundIndex + 1;
            minAllowedIndent = foundIndent;
        }

        return -1;
    }

    /**
     * 将缺失的最浅配置片段按层级精准就近插入到磁盘文本行列表中
     */
    private static List<String> mergeMissingKeys(List<String> diskLines, List<String> jarLines, List<String> rootMissingKeys) {
        List<String> result = new ArrayList<>(diskLines);

        for (String missingKey : rootMissingKeys) {
            List<String> snippet = extractSectionSnippet(jarLines, missingKey);
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
     * 若该节点存在父级 Section，插入在该父级 Section 内部末尾；
     * 若为顶层键（无父级），返回 -1（交由末尾追加）。
     */
    private static int findInsertionIndex(List<String> lines, String keyPath) {
        int dotIndex = keyPath.lastIndexOf('.');
        if (dotIndex == -1) {
            return -1; // 顶级全新块，直接追加在文件末尾
        }

        String parentPath = keyPath.substring(0, dotIndex);
        int parentLineIndex = findKeyLineIndex(lines, parentPath);
        if (parentLineIndex == -1) {
            return -1;
        }

        int parentIndent = getIndent(lines.get(parentLineIndex));
        int lastChildIndex = parentLineIndex + 1;

        // 向下扫描定位该父级 Section 结束的位置
        for (int lineIdx = parentLineIndex + 1; lineIdx < lines.size(); lineIdx++) {
            String line = lines.get(lineIdx);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            int indent = getIndent(line);
            // 遇到了同级或更外级的行（含同缩进注释），说明当前父级 Section 已结束
            if (indent <= parentIndent) {
                return lineIdx;
            }
            lastChildIndex = lineIdx + 1;
        }

        return lastChildIndex;
    }

    /**
     * 从 Jar 模板中提取该 Key 对应的完整代码片段（含其上方紧邻同缩进注释及其下属所有子行）
     */
    private static List<String> extractSectionSnippet(List<String> jarLines, String keyPath) {
        int keyLineIndex = findKeyLineIndex(jarLines, keyPath);
        if (keyLineIndex == -1) {
            return Collections.emptyList();
        }

        int baseIndent = getIndent(jarLines.get(keyLineIndex));

        // 1. 向上抓取同缩进的紧邻注释行
        int startLine = keyLineIndex;
        while (startLine > 0) {
            String prevLine = jarLines.get(startLine - 1);
            String prevTrimmed = prevLine.trim();
            if (prevTrimmed.startsWith("#") && getIndent(prevLine) == baseIndent) {
                startLine--;
            } else {
                break;
            }
        }

        // 2. 向下抓取自身及其下所有子行 (indent > baseIndent)
        int endLine = keyLineIndex + 1;
        while (endLine < jarLines.size()) {
            String line = jarLines.get(endLine);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                int nextNonEmpty = endLine + 1;
                while (nextNonEmpty < jarLines.size() && jarLines.get(nextNonEmpty).trim().isEmpty()) {
                    nextNonEmpty++;
                }
                if (nextNonEmpty < jarLines.size() && getIndent(jarLines.get(nextNonEmpty)) > baseIndent) {
                    endLine = nextNonEmpty;
                    continue;
                }
                break;
            }
            if (getIndent(line) > baseIndent) {
                endLine++;
            } else {
                break;
            }
        }

        return new ArrayList<>(jarLines.subList(startLine, endLine));
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
