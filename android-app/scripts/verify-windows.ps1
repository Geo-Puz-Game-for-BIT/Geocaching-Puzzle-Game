[CmdletBinding()]
param()

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryRoot = (Resolve-Path (Join-Path $projectRoot '..')).Path
$mappedDrive = $null
$cacheDrive = $null
$originalGradleUserHome = $env:GRADLE_USER_HOME
$originalLocation = Get-Location

try {
    $buildRoot = $projectRoot
    if ($projectRoot -match '[^\x00-\x7F]') {
        $mappedDrive = @('W', 'V', 'U', 'T') |
            Where-Object { -not (Test-Path "${_}:\") } |
            Select-Object -First 1
        if (-not $mappedDrive) {
            throw 'No free drive letter is available for the ASCII path workaround.'
        }
        & subst.exe "${mappedDrive}:" $repositoryRoot
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to create the temporary drive mapping.'
        }
        $buildRoot = "${mappedDrive}:\android-app"
    }

    # The test worker classpath also uses GRADLE_USER_HOME, independently of the project path.
    $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    if ($cacheRoot -match '[^\x00-\x7F]') {
        New-Item -ItemType Directory -Path $cacheRoot -Force | Out-Null
        $cacheDrive = @('S', 'R', 'Q', 'P') |
            Where-Object { -not (Test-Path "${_}:\") } |
            Select-Object -First 1
        if (-not $cacheDrive) { throw 'No free drive letter is available for the Gradle cache.' }
        & subst.exe "${cacheDrive}:" (Resolve-Path $cacheRoot).Path
        if ($LASTEXITCODE -ne 0) { throw 'Failed to map the Gradle cache.' }
        $env:GRADLE_USER_HOME = "${cacheDrive}:\"
    }

    Set-Location $buildRoot
    # A single-use daemon releases the temporary paths when this build ends.
    & .\gradlew.bat --no-daemon clean testDebugUnitTest assembleDebug lintDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Android verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Set-Location $originalLocation
    $env:GRADLE_USER_HOME = $originalGradleUserHome
    if ($cacheDrive) {
        & subst.exe "${cacheDrive}:" /D
    }
    if ($mappedDrive) {
        & subst.exe "${mappedDrive}:" /D
    }
}
