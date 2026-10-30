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
 * 【WebMagic + Selenium 拼接】的"解析器"：StudentListPageProcessor。
 *
 * 它和 lesson1 的 BookListPageProcessor 是【同一套写法】——
 * 用 WebMagic 的 CSS 选择器把 HTML 切成一条条记录。
 * 区别只在于：lesson1 的 HTML 是 HTTP 下载来的，本类的 HTML 是
 * StudentSeleniumDownloader 用浏览器渲染后"塞"进来的。
 *
 * 这正说明"解耦"的价值：解析器完全不知道数据来自 HTTP 还是浏览器，
 * 它只关心"拿到手的那段 HTML 长什么样"。
 */
public class StudentListPageProcessor implements PageProcessor {

    /** CSV 表头（顺序即写入顺序）。和 WebMagicSeleniumStudentCrawler 里传给 Pipeline 的必须一致 */
    public static final String[] HEADERS = {"姓名", "性别", "家长", "联系电话", "出生日期", "入学时间", "主教老师", "备注"};

    /**
     * WebMagic 每下载完一个页面就回调本方法。这里它拿到的 page，
     * 就是 StudentSeleniumDownloader 构造的那个"包含全部学员行"的合成 Page。
     */
    @Override
    public void process(Page page) {
        // 用 CSS 选择器把合成 HTML 里的每一行切成 Selectable 节点
        // （和 lesson1 的 css("div.book-card").nodes() 一模一样，只是标签名不同）
        List<Selectable> rows = page.getHtml().css("tr.el-table__row").nodes();

        List<Map<String, String>> data = new ArrayList<>();
        for (Selectable row : rows) {
            // 一行里有多个单元格，每个单元格里的 .cell 才是可见文本
            List<Selectable> cells = row.css("td .cell").nodes();
            Map<String, String> m = new LinkedHashMap<>();
            m.put("姓名", cell(cells, 2));
            m.put("性别", cell(cells, 3));
            m.put("家长", cell(cells, 4));
            m.put("联系电话", cell(cells, 5));
            m.put("出生日期", "");   // 列表页不展示出生日期，留空（接口法才能拿到）
            m.put("入学时间", cell(cells, 7));
            m.put("主教老师", cell(cells, 8));
            m.put("备注", cell(cells, 9));
            data.add(m);
        }
        System.out.println("[WebMagic解析] 共提取 " + data.size() + " 条学员记录");

        // 一次性把整页数据交给 Pipeline 落地。
        // 注意：必须用 "rows" 这个固定 key 一次性 put，否则多次 putField 同 key 会互相覆盖
        page.putField("rows", data);
    }

    /** 安全取第 i 个单元格文本，越界返回空串 */
    private String cell(List<Selectable> cells, int i) {
        if (i < cells.size()) {
            return cells.get(i).get().trim();
        }
        return "";
    }

    /** WebMagic 要求提供 Site 配置（重试、间隔等），这里用默认即可 */
    @Override
    public Site getSite() {
        return Site.me().setRetryTimes(1).setSleepTime(500);
    }
}
