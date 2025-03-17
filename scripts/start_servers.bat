@echo off
setlocal

:: Defina o diretório base do projeto onde as classes estão localizadas (ajuste conforme necessário)
set PROJECT_DIR=D:\Dev\Projects\distributed-system-cfwos
set LANTERNA_JAR=%PROJECT_DIR%\lib\lanterna-3.1.1.jar 
set CLASS_PATH=%PROJECT_DIR%\bin;%LANTERNA_JAR%

:: Inicie o servidor de localização no diretório correto
@REM echo Starting Localization Server...
@REM start wt cmd /k "cd /d %PROJECT_DIR% && title Localization Server && echo Starting Localization Server on port 11110... && java -cp bin main.server.localization.LocalizationServer"

@REM echo Waiting for Localization Server to initialize...
@REM timeout /t 1 /nobreak > nul

@REM :: Inicie o servidor proxy no diretório correto
@REM echo Starting Proxy Server...
@REM start wt cmd /k "cd /d %PROJECT_DIR% && title Proxy Server && echo Starting Proxy Server on port 22220... && java -cp bin main.server.proxy.ProxyServer"

@REM echo Waiting for Proxy Server to initialize...
@REM timeout /t 1 /nobreak > nul

@REM : Inicie o servidor de Aplicação no diretório correto
@REM echo Starting Application Server...
@REM start wt cmd /k "cd /d %PROJECT_DIR% && title Application Server && echo Starting Application Server on port 33330... && java -cp bin main.server.application.ApplicationServer"

@REM echo Waiting for Localization Server to initialize...
@REM timeout /t 1 /nobreak > nul

:: Inicie o cliente no diretório correto
echo Starting Client...
start wt cmd /k "cd /d %PROJECT_DIR% && javaw -cp %CLASS_PATH% main.client.Client"

::echo Waiting for Client to initialize...

echo All components started.
endlocal
