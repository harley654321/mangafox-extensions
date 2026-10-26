package com.harley.mangafox.mfx.mantrazscan

import com.harley.mangafox.data.parser.MiniHtml
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EXTENSION MFX mantrazscan: Mantraz Scan (mantrazscanss.lat).
 *
 * Sitio Next.js autopsiado en vivo 26-oct-2026 (evidencia
 * keiyoushi src/es/mantrazscan + navegacion real desde este
 * servidor). Detras de Cloudflare pero SIN challenge para
 * requests normales (todas las respuestas aterrizaron 200).
 *
 * SECCIONES (todas verificadas contra HTML real):
 *   RECIENTES : /explorar/page/N (15 tarjetas s-card por
 *               pagina, orden = ultimas actualizaciones; la
 *               paginacion real llega hasta la 353 y MAS ALLA
 *               devuelve 0 tarjetas = FIN honesto, no hay
 *               repeticion fantasma).
 *   POPULARES : VACIO con log explicito. El sitio NO tiene
 *               listado por popularidad: ?ordenar=views y
 *               ?sort=popular devuelven el mismo listado
 *               (comparado byte a byte contra /explorar/).
 *               Ley de honestidad del CONTRATO: no se duplica
 *               RECIENTES ni se inventa nada.
 *   RANKING   : home, slider "🔥 Mas vistos hoy": 10
 *               article.tsl-slide con posicion (#1 en
 *               tendencia). PAGINA UNICA: pag>1 = vacio.
 *   DIRECTORIO: /explorar/page/N/?genero=<slug> (paginado,
 *               verificado con genero=romance hasta page/2).
 *   BUSQUEDA  : /explorar/?q=<texto> (URLEncoded). UNA SOLA
 *               PAGINA (verificado: /explorar/page/2/?q=stream
 *               devuelve 0 resultados aunque el termino exista):
 *               pag>1 = vacio honesto.
 *   FICHA     : /manga/<slug>/ TODO en una pagina: titulo
 *               (h1.series-title), portada (.series-cover img),
 *               estado (badge-pill: "En emision", etc.), nota
 *               (rate-meta <b>5.0</b>), generos (a.genre-tag),
 *               sinopsis (.series-desc) y TODOS los capitulos
 *               (a.ch-row, 113/113 en una sola pagina: sin
 *               capitulosIncompletos). Fechas reales de cada
 *               capitulo desde el flight data de Next.js
 *               ("dates":{"113":unix_segundos}).
 *   LECTOR    : paginas del capitulo, imagenes /img/WP-manga/
 *               en orden del DOM (regex de evidencia
 *               keiyoushi). VERIFICADO: las imagenes responden
 *               200 SIN Referer ni cabeceras especiales.
 *
 * Dominio volatil (.lat tras migraciones; los .com muertos):
 * recoveryUrl = el propio dominio vigente.
 *
 * Rate limit 1 peticion / 2 s (Cloudflare, cortesia).
 */
class FuenteMantrazScan(contexto: ContextoMfx) : FuenteMfx2 {

    override val id: String = "mantrazscan"

    private val web: ClienteWeb = contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    // ------------------------------------------------------------ secciones

    override suspend fun cargarRecientes(): List<ObraHome> =
        cargarSeccion(Seccion.RECIENTES, 1)

    /** Sitio sin listado popular: estado honesto, jamas duplicar. */
    override suspend fun cargarPopulares(): List<ObraHome> {
        MangaFoxLog.d(
            ETIQUETA,
            "populares: 0 (FIN honesto: el sitio no tiene listado " +
                "por popularidad, ?ordenar=views lo ignora)"
        )
        return emptyList()
    }

    override suspend fun cargarRanking(): List<ObraHome> =
        cargarSeccion(Seccion.RANKING, 1)

    override suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        return when (seccion) {
            Seccion.RECIENTES ->
                explorar(pagina, genero = null, texto = null)
            Seccion.RANKING -> {
                if (pagina > 1) {
                    MangaFoxLog.d(
                        ETIQUETA,
                        "ranking pag=" + pagina + ": 0 (FIN: slider unico)"
                    )
                    return emptyList()
                }
                val html = web.getHtml("/") ?: return emptyList()
                withContext(Dispatchers.IO) { parsearTendencias(html) }
            }
            else -> {
                // POPULARES pasa por cargarPopulares; por contrato
                // toda seccion pedida aca se responde honesta.
                MangaFoxLog.d(ETIQUETA, "seccion " + seccion + ": 0")
                emptyList()
            }
        }.also {
            MangaFoxLog.d(
                ETIQUETA,
                "seccion " + seccion.name + " pag=" + pagina + ": " +
                    it.size + " obras" + (if (it.isEmpty()) " (FIN)" else "")
            )
        }
    }

    /** Slider "🔥 Mas vistos hoy" de la home: article.tsl-slide
     *  con a.tsl-cover (href), img (portada), h2.tsl-title y
     *  span.tsl-num (posicion #N en tendencia). */
    private fun parsearTendencias(html: String): List<ObraHome> {
        return MiniHtml.bloques(html, "<article class=\"tsl-slide")
            .mapNotNull { bloque ->
                val apertura = Regex("<a\\s[^>]*tsl-cover[^>]*>").find(bloque)
                val href = apertura?.let { MiniHtml.atributo(it.value, "href") }
                    ?.trim() ?: return@mapNotNull null
                val titulo = Regex(
                    "<h2[^>]*tsl-title[^>]*>(.*?)</h2>",
                    RegexOption.DOT_MATCHES_ALL
                ).find(bloque)?.groupValues?.get(1)
                    ?.let { limpiar(it) }?.takeIf { it.length >= 2 }
                    ?: return@mapNotNull null
                val pos = Regex(
                    "tsl-num[^>]*>(\\d+)<"
                ).find(bloque)?.groupValues?.get(1)
                ObraHome(
                    id = href,
                    titulo = titulo,
                    portadaUrl = MiniHtml.imagen(bloque) ?: "",
                    etiqueta = pos?.let { "#" + it },
                    enlace = href
                )
            }.distinctBy { it.id }
    }

    // ------------------------------------------------- explorador y tarjetas

    /** URL del explorador: pag 1 = /explorar/, pag>=2 =
     *  /explorar/page/N/ (verificado: /page/1/ NO existe,
     *  devuelve 0 tarjetas). genero y texto se combinan. */
    private fun rutaExplorar(pagina: Int, genero: String?, texto: String?): String {
        val base = if (pagina <= 1) "/explorar/" else "/explorar/page/" + pagina + "/"
        val params = mutableListOf<String>()
        genero?.takeIf { it.isNotBlank() }?.let { params.add("genero=" + it) }
        texto?.takeIf { it.isNotBlank() }?.let {
            params.add("q=" + URLEncoder.encode(it, "UTF-8"))
        }
        return if (params.isEmpty()) base
               else base + "?" + params.joinToString("&")
    }

    /** Listado de tarjetas s-card: a.s-card-imglink (href),
     *  a.s-card-title (titulo), primer img (portada) y el
     *  primer ch-chip como etiqueta (Cap. N del ultimo cap). */
    private suspend fun explorar(
        pagina: Int,
        genero: String?,
        texto: String?
    ): List<ObraHome> {
        val html = web.getHtml(rutaExplorar(pagina, genero, texto))
            ?: return emptyList()
        return withContext(Dispatchers.IO) { parsearTarjetas(html) }
    }

    private fun parsearTarjetas(html: String): List<ObraHome> {
        // Marca EXACTA de apertura: "s-card" a secas romperia
        // bloques en las variantes compuestas (s-card-imglink,
        // s-card-title...) y las tarjetas saldrian vacias.
        return MiniHtml.bloques(html, "<div class=\"s-card\">")
            .mapNotNull { bloque ->
                val imgLink = Regex(
                    "<a\\s[^>]*s-card-imglink[^>]*>"
                ).find(bloque)?.value ?: return@mapNotNull null
                val href = MiniHtml.atributo(imgLink, "href")?.trim()
                    ?: return@mapNotNull null
                if (!href.startsWith("/manga/")) return@mapNotNull null
                val titulo = Regex(
                    "s-card-title[^>]*>(.*?)</a>",
                    RegexOption.DOT_MATCHES_ALL
                ).find(bloque)?.groupValues?.get(1)
                    ?.let { limpiar(it) }?.takeIf { it.length >= 2 }
                    ?: return@mapNotNull null
                val cap = Regex(
                    "ch-chip[^>]*>Cap\\s*(?:<!--[^>]*-->)*\\s*(\\d+)"
                ).find(bloque)?.groupValues?.get(1)
                ObraHome(
                    id = href,
                    titulo = titulo,
                    portadaUrl = MiniHtml.imagen(bloque) ?: "",
                    etiqueta = cap?.let { "Cap. " + it },
                    enlace = href
                )
            }.distinctBy { it.id }
    }

    // ------------------------------------------------------------ directorio

    override suspend fun generos(): List<Genero> = GENEROS_MANTRAZ

    /** 15 tarjetas por pagina (verificado en /explorar/ y
     *  /explorar/page/2/?genero=romance). */
    override fun tamanoPaginaPedido(generoSlug: String?, busqueda: Boolean) = 15

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        return explorar(pagina, generoSlug, null).also {
            MangaFoxLog.d(
                ETIQUETA,
                "directorio pag=" + pagina + " genero=" +
                    (generoSlug ?: "todos") + ": " + it.size +
                    " obras" + (if (it.isEmpty()) " (FIN)" else "")
            )
        }
    }

    // -------------------------------------------------------------- busqueda

    /** Busqueda del sitio: UNA SOLA pagina de resultados
     *  (verificado en vivo: page/2 con q devuelve 0 tarjetas). */
    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        if (texto.isBlank()) return emptyList()
        if (pagina > 1) {
            MangaFoxLog.d(
                ETIQUETA,
                "busqueda pag=" + pagina + ": 0 (FIN: resultados unicos)"
            )
            return emptyList()
        }
        return explorar(1, null, texto.trim()).also {
            MangaFoxLog.d(
                ETIQUETA, "busqueda '" + texto + "': " + it.size + " obras"
            )
        }
    }

    // -------------------------------------------- v2: filtros tipados

    /** Filtros del explorador: texto (titulo) + genero (los 19
     *  curados). "Todos" = sin parametro. Con texto presente la
     *  busqueda es de pagina unica (verificado); solo genero
     *  mantiene la paginacion completa del directorio. */
    override suspend fun filtros(): List<Filtro> = listOf(
        Filtro.Texto("Título"),
        Filtro.Seleccion(
            "Género",
            listOf("Todos") + GENEROS_MANTRAZ.map { it.nombre }
        )
    )

    override suspend fun buscar(
        filtros: List<Filtro>,
        pagina: Int
    ): List<ObraHome> {
        val texto = filtros.filterIsInstance<Filtro.Texto>()
            .firstOrNull()?.valor.orEmpty().trim()
        val indiceGenero = filtros.filterIsInstance<Filtro.Seleccion>()
            .firstOrNull()?.indice ?: 0
        val genero = if (indiceGenero > 0) {
            GENEROS_MANTRAZ.getOrNull(indiceGenero - 1)?.slug
        } else null
        if (texto.isBlank() && genero == null) return emptyList()
        // Solo genero: paginacion del directorio. Con texto:
        // resultados de pagina unica (pagina>1 = vacio honesto).
        if (texto.isBlank()) return cargarDirectorio(pagina, genero)
        if (pagina > 1) {
            MangaFoxLog.d(
                ETIQUETA,
                "busqueda pag=" + pagina + ": 0 (FIN: resultados unicos)"
            )
            return emptyList()
        }
        return explorar(1, genero, texto)
    }

    // ---------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val t0 = System.currentTimeMillis()
        val html = web.getHtml(obra.enlace) ?: return null
        val detalle = withContext(Dispatchers.IO) { parsearFicha(html, obra) }
        MangaFoxLog.d(
            ETIQUETA,
            "ficha '" + obra.titulo + "': " + detalle.capitulos.size +
                " caps en " + (System.currentTimeMillis() - t0) + " ms"
        )
        return detalle
    }

    private fun parsearFicha(html: String, obra: ObraHome): ObraDetalle {
        val titulo = Regex(
            "<h1[^>]*series-title[^>]*>(.*?)</h1>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)?.let { limpiar(it) }
            ?.takeIf { it.isNotBlank() } ?: obra.titulo

        val portada = Regex(
            "series-cover[^>]*>[^<]*<img[^>]*src=\"([^\"]+)\""
        ).find(html)?.groupValues?.get(1)
            ?: MiniHtml.imagen(html) ?: obra.portadaUrl

        // badge-pill badge-ongoing "En emision" (y hermanas:
        // completed/dropped segun el sitio).
        val estado = Regex(
            "badge-pill[^>]*>([^<]{2,40})</span>"
        ).find(html)?.groupValues?.get(1)?.trim()

        // rate-meta: <b>5.0</b> · 6 votos
        val nota = Regex(
            "rate-meta[^>]*><b>([0-9]+(?:[.,][0-9]+)?)</b>"
        ).find(html)?.groupValues?.get(1)
            ?.replace(',', '.')?.toFloatOrNull()

        val generos = Regex(
            "genre-tag[^>]*>([^<]{2,40})</a>"
        ).findAll(html).map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }.distinct().toList()

        val sinopsis = Regex(
            "series-desc[^>]*>(.*?)</div>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1)?.let { limpiar(it) }
            ?.takeIf { it.length >= 10 } ?: ""

        // TODOS los capitulos viven en la misma pagina: el boton
        // "Ver todos" del sitio solo EXPANDE el mismo DOM (veri-
        // ficado en navegador real 26-oct: stream 114/114 y
        // renacer 220/220, caps numerados 279-498 porque el scan
        // solo tiene esos; los comentarios del sitio lo confirman).
        val fechas = fechasFlight(html)
        // El tag completo se captura y el href se extrae aparte:
        // orden de atributos libre (leccion viva de ikigai: el
        // HTML puede escribir href ANTES que class).
        val capitulos = Regex("<a\\s[^>]*ch-row[^>]*>")
            .findAll(html).mapNotNull { m ->
            val enlace = MiniHtml.atributo(m.value, "href")?.trim()
                ?: return@mapNotNull null
            val numero = Regex(
                "capitulo-([0-9]+(?:\\.[0-9]+)?)"
            ).find(enlace)?.groupValues?.get(1) ?: return@mapNotNull null
            Capitulo(
                numero = numero,
                titulo = "Capítulo " + numero,
                fecha = fechas[numero]?.let { fechaDeEpoch(it) },
                enlace = enlace
            )
        }.distinctBy { it.enlace }
            .sortedByDescending { it.numero.toFloatOrNull() ?: -1f }
            .toList()

        // Defensa honesta (leccion viva de ikigai): el contador
        // "Capitulos" de la propia ficha vs los caps parseados.
        // Verificado en vivo: renacer 220/220 y stream 114/114.
        // Si el DOM trajera menos, la ficha se marca incompleta
        // y el kernel ofrece reintentar; jamas caps truncados
        // en silencio.
        val capsDeclarados = Regex(
            "series-stat-val\\">(\\d+)</div>" +
                "<div class=\"series-stat-lbl\">Capítulos"
        ).find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val incompletos =
            capsDeclarados > 0 && capitulos.size < capsDeclarados
        if (incompletos) {
            MangaFoxLog.w(
                ETIQUETA,
                "ficha incompleta: " + capitulos.size + " caps " +
                    "parseados de " + capsDeclarados + " declarados"
            )
        }

        return ObraDetalle(
            id = obra.id,
            titulo = titulo,
            portadaUrl = portada,
            sinopsis = sinopsis,
            estado = estado,
            nota = nota,
            generos = generos,
            capitulos = capitulos,
            capitulosIncompletos = incompletos
        )
    }

    /** Fechas de capitulos del flight data de Next.js: en el HTML
     *  crudo viajan ESCAPADAS dentro de self.__next_f.push:
     *  \"dates\":{\"113\":1772806126,...}. Se normalizan las
     *  comillas y se leen los pares numero -> epoch en segundos
     *  (evidencia keiyoushi ChapterDatesDto + autopsia local). */
    private fun fechasFlight(html: String): Map<String, Long> {
        val plano = html.replace("\\\"", "\"")
        val cuerpo = Regex("\"dates\"\\s*:\\s*\\{([^}]*)\\}")
            .find(plano)?.groupValues?.get(1) ?: return emptyMap()
        val res = mutableMapOf<String, Long>()
        Regex("\"(\\d+(?:\\.\\d+)?)\"\\s*:\\s*(\\d{9,13})")
            .findAll(cuerpo).forEach { m ->
                val n = m.groupValues[1]
                val epoch = m.groupValues[2].toLongOrNull() ?: return@forEach
                // segundos o milisegundos: normalizar a segundos
                res[n] = if (epoch > 10_000_000_000L) epoch / 1000 else epoch
            }
        return res
    }

    private fun fechaDeEpoch(segundos: Long): String? = runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.US)
            .format(Date(segundos * 1000))
    }.getOrNull()

    // -------------------------------------------------------------- lector

    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        val html = web.getHtml(capitulo.enlace) ?: return null
        val urls = withContext(Dispatchers.IO) {
            // Regex de evidencia keiyoushi (IMAGE_REGEX): las
            // paginas viven como URLs /WP-manga/ dentro del
            // HTML de Next.js, con \\ en el flight data. Orden
            // del DOM, sin repetir (el slider pre-carga la
            // primera imagen dos veces).
            Regex(
                "https://[^\"\\\\ ]+/WP-manga/[^\"\\\\ ]+\\." +
                    "(?:webp|WEBP|jpg|JPG|jpeg|JPEG|png|PNG)"
            ).findAll(html).map { it.value }.distinct().toList()
        }
        MangaFoxLog.d(
            ETIQUETA,
            "lector cap " + capitulo.numero + ": " + urls.size + " paginas"
        )
        if (urls.isEmpty()) return null
        // Verificado en vivo: las imagenes responden 200 SIN
        // Referer ni cabeceras especiales -> nada que inyectar.
        return PaginasCapitulo(urls, referer = null, cabeceras = emptyMap())
    }

    // -------------------------------------------------------------- utils

    /** Limpia etiquetas anidadas, comentarios HTML y espacios. */
    private fun limpiar(crudo: String): String {
        return crudo
            .replace("<!--", " ").replace("-->", " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&#39;", "'")
            .replace("&quot;", "\"").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val ETIQUETA = "Mfx.Mantraz"

        /** Dominio vigente 26-oct-2026 (los .com anteriores
         *  murieron; migran entre TLDs). */
        const val BASE = "https://mantrazscanss.lat"
        const val RECOVERY = BASE
        const val RITMO_MS = 2000L
    }
}
