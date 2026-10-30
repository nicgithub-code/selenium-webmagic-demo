package com.demo.crawler.site;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地练习站点（纯属教学用，真实爬虫不会这么干）。
 *
 * 为什么要它？
 *   新手没有"目标网站"也能练手。这个服务会在本机 8899 端口开一个小网站，
 *   提供：
 *     - /books-list.html   静态图书列表（第 1 页）—— 给 WebMagic 练 HTTP 爬取
 *     - /books-list-2.html 静态图书列表（第 2 页）
 *     - /books-spa.html    动态图书列表（JS 渲染）—— 给 Selenium 练"浏览器爬取"
 *     - /book-detail?id=b1 图书详情页（由本服务动态生成）—— 练习"列表→详情"二级爬取
 *
 * 它借助 JDK 自带的 com.sun.net.httpserver（不需要任何第三方库）实现。
 *
 * 重要认知：爬虫的本质就是"向一个 URL 发请求，拿回 HTML 文本再解析"。
 * 这里的 URL 是 http://127.0.0.1:8899/...，和真实网站的 https://... 没区别。
 */
public class LocalSiteServer {

    /** 默认端口 */
    public static final int DEFAULT_PORT = 8899;

    /** 详情页的"额外信息"表：键是图书 id。列表页只有基础信息，详情页才有出版社等。 */
    private static final Map<String, Detail> DETAILS = new HashMap<>();
    static {
        DETAILS.put("b1", new Detail("Java 网络爬虫入门",      "人民邮电出版社", "9787115582201", "2023-03", 42, "从零讲清 HTTP、选择器、WebMagic，循序渐进。"));
        DETAILS.put("b2", new Detail("HTTP 权威指南",         "人民邮电出版社", "9787115315670", "2014-01", 8,  "HTTP 协议圣经，搞爬虫必读网络基础。"));
        DETAILS.put("b3", new Detail("CSS 选择器实战",         "电子工业出版社", "9787121380800", "2022-07", 100, "把 CSS 选择器讲透，爬虫挑数据全靠它。"));
        DETAILS.put("b4", new Detail("XPath 从入门到精通",     "机械工业出版社", "9787111598765", "2021-11", 35, "XPath 定位神器，复杂页面救星。"));
        DETAILS.put("b5", new Detail("Selenium 自动化测试实战", "清华大学出版社", "9787302560890", "2020-09", 0,  "Selenium 用法大全，爬虫也可借鉴。"));
        DETAILS.put("b6", new Detail("MySQL 是怎样运行的",     "人民邮电出版社", "9787115547126", "2021-01", 73, "把数据库原理讲得像故事书，强烈推荐。"));
    }

    private final int port;
    private final Path pagesDir;   // 静态 html 文件所在目录
    private HttpServer server;

    public LocalSiteServer(int port) {
        this.port = port;
        // pages 目录：相对于"项目根目录"。两种运行方式（mvn exec / java -jar）都默认在根目录启动。
        this.pagesDir = Paths.get(System.getProperty("user.dir"), "pages");
        if (!Files.isDirectory(pagesDir)) {
            throw new IllegalStateException("找不到 pages 目录：" + pagesDir
                    + "\n请在该目录下运行本程序（详见 docs/04-操作手册.md）。");
        }
    }

    /** 启动站点（非阻塞，调用后立刻返回，站点在后台跑） */
    public void start() throws IOException {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null); // 用 JDK 自带线程池
        server.start();
        System.out.println("[本地站点] 已启动: http://127.0.0.1:" + port + "/books-list.html");
    }

    /** 停止站点 */
    public void stop() {
        if (server != null) server.stop(0);
    }

    /** 根据路径拼出完整 URL（供爬虫使用） */
    public String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    /** 请求分发 */
    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery(); // 例如 "id=b1"

        try {
            if (path.equals("/") || path.isEmpty()) {
                sendRedirect(ex, "/books-list.html");
            } else if (path.equals("/book-detail")) {
                sendHtml(ex, buildDetailHtml(query));
            } else if (path.endsWith(".html")) {
                sendFile(ex, path);
            } else if (path.equals("/favicon.ico")) {
                ex.sendResponseHeaders(204, -1); // 没图标，返回空
            } else {
                send404(ex);
            }
        } catch (Exception e) {
            sendText(ex, 500, "服务器错误: " + e.getMessage());
        } finally {
            ex.close();
        }
    }

    /** 返回静态 html 文件内容 */
    private void sendFile(HttpExchange ex, String path) throws IOException {
        Path file = pagesDir.resolve(path.substring(1)); // 去掉开头的 "/"
        if (!Files.exists(file)) { send404(ex); return; }
        String html = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        sendHtml(ex, html);
    }

    /** 动态生成详情页 HTML */
    private String buildDetailHtml(String query) {
        String id = extractParam(query, "id");
        Detail d = DETAILS.get(id);
        if (d == null) {
            return wrapDetail(id, "<p>未找到该书 id=" + id + "</p>");
        }
        String body =
                "<h1 class=\"title\">" + d.title + "</h1>" +
                "<dl class=\"meta\">" +
                "  <dt>出版社</dt><dd class=\"publisher\">" + d.publisher + "</dd>" +
                "  <dt>ISBN</dt><dd class=\"isbn\">" + d.isbn + "</dd>" +
                "  <dt>出版日期</dt><dd class=\"publish-date\">" + d.publishDate + "</dd>" +
                "  <dt>库存</dt><dd class=\"stock\">" + d.stock + " 本</dd>" +
                "</dl>" +
                "<p class=\"summary\">" + d.summary + "</p>";
        return wrapDetail(id, body);
    }

    /** 给详情页内容套一个带 class 的完整 HTML 骨架（方便爬虫用选择器提取） */
    private String wrapDetail(String id, String body) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>图书详情</title></head>" +
               "<body><div class=\"book-detail\" data-id=\"" + id + "\">" + body + "</div></body></html>";
    }

    // ===== 下面是纯 HTTP 工具方法，看不懂不影响理解爬虫主流程 =====

    private void sendHtml(HttpExchange ex, String html) throws IOException {
        sendText(ex, 200, html);
    }

    private void sendText(HttpExchange ex, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendRedirect(HttpExchange ex, String to) throws IOException {
        ex.getResponseHeaders().add("Location", to);
        ex.sendResponseHeaders(302, -1);
    }

    private void send404(HttpExchange ex) throws IOException {
        sendText(ex, 404, "<h1>404 页面不存在</h1>");
    }

    /** 从 "id=b1&x=2" 这样的 query 中取出某个参数值 */
    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String kv : query.split("&")) {
            String[] arr = kv.split("=", 2);
            if (arr.length == 2 && arr[0].equals(key)) return arr[1];
        }
        return null;
    }

    /** 详情页数据载体 */
    private static class Detail {
        String title, publisher, isbn, publishDate;
        int stock;
        String summary;
        Detail(String title, String publisher, String isbn, String publishDate, int stock, String summary) {
            this.title = title; this.publisher = publisher; this.isbn = isbn;
            this.publishDate = publishDate; this.stock = stock; this.summary = summary;
        }
    }
}
