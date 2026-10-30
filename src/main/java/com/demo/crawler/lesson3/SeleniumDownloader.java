package com.demo.crawler.lesson3;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.Downloader;
import us.codecraft.webmagic.selector.PlainText;

import java.time.Duration;

/**
 * 把 Selenium 接到 WebMagic 上：自定义一个"下载器"。
 *
 * 意义：WebMagic 的 PageProcessor、Pipeline 你都写好了，唯一的问题是
 * 默认的 HttpClientDownloader 拿不到 JS 渲染后的内容。那我们就只替换"下载器"这一环，
 * 让 WebMagic 改用真实浏览器去取页面，后面的解析/存储逻辑一行都不用改。
 *
 * 这也正是 WebMagic 架构设计优雅的地方：四个角色各自独立、可插拔。
 */
public class SeleniumDownloader implements Downloader {

    /** 浏览器实例（由外面创建好传进来） */
    private final WebDriver driver;
    /** 渲染完成标志：等到这个 CSS 选择器出现，说明 JS 已经把内容画好了 */
    private String waitSelector;
    /** 可选：加载后需要点击的元素（例如"加载更多"按钮），不点就留空 */
    private String clickSelector;
    /** 点击后 / JS 稳定所需的额外等待时间（秒） */
    private int extraWaitSeconds = 2;
    /** 单次等待上限 */
    private Duration timeout = Duration.ofSeconds(15);

    public SeleniumDownloader(WebDriver driver) {
        this.driver = driver;
    }

    /** 链式设置：等哪个选择器出现才认为加载完成 */
    public SeleniumDownloader setWaitSelector(String selector) {
        this.waitSelector = selector;
        return this;
    }

    /** 链式设置：加载完成后点击哪个选择器（如 #load-more） */
    public SeleniumDownloader setClickSelector(String selector) {
        this.clickSelector = selector;
        return this;
    }

    public SeleniumDownloader setExtraWaitSeconds(int seconds) {
        this.extraWaitSeconds = seconds;
        return this;
    }

    /**
     * WebMagic 每要抓一个 URL，就会调用这个方法。我们在这里：
     *  1) 用浏览器打开页面
     *  2) 等 JS 渲染（waitSelector）
     *  3) 按需点击（clickSelector）
     *  4) 取"渲染后"的页面源码，包装成 WebMagic 的 Page 交回去
     */
    @Override
    public Page download(Request request, Task task) {
        driver.get(request.getUrl());

        WebDriverWait wait = new WebDriverWait(driver, timeout);

        if (waitSelector != null) {
            // 等到关键元素出现，证明 JS 已渲染
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitSelector)));
        }

        if (clickSelector != null) {
            // 等到按钮可点（未被禁用、可见），然后点它
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(clickSelector))).click();
        }

        // 给异步渲染留一点缓冲时间（JS 点击后可能还要再画一会儿）
        try { Thread.sleep(extraWaitSeconds * 1000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        // 取"当前浏览器里看到的完整 HTML"（已包含 JS 渲染结果）
        String html = driver.getPageSource();

        // 把结果包装成 WebMagic 认识的 Page 对象
        Page page = new Page();
        page.setRequest(request);
        page.setUrl(new PlainText(request.getUrl()));
        page.setRawText(html);
        page.setStatusCode(200);
        page.setDownloadSuccess(true);
        return page;
    }

    /**
     * WebMagic 会调用这个方法告知并发数。注意：一个浏览器实例是"单线程"的，
     * 多个线程同时操作同一个 driver 会乱套，所以我们这里忽略它，实际运行时请配合 .thread(1)。
     */
    @Override
    public void setThread(int threadNum) {
        // 故意留空：本下载器不支持多线程共享一个浏览器。
    }
}
