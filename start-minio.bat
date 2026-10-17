@echo off
setlocal

rem ============================================================
rem MinIO 本地启动脚本（社区版，单机模式）
rem 用法：双击运行，或在终端执行 start-minio.bat
rem 如需换安装目录：set MINIO_HOME=D:\minio 后再执行
rem 启动完成后会自动打开 MinIO 控制台
rem ============================================================

if "%MINIO_HOME%"=="" set "MINIO_HOME=C:\Users\29356\tools\minio"
set "MINIO_API_PORT=9002"
set "MINIO_CONSOLE_PORT=9003"
set "MINIO_DATA=%MINIO_HOME%\data"
set "MINIO_CONSOLE_URL=http://127.0.0.1:%MINIO_CONSOLE_PORT%"
rem 默认账号密码，与 cloud-rag-service 的配置保持一致
set "MINIO_ROOT_USER=minioadmin"
set "MINIO_ROOT_PASSWORD=minioadmin"

echo [INFO] MINIO_HOME = %MINIO_HOME%

if not exist "%MINIO_HOME%\minio.exe" (
    echo [ERROR] 未找到 MinIO 可执行文件：%MINIO_HOME%\minio.exe
    echo         请确认 MINIO_HOME 指向 MinIO 解压目录。
    exit /b 1
)
if not exist "%MINIO_DATA%" mkdir "%MINIO_DATA%"

netstat -ano | findstr "LISTENING" | findstr ":%MINIO_API_PORT%" >nul
if not errorlevel 1 (
    echo [INFO] MinIO 已在运行，控制台：%MINIO_CONSOLE_URL%
    start "" "%MINIO_CONSOLE_URL%"
    exit /b 0
)

echo [INFO] 正在启动 MinIO（API 端口 %MINIO_API_PORT%，控制台端口 %MINIO_CONSOLE_PORT%）...
start "MinIO" "%MINIO_HOME%\minio.exe" server "%MINIO_DATA%" --address :%MINIO_API_PORT% --console-address :%MINIO_CONSOLE_PORT%

echo [INFO] 等待 MinIO 控制台就绪 ...
call :wait_port %MINIO_CONSOLE_PORT%
if errorlevel 1 (
    echo [WARN] 等待超时，请查看 MinIO 窗口的日志输出。
    exit /b 1
)

echo [INFO] MinIO 已就绪，正在打开控制台 ...
start "" "%MINIO_CONSOLE_URL%"
echo [INFO] 控制台：%MINIO_CONSOLE_URL%  账号：%MINIO_ROOT_USER%  密码：%MINIO_ROOT_PASSWORD%

endlocal
exit /b 0

rem ------------------------------------------------------------
rem 等待端口进入监听状态，最多 60 秒
rem ------------------------------------------------------------
:wait_port
set "WAIT_PORT=%~1"
powershell -NoProfile -Command "$i=0; while($i -lt 120){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%WAIT_PORT%); $c.Close(); exit 0 } catch { Start-Sleep -Milliseconds 500; $i++ } }; exit 1"
exit /b %ERRORLEVEL%
