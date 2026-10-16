@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem Nacos 本地启动脚本（standalone 模式，内嵌 Derby 存储）
rem 用法：双击运行，或在终端执行 start-nacos.bat
rem 如需换安装目录：set NACOS_HOME=D:\nacos 后再执行
rem ============================================================

if "%NACOS_HOME%"=="" set "NACOS_HOME=C:\Users\29356\tools\nacos"
set "NACOS_PORT=8848"

echo [INFO] NACOS_HOME = %NACOS_HOME%

if not exist "%NACOS_HOME%\bin\startup.cmd" (
    echo [ERROR] 未找到 Nacos 启动程序：%NACOS_HOME%\bin\startup.cmd
    echo         请确认 NACOS_HOME 指向 Nacos 解压目录。
    exit /b 1
)

if "%JAVA_HOME%"=="" (
    echo [ERROR] 未设置 JAVA_HOME，Nacos 需要 JDK 运行环境。
    exit /b 1
)

rem 已在运行则不重复启动
netstat -ano | findstr "LISTENING" | findstr ":%NACOS_PORT%" >nul
if not errorlevel 1 (
    echo [INFO] Nacos 已在运行，控制台：http://127.0.0.1:%NACOS_PORT%/nacos/
    exit /b 0
)

echo [INFO] 正在以 standalone 模式启动 Nacos ...
call "%NACOS_HOME%\bin\startup.cmd" -m standalone

endlocal
