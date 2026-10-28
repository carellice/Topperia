# Verifica di Topperia 1.1.1

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
