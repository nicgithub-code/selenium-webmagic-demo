package com.demo.crawler.lesson2;

import com.demo.crawler.common.SeleniumFactory;
import com.demo.crawler.site.LocalSiteServer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第 2 课：用【原生 Selenium】（不借助 WebMagic）爬【动态页面】。
 *
 * 什么时候需要 Selenium？
 *   当网页内容是 JavaScript 运行后才"画"出来时（F12 看源码是空的），
 *   普通的 HTTP 请求只能拿到"空壳"。Selenium 帮你真正打开浏览器、等 JS 跑完、
 *   再读取页面上"看到的东西"。
 *
 * 本课你会学到 Selenium 最常用的 4 个动作：
 *   1. driver.get(url)              —— 打开网页
 *   2. WebDriverWait + 条件          —— 等待某个元素出现（关键！动态页必须等）
 *   3. driver.findElements(定位器)   —— 定位元素（用 CSS 选择器）
 *   4. element.click()              —— 点击（比如"加载更多"按钮）
 */
public class Lesson2SeleniumDynamic {

    /**
     * 运行第 2 课。
     * @param headless true=不显示浏览器窗口（后台跑更快）；false=弹出窗口看它干活（新手推荐）
     */
    public static void run(boolean headless) throws Exception {
        LocalSiteServer site = new LocalSiteServer(LocalSiteServer.DEFAULT_PORT);
        site.start();
        // 创建一个真实浏览器（Edge）。headless=false 时你能亲眼看到它打开网页、点按钮。
        WebDriver driver = SeleniumFactory.createEdgeDriver(headless);

        try {
            driver.get(site.url("/books-spa.html"));
            System.out.println("已打开动态页面，正在等待 JS 渲染出书卡……");

            // ---- 关键：等待"书卡"出现 ----
            // 动态页绝不能"打开就抓"，必须等 JS 把内容渲染出来。
            // WebDriverWait 最多等 10 秒，直到页面里出现至少 1 个 .book-card 元素为止。
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(
                    By.cssSelector(".book-card"), 0));

            // 此时已显示前 3 本（这里只是打印数量做演示，先不抓取，避免重复统计）
            int initialCount = driver.findElements(By.cssSelector(".book-card")).size();
            System.out.println("初始加载了 " + initialCount + " 本书（应为 3）");

            // ---- 点击"加载更多" ----
            WebElement loadMore = driver.findElement(By.id("load-more"));
            loadMore.click();
            System.out.println("已点击「加载更多」按钮，等待剩余内容加载……");

            // 等待书卡数量超过初始值（变成 6）
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(
                    By.cssSelector(".book-card"), initialCount));
            List<WebElement> cards = driver.findElements(By.cssSelector(".book-card"));
            System.out.println("点击后共有 " + cards.size() + " 本书（应为 6）");

            // 只抓取"最终渲染完成"的这 6 张卡（一次即可，避免把前 3 本重复算一遍）
            List<Map<String, String>> rows = extract(cards);

            // 把结果存成 CSV
            String out = "output/lesson2-books.csv";
            writeCsv(out, new String[]{"书名", "作者", "价格", "评分", "标签"}, rows);
            System.out.println("第 2 课完成！结果见 " + out);

        } finally {
            driver.quit();   // 一定要关浏览器！否则浏览器/驱动进程会残留占用资源
            site.stop();
        }
    }

    /** 把一组书卡元素解析成"一行行 Map"（和 WebMagic 课里的 row 结构完全一样） */
    private static List<Map<String, String>> extract(List<WebElement> cards) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (WebElement card : cards) {
            // 在"这张卡"范围内继续定位子元素；.findElement 找 1 个，.findElements 找多个
            String title  = card.findElement(By.cssSelector("h3.title")).getText();
            String author = card.findElement(By.cssSelector("p.author")).getText().replaceFirst("^作者[:：]\\s*", "");
            String price  = card.findElement(By.cssSelector("p.price")).getText().replaceFirst("^价格[:：]\\s*", "");
            String score  = card.findElement(By.cssSelector("p.score")).getText().replaceFirst("^评分[:：]\\s*", "");
            List<WebElement> tagEls = card.findElements(By.cssSelector("ul.tags li"));
            List<String> tags = new ArrayList<>();
            for (WebElement t : tagEls) tags.add(t.getText());

            Map<String, String> row = new LinkedHashMap<>();
            row.put("书名", title);
            row.put("作者", author);
            row.put("价格", price);
            row.put("评分", score);
            row.put("标签", String.join(",", tags));
            rows.add(row);
            System.out.println("  抓到: " + title + " / " + author + " / " + price);
        }
        return rows;
    }

    /** 把数据写成带 BOM 的 UTF-8 CSV（和 CsvPipeline 同款格式，Excel 不乱码） */
    private static void writeCsv(String file, String[] headers, List<Map<String, String>> rows) throws Exception {
        Path p = Paths.get(file);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        try (java.io.BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            bw.write("﻿");
            bw.write(String.join(",", headers));
            bw.newLine();
            for (Map<String, String> r : rows) {
                List<String> cells = new ArrayList<>();
                for (String h : headers) cells.add(r.getOrDefault(h, ""));
                bw.write(String.join(",", cells));
                bw.newLine();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // 默认弹出浏览器窗口（headless=false），方便第一次看清楚 Selenium 在做什么
        run(!"true".equals(System.getProperty("crawler.headless", "false")));
    }
}
