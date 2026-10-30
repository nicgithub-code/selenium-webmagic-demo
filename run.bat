@echo off
REM ============================================================
REM  一键运行脚本（推荐新手使用）
REM  作用：先编译，再启动菜单，然后用数字选择要跑的课程。
REM  用法：双击本文件，或在工程根目录执行 run.bat
REM  前提：已安装 JDK 17 和 Maven，且 "java" / "mvn" 命令可用。
REM
REM  常用参数（在下面 mvn 命令后追加）：
REM    -Dcrawler.headless=true   第 2/3 课用无头模式（不弹浏览器窗口）
REM    -Dcrawler.edge.driver=路径  手动指定 Edge 驱动
REM ============================================================
cd /d %~dp0
mvn -q clean compile exec:java
pause
