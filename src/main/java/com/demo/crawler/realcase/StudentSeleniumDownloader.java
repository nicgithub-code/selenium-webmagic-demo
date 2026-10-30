package com.demo.crawler.realcase;

import com.demo.crawler.common.SeleniumFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.Downloader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 【WebMagic + Selenium 拼接】的"下载引擎"：StudentSeleniumDownloader。
 *
 * 它实现 WebMagic 的 Downloader 接口 —— 也就是说，对 WebMagic 而言，
 * 它就是个普通的"下载器"，只不过底层不是 HTTP，而是【真浏览器(Selenium)】。
 *
 * 这就是"拼接"的核心：
 *   WebMagic 负责调度/解析/输出（骨架），本类负责"把 URL 变成渲染后的 HTML"（引擎）。
 *   两者通过 Downloader 接口解耦，WebMagic 完全不知道数据是怎么来的。
 *
 * 本类做的事（和纯 Selenium 方案一样，但结果交给 WebMagic 而不是自己写 CSV）：
 *   1) 第一次下载前先登录一次（一个浏览器保持登录态，后面所有请求复用）；
 *   2) 打开学员列表页，循环点"下一页"翻页，把每一页的 <tr> 行收集起来；
 *   3) 把所有行拼成一段"合成 HTML"，塞进 WebMagic 的 Page，交给 StudentListPageProcessor 去解析。
 */
public class StudentSeleniumDownloader implements Downloader {

    /** Selenium 真浏览器（一个下载器 = 一个浏览器，所以必须单线程，见 setThread） */
    private final WebDriver driver;
    /** 站点根地址 */
    private final String baseUrl;
    /** 显式等待器 */
    private final WebDriverWait wait;
    /** 是否已登录（登录只需做一次） */
    private boolean loggedIn = false;

    public StudentSeleniumDownloader(boolean headless, String baseUrl) {
        this.baseUrl = baseUrl;
        // 复用工程里的 Selenium 驱动工厂
        this.driver = SeleniumFactory.createEdgeDriver(headless);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /**
     * WebMagic 每要抓一个 URL 就回调一次本方法。
     * 我们在这里用 Selenium 把页面"真正跑起来"，再把渲染后的 HTML 交回去。
     */
    @Override
    public Page download(Request request, Task task) {
        // ① 第一次才登录（之后浏览器已带登录态，直接复用）
        if (!loggedIn) {
            loginBySelenium();
            loggedIn = true;
        }

        // ② 打开目标页（学员列表）
        driver.get(request.getUrl());
        System.out.println("[Selenium引擎] 已打开列表页，开始翻页收集...");

        // ③ 循环翻页，把每行 <tr> 的 outerHTML 收集起来
        List<String> rowHtmls = new ArrayList<>();
        String previousFirstName = null;
        while (true) {
            // 等表格行出现（至少 1 行）
            wait.until(ExpectedConditions
                    .presenceOfElementLocated(By.cssSelector(".el-table__row")));
            List<WebElement> trs = driver.findElements(By.cssSelector(".el-table__row"));
            for (WebElement tr : trs) {
                // outerHTML = 这个 <tr> 节点及其全部子节点的 HTML 文本
                rowHtmls.add(tr.getAttribute("outerHTML"));
            }
            System.out.println("  [Selenium引擎] 本页收集 " + trs.size() + " 行，累计 " + rowHtmls.size());

            // ④ 找"下一页"按钮；到末页时按钮带 disabled 属性（而非 is-disabled 类），停止。
            //    用 isEnabled() 读属性最稳，不依赖 Element Plus 版本的类名习惯。
            WebElement nextBtn = driver.findElement(By.cssSelector(".el-pagination .btn-next"));
            if (!nextBtn.isEnabled()) {
                break;
            }
            // ⑤ 点下一页，等新一页第一条变了再继续（防止翻页动画期间误读旧数据）
            String currentFirstName = firstCellText(trs);
            if (previousFirstName == null) {
                previousFirstName = currentFirstName;
            }
            nextBtn.click();
            String finalPrev = previousFirstName;
            wait.until(d -> {
                List<WebElement> n = d.findElements(By.cssSelector(".el-table__row"));
                return !n.isEmpty() && !firstCellText(n).equals(finalPrev);
            });
            List<WebElement> newTrs = driver.findElements(By.cssSelector(".el-table__row"));
            previousFirstName = firstCellText(newTrs);
        }

        // ⑥ 把收集到的所有行拼成一段合成 HTML，交给 WebMagic 解析器
        //    （页面原本是 el-table 渲染的，这里把零散的 <tr> 包回 <table><tbody> 让它可被选择器解析）
        StringBuilder html = new StringBuilder("<table><tbody>");
        for (String r : rowHtmls) {
            html.append(r);
        }
        html.append("</tbody></table>");

        // ⑦ 构造 WebMagic 的 Page 对象：把"渲染后的 HTML"设为原始文本
        Page page = new Page();
        page.setRequest(request);
        page.setRawText(html.toString());
        return page;
    }

    /** 用 Selenium 走一遍登录（填账号密码 -> 点登录 -> 等跳转 dashboard） */
    private void loginBySelenium() {
        driver.get(baseUrl + "/login");
        WebElement userInput = wait.until(ExpectedConditions
                .presenceOfElementLocated(By.cssSelector("input[placeholder='请输入账号']")));
        WebElement pwdInput = driver.findElement(By.cssSelector("input[placeholder='请输入密码']"));
        userInput.sendKeys("admin");
        pwdInput.sendKeys("123456");
        driver.findElement(By.cssSelector("button.login-btn")).click();
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        System.out.println("[Selenium引擎] 登录成功");
    }

    /** 取表格第一行"姓名"单元格（索引2），用于翻页判重 */
    private String firstCellText(List<WebElement> trs) {
        if (trs.isEmpty()) {
            return "";
        }
        List<WebElement> cells = trs.get(0).findElements(By.cssSelector("td .cell"));
        return cells.size() > 2 ? cells.get(2).getText().trim() : "";
    }

    /**
     * WebMagic 会问下载器"支持几个线程"。
     * 一个 Selenium 下载器 = 一个浏览器，多线程会抢同一个浏览器导致数据错乱，
     * 所以这里【故意忽略】，强制单线程（Spider 那边也要 .thread(1)）。
     */
    @Override
    public void setThread(int threadNum) {
        // 单浏览器不支持多线程，留空即可
    }

    /** 爬虫结束后关闭浏览器，释放资源 */
    public void closeBrowser() {
        driver.quit();
    }
}
