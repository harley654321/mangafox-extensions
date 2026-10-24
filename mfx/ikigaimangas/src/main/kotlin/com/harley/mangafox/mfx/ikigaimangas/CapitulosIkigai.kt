package com.harley.mangafox.mfx.ikigaimangas

import com.harley.mangafox.logic.detalle.Capitulo
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * LOG DUENO 24-oct (0d86eac01): 'La vida difícil...' mostro 24
 * caps con bandera [incompletos] y el lector ofrecio reintentar:
 * el sitio PAGINA los capitulos de la ficha (?pagina=N) y la v3
 * solo leia la pagina 1. EVIDENCIA KEIYOUSHI (fetchMangaUpdate):
 * parsea la ultima pagina del nav[aria-label=pagination] con
 * q:key="page-N" y concatena las paginas 2..N de
 * /series/<slug>/?pagina=N. Aqui vive TODO el parseo de la lista
 * de capitulos (tambien para que la fuente respire la ley de
 * 500 lineas).
 */
internal object CapitulosIkigai {

    /** Caps visibles en UNA pagina de la ficha + bandera de
     *  si existe paginacion (nav aria-label=pagination). */
    fun caps(html: String): Pair<List<Capitulo>, Boolean> {
        val res = mutableListOf<Capitulo>()
        var idx = 0
        val apertura = Regex("<section\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: break
            val fin = html.indexOf("</section>", m.range.last + 1)
            val cuerpo =
                if (fin > 0) html.substring(m.range.last + 1, fin)
                else html.substring(m.range.last + 1)
            idx = if (fin > 0) fin + 10 else m.range.last + 1
            if (!HtmlIkigai.tieneClase(m.value, "card")) continue
            if (!cuerpo.contains("capitulo") && !cuerpo.contains("series/")) {
                continue
            }
            HtmlIkigai.tarjetasA(cuerpo, "card").forEach { (tag, ccuerpo) ->
                val href = HtmlIkigai.atributo(tag, "href")?.trim()
                    ?: return@forEach
                val titulo = HtmlIkigai.textoDeClase(ccuerpo, "card-title")
                    ?: return@forEach
                val fecha = Regex("datetime=\"([^\"]+)\"")
                    .find(ccuerpo)?.groupValues?.get(1)
                    ?.let { fechaIkigai(it) }
                val numero = titulo.removePrefix("Capítulo")
                    .removePrefix("Capitulo").trim().ifBlank { titulo }
                res.add(
                    Capitulo(numero, titulo, fecha, HtmlIkigai.rutaDe(href))
                )
            }
        }
        val incompletos =
            Regex("nav[^>]*aria-label=\"pagination\"").containsMatchIn(html)
        return res to incompletos
    }

    /** Ultima pagina del paginador de la ficha: el qwik escribe
     *  q:key="page-N" en los enlaces del nav (evidencia
     *  keiyoushi). Sin paginador -> 1. */
    fun ultimaPagina(html: String): Int {
        return Regex("q:key=\"page-(\\d+)\"").findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull() ?: 1
    }

    /** datetime="Wed Oct 21 2026 12:00:00 GMT+0000 (...)" (el
     *  formato qwik del sitio, evidencia keiyoushi). */
    fun fechaIkigai(crudo: String): String? {
        val m = Regex("([A-Z][a-z]{2} ([A-Z][a-z]{2}) (\\d{2}) (\\d{4}))")
            .find(crudo) ?: return null
        return runCatching {
            val f = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
            SimpleDateFormat("dd/MM/yyyy", Locale.US)
                .format(f.parse(m.groupValues[1]))
        }.getOrNull()
    }
}
