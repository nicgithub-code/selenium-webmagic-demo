package com.demo.crawler.common;

import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义"输出管道"（Pipeline）——把抓取结果写成 CSV 文件。
 *
 * WebMagic 的 4 个角色再复习一遍：
 *   Downloader     下载器：拿 HTML（默认 HTTP；动态页换成 Selenium）
 *   PageProcessor  解析器：挑数据（本工程的业务逻辑都在这）
 *   Scheduler      调度器：URL 排队 + 去重（基本不用管）
 *   Pipeline       管道：把结果"落地"成文件/数据库（就是本类）
 *
 * 本类设计成"通用"的：不绑定具体业务，只认一个 Map（一行 = 一个 Map）。
 * 解析器里用 page.putField("row", 一行Map) 把数据交出来，这里就原样写进 CSV。
 */
public class CsvPipeline implements Pipeline {

    /** CSV 文件路径，例如 output/lesson1-books.csv */
    private final String filePath;
    /** CSV 表头（列名顺序），例如 ["书名","作者","价格","评分","标签"] */
    private final String[] headers;

    public CsvPipeline(String filePath, String[] headers) {
        this.filePath = filePath;
        this.headers = headers;
        try {
            // 先确保父目录存在（output/）
            Path p = Paths.get(filePath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            // 每次启动都先清空旧文件，避免上次的结果混进来
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
            // 目录创建失败通常不是致命问题，运行时会再报错暴露出来
        }
    }

    /**
     * WebMagic 每解析出一条结果就回调一次本方法。
     * 用 synchronized 保证多线程写文件时不会"行与行交错"。
     */
    @Override
    public synchronized void process(ResultItems resultItems, Task task) {
        // 重要坑点（新手必看）：
        //   WebMagic 的 page.putField("row", x) 以"字段名"为 key 存进一个 Map，
        //   同一页里如果多次用同一 key 调用 putField，后者会【覆盖】前者！
        //   所以正确做法是：先把本页所有数据收集成一个 List<Map>，最后一次性 putField("rows", list)。
        //   这里就按 "rows" 这个 key 取出整页的列表来写。
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) resultItems.get("rows");
        if (rows == null || rows.isEmpty()) {
            return; // 有些页面（比如详情页）不产出 rows，跳过
        }

        boolean firstWrite = !Files.exists(Paths.get(filePath));
        try (java.io.BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(filePath), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
            if (firstWrite) {
                // BOM（﻿）让 Excel 正确识别 UTF-8，中文不会乱码
                bw.write("﻿");
                bw.write(String.join(",", headers));
                bw.newLine();
            }
            for (Map<String, String> row : rows) {
                // 按表头顺序，把 Map 里对应的值拼成一个数组
                List<String> cells = new ArrayList<>();
                for (String h : headers) {
                    cells.add(safe(row.get(h)));
                }
                bw.write(String.join(",", cells));
                bw.newLine();
                System.out.println("[CSV] 已写入: " + cells);
            }
        } catch (Exception e) {
            System.err.println("[CSV] 写入失败: " + e.getMessage());
        }
    }

    /** 小工具：把 null 变成空串，避免 CSV 出现 "null" 字样 */
    private String safe(String s) {
        return s == null ? "" : s;
    }
}
