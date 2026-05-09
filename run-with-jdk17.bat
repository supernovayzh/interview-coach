@echo off
REM Project-level launcher using local JDK17 (adjust path if needed)
SET "JAVA_HOME=D:\tools\jdk-17"
SET "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
java -version
cd /d "%~dp0"
echo Building project (skip tests)...
REM Use full path to mvn to avoid PATH issues
SET "MVN_CMD=C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.9.15\\bin\\mvn.cmd"
IF NOT EXIST "%MVN_CMD%" (
	echo Maven not found at %MVN_CMD%. Ensure Maven is installed or adjust MVN_CMD.
	exit /b 1
)
call "%MVN_CMD%" -DskipTests -e clean package
echo Starting Spring Boot app...
call "%MVN_CMD%" -DskipTests spring-boot:run
