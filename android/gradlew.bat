@rem
@rem Gradle startup script for Windows (standard Gradle wrapper launcher).
@rem If gradle\wrapper\gradle-wrapper.jar is missing, open this project in
@rem Android Studio to regenerate it automatically, or run "gradle wrapper"
@rem once using a local Gradle installation.
@rem

@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
    echo ERROR: gradle\wrapper\gradle-wrapper.jar not found.
    echo Open this project in Android Studio ^(it will regenerate the wrapper jar automatically^),
    echo or run "gradle wrapper" once using a local Gradle installation.
    exit /b 1
)

"java" -Dorg.gradle.appname=%~n0 -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
