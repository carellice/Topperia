package it.topperia.game.data;

import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankedList.Entry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Additional offline snapshots, transcribed from the original statistical publishers. */
public final class WorldRankings {
    private WorldRankings() { }

    private static final String ISTAT_NAMES = "https://www.istat.it/dati/calcolatori/contanomi/";
    private static final String ISTAT_TERRITORY = "https://www.istat.it/storage/ASI/2024/capitoli/C01.pdf";
    private static final String SPOTIFY_WRAPPED = "https://newsroom.spotify.com/2024-12-04/top-songs-artists-podcasts-audiobooks-albums-trends-2024/";

    public static List<RankedList> all() {
        return ALL;
    }

    public static final List<RankedList> ALL = Collections.unmodifiableList(Arrays.asList(
        new RankedList(
            "italian-boy-names-2024", "I nomi più scelti per i bambini",
            "Nati maschi in Italia nel 2024: quale nome sale sul podio?",
            "Italia", "solo", "Istat · Conta nomi", ISTAT_NAMES, "2024",
            "Nomi dei nati maschi iscritti nelle anagrafi italiane nel 2024. " +
            "Graduatoria Conta nomi verificata sul servizio dati Istat. " +
            "Le grafie diverse sono contate separatamente; non è una graduatoria di tutte le età.",
            new Entry("Leonardo", "6.580 bambini"),
            new Entry("Edoardo", "5.819 bambini"),
            new Entry("Tommaso", "4.389 bambini"),
            new Entry("Mattia", "4.233 bambini"),
            new Entry("Alessandro", "4.201 bambini"),
            new Entry("Francesco", "4.199 bambini"),
            new Entry("Lorenzo", "3.851 bambini"),
            new Entry("Gabriele", "3.805 bambini"),
            new Entry("Riccardo", "3.505 bambini"),
            new Entry("Andrea", "3.143 bambini")
        ),
        new RankedList(
            "italian-girl-names-2024", "I nomi più scelti per le bambine",
            "Nate in Italia nel 2024: dieci nomi amatissimi.",
            "Italia", "solo", "Istat · Conta nomi", ISTAT_NAMES, "2024",
            "Nomi delle nate iscritte nelle anagrafi italiane nel 2024. " +
            "Graduatoria Conta nomi verificata sul servizio dati Istat. " +
            "Si mantengono distinte le grafie: Sofia, per esempio, non somma Sophia.",
            new Entry("Sofia", "4.636 bambine"),
            new Entry("Aurora", "4.265 bambine"),
            new Entry("Ginevra", "4.153 bambine"),
            new Entry("Vittoria", "3.893 bambine"),
            new Entry("Giulia", "3.397 bambine"),
            new Entry("Beatrice", "3.316 bambine"),
            new Entry("Ludovica", "3.064 bambine"),
            new Entry("Matilde", "2.959 bambine"),
            new Entry("Alice", "2.901 bambine"),
            new Entry("Emma", "2.539 bambine")
        ),
        new RankedList(
            "italian-region-area-2023", "Le regioni italiane più grandi",
            "Superficie territoriale in km². Non conta la popolazione!",
            "Italia", "flag", "Istat · Annuario 2024", ISTAT_TERRITORY, "2023",
            "Superficie delle regioni in km², tavola 1.1 dell'Annuario statistico italiano 2024, " +
            "pagina 30. Valori riferiti al 2023, arrotondati al km² come nella tavola; " +
            "si ordinano le venti regioni, senza contare separatamente le province autonome.",
            new Entry("Sicilia", "25.824 km²", "Sicily"),
            new Entry("Piemonte", "25.392 km²", "Piedmont"),
            new Entry("Sardegna", "24.106 km²", "Sardinia"),
            new Entry("Lombardia", "23.854 km²", "Lombardy"),
            new Entry("Toscana", "22.985 km²", "Tuscany"),
            new Entry("Emilia-Romagna", "22.502 km²", "Emilia Romagna"),
            new Entry("Puglia", "19.541 km²", "Apulia"),
            new Entry("Veneto", "18.351 km²"),
            new Entry("Lazio", "17.236 km²", "Latium"),
            new Entry("Calabria", "15.213 km²")
        ),
        new RankedList(
            "italian-region-population-2023", "Le regioni italiane più popolate",
            "Abitanti residenti nel 2023: conosci l'Italia che vive?",
            "Italia", "globe", "Istat · Annuario 2024", ISTAT_TERRITORY, "2023",
            "Popolazione residente delle venti regioni, tavola 1.1 dell'Annuario statistico " +
            "italiano 2024, pagina 30. Si conserva la fotografia provvisoria del 2023 " +
            "pubblicata in quella tavola; i successivi aggiornamenti non sono mescolati a questa serie.",
            new Entry("Lombardia", "10.020.528 abitanti", "Lombardy"),
            new Entry("Lazio", "5.720.272 abitanti", "Latium"),
            new Entry("Campania", "5.590.076 abitanti"),
            new Entry("Veneto", "4.851.972 abitanti"),
            new Entry("Sicilia", "4.794.512 abitanti", "Sicily"),
            new Entry("Emilia-Romagna", "4.455.188 abitanti", "Emilia Romagna"),
            new Entry("Piemonte", "4.252.581 abitanti", "Piedmont"),
            new Entry("Puglia", "3.890.250 abitanti", "Apulia"),
            new Entry("Toscana", "3.664.798 abitanti", "Tuscany"),
            new Entry("Calabria", "1.838.150 abitanti")
        ),
        new RankedList(
            "country-land-area-2023", "I paesi con più terraferma",
            "Solo superficie terrestre: le acque interne sono escluse.",
            "Mondo", "globe", "World Bank · FAOSTAT",
            "https://data.worldbank.org/indicator/AG.LND.TOTL.K2?most_recent_value_desc=true", "2023",
            "Indicatore World Bank AG.LND.TOTL.K2, fonte FAOSTAT, anno 2023. " +
            "Superficie terrestre in km²: sono escluse le acque interne. " +
            "Questa definizione spiega perché la Cina precede Stati Uniti e Canada. " +
            "Aggregati regionali esclusi; valori verificati anche tramite l'API pubblica World Bank.",
            new Entry("Russia", "16.376.870 km²", "Federazione Russa", "Russian Federation"),
            new Entry("Cina", "9.388.210 km²", "China", "Repubblica Popolare Cinese"),
            new Entry("Stati Uniti", "9.147.420 km²", "USA", "US", "United States", "Stati Uniti d'America"),
            new Entry("Canada", "8.788.700 km²"),
            new Entry("Brasile", "8.358.140 km²", "Brazil", "Brasil"),
            new Entry("Australia", "7.692.020 km²"),
            new Entry("India", "2.973.190 km²"),
            new Entry("Argentina", "2.736.690 km²"),
            new Entry("Kazakistan", "2.699.700 km²", "Kazakhstan", "Kazahstan"),
            new Entry("Algeria", "2.381.740 km²", "Algerie")
        ),
        new RankedList(
            "airports-passengers-2025", "Gli aeroporti più trafficati",
            "Passeggeri nel 2025. Scrivi la città o l'aeroporto.",
            "Mondo", "plane", "ACI World · Airport rankings",
            "https://aci.aero/resources/busiest-airports-in-the-world/", "2025",
            "Traffico totale di passeggeri, nazionali e internazionali, anno 2025: " +
            "prime dieci posizioni ACI World. La tabella contrassegna i dati come preliminari. " +
            "Ogni casella rappresenta un singolo aeroporto, non la somma degli aeroporti di una città.",
            new Entry("Atlanta", "106.302.208 · ATL", "ATL", "Hartsfield Jackson", "Hartsfield-Jackson Atlanta", "Atlanta International"),
            new Entry("Dubai", "95.192.160 · DXB", "DXB", "Dubai International", "Aeroporto di Dubai"),
            new Entry("Tokyo Haneda", "91.679.814 · HND", "Tokyo", "Haneda", "HND", "Tokyo International"),
            new Entry("Dallas-Fort Worth", "85.660.127 · DFW", "Dallas", "Fort Worth", "DFW", "Dallas Fort Worth"),
            new Entry("Shanghai Pudong", "84.994.548 · PVG", "Shanghai", "Pudong", "PVG"),
            new Entry("Chicago O'Hare", "84.856.018 · ORD", "Chicago", "O'Hare", "Ohare", "ORD"),
            new Entry("Londra Heathrow", "84.482.126 · LHR", "Londra", "London", "Heathrow", "LHR", "London Heathrow"),
            new Entry("Istanbul", "84.437.710 · IST", "IST", "Aeroporto di Istanbul", "Istanbul Airport"),
            new Entry("Guangzhou Baiyun", "83.582.952 · CAN", "Guangzhou", "Canton", "Baiyun", "CAN"),
            new Entry("Denver", "82.427.962 · DEN", "DEN", "Denver International")
        ),
        new RankedList(
            "coffee-production-2025-26", "I grandi produttori di caffè",
            "Milioni di sacchi da 60 kg: la stagione 2025/26.",
            "Curiosità", "coffee", "USDA · Foreign Agricultural Service",
            "https://fas-prod.azureedge.us/data/production/0711100", "2025/26",
            "Produzione di caffè verde nella stagione commerciale 2025/26, " +
            "stime USDA Production, Supply and Distribution. Milioni di sacchi da 60 kg, " +
            "somma di arabica e robusta. Non sono consumi o esportazioni; " +
            "si mantiene la fotografia del prospetto consultato il 28 ottobre 2026.",
            new Entry("Brasile", "63,00 milioni di sacchi", "Brazil", "Brasil"),
            new Entry("Vietnam", "31,70 milioni di sacchi", "Viet Nam"),
            new Entry("Colombia", "12,50 milioni di sacchi"),
            new Entry("Indonesia", "12,37 milioni di sacchi"),
            new Entry("Etiopia", "11,56 milioni di sacchi", "Ethiopia"),
            new Entry("Uganda", "7,10 milioni di sacchi"),
            new Entry("India", "6,43 milioni di sacchi"),
            new Entry("Honduras", "5,53 milioni di sacchi"),
            new Entry("Perù", "4,76 milioni di sacchi", "Peru"),
            new Entry("Messico", "4,08 milioni di sacchi", "Mexico")
        ),
        new RankedList(
            "worldwide-film-gross-oct-2026", "I film da record al botteghino",
            "Incassi mondiali di sempre: fotografia al 25 ottobre 2026.",
            "Cultura", "film", "Box Office Mojo · Worldwide",
            "https://www.boxofficemojo.com/chart/ww_top_lifetime_gross/?offset=0", "25 ott. 2026",
            "Worldwide lifetime gross in dollari USA nella tabella Box Office Mojo " +
            "aggiornata al 25 ottobre 2026. Include riedizioni; non è corretto per l'inflazione. " +
            "I valori sono arrotondati al milione di dollari, senza modificare l'ordine della fonte.",
            new Entry("Avengers: Endgame", "2.941 milioni US$", "Endgame", "Avengers End Game"),
            new Entry("Avatar", "2.924 milioni US$", "Avatar 2009"),
            new Entry("Spider-Man: Brand New Day", "2.517 milioni US$", "Brand New Day", "Spider Man Brand New Day", "Spiderman Brand New Day"),
            new Entry("Avatar: La via dell'acqua", "2.334 milioni US$", "Avatar 2", "Avatar The Way of Water", "The Way of Water", "La via dell'acqua"),
            new Entry("Ne Zha 2", "2.271 milioni US$", "Nezha 2", "Ne Zha II"),
            new Entry("Titanic", "2.265 milioni US$"),
            new Entry("Star Wars: Il risveglio della Forza", "2.071 milioni US$", "Il risveglio della Forza", "The Force Awakens", "Star Wars 7", "Star Wars Episode VII The Force Awakens"),
            new Entry("Avengers: Infinity War", "2.052 milioni US$", "Infinity War"),
            new Entry("Spider-Man: No Way Home", "1.921 milioni US$", "No Way Home", "Spiderman No Way Home"),
            new Entry("Zootropolis 2", "1.870 milioni US$", "Zootopia 2", "Zootropolis II")
        ),
        new RankedList(
            "italian-state-museums-2024", "I luoghi della cultura più visitati",
            "Musei e siti statali italiani per visitatori nel 2024.",
            "Italia", "museum", "Ministero della Cultura · Top 30",
            "https://cultura.gov.it/comunicato/27590", "2024",
            "Prime dieci righe della Top 30 MiC 2024, pubblicata il 13 maggio 2025. " +
            "Visitatori di musei, monumenti e aree archeologiche statali del prospetto. " +
            "I complessi restano raggruppati come nella fonte: Villa Adriana e Villa d'Este " +
            "valgono una casella. Sono esclusi i Musei Vaticani e gli istituti non statali.",
            new Entry("Colosseo", "14.733.395 visitatori", "Parco archeologico del Colosseo", "Colosseum"),
            new Entry("Uffizi", "5.294.968 visitatori", "Gallerie degli Uffizi", "Galleria degli Uffizi"),
            new Entry("Pompei", "4.267.233 visitatori", "Parco archeologico di Pompei", "Scavi di Pompei", "Pompeii"),
            new Entry("Pantheon", "4.086.947 visitatori", "Pantheon di Roma"),
            new Entry("Accademia di Firenze", "2.189.103 visitatori", "Galleria dell'Accademia", "Galleria dell'Accademia di Firenze", "Gallerie dell'Accademia di Firenze"),
            new Entry("Castel Sant'Angelo", "1.315.063 visitatori", "Castel Santangelo", "Museo Nazionale di Castel Sant'Angelo", "Castello di Sant'Angelo"),
            new Entry("Museo Egizio", "1.036.689 visitatori", "Museo Egizio di Torino", "Museo delle Antichità Egizie"),
            new Entry("Reggia di Caserta", "902.410 visitatori", "Caserta", "Palazzo Reale di Caserta"),
            new Entry("Ville di Tivoli", "729.907 visitatori", "Villa Adriana e Villa d'Este", "Villa Adriana", "Villa d'Este", "Villa Deste"),
            new Entry("Musei del Bargello", "720.470 visitatori", "Bargello", "Museo del Bargello", "Museo nazionale del Bargello")
        ),
        new RankedList(
            "spotify-wrapped-artists-2024", "Le star di Spotify Wrapped 2024",
            "I dieci artisti più ascoltati al mondo nel riepilogo Spotify.",
            "Cultura", "music", "Spotify · Wrapped 2024", SPOTIFY_WRAPPED, "Wrapped 2024",
            "Graduatoria globale di artisti pubblicata da Spotify per Wrapped il 4 dicembre 2024. " +
            "È il riepilogo della piattaforma, non una misura di tutta la musica mondiale. " +
            "La fotografia Wrapped non copre le ultime settimane di dicembre.",
            new Entry("Taylor Swift", "Artista globale · Wrapped", "Taylor", "Swift"),
            new Entry("The Weeknd", "Artista globale · Wrapped", "Weeknd", "Abel Tesfaye"),
            new Entry("Bad Bunny", "Artista globale · Wrapped"),
            new Entry("Drake", "Artista globale · Wrapped"),
            new Entry("Billie Eilish", "Artista globale · Wrapped", "Billie"),
            new Entry("Travis Scott", "Artista globale · Wrapped"),
            new Entry("Peso Pluma", "Artista globale · Wrapped"),
            new Entry("Kanye West", "Artista globale · Wrapped", "Kanye", "Ye"),
            new Entry("Ariana Grande", "Artista globale · Wrapped", "Ariana"),
            new Entry("Feid", "Artista globale · Wrapped", "Ferxxo")
        ),
        new RankedList(
            "spotify-wrapped-songs-2024", "Le canzoni di Spotify Wrapped 2024",
            "Scrivi il titolo di una delle dieci hit globali del riepilogo.",
            "Cultura", "music", "Spotify · Wrapped 2024", SPOTIFY_WRAPPED, "Wrapped 2024",
            "Brani più ascoltati globalmente nella lista Wrapped pubblicata da Spotify " +
            "il 4 dicembre 2024. Si indovinano i titoli, non gli interpreti. " +
            "Non è la classifica definitiva dell'intero anno solare: dicembre può cambiare l'ordine.",
            new Entry("Espresso", "Sabrina Carpenter"),
            new Entry("Beautiful Things", "Benson Boone"),
            new Entry("Birds of a Feather", "Billie Eilish"),
            new Entry("Gata Only", "FloyyMenor · Cris MJ"),
            new Entry("Lose Control", "Teddy Swims"),
            new Entry("End of Beginning", "Djo"),
            new Entry("Too Sweet", "Hozier"),
            new Entry("One of the Girls", "The Weeknd · JENNIE · L.-R. Depp", "One Of The Girls with Jennie Lily Rose Depp"),
            new Entry("Cruel Summer", "Taylor Swift"),
            new Entry("Die With a Smile", "Lady Gaga · Bruno Mars")
        ),
        new RankedList(
            "nintendo-switch-bestsellers-june-2026", "I bestseller di Nintendo Switch",
            "Giochi da milioni di copie, al 30 giugno 2026.",
            "Cultura", "gamepad", "Nintendo · Investor Relations",
            "https://www.nintendo.co.jp/ir/en/finance/software/switch.html", "30 giu. 2026",
            "Classifica Nintendo IR dei propri titoli Switch: vendite consolidate mondiali " +
            "cumulative, incluse copie digitali e bundle. Milioni di unità. " +
            "Le coppie Pokémon restano aggregate come nella fonte. Le edizioni fisiche Switch 2 " +
            "sono conteggiate da Nintendo separatamente, mentre i download seguono la relativa nota IR.",
            new Entry("Mario Kart 8 Deluxe", "71,53 milioni di copie", "Mario Kart 8", "MK8 Deluxe", "MK8D"),
            new Entry("Animal Crossing: New Horizons", "50,29 milioni di copie", "Animal Crossing", "New Horizons", "ACNH"),
            new Entry("Super Smash Bros. Ultimate", "38,14 milioni di copie", "Smash Ultimate", "Super Smash Bros Ultimate", "Smash Bros", "Smash Bros Ultimate"),
            new Entry("Zelda: Breath of the Wild", "34,06 milioni di copie", "The Legend of Zelda Breath of the Wild", "Breath of the Wild", "BOTW"),
            new Entry("Super Mario Odyssey", "30,80 milioni di copie", "Mario Odyssey", "Odyssey"),
            new Entry("Pokémon Scarlatto e Violetto", "28,46 milioni di copie", "Pokemon Scarlatto", "Pokemon Violetto", "Pokemon Scarlet", "Pokemon Violet", "Pokemon Scarlet Pokemon Violet", "Scarlatto", "Violetto"),
            new Entry("Pokémon Spada e Scudo", "27,26 milioni di copie", "Pokemon Spada", "Pokemon Scudo", "Pokemon Sword", "Pokemon Shield", "Pokemon Sword Pokemon Shield", "Spada", "Scudo"),
            new Entry("Zelda: Tears of the Kingdom", "22,71 milioni di copie", "The Legend of Zelda Tears of the Kingdom", "Tears of the Kingdom", "TOTK"),
            new Entry("Super Mario Party", "21,36 milioni di copie", "Mario Party"),
            new Entry("New Super Mario Bros. U Deluxe", "19,10 milioni di copie", "New Super Mario Bros U", "Super Mario Bros U Deluxe", "Mario Bros U Deluxe", "NSMBU Deluxe")
        )
    ));
}
