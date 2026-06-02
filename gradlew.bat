@echo off
rem Nexus Agent Gradle Wrapper
rem Downloads and runs Gradle if not yet installed.

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set GRADLE_HOME=%DIRNAME%gradle\wrapper

java -jar "%GRADLE_HOME%\gradle-wrapper.jar" %*
