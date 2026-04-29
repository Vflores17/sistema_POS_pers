@ECHO OFF
SETLOCAL

SET "BASE_DIR=%~dp0"
IF "%BASE_DIR:~-1%"=="\" SET "BASE_DIR=%BASE_DIR:~0,-1%"
SET "WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper"
SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
SET "WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties"

IF NOT EXIST "%WRAPPER_PROPS%" (
  ECHO Missing "%WRAPPER_PROPS%"
  EXIT /B 1
)

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Downloading Maven wrapper jar from: https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 (
    ECHO Failed to download Maven wrapper jar.
    EXIT /B 1
  )
)

IF DEFINED JAVA_HOME (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java"
)

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
IF ERRORLEVEL 1 EXIT /B 1

ENDLOCAL
