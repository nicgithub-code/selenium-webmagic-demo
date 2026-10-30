package com.demo.crawler.realcase;

import java.util.Scanner;

/**
 * 实战章节入口菜单（仿照工程根目录的 Main.java）。
 *
 * 三种抓取方式，对应三种"技术栈组合"：
 *   1) 纯 HTTP 接口法   → PureHttpStudentCrawler       （不用 WebMagic，也不用 Selenium，最快最全）
 *   2) 纯 Selenium 法   → SeleniumStudentCrawler       （只用 Selenium，不用 WebMagic）
 *   3) WebMagic+Selenium→ WebMagicSeleniumStudentCrawler（两栈拼接 = 你公司用的方式）
 *
 * 运行：在 IDE 里直接点本类的 main 上方 Run；或
 *      mvn -q compile exec:java -Dexec.mainClass=com.demo.crawler.realcase.RealcaseMain
 *      （注意：pom 里 exec 插件写死了 Main，直接 exec 会跑教材菜单；本类请右键 Run 或用 java -cp 跑）
 */
public class RealcaseMain {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in, "UTF-8");
        while (true) {
            System.out.println("\n========== 实战：抓取玺乐音乐学员列表（三种技术栈组合） ==========");
            System.out.println("  1) 纯 HTTP 接口法（不用 WebMagic / Selenium，最快、字段最全）");
            System.out.println("  2) 纯 Selenium 法（只用 Selenium，不用 WebMagic）");
            System.out.println("  3) WebMagic + Selenium 拼接（两栈组合，公司主流方式）");
            System.out.println("  q) 退出");
            System.out.print("请输入编号：");
            String input = sc.nextLine().trim();
            if ("q".equalsIgnoreCase(input)) {
                System.out.println("再见！");
                return;
            }
            switch (input) {
                case "1":
                    PureHttpStudentCrawler.main(args);
                    break;
                case "2":
                    SeleniumStudentCrawler.main(args);
                    break;
                case "3":
                    WebMagicSeleniumStudentCrawler.main(args);
                    break;
                default:
                    System.out.println("没看懂，请输入 1 / 2 / 3 或 q。");
            }
        }
    }
}
