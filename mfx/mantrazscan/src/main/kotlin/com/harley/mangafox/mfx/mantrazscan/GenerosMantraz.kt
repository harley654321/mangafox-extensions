package com.harley.mangafox.mfx.mantrazscan

import com.harley.mangafox.logic.directorio.Genero

/**
 * Géneros del directorio de Mantraz Scan. Subconjunto curado
 * y VERIFICADO EN VIVO (26-oct-2026) de los ~300 enlaces
 * exp-genre que pinta /explorar/ (la mayoría son etiquetas de
 * scan/ruido: nombres de grupos, adjetivos sueltos, tags +18
 * duplicadas). Los 19 elegidos existen como slug real en el
 * sitio (evidencia keiyoushi GenreFilter + autopsia local).
 */
val GENEROS_MANTRAZ: List<Genero> = listOf(
    Genero("romance", "Romance"),
    Genero("drama", "Drama"),
    Genero("fantasia", "Fantasía"),
    Genero("comedia", "Comedia"),
    Genero("accion", "Acción"),
    Genero("aventura", "Aventura"),
    Genero("harem", "Harem"),
    Genero("isekai", "Isekai"),
    Genero("manhwa", "Manhwa"),
    Genero("manga", "Manga"),
    Genero("manhua", "Manhua"),
    Genero("shounen", "Shounen"),
    Genero("seinen", "Seinen"),
    Genero("bl", "BL"),
    Genero("yaoi", "Yaoi"),
    Genero("yuri", "Yuri"),
    Genero("18", "+18"),
    Genero("sin-censura", "Sin censura"),
    Genero("misterio", "Misterio")
)
