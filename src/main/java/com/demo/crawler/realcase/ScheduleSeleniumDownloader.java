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
 * 【WebMagic + Selenium 拼接】的"下载引擎"：ScheduleSeleniumDownloader（课程安排-月视图下方的列表）。
 *
 * 定位：和 StudentSeleniumDownloader 是【同一个套路】，只是换了个页面。
 * WebMagic 眼里它只是个下载器；真正的活（登录、切视图、翻页、抠 DOM）全在 Selenium 这边。
 *
 * ────────────────────────────────────────────────────────────
 * 【已侦察到的页面事实】（F12 确认过，写代码的依据）
 *   · 该页有两个视图：【月视图】（日历，默认选中）和【列表视图】（带分页的表格）。
 *     我们要的带分页的表在【列表视图】下 —— 所以进来后【必须先点一下"列表视图"】再抠数据。
 *   · 视图切换不是 Tab，是 Element Plus 的 el-radio-button 按钮组，形如：
 *       <label class="el-radio-button is-active">          ← 选中态体现在这里
 *         <input class="el-radio-button__original-radio" value="month">  ← value 是机器标识
 *         <span class="el-radio-button__inner">月视图</span>
 *       </label>
 *     默认选中项 value="month"；我们要点的那个 value="list"、文字"列表视图"。
 *
 * 【写代码之前的 F12 侦察清单】——换任何新页面都按这个顺序走
 *   1) 路由：点左侧菜单进课程安排，看地址栏得到路径（如 /course/schedule）→ 填到起始 URL
 *   2) 视图切换：确认按钮组的父容器是不是 .el-radio-group（见 SEL_VIEW_OPTION 的注释说明）；
 *        写之前先用 Console 数一下：
 *            document.querySelectorAll('.el-radio-group .el-radio-button').length   // 期待 = 视图个数
 *            document.querySelectorAll('.gap-3 .el-radio-button').length            // 对比，别误伤别处
 *   3) 列表容器：切到列表视图后，确认列表是 el-table
 *        F12 Console 验证（数量必须等于页面看到的条数）：
 *            document.querySelectorAll('.el-table__row').length
 *      ⚠️ 如果页面上有【多个 el-table】（比如上面还有个统计表），上面这句会偏多，
 *         那就得给列表加限定容器，例如 '.schedule-list .el-table__row'
 *   4) 分页器：确认它是 Element Plus 的 .el-pagination 还是自定义按钮。
 *        是否有总条数文案（"共 X 条"）？有就能用来自我校验抓到的行数
 *   5) 结束标志：翻到末页时"下一页"按钮长什么样？
 *        务必看 DOM 属性：<button class="btn-next" disabled>  → 用 isEnabled() 判断
 *        （血泪教训：不要赌 is-disabled 类名，见 realcase/README.md）
 *
 * 【本页特有的坑，提前想清楚】
 *   A) 顺序坑：必须先点"列表视图"，再找 .el-table__row。
 *      月视图里也有 <table>（日历格子），直接抠会把日历的行当数据抓进来。
 *   B) 异步坑：点完视图切换，数据是异步请求回来的，必须 wait 到
 *      「表格行出现 + 分页条出现」再开始收集，否则拿到的是空表或旧数据。
 *   C) 时间范围坑：列表视图一般默认只显示【某个月/某个区间】的课。
 *      要抓跨月数据，就得在翻页循环外再套一层"改筛选条件 → 重新翻页"的循环（第 ④ 步外预留）。
 *   D) 空档期：某些月份可能一条都没有，列表会显示"暂无数据"，
 *      循环里要能容忍 trs.size()==0 而不崩。
 * ────────────────────────────────────────────────────────────
 */
public class ScheduleSeleniumDownloader implements Downloader {

    /** Selenium 真浏览器（一个下载器 = 一个浏览器，必须单线程，见 setThread） */
    private final WebDriver driver;
    /** 站点根地址 */
    private final String baseUrl;
    /** 显式等待器（最多等 15 秒，SPA 首屏慢的时候可以调大） */
    private final WebDriverWait wait;
    /** 是否已登录（登录只需做一次，之后浏览器带着登录态复用） */
    private boolean loggedIn = false;

    // ==================== 【必改】选择器集中放这里，改的时候不用翻代码 ====================
    // 下面这些都只是"按 Element Plus 惯例猜的"，请拿 F12 核对后再跑。
    /** 数据行的重复单元 */
    private static final String SEL_ROW = ".el-table__row";                       // TODO(核对)：若页面有多个表格，改成 ".某个容器 .el-table__row"
    /** 分页容器（用来确认分页条已渲染） */
    private static final String SEL_PAGINATION = ".el-pagination";                // TODO(核对)
    /** "下一页"按钮 */
    private static final String SEL_NEXT_BTN = ".el-pagination .btn-next";        // TODO(核对)：自定义分页的话改成对应按钮
    /**
     * 视图切换：按钮组里的每个选项（外层 label，才是可点击的元素）。
     * 为什么用 .el-radio-group 而不是截图里那个 .gap-3？
     *   - .el-radio-group 是 Element Plus 组件的根类名，版本不变它就在，稳；
     *   - .gap-3 只是 Tailwind 的间距工具类（布局噪音），改天换成 gap-2 选择器就失效；
     *     而且它是通用类，页面别处（筛选栏等）也可能有，容易把不相干的按钮混进来。
     *   若 Console 验证 .el-radio-group 数量为 0（说明父容器不是组件根），
     *   再退而用 ".gap-3 > .el-radio-button"（加 > 限定直接子元素，范围更窄）。
     */
    private static final String SEL_VIEW_OPTION = ".el-radio-group .el-radio-button";
    /** 视图切换：每个选项里隐藏的真 radio，它的 value 是机器标识（list / month），不随文字变 */
    private static final String SEL_VIEW_INPUT = "input.el-radio-button__original-radio";
    /** 目标视图的 value：默认进来选中的是 month（月视图），我们要的是 list（列表视图） */
    private static final String TARGET_VIEW_VALUE = "list";
    /** 目标视图的可见文字（value 万一查不到，就用它兜底匹配） */
    private static final String TARGET_VIEW_TEXT = "列表视图";
    // =====================================================================================

    public ScheduleSeleniumDownloader(boolean headless, String baseUrl) {
        this.baseUrl = baseUrl;
        this.driver = SeleniumFactory.createEdgeDriver(headless);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /**
     * WebMagic 每要抓一个 URL 就回调一次本方法。
     * 我们在这里用真浏览器把页面"跑起来"，再把渲染后的 HTML 交回去。
     *
     * 整体剧本（对应下面的 ①②③④⑤）：
     *   ① 首次才登录 → ② 打开课程安排页 → ③ 从默认的"月视图"切到"列表视图"
     *   → ④ 循环翻页收集所有行 → ⑤ 拼合成 HTML 交给 ScheduleListPageProcessor 解析
     */
    @Override
    public Page download(Request request, Task task) {
        // ① 登录幂等：只有第一次进来才登录。
        //    为什么可以复用？因为整个类共用同一个 WebDriver，Cookie/localStorage 一直在。
        ensureLoggedIn();

        // ② 打开目标页（课程安排）。由 Spider 的 addUrl 传入。
        driver.get(request.getUrl());
        System.out.println("[Selenium引擎] 已打开课程安排页");

        // ③ 切到"列表视图"：这是本页比学员页【多出来的一步】。
        //    页面默认停在"月视图"（日历），而带分页的那张表在"列表视图"下，
        //    不切就永远抠不到数据（月视图里是日历格子，没有 .el-table__row）。
        switchToListView();

        // ④ 循环翻页，把每一行 <tr> 的 outerHTML 收集起来（跨月需求见类注释 A））
        List<String> rowHtmls = collectRowsFromAllPages();

        // ⑤ 把收集到的所有行拼成"合成 HTML"，塞进 WebMagic 的 Page。
        //    为什么包一层 <table><tbody>？因为原始页面是 el-table 由 JS 渲染出来的，
        //    我们手里只有零散的 <tr>，包回 table 结构才能让 WebMagic 的 CSS 选择器正常切。
        Page page = new Page();
        page.setRequest(request);
        page.setRawText(buildTableHtml(rowHtmls));
        return page;
    }

    // =================================================================================
    // 下面是分解后的小步骤，按编号实现即可。
    // 每一步都写清楚了：做什么 / 为什么 / 用哪个 API / 怎么验证。
    // 遇到想不通的，直接对照已跑通的 StudentSeleniumDownloader.java 同编号处（下面给了行号）。
    // =================================================================================

    /**
     * 步骤 ①：登录（只需一次）。
     *
     * 做什么：打开 /login → 等账号框出现 → 填 admin/123456 → 点 button.login-btn → 等 URL 出现 /dashboard
     * 为什么：SPA 的表单是 JS 渲染的，driver.get 之后 DOM 还没生成，必须 wait.until(...)，
     *         不然第一行 findElement 就 NoSuchElementException。
     * 用哪个 API：wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(...)))
     *             WebElement.sendKeys(...) / click()
     *             wait.until(ExpectedConditions.urlContains("/dashboard"))  ← 用 URL 变化确认登录成功
     * 验证：跑完看到"[Selenium引擎] 登录成功"，且地址栏已是 dashboard。
     *
     * 可直接参考：StudentSeleniumDownloader.java:117-128（loginBySelenium，逻辑完全一致，建议照抄）
     */
    private void ensureLoggedIn() {
        if (loggedIn) {
            return; // 已登录过，浏览器带着登录态，直接复用
        }
        // TODO(你来实现)：打开 baseUrl + "/login" 并走一遍表单登录
        driver.get(baseUrl+"/login");
        //   提示1：先 wait 账号框 By.cssSelector("input[placeholder='请输入账号']")
        WebElement user = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[placeholder='请输入账号']")));
        WebElement pwd = driver.findElement(By.cssSelector("input[placeholder='请输入密码']"));
        user.sendKeys("admin");
        pwd.sendKeys("123456");
        driver.findElement(By.cssSelector("button.login-btn")).click();
        //   提示2：登录后务必 wait.until(ExpectedConditions.urlContains("/dashboard")) 再返回
        wait.until(ExpectedConditions.urlContains("/dashboard"));
        //   提示3：最后 System.out.println("[Selenium引擎] 登录成功");
        System.out.println("[Selenium引擎] 登录成功");
        //throw todo("步骤①：用 Selenium 登录");
        // 实现完再去掉上面这行 throw；并把下面这行打开：
        loggedIn = true;
    }

    /**
     * 步骤 ③：从默认的"月视图"切到"列表视图"（本页特有的关键一步）。
     *
     * 为什么必须做：页面进来默认停在【月视图】（一个日历格子，里面没有 .el-table__row），
     *   而带分页的那张课程表在【列表视图】下面。不切这一下，后面第 ④ 步一行都抠不到。
     *
     * 做什么（模拟人手点击）：
     *   a) 遍历视图按钮组，找到 value="list"（或文字含"列表视图"）的那个选项
     *   b) 如果它还没选中，就点它
     *   c) 等"列表视图"的表格和分页条都渲染出来，再返回
     *
     * 真实 DOM（不是 el-tabs，是 Element Plus 的 el-radio-button）：
     *   <label class="el-radio-button">                              ← 未选中（默认选中项带 is-active）
     *     <input class="el-radio-button__original-radio" value="list">  ← 隐藏的 radio，value 是机器标识
     *     <span class="el-radio-button__inner">列表视图</span>          ← 人看到的文字
     *   </label>
     *
     * 三条红线：
     *   1) 点外层 label（或里面的 span），【不要点 input】—— input 被隐藏，点了抛
     *      ElementNotInteractableException
     *   2) 点完必须 wait，不能立刻 findElements —— 切换是异步拉数据的，
     *      立刻查会拿到月视图的残留 DOM（0 行）或旧数据
     *   3) 等待条件要能证明"已经切到列表视图"，建议同时等两个东西：
     *      presenceOfElementLocated(SEL_ROW)        表格行出现
     *      presenceOfElementLocated(SEL_PAGINATION) 分页条出现（月视图下是没有分页条的）
     */
    private void switchToListView() {
        // TODO(你来实现)：定位"列表视图"选项 → 点击 → 等表格与分页条出现。
        //
        //   【为什么要用循环，不能直接点？】
        //     两个视图按钮长得完全一样（都是 <label class="el-radio-button">），
        //     唯一区别是内层隐藏 input 的 value（month / list）和 span 的文字。
        //     CSS 能选中 input（.el-radio-button__original-radio[value='list']），
        //     但 input 被 Element Plus 隐藏了点不了；能点的是它的父级 label，
        //     而标准 CSS 无法"根据子元素属性反查父元素"。所以只能遍历后用 Java 筛。
        //
        //   【不想写循环的话，三种一行写法任选其一（逻辑等价）】
        //     ① CSS :has()（最短，要求浏览器 Chrome/Edge 105+）
        //          By.cssSelector(".el-radio-button:has(input[value='list'])")
        //     ② XPath 按 value 定位 label（不挑浏览器版本，推荐）
        //          By.xpath("//label[contains(@class,'el-radio-button')][.//input[@value='list']]")
        //     ③ XPath 按可见文字定位 span（最贴近肉眼，点 span 也行，它是可见的）
        //          By.xpath("//span[@class='el-radio-button__inner'][normalize-space(text())='列表视图']")
        //     拿到元素后同样判断 is-active 再点，点完再 wait（见提示 5）
        //
        //   提示1（定位，按 value，用循环——最稳、不依赖 :has() 版本）：
        //     List<WebElement> opts = driver.findElements(By.cssSelector(SEL_VIEW_OPTION));
        //     for (WebElement opt : opts) {
        //         String v = opt.findElement(By.cssSelector(SEL_VIEW_INPUT)).getAttribute("value");
        //         if (TARGET_VIEW_VALUE.equals(v)) { target = opt; break; }
        //     }
        //   提示2（兜底，按可见文字）：value 查不到或为空时，改成
        //         opt.getText().contains(TARGET_VIEW_TEXT)
        //   提示3（幂等）：点到之前先看 opt.getAttribute("class") 是否已含 "is-active"，
        //         已含就别点了（月视图才是默认选中，这里通常是"还没选中"）
        //   提示4（点击对象）：target.click() 或 target.findElement(By.cssSelector(".el-radio-button__inner")).click()
        //   提示5（等待）：
        //         wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_ROW)));
        //         wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_PAGINATION)));
        //   提示6（自检）：切完在 Console 验证一下确实切过来了
        //         document.querySelectorAll('.el-table__row').length         // 应该 > 0
        //         document.querySelectorAll('.el-pagination').length         // 应该 = 1
//        List<WebElement> eles = driver.findElements(By.cssSelector(SEL_VIEW_OPTION));
//        WebElement target = null;
//        for (WebElement ele : eles) {
//            String value = ele.findElement(By.cssSelector(SEL_VIEW_INPUT)).getAttribute("value");
//            if (TARGET_VIEW_VALUE.equals(value)) {
//                target = ele;
//                break;
//            }
//        }
        //不用循环方式就用下面这种xpath的方式
        /*
            WebElement listOpt = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//label[contains(@class,'el-radio-button')][.//input[@value='list']]")));
            listOpt.click();


            或者
            WebElement listOpt = wait.until(
            ExpectedConditions.presenceOfElementLocated( By.cssSelector(".el-radio-button:has(input[value='list'])")));
            listOpt.click();


            或者
            WebElement listOpt = wait.until(ExpectedConditions.elementToBeClickable(
            By.cssSelector(".el-radio-button:has(input[value='list'])")));
            listOpt.click();
         */
        // 兜底：没找到就立刻说清原因，别让下面变成莫名的 NullPointerException

//        WebElement target = wait.until(
//                ExpectedConditions.elementToBeClickable( By.cssSelector(".el-radio-button:has(input[value='list'])")));
        WebElement target = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//label[.//input[@value='list']]")));

        // getAttribute("class") 返回的就是整串 class（如 "el-radio-button is-active"），
        // 它本来就是 String，不需要再 toString()
        String cls = target.getAttribute("class");
        boolean alreadyActive = cls != null && cls.contains("is-active");
        // 3) 没选中才点
        if (!alreadyActive) {
            target.click();                     // 点 label 本身，不要点里面的 input
            System.out.println("[Selenium引擎] 已点击切换到「列表视图」");
        } else {
            System.out.println("[Selenium引擎] 当前已在「列表视图」，无需点击");
        }

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_ROW)));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(SEL_PAGINATION)));
    }

    /**
     * 步骤 ④：循环翻页，收集所有数据行的 outerHTML。
     *
     * 做什么（每一轮循环）：
     *   4.1 等列表行出现（至少渲染出容器；该月无数据时也要能安全跳出）
     *   4.2 findElements(SEL_ROW) 拿到本页所有行，逐行 getAttribute("outerHTML") 存进 rowHtmls
     *   4.3 打印进度：本页几行、累计几行（排错全靠它）
     *   4.4 找"下一页"按钮：!nextBtn.isEnabled() → break（末页，结束）
     *   4.5 点击前记下当前页【第一行特征串】，点击后 wait 到"特征串变了"才继续
     *       ← 这是防止翻页动画/异步请求期间误读旧数据的关键，写不出来就直接抄
     *         StudentSeleniumDownloader.java:88-99
     *   4.6 更新特征串，进入下一轮
     *
     * 为什么 4.5 用 wait.until(lambda)：Selenium 会每 500ms 重跑一次这个 lambda，
     *      一旦返回 true 就跳出等待；超时（15s）会抛 TimeoutException。
     *      所以那句 lambda 的 return 是给 WebDriverWait 用的，不是给你自己用的。
     *
     * 建议：特征串用【整行文本】而不是单列（学员版只比"姓名"列，两页首行同名就会误判超时）。
     *      见本类 firstRowSignature 方法。
     *
     * 可直接参考：StudentSeleniumDownloader.java:67-100（翻页主循环）
     *
     * @return 所有页的行 HTML 列表（顺序即页面顺序）
     */
    private List<String> collectRowsFromAllPages() {
        List<String> rowHtmls = new ArrayList<>();

        while (true) {
            // 4.1 等表格行出现，并直接把全部行抓回来
            //     presenceOfAllElementsLocatedBy 返回的是 List<WebElement>，
            //     比「presenceOfElementLocated(单个) + 再 findElements」少一次查询
            List<WebElement> trs = wait.until(ExpectedConditions
                    .presenceOfAllElementsLocatedBy(By.cssSelector(SEL_ROW)));

            // 4.2 把本页每一行的 outerHTML 收起来（外面会包回 <table><tbody>）
            for (WebElement tr : trs) {
                rowHtmls.add(tr.getAttribute("outerHTML"));
            }
            System.out.println("  [Selenium引擎] 本页收集 " + trs.size()
                    + " 行，累计 " + rowHtmls.size());

            // 4.3 找"下一页"按钮；末页时 aria-disabled="true" → 结束
            WebElement nextBtn = driver.findElement(By.cssSelector(SEL_NEXT_BTN));
            if ("true".equals(nextBtn.getAttribute("aria-disabled"))) {
                break;
            }

            // 4.4 用"当前页"第一行指纹当翻页判据（顶部刚抓的 trs 就是本页，无需再查一次 DOM）
            String currentSignature = firstRowSignature(trs);

            // 4.5 点下一页，等到"第一行指纹变了"才继续，
            //     —— 防止翻页动画/异步请求期间误读旧数据
            nextBtn.click();
            wait.until(d -> {
                List<WebElement> n = d.findElements(By.cssSelector(SEL_ROW));
                // 既要"有数据行"，又要"首行和当前页不同"，才算翻页完成
                return !n.isEmpty() && !firstRowSignature(n).equals(currentSignature);
            });
            // 注：不再需要 previousSignature 外层变量，也不再需要 4.6 二次抓页更新基准
        }
        return rowHtmls;
    }

    /**
     * 工具方法：取【当前列表第一行】的特征串，用于判断"翻页后数据是否真的换了"。
     *
     * 建议实现：找到第一行 tr → 取它所有 td .cell 的文本 → 用 "|" 拼接 → trim。
     * 为什么用整行而不是单列：避免相邻两页首行某列恰好吃同名/同时间的亏。
     * 边界：若页面尚无数据（trs 为空），返回 "" —— 注意这时 wait 条件要保持 false，
     *       别让"无数据"被当成"数据已变"而提前放行。
     *
     * 可直接参考：StudentSeleniumDownloader.java:130-137（firstCellText，这里改成整行）
     *
     * @param rows 当前页面上抓到的所有行
     * @return 第一行的内容指纹
     */
    private String firstRowSignature(List<WebElement> rows) {
        // 边界：没有数据行时返回空串。注意 wait 条件里用 "!n.isEmpty() && ..." 兜底，
        //      避免出现"空数据"被误判成"数据已变化"而提前放行。
        if (rows.isEmpty()) {
            return "";
        }
        // 取第一行所有单元格（td .cell 才是可见文本），用 " | " 拼成一条指纹。
        // 用整行而不是单列：相邻两页首行某列恰好同名/同时间的概率更低，翻页判重更可靠。
        List<WebElement> cells = rows.get(0).findElements(By.cssSelector("td .cell"));
        StringBuilder sb = new StringBuilder();
        for (WebElement c : cells) {
            sb.append(c.getText().trim()).append(" | ");
        }
        return sb.toString();
    }

    /**
     * 步骤 ⑤ 的辅助：把零散的 <tr> 拼成可被 WebMagic 选择器解析的合成 HTML。
     * 这段不需要你改，直接给 WebMagic 用。
     */
    private String buildTableHtml(List<String> rowHtmls) {
        StringBuilder html = new StringBuilder("<table><tbody>");
        for (String r : rowHtmls) {
            html.append(r);
        }
        return html.append("</tbody></table>").toString();
    }

    /**
     * WebMagic 会问下载器"支持几个线程"。
     * 一个 Selenium 下载器 = 一个浏览器，多线程会抢同一个浏览器导致数据错乱，
     * 所以这里【故意留空】，强制单线程（Spider 那边也要 .thread(1)）。
     */
    @Override
    public void setThread(int threadNum) {
        // 单浏览器不支持多线程，留空即可
    }

    /** 爬虫结束后关闭浏览器，释放资源（在 main 的 finally 里调用） */
    public void closeBrowser() {
        driver.quit();
    }

    /** 占位用的小工具：所有未完成步骤都抛它，运行时一眼看出卡在第几步 */
    private static UnsupportedOperationException todo(String msg) {
        return new UnsupportedOperationException("TODO(你来实现): " + msg);
    }
}
