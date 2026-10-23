@echo off
setlocal
set "JDK=C:\Users\Rene\Documents\claude\tools\jdk25\jdk-25.0.4.1+1"
set "GRADLE=C:\Users\Rene\Documents\claude\tools\gradle\gradle-9.7.0\bin\gradle.bat"
set "JAVA_HOME=%JDK%"
call "%GRADLE%" build --no-daemon -p "%~dp0." -Dorg.gradle.java.home="%JDK%" -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT
if errorlevel 1 (
    echo BUILD FEHLGESCHLAGEN
    exit /b 1
)
echo Fertig: %~dp0build\libs\CelestialWorld.jar
endlocal
