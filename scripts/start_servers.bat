@echo off
setlocal

echo Starting Localization Server...
start cmd /k "title Localization Server && echo Starting Localization Server on port 11110... && java -cp bin server.server_location.ServerLocalization"

echo Waiting for Localization Server to initialize...
timeout /t 3 /nobreak > nul

echo Starting Proxy Server...
start cmd /k "title Proxy Server && echo Starting Proxy Server on port 11111... && java -cp bin server.server_proxy.ServerProxy"

echo Waiting for Proxy Server to initialize...
timeout /t 3 /nobreak > nul

echo Starting Client...
start cmd /k "title Client && echo Starting Client... && java -cp bin client.Client"
echo Waiting for Client to initialize...

echo All components started.
endlocal