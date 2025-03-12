@echo off
setlocal

:: Defina o diretório base do projeto onde as classes estão localizadas (ajuste conforme necessário)
set PROJECT_DIR=C:\Users\NEaD_TI\Documents\GitHub\distributed-system-cfwos\

:: Inicie o servidor de localização no diretório correto
echo Starting Localization Server...
start wt cmd /k "cd /d %PROJECT_DIR% && title Localization Server && echo Starting Localization Server on port 11110... && java -cp bin server.localization.LocalizationServer"

echo Waiting for Localization Server to initialize...
timeout /t 1 /nobreak > nul

:: Inicie o servidor proxy no diretório correto
::echo Starting Proxy Server...
::start wt cmd /k "cd /d %PROJECT_DIR% && title Proxy Server && echo Starting Proxy Server on port 11111... && java -cp bin server.server_proxy.ServerProxy"

echo Waiting for Proxy Server to initialize...
timeout /t 1 /nobreak > nul

:: Inicie o cliente no diretório correto
echo Starting Client...
start wt cmd /k "cd /d %PROJECT_DIR% && title Client && echo Starting Client... && java -cp bin client.Client"

echo Waiting for Client to initialize...

echo All components started.
endlocal
