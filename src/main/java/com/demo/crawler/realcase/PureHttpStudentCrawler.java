package com.demo.crawler.realcase;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实战方案一（基线）：纯 HTTP 接口法 —— 【不使用 WebMagic，也不使用 Selenium】。
 *
 * 为什么把它列为"基线"？因为它是爬虫里最高效、最干净的做法：
 *   直接调后端 JSON 接口拿结构化数据，不需要浏览器、不需要爬虫框架。
 * 把它和下面两种（纯 Selenium、WebMagic+Selenium）放一起对比，能清楚看到：
 *   - 能用接口就别用浏览器（方案一最快、字段最全）；
 *   - 接口拿不到时，才上 Selenium；而 Selenium 又能"嵌进"WebMagic 复用其生态（方案三）。
 *
 * 方法名 crawlByHttpApi 明确标识：这是走 HTTP API 的方式。
 */
public class PureHttpStudentCrawler {

    private static final String BASE_URL = "http://localhost:5173";
    private static final String LOGIN_URL = BASE_URL + "/api/auth/login";
    private static final String STUDENTS_URL = BASE_URL + "/api/students";
    private static final String TEACHERS_URL = BASE_URL + "/api/system/users/teachers";

    private static final String USERNAME = "teacher1";
    private static final String PASSWORD = "123456";

    /** CSV 表头（顺序即写入顺序） */
    private static final String[] HEADERS = {"姓名", "性别", "家长", "联系电话", "出生日期", "入学时间", "主教老师", "备注"};

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public static void main(String[] args) throws Exception {
        new PureHttpStudentCrawler().crawlByHttpApi("output/realcase-students-api.csv");
    }

    /** 走 HTTP 接口抓取（不带任何爬虫框架） */
    public void crawlByHttpApi(String outFile) throws Exception {
        String token = loginByHttp();
        System.out.println("登录成功，token 前 20 位：" + token.substring(0, Math.min(20, token.length())) + "...");

        Map<Integer, String> teacherMap = fetchTeachers(token);
        List<Map<String, String>> rows = fetchAllStudents(token, teacherMap);
        System.out.println("学员总数：" + rows.size());

        StudentCsvWriter.write(outFile, HEADERS, rows);
    }

    // ------------------------- 以下都是普通 HTTP 调用，与 WebMagic/Selenium 无关 -------------------------

    private String loginByHttp() throws Exception {
        String body = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(LOGIN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonObject json = JsonParser.parseString(send(req)).getAsJsonObject();
        if (json.get("code").getAsInt() != 200) {
            throw new RuntimeException("登录失败：" + json.get("msg").getAsString());
        }
        return json.getAsJsonObject("data").get("token").getAsString();
    }

    private Map<Integer, String> fetchTeachers(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TEACHERS_URL))
                .header("Authorization", "Bearer " + token).GET().build();
        JsonObject json = JsonParser.parseString(send(req)).getAsJsonObject();
        JsonArray arr = json.getAsJsonObject("data").getAsJsonArray();
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject t = arr.get(i).getAsJsonObject();
            map.put(t.get("id").getAsInt(), t.get("realName").getAsString());
        }
        return map;
    }

    private List<Map<String, String>> fetchAllStudents(String token, Map<Integer, String> teacherMap) throws Exception {
        List<Map<String, String>> all = new ArrayList<>();
        int pageNum = 1, pageSize = 50;
        while (true) {
            String url = STUDENTS_URL + "?pageNum=" + pageNum + "&pageSize=" + pageSize;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token).GET().build();
            JsonObject data = JsonParser.parseString(send(req)).getAsJsonObject().getAsJsonObject("data");
            JsonArray records = data.getAsJsonArray("records");
            int total = data.get("total").getAsInt();
            for (int i = 0; i < records.size(); i++) {
                all.add(toRow(records.get(i).getAsJsonObject(), teacherMap));
            }
            System.out.println("  已拉取第 " + pageNum + " 页，累计 " + all.size() + " / " + total);
            if (records.size() == 0 || all.size() >= total) break;
            pageNum++;
        }
        return all;
    }

    private Map<String, String> toRow(JsonObject s, Map<Integer, String> teacherMap) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("姓名", opt(s, "name"));
        row.put("性别", opt(s, "gender"));
        row.put("家长", opt(s, "parentName"));
        row.put("联系电话", opt(s, "phone"));
        row.put("出生日期", opt(s, "birthDate"));
        row.put("入学时间", opt(s, "entryDate"));
        if (s.has("teacherId") && !s.get("teacherId").isJsonNull()) {
            int tid = s.get("teacherId").getAsInt();
            row.put("主教老师", teacherMap.getOrDefault(tid, "id:" + tid));
        } else {
            row.put("主教老师", "-");
        }
        row.put("备注", opt(s, "remark"));
        return row;
    }

    private String opt(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("请求失败 HTTP " + resp.statusCode() + " -> " + resp.body());
        }
        return resp.body();
    }
}
