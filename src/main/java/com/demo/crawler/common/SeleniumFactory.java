package com.demo.crawler.common;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

/**
 * Selenium 浏览器驱动工厂。
 *
 * 一句话解释 Selenium 在爬虫里干什么：
 *   它是一个"机器人司机"，替你打开 Edge 浏览器、输入网址、等待页面加载、
 *   点击按钮、读取页面上看到的内容。凡是 JS 渲染 / 需要登录 / 需要交互的页面，
 *   普通 HTTP 请求拿不到，就得请它出马。
 *
 * 关于"驱动"（driver）：
 *   Selenium 4.6+ 自带 Selenium Manager，第一次运行时会自动下载与你的 Edge
 *   版本匹配的 msedgedriver（一个驱动程序），通常不用手动装。
 *   如果公司内网下载失败，可手动下载 msedgedriver.exe 放到某目录，
 *   然后启动时加 JVM 参数：-Dcrawler.edge.driver=D:/xxx/msedgedriver.exe
 */
public class SeleniumFactory {

    /**
     * 创建一个 Edge 浏览器实例。
     *
     * @param headless 是否"无头模式"：true=不显示浏览器窗口（更快、适合后台批量跑）；
     *                          false=弹出真实浏览器窗口（适合新手第一次看它怎么干活）
     * @return WebDriver 对象，代表"一个可控的浏览器"
     */
    public static WebDriver createEdgeDriver(boolean headless) {
        EdgeOptions options = new EdgeOptions();

        // 一些让无头模式更稳、更像真人的启动参数
        options.addArguments("--window-size=1920,1080"); // 给个足够大的窗口，避免元素因为太小没渲染
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");               // 在受限/服务器环境常见
        options.addArguments("--disable-dev-shm-usage");     // 防止 /dev/shm 太小导致崩溃

        if (headless) {
            // 新版 Edge 支持 "--headless=new"，比老版 --headless 功能更全
            options.addArguments("--headless=new");
        }

        // 解析驱动路径：三级优先级
        //   1) -Dcrawler.edge.driver=路径            （用户手动指定，最优先）
        //   2) 项目目录下 drivers/msedgedriver.exe   （已随项目放好，无需联网）
        //   3) 都没——交给 Selenium Manager 自动下载（需联网，可能受网络限制失败）
        String driverPath = System.getProperty("crawler.edge.driver");
        if (driverPath == null || driverPath.trim().isEmpty()) {
            // 相对"启动目录"去找（详见 docs/04-操作手册.md 关于 drivers 目录的说明）
            java.nio.file.Path local = java.nio.file.Paths.get(
                    System.getProperty("user.dir"), "drivers", "msedgedriver.exe");
            if (java.nio.file.Files.exists(local)) {
                driverPath = local.toString();
            }
        }

        if (driverPath != null && !driverPath.trim().isEmpty()) {
            System.setProperty("webdriver.edge.driver", driverPath);
            System.out.println("[Selenium] 使用本地驱动: " + driverPath);
        } else {
            System.out.println("[Selenium] 未找到本地驱动，尝试用 Selenium Manager 自动匹配 Edge 版本（首次需联网下载）");
        }

        return new EdgeDriver(options);
    }
}
