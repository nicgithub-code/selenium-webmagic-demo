package com.demo.crawler.lesson4;

import com.demo.crawler.common.CsvPipeline;
import us.codecraft.webmagic.Spider;

/**
 * 第 4 课：把第 1 课学到的方法，用到【真实网站】上。
 *
 * 目标站：https://quotes.toscrape.com  —— 专门给爬虫新手练手用的公开站点，
 *         robots.txt 允许抓取，无反爬、无需登录，非常友好。
 *
 * 你会发现：解析器的写法和第 1 课一模一样，只是把 URL 从本地 http://127.0.0.1
 * 换成了真实的 https://...。这正是 WebMagic 的价值——业务代码与"数据从哪来"解耦。
 *
 * 前提：本机需要能访问外网。如果连不上，程序会给出友好提示并跳过。
 */
public class Lesson4RealSite {

    public static void run() {
        String startUrl = "https://quotes.toscrape.com/";
        System.out.println("第 4 课：开始爬真实站点 " + startUrl + "（需要联网）");

        try {
            Spider.create(new QuotePageProcessor())
                    .addUrl(startUrl)
                    .addPipeline(new CsvPipeline("output/lesson4-quotes.csv",
                            new String[]{"名言", "作者", "标签"}))
                    .thread(3)
                    .run();
            System.out.println("第 4 课完成！结果见 output/lesson4-quotes.csv");
        } catch (Exception e) {
            // 常见失败原因：没有外网、公司网络拦截、DNS 解析失败
            System.err.println("第 4 课执行失败（多半是网络问题）：" + e.getMessage());
            System.err.println("提示：本机需要能访问 quotes.toscrape.com。可换网络或跳过本课后半段再试。");
        }
    }

    public static void main(String[] args) {
        run();
    }
}
