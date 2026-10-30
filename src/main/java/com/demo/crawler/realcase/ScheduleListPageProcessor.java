package com.demo.crawler.realcase;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【WebMagic + Selenium 拼接】的"解析器"：ScheduleListPageProcessor（课程安排-月视图列表）。
 *
 * 它拿到手的是 ScheduleSeleniumDownloader 拼好的"合成 HTML"（一段包着所有 <tr> 的 table），
 * 任务只有一个：用 WebMagic 的 CSS 选择器把这一坨 HTML 切成一条条课程记录。
 *
 * 和 StudentListPageProcessor 完全同构 —— 解析器不关心 HTML 是 HTTP 下下来的还是浏览器渲染的。
 *
 * ────────────────────────────────────────────────────────────
 * 【动手前必做：先把"列 → 索引"的对应关系查出来】
 * 月视图下方的列表列数和学员页肯定不一样，别猜，去 Console 一行命令打印出来：
 *
 *   Array.from(document.querySelectorAll('.el-table__row')[0]
 *              .querySelectorAll('td .cell')).map((e,i)=>i+' : '+e.innerText)
 *
 * 输出形如：
 *   0 : 2026-10-29
 *   1 : 10:00-11:00
 *   2 : 钢琴
 *   3 : 张老师
 *   4 : 待上课
 *   5 : 编辑 删除
 *
 * 然后你要做的事：
 *   ① 把要的字段抄进下面的 HEADERS（顺序 = CSV 列顺序）
 *   ② 按上面打印出来的数字填 process() 里的 cell(cells, 数字)
 *   ③ 别漏掉"隐藏列"：Element Plus 如果开了多选列/展开列，索引会整体位移，
 *      一切以 Console 打印的为准，不要凭肉眼数页面上的可见列。
 * ────────────────────────────────────────────────────────────
 */
public class ScheduleListPageProcessor implements PageProcessor {

    /**
     * CSV 表头（顺序即写入顺序）。
     * 【必改】按你 Console 查到的列，改成这一页真实有的字段。
     * 注意：必须和 WebMagicSeleniumScheduleCrawler 里传给 CsvPipeline 的 headers 一致。
     */
    public static final String[] HEADERS = {
            "序号",        // 累计行号（处理器生成，非页面字段）
            "课程",        // courseName
            "学生姓名",    // studentName
            "老师",        // teacherName
            "教室",        // roomName
            "课程级别",    // levelName
            "开始时间",    // startTime
            "结束时间",    // endTime
            "状态",        // status
            "课时"         // lessons
    };

    /**
     * WebMagic 每下载完一个页面就回调本方法。
     * 这里拿到的 page，就是 ScheduleSeleniumDownloader 塞进来的"全部页拼起来"的合成 Page。
     *
     * 三步走：
     *   ① 选中所有数据行
     *   ② 每行取单元格文本，按索引组装成一条 Map
     *   ③ 一次性 putField("rows", 整个 List) 交给 Pipeline
     */
    @Override
    public void process(Page page) {
        // ① 把合成 HTML 里的每一行切成 Selectable 节点
        //    Selectable 可以粗浅理解为"一小段还能继续用选择器往下挖的 HTML"
        List<Selectable> rows = page.getHtml().css("tr.el-table__row").nodes();
        // TODO(可选核对)：如果下载器那边的行选择器加了容器限定（如 .schedule-list），这里不用改，
        //        因为合成 HTML 里只剩 <tr> 了；但如果抠回来带了别的结构，这里要同步调整。

        List<Map<String, String>> data = new ArrayList<>();
        int seq = 1;   // 跨页累计序号：从 1 开始，每写一行 +1；避免翻页后单页行号重复
        for (Selectable row : rows) {
            // ② 一行里所有单元格：注意双层结构 —— <td><div class="cell">文本</div></td>
            //    所以选择器是 "td .cell"（空格 = 后代选择器），直接 css("td") 拿到的是含标签的字符串。
            List<Selectable> cells = row.css("td .cell").nodes();

            Map<String, String> m = new LinkedHashMap<>();
            // ┌─────────────────────────────────────────────────────────────┐
            // │ 实测列顺序（来自跑出来的 CSV 第一行的真实 DOM 反推）：        │
            // │   cell(0) 页面单页行号(已弃用，改用累计序号)                 │
            // │   cell(1) 时间(开始~结束合并)   cell(2) 学生姓名             │
            // │   cell(3) 课程   cell(4) 课程级别(如 4-6级)  cell(5) 老师   │
            // │   cell(6) 教室   cell(7) 课时(如 1)     cell(8) 状态        │
            // │   cell(9) 操作(复制按钮——无对应字段，忽略)                   │
            // │ 注意：开始/结束时间被合并在 cell(1)、用 "~" 分隔；           │
            // │       "序号"由本处理器按累计行号生成，不取自页面。            │
            // └─────────────────────────────────────────────────────────────┘
            m.put("序号", String.valueOf(seq++));   // 累计序号，第一列
            m.put("课程",     cell(cells, 3));
            m.put("学生姓名", cell(cells, 2));
            m.put("老师",     cell(cells, 5));
            m.put("教室",     cell(cells, 6));
            // 开始/结束时间合并在 cell(1)，按 "~" 拆开
            String[] timeRange = cell(cells, 1).split("~");
            m.put("开始时间", timeRange.length > 0 ? timeRange[0].trim() : "");
            m.put("结束时间", timeRange.length > 1 ? timeRange[1].trim() : "");
            m.put("状态",     cell(cells, 8));   // 页面是中文，接口是数字
            m.put("课时",     cell(cells, 7));
            m.put("课程级别", cell(cells, 4));

            data.add(m);
        }

        System.out.println("[WebMagic解析] 共提取 " + data.size() + " 条课程记录");

        // ③ 一次性把整页数据交给 Pipeline 落地。
        //    坑点：WebMagic 的 putField 以 key 存 Map，同一个 key 多次 put 会【互相覆盖】，
        //    所以必须攒成 List 后一次性 put，不能一条一条 put。
        page.putField("rows", data);
    }

    /**
     * 安全取第 i 个单元格文本，越界返回空串。
     * 为什么要有这个：不同月份/不同筛选条件下，某些列可能不渲染，
     * 直接 cells.get(i).get() 会抛 IndexOutOfBounds 让整只爬虫挂掉。
     */
    private String cell(List<Selectable> cells, int i) {
        if (i < cells.size()) {
            // WebMagic 的 get() 返回的是该单元格的 outerHTML（带 <div class="cell"> 等标签），
            // 不是文字。这里用正则把所有标签/注释(<!---->)和 HTML 实体去掉，再合并空白。
            String html = cells.get(i).get();
            return html.replaceAll("<[^>]*>", " ")
                       .replaceAll("&[a-zA-Z]+;", " ")
                       .replaceAll("\\s+", " ")
                       .trim();
        }
        return "";
    }

    /** WebMagic 要求提供 Site 配置（重试、间隔等），照抄学员版即可 */
    @Override
    public Site getSite() {
        return Site.me().setRetryTimes(1).setSleepTime(500);
    }

    /** 占位：未完成处抛错，运行时一眼看出还没实现 */
    private static UnsupportedOperationException todo(String msg) {
        return new UnsupportedOperationException("TODO(你来实现): " + msg);
    }
}
