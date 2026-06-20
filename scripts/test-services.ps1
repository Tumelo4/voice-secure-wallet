$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildDir = Join-Path $Root ".codex_tmp\service-classes"
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$ServiceRoot = Join-Path $Root "services"
$SourceFiles = Get-ChildItem -Path $ServiceRoot -Recurse -Filter *.java | Sort-Object FullName
if ($SourceFiles.Count -eq 0) {
    throw "No Java sources found under services"
}

javac -Xlint:all -d $BuildDir @($SourceFiles.FullName)

$TestFiles = $SourceFiles | Where-Object { $_.FullName -like "*\src\test\java\*Tests.java" }
if ($TestFiles.Count -eq 0) {
    throw "No service test classes found"
}

foreach ($testFile in $TestFiles) {
    $relative = $testFile.FullName -replace '^.*\\src\\test\\java\\', ''
    $className = ($relative -replace '\\', '.') -replace '\.java$', ''
    Write-Host "Running $className"
    java -cp $BuildDir $className
}

$PythonTestFiles = Get-ChildItem -Path $ServiceRoot -Recurse -Filter test_*.py | Sort-Object FullName
foreach ($pythonTest in $PythonTestFiles) {
    Write-Host "Running Python $($pythonTest.FullName)"
    Push-Location $pythonTest.Directory.FullName
    try {
        python $pythonTest.Name
    }
    finally {
        Pop-Location
    }
}
