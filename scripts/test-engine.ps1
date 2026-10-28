$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$classesPath = Join-Path $projectRoot '.build/engine-tests'
New-Item -ItemType Directory -Force -Path $classesPath | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/main/java/it/topperia/game/engine') -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/main/java/it/topperia/game/data') -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $projectRoot 'tests/EngineTest.java'
$sources += Join-Path $projectRoot 'tests/RankingPickerTest.java'
$sources += Join-Path $projectRoot 'app/src/main/java/it/topperia/game/update/UpdatePolicy.java'
$sources += Join-Path $projectRoot 'tests/UpdatePolicyTest.java'
& javac -encoding UTF-8 --release 17 -d $classesPath @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilazione dei test fallita.' }
& java -ea -cp $classesPath EngineTest
if ($LASTEXITCODE -ne 0) { throw 'Verifica del motore fallita.' }
& java -ea -cp $classesPath RankingPickerTest
if ($LASTEXITCODE -ne 0) { throw 'Verifica della selezione casuale fallita.' }
& java -ea -cp $classesPath UpdatePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Verifica della sicurezza degli aggiornamenti fallita.' }
