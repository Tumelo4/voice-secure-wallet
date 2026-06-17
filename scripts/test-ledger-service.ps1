$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$BuildDir = Join-Path $Root ".codex_tmp\ledger-service-classes"
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$MainSources = Get-ChildItem -Path (Join-Path $Root "services\ledger-service\src\main\java") -Filter *.java -Recurse
$TestSources = Get-ChildItem -Path (Join-Path $Root "services\ledger-service\src\test\java") -Filter *.java -Recurse
$Sources = @($MainSources + $TestSources)

if ($Sources.Count -eq 0) {
    throw "No Java sources found for ledger-service"
}

javac -Xlint:all -d $BuildDir $Sources.FullName
java -cp $BuildDir com.voicesecure.ledger.LedgerServiceTests

