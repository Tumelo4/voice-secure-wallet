$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildRoot = Join-Path $Root ".codex_tmp"
$BuildDir = Join-Path $BuildRoot "service-classes"
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$ServiceRoot = Join-Path $Root "services"
$SourceFiles = Get-ChildItem -Path $ServiceRoot -Recurse -Filter *.java | Sort-Object FullName
if ($SourceFiles.Count -eq 0) {
    throw "No Java sources found under services"
}

javac -Xlint:all -d $BuildDir @($SourceFiles.FullName)

function Get-JavaTestClassName {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo] $File
    )

    $parts = $File.FullName -split '[\\/]+'
    for ($index = 0; $index -le $parts.Length - 4; $index++) {
        if ($parts[$index] -eq 'src' -and $parts[$index + 1] -eq 'test' -and $parts[$index + 2] -eq 'java') {
            $classParts = @($parts[($index + 3)..($parts.Length - 1)])
            $classParts[$classParts.Length - 1] = $classParts[$classParts.Length - 1] -replace '\.java$', ''
            return ($classParts -join '.')
        }
    }

    throw "Unexpected Java test path: $($File.FullName)"
}

$TestFiles = $SourceFiles | Where-Object { $_.FullName -match '(^|[\\/])src[\\/]+test[\\/]+java[\\/].*Tests\.java$' }
if ($TestFiles.Count -eq 0) {
    throw "No service test classes found"
}

foreach ($testFile in $TestFiles) {
    $className = Get-JavaTestClassName -File $testFile
    Write-Host "Running $className"
    java -cp $BuildDir $className
}

$PythonProjects = Get-ChildItem -Path $ServiceRoot -Recurse -Filter pyproject.toml | Sort-Object FullName
foreach ($pythonProject in $PythonProjects) {
    Write-Host "Running pytest for $($pythonProject.Directory.FullName)"
    Push-Location $pythonProject.Directory.FullName
    try {
        python -m pytest
    }
    finally {
        Pop-Location
    }
}

$Bash = Get-Command bash -ErrorAction SilentlyContinue
$Brew = Get-Command brew -ErrorAction SilentlyContinue
$PostgresInstalled = $false
if ($Brew) {
    $PostgresInstalled = [bool](& brew list --formula --versions postgresql@16 2>$null)
}

if ($Bash -and $Brew -and $PostgresInstalled) {
    Write-Host "Running PostgreSQL migration smoke test"
    & bash (Join-Path $PSScriptRoot "test-postgres-migrations.sh")
}
