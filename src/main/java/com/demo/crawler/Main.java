package com.demo.crawler;

import com.demo.crawler.lesson1.BookListPageProcessor;
import com.demo.crawler.lesson2.Lesson2SeleniumDynamic;
import com.demo.crawler.lesson3.Lesson3WebMagicPlusSelenium;
import com.demo.crawler.lesson4.Lesson4RealSite;

import java.util.Scanner;

/**
 * 总入口 / 菜单。
 *
 * 运行方式（任选其一，详见 docs/04-操作手册.md）：
 *   方式一（推荐新手）：mvn -q compile exec:java
 *   方式二（打包后）  ：mvn -q clean package  ->  java -jar target/crawler-demo.jar
 *
 * 也可以直接传数字参数，例如：java -jar target/crawler-demo.jar 1   等价于选 1。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            choose(args[0]);
            return;
        }

        Scanner sc = new Scanner(System.in, "UTF-8");
        while (true) {
            printMenu();
            System.out.print("请输入编号（q 退出）：");
            String input = sc.nextLine().trim();
            if ("q".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                System.out.println("再见！");
                return;
            }
            if (choose(input)) {
                System.out.println("\n----- 本次运行结束，回到菜单 -----\n");
            }
        }
    }

    /** 打印菜单 */
    private static void printMenu() {
        System.out.println("\n================ 爬虫学习 Demo 菜单 ================");
        System.out.println("  1) 第1课 WebMagic 爬【静态页面】（本地，无需浏览器）");
        System.out.println("  2) 第2课 Selenium 爬【动态页面】（会弹出 Edge 浏览器）");
        System.out.println("  3) 第3课 WebMagic + Selenium 对照实验（动态页）");
        System.out.println("  4) 第4课 真实网站练习（quotes.toscrape.com，需联网）");
        System.out.println("  0) 一次性跑完 1 + 2 + 3（本地演示全家福）");
        System.out.println("=====================================================");
    }

    /**
     * 根据编号执行对应课程。
     * @return 是否是有效选择（用于决定是否打印结束提示）
     */
    private static boolean choose(String code) throws Exception {
        switch (code) {
            case "1":
                BookListPageProcessor.run();
                return true;
            case "2":
                // 默认弹出浏览器窗口（headless=false），方便看清 Selenium 在做什么；
                // 加 -Dcrawler.headless=true 可改成无头模式（后台跑、不弹窗）。
                boolean headless2 = !"false".equalsIgnoreCase(System.getProperty("crawler.headless", "false"));
                Lesson2SeleniumDynamic.run(headless2);
                return true;
            case "3":
                Lesson3WebMagicPlusSelenium.run(true);
                return true;
            case "4":
                Lesson4RealSite.run();
                return true;
            case "0":
                BookListPageProcessor.run();
                Lesson2SeleniumDynamic.run(true);
                Lesson3WebMagicPlusSelenium.run(true);
                return true;
            default:
                System.out.println("没看懂，请输入 0~4 或 q。");
                return false;
        }
    }
}
