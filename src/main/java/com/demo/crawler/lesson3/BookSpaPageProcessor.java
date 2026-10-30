package com.demo.crawler.lesson3;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第 3 课的解析器：和"第 1 课几乎一模一样"——只挑 .book-card 里的字段。
 *
 * 这正是本课要教的核心思想：
 *   解析逻辑与"页面怎么下载下来的"完全解耦。
 *   同样的 PageProcessor，配 HttpClientDownloader 抓静态页、配 SeleniumDownloader 抓动态页，
 *   代码一行都不用改。动态页难的是"下载"，而不是"解析"。
 */
public class BookSpaPageProcessor implements PageProcessor {

    private Site site = Site.me()
            .setCharset("utf-8")
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36")
            .setSleepTime(500)
            .setTimeOut(15000);

    @Override
    public void process(Page page) {
        if (!page.getHtml().css("div.book-card").match()) {
            return; // 不是列表页就跳过
        }
        List<Selectable> cards = page.getHtml().css("div.book-card").nodes();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Selectable card : cards) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("书名", card.xpath("h3[@class='title']/text()").get());
            row.put("作者", card.xpath("p[@class='author']/text()").replace("^作者[:：]\\s*", "").get());
            row.put("价格", card.xpath("p[@class='price']/text()").replace("^价格[:：]\\s*", "").get());
            row.put("评分", card.xpath("p[@class='score']/text()").replace("^评分[:：]\\s*", "").get());
            List<String> tags = card.xpath("ul[@class='tags']/li/text()").all();
            row.put("标签", String.join(",", tags));
            rows.add(row);
        }
        // 同页多数据：一次性提交整页列表，避免同 key 覆盖（详见 CsvPipeline 注释）
        page.putField("rows", rows);
        // 注意：动态页所有数据都在一页里，没有"下一页"需要发现，所以不调用 addTargetRequests。
    }

    @Override
    public Site getSite() {
        return site;
    }
}
