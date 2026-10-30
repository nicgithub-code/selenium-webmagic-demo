package com.demo.crawler.realcase;

import com.demo.crawler.common.SeleniumFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实战方案二：浏览器法（Selenium 真实打开网站，从 DOM 里抠数据）。
 *
 * 这个方案"慢但通用"——它不关心后端有没有接口、接口长什么样，
 * 只要人能在网页上看到的数据，它都能抓。这正是第 2、3 课学的本事在真实站点上的应用。
 *
 * 流程和我们第 2 课几乎一模一样：
 *   打开登录页 → 填账号密码 → 点登录 → 等跳转 → 打开学员列表 → 读表格 → 点"下一页"翻页 → 写 CSV
 *
 * 与方案一对比：
 *   - 方案一拿的是"数据接口"，干净、快、可并发；
 *   - 本方案拿的是"人眼看到的结果"，慢、单线程，但能应对没有接口/接口加密/反爬严重的场景。
 *   真实项目里，能接口法就接口法，接口法不行再上 Selenium（就像教材第 5 课反复强调的）。
 */
public class SeleniumStudentCrawler {

    /** 站点地址（和方案一一致） */
    private static final String BASE_URL = "http://localhost:5173";

    /** CSV 表头。注意：下表列顺序 = 网页里 el-table 列的顺序，索引要严格对上 */
    private static final String[] HEADERS = {"姓名", "性别", "家长", "联系电话", "出生日期", "入学时间", "主教老师", "备注"};

    /** 运行入口。默认弹出浏览器窗口（headless=false），方便你看它怎么干活；
     *  想后台跑加 JVM 参数 -Dcrawler.headless=true */
    public static void main(String[] args) throws Exception {
        boolean headless = "true".equalsIgnoreCase(System.getProperty("crawler.headless", "false"));
        new SeleniumStudentCrawler().crawlBySelenium(headless, "output/realcase-students-selenium.csv");
    }

    /** 纯 Selenium 方案：只用 Selenium 打开浏览器、翻页抠表格，不用 WebMagic */
    public void crawlBySelenium(boolean headless, String outFile) throws Exception {
        // 创建一个真实 Edge 浏览器（复用第 2 课那个工厂，已处理好驱动路径）
        WebDriver driver = SeleniumFactory.createEdgeDriver(headless);
        try {
            // ===== 1) 用 Selenium 打开登录页 =====
            driver.get(BASE_URL + "/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // 等登录框出现。Element Plus 的输入框渲染出来是一个 <input placeholder="请输入账号">
            // 我们用"按 placeholder 属性定位"最稳，因为 class 名（el-input__inner）也可能变
            WebElement userInput = wait.until(ExpectedConditions
                    .presenceOfElementLocated(By.cssSelector("input[placeholder='请输入账号']")));
            WebElement pwdInput = driver.findElement(By.cssSelector("input[placeholder='请输入密码']"));

            // ===== 2) 填账号密码并提交 =====
            userInput.sendKeys("admin");
            pwdInput.sendKeys("123456");
            // 登录按钮：LoginView.vue 里给它加了 class="login-btn"
            driver.findElement(By.cssSelector("button.login-btn")).click();

            // ===== 3) 等登录成功跳转。登录后路由会 push 到 /dashboard =====
            wait.until(ExpectedConditions.urlContains("/dashboard"));
            System.out.println("登录成功，已跳转到：" + driver.getCurrentUrl());

            // ===== 4) 直接打开学员列表页 =====
            // 之所以能直接打开，是因为登录时 token 已存进浏览器 localStorage，
            // 前端路由守卫读到 token 就会放行。等价于"已登录状态下点菜单进来的"。
            driver.get(BASE_URL + "/student/list");

            // ===== 5) 循环翻页读取表格 =====
            List<Map<String, String>> rows = new ArrayList<>();
            String prevFirstName = null; // 用来判断"翻页后内容确实变了"

            while (true) {
                // 等表格行出现（至少 1 行）。.el-table__row 是 Element Plus 表格体的行 class
                wait.until(ExpectedConditions
                        .presenceOfElementLocated(By.cssSelector(".el-table__row")));

                // 重新查一遍当前页所有行（上一页的 WebElement 翻页后会失效，必须重查）
                List<WebElement> trs = driver.findElements(By.cssSelector(".el-table__row"));
                for (WebElement tr : trs) {
                    rows.add(parseRow(tr));
                }
                System.out.println("  本页读取 " + trs.size() + " 行，累计 " + rows.size());

                // 记录当前页"第一条的姓名"，用于翻页后判断内容是否更新
                String firstCell = firstCellText(trs);
                if (prevFirstName == null) {
                    prevFirstName = firstCell;
                }

                // 找"下一页"按钮：el-pagination 的下一页按钮 class 是 .btn-next
                WebElement nextBtn = driver.findElement(By.cssSelector(".el-pagination .btn-next"));
                // 到了最后一页，按钮会带上 disabled 属性（HTML 原生禁用），不能再点。
                // 注意：别用 class.contains("is-disabled") 判断 —— Element Plus 不同版本
                //   末页按钮的类名可能是 is-last / aria-disabled，未必有 is-disabled；
                //   而 isEnabled() 读的是 disabled 属性（property），跨版本稳定。
                if (!nextBtn.isEnabled()) {
                    System.out.println("  已是最后一页（btn-next 已禁用），停止翻页。");
                    break;
                }

                // ===== 6) 点下一页，并等新一页数据出现 =====
                nextBtn.click();
                String finalPrev = prevFirstName;
                wait.until(d -> {
                    List<WebElement> newTrs = d.findElements(By.cssSelector(".el-table__row"));
                    return !newTrs.isEmpty() && !firstCellText(newTrs).equals(finalPrev);
                });
                // 更新基准，进入下一轮循环
                List<WebElement> newTrs = driver.findElements(By.cssSelector(".el-table__row"));
                prevFirstName = firstCellText(newTrs);
            }

            // ===== 7) 写 CSV =====
            StudentCsvWriter.write(outFile, HEADERS, rows);
        } finally {
            driver.quit(); // 一定关浏览器，否则进程残留占资源
        }
    }

    /**
     * 解析一行 <tr> 成 CSV 的一行 Map。
     * el-table 每一行的结构是：<td>...<div class="cell">内容</div>...</td>
     * 我们取每个 td 里的 .cell 文本，再按列索引挑出想要的字段。
     *
     * 网页列顺序（来自 StudentList.vue 的 <el-table-column> 排列）：
     *   0 序号 | 1 头像 | 2 姓名 | 3 性别 | 4 家长 | 5 联系电话 | 6 意向(红点) | 7 入学时间 | 8 主教老师 | 9 备注 | 10 操作
     * 所以我们要的是索引：姓名=2, 性别=3, 家长=4, 电话=5, 入学时间=7, 主教老师=8, 备注=9
     */
    private Map<String, String> parseRow(WebElement tr) {
        List<WebElement> cells = tr.findElements(By.cssSelector("td .cell"));
        Map<String, String> row = new LinkedHashMap<>();
        // 防御：万一某行列数不对（比如网络异常少了列），直接给空，避免数组越界
        row.put("姓名", cell(cells, 2));
        row.put("性别", cell(cells, 3));
        row.put("家长", cell(cells, 4));
        row.put("联系电话", cell(cells, 5));
        row.put("出生日期", "");   // 列表页不展示出生日期，留空（接口法才能拿到）
        row.put("入学时间", cell(cells, 7));
        row.put("主教老师", cell(cells, 8));
        row.put("备注", cell(cells, 9));
        return row;
    }

    /** 安全取第 i 个 cell 的文本，越界返回空串 */
    private String cell(List<WebElement> cells, int i) {
        if (i < cells.size()) {
            return cells.get(i).getText().trim();
        }
        return "";
    }

    /** 取表格第一行的"姓名"单元格文本（索引 2），用于翻页判重 */
    private String firstCellText(List<WebElement> trs) {
        if (trs.isEmpty()) {
            return "";
        }
        List<WebElement> cells = trs.get(0).findElements(By.cssSelector("td .cell"));
        return cell(cells, 2);
    }
}
