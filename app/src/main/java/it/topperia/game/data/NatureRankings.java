package it.topperia.game.data;

import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankedList.Entry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independently sourced nature, science, and space rounds; immutable offline snapshots. */
public final class NatureRankings {
    private NatureRankings() { }

    public static List<RankedList> all() { return ALL; }

    public static final List<RankedList> ALL = Collections.unmodifiableList(Arrays.asList(
        new RankedList("italian-lakes-2019", "I laghi italiani più grandi",
            "Superficie totale in km² · tabella Istat 2019.", "Natura", "water",
            "Istat · Olimpiadi di statistica", "https://www.istat.it/wp-content/uploads/2018/11/prove-I-II-testo.pdf", "2019",
            "Classifica per superficie della tabella Istat delle Olimpiadi di statistica 2019. " +
            "Comprende i laghi costieri Varano e Lesina. Per Maggiore e Lugano conta l'intero lago, " +
            "anche oltreconfine, non soltanto la porzione italiana. Misure della fonte, non rilievi attuali.",
            new Entry("Garda", "370,0 km²", "Lago di Garda", "Benaco", "Lake Garda"),
            new Entry("Maggiore", "212,2 km²", "Lago Maggiore", "Verbano", "Lake Maggiore"),
            new Entry("Como", "145,9 km²", "Lago di Como", "Lario", "Lake Como"),
            new Entry("Trasimeno", "128,0 km²", "Lago Trasimeno", "Lago di Trasimeno", "Lake Trasimeno"),
            new Entry("Bolsena", "114,5 km²", "Lago di Bolsena", "Vulsino", "Lake Bolsena"),
            new Entry("Iseo", "65,3 km²", "Lago d'Iseo", "Lago di Iseo", "Sebino", "Lake Iseo"),
            new Entry("Varano", "60,5 km²", "Lago di Varano", "Lake Varano"),
            new Entry("Bracciano", "57,5 km²", "Lago di Bracciano", "Sabatino", "Lake Bracciano"),
            new Entry("Lesina", "51,4 km²", "Lago di Lesina", "Lake Lesina"),
            new Entry("Lugano", "50,5 km²", "Lago di Lugano", "Ceresio", "Lake Lugano")
        ),
        new RankedList("italian-rivers-2023", "I grandi fiumi italiani per lunghezza",
            "Lunghezza in km · elenco CREA, rapporto 2023.", "Natura", "water",
            "CREA · Turismo rurale 2023",
            "https://www.crea.gov.it/documents/20126/0/TURISMO_RURALE_2023_DEF_WEB.pdf/91eceb13-3220-08b0-693b-08eadfc44feb?t=1709639699126", "2023",
            "Primi dieci nell'elenco dei principali fiumi italiani pubblicato da CREA, " +
            "Il patrimonio idrico nazionale, pagina 97. Lunghezza del corso come riportata: " +
            "per il Ticino comprende anche la parte svizzera. Non è un elenco di ogni fiume " +
            "che attraversa l'Italia per un breve tratto, come la Drava.",
            new Entry("Po", "652 km", "Fiume Po", "River Po"),
            new Entry("Adige", "410 km", "Fiume Adige", "Etsch"),
            new Entry("Tevere", "405 km", "Fiume Tevere", "Tiber"),
            new Entry("Adda", "313 km", "Fiume Adda"),
            new Entry("Oglio", "280 km", "Fiume Oglio"),
            new Entry("Tanaro", "276 km", "Fiume Tanaro"),
            new Entry("Ticino", "248 km", "Fiume Ticino"),
            new Entry("Arno", "241 km", "Fiume Arno"),
            new Entry("Piave", "220 km", "Fiume Piave"),
            new Entry("Reno", "210 km", "Fiume Reno", "Reno italiano", "Reno emiliano")
        ),
        new RankedList("freshwater-lakes-glwd", "I laghi d'acqua dolce più estesi",
            "Superficie in km² · database GLWD 2004 / NASA.", "Natura", "water",
            "NASA JPL · World Lakes", "https://largelakes.jpl.nasa.gov/world-lakes", "GLWD 2004",
            "Ordinamento dei laghi d'acqua dolce della tabella NASA JPL, basata prevalentemente " +
            "sul Global Lakes and Wetlands Database 2004. Sono esclusi i bacini salati, " +
            "compresi Caspio e Aral; Michigan e Huron restano due voci. Superfici della " +
            "fotografia storica del database, arrotondate al km².",
            new Entry("Superiore", "81.936 km²", "Lago Superiore", "Superior", "Lake Superior"),
            new Entry("Vittoria", "67.075 km²", "Lago Vittoria", "Victoria", "Lake Victoria"),
            new Entry("Huron", "59.757 km²", "Lago Huron", "Lake Huron"),
            new Entry("Michigan", "57.399 km²", "Lago Michigan", "Lake Michigan"),
            new Entry("Tanganica", "32.821 km²", "Tanganyika", "Lago Tanganica", "Lake Tanganyika"),
            new Entry("Bajkal", "31.925 km²", "Baikal", "Lago Bajkal", "Lago Baikal", "Lake Baikal"),
            new Entry("Grande Lago degli Orsi", "30.530 km²", "Great Bear", "Great Bear Lake", "Lago degli Orsi", "Gran Lago degli Orsi"),
            new Entry("Malawi", "29.252 km²", "Lago Malawi", "Nyasa", "Niassa", "Lago Niassa", "Lake Malawi", "Lake Nyasa"),
            new Entry("Grande Lago degli Schiavi", "27.816 km²", "Great Slave", "Great Slave Lake", "Lago degli Schiavi", "Gran Lago degli Schiavi"),
            new Entry("Erie", "25.691 km²", "Lago Erie", "Lake Erie")
        ),
        new RankedList("us-national-parks-2025", "I parchi USA più visitati",
            "Visite ricreative nel 2025 · soli National Parks.", "Natura", "park",
            "National Park Service · Visite 2025",
            "https://www.nps.gov/orgs/1207/03-13-26-2025-visitation-statsitics.htm", "2025",
            "Prime dieci unità con designazione National Park per visite ricreative nel 2025. " +
            "Una visita non equivale a una persona unica. Sono escluse le altre tipologie NPS, " +
            "come parkway, memoriali e aree ricreative. Dati pubblicati il 13 marzo 2026.",
            new Entry("Great Smoky Mountains", "11.527.939 visite", "Smoky Mountains", "Great Smoky", "Great Smoky Mountains National Park", "Parco Great Smoky Mountains"),
            new Entry("Zion", "4.984.525 visite", "Zion National Park", "Parco Zion"),
            new Entry("Yellowstone", "4.762.988 visite", "Yellowstone National Park", "Parco Yellowstone"),
            new Entry("Grand Canyon", "4.430.653 visite", "Grand Canyon National Park", "Gran Canyon", "Parco Grand Canyon"),
            new Entry("Yosemite", "4.278.413 visite", "Yosemite National Park", "Parco Yosemite"),
            new Entry("Rocky Mountain", "4.171.431 visite", "Rocky Mountains", "Rocky Mountain National Park", "Montagne Rocciose"),
            new Entry("Acadia", "4.079.318 visite", "Acadia National Park", "Parco Acadia"),
            new Entry("Grand Teton", "3.800.648 visite", "Grand Teton National Park", "Parco Grand Teton"),
            new Entry("Olympic", "3.584.187 visite", "Olympic National Park", "Parco Olympic"),
            new Entry("Glacier", "3.136.557 visite", "Glacier National Park", "Parco Glacier")
        ),
        new RankedList("large-constellations", "Le costellazioni più grandi",
            "Area apparente in gradi quadrati · confini IAU 1930.", "Spazio", "star",
            "RASC Calgary · Costellazioni", "https://calgary.rasc.ca/constellation.htm", "Confini IAU 1930",
            "Aree della sfera celeste dalla tabella RASC di Larry McNish, aggiornata nel 2018. " +
            "Si misura l'area delimitata dai confini IAU, non la distanza delle stelle.",
            new Entry("Idra", "1.302,844 gradi²", "Hydra", "Idra femmina"),
            new Entry("Vergine", "1.294,428 gradi²", "Virgo"),
            new Entry("Orsa Maggiore", "1.279,660 gradi²", "Ursa Major", "Grande Orsa"),
            new Entry("Balena", "1.231,411 gradi²", "Cetus"),
            new Entry("Ercole", "1.225,148 gradi²", "Hercules"),
            new Entry("Eridano", "1.137,919 gradi²", "Eridanus"),
            new Entry("Pegaso", "1.120,794 gradi²", "Pegasus"),
            new Entry("Dragone", "1.082,952 gradi²", "Draco", "Drago"),
            new Entry("Centauro", "1.060,422 gradi²", "Centaurus"),
            new Entry("Acquario", "979,854 gradi²", "Aquarius", "Aquario")
        ),
        new RankedList("small-constellations", "Le costellazioni più piccole",
            "Area crescente in gradi quadrati · confini IAU 1930.", "Spazio", "star",
            "RASC Calgary · Costellazioni", "https://calgary.rasc.ca/constellation.htm", "Confini IAU 1930",
            "La posizione 1 ha l'area minore. Tabella RASC 2018: confini IAU, " +
            "non luminosità o numero di stelle. Il Camaleonte precede il Triangolo boreale.",
            new Entry("Croce del Sud", "68,447 gradi²", "Crux", "Croce Australe", "Southern Cross"),
            new Entry("Cavallino", "71,641 gradi²", "Equuleus"),
            new Entry("Freccia", "79,932 gradi²", "Sagitta"),
            new Entry("Compasso", "93,353 gradi²", "Circinus"),
            new Entry("Scudo", "109,114 gradi²", "Scutum"),
            new Entry("Triangolo Australe", "109,978 gradi²", "Triangulum Australe", "Triangolo del Sud", "Southern Triangle"),
            new Entry("Reticolo", "113,936 gradi²", "Reticulum"),
            new Entry("Bulino", "124,865 gradi²", "Caelum"),
            new Entry("Corona Australe", "127,696 gradi²", "Corona Australis", "Corona del Sud", "Southern Crown"),
            new Entry("Camaleonte", "131,592 gradi²", "Chamaeleon", "Chameleon")
        ),
        new RankedList("first-elements", "I primi elementi della tavola periodica",
            "Numero atomico crescente, da 1 a 10 · IUPAC 2022.", "Scienza", "atom",
            "IUPAC · Tavola periodica 2022", "https://iupac.org/what-we-do/periodic-table-of-elements/", "2022",
            "Primi dieci elementi per numero atomico crescente: la posizione indica " +
            "il numero di protoni nel nucleo. Tavola IUPAC del 4 maggio 2022. " +
            "Si accettano anche i simboli chimici; non è una classifica per densità.",
            new Entry("Idrogeno", "H · numero atomico 1", "Hydrogen", "H"),
            new Entry("Elio", "He · numero atomico 2", "Helium", "He"),
            new Entry("Litio", "Li · numero atomico 3", "Lithium", "Li"),
            new Entry("Berillio", "Be · numero atomico 4", "Beryllium", "Be"),
            new Entry("Boro", "B · numero atomico 5", "Boron", "B"),
            new Entry("Carbonio", "C · numero atomico 6", "Carbon", "C"),
            new Entry("Azoto", "N · numero atomico 7", "Nitrogen", "N"),
            new Entry("Ossigeno", "O · numero atomico 8", "Oxygen", "O"),
            new Entry("Fluoro", "F · numero atomico 9", "Fluorine", "F"),
            new Entry("Neon", "Ne · numero atomico 10", "Ne")
        ),
        new RankedList("crust-elements-usgs", "Gli elementi più comuni nella crosta",
            "Percentuale in massa · tabella storica USGS 1967.", "Scienza", "atom",
            "USGS · Composition of Earth's Crust", "https://pubs.usgs.gov/pp/0440d/report.pdf", "1967",
            "Tabella 18 del Professional Paper USGS 440-D di R. L. Parker (1967), " +
            "con stime di Mason (1958). Percentuali in massa della crosta terrestre, " +
            "non dell'intero pianeta. È una serie storica: altre stime possono " +
            "differire, in particolare nell'ordine di potassio e magnesio.",
            new Entry("Ossigeno", "46,60% in massa", "Oxygen", "O"),
            new Entry("Silicio", "27,72% in massa", "Silicon", "Si"),
            new Entry("Alluminio", "8,13% in massa", "Aluminium", "Aluminum", "Al"),
            new Entry("Ferro", "5,00% in massa", "Iron", "Fe"),
            new Entry("Calcio", "3,63% in massa", "Calcium", "Ca"),
            new Entry("Sodio", "2,83% in massa", "Sodium", "Na"),
            new Entry("Potassio", "2,59% in massa", "Potassium", "K"),
            new Entry("Magnesio", "2,09% in massa", "Magnesium", "Mg"),
            new Entry("Titanio", "0,44% in massa", "Titanium", "Ti"),
            new Entry("Idrogeno", "0,14% in massa", "Hydrogen", "H")
        ),
        new RankedList("solar-bodies-diameter", "I corpi più grandi del Sistema solare",
            "Diametro in km · Sole escluso · schede NASA 2025.", "Spazio", "moon",
            "NASA NSSDCA · Planetary Fact Sheets", "https://nssdc.gsfc.nasa.gov/planetary/factsheet/", "2025",
            "Diametri tabulati NASA, Sole escluso: pianeti e satelliti naturali sono " +
            "confrontati insieme. Per i pianeti si usa il diametro equatoriale, per le lune " +
            "il diametro medio delle schede NASA. I due satelliti qui presenti superano Mercurio.",
            new Entry("Giove", "142.984 km", "Jupiter"),
            new Entry("Saturno", "120.536 km", "Saturn"),
            new Entry("Urano", "51.118 km", "Uranus"),
            new Entry("Nettuno", "49.528 km", "Neptune"),
            new Entry("Terra", "12.756 km", "Earth", "Pianeta Terra"),
            new Entry("Venere", "12.104 km", "Venus"),
            new Entry("Marte", "6.792 km", "Mars"),
            new Entry("Ganimede", "5.262 km", "Ganymede"),
            new Entry("Titano", "5.150 km", "Titan"),
            new Entry("Mercurio", "4.879 km", "Mercury")
        ),
        new RankedList("first-moonwalkers", "I primi uomini a camminare sulla Luna",
            "Ordine del primo passo · missioni Apollo 1969–1972.", "Spazio", "moon",
            "NASA · Moonwalkers", "https://science.nasa.gov/moon/moon-walkers/", "1969–1972",
            "Primi dieci astronauti nell'ordine del primo passo sulla superficie lunare. " +
            "I due di ogni missione mantengono l'ordine d'uscita, non quello alfabetico. " +
            "La serie termina con Apollo 16: Cernan e Schmitt furono undicesimo e dodicesimo.",
            new Entry("Neil Armstrong", "Apollo 11 · 1969", "Armstrong", "Neil Alden Armstrong"),
            new Entry("Buzz Aldrin", "Apollo 11 · 1969", "Aldrin", "Edwin Aldrin", "Edwin Buzz Aldrin", "Edwin Eugene Aldrin"),
            new Entry("Pete Conrad", "Apollo 12 · 1969", "Conrad", "Charles Conrad", "Charles Pete Conrad"),
            new Entry("Alan Bean", "Apollo 12 · 1969", "Bean", "Alan LaVern Bean"),
            new Entry("Alan Shepard", "Apollo 14 · 1971", "Shepard", "Alan B Shepard", "Alan Bartlett Shepard"),
            new Entry("Edgar Mitchell", "Apollo 14 · 1971", "Mitchell", "Ed Mitchell", "Edgar D Mitchell"),
            new Entry("David Scott", "Apollo 15 · 1971", "Scott", "Dave Scott", "David R Scott"),
            new Entry("James Irwin", "Apollo 15 · 1971", "Irwin", "Jim Irwin", "James B Irwin"),
            new Entry("John Young", "Apollo 16 · 1972", "Young", "John W Young"),
            new Entry("Charles Duke", "Apollo 16 · 1972", "Duke", "Charlie Duke", "Charles M Duke")
        ),
        new RankedList("first-soft-moon-landings", "I primi allunaggi riusciti",
            "Ordine cronologico · atterraggi morbidi 1966–1970.", "Spazio", "rocket",
            "NASA · Moon Missions", "https://science.nasa.gov/moon/missions/", "1966–1970",
            "Primi dieci allunaggi morbidi riusciti, con e senza equipaggio, " +
            "per data di atterraggio. Sono esclusi impatti, orbite, sorvoli e tentativi falliti. " +
            "Non si ordinano le date di lancio. Le date visualizzate sono quelle dell'allunaggio.",
            new Entry("Luna 9", "3 febbraio 1966", "Lunik 9", "Luna IX"),
            new Entry("Surveyor 1", "2 giugno 1966", "Surveyor I"),
            new Entry("Luna 13", "24 dicembre 1966", "Lunik 13", "Luna XIII"),
            new Entry("Surveyor 3", "20 aprile 1967", "Surveyor III"),
            new Entry("Surveyor 5", "11 settembre 1967", "Surveyor V"),
            new Entry("Surveyor 6", "10 novembre 1967", "Surveyor VI"),
            new Entry("Surveyor 7", "10 gennaio 1968", "Surveyor VII"),
            new Entry("Apollo 11", "20 luglio 1969", "Apollo XI", "Eagle", "Apollo 11 Eagle"),
            new Entry("Apollo 12", "19 novembre 1969", "Apollo XII", "Intrepid", "Apollo 12 Intrepid"),
            new Entry("Luna 16", "20 settembre 1970", "Lunik 16", "Luna XVI")
        ),
        new RankedList("mohs-index-minerals", "I minerali della scala di Mohs",
            "Minerali indice, dal più duro al più tenero · NPS 2023.", "Scienza", "gem",
            "National Park Service · Scala Mohs", "https://home.nps.gov/articles/mohs-hardness-scale.htm", "2023",
            "Dieci minerali di riferimento della scala di Mohs, in ordine di resistenza " +
            "al graffio decrescente. Non è la Top 10 di tutti i minerali esistenti. " +
            "La scala è ordinale: un valore doppio non significa durezza doppia.",
            new Entry("Diamante", "Durezza Mohs 10", "Diamond"),
            new Entry("Corindone", "Durezza Mohs 9", "Corundum"),
            new Entry("Topazio", "Durezza Mohs 8", "Topaz"),
            new Entry("Quarzo", "Durezza Mohs 7", "Quartz"),
            new Entry("Ortoclasio", "Durezza Mohs 6", "Orthoclase", "Orthose", "Ortose"),
            new Entry("Apatite", "Durezza Mohs 5"),
            new Entry("Fluorite", "Durezza Mohs 4", "Fluorspar"),
            new Entry("Calcite", "Durezza Mohs 3"),
            new Entry("Gesso", "Durezza Mohs 2", "Gypsum"),
            new Entry("Talco", "Durezza Mohs 1", "Talc")
        )
    ));
}
