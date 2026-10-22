package com.harley.mangafox.mfx.ikigaimangas

import com.harley.mangafox.logic.directorio.Genero

/**
 * Generos del filtro del directorio de Ikigai Mangas (ids snow
 * flake que la web manda como generos[]). Evidencia: lista del
 * mantenedor de la extension de keiyoushi (21-oct-2026); el
 * form Qwik de /series/ los pinta en JS y no son scrapeables en
 * HTML estatico confiable.
 */
val GENEROS_IKIGAI: List<Genero> = listOf(
    Genero("906409351272792067", "+18"),
    Genero("906397904327999491", "Acción"),
    Genero("906409527934582787", "Adulto"),
    Genero("906409378635186179", "Apocalíptico"),
    Genero("906397904169861123", "Artes Marciales"),
    Genero("906397904061530115", "Aventura"),
    Genero("906409351330037763", "Boys Love"),
    Genero("906409468787720195", "Ciencia Ficción"),
    Genero("906398112851165187", "Comedia"),
    Genero("906397904115531779", "Demonios"),
    Genero("906410143226462211", "Deportes"),
    Genero("1187154685166452739", "Doujinshi"),
    Genero("906397903933407235", "Drama"),
    Genero("906409370648543235", "Ecchi"),
    Genero("906409382485884931", "Familia"),
    Genero("906397894348570627", "Fantasía"),
    Genero("1093357252096753667", "Gender Bender"),
    Genero("906409644012961795", "Girls Love"),
    Genero("906409472386203651", "Gore"),
    Genero("1182242384692314113", "Guideverse"),
    Genero("906397904221962243", "Harem"),
    Genero("906424352006438914", "Harem Inverso"),
    Genero("906398112923385859", "Histórico"),
    Genero("906423434084679682", "Horror"),
    Genero("906409454067646467", "Isekai"),
    Genero("906409501957390339", "Josei"),
    Genero("906409612041551875", "Maduro"),
    Genero("906409459593347075", "Magia"),
    Genero("1187155307072782337", "Manga"),
    Genero("906409472453410819", "Mecha"),
    Genero("906409472509739011", "Militar"),
    Genero("906409374254727171", "Misterio"),
    Genero("1182242409543827457", "Omegaverse"),
    Genero("906409351382073347", "Psicológico"),
    Genero("906424676182294530", "Realidad Virtual"),
    Genero("906409508165124099", "Recuentos de la vida"),
    Genero("906409400553046019", "Reencarnación"),
    Genero("906397894469255171", "Regresion"),
    Genero("906397894527549443", "Romance"),
    Genero("906397903999959043", "Seinen"),
    Genero("906398112991150083", "Shonen"),
    Genero("906397894408372227", "Shoujo"),
    Genero("1187155022664531971", "Shoujo Ai"),
    Genero("1187155082787848194", "Shounen Ai"),
    Genero("906409408107216899", "Sistema"),
    Genero("906409419999641603", "Smut"),
    Genero("906410027513937923", "Supernatural"),
    Genero("906409454130921475", "Supervivencia"),
    Genero("906409449984655363", "Tragedia"),
    Genero("906409378688663555", "Transmigración"),
    Genero("906409508232822787", "Vida Escolar"),
    Genero("906409432216403971", "Yaoi"),
    Genero("906409472567017475", "Yuri"),
)
