[CmdletBinding()]
param(
    [string]$OutputPath = "dist/cost-estimator-backend-eb.zip"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputPath))
$distRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot "dist"))

if (-not $resolvedOutput.StartsWith($distRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputPath must be inside the project's dist directory."
}

Push-Location $projectRoot
try {
    & .\mvnw.cmd clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

    $jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot "target") -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*.original" } |
        Select-Object -First 1
    if (-not $jar) { throw "Packaged Spring Boot jar was not found." }

    $stage = Join-Path $distRoot "eb-package"
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
    New-Item -ItemType Directory -Path $stage | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stage ".ebextensions") | Out-Null
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $stage "application.jar")
    Copy-Item -LiteralPath (Join-Path $projectRoot "Procfile") -Destination $stage
    Copy-Item -LiteralPath (Join-Path $projectRoot ".ebextensions\01-environment.config") -Destination (Join-Path $stage ".ebextensions")

    $outputDirectory = Split-Path -Parent $resolvedOutput
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    if (Test-Path -LiteralPath $resolvedOutput) { Remove-Item -LiteralPath $resolvedOutput -Force }
    # PowerShell Compress-Archive writes Windows backslashes into ZIP entry
    # names. Elastic Beanstalk extracts bundles on Linux and rejects those
    # entries, so use the JDK archiver which always emits portable '/' paths.
    & jar --create --file $resolvedOutput --no-manifest -C $stage .
    if ($LASTEXITCODE -ne 0) { throw "Could not create the deployment ZIP with the JDK jar tool." }
    Write-Host "Elastic Beanstalk bundle created: $resolvedOutput"
}
finally {
    Pop-Location
}
