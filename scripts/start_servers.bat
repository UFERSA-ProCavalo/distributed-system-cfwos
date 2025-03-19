@echo off
setlocal

:: Define o diretório do projeto onde as classes estão localizadas (ajuste conforme necessário)
set PROJECT_DIR=D:\Dev\Projects\distributed-system-cfwos
set LANTERNA_JAR=%PROJECT_DIR%\lib\lanterna-3.1.1.jar 
set CLASS_PATH=%PROJECT_DIR%\bin;%LANTERNA_JAR%

:: Inicia o servidor de localização
echo Iniciando Servidor de Localização...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor de Localização && echo Iniciando Servidor de Localização na porta 11110... && java -cp bin main.server.localization.LocalizationServer"

echo Aguardando inicialização do Servidor de Localização...
timeout /t 2 /nobreak > nul

:: Inicia o primeiro servidor proxy
echo Iniciando Servidor Proxy 1...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor Proxy 1 && echo Iniciando Servidor Proxy 1 na porta 22220... && java -cp bin main.server.proxy.ProxyServer -sp1"

echo Aguardando inicialização do Servidor Proxy 1...
timeout /t 2 /nobreak > nul

:: Inicia o segundo servidor proxy
echo Iniciando Servidor Proxy 2...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor Proxy 2 && echo Iniciando Servidor Proxy 2 na porta 22221... && java -cp bin main.server.proxy.ProxyServer -sp2"

echo Aguardando inicialização do Servidor Proxy 2...
timeout /t 2 /nobreak > nul

:: Inicia o terceiro servidor proxy
echo Iniciando Servidor Proxy 3...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor Proxy 3 && echo Iniciando Servidor Proxy 3 na porta 22222... && java -cp bin main.server.proxy.ProxyServer -sp3"

echo Aguardando inicialização do Servidor Proxy 3...
timeout /t 2 /nobreak > nul

:: Inicia o servidor de aplicação como PRIMÁRIO
echo Iniciando Servidor de Aplicação PRIMÁRIO...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor de Aplicação && echo Iniciando Servidor de Aplicação na porta 33330... && java -cp bin main.server.application.ApplicationServer -prim"

echo Aguardando inicialização do Servidor de Aplicação...
timeout /t 2 /nobreak > nul

:: Inicia o servidor de aplicação como BACKUP
echo Iniciando Servidor de Aplicação BACKUP...
start wt cmd /k "cd /d %PROJECT_DIR% && title Servidor de Aplicação && echo Iniciando Servidor de Aplicação na porta 33330... && java -cp bin main.server.application.ApplicationServer -back"

echo Aguardando inicialização do Servidor de Aplicação...
timeout /t 2 /nobreak > nul

:: Inicia o cliente
::echo Iniciando Cliente...
::start wt cmd /k "cd /d %PROJECT_DIR% && javaw -cp %CLASS_PATH% main.client.Client"

echo Todos os componentes foram iniciados.
endlocal
