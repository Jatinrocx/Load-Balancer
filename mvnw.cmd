@echo off
setlocal

set "MVN_DIR=%~dp0.mvn\apache-maven-3.9.6"
set "MVN_ZIP=%~dp0.mvn\maven.zip"
set "MVN_CMD=%MVN_DIR%\bin\mvn.cmd"

if exist "%MVN_CMD%" (
    goto RUN_MAVEN
)

echo [Setup] Downloading portable Maven 3.9.6...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip', '%MVN_ZIP%'); Expand-Archive -Path '%MVN_ZIP%' -DestinationPath '%~dp0.mvn\' -Force; Remove-Item -Path '%MVN_ZIP%' -Force"

if not exist "%MVN_CMD%" (
    echo [Error] Failed to setup portable Maven.
    exit /b 1
)

:RUN_MAVEN
"%MVN_CMD%" %*
