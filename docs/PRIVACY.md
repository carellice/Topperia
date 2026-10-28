# Privacy di Topperia

Ultimo aggiornamento: 28 ottobre 2026.

Topperia funziona senza account, pubblicità, acquisti in app o strumenti di analisi. Le partite e il catalogo sono disponibili offline. L’app non invia risposte, nomi dei giocatori o statistiche di gioco allo sviluppatore.

## Dati sul dispositivo

Nello spazio privato dell’app vengono conservati la partita in corso, le risposte inserite, i nomi scelti per giocatori o squadre, i risultati, le statistiche, i traguardi e le preferenze. Sono locali anche il completamento dell’introduzione e le informazioni necessarie a riprendere il gioco.

Il sistema di aggiornamento può conservare l’ultima verifica, i metadati della release e un APK scaricato. Questi file rimangono nello spazio privato di Topperia, non nella cartella pubblica Download.

Per eliminare i dati locali puoi usare la gestione della memoria dell’app nelle impostazioni Android. L’operazione azzera anche partite, statistiche e preferenze.

## Connessioni per gli aggiornamenti

Topperia può verificare le nuove release stabili, al massimo una volta ogni 24 ore, oppure quando richiedi una verifica dalle impostazioni. La richiesta usa HTTPS e l’API pubblica:

~~~text
https://api.github.com/repos/carellice/Topperia/releases/latest
~~~

Non è richiesto un account GitHub e l’app non contiene token o credenziali GitHub. Come per una normale richiesta HTTPS, GitHub riceve le informazioni di rete necessarie alla connessione, incluso l’indirizzo IP; non riceve lo stato delle partite.

Il download parte dopo la tua conferma e usa gli asset delle release di carellice/Topperia e i servizi di distribuzione file di GitHub. Topperia verifica metadati, integrità del pacchetto e corrispondenza della firma con l’app installata. Per installare serve un’ulteriore scelta dell’utente e la conferma dell’installer Android.

Le connessioni a GitHub sono soggette anche alla [dichiarazione sulla privacy di GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## Collegamenti esterni

Le classifiche contengono collegamenti alle proprie fonti. Aprirne uno avvia il browser o un’altra app del dispositivo; le pagine visitate applicano le rispettive condizioni e politiche sulla privacy.

## Permessi

- **Internet:** verifica e download degli aggiornamenti dell’app.
- **Vibrazione:** feedback durante il gioco, disattivabile dalle impostazioni.
- **Installazione di pacchetti:** apertura dell’aggiornamento verificato nell’installer Android. L’app non installa APK silenziosamente e l’eventuale autorizzazione viene gestita da Android.

Topperia non richiede l’accesso a contatti, posizione, fotocamera o microfono per giocare.
