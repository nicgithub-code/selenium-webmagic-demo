package com.demo.crawler.lesson1;

import com.demo.crawler.common.CsvPipeline;
import com.demo.crawler.site.LocalSiteServer;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第 1 课：用 WebMagic 爬【静态页面】（数据直接写在 HTML 里）。
 *
 * 这是爬虫最基础、最常见的形态。核心就三步：
 *   1. 发请求把 HTML 拿回来（Downloader，默认就是 HTTP，不用管）
 *   2. 用"选择器"从 HTML 里挑出想要的内容（本类，PageProcessor）
 *   3. 把挑出来的内容存成文件（Pipeline，CsvPipeline）
 *
 * 本页演示两个重要能力：
 *   - 列表页：一个页面里有多条数据（多本书）
 *   - 自动翻页：从"下一页"链接里发现新 URL，让爬虫自己接着爬（第 2 页）
 */
public class BookListPageProcessor implements PageProcessor {

    /**
     * Site 是 WebMagic 的"请求配置中心"：UA、超时、重试、抓多快都在这配。
     * 对初学者：照抄下面这组配置基本够用。
     */
    private Site site = Site.me()
            .setCharset("utf-8")                                   // 网页编码，中文站一般是 utf-8
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36") // 伪装成浏览器，降低被拒概率
            .setRetryTimes(3)                                      // 请求失败重试 3 次
            .setSleepTime(500)                                     // 每抓完一个页面，歇 500 毫秒（礼貌 + 防封）
            .setTimeOut(10000);                                    // 单次请求最多等 10 秒

    @Override
    public void process(Page page) {
        // ---- 第 1 步：判断这是不是"列表页" ----
        // 我们的列表页里每本书是一个 <div class="book-card">，用它来识别页面类型。
        // .match() 表示"页面里有没有匹配到这个元素"。
        if (!page.getHtml().css("div.book-card").match()) {
            // 不是列表页（比如后面误抓到了详情页），本课先不处理，直接跳过。
            return;
        }

        // ---- 第 2 步：把每本书挑出来 ----
        // page.getHtml().css("div.book-card") 选出所有书卡；
        // .nodes() 把结果变成"一个个独立的 Selectable（可继续挑选的对象）"列表。
        List<Selectable> cards = page.getHtml().css("div.book-card").nodes();
        // 先收集本页所有行，最后一起提交（原因见 CsvPipeline 注释：同 key 会被覆盖）
        List<Map<String, String>> rows = new ArrayList<>();
        for (Selectable card : cards) {
            // 在"这一张卡"的范围内继续挑字段。
            // 写法：card.xpath("标签[@属性='值']/子标签.../text()")
            //   - xpath 是 XML/HTML 路径语言，比 CSS 更灵活（能挑文本、属性、按文本内容过滤）
            //   - text() 表示取"标签内的文字"； @href 表示取"href 属性"
            Map<String, String> row = new LinkedHashMap<>();
            row.put("书名",   card.xpath("h3[@class='title']/a/text()").get());
            row.put("作者",   card.xpath("p[@class='author']/text()").replace("^作者[:：]\\s*", "").get());
            row.put("价格",   card.xpath("p[@class='price']/text()").replace("^价格[:：]\\s*", "").get());
            row.put("评分",   card.xpath("p[@class='score']/text()").replace("^评分[:：]\\s*", "").get());
            // 标签可能不止一个，.all() 一次取所有 li 的文本
            List<String> tags = card.xpath("ul[@class='tags']/li/text()").all();
            row.put("标签", String.join(",", tags));
            rows.add(row);
        }
        // 把本页这一批数据一次性交给 Pipeline（key 用 "rows"）
        page.putField("rows", rows);

        // ---- 第 3 步：发现并加入"下一页"链接，实现自动翻页 ----
        // .css("div.pagination a") 选中分页区里所有 <a>；
        // .links() 表示"把这些 <a> 的链接（href）取出来，并自动补全成完整 URL"；
        // .all() 变列表，再交给 Scheduler 去重 + 排队 -> 爬虫会自动去抓第 2 页。
        page.addTargetRequests(page.getHtml().css("div.pagination a").links().all());

        // 小提示：第 2 页里还有个指向详情页的"干扰链接"、以及指回第 1 页的链接，
        // 爬虫都会尝试，但 Scheduler 会自动去重，且我们的 process 对"非列表页"直接跳过。
    }

    @Override
    public Site getSite() {
        return site;
    }

    /** 运行入口 */
    public static void run() throws Exception {
        LocalSiteServer site = new LocalSiteServer(LocalSiteServer.DEFAULT_PORT);
        site.start();

        try {
            // 真正"开爬"的 5 行核心代码：
            Spider.create(new BookListPageProcessor())     // 1) 用哪个解析器
                    .addUrl(site.url("/books-list.html"))   // 2) 从哪个 URL 开始
                    .addPipeline(new CsvPipeline("output/lesson1-books.csv",
                            new String[]{"书名", "作者", "价格", "评分", "标签"})) // 3) 结果存哪
                    .thread(3)                             // 4) 开几个线程并发（HTTP 爬虫快就靠它）
                    .run();                               // 5) 开始！阻塞直到全部抓完
        } finally {
            site.stop(); // 不管成功失败，把本地站点关掉
        }
        System.out.println("第 1 课完成！结果见 output/lesson1-books.csv");
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
