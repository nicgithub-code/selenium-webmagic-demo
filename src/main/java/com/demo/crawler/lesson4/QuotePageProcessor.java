package com.demo.crawler.lesson4;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第 4 课解析器：爬真实公开练习站 quotes.toscrape.com 上的"名言 + 作者 + 标签"。
 *
 * 这个站点是专门为爬虫练习做的，robots.txt 允许爬取，没有任何反爬，
 * 是新手练手真实网站的首选（千万别一上来就去爬电商/社交大站）。
 *
 * 结构：
 *   - 每页有多条名言，每条是一个 <div class="quote">
 *   - 翻页链接在 <li class="next"><a href="...">Next</a></li>
 */
public class QuotePageProcessor implements PageProcessor {

    private Site site = Site.me()
            .setCharset("utf-8")
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36")
            .setRetryTimes(3)
            .setSleepTime(800)    // 两次请求之间稍作停顿，对目标站点友好、也降低被限流概率
            .setTimeOut(30000);   // 真实网络慢，单请求放宽到 30 秒

    @Override
    public void process(Page page) {
        if (!page.getHtml().css("div.quote").match()) {
            return;
        }
        List<Selectable> quotes = page.getHtml().css("div.quote").nodes();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Selectable q : quotes) {
            Map<String, String> row = new LinkedHashMap<>();
            // 名言文字在 <span class="text"> 里；注意文字里可能含引号，这里原样取
            row.put("名言", q.xpath("span[@class='text']/text()").get());
            row.put("作者", q.xpath("small[@class='author']/text()").get());
            // 标签在 <div class="tags"><a class="tag">...</a></div>
            List<String> tags = q.xpath("div[@class='tags']/a[@class='tag']/text()").all();
            row.put("标签", String.join(",", tags));
            rows.add(row);
        }
        // 一次性提交整页多条数据（避免同 key 覆盖，详见 CsvPipeline 注释）
        page.putField("rows", rows);

        // 自动翻页：取出 "下一页" 链接
        page.addTargetRequests(page.getHtml().css("li.next a").links().all());
    }

    @Override
    public Site getSite() {
        return site;
    }
}
