package com.demo.crawler.lesson3;

import com.demo.crawler.common.CsvPipeline;
import com.demo.crawler.common.SeleniumFactory;
import com.demo.crawler.site.LocalSiteServer;
import org.openqa.selenium.WebDriver;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.downloader.HttpClientDownloader;

/**
 * 第 3 课：WebMagic + Selenium 联手（用真实浏览器做下载器）。
 *
 * 本课做一个对照实验，让你一眼看懂"下载器"决定了能不能拿到动态内容：
 *   实验 A：用默认 HTTP 下载器 抓 动态页面  -> 拿到 0 本（只抓到空壳）
 *   实验 B：换成 Selenium 下载器 抓 同一个页面 -> 拿到 6 本（JS 渲染后的真实内容）
 */
public class Lesson3WebMagicPlusSelenium {

    public static void run(boolean headless) throws Exception {
        LocalSiteServer site = new LocalSiteServer(LocalSiteServer.DEFAULT_PORT);
        site.start();

        try {
            // ============ 实验 A：HTTP 下载器（WebMagic 默认） ============
            System.out.println("\n========== 实验 A：默认 HTTP 下载器 抓 动态页面 ==========");
            Spider.create(new BookSpaPageProcessor())
                    .setDownloader(new HttpClientDownloader())               // 显式指定默认下载器
                    .addUrl(site.url("/books-spa.html"))
                    .addPipeline(new CsvPipeline("output/lesson3-http.csv",
                            new String[]{"书名", "作者", "价格", "评分", "标签"}))
                    // 注意：HTTP 下载器是多线程安全的，可以 .thread(3)；这里仍用 1 保持简单
                    .run();
            System.out.println("实验 A 结束：本页没有任何 .book-card，所以 output/lesson3-http.csv 要么为空、要么不存在——这就是'没等 JS'的后果。");

            // ============ 实验 B：Selenium 下载器 ============
            System.out.println("\n========== 实验 B：Selenium 下载器 抓 同一个动态页面 ==========");
            WebDriver driver = SeleniumFactory.createEdgeDriver(headless);
            // 自定义下载器，并告诉它：等 .book-card 出现 + 点击 #load-more 展开全部
            SeleniumDownloader seleniumDownloader = new SeleniumDownloader(driver)
                    .setWaitSelector("div.book-card")
                    .setClickSelector("#load-more")
                    .setExtraWaitSeconds(2);

            Spider.create(new BookSpaPageProcessor())   // 解析器一行没改！
                    .setDownloader(seleniumDownloader)
                    .addUrl(site.url("/books-spa.html"))
                    .addPipeline(new CsvPipeline("output/lesson3-selenium.csv",
                            new String[]{"书名", "作者", "价格", "评分", "标签"}))
                    .thread(1)                          // 重要：Selenium 下载器是单浏览器，必须单线程
                    .run();

            driver.quit(); // 用完关浏览器
            System.out.println("实验 B 结束：打开 output/lesson3-selenium.csv，应有 6 本书。");

        } finally {
            site.stop();
        }
        System.out.println("\n第 3 课完成！对比 lesson3-http.csv 与 lesson3-selenium.csv 即可验证结论。");
    }

    public static void main(String[] args) throws Exception {
        run(!"false".equals(System.getProperty("crawler.headless", "true")));
    }
}
