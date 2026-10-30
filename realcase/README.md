# 实战：抓取「玺乐音乐」后台学员列表

把前面 1~3 课学的本事，用在你**自己部署的网站**上。目标：登录后台 → 打开学员管理 → 把学员列表下载成 CSV。

站点：http://localhost:5173 （Vue3 单页应用，经 Vite 把 `/api` 代理到后端 10001 端口）
账号/密码：`admin / 123456`

---

## 先搞清楚"数据在哪"（爬虫第一步）

打开 F12 → Network，干三件事：
1. 登录：`POST /api/auth/login` → 返回 `data.token`（JWT 登录凭证）。
2. 学员列表：`GET /api/students?pageNum=1&pageSize=10` → `data.records` 是数据、`data.total` 是总数。
3. 翻页参数用 **`pageNum`**（不是 `page`）、**`pageSize`**。

这个站是 SPA：直接看 HTML 源码是空的 `<div id="app">`，数据全是 JS 跑出来的——所以纯 HTTP 抓 HTML 拿不到，和教材第 2、3 课场景一致。

---

## 动手前的「F12 侦察清单」（写代码之前必做）

**爬虫 70% 的工作是前期侦察，代码只是把侦察结果固化下来。** 换任何新站点都按这个顺序走：

### 第 1 步：Network 找接口（永远先做）
```
F12 → Network → 勾选 Fetch/XHR → 刷新页面 / 点"下一页"
必须记下：
  ✓ 请求完整 URL 与 HTTP 方法（GET/POST）
  ✓ 请求头（有无 Authorization / Content-Type）
  ✓ 参数名到底叫什么（本例是 pageNum、pageSize，不是 page、size）
  ✓ 响应 JSON 结构（是 {data:{records:[...]}} 还是直接数组？）
  ✓ 每条记录有哪些字段
→ 接口是明文就直接用【方案①接口法】，全文在这收工，别去开浏览器。
```

### 第 2 步：接口走不通，才去 Elements 抠 DOM
```
用审查箭头（Ctrl+Shift+C）点页面上的目标数据，记下"稳定特征"：
  ✓ 每条数据的重复单元      .el-table__row（Element Plus 表格数据行）
  ✓ 单元内部的取值点        td .cell（注意空格=后代选择器）
  ✓ 要点的按钮              button.login-btn / .el-pagination .btn-next
  ✓ 要填的输入框            input[placeholder='请输入账号']
  ✓ 结束/状态怎么判定      见下方 ⚠️
```

### 第 3 步：每个选择器先在 Console 验证数量
```js
document.querySelectorAll('.el-table__row').length   // 必须等于页面上看到的条数
```
**验证通过再写进 Java**，能省掉后面大量调试时间。

### 第 4 步：把"操作剧本"写成步骤清单，再写代码
```
① 打开 /login
② 等账号框出现（SPA 的表单是 JS 渲染的，必须 wait）
③ 填 admin / 123456
④ 点 button.login-btn
⑤ 等 URL 含 /dashboard（确认登录成功）
⑥ 等表格行 .el-table__row 出现
⑦ 抠当前页所有行
⑧ 查"下一页"按钮是否被禁用
⑨ 没禁用就点，等首行内容变化 → 回到 ⑦
⑩ 写 CSV
```

### ⚠️ 血泪教训：状态判断优先读「属性」，别赌「类名」
本工程真实踩过一次坑：末页的"下一页"按钮实际是这个样子——

```html
<button class="btn-next is-last" aria-disabled="true" disabled>下一页</button>
```

**没有 `is-disabled` 类名！** 原先写的 `class.contains("is-disabled")` 恒为 false，
导致末页仍判定"还能翻页"，反复点击已禁用按钮 → 死循环/重复抓最后一页。

正确写法：**读 DOM 属性，不依赖 UI 库的类名习惯**
```java
if (!nextBtn.isEnabled()) { break; }   // ✅ 读 disabled 属性，跨版本稳定
// 等价写法：nextBtn.getAttribute("disabled") != null
//           "true".equals(nextBtn.getAttribute("aria-disabled"))
```
> 类名（`is-disabled` / `is-last`）是 Element Plus 的**实现细节**，换个版本就变；
> 属性（`disabled` / `aria-disabled`）是 **HTML 标准语义**，稳定得多。

---

## 三种"技术栈组合"（代码在 `src/main/java/com/demo/crawler/realcase/`）

| 方案 | 文件 | 技术栈 | 原理 | 速度 | 字段 |
|---|---|---|---|---|---|
| **① 纯接口法** | `PureHttpStudentCrawler.java` | 都不用 | 登录拿 token → 调 JSON 接口 → 解析 → CSV | 最快 | 最全（含出生日期、老师名） |
| **② 纯 Selenium** | `SeleniumStudentCrawler.java` | 只用 Selenium | 真打开浏览器，登录+翻页，从表格 DOM 抠 → CSV | 慢/单线程 | 列表页展示的列 |
| **③ WebMagic+Selenium** | `WebMagicSeleniumStudentCrawler.java` + `StudentSeleniumDownloader.java` + `StudentListPageProcessor.java` | **两者拼接** | WebMagic 当骨架，Selenium 当下载引擎 | 慢/单线程 | 列表页展示的列 |

> **第 ③ 种就是"你公司用的那种逻辑"**：WebMagic 负责调度/解析/输出（生态成熟、可分布式、好监控），
> 把默认的 HTTP 下载器换成自己写的 `StudentSeleniumDownloader`（实现 WebMagic 的 `Downloader` 接口，
> 底层用 Selenium 真浏览器登录、等渲染、翻页），解析仍用 WebMagic 的 CSS 选择器。两者通过 `Downloader` 接口解耦。

### 三者的关系（一句话）
- 能用接口就别用浏览器（① 最快、字段最全）；
- 接口拿不到/被加密/强反爬时，才上浏览器；
- 上了浏览器，又想复用 WebMagic 的调度与管道生态，就把浏览器"塞"进 WebMagic 当下载器（③）。

### 澄清：单独用 Selenium 到底行不行？
**行，方案②就证明了它能独立完成抓取（真实抓到 55 条）。** 精确区分是：

| 单独用 | 能否完成任务 | 缺什么 |
|---|---|---|
| WebMagic（默认 HTTP） | ❌ **功能性失败**——动态页抓到 0 条（第 3 课实验） | 必须引入 Selenium 才能拿到渲染后的数据 |
| Selenium（方案②） | ✅ **完全能干完** | 只是**没有框架设施**：URL 队列、去重、统一落地、重试、监控全得自己手写 |

所以方案②不是"不可行"，而是"**够用但重复造轮子**"。什么时候选它：
- 一次性小脚本、临时取个数 → **纯 Selenium 反而更直接**（没有抽象层，好懂好改）
- 要长期跑、要落地多条管线、要监控/重试/去重 → 上方案③，这些 WebMagic 免费送你
- 真实优先级永远是：**① 接口 → ③ 拼接（需渲染 + 有框架诉求）→ ② 纯 Selenium（临时脚本）**

---

## 怎么跑（IDE 直接跑 main 最方便）

| 想跑 | 打开文件 → 跑它的 main |
|---|---|
| ① 纯接口法 | `PureHttpStudentCrawler.java` |
| ② 纯 Selenium | `SeleniumStudentCrawler.java`（会弹 Edge 窗口） |
| ③ WebMagic+Selenium | `WebMagicSeleniumStudentCrawler.java`（会弹 Edge 窗口） |
| 菜单选 | `RealcaseMain.java` |

> ②③ 默认弹出浏览器窗口方便你看；后台跑加 VM 参数 `-Dcrawler.headless=true`。
> 注意：`pom.xml` 里 exec 插件写死了 `Main`，所以 `mvn exec:java` 会进教材菜单——
> 实战类请用 IDE 右键 Run，或 `java -cp` 直接跑。

---

## 产物对比

| 文件 | 来自 | 特点 |
|---|---|---|
| `output/realcase-students-api.csv` | ① 纯接口 | 字段最全（出生日期、主教老师姓名） |
| `output/realcase-students-selenium.csv` | ② 纯 Selenium | 列表页列，出生日期为空 |
| `output/realcase-students-webmagic-selenium.csv` | ③ 拼接 | 同 ②，但解析/输出走的是 WebMagic 选择器与 Pipeline |

把 ① 和 ③ 对比着看，能直观体会到：**接口给你的** vs **网页上让人看到的** 是有差距的——
接口法能拿到网页上没直接展示的字段（出生日期、teacherId 翻译后的老师名）。

---

## 合规提醒
这是你**自己部署**的站点，仅供学习。爬别人网站前务必看 `robots.txt` 与法律红线
（见工程 `docs/05-爬虫合规与反爬.md`）。
