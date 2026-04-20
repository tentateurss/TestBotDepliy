#!/bin/bash
echo "Starting bot_test_salon_1776690514..."
cd "$(dirname "$0")"
if [ ! -d "target" ]; then
    echo "Building bot..."
    mvn clean package -DskipTests
fi
echo "Running bot..."
java -jar target/BotTG2-1.0.0.jar
