package com.harley.mangafox.mfx.mhscans

import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.home.ObraHome

/**
 * Parsers del TEMA NUEVO de mhscans (autopsia en vivo 05-nov):
 * el sitio cambio de wp-manga a un tema propio.
 *  - catalogo: /library?page=N -> div.lib-grid > a[href^=/comics/]
 *  - capitulos: /comics/<slug>/chapters?page=N -> a[href^=/leer/][data-chapter-num]
 *  - lector:   /leer/<code> -> img.page-img
 * Referencia: keiyoushi MHScans.kt (versionCode 71).
 */
object HtmlMhscans {

    fun lista(html: String): List<ObraHome> {
        val res = mutableListOf<ObraHome>()
        val bloques = Regex(
            "<a\\s[^>]*href=\"(/comics/[^\"]+)\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(html)
        for (b in bloques) {
            val ruta = b.groupValues[1]
            val cuerpo = b.groupValues[2]
            val titulo = limpiar(
                Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                    .find(cuerpo)?.groupValues?.get(1)
            ) ?: continue
            if (titulo.isBlank()) continue
            val portada = Regex("<img[^>]*src=\"([^\"]+)\"").find(cuerpo)
                ?.groupValues?.get(1)?.trim().orEmpty()
            if (res.any { it.enlace == ruta }) continue
            res.add(
                ObraHome(
                    titulo = titulo,
                    portadaUrl = portada,
                    enlace = ruta,
                    id = ruta.trim('/').substringAfterLast('/')
                )
            )
        }
        return res
    }

    fun hayMas(html: String): Boolean =
        Regex("lib-pagination").containsMatchIn(html) &&
            Regex("Siguiente").containsMatchIn(html)

    fun capitulos(html: String): List<Capitulo> {
        val res = mutableListOf<Capitulo>()
        val anclas = Regex(
            "<a\\s[^>]*href=\"(/leer/[^\"]+)\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(html)
        for (a in anclas) {
            val ruta = a.groupValues[1]
            val cuerpo = a.groupValues[2]
            val etiqueta = limpiar(cuerpo) ?: continue
            val numero = Regex("data-chapter-num=\"([^\"]+)\"").find(a.value)
                ?.groupValues?.get(1)?.trim().orEmpty().ifBlank { etiqueta }
            val fecha = limpiar(
                Regex("<span[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    .find(cuerpo)?.groupValues?.get(1)
            )
            res.add(Capitulo(numero, etiqueta, fecha, ruta))
        }
        return res
    }

    fun paginas(html: String): List<String> {
        val res = mutableListOf<String>()
        val imgs = Regex("<img[^>]*class=\"[^\"]*page-img[^\"]*\"[^>]*>").findAll(html)
        for (m in imgs) {
            val tag = m.value
            val url = Regex("data-src=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
                ?: Regex("src=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
            val u = url?.trim().orEmpty()
            if (u.isNotEmpty()) res.add(u)
        }
        return res
    }

    private fun limpiar(crudo: String?): String? {
        if (crudo == null) return null
        val sinTags = crudo.replace(Regex("<[^>]+>"), " ")
        return sinTags.replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&#39;", "'").replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ").trim().ifBlank { null }
    }
}
