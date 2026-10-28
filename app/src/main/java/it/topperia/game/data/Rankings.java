package it.topperia.game.data;

import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankedList.Entry;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline editorial snapshots. Each list carries its own date, unit and source. */
public final class Rankings {
    private Rankings() { }

    public static final List<RankedList> ALL = build();

    private static List<RankedList> build() {
        List<RankedList> catalog = new ArrayList<>(Arrays.asList(
        new RankedList(
            "beer-2024",
            "I paesi che bevono più birra",
            "Litri per abitante, in un anno. Chi finisce sul podio?",
            "Curiosità", "beer",
            "Kirin Holdings · Global Beer Consumption",
            "https://www.kirinholdings.com/en/newsroom/release/2025/1222_01.pdf",
            "2024",
            "Consumo annuo pro capite, in litri, tabella 3. " +
            "Indagine Kirin su 170 paesi e regioni: si confronta il consumo per abitante, " +
            "non il volume totale. Report pubblicato il 22 dicembre 2025.",
            new Entry("Repubblica Ceca", "148,8 L / abitante", "Cechia", "Czechia", "Czech Republic", "Rep Ceca"),
            new Entry("Lituania", "110,6 L / abitante", "Lithuania"),
            new Entry("Austria", "104,6 L / abitante"),
            new Entry("Irlanda", "99,0 L / abitante", "Ireland", "Eire", "Repubblica d'Irlanda"),
            new Entry("Croazia", "95,1 L / abitante", "Croatia"),
            new Entry("Estonia", "93,2 L / abitante"),
            new Entry("Spagna", "91,8 L / abitante", "Spain", "España"),
            new Entry("Slovenia", "88,4 L / abitante"),
            new Entry("Romania", "87,4 L / abitante"),
            new Entry("Germania", "86,9 L / abitante", "Germany", "Deutschland")
        ),
        new RankedList(
            "population-2025",
            "I paesi più popolosi del mondo",
            "Miliardi di persone, dieci soli posti in classifica.",
            "Mondo", "globe",
            "ONU · UNData / World Population Prospects",
            "https://unstats.un.org/UNSDWebsite/publications/pocketbook/world-stats-pocketbook-2025.pdf",
            "2025",
            "Popolazione a metà 2025: proiezioni ONU, revisione WPP 2024, " +
            "riportate nello Statistical Pocketbook 2025 e in UNData. " +
            "Valori arrotondati al decimo di milione. Si confrontano paesi, non aggregati regionali.",
            new Entry("India", "1.463,9 milioni"),
            new Entry("Cina", "1.416,1 milioni", "China", "Repubblica Popolare Cinese"),
            new Entry("Stati Uniti", "347,3 milioni", "USA", "U.S.A.", "US", "United States", "Stati Uniti d'America", "America"),
            new Entry("Indonesia", "285,7 milioni"),
            new Entry("Pakistan", "255,2 milioni"),
            new Entry("Nigeria", "237,5 milioni"),
            new Entry("Brasile", "212,8 milioni", "Brazil", "Brasil"),
            new Entry("Bangladesh", "175,7 milioni", "Bangla Desh"),
            new Entry("Russia", "144,0 milioni", "Federazione Russa", "Russian Federation"),
            new Entry("Etiopia", "135,5 milioni", "Ethiopia")
        ),
        new RankedList(
            "paris-2024",
            "Il medagliere di Parigi 2024",
            "Chi ha conquistato più ori alle Olimpiadi?",
            "Sport", "trophy",
            "Australian Olympic Committee · Paris 2024",
            "https://www.olympics.com.au/games/paris-2024/medal-table/",
            "2024",
            "Ordine ufficiale del medagliere dei Giochi olimpici estivi di Parigi 2024: " +
            "prima gli ori, poi gli argenti e infine i bronzi. Non è la classifica " +
            "per numero totale di medaglie. Valori: oro / argento / bronzo.",
            new Entry("Stati Uniti", "40 ori · 44 arg. · 42 br.", "USA", "US", "United States", "Stati Uniti d'America", "America"),
            new Entry("Cina", "40 ori · 27 arg. · 24 br.", "China", "Repubblica Popolare Cinese"),
            new Entry("Giappone", "20 ori · 12 arg. · 13 br.", "Japan"),
            new Entry("Australia", "18 ori · 19 arg. · 16 br."),
            new Entry("Francia", "16 ori · 26 arg. · 22 br.", "France"),
            new Entry("Paesi Bassi", "15 ori · 7 arg. · 12 br.", "Olanda", "Netherlands", "Holland"),
            new Entry("Gran Bretagna", "14 ori · 22 arg. · 29 br.", "Regno Unito", "UK", "Great Britain", "Team GB", "GB"),
            new Entry("Corea del Sud", "13 ori · 9 arg. · 10 br.", "Sud Corea", "South Korea", "Repubblica di Corea"),
            new Entry("Italia", "12 ori · 13 arg. · 15 br.", "Italy"),
            new Entry("Germania", "12 ori · 13 arg. · 8 br.", "Germany", "Deutschland")
        ),
        new RankedList(
            "skyscrapers-2026",
            "I grattacieli più alti del mondo",
            "Dieci edifici che toccano il cielo. Li conosci?",
            "Architettura", "building",
            "The Skyscraper Center · Council on Vertical Urbanism",
            "https://www.skyscrapercenter.com/buildings",
            "Ottobre 2026",
            "Edifici completati, ordinati per altezza architettonica: guglie incluse, " +
            "antenne escluse. Metri arrotondati. Guangzhou e Tianjin sono pari merito " +
            "all'ottavo posto (530 m); le caselle 8 e 9 seguono l'ordine della fonte.",
            new Entry("Burj Khalifa", "828 m · Dubai", "Burj", "Khalifa", "Burj Dubai"),
            new Entry("Merdeka 118", "679 m · Kuala Lumpur", "Merdeka", "Menara Merdeka Maybank", "Merdeka Maybank Tower", "PNB 118", "KL 118"),
            new Entry("Shanghai Tower", "632 m · Shanghai", "Torre di Shanghai", "Torre Shanghai"),
            new Entry("Makkah Clock Tower", "601 m · La Mecca", "Makkah Royal Clock Tower", "Mecca Royal Clock Tower", "Abraj Al Bait", "Torre dell'orologio della Mecca", "Torre della Mecca"),
            new Entry("Ping An Finance Center", "599 m · Shenzhen", "Ping An", "Ping An Finance Centre", "Pingan"),
            new Entry("Lotte World Tower", "555 m · Seoul", "Lotte Tower", "Lotte"),
            new Entry("One World Trade Center", "541 m · New York", "One WTC", "1 WTC", "Freedom Tower", "World Trade Center"),
            new Entry("Guangzhou CTF", "530 m · 8º ex aequo", "Guangzhou CTF Finance Centre", "Guangzhou CTF Finance Center", "Canton CTF", "CTF Guangzhou", "CTF Canton"),
            new Entry("Tianjin CTF", "530 m · 8º ex aequo", "Tianjin CTF Finance Centre", "Tianjin CTF Finance Center", "CTF Tianjin"),
            new Entry("CITIC Tower", "528 m · Pechino", "China Zun", "CITIC", "Torre CITIC")
        ),
        new RankedList(
            "mountains",
            "Le montagne più alte della Terra",
            "Oltre le nuvole: le dieci vette più alte sul mare.",
            "Natura", "mountain",
            "Guinness World Records · Highest mountains",
            "https://www.guinnessworldrecords.com/world-records/tallest-mountain",
            "Ottobre 2026",
            "Altitudine sopra il livello medio del mare, non altezza dalla base. " +
            "Si considerano le montagne principali, non le cime secondarie dello " +
            "stesso massiccio. Everest arrotondato al metro; classifica Guinness, " +
            "confrontata con NASA Earth Observatory, The Eight-Thousanders.",
            new Entry("Everest", "8.849 m", "Monte Everest", "Mount Everest", "Sagarmatha", "Chomolungma"),
            new Entry("K2", "8.611 m", "K 2", "Monte K2", "Mount K2", "Chogori", "Godwin Austen"),
            new Entry("Kangchenjunga", "8.586 m", "Kanchenjunga", "Kangchenjunga I", "Kangchenjunga 1", "Kanchanjunga", "Kangchenzonga"),
            new Entry("Lhotse", "8.516 m"),
            new Entry("Makalu", "8.485 m"),
            new Entry("Cho Oyu", "8.188 m", "Cho You", "Chooyu"),
            new Entry("Dhaulagiri", "8.167 m", "Dhaulagiri I", "Dhaulagiri 1"),
            new Entry("Manaslu", "8.163 m"),
            new Entry("Nanga Parbat", "8.126 m", "Nangaparbat", "Diamir"),
            new Entry("Annapurna", "8.091 m", "Annapurna I", "Annapurna 1")
        ),
        new RankedList(
            "moons",
            "Le lune più grandi del Sistema solare",
            "Satelliti naturali, dal più grande al più piccolo.",
            "Spazio", "moon",
            "NASA / NSSDCA · Planetary Fact Sheets",
            "https://nssdc.gsfc.nasa.gov/planetary/factsheet/",
            "Ottobre 2026",
            "Dieci satelliti naturali per diametro medio, in km arrotondati. " +
            "Dati NASA delle schede di Giove, Saturno, Urano, Nettuno e della Luna. " +
            "Per Rea il diametro medio è ricavato dai tre raggi tabulati. " +
            "Pianeti, pianeti nani e asteroidi sono esclusi.",
            new Entry("Ganimede", "5.262 km · Giove", "Ganymede", "Giove III", "Jupiter III"),
            new Entry("Titano", "5.150 km · Saturno", "Titan", "Saturno VI"),
            new Entry("Callisto", "4.821 km · Giove", "Giove IV"),
            new Entry("Io", "3.643 km · Giove", "Giove I"),
            new Entry("Luna", "3.475 km · Terra", "Moon", "La Luna", "Luna terrestre", "Selene"),
            new Entry("Europa", "3.122 km · Giove", "Giove II"),
            new Entry("Tritone", "2.707 km · Nettuno", "Triton", "Nettuno I"),
            new Entry("Titania", "1.578 km · Urano", "Urano III"),
            new Entry("Rea", "1.527 km · Saturno", "Rhea", "Saturno V"),
            new Entry("Oberon", "1.523 km · Urano", "Urano IV")
        )
        ));
        catalog.addAll(NatureRankings.all());
        catalog.addAll(WorldRankings.all());
        return Collections.unmodifiableList(catalog);
    }

    public static List<RankedList> all() { return ALL; }

    public static RankedList byId(String id) {
        for (RankedList list : ALL) if (list.id.equals(id)) return list;
        return null;
    }
}
