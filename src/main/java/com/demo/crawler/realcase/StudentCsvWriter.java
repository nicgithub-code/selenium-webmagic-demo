package com.demo.crawler.realcase;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 学员 CSV 写出工具。
 *
 * 为什么单独抽出来？因为方案一（接口法）和方案二（浏览器法）最后都要"把数据写成 CSV"，
 * 把这部分做成公共方法，两个方案都调它，避免重复代码。
 *
 * 关键点：写入 UTF-8 的 BOM（﻿）头。
 *   - Excel 打开没有 BOM 的 UTF-8 CSV 时，会按系统默认编码（Windows 是 GBK）解读，中文就乱码；
 *   - 加一个 BOM 头，Excel 就能正确识别成 UTF-8。这是爬虫导数据给非技术同事看时的必备细节。
 */
public final class StudentCsvWriter {

    /** 禁止实例化（纯工具类） */
    private StudentCsvWriter() {
    }

    /**
     * 把"一行行学员数据"写成带 BOM 的 UTF-8 CSV。
     *
     * @param file    输出文件路径（如 output/realcase-students-api.csv）
     * @param headers 表头，顺序决定了每一行按什么字段写入
     * @param rows    数据行，每一行是一个 Map（key=字段名，value=值）
     */
    public static void write(String file, String[] headers, List<Map<String, String>> rows) throws IOException {
        Path p = Paths.get(file);
        // 如果父目录（如 output/）不存在，先建出来，否则写文件会报"No such file"
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }

        // try-with-resources：离开作用域自动关闭流，避免文件句柄泄漏
        try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            bw.write("﻿"); // UTF-8 BOM，告诉 Excel "我是 UTF-8"
            bw.write(String.join(",", headers)); // 第一行写表头
            bw.newLine();

            for (Map<String, String> row : rows) {
                // 按 headers 的顺序，从 row 里取出对应字段；缺失的用空串兜底
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < headers.length; i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    line.append(escape(row.getOrDefault(headers[i], "")));
                }
                bw.write(line.toString());
                bw.newLine();
            }
        }
        System.out.println("已写出 " + rows.size() + " 条到 " + file);
    }

    /**
     * CSV 字段转义：如果字段里包含逗号、双引号或换行，必须用双引号包起来，
     * 内部的双引号再写成两个双引号。否则 Excel 会把一行拆成多列。
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
