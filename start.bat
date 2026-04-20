@echo off
echo Starting bot_test_salon_1776690514...
cd /d "%~dp0"
if not exist "target" (
    echo Building bot...
    call mvn clean package -DskipTests
)
echo Running bot...
java -jar target\BotTG2-1.0.0.jar
pause
