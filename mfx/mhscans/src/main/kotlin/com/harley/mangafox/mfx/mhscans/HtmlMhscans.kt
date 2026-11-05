package com.harley.mangafox.mfx.mhscans


/**
 * PARSERS del TEMA NUEVO de mhscans (05-nov-2026).
 *
 * Autopsia en vivo (navegador + curl, evidencia exacta):
 *  - Catalogo: /library?page=N -> div.lib-grid con 24 <a href="/comics/...">
 *    cuyo interior es MULTILINEA (titulo en <p>, portada en <img src>).
 *  - Capitulos: /comics/<slug>/chapters?page=N -> <a href="/leer/<code>"
 *    data-chapter-id data-chapter-num data-chapter-label ...> (multilinea).
 *  - Lector: /leer/<code> -> la clase page-img vive en el DIV wrapper
 *    (page-img-wrap) y la URL real esta en el src del <img> interior.
 *
 * Puro: sin red, sin Android. Solo texto -> modelos.
 */
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.home.ObraHome

object HtmlMhscans {

    private const val TODO = "[\\s\\S]{0,900}?"

    /** Obras del directorio /library. */
    fun lista(html: String): List<ObraHome> {
        val res = mutableListOf<ObraHome>()
        val patron = Regex("<a\\s+href=\"(/comics/[^\"]+)\"([\\s\\S]{0,900}?)</a>")
        for (m in patron.findAll(html)) {
            val href = m.groupValues[1].trim()
            val resto = m.groupValues[2]
            val titulo = limpiar(
                Regex("<p[^>]*>([\\s\\S]{0,200}?)</p>").find(resto)?.groupValues?.get(1)
            ) ?: limpiar(
                Regex("alt=\"([^\"]+)\"").find(resto)?.groupValues?.get(1)
            ) ?: continue
            if (titulo.isBlank()) continue
            val portada = Regex("<img[^>]*(?:data-src|src)=\"([^\"]+)\"")
                .find(resto)?.groupValues?.get(1)
            val id = href.removePrefix("/comics/").trim('/')
            res.add(
                ObraHome(
                    id = id,
                    titulo = titulo,
                    portadaUrl = portada ?: "",
                    etiqueta = null,
                    enlace = href
                )
            )
        }
        return res.distinctBy { it.id }
    }

    /** True si el HTML anuncia otra pagina de catalogo. */
    fun hayMas(html: String): Boolean =
        html.contains("lib-pagination") && html.contains("Siguiente")

    /** Capitulos de la ficha (pagina HTML paginada). */
    fun capitulos(html: String): List<Capitulo> {
        val res = mutableListOf<Capitulo>()
        val patron = Regex("<a\\s+href=\"(/leer/[^\"]+)\"([^>]{0,400}?)>")
        for (m in patron.findAll(html)) {
            val href = m.groupValues[1].trim()
            val attrs = m.groupValues[2]
            val num = Regex("data-chapter-num=\"([^\"]+)\"")
                .find(attrs)?.groupValues?.get(1)
                ?: Regex("-ch([0-9]+(?:\\.[0-9]+)?)").find(href)?.groupValues?.get(1)
                ?: continue
            val etiqueta = limpiar(
                Regex("data-chapter-label=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
            )
            val titulo = if (etiqueta.isNullOrBlank()) "Capitulo " + num else etiqueta
            res.add(
                Capitulo(
                    numero = num.trim(),
                    titulo = titulo,
                    fecha = fechaCercana(html, m.range.last),
                    enlace = href
                )
            )
        }
        return res.distinctBy { it.enlace }
    }

    /** dd/MM/yyyy si el HTML trae una fecha cerca de la fila. */
    private fun fechaCercana(html: String, desde: Int): String? {
        val fin = Math.min(html.length, desde + 700)
        if (desde < 0 || fin <= desde) return null
        val trozo = html.substring(Math.max(0, desde), fin)
        Regex("(\\d{1,2}/\\d{1,2}/\\d{2,4})").find(trozo)
            ?.let { return it.groupValues[1] }
        Regex("(Hace\\s+[^<]{0,20})", RegexOption.IGNORE_CASE).find(trozo)
            ?.let { return limpiar(it.groupValues[1]) }
        return null
    }

    /** URLs de pagina del lector. */
    fun paginas(html: String): List<String> {
        val res = mutableListOf<String>()
        // Autopsia 05-nov: las imagenes del lector viven en
        // /uploads/pages/ y la clase page-img esta en el wrapper,
        // no siempre en el propio <img>. Se piden por URL real.
        Regex("<img[^>]*(?:data-src|src)=\"([^\"]*/uploads/pages/[^\"]+)\"")
            .findAll(html).forEach { res.add(it.groupValues[1]) }
        if (res.isNotEmpty()) return res.distinct()
        Regex("<img[^>]*(?:data-src|src)=\"([^\"]+)\"")
            .findAll(html).forEach { res.add(it.groupValues[1]) }
        return res.distinct()
    }

    private fun limpiar(crudo: String?): String? {
        if (crudo == null) return null
        val sinTags = crudo.replace(Regex("<[^>]+>"), " ")
        return sinTags.replace("&nbsp;", " ").replace("&amp;", " & ")
            .replace("&quot;", "\"").replace("\\s+".toRegex(), " ").trim()
    }
}
