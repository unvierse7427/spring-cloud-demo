@echo off
setlocal

rem ============================================================
rem RocketMQ 本地启动脚本（单机模式：NameServer + Broker + Dashboard）
rem 用法：双击运行，或在终端执行 start-rocketmq.bat
rem 如需换安装目录：set ROCKETMQ_HOME=D:\rocketmq 后再执行
rem 启动完成后会自动打开 RocketMQ Dashboard
rem
rem Dashboard 是可选的：把 rocketmq-dashboard-2.0.0.jar 放到
rem C:\Users\29356\tools\rocketmq-dashboard\ 下即可，缺失时只跳过控制台。
rem ============================================================

if "%ROCKETMQ_HOME%"=="" set "ROCKETMQ_HOME=C:\Users\29356\tools\rocketmq\rocketmq-all-5.5.1-bin-release"
set "ROCKETMQ_NAMESRV_PORT=9876"
set "ROCKETMQ_BROKER_PORT=10911"
set "ROCKETMQ_DASHBOARD_PORT=8090"
set "ROCKETMQ_DASHBOARD_HOME=C:\Users\29356\tools\rocketmq-dashboard"
set "ROCKETMQ_DASHBOARD_JAR=%ROCKETMQ_DASHBOARD_HOME%\rocketmq-dashboard-2.0.0.jar"
set "ROCKETMQ_DASHBOARD_URL=http://127.0.0.1:%ROCKETMQ_DASHBOARD_PORT%"

echo [INFO] ROCKETMQ_HOME = %ROCKETMQ_HOME%

if not exist "%ROCKETMQ_HOME%\bin\mqnamesrv.cmd" (
    echo [ERROR] 未找到 RocketMQ 启动程序：%ROCKETMQ_HOME%\bin\mqnamesrv.cmd
    echo         请确认 ROCKETMQ_HOME 指向 RocketMQ 解压目录。
    exit /b 1
)
if "%JAVA_HOME%"=="" (
    echo [ERROR] 未设置 JAVA_HOME，RocketMQ 需要 JDK 运行环境。
    exit /b 1
)

rem ---------------------- NameServer ----------------------
netstat -ano | findstr "LISTENING" | findstr ":%ROCKETMQ_NAMESRV_PORT%" >nul
if errorlevel 1 (
    echo [INFO] 正在启动 RocketMQ NameServer（端口 %ROCKETMQ_NAMESRV_PORT%）...
    start "RocketMQ NameServer" /D "%ROCKETMQ_HOME%\bin" cmd /c mqnamesrv.cmd
    call :wait_port %ROCKETMQ_NAMESRV_PORT%
    if errorlevel 1 (
        echo [WARN] NameServer 等待超时，请查看对应窗口的日志。
        exit /b 1
    )
) else (
    echo [INFO] RocketMQ NameServer 已在运行。
)

rem ---------------------- Broker ----------------------
netstat -ano | findstr "LISTENING" | findstr ":%ROCKETMQ_BROKER_PORT%" >nul
if errorlevel 1 (
    echo [INFO] 正在启动 RocketMQ Broker（端口 %ROCKETMQ_BROKER_PORT%）...
    start "RocketMQ Broker" /D "%ROCKETMQ_HOME%\bin" cmd /c mqbroker.cmd -n 127.0.0.1:%ROCKETMQ_NAMESRV_PORT% -c "%ROCKETMQ_HOME%\conf\broker.conf"
    call :wait_port %ROCKETMQ_BROKER_PORT%
    if errorlevel 1 (
        echo [WARN] Broker 等待超时，请查看对应窗口的日志。
        exit /b 1
    )
) else (
    echo [INFO] RocketMQ Broker 已在运行。
)

rem ---------------------- Dashboard ----------------------
netstat -ano | findstr "LISTENING" | findstr ":%ROCKETMQ_DASHBOARD_PORT%" >nul
if not errorlevel 1 (
    echo [INFO] RocketMQ Dashboard 已在运行，地址：%ROCKETMQ_DASHBOARD_URL%
    start "" "%ROCKETMQ_DASHBOARD_URL%"
    exit /b 0
)
if not exist "%ROCKETMQ_DASHBOARD_JAR%" (
    echo [WARN] 未找到 RocketMQ Dashboard：%ROCKETMQ_DASHBOARD_JAR%
    echo        RocketMQ 自身没有可视化界面，如需 Dashboard 请下载 rocketmq-dashboard-2.0.0.jar
    echo        （Maven 坐标：org.apache.rocketmq:rocketmq-dashboard:2.0.0）放到上述目录后重跑本脚本。
    exit /b 0
)
if not exist "%ROCKETMQ_DASHBOARD_HOME%" mkdir "%ROCKETMQ_DASHBOARD_HOME%"

echo [INFO] 正在启动 RocketMQ Dashboard（端口 %ROCKETMQ_DASHBOARD_PORT%）...
rem Dashboard 的日志默认写到 %user.home%\logs，这里把 user.home 指到安装目录，日志统一落在安装目录下
start "RocketMQ Dashboard" cmd /c ""%JAVA_HOME%\bin\java.exe" -Duser.home="%ROCKETMQ_DASHBOARD_HOME%" -jar "%ROCKETMQ_DASHBOARD_JAR%" --server.port=%ROCKETMQ_DASHBOARD_PORT% --rocketmq.config.namesrvAddr=127.0.0.1:%ROCKETMQ_NAMESRV_PORT%"

echo [INFO] 等待 Dashboard 就绪 ...
call :wait_port %ROCKETMQ_DASHBOARD_PORT%
if errorlevel 1 (
    echo [WARN] Dashboard 等待超时，请查看对应窗口的日志。
    exit /b 1
)

echo [INFO] RocketMQ Dashboard 已就绪，正在打开浏览器 ...
start "" "%ROCKETMQ_DASHBOARD_URL%"
echo [INFO] Dashboard：%ROCKETMQ_DASHBOARD_URL%

endlocal
exit /b 0

rem ------------------------------------------------------------
rem 等待端口进入监听状态，最多 60 秒
rem ------------------------------------------------------------
:wait_port
set "WAIT_PORT=%~1"
powershell -NoProfile -Command "$i=0; while($i -lt 120){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%WAIT_PORT%); $c.Close(); exit 0 } catch { Start-Sleep -Milliseconds 500; $i++ } }; exit 1"
exit /b %ERRORLEVEL%
