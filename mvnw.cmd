@REM ----------------------------------------------------------------------------
@REM Nexus Maven Wrapper for Windows
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "" @ECHO OFF
setlocal

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    mvn %*
    exit /b %ERRORLEVEL%
)

echo Error: Maven (mvn) binary not found on PATH. Please install Maven. 1>&2
exit /b 1
