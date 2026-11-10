package it.topperia.game.data;

import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankedList.Entry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Orders fixed by history or geography: they never change with a new edition of a statistic. */
public final class HistoryRankings {
    private HistoryRankings() { }

    public static List<RankedList> all() {
        return ALL;
    }

    public static final List<RankedList> ALL = Collections.unmodifiableList(Arrays.asList(
        new RankedList(
            "last-ten-popes-2025", "Gli ultimi dieci papi",
            "Dal più recente: chi sedeva sul soglio di Pietro?",
            "Storia", "crown", "Santa Sede · Sommi Pontefici",
            "https://www.vatican.va/content/vatican/it/holy-father.html", "2025",
            "I dieci pontefici più recenti, dal papa regnante all'indietro, con l'anno dell'elezione. " +
            "Si accettano il nome pontificale e il cognome di battesimo.",
            new Entry("Leone XIV", "eletto nel 2025", "Leo XIV", "Leone 14", "Prevost", "Robert Prevost", "Robert Francis Prevost"),
            new Entry("Francesco", "eletto nel 2013", "Papa Francesco", "Bergoglio", "Jorge Mario Bergoglio"),
            new Entry("Benedetto XVI", "eletto nel 2005", "Benedetto 16", "Ratzinger", "Joseph Ratzinger"),
            new Entry("Giovanni Paolo II", "eletto nel 1978", "Giovanni Paolo 2", "Wojtyla", "Karol Wojtyla"),
            new Entry("Giovanni Paolo I", "eletto nel 1978", "Giovanni Paolo 1", "Luciani", "Albino Luciani"),
            new Entry("Paolo VI", "eletto nel 1963", "Paolo 6", "Montini", "Giovanni Battista Montini"),
            new Entry("Giovanni XXIII", "eletto nel 1958", "Giovanni 23", "Roncalli", "Angelo Giuseppe Roncalli"),
            new Entry("Pio XII", "eletto nel 1939", "Pio 12", "Pacelli", "Eugenio Pacelli"),
            new Entry("Pio XI", "eletto nel 1922", "Pio 11", "Ratti", "Achille Ratti"),
            new Entry("Benedetto XV", "eletto nel 1914", "Benedetto 15", "Della Chiesa", "Giacomo Della Chiesa")
        ),
        new RankedList(
            "first-roman-emperors", "I primi imperatori romani",
            "Da Augusto in poi: dieci regni in ordine di successione.",
            "Storia", "crown", "Treccani · Imperatori romani",
            "https://www.treccani.it/enciclopedia/imperatori-romani/", "27 a.C.–81 d.C.",
            "Successione degli imperatori dalla nascita del principato, con l'anno di inizio del regno. " +
            "Nel 69, l'anno dei quattro imperatori, Galba, Otone, Vitellio e Vespasiano si susseguono nell'ordine della presa del potere.",
            new Entry("Augusto", "dal 27 a.C.", "Ottaviano", "Ottaviano Augusto", "Cesare Augusto", "Augustus"),
            new Entry("Tiberio", "dal 14", "Tiberius"),
            new Entry("Caligola", "dal 37", "Caligula", "Gaio Cesare"),
            new Entry("Claudio", "dal 41", "Claudius"),
            new Entry("Nerone", "dal 54", "Nero"),
            new Entry("Galba", "dal 68", "Servio Sulpicio Galba"),
            new Entry("Otone", "dal 69", "Otho", "Marco Salvio Otone"),
            new Entry("Vitellio", "dal 69", "Vitellius", "Aulo Vitellio"),
            new Entry("Vespasiano", "dal 69", "Vespasianus", "Tito Flavio Vespasiano"),
            new Entry("Tito", "dal 79", "Titus")
        ),
        new RankedList(
            "first-italian-presidents", "I primi presidenti della Repubblica",
            "Dieci capi dello Stato, nell'ordine in cui sono saliti al Quirinale.",
            "Italia", "flag", "Presidenza della Repubblica", "https://www.quirinale.it/page/presidenti", "1946–2006",
            "I primi dieci presidenti della Repubblica Italiana con gli anni del mandato. " +
            "Enrico De Nicola fu capo provvisorio dello Stato dal 1946 e assunse il titolo di presidente il 1° gennaio 1948.",
            new Entry("Enrico De Nicola", "1946–1948", "De Nicola"),
            new Entry("Luigi Einaudi", "1948–1955", "Einaudi"),
            new Entry("Giovanni Gronchi", "1955–1962", "Gronchi"),
            new Entry("Antonio Segni", "1962–1964", "Segni"),
            new Entry("Giuseppe Saragat", "1964–1971", "Saragat"),
            new Entry("Giovanni Leone", "1971–1978", "Leone"),
            new Entry("Sandro Pertini", "1978–1985", "Pertini", "Alessandro Pertini"),
            new Entry("Francesco Cossiga", "1985–1992", "Cossiga"),
            new Entry("Oscar Luigi Scalfaro", "1992–1999", "Scalfaro", "Oscar Scalfaro"),
            new Entry("Carlo Azeglio Ciampi", "1999–2006", "Ciampi", "Carlo Ciampi")
        ),
        new RankedList(
            "first-us-states", "I primi Stati degli USA",
            "Chi ha ratificato per primo la Costituzione americana?",
            "Storia", "flag", "U.S. House of Representatives · History",
            "https://history.house.gov/Institution/Origins-Development/Ratification-of-the-Constitution/", "1787–1788",
            "Ordine di ratifica della Costituzione degli Stati Uniti da parte delle tredici colonie, " +
            "con la data della ratifica. Si accettano i nomi in italiano e in inglese.",
            new Entry("Delaware", "7 dicembre 1787"),
            new Entry("Pennsylvania", "12 dicembre 1787", "Pensilvania"),
            new Entry("New Jersey", "18 dicembre 1787"),
            new Entry("Georgia", "2 gennaio 1788"),
            new Entry("Connecticut", "9 gennaio 1788"),
            new Entry("Massachusetts", "6 febbraio 1788"),
            new Entry("Maryland", "28 aprile 1788"),
            new Entry("Carolina del Sud", "23 maggio 1788", "South Carolina", "Sud Carolina"),
            new Entry("New Hampshire", "21 giugno 1788"),
            new Entry("Virginia", "25 giugno 1788")
        ),
        new RankedList(
            "sanremo-winners-1951-1960", "Le prime canzoni vincitrici di Sanremo",
            "Il primo decennio del Festival: indovina i titoli.",
            "Cultura", "music", "Festival di Sanremo · albo d'oro",
            "https://it.wikipedia.org/wiki/Festival_di_Sanremo", "1951–1960",
            "Canzoni vincitrici delle prime dieci edizioni del Festival di Sanremo, dal 1951 al 1960, " +
            "con gli interpreti. Si indovina il titolo; non sono inclusi testi delle canzoni.",
            new Entry("Grazie dei fiori", "1951 · Nilla Pizzi"),
            new Entry("Vola colomba", "1952 · Nilla Pizzi"),
            new Entry("Viale d'autunno", "1953 · Carla Boni e Flo Sandon's"),
            new Entry("Tutte le mamme", "1954 · Giorgio Consolini e Gino Latilla"),
            new Entry("Buongiorno tristezza", "1955 · Claudio Villa e Tullio Pane"),
            new Entry("Aprite le finestre", "1956 · Franca Raimondi"),
            new Entry("Corde della mia chitarra", "1957 · Claudio Villa e Nunzio Gallo"),
            new Entry("Nel blu dipinto di blu", "1958 · Domenico Modugno e Johnny Dorelli", "Volare"),
            new Entry("Piove", "1959 · Domenico Modugno e Johnny Dorelli", "Ciao ciao bambina", "Piove (ciao ciao bambina)"),
            new Entry("Romantica", "1960 · Renato Rascel e Tony Dallara")
        ),
        new RankedList(
            "northernmost-european-capitals", "Le capitali più a nord d'Europa",
            "Latitudine del centro città: dove fa più freddo?",
            "Mondo", "globe", "GeoNames", "https://www.geonames.org/", "2026",
            "Capitali degli Stati sovrani europei ordinate per latitudine del centro città, " +
            "arrotondata al decimo di grado. Non sono contati territori non sovrani come le Fær Øer.",
            new Entry("Reykjavík", "64,1° N"),
            new Entry("Helsinki", "60,2° N"),
            new Entry("Oslo", "59,9° N"),
            new Entry("Tallinn", "59,4° N"),
            new Entry("Stoccolma", "59,3° N", "Stockholm"),
            new Entry("Riga", "57,0° N"),
            new Entry("Mosca", "55,8° N", "Moscow", "Moskva"),
            new Entry("Copenaghen", "55,7° N", "Copenhagen", "København", "Kobenhavn"),
            new Entry("Vilnius", "54,7° N"),
            new Entry("Minsk", "53,9° N")
        )
    ));
}
