#!/usr/bin/env bash
# Build MINDMAP//84 desktop app -> dist/MINDMAP84/MINDMAP84.exe
# Requires a JDK 17+ with jpackage on PATH (built/tested with Temurin JDK 25).
set -e
cd "$(dirname "$0")"

echo "Cleaning..."
rm -rf out build dist
mkdir -p out build

echo "Compiling..."
javac -d out src/mindmap84/*.java

echo "Running self-test..."
java -cp out mindmap84.Main --selftest

echo "Building jar..."
jar --create --file build/app.jar --main-class mindmap84.Main -C out .

echo "Packaging .exe (jpackage)..."
jpackage --type app-image --name MINDMAP84 --input build --main-jar app.jar \
  --main-class mindmap84.Main --dest dist --app-version 0.1.10 \
  --description "MINDMAP//84 mind map, checklist and datalist" --vendor "MINDMAP84" \
  --add-modules java.desktop,java.net.http,jdk.crypto.ec \
  --add-launcher Installer=packaging/installer.properties

echo ""
echo "Done -> dist/MINDMAP84/MINDMAP84.exe"
