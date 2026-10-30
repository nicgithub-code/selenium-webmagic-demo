# 爬虫学习 Demo：WebMagic + Selenium（零基础版）

> 目标：让**没有爬虫基础、也没有前端基础**的你，照着能跑、看着能懂，快速上手 Java 爬虫。

---

## 这个工程能给你什么

| 你缺什么 | 工程怎么补 |
|---|---|
| 没有"目标网站"可练 | 内置一个**本地练习站点**（`pages/` 目录），不用联网就能跑 1~3 课 |
| 看不懂网页结构 | 每个示例都带**逐行中文注释**，挑数据用的选择器怎么写都讲清楚 |
| 不会配环境 | 已经帮你下载好 `drivers/msedgedriver.exe`，双击 `run.bat` 就能跑 |
| 不知道学什么 | 见 `docs/03-学习路线.md` 和 `docs/02-零前端基础速成.md` |

---

## 技术栈

- **WebMagic 0.7.4**：Java 爬虫框架，负责「下载 → 解析 → 调度 → 存储」四件事
- **Selenium 4.15**：浏览器自动化工具，负责处理「JavaScript 渲染 / 需要点击 / 需要登录」的页面
- **JDK 17 + Maven 3.9**（本机已具备）

---

## 5 分钟跑起来

1. 安装 JDK 17 与 Maven，并确认命令行能执行 `java -version` 和 `mvn -version`
2. 进入本工程目录 `selenium-webmagic-demo`
3. 双击 `run.bat`（或命令行执行 `mvn -q compile exec:java`）
4. 在菜单里输入数字 `1` / `2` / `3` / `4` 选择课程，`q` 退出

> 第 2、3 课会用到浏览器（Edge）。如果你用的是无界面服务器，可在运行时加参数 `-Dcrawler.headless=true`。

---

## 目录结构一览

```
selenium-webmagic-demo/
├─ pom.xml                      # Maven 依赖与打包配置（已配好）
├─ run.bat                      # 一键运行脚本
├─ pages/                       # 本地练习用的网页（静态页 + 动态页）
├─ drivers/
│  └─ msedgedriver.exe          # 已下载好的 Edge 驱动（Edge 升级后需重新下载）
├─ src/main/java/com/demo/crawler/
│  ├─ Main.java                 # 菜单入口
│  ├─ common/                   # 公共：CSV 输出、Selenium 工厂
│  ├─ site/                     # 本地练习站点（用 JDK 自带 HTTP 服务）
│  └─ lesson1~4/                # 四个课程
└─ docs/                        # 下面这些学习/操作文档
```

---

## 四节课分别学什么

| 课 | 主题 | 用到的工具 | 跑完得到 |
|---|---|---|---|
| 1 | **WebMagic 爬静态页面**（含自动翻页） | WebMagic 默认 HTTP 下载器 | `output/lesson1-books.csv`（6 本书） |
| 2 | **原生 Selenium 爬动态页面**（含点击"加载更多"） | Selenium 驱动真实浏览器 | `output/lesson2-books.csv`（6 本书） |
| 3 | **WebMagic + Selenium 对照实验** | 同一个解析器，换下载器 | `lesson3-http.csv`（0 条）vs `lesson3-selenium.csv`（6 条） |
| 4 | **真实网站练习** | WebMagic + 外网 | `output/lesson4-quotes.csv`（名言） |

第 3 课是最关键的「顿悟点」：**解析逻辑和"页面怎么下载下来"完全解耦**——动态页难的是下载，不是解析。

---

## 配套文档（强烈建议按顺序读）

1. `docs/01-豆包说法辨析.md` —— 你发的那个链接里"零基础学 Selenium+WebMagic"的说法对不对
2. `docs/02-零前端基础速成.md` —— 不懂前端也能写爬虫，只需掌握 4 个知识点
3. `docs/03-学习路线.md` —— 从零到能爬一个小站的路线图
4. `docs/04-操作手册.md` —— 每一步怎么操作、常见报错怎么解
5. `docs/05-爬虫合规与反爬.md` —— 法律红线 + 绕过反爬的正确姿势

---

## 一句心里话

爬虫本身不难，难的是**先想清楚"我要的数据在网页的哪个位置、由什么方式产生"**。本工程的所有注释都在帮你建立这个"网页 → 数据"的映射直觉。先跑通，再改着玩，是最好的学习方式。
