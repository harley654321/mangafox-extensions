package com.harley.mangafox.mfx.manhwaweb

import com.harley.mangafox.logic.directorio.Genero

/**
 * Generos del directorio de ManhwaWeb (ids enteros: la API los
 * filtra como generes=<id> unidos por 'a' y los devuelve en
 * _categoris). Evidencia: lista del mantenedor de la extension
 * de keiyoushi (21-oct-2026); la API no expone catalogo propio.
 */
val GENEROS_MANHWAWEB: List<Genero> = listOf(
    Genero("3", "Acción"),
    Genero("29", "Aventura"),
    Genero("18", "Comedia"),
    Genero("1", "Drama"),
    Genero("42", "Recuentos de la vida"),
    Genero("2", "Romance"),
    Genero("5", "Venganza"),
    Genero("6", "Harem"),
    Genero("23", "Fantasia"),
    Genero("31", "Sobrenatural"),
    Genero("25", "Tragedia"),
    Genero("43", "Psicológico"),
    Genero("32", "Horror"),
    Genero("44", "Thriller"),
    Genero("28", "Historias cortas"),
    Genero("30", "Ecchi"),
    Genero("34", "Gore"),
    Genero("27", "Girls love"),
    Genero("45", "Boys love"),
    Genero("41", "Reencarnación"),
    Genero("37", "Sistema de niveles"),
    Genero("33", "Ciencia ficción"),
    Genero("38", "Apocalíptico"),
    Genero("39", "Artes Marciales"),
    Genero("40", "Superpoderes"),
    Genero("35", "Cultivación (cultivo)"),
    Genero("8", "Milf"),
)

/** id -> nombre para pintar los generos de la ficha (la API
 *  solo devuelve los ids en _categoris). */
val NOMBRES_GENEROS: Map<Int, String> = mapOf(
    3 to "Acción",
    29 to "Aventura",
    18 to "Comedia",
    1 to "Drama",
    42 to "Recuentos de la vida",
    2 to "Romance",
    5 to "Venganza",
    6 to "Harem",
    23 to "Fantasia",
    31 to "Sobrenatural",
    25 to "Tragedia",
    43 to "Psicológico",
    32 to "Horror",
    44 to "Thriller",
    28 to "Historias cortas",
    30 to "Ecchi",
    34 to "Gore",
    27 to "Girls love",
    45 to "Boys love",
    41 to "Reencarnación",
    37 to "Sistema de niveles",
    33 to "Ciencia ficción",
    38 to "Apocalíptico",
    39 to "Artes Marciales",
    40 to "Superpoderes",
    35 to "Cultivación (cultivo)",
    8 to "Milf",
)
