package com.demo.crawler.realcase;

import com.demo.crawler.common.CsvPipeline;
import us.codecraft.webmagic.Spider;

/**
 * 【WebMagic + Selenium 拼接】主入口（= 你公司用的那种方式）。
 *
 * 架构一句话：
 *   WebMagic 当骨架（调度/解析/输出），Selenium 当下载引擎（打开浏览器、登录、翻页、渲染）。
 *   两者通过 WebMagic 的 Downloader 接口拼接 —— 把默认的 HTTP 下载器换成我们写的
 *   StudentSeleniumDownloader 即可，其余（Spider、PageProcessor、Pipeline）完全不变。
 *
 * 三个角色在本工程里的落点：
 *   - 骨架/调度/输出：us.codecraft.webmagic.Spider  +  CsvPipeline（WebMagic 自带生态）
 *   - 下载引擎      ：StudentSeleniumDownloader（Selenium 真浏览器）  ← 被塞进来的部分
 *   - 解析器        ：StudentListPageProcessor（WebMagic CSS 选择器）
 *
 * 运行：右键本类 main → Run Java。默认弹出浏览器窗口（想后台跑加 -Dcrawler.headless=true）。
 */
public class WebMagicSeleniumStudentCrawler {

    /** 站点根地址 */
    private static final String BASE_URL = "http://localhost:5173";

    public static void main(String[] args) throws Exception {
        new WebMagicSeleniumStudentCrawler().crawlWithWebMagicPlusSelenium();
    }

    /**
     * 用 WebMagic + Selenium 拼接的方式抓取学员列表。
     * 方法名特意带上 WebMagic 和 Selenium，一眼能看出这是"两栈拼接"方案。
     */
    public void crawlWithWebMagicPlusSelenium() throws Exception {
        // headless 默认 false：弹窗让你看清浏览器怎么登录、翻页；后台跑请用 -Dcrawler.headless=true
        boolean headless = "true".equalsIgnoreCase(System.getProperty("crawler.headless", "false"));

        // 下载引擎：自己写的 Selenium 下载器（一个浏览器，务必配合 .thread(1)）
        StudentSeleniumDownloader seleniumDownloader = new StudentSeleniumDownloader(headless, BASE_URL);

        try {
            Spider.create(new StudentListPageProcessor())                 // ① 解析器（WebMagic 选择器）
                    .setDownloader(seleniumDownloader)                    // ② 关键：把默认 HTTP 下载器换成 Selenium 引擎
                    .addUrl(BASE_URL + "/student/list")                   // ③ 起始 URL
                    .addPipeline(new CsvPipeline(                         // ④ 输出管道（WebMagic 生态，写 CSV）
                            "output/realcase-students-webmagic-selenium.csv",
                            StudentListPageProcessor.HEADERS))
                    .thread(1)                                            // ⑤ 单浏览器必须单线程
                    .run();
        } finally {
            seleniumDownloader.closeBrowser(); // 不管成功失败都关浏览器
        }
    }
}
