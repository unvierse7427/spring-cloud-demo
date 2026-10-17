@echo off
setlocal

rem ============================================================
rem Qdrant 本地启动脚本（单机模式，数据存放于安装目录下的 storage）
rem 用法：双击运行，或在终端执行 start-qdrant.bat
rem 如需换安装目录：set QDRANT_HOME=D:\qdrant 后再执行
rem 启动完成后会自动打开 Qdrant Dashboard
rem ============================================================

if "%QDRANT_HOME%"=="" set "QDRANT_HOME=C:\Users\29356\tools\qdrant"
rem REST 端口（Dashboard 与 HTTP API），gRPC 端口固定为 REST+1 即 6334
set "QDRANT_REST_PORT=6333"
set "QDRANT_DASHBOARD_URL=http://127.0.0.1:%QDRANT_REST_PORT%/dashboard"

echo [INFO] QDRANT_HOME = %QDRANT_HOME%

if not exist "%QDRANT_HOME%\qdrant.exe" (
    echo [ERROR] 未找到 Qdrant 可执行文件：%QDRANT_HOME%\qdrant.exe
    echo         请确认 QDRANT_HOME 指向 Qdrant 解压目录。
    exit /b 1
)

netstat -ano | findstr "LISTENING" | findstr ":%QDRANT_REST_PORT%" >nul
if not errorlevel 1 (
    echo [INFO] Qdrant 已在运行，Dashboard：%QDRANT_DASHBOARD_URL%
    start "" "%QDRANT_DASHBOARD_URL%"
    exit /b 0
)

echo [INFO] 正在启动 Qdrant（REST %QDRANT_REST_PORT%，gRPC 6334）...
rem Qdrant 相对工作目录读取 config 与写入 storage，因此必须切换工作目录
start "Qdrant" /D "%QDRANT_HOME%" "%QDRANT_HOME%\qdrant.exe"

echo [INFO] 等待 Qdrant 就绪 ...
call :wait_port %QDRANT_REST_PORT%
if errorlevel 1 (
    echo [WARN] 等待超时，请查看 Qdrant 窗口的日志输出。
    exit /b 1
)

echo [INFO] Qdrant 已就绪，正在打开 Dashboard ...
start "" "%QDRANT_DASHBOARD_URL%"
echo [INFO] Dashboard：%QDRANT_DASHBOARD_URL%

endlocal
exit /b 0

rem ------------------------------------------------------------
rem 等待端口进入监听状态，最多 60 秒
rem ------------------------------------------------------------
:wait_port
set "WAIT_PORT=%~1"
powershell -NoProfile -Command "$i=0; while($i -lt 120){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%WAIT_PORT%); $c.Close(); exit 0 } catch { Start-Sleep -Milliseconds 500; $i++ } }; exit 1"
exit /b %ERRORLEVEL%
