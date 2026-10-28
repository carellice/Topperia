# Verifiche di Topperia

## Release 1.1.3 e aggiornamenti integrati

Verifica del 28 ottobre 2026. Repository pubblica: [carellice/Topperia](https://github.com/carellice/Topperia). La release stabile [v1.1.3](https://github.com/carellice/Topperia/releases/tag/v1.1.3) contiene APK firmato, `update.json` e checksum SHA-256.

- Eseguito realmente `release.bat`: incremento da 1.1.2 / 4 a **1.1.3 / 5**, test, `assembleRelease`, `lintRelease`, verifica firma e metadati, commit di versione, tag, push, creazione bozza, upload, download di verifica e pubblicazione come Latest. Verificato anche il riconoscimento delle bozze tramite ID autenticato.
- Eseguito `release.bat -Resume` sulla release pubblicata: asset nuovamente verificati, versione invariata e nessuna nuova release. Il dry-run è verificato anche in una cartella con spazi e non modifica i file.
- Superate **122 asserzioni della procedura di rilascio**, **2.766 del motore e dei contenuti**, **925 della selezione casuale** e **53 delle regole degli aggiornamenti**. Queste ultime coprono metadati, URL, host dei redirect, hash, firma e confronto delle versioni.
- Android Lint: **zero errori e cinque avvisi**. Quattro riguardano orientamento, backup e risorse già presenti; uno segnala un aggiornamento disponibile del wrapper Gradle. Nessun errore di esecuzione Android nei log del collaudo.
- Chiave, password, configurazione locale, cache e APK esclusi da Git. L’archivio portabile dei sorgenti è stato controllato per escludere anche `signing.properties`, directory delle chiavi e file di credenziali.

Prova completa su un Pixel virtuale isolato, Android 15 / API 35:

1. Installata una build di verifica 1.1.1 / 3 con il nuovo updater e creato un salvataggio con Brasile scoperto nella classifica del caffè, una risposta corretta e nove tentativi residui.
2. Il controllo manuale ha letto la release pubblica GitHub, mostrato versione, dimensione e note, scaricato l’APK 1.1.2 e verificato integrità, package, versione e firma.
3. Rimandata l’installazione, arrestato il processo e riaperta l’app: l’APK privato era ancora disponibile con il pulsante Installa.
4. Concesso il permesso dalla schermata Android e confermato l’aggiornamento nell’installer di sistema. Dopo il passaggio alla build release 1.1.2, onboarding completato, animazioni ridotte e partita risultavano conservati; verificati di nuovo Brasile e i nove tentativi.
5. Ripetuto l’aggiornamento dall’app dalla 1.1.2 alla **1.1.3**, scaricando l’APK pubblico e confermando l’installer con il permesso già concesso. Il controllo successivo mostra **«Hai già la versione più recente»**; Android conferma versionCode 5.

Le schermate di prova sono in `output/screenshots-v1.1.3/`, escluse dalla repository. Il collaudo non copre un telefono fisico o tutte le versioni Android supportate.

Le sezioni seguenti documentano le verifiche precedenti della versione 1.1.1.

## Logo e icona Android

Il logo originale è stato generato con ImageGen e copiato senza ritocchi in `branding/topperia-logo.png` e nel drawable dell’app. Verificati formato RGBA, 1254 × 1254 pixel, trasparenza effettiva degli angoli e hash identico delle due copie. Il prompt definitivo è documentato in `branding/README.md`.

L’icona Android usa il nuovo asset come primo piano, con sfondo avorio e margini per le maschere adattive. Lo stesso bitmap compare nello splash, in tutte le intestazioni e nella prima scena animata dell’onboarding. L’app lo decodifica una sola volta a risoluzione piena; le regole e il catalogo del gioco restano quelli della versione 1.1.

Build 1.1.1 e firma APK verificate: **zero errori Lint e quattro avvisi** (i tre precedenti e una cartella di risorse mantenuta per compatibilità). Aggiornamento del Pixel con `install -r` riuscito; preferenze e salvataggi conservati. Il logo nelle intestazioni è stato controllato sulla schermata home in esecuzione.

Icona controllata anche nelle informazioni dell’app Android in un emulatore di test separato. Schermate valide in `output/screenshots-v1.1.1/android-icon.png` e `home-logo.png`; emulatore temporaneo chiuso dopo la verifica.

## Motore e contenuti

`scripts/test-engine.ps1`: **2.766 asserzioni del motore e dei contenuti + 925 asserzioni delle estrazioni casuali**, tutte superate. Verificati normalizzazione, accenti, alias, tentativi, duplicati, risposte vuote, chiusura del round, tabelloni indipendenti nella sfida, passaggio al secondo giocatore, vittorie, pareggi e salvataggi corrotti o incompatibili. I 30 dataset sono verificati per cardinalità, alias, metadati delle fonti e partite perfette con ripristino dopo ciascuna risposta.

Il selettore casuale è verificato con un generatore riproducibile: precedenza alle classifiche non completate, ciclo senza ripetizioni, esclusione dell’ultima classifica quando ci sono alternative, gruppi di categorie indipendenti, singola scelta possibile, esaurimento e rimescolamento, input non validi.

## Nuove schermate

- Onboarding al primo avvio: tre scene, Salta, scorrimento orizzontale, pulsanti, Indietro e completamento; dopo un riavvio non si ripresenta. Rivedibile dalle impostazioni.
- Barra fluttuante: provate le tre destinazioni e lo scorrimento del catalogo; gli elementi coperti dalla barra non ricevono tocchi.
- Scelta casuale: provata dalla home in solitaria e da una categoria filtrata; in modalità sfida la categoria Italia ha avviato correttamente un round `DUEL` sui nomi maschili. Conferma di sostituzione del salvataggio verificata sia annullando sia proseguendo.
- Annullamento durante il lancio del dado: Indietro torna al catalogo e, trascorso il tempo dell’animazione, non viene avviata alcuna partita.
- Aggiornamento sullo stesso emulatore: salvataggio della partita e completamento dell’onboarding conservati senza modifiche nelle preferenze.

## Build Android

- Gradle 8.13, Android Gradle Plugin 8.13.0, compilazione Java 17, SDK 36.
- `:app:assembleDebug` e `:app:lintDebug` completati.
- Lint della versione 1.1: **zero errori**, tre avvisi relativi all’orientamento verticale e alle regole di backup sulle versioni recenti di Android. La versione è pensata per telefoni in verticale; non richiede il permesso Internet e conserva i dati localmente. Il log della versione con il nuovo logo è in `output/build-validation-v1.1.1.txt`.
- L’APK di consegna è una build debug installabile, non una release pubblicata sul Play Store.
- Firma APK della versione 1.1 verificata con `apksigner`, schema v2 RSA 2048. Log precedenti in `output/build-validation-v1.1.txt` e `output/apk-signature-v1.1.txt`; log aggiornati in `output/build-validation-v1.1.1.txt` e `output/apk-signature-v1.1.1.txt`.

## Prove sul dispositivo virtuale

Pixel 8, Android 15 / API 35, schermo 1080 × 2400. Provati avvio, navigazione, tastiera nativa, risposta corretta, risposta errata, ripetizione gratuita, conteggio dei dieci tentativi, rivelazione finale, ripresa dopo arresto del processo e statistiche persistenti.

Nella sfida locale il primo giocatore termina con un’ultima risposta corretta: la schermata di passaggio nasconde il tabellone e il messaggio che contiene la risposta. Anche l’albero di accessibilità non contiene le risposte precedenti. Il secondo giocatore parte da zero scoperte e dieci tentativi.

Sfida terminata con risultato **1:0** e vincitore corretto; statistiche incrementate una sola volta. Nella build finale l’albero di accessibilità contiene anche il campo di risposta `EditText` e il pulsante nativo di conferma.

Le schermate e i controlli per l’accessibilità sono stati ispezionati attraverso l’albero nativo Android. Il tabellone fornisce posizione, nome e valore delle risposte scoperte, senza leggere quelle ancora coperte.

Le immagini in `output/screenshots-v1.1/` provengono dalla versione aggiornata nell’emulatore; quelle in `output/screenshots/` documentano le prove iniziali. Non è stata eseguita una prova su un telefono fisico o su ogni versione Android supportata.
