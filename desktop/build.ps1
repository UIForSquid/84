# Build MINDMAP//84 desktop app -> dist\MINDMAP84\MINDMAP84.exe
# Requires a JDK 17+ with jpackage on PATH (built/tested with Temurin JDK 25).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "Cleaning..."
Remove-Item -Recurse -Force out, build, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out, build | Out-Null

Write-Host "Compiling..."
$srcs = (Get-ChildItem "src\mindmap84\*.java").FullName
javac -d out $srcs

Write-Host "Running self-test..."
java -cp out mindmap84.Main --selftest

Write-Host "Building jar..."
jar --create --file build\app.jar --main-class mindmap84.Main -C out .

Write-Host "Packaging .exe (jpackage)..."
jpackage --type app-image --name MINDMAP84 --input build --main-jar app.jar `
  --main-class mindmap84.Main --dest dist --app-version 1.0.0 `
  --description "MINDMAP//84 mind map, checklist and datalist" --vendor "MINDMAP84" `
  --add-modules java.desktop,java.net.http,jdk.crypto.ec `
  --add-launcher Installer=packaging\installer.properties

Write-Host ""
Write-Host "Done -> dist\MINDMAP84\MINDMAP84.exe"
