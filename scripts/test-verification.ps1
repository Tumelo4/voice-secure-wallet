$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildRoot = Join-Path $Root ".codex_tmp"
$BuildDir = Join-Path $BuildRoot "verification-classes"
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$ServiceRoot = Join-Path $Root "services"
$TestRoot = Join-Path $Root "tests"
$SourceFiles = Get-ChildItem -Path @($ServiceRoot, $TestRoot) -Recurse -Filter *.java | Sort-Object FullName
if ($SourceFiles.Count -eq 0) {
    throw "No verification Java sources found under services or tests"
}

javac -Xlint:all -d $BuildDir @($SourceFiles.FullName)

$TestFiles = Get-ChildItem -Path $TestRoot -Recurse -Filter *Tests.java | Sort-Object FullName
if ($TestFiles.Count -eq 0) {
    throw "No verification test classes found under tests"
}

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

foreach ($testFile in $TestFiles) {
    $className = Get-JavaTestClassName -File $testFile
    Write-Host "Running $className"
    java -cp $BuildDir $className
}

$Terraform = Get-Command terraform -ErrorAction SilentlyContinue
if ($Bash -and $Terraform) {
    Write-Host "Running Terraform AWS baseline validation"
    & bash (Join-Path $PSScriptRoot "test-terraform-aws-baseline.sh")
}
