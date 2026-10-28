[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptDirectory = $PSScriptRoot
. (Join-Path $scriptDirectory 'release.ps1') -LibraryOnly
$testProject = Split-Path $scriptDirectory -Parent
$testDirectory = Join-Path $testProject ('.build/release-tests-' + [guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $testDirectory -Force)
$script:checks = 0

function Check {
    param([bool]$Condition, [string]$Description)
    $script:checks++
    if (-not $Condition) { throw "FAIL: $Description" }
}

function Rejects {
    param([scriptblock]$Action, [string]$Description)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Check $rejected $Description
}

$versionFile = Join-Path $testDirectory 'version.properties'
Write-Utf8File $versionFile "# Versione del test`r`n`r`nVERSION_CODE=3`r`nVERSION_NAME=1.1.1`r`n"
$initial = Read-AppVersion $versionFile
Check ($initial.Code -eq 3 -and $initial.Name -ceq '1.1.1') 'read the current version'
$next = Get-NextAppVersion $initial
Check ($next.Code -eq 4 -and $next.Name -ceq '1.1.2') 'increment both patch and versionCode'
Write-AppVersion $versionFile $initial $next
$saved = Read-AppVersion $versionFile
Check ($saved.Code -eq 4 -and $saved.Name -ceq '1.1.2') 'persist the incremented version'
Check ([IO.File]::ReadAllText($versionFile).StartsWith("# Versione del test`r`n`r`n")) 'preserve comments and blank lines'
Check ([IO.File]::ReadAllBytes($versionFile)[0] -ne 239) 'write without UTF8 BOM'
Rejects { Write-AppVersion $versionFile $initial $next } 'never overwrite a concurrent version change'

$bomVersion = [char]0xFEFF + "VERSION_CODE=5`r`nVERSION_NAME=1.2.3`r`n"
Write-Utf8File $versionFile $bomVersion
$bomCurrent = Read-AppVersion $versionFile
Write-AppVersion $versionFile $bomCurrent (Get-NextAppVersion $bomCurrent)
Check ((Read-AppVersion $versionFile).Code -eq 6) 'handle a Notepad BOM on the first property'

foreach ($invalid in @('1.1', 'v1.1.1', '1.01.1', '1.0.0-beta', '-1.0.0', '1.1.1;echo bad', '1.1.2147483648')) {
    Rejects { Get-SemanticVersion $invalid } "reject invalid version $invalid"
}
Check ((Compare-SemanticVersions '1.10.0' '1.9.99') -eq 1) 'compare numbers instead of strings'
Check ((Compare-SemanticVersions '2.0.0' '2.0.0') -eq 0) 'compare equal versions'
Check ((Compare-SemanticVersions '1.2.3' '2.0.0') -eq -1) 'compare older versions'
Rejects { Get-NextAppVersion ([pscustomobject]@{Code=2100000000;Name='1.2.3'}) } 'guard the Android versionCode limit'
Rejects { Get-NextAppVersion ([pscustomobject]@{Code=1;Name='1.2.2147483647'}) } 'guard patch overflow'
foreach ($text in @('VERSION_CODE=0', 'VERSION_CODE=-1', 'VERSION_CODE=2100000001', 'VERSION_CODE=abc')) {
    Write-Utf8File $versionFile ($text + "`nVERSION_NAME=1.1.1`n")
    Rejects { Read-AppVersion $versionFile } 'reject invalid versionCode'
}
Write-Utf8File $versionFile "VERSION_CODE=3`nVERSION_CODE=4`nVERSION_NAME=1.1.1`n"
Rejects { Read-AppVersion $versionFile } 'reject duplicate version properties'

foreach ($remote in @('https://github.com/carellice/Topperia.git', 'https://github.com/carellice/Topperia', 'git@github.com:carellice/Topperia.git', 'ssh://git@github.com/carellice/Topperia.git', 'https://github.com/carellice/Topperia/')) {
    Check ((Get-RepositoryFromRemote $remote) -ceq 'carellice/Topperia') "recognize safe remote $remote"
}
foreach ($remote in @('https://github.com.evil.test/carellice/Topperia', 'https://token@github.com/carellice/Topperia', 'https://gitlab.com/carellice/Topperia', 'file:///C:/private', 'https://github.com/a/b/c', 'git@github.com:carellice/../../oops')) {
    Rejects { Get-RepositoryFromRemote $remote } 'reject a foreign, credentialed, or malformed remote'
}
Rejects { Get-RepositoryIdentity '-option' 'Topperia' } 'reject option injection in repository owner'
Rejects { Get-RepositoryIdentity 'carellice' '../private' } 'reject traversal in repository name'

$inside = Join-Path $testDirectory 'output/releases/v1.1.2/test.apk'
Check ((Assert-PathUnderRoot $testDirectory $inside) -ceq [IO.Path]::GetFullPath($inside)) 'accept a generated path below the root'
Rejects { Assert-PathUnderRoot $testDirectory (Join-Path $testDirectory '../outside.apk') } 'reject path traversal'
Rejects { Assert-PathUnderRoot $testDirectory ($testDirectory + '-other/output.apk') } 'reject sibling prefixes'

Assert-NoTrackedSecrets @('README.md', 'app/src/main/AndroidManifest.xml', 'signing.properties.example')
Check $true 'allow public source and signing templates'
foreach ($private in @('signing.properties', 'local.properties', '.env', '.env.production', 'signing/topperia.keystore', 'nested/release.JKS', 'certificates/key.pfx', 'signing/password.txt', '.secrets/token.txt', 'private.pem', 'nested/private.key')) {
    Rejects { Assert-NoTrackedSecrets @('README.md', $private) } 'reject tracked signing and credential files'
}
Assert-CleanReleaseTree @()
Assert-CleanReleaseTree @(' M version.properties') $true
Assert-CleanReleaseTree @('M  version.properties') $true
Assert-CleanReleaseTree @('MM version.properties') $true
Check $true 'resume allows only the intended version change, staged or unstaged'
Rejects { Assert-CleanReleaseTree @(' M app/build.gradle') $true } 'never add other source edits automatically'
Rejects { Assert-CleanReleaseTree @('?? signing.properties') } 'reject untracked files before release'
Rejects { Assert-CleanReleaseTree @(' M version.properties') } 'normal releases begin with a clean tree'

$asset = Join-Path $testDirectory 'Topperia-1.1.2.apk'
[IO.File]::WriteAllBytes($asset, [byte[]](1,2,3,4,5))
$manifest = New-UpdateManifest $asset ([pscustomobject]@{Code=4;Name='1.1.2'})
Check ($manifest.schemaVersion -eq 1 -and $manifest.versionCode -eq 4 -and $manifest.versionName -ceq '1.1.2') 'manifest carries the exact version'
Check ($manifest.packageName -ceq 'it.topperia.game' -and $manifest.tag -ceq 'v1.1.2') 'manifest identifies this package and tag'
Check ($manifest.apkAssetName -ceq 'Topperia-1.1.2.apk' -and $manifest.sizeBytes -eq 5) 'manifest identifies the exact APK asset'
Check ($manifest.sha256 -ceq '74f81fe167d99b4cb41d6d0ccda82278caee9f3e2f25d5e5a3936ff3dcec60d0') 'manifest hashes the actual APK bytes'
Check (-not $manifest.Contains('apkUrl')) 'manifest cannot inject a download URL'
Rejects { New-UpdateManifest $asset ([pscustomobject]@{Code=5;Name='1.1.3'}) } 'reject an APK filename/version mismatch'
$release = [pscustomobject]@{assets=@([pscustomobject]@{name='Topperia-1.1.2.apk';size=5})}
Assert-ReleaseAssets $release @($asset)
Check $true 'accept the exact asset and byte count'
Rejects { Assert-ReleaseAssets ([pscustomobject]@{assets=@()}) @($asset) } 'reject a missing remote asset'
Rejects { Assert-ReleaseAssets ([pscustomobject]@{assets=@([pscustomobject]@{name='Topperia-1.1.2.apk';size=4})}) @($asset) } 'reject a truncated remote asset'

function Invoke-MockedReleaseLookup {
    param([object[]]$Responses, [string[]]$ExpectedCalls)
    $calls = New-Object 'Collections.Generic.List[string]'
    function Invoke-ReleaseCommand {
        param([string]$FilePath, [string[]]$Arguments, [switch]$AllowFailure, [switch]$ShowOutput)
        $index = $calls.Count
        [void]$calls.Add(($FilePath + '|' + ($Arguments -join '|')))
        if ($index -ge $Responses.Count) { throw 'Unexpected additional GitHub request in test.' }
        return $Responses[$index]
    }
    $value = $null
    $failure = $null
    try { $value = Get-ReleaseJson 'mock-gh' 'carellice/Topperia' 'v1.1.2' } catch { $failure = $_.Exception }
    Check ($calls.Count -eq $ExpectedCalls.Count) 'release lookup performs only the expected requests'
    for ($i = 0; $i -lt $ExpectedCalls.Count; $i++) {
        Check ($calls[$i] -ceq ('mock-gh|' + $ExpectedCalls[$i])) 'release lookup uses the exact repository, tag, and numeric ID'
    }
    return [pscustomobject]@{ Value = $value; Failure = $failure }
}

$tagRequest = 'api|repos/carellice/Topperia/releases/tags/v1.1.2'
$identityRequest = 'release|view|v1.1.2|--repo|carellice/Topperia|--json|databaseId,tagName'
$idRequest = 'api|repos/carellice/Topperia/releases/426811034'
$notFound = [pscustomobject]@{ExitCode=1;Text='gh: Not Found (HTTP 404)'}
$identity = [pscustomobject]@{ExitCode=0;Text='{"databaseId":426811034,"tagName":"v1.1.2"}'}
$publicResponse = [pscustomobject]@{ExitCode=0;Text='{"id":426811034,"tag_name":"v1.1.2","draft":false,"prerelease":false,"assets":[{"id":100,"name":"update.json","size":301}]}'}
$draftResponse = [pscustomobject]@{ExitCode=0;Text='{"id":426811034,"tag_name":"v1.1.2","draft":true,"prerelease":false,"assets":[{"id":101,"name":"Topperia-1.1.2.apk","size":12345}]}'}
$lookup = Invoke-MockedReleaseLookup @($publicResponse) @($tagRequest)
Check ($null -eq $lookup.Failure -and -not $lookup.Value.draft -and $lookup.Value.assets[0].name -ceq 'update.json') 'read a public release without a fallback or REST field conversion'
$lookup = Invoke-MockedReleaseLookup @($notFound, $identity, $draftResponse) @($tagRequest, $identityRequest, $idRequest)
Check ($null -eq $lookup.Failure -and $lookup.Value.draft -and $lookup.Value.tag_name -ceq 'v1.1.2') 'resolve a draft omitted by the REST tag endpoint'
Check ($lookup.Value.id -eq 426811034 -and $lookup.Value.assets[0].id -eq 101 -and $lookup.Value.assets[0].size -eq 12345) 'preserve draft REST identity and asset metadata for verification and publication'
$lookup = Invoke-MockedReleaseLookup @($notFound, [pscustomobject]@{ExitCode=1;Text='release not found'}) @($tagRequest, $identityRequest)
Check ($null -eq $lookup.Failure -and $null -eq $lookup.Value) 'return null only after GitHub CLI also confirms an absent release'
$lookup = Invoke-MockedReleaseLookup @([pscustomobject]@{ExitCode=1;Text='gh: Forbidden (HTTP 403)'}) @($tagRequest)
Check ($null -ne $lookup.Failure) 'do not mask a REST permission failure as a missing release'
$lookup = Invoke-MockedReleaseLookup @($notFound, [pscustomobject]@{ExitCode=1;Text='gh: Bad credentials (HTTP 401)'}) @($tagRequest, $identityRequest)
Check ($null -ne $lookup.Failure) 'do not mask a fallback authentication failure as a missing release'
foreach ($invalidIdentity in @('{"databaseId":0,"tagName":"v1.1.2"}', '{"databaseId":"../latest","tagName":"v1.1.2"}', '{"databaseId":426811034,"tagName":"v1.1.3"}', '{"databaseId":"9223372036854775808","tagName":"v1.1.2"}', '{"tagName":"v1.1.2"}')) {
    $lookup = Invoke-MockedReleaseLookup @($notFound, [pscustomobject]@{ExitCode=0;Text=$invalidIdentity}) @($tagRequest, $identityRequest)
    Check ($null -ne $lookup.Failure) 'reject a malformed or mismatched fallback identity before requesting an ID endpoint'
}
$lookup = Invoke-MockedReleaseLookup @($notFound, $identity, $notFound) @($tagRequest, $identityRequest, $idRequest)
Check ($null -ne $lookup.Failure) 'fail safely if a resolved draft cannot be read by its ID'
$lookup = Invoke-MockedReleaseLookup @($notFound, $identity, [pscustomobject]@{ExitCode=0;Text='{"tag_name":"v1.1.3","draft":true}'}) @($tagRequest, $identityRequest, $idRequest)
Check ($null -ne $lookup.Failure) 'reject a REST release belonging to another tag'

$stateFile = Join-Path $testDirectory 'release-state.json'
$state = [pscustomobject]@{schemaVersion=1;stage='prepared';versionName='1.1.2';versionCode=4;repository='carellice/Topperia'}
Save-ReleaseState $stateFile $state
Check ((Read-ReleaseState $stateFile).versionCode -eq 4) 'save and restore recovery state atomically'
Write-Utf8File $stateFile '{"schemaVersion":9,"stage":"prepared","versionName":"1.1.2","versionCode":4}'
Rejects { Read-ReleaseState $stateFile } 'reject a future state format'

$powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$failed = Invoke-ReleaseCommand $powershell @('-NoProfile','-Command','exit 13') -AllowFailure
Check ($failed.ExitCode -eq 13) 'capture external failure exit codes'
Rejects { Invoke-ReleaseCommand $powershell @('-NoProfile','-Command','exit 12') } 'do not continue after external failure'

$fixture = Join-Path $testDirectory 'project with spaces'
[void](New-Item -ItemType Directory -Path $fixture -Force)
$fixtureVersion = Join-Path $fixture 'version.properties'
Write-Utf8File $fixtureVersion "VERSION_CODE=3`nVERSION_NAME=1.1.1`n"
Write-Utf8File (Join-Path $fixture 'release.properties') ("GITHUB_OWNER=carellice`nGITHUB_REPO=Topperia`nSIGNING_CERT_SHA256=" + ('a' * 64) + "`n")
$before = (Get-FileHash -LiteralPath $fixtureVersion).Hash
$plan = Invoke-ReleaseCommand $powershell @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $scriptDirectory 'release.ps1'),'-DryRun','-ProjectRoot',$fixture)
$planJson = $plan.Text | ConvertFrom-Json
Check ($planJson.dryRun -and $planJson.nextVersionCode -eq 4 -and $planJson.nextVersion -ceq '1.1.2') 'execute a real PowerShell5.1 dry run from a path with spaces'
Check ((Get-FileHash -LiteralPath $fixtureVersion).Hash -ceq $before) 'dry run leaves the version file unchanged'
Check (-not (Test-Path -LiteralPath (Join-Path $fixture 'output'))) 'dry run creates no output, state, lock, Git repository, or artifacts'

Write-Host ("PASS: {0} release pipeline assertions" -f $script:checks) -ForegroundColor Green
