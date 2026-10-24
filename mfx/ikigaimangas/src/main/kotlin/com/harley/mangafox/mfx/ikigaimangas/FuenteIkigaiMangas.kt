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
import com.harley.mangafox.mfx.api.v2.Filtro
import com.harley.mangafox.mfx.api.v2.FuenteMfx2
import com.harley.mangafox.mfx.api.FuenteMfx
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * EXTENSION MFX ikigaimangas: Ikigai Mangas (visualikigai).
 *
 * HTML Qwik autopsiado en vivo 21-oct-2026 (evidencia keiyoushi
 * + navegacion real del dueno + LOG REAL del celular). JUEGO
 * DE DOMINIOS documentado: el landing ikigaimangas.com (tras
 * Cloudflare) anuncia en un script qwik (/build/q-CA3XLgLw.js)
 * el dominio del boton "Ir al sitio": visualikigai.com, que
 * REDIRIGE al dominio camuflado vigente (hoy
 * visualikigai.cmpunjabrashancard.pk). El HttpURLConnection
 * de la apk SIGUE redirects: baseUrl visualikigai.com
 * aterriza siempre en el camuflado vigente. CONFIRMADO EN LOG
 * REAL (21-oct 22:29): el celular recibio el sitio real
 * (clasificacion 457 KB, archivo 218 KB, home 400 KB).
 *
 * AUTOOPSIA DEL BUG v1 (log real: "0 obras (FIN)" con HTML
 * real recibido). TRES formas de tarjeta DISTINTAS por
 * seccion (evidencia keiyoushi DOM) y el HTML qwik real
 * escribe href ANTES que class: el parser v1 exigia class
 * antes de href y una sola forma para todo = 0 en todas.
 * El nucleo de parsing vive en HtmlIkigai.kt (atributos en
 * cualquier orden, clases por token exacto).
 *   RECIENTES : /?pagina=N -> section[aria-labelledby=
 *               new-chapters-heading] tarjetas a.card con
 *               titulo en .card-body .card-title
 *   POPULARES : /series/?tipos[]=comic&ordenar=bookmark_count&
 *               direccion=desc&pagina=N -> section[aria-
 *               labelledby=archive-heading] tarjetas a.card
 *               con titulo en h3 (NO card-title). FAVORITOS, no
 *               vistas: /clasificacion/ YA es el top por vistas
 *               (log dueno 23-oct 03:01: ranking y populares
 *               salian CLAVADOS).
 *   RANKING   : /clasificacion/ (pagina UNICA) -> tarjetas
 *               div.card con enlace en a.btn y titulo en
 *               .card-body .card-title
 *   DIRECTORIO: /series/?tipos[]=comic[&generos[]=id]&pagina=N
 *               (misma forma que POPULARES: titulo h3)
 *   FICHA     : /series/<slug>/ (main .card-body, capitulos
 *               en section.card > ul.grid a.card con
 *               paginacion ?pagina=N: v1 primera pagina +
 *               incompletos)
 *   LECTOR     : URL del capitulo, <section div > img> en
 *               orden del DOM (evidencia keiyoushi 291)
 *   BUSQUEDA  : NO SOPORTADA honesto: el buscador exige qfunc
 *               generado por WebView + POST qwik-json; la api
 *               congelada no tiene WebView. buscar() devuelve
 *               vacio y el directorio queda como explorador.
 *
 * Rate limit 1 peticion / 2 s (evidencia keiyoushi).
 */
class FuenteIkigaiMangas(contexto: ContextoMfx) : FuenteMfx2 {

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
                    parsearRecientes(rebanar(html, "new-chapters-heading"))
                }
            }
            Seccion.POPULARES -> {
                // bookmark_count (favoritos): view_count dejaba
                // POPULARES identico a RANKING (/clasificacion =
                // top por vistas). Verificado vivo 23-oct.
                val html = web.getHtml(
                    "/series/?tipos%5B%5D=comic&ordenar=bookmark_count" +
                        "&direccion=desc&pagina=" + pagina
                ) ?: return emptyList()
                withContext(Dispatchers.IO) {
                    parsearArchivo(rebanar(html, "archive-heading"))
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
                    parsearClasificacion(html)
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

    /** Recorta el HTML a la seccion exacta: desde el ancla
     *  (id/aria de la <section>) hasta el </section> que le
     *  sigue. Cero contaminacion cruzada entre secciones. */
    private fun rebanar(html: String, ancla: String): String {
        val i = html.indexOf(ancla)
        if (i < 0) {
            MangaFoxLog.w(ETIQUETA, "seccion ausente: " + ancla)
            return ""
        }
        val fin = html.indexOf("</section>", i)
        return if (fin > i) {
            html.substring(i, minOf(fin, i + 60000))
        } else {
            html.substring(i, minOf(i + 12000, html.length))
        }
    }

    // ----------------------------------------------- formas por seccion

    /** RECIENTES: a.card, titulo .card-body .card-title. */
    private fun parsearRecientes(fragmento: String): List<ObraHome> {
        if (fragmento.isEmpty()) return emptyList()
        return HtmlIkigai.tarjetasA(fragmento, "card")
            .mapNotNull { (tag, cuerpo) ->
                val href = HtmlIkigai.atributo(tag, "href")?.trim()
                    ?: return@mapNotNull null
                val slug = slugDe(href) ?: return@mapNotNull null
                val titulo = HtmlIkigai.textoDeClase(cuerpo, "card-title")
                    ?: return@mapNotNull null
                ObraHome(
                    id = "/series/" + slug + "/",
                    titulo = titulo,
                    portadaUrl = HtmlIkigai.primeraImagen(cuerpo),
                    enlace = "/series/" + slug + "/"
                )
            }.distinctBy { it.id }
    }

    /** POPULARES/DIRECTORIO: a.card, titulo en h3 (NO
     *  card-title: evidencia keiyoushi linea 163). */
    private fun parsearArchivo(fragmento: String): List<ObraHome> {
        if (fragmento.isEmpty()) return emptyList()
        return HtmlIkigai.tarjetasA(fragmento, "card")
            .mapNotNull { (tag, cuerpo) ->
                val href = HtmlIkigai.atributo(tag, "href")?.trim()
                    ?: return@mapNotNull null
                val slug = slugDe(href) ?: return@mapNotNull null
                val titulo =
                    Regex("<h3\\b[^>]*>(.*?)</h3>", DOT_MATCHES_ALL)
                        .find(cuerpo)?.groupValues?.get(1)
                        ?.let { HtmlIkigai.limpiar(it) }
                        ?.takeIf { it.length >= 2 } ?: return@mapNotNull null
                ObraHome(
                    id = "/series/" + slug + "/",
                    titulo = titulo,
                    portadaUrl = HtmlIkigai.primeraImagen(cuerpo),
                    enlace = "/series/" + slug + "/"
                )
            }.distinctBy { it.id }
    }

    /** RANKING: div.card con enlace en a.btn y titulo en
     *  .card-body .card-title (evidencia keiyoushi 95-99). */
    private fun parsearClasificacion(html: String): List<ObraHome> {
        val res = mutableListOf<ObraHome>()
        HtmlIkigai.bloquesDiv(html, "card").forEach { bloque ->
            val titulo = HtmlIkigai.textoDeClase(bloque, "card-title")
                ?: return@forEach
            val enlace = HtmlIkigai.tarjetasA(bloque, "btn").firstOrNull()
                ?.let { (tag, _) -> HtmlIkigai.atributo(tag, "href")?.trim() }
                ?: return@forEach
            val slug = slugDe(enlace) ?: return@forEach
            val obra = ObraHome(
                id = "/series/" + slug + "/",
                titulo = titulo,
                portadaUrl = HtmlIkigai.primeraImagen(bloque),
                enlace = "/series/" + slug + "/"
            )
            if (res.none { it.id == obra.id }) res.add(obra)
        }
        return res
    }

    private fun slugDe(href: String): String? =
        Regex("/series/([a-z0-9-]+)").find(href)
            ?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------------------ generos

    /** Lista congelada de evidencia keiyoushi: el form Qwik de
     *  /series/ pinta los generos por JS y no son scrapeables
     *  confiablemente en HTML estatico. */
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
            parsearArchivo(rebanar(html, "archive-heading"))
        }
        MangaFoxLog.d(
            ETIQUETA, "directorio pag=" + pagina +
                (if (!generoSlug.isNullOrEmpty()) " genero=" + generoSlug else "") +
                ": " + res.size + " obras"
        )
        return res
    }

    // -------------------------------------------- v2: filtros tipados

    /** Filtros v2: la consulta y el ORDEN del listado (valores
     *  reales del sitio; evidencia Filters.kt Keiyoushi). */
    override suspend fun filtros(): List<Filtro> = listOf(
        Filtro.Texto("Titulo"),
        Filtro.Orden(
            "Ordenar por",
            listOf(
                "Vistas", "Favoritos", "Valoracion",
                "Actualizacion", "Nombre", "Creado en"
            )
        )
    )

    /** Valor 'ordenar' del sitio por cada opcion del filtro. */
    private val ordenarPorIndice = listOf(
        "view_count", "bookmark_count", "rating_count",
        "last_chapter_date", "name", "created_at"
    )

    /** Busqueda v2: por ORDEN navega el archivo real del sitio
     *  (misma forma que POPULARES); por TITULO es honesto: el
     *  buscador qwik exige el token qfunc que solo existe en JS
     *  runtime (verificado 23-oct: NO vive en el HTML crudo) —
     *  queda para la exposicion de la escotilla (v2.1). */
    override suspend fun buscar(
        filtros: List<Filtro>,
        pagina: Int
    ): List<ObraHome> {
        val texto = filtros.filterIsInstance<Filtro.Texto>()
            .firstOrNull()?.valor?.trim().orEmpty()
        if (texto.isNotEmpty()) {
            MangaFoxLog.w(
                ETIQUETA,
                "busqueda por titulo pendiente de qfunc (escotilla v2.1)"
            )
            return emptyList()
        }
        val indice = filtros.filterIsInstance<Filtro.Orden>()
            .firstOrNull()?.indice ?: 0
        val ordenar = ordenarPorIndice.getOrNull(indice)
            ?: "view_count"
        val html = web.getHtml(
            "/series/?tipos%5B%5D=comic&ordenar=" + ordenar +
                "&direccion=desc&pagina=" + pagina
        ) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            parsearArchivo(rebanar(html, "archive-heading"))
        }
    }

    // ---------------------------------------- v2: politica de imagen

    /** El CDN image2 solo abre a peticiones de navegador real
     *  (evidencia imageRequest de Keiyoushi; cazado en vivo
     *  23-oct 02:55: sin Sec-Fetch el CDN responde 403). La
     *  fuente ES DUENA de su politica de imagen. */
    override fun cabecerasImagen(): Map<String, String> = mapOf(
        "Sec-Fetch-Dest" to "image",
        "Sec-Fetch-Mode" to "no-cors",
        "Sec-Fetch-Site" to "cross-site",
        "Referer" to "https://visualikigai.com/"
    )

    /** Host al que aplica la politica (sufijo). */
    override fun hostImagenes(): String = "ikigaimangas.cloud"

    // ------------------------------------------------------------ busqueda

    /** Buscador qwik (qfunc por WebView + POST): NO accesible
     *  desde la api congelada. Estado honesto propio: vacio. */
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
        val detalle = withContext(Dispatchers.IO) {
            val (capsPagina1, hayMas) = CapitulosIkigai.caps(html)
            ObraDetalle(
                id = obra.enlace,
                titulo = HtmlIkigai.textoDeClase(main, "card-title")
                    ?: obra.titulo,
                portadaUrl = bloqueArticleCard(main)
                    ?.let { HtmlIkigai.primeraImagen(it) } ?: obra.portadaUrl,
                sinopsis = primerParrafoDeClase(main, "card-body"),
                estado = HtmlIkigai.enlacesConTexto(main, "estados[]=")
                    .firstOrNull(),
                generos = HtmlIkigai.enlacesConTexto(main, "generos[]="),
                capitulos = capsPagina1,
                capitulosIncompletos = hayMas
            )
        }
        // PAGINACION (log 24-oct): solo entregabamos la pag 1 (24 de 58 caps). Keiyoushi: ?pagina=N.
        val detalleFinal =
            if (!detalle.capitulosIncompletos) detalle
            else {
                val ultima = withContext(Dispatchers.IO) {
                    CapitulosIkigai.ultimaPagina(html)
                }
                val vistas = detalle.capitulos
                    .map { it.enlace }.toMutableSet()
                val extras = mutableListOf<Capitulo>()
                var fallo = false
                for (pag in 2..ultima) {
                    val sep = if (obra.enlace.contains("?")) "&" else "?"
                    val ph = web.getHtml(obra.enlace + sep + "pagina=" + pag)
                    if (ph == null) { fallo = true; break }
                    CapitulosIkigai.caps(ph).first
                        .filter { vistas.add(it.enlace) }
                        .forEach { extras.add(it) }
                }
                MangaFoxLog.d(ETIQUETA, "paginador: 2.." + ultima +
                    " extra=" + extras.size +
                    (if (fallo) " (fallo)" else " ok"))
                if (extras.isEmpty()) detalle
                else detalle.copy(
                    capitulos = detalle.capitulos + extras,
                    capitulosIncompletos = fallo
                )
            }
        MangaFoxLog.d(
            ETIQUETA, "ficha '" + detalleFinal.titulo + "': " +
                detalleFinal.capitulos.size + " caps (" +
                (System.currentTimeMillis() - t0) + " ms)" +
                (if (detalleFinal.capitulosIncompletos) " [incompletos]" else "")
        )
        return detalleFinal
    }

    /** Cuerpo del primer <article class="...card...">: la
     *  portada de la ficha (figure > img, evidencia keiyoushi
     *  226). Orden de atributos irrelevante. */
    private fun bloqueArticleCard(html: String): String? {
        var idx = 0
        val apertura = Regex("<article\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: return null
            if (HtmlIkigai.tieneClase(m.value, "card")) {
                val fin = html.indexOf("</article>", m.range.last + 1)
                return if (fin > 0) html.substring(m.range.last + 1, fin)
                else html.substring(m.range.last + 1)
            }
            idx = m.range.last + 1
        }
    }

    /** Primer <p> dentro del primer bloque con la clase dada
     *  (la sinopsis vive en .card-body > p, NO pegada a la
     *  apertura: el titulo viene antes en el mismo card-body). */
    private fun primerParrafoDeClase(html: String, clase: String): String {
        var idx = 0
        val apertura = Regex("<(div|article)\\b[^>]*>")
        while (true) {
            val m = apertura.find(html, idx) ?: break
            if (HtmlIkigai.tieneClase(m.value, clase)) {
                val limite = minOf(m.range.last + 1 + 6000, html.length)
                val bloque = html.substring(m.range.last + 1, limite)
                return Regex("<p\\b[^>]*>(.*?)</p>", DOT_MATCHES_ALL)
                    .find(bloque)?.groupValues?.get(1)
                    ?.let { HtmlIkigai.limpiar(it) } ?: ""
            }
            idx = m.range.last + 1
        }
        return ""
    }

    /** Capitulos: section.card > ul.grid a.card en la propia
     *  ficha (pagina 1). Si hay nav de paginacion hay mas
     *  paginas: v1 entrega la primera y declara incompletos. */
    // -------------------------------------------------------------- lector

    /** La pagina del capitulo: <section div > img> en orden
     *  del DOM = orden de lectura (evidencia keiyoushi 291). */
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

    /** LOG 24-oct: 1 sola pagina por capitulo. El corte por
     *  </section> anidado descartaba el resto. Patron en vivo:
     *  paginas = image3.../series/<obra>/<cap>/NN-hash.webp
     *  (excluye miniaturas y banners). Orden DOM = lectura. */
    private fun extraerPaginas(html: String): List<String> {
        val urls = mutableListOf<String>()
        val patronPagina = Regex("/series/\\d+/\\d+/")
        Regex("<img[^>]+src="(https?://[^\"]+)"").findAll(html)
            .forEach { m ->
                val src = m.groupValues[1].trim()
                if (patronPagina.containsMatchIn(src) &&
                    !urls.contains(src)
                ) urls.add(src)
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
