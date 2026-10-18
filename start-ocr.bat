@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem PaddleOCR 服务启动脚本（本地开发，Python 原生运行）
rem 直接双击运行，或在终端执行 start-ocr.bat
rem 需换目录时：set OCR_HOME=D:\ocr-service 后执行
rem 首次启动会下载 PaddleOCR 模型，耗时较长
rem ============================================================

if "%OCR_HOME%"=="" set "OCR_HOME=f:\project\ocr-service"
if "%OCR_PYTHON%"=="" set "OCR_PYTHON=C:\Users\29356\tools\python\python.exe"
if "%OCR_PORT%"=="" set "OCR_PORT=8100"
set "OCR_HEALTH_URL=http://127.0.0.1:%OCR_PORT%/health"

echo [INFO] OCR_HOME   = %OCR_HOME%
echo [INFO] OCR_PYTHON = %OCR_PYTHON%
echo [INFO] OCR_PORT   = %OCR_PORT%

if not exist "%OCR_PYTHON%" (
    echo [ERROR] 未找到 Python 解释器：%OCR_PYTHON%
    echo         可设置 OCR_PYTHON 指向实际安装路径
    exit /b 1
)

if not exist "%OCR_HOME%\main.py" (
    echo [ERROR] 未找到服务入口：%OCR_HOME%\main.py
    exit /b 1
)

netstat -ano | findstr "LISTENING" | findstr ":%OCR_PORT%" >nul
if not errorlevel 1 (
    echo [INFO] OCR 服务已在运行：%OCR_HEALTH_URL%
    exit /b 0
)

echo [INFO] 正在启动 OCR 服务（首次运行会下载模型，请耐心等待）...
start "PaddleOCR Service" /D "%OCR_HOME%" "%OCR_PYTHON%" main.py

echo [INFO] 等待 OCR 服务就绪 ...
call :wait_port %OCR_PORT%
if errorlevel 1 (
    echo [WARN] 等待超时，请查看 "PaddleOCR Service" 窗口的输出排查原因
    exit /b 1
)

echo [INFO] OCR 服务已就绪：%OCR_HEALTH_URL%

endlocal
exit /b 0

rem ------------------------------------------------------------
rem 等待端口进入监听状态，最多 120 次（每次 500ms）
rem ------------------------------------------------------------
:wait_port
set "WAIT_PORT=%~1"
powershell -NoProfile -Command "$i=0; while($i -lt 120){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%WAIT_PORT%); $c.Close(); exit 0 } catch { Start-Sleep -Milliseconds 500; $i++ } }; exit 1"
exit /b %ERRORLEVEL%
