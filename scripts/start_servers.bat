@echo off

:: Start Localization Server
start cmd /k "java -cp bin server.location_server.ServerLocalization"

:: Start Proxy Server
start cmd /k "java -cp bin server.proxy_server.ServerProxy"

:: Start Client
start cmd /k "java -cp bin client.Client"