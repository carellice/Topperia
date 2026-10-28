[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$Resume,
    [string]$NotesFile,
    [switch]$LibraryOnly,
    [string]$ProjectRoot
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = Split-Path -Parent $PSScriptRoot }

function Write-Utf8File {
    param([string]$Path, [string]$Text)
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}

function Read-ReleaseProperties {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "File mancante: $Path" }
    $properties = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $trimmed = $line.Trim().TrimStart([char]0xFEFF)
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        if ($trimmed -notmatch '^([A-Za-z][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            throw "Proprieta non valida in $Path. Usa CHIAVE=valore."
        }
        $key = $Matches[1]
        if ($properties.ContainsKey($key)) { throw "Proprieta duplicata: $key" }
        $properties[$key] = $Matches[2]
    }
    return $properties
}

function Read-AppVersion {
    param([string]$Path)
    $properties = Read-ReleaseProperties $Path
    if (-not $properties.ContainsKey('VERSION_CODE') -or -not $properties.ContainsKey('VERSION_NAME')) {
        throw 'version.properties deve contenere VERSION_CODE e VERSION_NAME.'
    }
    if ($properties.VERSION_CODE -notmatch '^[1-9][0-9]*$') { throw 'VERSION_CODE deve essere un intero positivo.' }
    $code = [long]$properties.VERSION_CODE
    if ($code -gt 2100000000) { throw 'VERSION_CODE supera il limite Android.' }
    $parts = Get-SemanticVersion $properties.VERSION_NAME
    return [pscustomobject]@{ Code = [int]$code; Name = $properties.VERSION_NAME; Parts = $parts }
}

function Get-SemanticVersion {
    param([string]$Version)
    if ($Version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        throw "Versione non valida: $Version. Attesa MAJOR.MINOR.PATCH, senza prerelease."
    }
    $parts = @([long]$Matches[1], [long]$Matches[2], [long]$Matches[3])
    foreach ($part in $parts) { if ($part -gt [int]::MaxValue) { throw 'Componente di versione troppo grande.' } }
    return ,$parts
}

function Compare-SemanticVersions {
    param([string]$Left, [string]$Right)
    $leftParts = Get-SemanticVersion $Left
    $rightParts = Get-SemanticVersion $Right
    for ($i = 0; $i -lt 3; $i++) {
        if ($leftParts[$i] -lt $rightParts[$i]) { return -1 }
        if ($leftParts[$i] -gt $rightParts[$i]) { return 1 }
    }
    return 0
}

function Get-NextAppVersion {
    param($Current)
    $parts = Get-SemanticVersion $Current.Name
    if ($Current.Code -ge 2100000000 -or $parts[2] -ge [int]::MaxValue) { throw 'Non e possibile incrementare ancora questa versione.' }
    return [pscustomobject]@{
        Code = [int]($Current.Code + 1)
        Name = ('{0}.{1}.{2}' -f $parts[0], $parts[1], ($parts[2] + 1))
    }
}

function Write-AppVersion {
    param([string]$Path, $Expected, $Next)
    $current = Read-AppVersion $Path
    if ($current.Code -ne $Expected.Code -or $current.Name -cne $Expected.Name) {
        throw 'version.properties e cambiato durante il rilascio. Nessuna modifica sovrascritta.'
    }
    $raw = [IO.File]::ReadAllText($Path).TrimStart([char]0xFEFF)
    $raw = [regex]::Replace($raw, '(?m)^[ \t]*VERSION_CODE[ \t]*=[^\r\n]*', ('VERSION_CODE={0}' -f $Next.Code))
    $raw = [regex]::Replace($raw, '(?m)^[ \t]*VERSION_NAME[ \t]*=[^\r\n]*', ('VERSION_NAME={0}' -f $Next.Name))
    Write-Utf8File $Path $raw
}

function Get-RepositoryIdentity {
    param([string]$Owner, [string]$Name)
    if ($Owner -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$' -or
        $Name -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$') {
        throw 'GITHUB_OWNER o GITHUB_REPO non valido in release.properties.'
    }
    return "$Owner/$Name"
}

function Get-RepositoryFromRemote {
    param([string]$Remote)
    $identity = ''
    if ($Remote -match '^https://github\.com/([^/]+/[^/]+?)(?:\.git)?/?$') { $identity = $Matches[1] }
    elseif ($Remote -match '^git@github\.com:([^/]+/[^/]+?)(?:\.git)?$') { $identity = $Matches[1] }
    elseif ($Remote -match '^ssh://git@github\.com/([^/]+/[^/]+?)(?:\.git)?/?$') { $identity = $Matches[1] }
    else { throw 'origin deve essere un URL HTTPS o SSH di github.com, senza credenziali incorporate.' }
    $segments = $identity -split '/'
    if ($segments.Length -ne 2) { throw 'origin non identifica una repository GitHub.' }
    return Get-RepositoryIdentity $segments[0] $segments[1]
}

function Assert-PathUnderRoot {
    param([string]$Root, [string]$Path)
    $base = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]'\/') + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) { throw 'Il percorso di rilascio esce dal progetto.' }
    return $resolved
}

function Assert-NoTrackedSecrets {
    param([string[]]$Paths)
    $bad = @($Paths | Where-Object {
        $_ -match '(?i)(^|/)(signing|\.secrets)/' -or
        $_ -match '(?i)(^|/)(signing\.properties|local\.properties|\.env(?:\..*)?|[^/]+\.(jks|keystore|p12|pfx|pem|key))$'
    })
    if ($bad.Count -gt 0) { throw ('File privati gia tracciati da Git: ' + ($bad -join ', ') + '. Rimuovili dalla cronologia prima di pubblicare.') }
}

function Assert-CleanReleaseTree {
    param([string[]]$Status, [bool]$AllowVersionChange = $false)
    $unexpected = @($Status | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_) -and -not ($AllowVersionChange -and $_ -match '^( M|M |MM) version\.properties$')
    })
    if ($unexpected.Count -gt 0) {
        throw ('La cartella Git contiene modifiche non pubblicate. Esegui prima il commit del tuo lavoro. Nessun file e stato aggiunto automaticamente.' + [Environment]::NewLine + ($unexpected -join [Environment]::NewLine))
    }
}

function Invoke-ReleaseCommand {
    param([string]$FilePath, [string[]]$Arguments, [switch]$AllowFailure, [switch]$ShowOutput)
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    if ($ShowOutput) { foreach ($line in $lines) { Write-Host $line } }
    if ($code -ne 0 -and -not $AllowFailure) {
        throw ((Split-Path $FilePath -Leaf) + ' non riuscito (codice ' + $code + ').' + [Environment]::NewLine + ($lines -join [Environment]::NewLine))
    }
    return [pscustomobject]@{ ExitCode = $code; Lines = $lines; Text = ($lines -join "`n") }
}

function Find-AndroidBuildTools {
    param([string]$Root)
    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
    $localProperties = Join-Path $Root 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        foreach ($line in [IO.File]::ReadAllLines($localProperties)) {
            if ($line -match '^sdk\.dir=(.+)$') { $roots += $Matches[1].Replace('\:', ':').Replace('\\', '\') }
        }
    }
    if ($env:LOCALAPPDATA) { $roots += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    foreach ($sdk in $roots) {
        if ([string]::IsNullOrWhiteSpace($sdk)) { continue }
        $buildTools = Join-Path $sdk 'build-tools'
        if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) { continue }
        $versions = @(Get-ChildItem -LiteralPath $buildTools -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending)
        foreach ($version in $versions) {
            $signer = Join-Path $version.FullName 'apksigner.bat'
            $aapt = Join-Path $version.FullName 'aapt.exe'
            if ((Test-Path -LiteralPath $signer) -and (Test-Path -LiteralPath $aapt)) {
                return [pscustomobject]@{ Sdk = $sdk; Signer = $signer; Aapt = $aapt }
            }
        }
    }
    throw 'Android SDK Build Tools non trovati. Configura Android Studio / local.properties oppure ANDROID_SDK_ROOT.'
}

function Assert-VerifiedReleaseApk {
    param([string]$Apk, $Tools, $Version, [string]$Certificate)
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK release non trovato dopo la compilazione.' }
    $signature = Invoke-ReleaseCommand $Tools.Signer @('verify', '--verbose', '--print-certs', $Apk)
    if ($signature.Text -notmatch '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$') { throw 'Impossibile verificare il certificato dell APK.' }
    $actual = $Matches[1]
    if ($actual -ine $Certificate) { throw 'Il certificato dell APK e cambiato: interrompo per non rompere gli aggiornamenti delle app gia installate.' }
    $badging = Invoke-ReleaseCommand $Tools.Aapt @('dump', 'badging', $Apk)
    if ($badging.Text -notmatch "(?m)^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") { throw 'Metadati APK non riconosciuti.' }
    if ($Matches[1] -cne 'it.topperia.game' -or $Matches[2] -cne [string]$Version.Code -or $Matches[3] -cne $Version.Name) {
        throw 'Package o versione dell APK non corrispondono a version.properties.'
    }
    if ($badging.Text -match '(?m)^application-debuggable') { throw 'L APK e debuggable: e richiesta assembleRelease con firma persistente.' }
}

function New-UpdateManifest {
    param([string]$Apk, $Version)
    $expectedName = 'Topperia-' + $Version.Name + '.apk'
    if ((Split-Path $Apk -Leaf) -cne $expectedName) { throw 'Nome APK non corrispondente alla versione.' }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Apk).Hash.ToLowerInvariant()
    return [ordered]@{
        schemaVersion = 1
        versionCode = [int]$Version.Code
        versionName = $Version.Name
        packageName = 'it.topperia.game'
        apkAssetName = $expectedName
        sha256 = $hash
        sizeBytes = [long](Get-Item -LiteralPath $Apk).Length
        tag = 'v' + $Version.Name
    }
}

function Read-ReleaseState {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    if ((Get-Item -LiteralPath $Path).Length -gt 65536) { throw 'Stato del rilascio troppo grande.' }
    $state = [IO.File]::ReadAllText($Path) | ConvertFrom-Json
    if ($state.schemaVersion -ne 1 -or $state.stage -notin @('prepared', 'built', 'committed', 'tagged', 'pushed', 'draft', 'published')) { throw 'Stato del rilascio non riconosciuto.' }
    [void](Get-SemanticVersion $state.versionName)
    if ($state.versionCode -lt 1 -or $state.versionCode -gt 2100000000) { throw 'VERSION_CODE non valido nello stato del rilascio.' }
    return $state
}

function Save-ReleaseState {
    param([string]$Path, $State)
    $temporary = $Path + '.tmp'
    Write-Utf8File $temporary (($State | ConvertTo-Json -Depth 6) + "`n")
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Assert-ReleaseAssets {
    param($Release, [string[]]$Files)
    foreach ($file in $Files) {
        $name = Split-Path $file -Leaf
        $matching = @($Release.assets | Where-Object { $_.name -ceq $name })
        if ($matching.Count -ne 1 -or [long]$matching[0].size -ne (Get-Item -LiteralPath $file).Length) { throw "Asset remoto assente o incompleto: $name" }
    }
}

function Get-ReleaseJson {
    param([string]$Gh, [string]$Repository, [string]$Tag)
    $result = Invoke-ReleaseCommand $Gh @('api', "repos/$Repository/releases/tags/$Tag") -AllowFailure
    if ($result.ExitCode -ne 0) {
        if ($result.Text -notmatch '(?i)HTTP 404') { throw ('Impossibile leggere la release GitHub.' + "`n" + $result.Text) }
        # The REST tag endpoint can omit drafts. Let gh resolve the authenticated
        # release identity, then fetch the REST object expected by the pipeline.
        $identityResult = Invoke-ReleaseCommand $Gh @('release', 'view', $Tag, '--repo', $Repository, '--json', 'databaseId,tagName') -AllowFailure
        if ($identityResult.ExitCode -ne 0) {
            if ($identityResult.Text -match '(?im)^\s*release not found\s*$' -or $identityResult.Text -match '(?i)HTTP 404') { return $null }
            throw ('Impossibile risolvere la release GitHub, inclusa una eventuale bozza.' + "`n" + $identityResult.Text)
        }
        $identity = $identityResult.Text | ConvertFrom-Json
        $releaseId = 0L
        if ($null -eq $identity -or $null -eq $identity.PSObject.Properties['databaseId'] -or
            $null -eq $identity.PSObject.Properties['tagName'] -or $identity.tagName -cne $Tag -or
            [string]$identity.databaseId -notmatch '^[1-9][0-9]*$' -or
            -not [long]::TryParse([string]$identity.databaseId, [ref]$releaseId)) {
            throw 'GitHub CLI non ha restituito un ID valido per il tag richiesto.'
        }
        $result = Invoke-ReleaseCommand $Gh @('api', "repos/$Repository/releases/$releaseId") -AllowFailure
        if ($result.ExitCode -ne 0) { throw ('La release GitHub esiste ma non e leggibile tramite il suo ID.' + "`n" + $result.Text) }
    }
    $release = $result.Text | ConvertFrom-Json
    if ($null -eq $release -or $null -eq $release.PSObject.Properties['tag_name'] -or $release.tag_name -cne $Tag) {
        throw 'GitHub ha restituito una release con un tag diverso da quello richiesto.'
    }
    return $release
}

function Assert-NewerThanLatestRelease {
    param([string]$Gh, [string]$Repository, $Version, [string]$Root)
    $latest = Invoke-ReleaseCommand $Gh @('api', "repos/$Repository/releases/latest") -AllowFailure
    if ($latest.ExitCode -ne 0) {
        if ($latest.Text -match '(?i)HTTP 404') { return }
        throw ('Impossibile controllare l ultima release pubblica.' + "`n" + $latest.Text)
    }
    $release = $latest.Text | ConvertFrom-Json
    if ($release.draft -or $release.prerelease) { throw 'GitHub ha restituito una release non stabile come latest.' }
    if (@($release.assets | Where-Object { $_.name -ceq 'update.json' }).Count -ne 1) { throw 'La release latest non contiene update.json: verificare la configurazione prima di pubblicare.' }
    $directory = Assert-PathUnderRoot $Root (Join-Path $Root ('.build/release-preflight-' + [guid]::NewGuid().ToString('N')))
    [void](New-Item -ItemType Directory -Path $directory -Force)
    [void](Invoke-ReleaseCommand $Gh @('release', 'download', $release.tag_name, '--repo', $Repository, '--pattern', 'update.json', '--dir', $directory))
    $manifestPath = Join-Path $directory 'update.json'
    if ((Get-Item -LiteralPath $manifestPath).Length -gt 16384) { throw 'Manifest della release troppo grande.' }
    $manifest = [IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1 -or $manifest.packageName -cne 'it.topperia.game' -or $manifest.versionCode -lt 1) { throw 'Manifest della release latest non valido.' }
    if ($Version.Code -le [long]$manifest.versionCode -or (Compare-SemanticVersions $Version.Name $manifest.versionName) -le 0) {
        throw 'La prossima versione non e piu recente della release latest. Aggiorna il checkout prima di rilasciare.'
    }
}

function Start-TopperiaRelease {
    param([string]$Root, [switch]$PlanOnly, [switch]$ContinueExisting, [string]$CustomNotes)
    $Root = [IO.Path]::GetFullPath($Root)
    $versionPath = Join-Path $Root 'version.properties'
    $configuration = Read-ReleaseProperties (Join-Path $Root 'release.properties')
    foreach ($key in @('GITHUB_OWNER', 'GITHUB_REPO', 'SIGNING_CERT_SHA256')) {
        if (-not $configuration.ContainsKey($key)) { throw "release.properties non contiene $key." }
    }
    $repository = Get-RepositoryIdentity $configuration.GITHUB_OWNER $configuration.GITHUB_REPO
    $certificate = $configuration.SIGNING_CERT_SHA256.Replace(':', '').ToLowerInvariant()
    if ($certificate -notmatch '^[0-9a-f]{64}$') { throw 'SIGNING_CERT_SHA256 deve essere il fingerprint SHA-256 del certificato Android.' }
    $current = Read-AppVersion $versionPath
    $statePath = Join-Path $Root 'output/release-state.json'
    $state = Read-ReleaseState $statePath
    if ($ContinueExisting) {
        if ($null -eq $state) { throw 'Nessun rilascio da riprendere. Esegui release.bat senza -Resume.' }
        if ($state.repository -ine $repository) { throw 'Lo stato appartiene a un altra repository.' }
        $next = [pscustomobject]@{ Code = [int]$state.versionCode; Name = $state.versionName }
    } else {
        if ($null -ne $state -and $state.stage -ne 'published') { throw 'Un rilascio e rimasto incompleto. Esegui release.bat -Resume per riprenderlo senza incrementare ancora.' }
        $next = Get-NextAppVersion $current
    }
    $tag = 'v' + $next.Name
    $releaseDirectory = Assert-PathUnderRoot $Root (Join-Path $Root ('output/releases/' + $tag))
    $apkPath = Join-Path $releaseDirectory ('Topperia-' + $next.Name + '.apk')
    $manifestPath = Join-Path $releaseDirectory 'update.json'
    $checksumPath = $apkPath + '.sha256'
    $notesPath = Join-Path $releaseDirectory 'release-notes.md'
    if ($PlanOnly) {
        [pscustomobject]@{
            dryRun = $true; resume = [bool]$ContinueExisting; repository = $repository
            currentVersion = $current.Name; nextVersion = $next.Name; nextVersionCode = $next.Code
            tag = $tag; apkAssetName = (Split-Path $apkPath -Leaf); manifestAssetName = 'update.json'
            publishedReleaseUrl = "https://github.com/$repository/releases/tag/$tag"
        } | ConvertTo-Json
        return
    }

    $git = (Get-Command git -ErrorAction Stop).Source
    $gh = (Get-Command gh -ErrorAction Stop).Source
    [void](Get-Command java -ErrorAction Stop)
    [void](Get-Command javac -ErrorAction Stop)
    $tools = Find-AndroidBuildTools $Root
    $lock = $null
    Push-Location -LiteralPath $Root
    try {
        [void](New-Item -ItemType Directory -Path (Join-Path $Root '.build') -Force)
        try { $lock = [IO.File]::Open((Join-Path $Root '.build/release.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
        catch { throw 'Un altro rilascio e gia in esecuzione in questa cartella.' }
        $inside = Invoke-ReleaseCommand $git @('rev-parse', '--show-toplevel')
        if ([IO.Path]::GetFullPath($inside.Text) -ine $Root.TrimEnd([char[]]'\/')) { throw 'La cartella deve essere la radice della repository Git.' }
        $remote = Invoke-ReleaseCommand $git @('remote', 'get-url', 'origin')
        if ((Get-RepositoryFromRemote $remote.Text) -ine $repository) { throw 'origin non corrisponde a release.properties. Nessuna pubblicazione effettuata.' }
        $tracked = Invoke-ReleaseCommand $git @('ls-files')
        Assert-NoTrackedSecrets $tracked.Lines
        foreach ($private in @('signing.properties', 'output/release-state.json', '.build/release.lock')) {
            $ignored = Invoke-ReleaseCommand $git @('check-ignore', '--quiet', '--', $private) -AllowFailure
            if ($ignored.ExitCode -ne 0) { throw "$private deve essere escluso da Git prima di rilasciare." }
        }
        if (-not (Test-Path -LiteralPath (Join-Path $Root 'signing.properties') -PathType Leaf)) { throw 'Configura prima signing.properties e la chiave privata persistente.' }
        $branch = (Invoke-ReleaseCommand $git @('symbolic-ref', '--quiet', '--short', 'HEAD')).Text
        $repoInfo = (Invoke-ReleaseCommand $gh @('repo', 'view', $repository, '--json', 'nameWithOwner,isPrivate,viewerPermission,defaultBranchRef')).Text | ConvertFrom-Json
        if ($repoInfo.isPrivate -or $repoInfo.nameWithOwner -ine $repository) { throw 'La repository deve essere quella pubblica indicata in release.properties.' }
        if ($repoInfo.viewerPermission -notin @('ADMIN', 'MAINTAIN', 'WRITE')) { throw 'L account GitHub CLI non dispone dei permessi di scrittura sulla repository.' }
        if ($branch -cne $repoInfo.defaultBranchRef.name) { throw 'Esegui i rilasci dalla branch predefinita della repository.' }
        $status = Invoke-ReleaseCommand $git @('status', '--porcelain', '--untracked-files=all')
        Assert-CleanReleaseTree $status.Lines ([bool]$ContinueExisting)
        [void](Invoke-ReleaseCommand $git @('fetch', 'origin', $branch))
        $counts = (Invoke-ReleaseCommand $git @('rev-list', '--left-right', '--count', "HEAD...origin/$branch")).Text -split '\s+'
        if ([int]$counts[1] -gt 0) { throw 'La branch locale e indietro rispetto a GitHub. Integra prima gli aggiornamenti (git pull --ff-only).' }
        $head = (Invoke-ReleaseCommand $git @('rev-parse', 'HEAD')).Text
        if ($ContinueExisting) {
            if ($branch -cne $state.branch) { throw 'La branch e cambiata dal rilascio incompleto.' }
            $expectedHead = if ($state.commit) { $state.commit } else { $state.baseCommit }
            if ($head -cne $expectedHead) {
                $changedPaths = (Invoke-ReleaseCommand $git @('diff-tree', '--no-commit-id', '--name-only', '-r', $head)).Lines
                $parent = (Invoke-ReleaseCommand $git @('rev-parse', 'HEAD^')).Text
                if ($state.stage -eq 'built' -and $parent -ceq $state.baseCommit -and $changedPaths.Count -eq 1 -and $changedPaths[0] -ceq 'version.properties' -and $current.Code -eq $next.Code -and $current.Name -ceq $next.Name) {
                    $state.commit = $head; $state.stage = 'committed'; Save-ReleaseState $statePath $state
                } else { throw 'HEAD e cambiato dal rilascio incompleto. Nessun commit o tag modificato.' }
            }
            if ($current.Code -ne $next.Code -or $current.Name -cne $next.Name) {
                if ($state.stage -ne 'prepared' -or $current.Code -ne $state.previousVersionCode -or $current.Name -cne $state.previousVersionName) { throw 'Versione locale diversa dal rilascio da riprendere.' }
                Write-AppVersion $versionPath $current $next
            }
        } else {
            Assert-NewerThanLatestRelease $gh $repository $next $Root
            $existingTag = Invoke-ReleaseCommand $git @('show-ref', '--verify', '--quiet', "refs/tags/$tag") -AllowFailure
            if ($existingTag.ExitCode -eq 0 -or $null -ne (Get-ReleaseJson $gh $repository $tag)) { throw 'Questa versione esiste gia. Non e stata modificata: aggiorna il checkout o usa -Resume.' }
            [void](New-Item -ItemType Directory -Path $releaseDirectory -Force)
            $state = [pscustomobject]@{
                schemaVersion = 1; repository = $repository; branch = $branch; baseCommit = $head; commit = ''
                previousVersionCode = $current.Code; previousVersionName = $current.Name
                versionCode = $next.Code; versionName = $next.Name; stage = 'prepared'; sha256 = ''
            }
            Save-ReleaseState $statePath $state
            Write-AppVersion $versionPath $current $next
        }

        Write-Host ("Topperia {0} - versionCode {1}" -f $next.Name, $next.Code) -ForegroundColor Cyan
        if ($state.stage -eq 'prepared') {
            Write-Host 'Verifico il motore, compilo e controllo la release firmata...'
            [void](Invoke-ReleaseCommand (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe') @('-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $Root 'scripts/test-release.ps1')) -ShowOutput)
            [void](Invoke-ReleaseCommand (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe') @('-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $Root 'scripts/test-engine.ps1')) -ShowOutput)
            [void](Invoke-ReleaseCommand (Join-Path $Root 'gradlew.bat') @(':app:assembleRelease', ':app:lintRelease', '--no-daemon', '--max-workers=2') -ShowOutput)
            $builtApk = Join-Path $Root 'app/build/outputs/apk/release/app-release.apk'
            Assert-VerifiedReleaseApk $builtApk $tools $next $certificate
            [void](New-Item -ItemType Directory -Path $releaseDirectory -Force)
            Copy-Item -LiteralPath $builtApk -Destination $apkPath -Force
            $manifest = New-UpdateManifest $apkPath $next
            Write-Utf8File $manifestPath (($manifest | ConvertTo-Json -Depth 5) + "`n")
            Write-Utf8File $checksumPath ($manifest.sha256 + '  ' + $manifest.apkAssetName + "`n")
            if ($CustomNotes) {
                if (-not (Test-Path -LiteralPath $CustomNotes -PathType Leaf)) { throw 'File delle note di rilascio non trovato.' }
                Write-Utf8File $notesPath ([IO.File]::ReadAllText((Resolve-Path -LiteralPath $CustomNotes)))
            } else {
                $notes = "## Topperia $($next.Name)`n`nTrivia nativo Android: 30 classifiche, 300 risposte e sfide locali.`n`n### Installa o aggiorna`n`nScarica **$($manifest.apkAssetName)** e aprilo sul dispositivo (Android 8+). La firma mantiene compatibili gli aggiornamenti e i salvataggi esistenti.`n`nIl controllo aggiornamenti integrato legge le release stabili di questa repository. Android richiede la conferma prima di installare l'APK.`n`n### Integrita`n`nSHA-256: ``$($manifest.sha256)```n`nIn questa release sono inclusi anche update.json e il checksum dell'APK.`n"
                Write-Utf8File $notesPath $notes
            }
            $state.sha256 = $manifest.sha256; $state.stage = 'built'; Save-ReleaseState $statePath $state
        }

        foreach ($file in @($apkPath, $manifestPath, $checksumPath, $notesPath)) {
            if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Un artefatto del rilascio non e piu presente. Ripristina output/releases prima di usare -Resume.' }
        }
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash -ine $state.sha256) { throw 'APK locale cambiato dopo la compilazione. Nessun asset pubblicato.' }
        Assert-VerifiedReleaseApk $apkPath $tools $next $certificate
        $localManifest = [IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
        if ($localManifest.sha256 -cne $state.sha256 -or $localManifest.versionCode -ne $next.Code -or $localManifest.versionName -cne $next.Name -or $localManifest.apkAssetName -cne (Split-Path $apkPath -Leaf) -or $localManifest.packageName -cne 'it.topperia.game') { throw 'update.json locale non corrisponde all APK verificato.' }
        if ($state.stage -eq 'built') {
            $status = Invoke-ReleaseCommand $git @('status', '--porcelain', '--untracked-files=all')
            Assert-CleanReleaseTree $status.Lines $true
            [void](Invoke-ReleaseCommand $git @('add', '--', 'version.properties'))
            [void](Invoke-ReleaseCommand $git @('commit', '-m', "Release Topperia $($next.Name)") -ShowOutput)
            $state.commit = (Invoke-ReleaseCommand $git @('rev-parse', 'HEAD')).Text
            $state.stage = 'committed'; Save-ReleaseState $statePath $state
        }
        if ($state.stage -eq 'committed') {
            $tagCheck = Invoke-ReleaseCommand $git @('rev-parse', '--verify', "refs/tags/$tag`^{commit}") -AllowFailure
            if ($tagCheck.ExitCode -eq 0) {
                if ($tagCheck.Text -cne $state.commit) { throw 'Il tag locale punta a un commit diverso. Non e stato sovrascritto.' }
            } else { [void](Invoke-ReleaseCommand $git @('tag', '-a', $tag, '-m', "Topperia $($next.Name)", $state.commit)) }
            $state.stage = 'tagged'; Save-ReleaseState $statePath $state
        }
        if ($state.stage -eq 'tagged') {
            Write-Host 'Invio commit e tag a GitHub...'
            [void](Invoke-ReleaseCommand $git @('push', '--atomic', 'origin', "HEAD:refs/heads/$branch", "refs/tags/$tag") -ShowOutput)
            $state.stage = 'pushed'; Save-ReleaseState $statePath $state
        }
        $release = Get-ReleaseJson $gh $repository $tag
        if ($null -eq $release) {
            Write-Host 'Creo una bozza e carico gli artefatti...'
            [void](Invoke-ReleaseCommand $gh @('release', 'create', $tag, $apkPath, $manifestPath, $checksumPath, '--repo', $repository, '--title', "Topperia $($next.Name)", '--notes-file', $notesPath, '--draft', '--verify-tag') -ShowOutput)
            $release = Get-ReleaseJson $gh $repository $tag
        } elseif ($release.draft) {
            $missing = @()
            foreach ($file in @($apkPath, $manifestPath, $checksumPath)) {
                $name = Split-Path $file -Leaf
                if (@($release.assets | Where-Object { $_.name -ceq $name }).Count -eq 0) { $missing += $file }
            }
            if ($missing.Count -gt 0) { [void](Invoke-ReleaseCommand $gh (@('release', 'upload', $tag, '--repo', $repository) + $missing) -ShowOutput) }
            $release = Get-ReleaseJson $gh $repository $tag
        }
        if ($null -eq $release -or $release.prerelease) { throw 'Release remota assente o marcata come prerelease.' }
        if ($release.draft) { $state.stage = 'draft'; Save-ReleaseState $statePath $state }
        $assets = @($apkPath, $manifestPath, $checksumPath)
        Assert-ReleaseAssets $release $assets
        Write-Host 'Verifico gli artefatti scaricandoli dalla release...'
        $verificationDirectory = Assert-PathUnderRoot $Root (Join-Path $Root ('.build/release-verify-' + [guid]::NewGuid().ToString('N')))
        [void](New-Item -ItemType Directory -Path $verificationDirectory -Force)
        foreach ($file in $assets) {
            $name = Split-Path $file -Leaf
            [void](Invoke-ReleaseCommand $gh @('release', 'download', $tag, '--repo', $repository, '--pattern', $name, '--dir', $verificationDirectory))
            $downloaded = Join-Path $verificationDirectory $name
            if ((Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash -cne (Get-FileHash -Algorithm SHA256 -LiteralPath $downloaded).Hash) { throw "Verifica dell asset remoto non riuscita: $name. La bozza non e stata pubblicata." }
        }
        if ($release.draft) {
            Write-Host 'Artefatti verificati. Pubblico la release stabile latest...'
            [void](Invoke-ReleaseCommand $gh @('release', 'edit', $tag, '--repo', $repository, '--draft=false', '--prerelease=false', '--latest', '--verify-tag') -ShowOutput)
        }
        $published = Get-ReleaseJson $gh $repository $tag
        if ($published.draft -or $published.prerelease) { throw 'GitHub non ha confermato la pubblicazione stabile.' }
        Assert-ReleaseAssets $published $assets
        $state.stage = 'published'; Save-ReleaseState $statePath $state
        Copy-Item -LiteralPath $apkPath -Destination (Join-Path $Root 'output/Topperia.apk') -Force
        Write-Host ''
        Write-Host "Release pubblicata: https://github.com/$repository/releases/tag/$tag" -ForegroundColor Green
        Write-Host ('APK: ' + $apkPath)
    } finally {
        if ($null -ne $lock) { $lock.Dispose() }
        Pop-Location
    }
}

if (-not $LibraryOnly) {
    try { Start-TopperiaRelease -Root $ProjectRoot -PlanOnly:$DryRun -ContinueExisting:$Resume -CustomNotes $NotesFile }
    catch {
        Write-Host ''
        Write-Host ('ERRORE: ' + $_.Exception.Message) -ForegroundColor Red
        Write-Host 'Nessun reset, cancellazione di commit o sovrascrittura di release pubbliche e stato eseguito.'
        Write-Host 'Se il rilascio e gia iniziato, risolvi il problema e usa: release.bat -Resume'
        exit 1
    }
}
