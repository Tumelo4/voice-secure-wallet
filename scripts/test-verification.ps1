$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildDir = Join-Path $Root ".codex_tmp\verification-classes"
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

foreach ($testFile in $TestFiles) {
    $relative = $testFile.FullName -replace '^.*\\src\\test\\java\\', ''
    $className = ($relative -replace '\\', '.') -replace '\.java$', ''
    Write-Host "Running $className"
    java -cp $BuildDir $className
}
