package com.harley.mangafox.mfx.ikigaimangas

import com.harley.mangafox.data.parser.Seccion
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.lector.PaginasCapitulo
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * EXTENSION MFX ikigaimangas — Ikigai Mangas (visualikigai).
 *
 * HTML Qwik autopsiado en vivo 21-oct-2026 (evidencia keiyoushi
 * + navegacion real del dueno: pide verificar dominio). JUEGO
 * DE DOMINIOS documentado: el landing ikigaimangas.com (tras
 * Cloudflare) anuncia en un script qwik (/build/q-CA3XLgLw.js)
 * el dominio del boton "Ir al sitio": visualikigai.com, que a
 * su vez REDIRIGE al dominio camuflado vigente (hoy
 * visualikigai.cmpunjabrashancard.pk). El HttpURLConnection de
 * la apk SIGUE redirects: baseUrl visualikigai.com aterriza
 * siempre en el camuflado vigente sin codificarlo.
 *
 *   RECIENTES : /?pagina=N -> section[aria-labelledby=
 *               new-chapters-heading] (tarjetas a.card,
 *               paginacion nav[aria-label=pagination])
 *   POPULARES : /series/?tipos[]=comic&ordenar=view_count&
 *               direccion=desc&pagina=N (archivo ordenado por
 *               vistas: lista real DISTINTA)
 *   RANKING   : /clasificacion/ (la clasificacion oficial del
 *               sitio, pagina UNICA: pag 2+ = vacio honesto)
 *   DIRECTORIO: /series/?tipos[]=comic[&generos[]=id]&pagina=N
 *   FICHA     : /series/<slug>/ (main .card-body, capitulos en
 *               section.card > ul.grid a.card con paginacion
 *               ?pagina=N: v1 primera pagina + incompletos)
 *   LECTOR     : la URL del capitulo (abs:href) con los <img>
 *               del interior de <section> (orden del DOM)
 *   BUSQUEDA  : NO SOPORTADA honesto: el buscador del sitio es
 *               un endpoint Qwik que exige qfunc generado por
 *               WebView (keiyoushi lo resuelve asi); la api
 *               congelada no tiene WebView. buscar() devuelve
 *               vacio y el directorio queda como explorador.
 *
 * Rate limit 1 peticion / 2 s (evidencia keiyoushi): el mismo
 * ritmo que pide el sitio a los lectores rapidos.
 */
class FuenteIkigaiMangas(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "ikigaimangas"

    private val web: ClienteWeb = contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    // ------------------------------------------------------------ secciones

    override suspend fun cargarRecientes(): List<ObraHome> =
        cargarSeccion(Seccion.RECIENTES, 1)

    override suspend fun cargarPopulares(): List<ObraHome> =
        cargarSeccion(Seccion.POPULARES, 1)

    override suspend fun cargarRanking(): List<ObraHome> =
        cargarSeccion(Seccion.RANKING, 1)

    override suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        return when (seccion) {
            Seccion.RECIENTES -> {
                val html = web.getHtml("/?pagina=" + pagina)
                    ?: return emptyList()
                withContext(Dispatchers.IO) {
                    parsearTarjetas(
                        seccionRecentes(html),
                        "section[aria-labelledby=new-chapters-heading] " +
                            "ul.grid:last-of-type"
                    )
                }
            }
            Seccion.POPULARES -> {
                val html = web.getHtml(
                    "/series/?tipos%5B%5D=comic&ordenar=view_count" +
                        "&direccion=desc&pagina=" + pagina
                ) ?: return emptyList()
                withContext(Dispatchers.IO) {
                    parsearTarjetas(
                        seccionArchivo(html), "section[archive]"
                    )
                }
            }
            Seccion.RANKING -> {
                if (pagina > 1) {
                    MangaFoxLog.d(
                        ETIQUETA, "ranking pag=" + pagina +
                            ": 0 (FIN: clasificacion unica)"
                    )
                    return emptyList()
                }
                val html = web.getHtml("/clasificacion/") ?: return emptyList()
                withContext(Dispatchers.IO) {
                    parsearTarjetas(seccionClasificacion(html), "clasificacion")
                }
            }
        }.also {
            MangaFoxLog.d(
                ETIQUETA, "seccion " + seccion.name + " pag=" + pagina +
                    ": " + it.size + " obras" +
                    (if (it.isEmpty()) " (FIN)" else "")
            )
        }
    }

    /** Recorta el HTML a la seccion que importa antes de
     *  parsear tarjetas: cero contaminacion cruzada entre
     *  secciones de la misma pagina. */
    private fun seccionRecentes(html: String): String {
        val i = html.indexOf("new-chapters-heading")
        if (i < 0) return ""
        val fin = html.indexOf("pagination", i)
        return if (fin > i) html.substring(i, fin + 2000) else html.substring(i)
    }

    private fun seccionArchivo(html: String): String {
        val i = html.indexOf("archive-heading")
        if (i < 0) return ""
        val fin = html.indexOf("pagination", i)
        return if (fin > i) html.substring(i, fin + 2000) else html.substring(i)
    }

    private fun seccionClasificacion(html: String): String = html

    /** Tarjetas a.card: <a class="card" href="...series/<slug>/">
     *  con img y .card-body .card-title (evidencia keiyoushi
     *  sobre el mismo DOM vigente). */
    private fun parsearTarjetas(
        fragmento: String,
        deDonde: String
    ): List<ObraHome> {
        if (fragmento.isEmpty()) {
            MangaFoxLog.w(ETIQUETA, "seccion ausente: " + deDonde)
            return emptyList()
        }
        val res = mutableListOf<ObraHome>()
        Regex("<a[^>]*class=\"[^\"]*card[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>", DOT_MATCHES_ALL)
            .findAll(fragmento).forEach { m ->
                val href = m.groupValues[1].trim()
                val slug = slugDe(href) ?: return@forEach
                val enlace = "/series/" + slug + "/"
                val titulo = Regex("card-title[^>]*>\\s*([^<]{2,120})<")
                    .find(m.value)?.groupValues?.get(1)?.trim() ?: return@forEach
                val portada = Regex("<img[^>]*src=\"([^\"]+)\"")
                    .find(m.value)?.groupValues?.get(1)?.trim() ?: ""
                if (res.none { it.id == enlace }) {
                    res.add(ObraHome(enlace, titulo, portada, enlace = enlace))
                }
            }
        return res
    }

    private fun slugDe(href: String): String? {
        val m = Regex("/series/([a-z0-9-]+)").find(href) ?: return null
        return m.groupValues[1].takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------ generos

    /** Lista congelada de evidencia keiyoushi: el form Qwik de
     *  /series/ pinta los generos por JS y no son scrapeables
     *  confiables en HTML estatico. */
    override suspend fun generos(): List<Genero> = GENEROS_IKIGAI

    // ------------------------------------------------------------ directorio

    /** El tamano por pagina del archivo no es estable en el
     *  HTML Qwik: 0 = el motor no aplica fin temprano y
     *  confirma con la pagina vacia real. */
    override fun tamanoPaginaPedido(generoSlug: String?, busqueda: Boolean) = 0

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        var ruta = "/series/?tipos%5B%5D=comic&pagina=" + pagina
        if (!generoSlug.isNullOrEmpty()) {
            ruta += "&generos%5B%5D=" + URLEncoder.encode(generoSlug, "UTF-8")
        }
        val html = web.getHtml(ruta) ?: return emptyList()
        val res = withContext(Dispatchers.IO) {
            parsearTarjetas(seccionArchivo(html), "archivo")
        }
        MangaFoxLog.d(
            ETIQUETA, "directorio pag=" + pagina +
                (if (!generoSlug.isNullOrEmpty()) " genero=" + generoSlug else "") +
                ": " + res.size + " obras"
        )
        return res
    }

    // ------------------------------------------------------------ busqueda

    /** Buscador qwik (qfunc por WebView): NO accesible desde
     *  la api congelada. Estado honesto propio: vacio. */
    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        MangaFoxLog.w(
            ETIQUETA, "busqueda no soportada por la fuente " +
                "(buscador qwik requiere JS); usa el directorio"
        )
        return emptyList()
    }

    // -------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        if (obra.enlace.isEmpty()) return null
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(obra.enlace)
        if (html == null) {
            MangaFoxLog.w(ETIQUETA, "ficha sin HTML: " + obra.enlace)
            return null
        }
        val main = withContext(Dispatchers.IO) {
            val i = html.indexOf("<main")
            if (i < 0) "" else html.substring(i, (i + 20000).coerceAtMost(html.length))
        }
        if (main.isEmpty()) {
            MangaFoxLog.w(ETIQUETA, "ficha sin <main>: " + obra.enlace)
            return null
        }
        val titulo = Regex("card-title[^>]*>\\s*([^<]{2,150})<")
            .find(main)?.groupValues?.get(1)?.trim() ?: obra.titulo
        val portada = Regex("<article[^>]*class=\"[^\"]*card[^\"]*\"[^>]*>.*?<img[^>]*src=\"([^\"]+)\"", DOT_MATCHES_ALL)
            .find(main)?.groupValues?.get(1)?.trim() ?: obra.portadaUrl
        val sinopsis = Regex("card-body[^>]*>\\s*<p[^>]*>(.*?)</p>", DOT_MATCHES_ALL)
            .find(main)?.groupValues?.get(1)?.let { limpiar(it) }.orEmpty()
        val generos = mutableListOf<String>()
        Regex("[?&]generos\\[\\]=\\d+[^>]*>\\s*([^<]{2,40})<")
            .findAll(main).forEach {
                val g = it.groupValues[1].trim()
                if (g.isNotEmpty() && !generos.contains(g)) generos.add(g)
            }
        val estado = Regex("[?&]estados\\[\\]=\\d+[^>]*>\\s*([^<]{2,40})<")
            .find(main)?.groupValues?.get(1)?.trim()?.ifBlank { null }
        val (capitulos, incompletos) = extraerCapitulos(html)
        val detalle = ObraDetalle(
            id = obra.enlace,
            titulo = titulo,
            portadaUrl = portada,
            sinopsis = sinopsis,
            estado = estado,
            generos = generos,
            capitulos = capitulos,
            capitulosIncompletos = incompletos
        )
        MangaFoxLog.d(
            ETIQUETA, "ficha '" + detalle.titulo + "': " + capitulos.size +
                " caps (" + (System.currentTimeMillis() - t0) + " ms)" +
                (if (incompletos) " [incompletos]" else "")
        )
        return detalle
    }

    /** Capitulos: section.card > ul.grid a.card en la propia
     *  ficha (pagina 1). Si hay nav de paginacion hay mas
     *  paginas: v1 entrega la primera y declara incompletos. */
    private fun extraerCapitulos(html: String): Pair<List<Capitulo>, Boolean> {
        val res = mutableListOf<Capitulo>()
        Regex("section[^>]*class=\"[^\"]*card[^\"]*\"[^>]*>(.*?)</section>", DOT_MATCHES_ALL)
            .findAll(html).forEach { s ->
                val cuerpo = s.groupValues[1]
                if (!cuerpo.contains("capitulo") && !cuerpo.contains("series/")) {
                    return@forEach
                }
                Regex("<a[^>]*class=\"[^\"]*card[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", DOT_MATCHES_ALL)
                    .findAll(cuerpo).forEach { m ->
                        val href = m.groupValues[1].trim()
                        val titulo = Regex("card-title[^>]*>\\s*([^<]{2,120})<")
                            .find(m.groupValues[2])?.groupValues?.get(1)?.trim()
                            ?: return@forEach
                        val fecha = Regex("datetime=\"([^\"]+)\"")
                            .find(m.groupValues[2])?.groupValues?.get(1)
                            ?.let { fechaIkigai(it) }
                        val numero = titulo.removePrefix("Capítulo").removePrefix("Capitulo")
                            .trim().ifBlank { titulo }
                        val enlace = if (href.startsWith("http")) {
                            href.substringAfter(BASE.trimEnd('/'))
                        } else href
                        res.add(Capitulo(numero, titulo, fecha, enlace))
                    }
            }
        val incompletos = Regex("nav[^>]*aria-label=\"pagination\"").containsMatchIn(html)
        return res to incompletos
    }

    /** datetime="Wed Oct 21 2026 12:00:00 GMT+0000 (...)" (el
     *  formato qwik del sitio, evidencia keiyoushi). */
    private fun fechaIkigai(crudo: String): String? {
        val m = Regex("([A-Z][a-z]{2}) ([A-Z][a-z]{2}) (\\d{2}) (\\d{4})")
            .find(crudo) ?: return null
        return runCatching {
            val f = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
            SimpleDateFormat("dd/MM/yyyy", Locale.US)
                .format(f.parse(m.value))
        }.getOrNull()
    }

    private fun limpiar(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()

    // -------------------------------------------------------------- lector

    /** La pagina del capitulo: <section> con <img> dentro de
     *  <div> (orden del DOM = orden de lectura). */
    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        if (capitulo.enlace.isEmpty()) return PaginasCapitulo(emptyList())
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(capitulo.enlace)
        if (html == null) {
            MangaFoxLog.w(ETIQUETA, "capitulo sin HTML: " + capitulo.enlace)
            return PaginasCapitulo(emptyList())
        }
        val urls = withContext(Dispatchers.IO) { extraerPaginas(html) }
        MangaFoxLog.d(
            ETIQUETA, "capitulo '" + capitulo.titulo + "': " + urls.size +
                " paginas (" + (System.currentTimeMillis() - t0) + " ms)"
        )
        return PaginasCapitulo(
            urls,
            referer = BASE.trimEnd('/') + capitulo.enlace
        )
    }

    private fun extraerPaginas(html: String): List<String> {
        val urls = mutableListOf<String>()
        val cuerpo = Regex("<section[^>]*>(.*?)</section>", DOT_MATCHES_ALL)
            .findAll(html).flatMap { it.groupValues }.joinToString(" ")
        Regex("<div[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"")
            .findAll(cuerpo).forEach { m ->
                val src = m.groupValues[1].trim()
                if (src.startsWith("http") && !src.contains("placeholder") &&
                    !urls.contains(src)
                ) {
                    urls.add(src)
                }
            }
        return urls
    }

    companion object {
        const val ETIQUETA = "Mfx.Ikigai"
        /** Puerta estable: redirige al dominio camuflado vigente
         *  (el landing ikigaimangas.com anuncia esta URL via su
         *  script qwik; verificado 21-oct-2026). */
        const val BASE = "https://visualikigai.com"
        const val RECOVERY = "https://ikigaimangas.com"
        const val RITMO_MS = 2000L
    }
}
