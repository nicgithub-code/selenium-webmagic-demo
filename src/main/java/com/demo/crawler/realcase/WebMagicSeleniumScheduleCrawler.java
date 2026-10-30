package com.demo.crawler.realcase;

import com.demo.crawler.common.CsvPipeline;
import us.codecraft.webmagic.Spider;

/**
 * 【WebMagic + Selenium 拼接】主入口：抓取「课程安排-月视图」下方的列表。
 *
 * 架构一句话（和学员列表那套一模一样，只是换了页面）：
 *   WebMagic 当骨架（调度/解析/输出），Selenium 当下载引擎（登录、切月视图、翻页）。
 *   拼接点就一行：.setDownloader(我们写的 Selenium 下载器)。除此之外 Spider / PageProcessor / Pipeline 全不变。
 *
 * 运行：右键本类 main → Run Java。默认弹 Edge 窗口；后台跑加 VM 参数 -Dcrawler.headless=true。
 *
 * 【跑之前先自查三件事】
 *   1) 站点已启动（http://localhost:5173），账号 admin/123456 能登录
 *   2) 起始 URL 路径是真实的课程安排路由（下面 SCHEDULE_PATH 【必改】）
 *   3) ScheduleSeleniumDownloader / ScheduleListPageProcessor 里的 TODO 都实现完了，
 *      否则一跑就抛 UnsupportedOperationException("TODO(你来实现): ...")
 */
public class WebMagicSeleniumScheduleCrawler {

    /** 站点根地址 */
    private static final String BASE_URL = "http://localhost:5173";
    /** 课程安排页路径 —— 【必改】去浏览器点菜单，看地址栏得到真实路径（如 /schedule/calendar） */
    private static final String SCHEDULE_PATH = "/schedule/calendar";   // TODO(必改)
    /** 输出 CSV 路径 */
    private static final String OUT_CSV = "output/realcase-schedule-webmagic-selenium.csv";

    public static void main(String[] args) throws Exception {
        new WebMagicSeleniumScheduleCrawler().crawlWithWebMagicPlusSelenium();
    }

    /**
     * 组装并启动爬虫。五步，缺一不可：
     *   ① 造下载引擎  ② 建 Spider 挂解析器  ③ 换成 Selenium 下载器  ④ 加起始 URL 与输出管道  ⑤ 单线程跑 + 关浏览器
     */
    public void crawlWithWebMagicPlusSelenium() throws Exception {
        // headless 默认 false：弹窗让你看清浏览器怎么登录、切月视图、翻页；
        // 正式跑加 -Dcrawler.headless=true 就不弹窗了。
        boolean headless = "true".equalsIgnoreCase(System.getProperty("crawler.headless", "false"));

        // ① 下载引擎：自己写的 Selenium 下载器（一个浏览器，必须配合 .thread(1)）
        ScheduleSeleniumDownloader seleniumDownloader = new ScheduleSeleniumDownloader(headless, BASE_URL);

        try {
            Spider.create(new ScheduleListPageProcessor())                   // ② 解析器（WebMagic CSS 选择器）
                    .setDownloader(seleniumDownloader)                       // ③ 关键：把默认 HTTP 下载器换成 Selenium 引擎
                    .addUrl(BASE_URL + SCHEDULE_PATH)                        // ④ 起始 URL（课程安排页，不是列表 iframe）
                    .addPipeline(new CsvPipeline(                            //    输出管道（WebMagic 生态，写 CSV）
                            OUT_CSV,
                            ScheduleListPageProcessor.HEADERS))
                    .thread(1)                                               // ⑤ 单浏览器必须单线程，否则会抢同一个 driver
                    .run();                                                  //    run() 会阻塞到抓完
        } finally {
            // 不管成功还是抛异常都要关浏览器，不然进程会留下一个 Edge 僵尸窗口
            seleniumDownloader.closeBrowser();
        }
    }
}
