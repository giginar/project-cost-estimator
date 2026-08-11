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
    # Compress-Archive/ZipFile preserve Windows backslashes, while the JDK jar
    # tool can make Beanstalk treat the entire bundle as the application JAR.
    # Create every ZIP entry explicitly with a portable '/' path and no
    # META-INF manifest.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zipStream = [System.IO.File]::Open($resolvedOutput, [System.IO.FileMode]::CreateNew)
    $zipArchive = [System.IO.Compression.ZipArchive]::new(
        $zipStream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        foreach ($file in Get-ChildItem -LiteralPath $stage -Recurse -File) {
            $entryName = $file.FullName.Substring($stage.Length).TrimStart('\', '/').Replace('\', '/')
            $entry = $zipArchive.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
            $entryStream = $entry.Open()
            $inputStream = [System.IO.File]::OpenRead($file.FullName)
            try {
                $inputStream.CopyTo($entryStream)
            }
            finally {
                $inputStream.Dispose()
                $entryStream.Dispose()
            }
        }
    }
    finally {
        $zipArchive.Dispose()
        $zipStream.Dispose()
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedOutput)
    try {
        $entryNames = @($archive.Entries | ForEach-Object FullName)
        if ($entryNames | Where-Object { $_.Contains('\') }) {
            throw "Deployment ZIP contains Windows path separators."
        }
        if ($entryNames | Where-Object { $_ -like 'META-INF/*' }) {
            throw "Deployment ZIP contains a JAR manifest and may be misdetected by Beanstalk."
        }
        foreach ($requiredEntry in @('application.jar', 'Procfile', '.ebextensions/01-environment.config')) {
            if ($entryNames -notcontains $requiredEntry) {
                throw "Deployment ZIP is missing $requiredEntry."
            }
        }
    }
    finally {
        $archive.Dispose()
    }

    & jar --list --file (Join-Path $stage "application.jar") | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "application.jar is not a valid Java archive." }
    Write-Host "Elastic Beanstalk bundle created: $resolvedOutput"
}
finally {
    Pop-Location
}
