# Pubblicare un aggiornamento di Topperia

Da Windows, **apri `release.bat`** nella cartella del progetto. La finestra rimane aperta alla fine, così puoi leggere il risultato. Dalla riga di comando:

```bat
release.bat
```

Lo script incrementa automaticamente la versione patch e il codice Android: per esempio, **1.1.1 / 3 → 1.1.2 / 4**. La fonte della versione è `version.properties`; non occorre modificare Gradle o rinominare a mano l’APK.

## Prima di eseguirlo

- Installa Git, [GitHub CLI](https://cli.github.com/), Java 17 o successivo con `javac` e Android SDK 36 con Build Tools. Il progetto contiene il Gradle Wrapper.
- Accedi al tuo account con `gh auth login`, configura l’autenticazione Git con `gh auth setup-git` e assicurati di avere permessi di scrittura su `carellice/Topperia`.
- Lavora nella branch predefinita della repository (`main`) e fai il commit delle modifiche che vuoi rilasciare. La cartella deve essere pulita: lo script **non aggiunge automaticamente il tuo lavoro** né file non tracciati.
- `origin` deve corrispondere alla repository pubblica dichiarata in `release.properties`. Sono ammessi gli URL HTTPS e SSH di `github.com` senza credenziali incorporate.
- Mantieni il file locale `signing.properties` e la chiave privata persistente nella posizione prevista dal progetto. Devono restare esclusi da Git. Gli strumenti verificano che il certificato coincida con `SIGNING_CERT_SHA256` in `release.properties`.

La stessa chiave permette ad Android di installare le nuove versioni sopra quelle esistenti, conservando i salvataggi. **Conservane un backup privato**: la chiave non viene pubblicata con i sorgenti, le release o il manifest di aggiornamento. Il fingerprint SHA-256 del certificato è pubblico; password e chiave privata restano locali.

## Cosa esegue il `.bat`

1. Verifica strumenti, repository pubblica, permessi, branch, file ignorati e assenza di chiavi o credenziali già tracciate. Recupera lo stato remoto e si ferma se il checkout è indietro.
2. Controlla l’ultima release stabile e impedisce versioni o codici Android uguali o precedenti.
3. Incrementa `VERSION_NAME` e `VERSION_CODE`, poi esegue i test della procedura di rilascio, del motore, della selezione casuale e delle regole degli aggiornamenti, `assembleRelease` e `lintRelease`.
4. Verifica la firma con `apksigner`, il package `it.topperia.game`, la versione dell’APK e che la build non sia debuggable.
5. Prepara in `output/releases/vVERSION/` l’APK firmato, `update.json`, il checksum e le note di rilascio.
6. Esegue il commit **del solo `version.properties`**, crea il tag annotato `vVERSION` e invia commit e tag a GitHub con un push atomico, senza forzature.
7. Crea una release in **bozza**, carica gli artefatti e li scarica nuovamente per confrontarne gli SHA-256. Pubblica la release stabile come **Latest** soltanto dopo queste verifiche.

Il risultato è disponibile nelle [release pubbliche](https://github.com/carellice/Topperia/releases). Una copia dell’APK viene salvata anche in `output/Topperia.apk`. `output/` e `.build/` non vengono inseriti nella repository.

## Anteprima senza modifiche

```bat
release.bat -DryRun
```

Mostra la prossima versione, il nome del tag e quello dell’APK, senza modificare file, creare commit o tag, contattare GitHub o compilare. È utile anche per controllare la configurazione prima della prima pubblicazione.

## Note personalizzate

```bat
release.bat -NotesFile "docs\note della versione.md"
```

Le note vengono lette da un file UTF-8 e associate alla nuova release. Senza questo parametro, vengono scritte note essenziali con istruzioni di installazione e checksum. Il file personalizzato deve essere già incluso nel commit se si trova nel progetto e non è ignorato.

## Riprendere un rilascio interrotto

```bat
release.bat -Resume
```

Non incrementa di nuovo la versione. Riprende lo stato conservato in `output/release-state.json`: può ripetere una compilazione non completata, ritentare il push o finire gli upload mancanti di una bozza. Se il rilascio era già pubblicato, ne verifica gli artefatti senza crearne uno nuovo.

In caso di errore, conserva `output/releases/` e `output/release-state.json`. Risolvi prima il problema indicato, per esempio la connessione, l’accesso GitHub o la configurazione dell’SDK. Non modificare `HEAD`, la versione o gli artefatti tra un tentativo e il successivo: lo script li riconosce e si ferma se non corrispondono più al rilascio iniziato. Un problema nel codice che richiede nuovi commit va risolto e revisionato separatamente prima di preparare un altro rilascio.

Non vengono eseguiti `reset`, push forzati, cancellazioni di commit o sostituzioni di asset già pubblicati. Un tag che punta a un commit diverso o un asset remoto con contenuto diverso interrompe il processo. Una bozza incompleta può essere corretta manualmente su GitHub e poi ripresa; una release pubblicata non viene riscritta dal `.bat`.

## Come l’app trova le nuove versioni

L’app consulta l’ultima release stabile della repository. Il `.bat` pubblica questi tre file:

| Asset | Scopo |
| --- | --- |
| `Topperia-VERSION.apk` | APK Android firmato con la chiave persistente |
| `Topperia-VERSION.apk.sha256` | Checksum leggibile dell’APK |
| `update.json` | Versione, nome esatto dell’asset, dimensione e SHA-256 verificabili dall’app |

Esempio del manifest (il checksum qui è abbreviato):

```json
{
  "schemaVersion": 1,
  "versionCode": 4,
  "versionName": "1.1.2",
  "packageName": "it.topperia.game",
  "apkAssetName": "Topperia-1.1.2.apk",
  "sha256": "…64 cifre esadecimali…",
  "sizeBytes": 123456,
  "tag": "v1.1.2"
}
```

Il manifest identifica un asset della release: non può indirizzare l’app a un URL arbitrario. Per aggiornare, Android richiede comunque la conferma dell’installazione e, quando necessario, il permesso di installare app da questa fonte.

## Verificare lo script

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\test-release.ps1
```

I test controllano incremento e limiti della versione, fingerprint e metadati, URL della repository, esclusione dei file privati, percorsi confinati al progetto, manifest e hash, recupero dello stato, errori dei processi esterni e un vero dry-run PowerShell 5.1 in una cartella con spazi. Non pubblicano release.

Riferimenti del flusso GitHub: [creazione di una bozza con tag verificato](https://cli.github.com/manual/gh_release_create), [pubblicazione della bozza](https://cli.github.com/manual/gh_release_edit), [caricamento degli asset](https://cli.github.com/manual/gh_release_upload).
