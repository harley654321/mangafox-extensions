package com.harley.mangafox.mfx.ikigaimangas

import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * Utilidades de HTML para la extension ikigaimangas.
 *
 * Autopsia del bug v1 (log real 21-oct: HTML real recibido,
 * 0 obras): el HTML qwik real escribe href ANTES que class y
 * usa tres formas de tarjeta distintas por seccion. Este mini
 * nucleo reconoce atributos en CUALQUIER orden y clases por
 * TOKEN exacto ("card-body" no contiene el token "card").
 * Regex pura: la api congelada no expone parser DOM.
 */
internal object HtmlIkigai {

    /** Valor de un atributo de una etiqueta de apertura,
     *  independiente del orden en que el sitio lo escriba. */
    fun atributo(etiqueta: String, nombre: String): String? =
        Regex("\\b" + nombre + "\\s*=\\s*\"([^\"]*)\"")
            .find(etiqueta)?.groupValues?.get(1)

    /** ¿La etiqueta tiene la clase pedida como TOKEN exacto? */
    fun tieneClase(etiqueta: String, clase: String): Boolean =
        atributo(etiqueta, "class")
            ?.split(Regex("\\s+"))?.any { it == clase } == true

    /** Tarjetas <a ... class="...clase...">: (tag, cuerpo hasta
     *  </a>) en orden del DOM. */
    fun tarjetasA(html: String, clase: String): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        var idx = 0
        val apertura = Regex("<a\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: break
            if (tieneClase(m.value, clase)) {
                val fin = html.indexOf("</a>", m.range.last + 1)
                val cuerpo =
                    if (fin > 0) html.substring(m.range.last + 1, fin)
                    else html.substring(m.range.last + 1)
                res.add(m.value to cuerpo)
                idx = if (fin > 0) fin + 4 else m.range.last + 1
            } else {
                idx = m.range.last + 1
            }
        }
        return res
    }

    /** Bloques <div ... class="...clase...">: cuerpo desde la
     *  apertura hasta el inicio del siguiente bloque con la
     *  misma clase (o +8000 caracteres en el ultimo). */
    fun bloquesDiv(html: String, clase: String): List<String> {
        val inicios = mutableListOf<Int>()
        var idx = 0
        val apertura = Regex("<div\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: break
            if (tieneClase(m.value, clase)) inicios.add(m.range.last + 1)
            idx = m.range.last + 1
        }
        return inicios.mapIndexed { i, desde ->
            val hasta =
                if (i + 1 < inicios.size) inicios[i + 1]
                else minOf(desde + 8000, html.length)
            html.substring(desde, hasta)
        }
    }

    /** Texto del primer elemento con la clase dada, limpio de
     *  etiquetas internas (los titulos qwik a veces envuelven
     *  el texto en spans). */
    fun textoDeClase(cuerpo: String, clase: String): String? =
        Regex(
            "<[a-z0-9]+\\b[^>]*class=\"[^\"]*\\b" + clase +
                "\\b[^\"]*\"[^>]*>(.*?)</[a-z0-9]+>",
            DOT_MATCHES_ALL
        ).find(cuerpo)?.groupValues?.get(1)
            ?.let { limpiar(it) }?.takeIf { it.length >= 2 }

    /** Primera <img> del fragmento: src tal cual lo emite el
     *  sitio (Jsoup abs:src equivalente cuando es absoluto). */
    fun primeraImagen(cuerpo: String): String =
        Regex("<img\\b[^>]*>").findAll(cuerpo)
            .mapNotNull { atributo(it.value, "src")?.trim() }
            .firstOrNull() ?: ""

    /** hrefs ABSOLUTOS con el dominio camuflado vigente:
     *  quedan normalizados a ruta relativa (la api construye
     *  sobre BASE y el redirect hace el resto). */
    fun rutaDe(href: String): String {
        if (!href.startsWith("http")) return href
        return Regex("https?://[^/]+(/.*)").find(href)
            ?.groupValues?.get(1) ?: href
    }

    /** Texto de los <a> cuyo href contiene la marca dada
     *  (generos[]= / estados[]=). */
    fun enlacesConTexto(html: String, marca: String): List<String> {
        val res = mutableListOf<String>()
        var idx = 0
        val apertura = Regex("<a\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: break
            val href = atributo(m.value, "href").orEmpty()
            val fin = html.indexOf("</a>", m.range.last + 1)
            if (href.contains(marca) && fin > 0) {
                val texto = limpiar(html.substring(m.range.last + 1, fin))
                if (texto.length in 2..40 && !res.contains(texto)) {
                    res.add(texto)
                }
                idx = fin + 4
            } else {
                idx = m.range.last + 1
            }
        }
        return res
    }

    /** Quita etiquetas y entidades, colapsa espacios. */
    fun limpiar(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()
}
