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
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.Downloader;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================
 *  练习作业：用【WebMagic + Selenium 拼接】爬取「课程安排 - 列表视图」的排课数据
 * ============================================================
 *
 * 本文件是【单文件练习骨架】：外层负责编排，两个嵌套内部类分别扮演 Downloader / PageProcessor。
 * 所有 // TODO 处由你自己写代码，完成后直接 Run 本类的 main。
 *
 * ------------------------------------------------------------------
 * 【已替你做完的 F12 / 源码侦察结果 —— 照着写，不用再猜】
 * ------------------------------------------------------------------
 * 1) 页面地址：http://localhost:5173/schedule/calendar
 *
 * 2) ⚠️ 最大的坑：默认进来是【月视图】，而月视图里【没有表格、也没有分页】，只有日历格子！
 *    带分页的表格在【列表视图】里。所以流程里必须多一步：
 *        先点击 "列表视图" 这个单选按钮 → 等表格出现 → 再开始翻页
 *    该按钮的 DOM（Element Plus 渲染结果）大致是：
 *        <label class="el-radio-button">
 *          <input type="radio" value="list" class="el-radio-button__original-radio">   ← 隐藏的，不能直接点
 *          <span class="el-radio-button__inner">列表视图</span>                          ← 点这个（可见文字）
 *        </label>
 *    推荐用 XPath 按文字定位（比 CSS 省心）：
 *        By.xpath("//span[contains(@class,'el-radio-button__inner') and normalize-space()='列表视图']")
 *    提示：也可用 JS 兜底 ((JavascriptExecutor) driver).executeScript("arguments[0].click()", el)
 *
 * 3) 列表视图的表格仍是 Element Plus 的 <el-table stripe>：
 *      每行        tr.el-table__row
 *      单元格文本  td .cell
 *
 * 4) 表格列顺序（CalendarSchedule.vue 第 158-181 行）：
 *      0 序号 | 1 时间 | 2 学员 | 3 课程 | 4 级别 | 5 老师 | 6 教室 | 7 课时 | 8 状态 | 9 操作
 *    建议抓取：时间=1, 学员=2, 课程=3, 级别=4, 老师=5, 教室=6, 课时=7, 状态=8
 *
 * 5) ✅ 有分页（正是你想练的）：el-pagination，默认每页 10 条，下一页按钮 .btn-next
 *    ⚠️ 两个必须注意的点：
 *      a) 末页判定请用 isEnabled() 读 disabled 属性，【不要】用 class.contains("is-disabled")
 *         （教师/学员页已踩过坑：Element Plus 末页按钮实际是 class="btn-next is-last" + disabled 属性）
 *      b) 本页分页是【纯前端分页】：pagedSchedules = schedules.slice(...) 直接在内存里切，
 *         点"下一页"【不会发任何网络请求】，也没有 loading 状态。
 *         所以只能用我们学过的"等首行文本变化"来判断翻页完成：
 *             String prev = firstCellText(rows);
 *             nextBtn.click();
 *             wait.until(d -> !firstCellText(当前行).equals(prev));
 *    ⚠️ 再提醒：翻页务必加"最大页数"保险，防止判定失灵时死循环。
 *
 * 6) 登录和其他页面完全一致，照搬 StudentSeleniumDownloader.loginBySelenium() 即可。
 *
 * ------------------------------------------------------------------
 * 【不用你写、直接复用】
 * ------------------------------------------------------------------
 *   SeleniumFactory.createEdgeDriver(headless)  造浏览器
 *   CsvPipeline                                 写 CSV（工程通用）
 *   Spider.create(...)                          WebMagic 编排骨架
 *
 * 【必须你写的（因页面而异）】
 *   登录 → 打开课程安排页 → 切列表视图 → 翻页收集 → 合成 HTML
 *   字段索引提取 + HEADERS
 */
public class ScheduleListCrawler {

    private static final String BASE_URL = "http://localhost:5173";
    /** 课程安排页（列表视图所需的入口 URL） */
    private static final String SCHEDULE_URL = BASE_URL + "/schedule/calendar";

    public static void main(String[] args) throws Exception {
        new ScheduleListCrawler().crawlSchedulesWithWebMagicPlusSelenium(
                "true".equalsIgnoreCase(System.getProperty("crawler.headless", "false")),
                "output/realcase-schedules-webmagic-selenium.csv");
    }

    /**
     * 编排：把 Selenium 引擎塞进 WebMagic 骨架。
     *
     * TODO: 照着拼（参考 WebMagicSeleniumStudentCrawler 第 42-49 行）：
     *   1) 下载引擎 = new ScheduleSeleniumDownloader(headless, BASE_URL)
     *   2) Spider.create(new ScheduleListPageProcessor())
     *   3) .setDownloader(下载引擎)                      ★关键一行：换掉默认 HTTP 下载器
     *   4) .addUrl(SCHEDULE_URL)
     *   5) .addPipeline(new CsvPipeline(outFile, ScheduleListPageProcessor.HEADERS))
     *   6) .thread(1)                                    单浏览器必须单线程
     *   7) .run()
     *   8) finally 里 closeBrowser()
     */
    public void crawlSchedulesWithWebMagicPlusSelenium(boolean headless, String outFile) throws Exception {
        // TODO: 实现上面的编排
        throw new UnsupportedOperationException("请实现 crawlSchedulesWithWebMagicPlusSelenium()");
    }

    // ==================================================================
    //  嵌套内部类 ①：下载引擎（Selenium）—— 职责："怎么把页面跑起来"
    // ==================================================================
    public static class ScheduleSeleniumDownloader implements Downloader {

        private final WebDriver driver;
        private final String baseUrl;
        private final WebDriverWait wait;
        private boolean loggedIn = false;

        /** 保险：最多翻多少页，防止末页判定失灵导致死循环 */
        private static final int MAX_PAGES = 50;

        public ScheduleSeleniumDownloader(boolean headless, String baseUrl) {
            // TODO: 初始化
            //   this.baseUrl = baseUrl;
            //   this.driver = SeleniumFactory.createEdgeDriver(headless);
            //   this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            throw new UnsupportedOperationException("请实现构造方法");
        }

        /**
         * WebMagic 回调这里拿一个 URL 的 HTML。我们要做的：
         *  ① 首次调用 → loginBySelenium() 登录（同一站点，登录逻辑照搬学员版）
         *  ② driver.get(request.getUrl()) 打开 /schedule/calendar
         *  ③ 【多出来的一步】点击"列表视图"单选按钮，否则页面只有日历、没有表格
         *  ④ wait.until(presenceOfElementLocated(tr.el-table__row)) 等表格渲染
         *  ⑤ 循环翻页：
         *        记录当前首行文本 prev
         *        收集本页所有行的 outerHTML
         *        找 .el-pagination .btn-next；if (!nextBtn.isEnabled()) break;
         *        nextBtn.click(); wait.until(首行文本 != prev);
         *     循环别忘 incr page 计数 + MAX_PAGES 保险
         *  ⑥ 把所有行拼成 "<table><tbody>...</tbody></table>"
         *  ⑦ Page page = new Page(); page.setRequest(request); page.setRawText(合成HTML); return page;
         */
        @Override
        public Page download(Request request, Task task) {
            // TODO: 实现 download()（登录 → 切列表视图 → 翻页收集 → 合成 HTML）
            throw new UnsupportedOperationException("请实现 download()");
        }

        /** 登录（与学员版一致） */
        private void loginBySelenium() {
            // TODO: 照搬 StudentSeleniumDownloader.loginBySelenium()
            //   /login → 填 input[placeholder='请输入账号'] / '请输入密码' → 点 button.login-btn
            //   → wait.until(ExpectedConditions.urlContains("/dashboard"))
            throw new UnsupportedOperationException("请实现 loginBySelenium()");
        }

        /** 点击"列表视图"（默认月视图里没有表格，这步是本题的关键差异） */
        private void switchToListMode() {
            // TODO:
            //   WebElement listLabel = wait.until(ExpectedConditions.elementToBeClickable(
            //        By.xpath("//span[contains(@class,'el-radio-button__inner') and normalize-space()='列表视图']")));
            //   listLabel.click();
            //   // 点完等表格出现，证明视图已切换成功
            //   wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("tr.el-table__row")));
            throw new UnsupportedOperationException("请实现 switchToListMode()");
        }

        /** 取当前页第一行某列的文本，作为"页面是否真的翻了"的判断依据 */
        private String firstRowSignature(List<WebElement> trs) {
            // TODO: 若 trs 为空返回 ""；否则取第一行 tr.findElements("td .cell") 的第 0/1 个单元格文本
            //   （建议用第 2 列"学员"或第 1 列"时间"，只要能区分不同页即可）
            throw new UnsupportedOperationException("请实现 firstRowSignature()");
        }

        /** 单浏览器不支持并发（故意留空，等价于强制单线程） */
        @Override
        public void setThread(int threadNum) {
        }

        public void closeBrowser() {
            driver.quit();
        }
    }

    // ==================================================================
    //  嵌套内部类 ②：解析器（WebMagic）—— 职责："从 HTML 里挑哪些字段"
    // ==================================================================
    public static class ScheduleListPageProcessor implements PageProcessor {

        /** TODO: CSV 表头（顺序要和 process() 里 Map 的 key 一致） */
        public static final String[] HEADERS = {"时间", "学员", "课程", "级别", "老师", "教室", "课时", "状态"};

        @Override
        public void process(Page page) {
            // TODO 1：List<Selectable> rows = page.getHtml().css("tr.el-table__row").nodes();
            // TODO 2：每行 → List<Selectable> cells = row.css("td .cell").nodes();
            // TODO 3：按侦察好的列索引取值（务必越界保护）：
            //          时间=cells[1]  学员=cells[2]  课程=cells[3]  级别=cells[4]
            //          老师=cells[5]  教室=cells[6]  课时=cells[7]  状态=cells[8]
            // TODO 4：收集成 List<Map<String,String>>，最后一次性 page.putField("rows", data);
            //         ⚠️ 必须用 "rows" 这个 key 一次性 put；多次 put 同 key 会互相覆盖
            throw new UnsupportedOperationException("请实现 process()：提取排课字段");
        }

        /** 提示：照抄 StudentListPageProcessor.cell() 的越界保护写法即可 */
        private String cell(List<Selectable> cells, int i) {
            if (i < cells.size()) {
                return cells.get(i).get().trim();
            }
            return "";
        }

        @Override
        public Site getSite() {
            return Site.me().setRetryTimes(1).setSleepTime(500);
        }
    }
}
