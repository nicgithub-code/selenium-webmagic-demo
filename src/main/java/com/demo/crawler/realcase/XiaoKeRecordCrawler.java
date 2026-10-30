package com.demo.crawler.realcase;

import com.demo.crawler.common.CsvPipeline;
import com.demo.crawler.common.SeleniumFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.selector.Selectable;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.Downloader;
import us.codecraft.webmagic.processor.PageProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【单文件版 · Selenium + WebMagic】抓取「课程管理 - 消课记录」。
 *
 * 把下载引擎、解析器、启动入口全写在同一个文件里，方便单独拎出去跑。
 * 套路和 WebMagicSeleniumScheduleCrawler / ScheduleSeleniumDownloader 完全一致，只是换了页面。
 *
 * 架构一句话：
 *   WebMagic 当骨架（调度 / 解析 / 输出 CSV），Selenium 当下载引擎（登录、切视图、翻页、抠 DOM）。
 *   拼接点：.setDownloader(我们写的 XiaoKeDownloader)。
 *
 * 运行：右键本类 main → Run Java。默认弹 Edge 窗口；后台跑加 VM 参数 -Dcrawler.headless=true。
 *
 * 【跑之前必须核对的 3 处（已用 TODO 标出）】
 *   1) RECORD_PATH        —— 消课记录页的真实路由（点菜单看地址栏）
 *   2) NEED_VIEW_SWITCH   —— 该页是否要先从"月视图/卡片"切到"列表视图"（同排课页那样）
 *   3) XiaoKeRecordProcessor.HEADERS + process() 里的 cell(...) 索引 —— 列名与顺序
 *
 * 和排课页一致的已验证事实（直接复用，不用再测）：
 *   · 登录 admin/123456，登录后等 URL 含 /dashboard
 *   · 数据行 = .el-table__row，单元格文本在 td .cell 里
 *   · 末页"下一页"按钮靠 aria-disabled="true" 判断（不是 isEnabled()）
 *   · 翻页后用"首行指纹变了"判断异步加载完成
 */
public class XiaoKeRecordCrawler {

    /** 站点根地址 */
    private static final String BASE_URL = "http://localhost:5173";
    /** 消课记录页路径 —— 【必改】去浏览器点菜单，看地址栏得到真实路径（如 /course/consumed） */
    private static final String RECORD_PATH = "/consumption/record";
    /** 输出 CSV 路径 */
    private static final String OUT_CSV = "output/realcase-xiaoke-webmagic-selenium.csv";

    public static void main(String[] args) throws Exception {
        // headless 默认 false 弹窗看过程；正式跑加 -Dcrawler.headless=true
        boolean headless = "true".equalsIgnoreCase(System.getProperty("crawler.headless=true", "false"));

        // ① 下载引擎：自己写的 Selenium 下载器（一个浏览器，必须配合 .thread(1)）
        XiaoKeDownloader seleniumDownloader = new XiaoKeDownloader(headless, BASE_URL);

        try {
            Spider.create(new XiaoKeRecordProcessor())                  // ② 解析器
                    .setDownloader(seleniumDownloader)                  // ③ 关键：换掉默认 HTTP 下载器
                    .addUrl(BASE_URL + RECORD_PATH)                     // ④ 起始 URL
                    .addPipeline(new CsvPipeline(                       // ⑤ 输出管道写 CSV
                            OUT_CSV,
                            XiaoKeRecordProcessor.HEADERS))
                    .thread(1)                                          // ⑥ 单浏览器必须单线程
                    .run();
        } finally {
            seleniumDownloader.closeBrowser();                          // 关浏览器，避免僵尸进程
        }
    }

    // =================================================================================
    // 下载引擎：用真浏览器把页面跑起来，把所有行的 outerHTML 收齐后交给解析器
    // =================================================================================
    public static class XiaoKeDownloader implements Downloader {

        private final WebDriver driver;
        private final String baseUrl;
        private final WebDriverWait wait;
        private boolean loggedIn = false;

        // ===== 选择器集中放这里，改的时候不用翻代码（按 Element Plus 惯例，跑前用 F12 核对）=====
        /** 数据行的重复单元 */
        private static final String SEL_ROW = ".el-table__row";
        /** 分页容器（用来确认分页条已渲染） */
        private static final String SEL_PAGINATION = ".el-pagination";
        /** "下一页"按钮 */
        private static final String SEL_NEXT_BTN = ".el-pagination .btn-next";

        // —— 视图切换（若该页本身就是列表表格、无需切换，把下面开关设为 false）——
        /** 该页是否要先切到"列表视图"。TODO: 若无需切换（本身就是表格）改成 false */
        private static final boolean NEED_VIEW_SWITCH = true;
        private static final String SEL_VIEW_OPTION = ".el-radio-group .el-radio-button";
        private static final String SEL_VIEW_INPUT = "input.el-radio-button__original-radio";
        /** 目标视图的 value（Element Plus 的 el-radio-button 隐藏 input 的 value） */
        private static final String TARGET_VIEW_VALUE = "list";
        /** 目标视图的可见文字（value 查不到时兜底） */
        private static final String TARGET_VIEW_TEXT = "列表视图";
        // =============================================================================

        public XiaoKeDownloader(boolean headless, String baseUrl) {
            this.baseUrl = baseUrl;
            this.driver = SeleniumFactory.createEdgeDriver(headless);
            this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        }

        @Override
        public Page download(Request request, Task task) {
            ensureLoggedIn();                                   // ① 登录（幂等，只一次）
            driver.get(request.getUrl());                        // ② 打开消课记录页
            System.out.println("[Selenium引擎] 已打开消课记录页");

            if (NEED_VIEW_SWITCH) {
                switchToListView();                             // ③ 必要时切到列表视图
            }

            List<String> rowHtmls = collectRowsFromAllPages();  // ④ 循环翻页收集所有行

            // ⑤ 拼合成 HTML 交给解析器（零散 <tr> 包回 <table><tbody> 才能让 CSS 选择器生效）
            Page page = new Page();
            page.setRequest(request);
            page.setRawText(buildTableHtml(rowHtmls));
            return page;
        }

        /** 步骤①：登录（只需一次，之后浏览器带着登录态复用） */
        private void ensureLoggedIn() {
            if (loggedIn) return;
            driver.get(baseUrl + "/login");
            WebElement user = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("input[placeholder='请输入账号']")));
            WebElement pwd = driver.findElement(By.cssSelector("input[placeholder='请输入密码']"));
            user.sendKeys("admin");
            pwd.sendKeys("123456");
            driver.findElement(By.cssSelector("button.login-btn")).click();
            wait.until(ExpectedConditions.urlContains("/dashboard"));
            System.out.println("[Selenium引擎] 登录成功");
            loggedIn = true;
        }

        /** 步骤③：有需要才切到"列表视图"（el-radio-button 按钮组，按隐藏 input 的 value 定位） */
        private void switchToListView() {
            WebElement target = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//label[.//input[@value='" + TARGET_VIEW_VALUE + "']]")));
            String cls = target.getAttribute("class");
            boolean alreadyActive = cls != null && cls.contains("is-active");
            if (!alreadyActive) {
                target.click();   // 点外层 label，别点隐藏的 input
                System.out.println("[Selenium引擎] 已切换到「列表视图」");
            }
            // 切换是异步拉数据，必须等表格 + 分页条都出现再返回
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_ROW)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_PAGINATION)));
        }

        /** 步骤④：循环翻页，收集所有数据行的 outerHTML（跨页累积，顺序即页面顺序） */
        private List<String> collectRowsFromAllPages() {
            List<String> rowHtmls = new ArrayList<>();
            while (true) {
                // 4.1 等表格行出现并抓回
                List<WebElement> trs = wait.until(ExpectedConditions
                        .presenceOfAllElementsLocatedBy(By.cssSelector(SEL_ROW)));

                // 4.2 把本页每一行的 outerHTML 收起来
                for (WebElement tr : trs) {
                    rowHtmls.add(tr.getAttribute("outerHTML"));
                }
                System.out.println("  [Selenium引擎] 本页收集 " + trs.size()
                        + " 行，累计 " + rowHtmls.size());

                // 4.3 末页时 aria-disabled="true" → 结束
                WebElement nextBtn = driver.findElement(By.cssSelector(SEL_NEXT_BTN));
                if ("true".equals(nextBtn.getAttribute("aria-disabled"))) {
                    break;
                }

                // 4.4 用"当前页"第一行指纹当翻页判据（顶部刚抓的 trs 就是本页）
                String currentSignature = firstRowSignature(trs);

                // 4.5 点下一页，等到"首行指纹变了"才继续，防止异步期间误读旧数据
                nextBtn.click();
                wait.until(d -> {
                    List<WebElement> n = d.findElements(By.cssSelector(SEL_ROW));
                    return !n.isEmpty() && !firstRowSignature(n).equals(currentSignature);
                });
            }
            return rowHtmls;
        }

        /** 取当前列表第一行的整行特征串，用于判断翻页后数据是否真的换了 */
        private String firstRowSignature(List<WebElement> rows) {
            if (rows.isEmpty()) return "";
            List<WebElement> cells = rows.get(0).findElements(By.cssSelector("td .cell"));
            StringBuilder sb = new StringBuilder();
            for (WebElement c : cells) {
                sb.append(c.getText().trim()).append(" | ");
            }
            return sb.toString();
        }

        /** 把零散 <tr> 拼成可被 WebMagic 选择器解析的合成 HTML */
        private String buildTableHtml(List<String> rowHtmls) {
            StringBuilder html = new StringBuilder("<table><tbody>");
            for (String r : rowHtmls) {
                html.append(r);
            }
            return html.append("</tbody></table>").toString();
        }

        /** 单浏览器不支持多线程，留空即可（Spider 那边也要 .thread(1)） */
        @Override
        public void setThread(int threadNum) {
        }

        /** 爬虫结束后关闭浏览器释放资源（在 main 的 finally 里调用） */
        public void closeBrowser() {
            driver.quit();
        }
    }

    // =================================================================================
    // 解析器：把合成 HTML 里的每一行切成字段，攒成 List<Map> 交给 CsvPipeline
    // =================================================================================
    public static class XiaoKeRecordProcessor implements PageProcessor {

        /**
         * CSV 表头（顺序即写入顺序）。【必改】按你在页面上 F12 查到的真实列调整。
         * 提示：查列顺序用 Console 跑
         *   Array.from(document.querySelectorAll('.el-table__row')[0]
         *     .querySelectorAll('td .cell')).map((e,i)=>i+' : '+e.innerText)
         * ⚠️ 若表格第一列是"选择框/序号"等非数据列，下面 process() 里的索引要整体 +1。
         */
        public static final String[] HEADERS = {
                "序号",      // 累计行号（处理器生成，不取自页面）
                "学员姓名",  // TODO(必改)：按真实列顺序调整下面所有字段与索引
                "课程",
                "老师",
                "教室",
                "上课时间",
                "课时",
                "状态"
        };

        @Override
        public void process(Page page) {
            // ① 把合成 HTML 里的每一行切成 Selectable 节点
            List<Selectable> rows = page.getHtml().css("tr.el-table__row").nodes();

            List<Map<String, String>> data = new ArrayList<>();
            int seq = 1;   // 跨页累计序号
            for (Selectable row : rows) {
                // ② 一行里所有单元格（td .cell 才是可见文本）
                List<Selectable> cells = row.css("td .cell").nodes();

                Map<String, String> m = new LinkedHashMap<>();
                // TODO(必改)：按 F12 确认的列顺序调整索引。
                //   若第一列是"选择框/序号"非数据列，则所有索引 +1（学员姓名→cell(2) 等）。
                m.put("序号", String.valueOf(seq++));   // 生成列，不取自页面
                m.put("学员姓名", cell(cells, 1));
                m.put("课程",     cell(cells, 2));
                m.put("老师",     cell(cells, 3));
                m.put("教室",     cell(cells, 4));
                m.put("上课时间", cell(cells, 5));
                m.put("课时",     cell(cells, 6));
                m.put("状态",     cell(cells, 7));

                data.add(m);
            }

            System.out.println("[WebMagic解析] 共提取 " + data.size() + " 条消课记录");

            // ③ 一次性把整页数据交给 Pipeline（多次 putField 同 key 会互相覆盖，必须攒 List 一次性 put）
            page.putField("rows", data);
        }

        /** 安全取第 i 个单元格文本，越界返回空串；顺手剥掉 HTML 标签/实体 */
        private String cell(List<Selectable> cells, int i) {
            if (i < cells.size()) {
                String html = cells.get(i).get();
                return html.replaceAll("<[^>]*>", " ")
                           .replaceAll("&[a-zA-Z]+;", " ")
                           .replaceAll("\\s+", " ")
                           .trim();
            }
            return "";
        }

        @Override
        public Site getSite() {
            return Site.me().setRetryTimes(1).setSleepTime(500);
        }
    }
}
