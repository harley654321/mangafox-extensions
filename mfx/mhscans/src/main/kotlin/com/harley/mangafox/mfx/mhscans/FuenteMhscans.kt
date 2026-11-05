package com.harley.mangafox.mfx.mhscans

import com.harley.mangafox.data.parser.MiniHtml
import com.harley.mangafox.data.parser.Seccion
import com.harley.mangafox.data.parser.TemaFuente
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.lector.PaginasCapitulo
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EXTENSION MFX mhscans: parser Madara validado en vivo
 * (generos, ajax96, busqueda ajax, ficha, lector page-break).
 * rateLimit 1/3 s (rafaga tumba ~24 h); recovery mhscans.com.
 */
class FuenteMhscans(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "mhscans"

    private val web: ClienteWeb = contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    // ------------------------------------------------------------ generos

    private var generosCache: List<Genero>? = null

    override suspend fun generos(): List<Genero> {
        generosCache?.let { return it }
        val xml = web.getHtml("/wp-sitemap-taxonomies-wp-manga-genre-1.xml")
        val delSitio = if (xml == null) null else
            Regex("/manga-genre/([a-z0-9_-]+)/")
                .findAll(xml)
                .map { it.groupValues[1] }
                .distinct()
                .map { slug -> Genero(slug, NOMBRES_GENERO[slug] ?: bonito(slug)) }
                .toList()
        val res = if (delSitio.isNullOrEmpty()) GENEROS_RESPALDO else delSitio
        generosCache = res
        MangaFoxLog.d("Mfx.Mhscans", "generos: " + res.size + " (sitio o respaldo)")
        return res
    }

    // ------------------------------------------------------------ directorio

    private var claveListado = ""
    private var tamanoHtmlListado = 0
    private var ecoDetectado = false
    private var primerSlugAjax = ""

    override val prefetchAlFinal: Boolean get() = true

    override fun tamanoPaginaPedido(
        generoSlug: String?,
        busqueda: Boolean
    ) = if (ecoDetectado) tamanoHtmlListado else AJAX_POR_PAGINA

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        val clave = generoSlug ?: "todos"
        if (pagina <= 1 || clave != claveListado) {
            claveListado = clave
            tamanoHtmlListado = 0
            ecoDetectado = false
            primerSlugAjax = ""
            val ajax = try {
                listarAjax96(generoSlug, 0)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                MangaFoxLog.w("Mfx.Mhscans", "lote ajax cayo, calzo HTML: " + e.message)
                emptyList()
            }
            if (ajax.isNotEmpty()) {
                primerSlugAjax = ajax[0].id
                MangaFoxLog.d("Mfx.Mhscans", "lote 1 via ajax: " + ajax.size + " obras")
                return ajax
            }
            val lista = parsear(web.getHtml(rutaListado(generoSlug, 1)))
            tamanoHtmlListado = lista.size
            ecoDetectado = true
            return lista
        }
        if (!ecoDetectado) {
            val ajax = listarAjax96(generoSlug, pagina - 1)
            if (ajax.isEmpty()) return emptyList()
            if (pagina == 2 && ajax[0].id == primerSlugAjax) {
                ecoDetectado = true
                MangaFoxLog.w("Mfx.Mhscans", "eco ajax: calibro HTML")
            } else {
                MangaFoxLog.d("Mfx.Mhscans", "lote " + pagina + " via ajax: " + ajax.size)
                return ajax
            }
        }
        if (tamanoHtmlListado == 0) {
            tamanoHtmlListado = parsear(web.getHtml(rutaListado(generoSlug, 1))).size
        }
        val paginasCubiertas = if (tamanoHtmlListado > 0)
            (AJAX_POR_PAGINA + tamanoHtmlListado - 1) / tamanoHtmlListado else 1
        return parsear(web.getHtml(rutaListado(generoSlug, paginasCubiertas + pagina - 1)))
    }

    private fun rutaListado(generoSlug: String?, pagina: Int): String {
        val p = if (pagina < 1) 1 else pagina
        val extra = if (generoSlug.isNullOrBlank()) "" else "&genre=" + generoSlug
        return "/library?page=" + p + extra
    }

    /** Lote admin-ajax de 96 obras, page 0-based (validado en vivo
     *  en MHScans: page SI pagina; vars[paged] es ignorado). */
    private suspend fun listarAjax96(
        generoSlug: String?,
        page: Int
    ): List<ObraHome> {
        val params = ajaxBase().toMutableMap()
        params["vars[posts_per_page]"] = AJAX_POR_PAGINA.toString()
        params["page"] = page.toString()
        params["vars[paged]"] = (page + 1).toString()
        if (generoSlug != null) {
            params["vars[tax_query][0][taxonomy]"] = "wp-manga-genre"
            params["vars[tax_query][0][field]"] = "slug"
            params["vars[tax_query][0][terms]"] = generoSlug
        }
        val html = web.postForm("/wp-admin/admin-ajax.php", params)
            ?: throw IllegalStateException("ajax sin respuesta")
        return parsear(html)
    }

    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val codificado = java.net.URLEncoder.encode(texto, "UTF-8")
        val html = web.getHtml(
            rutaListado(null, pagina) + "&q=" + codificado
        ) ?: return emptyList()
        return parsear(html)
    }

    private fun ajaxBase(): Map<String, String> = linkedMapOf(
        "action" to "madara_load_more",
        "template" to "madara-core/content/content-archive",
        "vars[paged]" to "1",
        "vars[template]" to "archive",
        "vars[posts_per_page]" to "50",
        "vars[post_type]" to "wp-manga",
        "vars[post_status]" to "publish",
        "vars[manga_archives_item_layout]" to "big_thumbnail"
    )

    // ------------------------------------------------------------ home

    override suspend fun cargarRecientes(): List<ObraHome> =
        parsear(web.getHtml("/library?page=1&sort=updated"))

    override suspend fun cargarPopulares(): List<ObraHome> =
        parsear(web.getHtml("/library?page=1&sort=views"))

    // RANKING = rating REAL (decision del dueno 21-oct: views era un
    // disfraz de Populares y duplicaba sus paginas al sitio; el sitio
    // soporta m_orderby=rating, lista distinta verificada en vivo).
    override suspend fun cargarRanking(): List<ObraHome> =
        parsear(web.getHtml("/library?page=1&sort=rating"))

    override suspend fun cargarSeccion(
        seccion: Seccion,
        pagina: Int
    ): List<ObraHome> {
        val (base, pag) = when (seccion) {
            Seccion.RECIENTES -> "/library?page=1&sort=updated" to pagina
            Seccion.POPULARES -> "/library?page=1&sort=views" to pagina
            Seccion.RANKING -> "/library?page=1&sort=rating" to pagina
        }
        val ruta = if (pag <= 1) base
        else base.replace("/library?page=1", "/manga/page/" + pag + "/")
        return parsear(web.getHtml(ruta))
    }

    // ------------------------------------------------------------ parseo listado

    private suspend fun parsear(html: String?): List<ObraHome> =
        withContext(Dispatchers.IO) {
            if (html == null) return@withContext emptyList()
            val obras = HtmlMhscans.lista(html)
                .filterNot { it.titulo.lowercase().contains("mhscans") }
                .distinctBy { it.id }
            MangaFoxLog.d("Mfx.Mhscans", "parseados " + obras.size + " items")
            obras
        }

    private fun extraerEnlace(bloque: String): Pair<String, String>? {
        val (href, tituloAttr) = MiniHtml.enlaceYTitulo(bloque)
        if (href != null && !tituloAttr.isNullOrBlank()) return href to tituloAttr
        Regex(
            "<h[1-4][^>]*>.{0,300}?<a\\s[^>]*href=\"([^\"]+)\"[^>]*>\\s*([^<]+?)\\s*</a>",
            RegexOption.DOT_MATCHES_ALL
        ).find(bloque)?.let { return it.groupValues[1] to it.groupValues[2] }
        if (href != null) {
            val texto = Regex(">\\s*([^<>]+)\\s*</a>").find(bloque)?.groupValues?.get(1)
            if (!texto.isNullOrBlank()) return href to texto
        }
        return null
    }

    // ------------------------------------------------------------ ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val enlace = obra.enlace
        if (enlace.isBlank()) return null
        val ruta = if (enlace.startsWith("http")) {
            try { java.net.URI(enlace).path ?: return null }
            catch (e: Exception) { return null }
        } else enlace
        val html = web.getHtml(ruta) ?: run {
            MangaFoxLog.w("Mfx.Mhscans", "ficha sin HTML: " + ruta)
            return null
        }
        // TEMA NUEVO (05-nov): los capitulos ya NO salen por
        // admin-ajax; son una pagina HTML paginada con /leer/.
        val base = ruta.trimEnd('/')
        val acumulado = StringBuilder()
        var paginasLeidas = 0
        var paginaCap = 1
        while (paginaCap <= 25) {
            val trozo = web.getHtml(
                base + "/chapters?page=" + paginaCap
            ) ?: break
            paginasLeidas++
            if (HtmlMhscans.capitulos(trozo).isEmpty()) break
            acumulado.append(trozo)
            if (!trozo.contains("chapters?page=" + (paginaCap + 1))) break
            paginaCap++
        }
        val capsHtml = acumulado.toString()
        return withContext(Dispatchers.IO) {
            extraerFicha(html, capsHtml, obra)
        }.also {
            MangaFoxLog.d("Mfx.Mhscans", "ficha '" + obra.titulo + "': " +
                (it?.capitulos?.size ?: 0) + " caps en " +
                paginasLeidas + " pagina(s) de capitulos")
        }
    }

    private fun extraerFicha(
        html: String, capsHtml: String, obra: ObraHome
    ): ObraDetalle? {
        // TEMA NUEVO (05-nov): metadatos estandar (og/h1/meta) porque
        // las clases wp-manga desaparecieron del sitio.
        val titulo = texto(
            Regex("<meta property=\"og:title\" content=\"([^\"]+)\"")
                .find(html)?.groupValues?.get(1)
        ) ?: texto(
            Regex("<h1[^>]*>(.{0,300}?)</h1>", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)
        ) ?: obra.titulo
        val sinopsis = texto(
            Regex(
                "<meta name=\"description\" content=\"([^\"]{0,4000})\"",
                RegexOption.DOT_MATCHES_ALL
            ).find(html)?.groupValues?.get(1)
        ).orEmpty()
        val portadaCruda = Regex(
            "<meta property=\"og:image\" content=\"([^\"]+)\""
        ).find(html)?.groupValues?.get(1)
        val portada = when {
            portadaCruda.isNullOrBlank() -> obra.portadaUrl
            portadaCruda.startsWith("http") -> portadaCruda
            else -> BASE.trimEnd('/') + "/" + portadaCruda.trimStart('/')
        }
        var estado: String? = null
        Regex(">(En curso|Finalizado|Activo|Completado|Pausado)<",
            RegexOption.IGNORE_CASE)
            .find(html)?.let { estado = traducirEstado(it.groupValues[1]) }
        val generos = Regex(
            "href=\"/(?:genero|genre|comics\\?genre=)([a-z0-9-]{2,40})\""
        ).findAll(html).map { bonito(it.groupValues[1]) }
            .distinct().take(12).toList()
        val (capitulos, premium) = extraerCapitulos(capsHtml)
        MangaFoxLog.d(
            "Mfx.Mhscans", "ficha: " + capitulos.size + " caps (" +
                premium + " premium), generos=" + generos.size +
                ", sinopsis=" + sinopsis.length + "B"
        )
        return ObraDetalle(
            id = obra.id,
            titulo = titulo,
            portadaUrl = portada,
            sinopsis = sinopsis,
            estado = estado,
            generos = generos,
            capitulos = capitulos
        )
    }

    /** "14 de octubre de 2026" -> dd/MM/yyyy (dueno 24-oct). */
    private fun fechaNormal(crudo: String?): String? {
        if (crudo.isNullOrBlank()) return null
        val m = Regex("(\\d{1,2}) de ([a-záéíóú]+) de (\\d{4})").find(crudo) ?: return crudo
        val mes = mapOf("enero" to 1, "febrero" to 2, "marzo" to 3,
            "abril" to 4, "mayo" to 5, "junio" to 6, "julio" to 7,
            "agosto" to 8, "septiembre" to 9, "setiembre" to 9,
            "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )[m.groupValues[2].lowercase()] ?: return crudo
        return m.groupValues[1] + "/" +
            (if (mes < 10) "0" + mes else mes.toString()) + "/" +
            m.groupValues[3]
    }

    private fun extraerCapitulos(
        html: String
    ): Pair<List<Capitulo>, Int> = HtmlMhscans.capitulos(html) to 0

    // ------------------------------------------------------------ lector

    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        val enlace = capitulo.enlace
        if (enlace.isBlank()) return PaginasCapitulo(emptyList())
        val ruta = if (enlace.startsWith("http")) {
            try { java.net.URI(enlace).path ?: return PaginasCapitulo(emptyList()) }
            catch (e: Exception) { return PaginasCapitulo(emptyList()) }
        } else enlace
        val html = web.getHtml(ruta) ?: run {
            MangaFoxLog.w("Mfx.Mhscans", "capitulo sin HTML: " + ruta)
            return PaginasCapitulo(emptyList())
        }
        val urls = withContext(Dispatchers.IO) { extraerPaginas(html) }
        MangaFoxLog.d("Mfx.Mhscans", "capitulo '" + capitulo.titulo + "': " +
            urls.size + " paginas")
        return PaginasCapitulo(urls, referer = BASE.trimEnd('/') + ruta)
    }

    private fun extraerPaginas(html: String): List<String> =
        HtmlMhscans.paginas(html).map { resolver(it) }.distinct()

    private fun urlDeImagen(img: String): String? {
        for (nombre in listOf(
            "data-src", "data-lazy-src", "data-lzl-src",
            "data-cfsrc", "data-manga-src", "srcset", "src"
        )) {
            val valor = Regex("[\\s']" + nombre + "\\s*=\\s*\"([^\"]*)\"")
                .find(img)?.groupValues?.get(1)?.trim() ?: continue
            if (valor.isEmpty() || valor.startsWith("data:")) continue
            if (nombre == "srcset") return descriptorMayor(valor)
            return valor
        }
        return null
    }

    private fun descriptorMayor(srcset: String): String? {
        var mejor: String? = null
        var mejorDesc = -1
        for (candidato in srcset.split(',')) {
            val partes = candidato.trim().split(Regex("\\s+"))
            val url = partes.firstOrNull() ?: continue
            if (url.isEmpty()) continue
            val desc = partes.getOrNull(1)?.trimEnd('w', 'h')?.toIntOrNull() ?: 0
            if (desc >= mejorDesc) { mejor = url; mejorDesc = desc }
        }
        return mejor
    }

    private fun resolver(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:" + url
        url.startsWith("/") -> BASE.trimEnd('/') + url
        else -> url
    }

    // ------------------------------------------------------------ comunes

    private fun traducirEstado(crudo: String): String = when (crudo.lowercase()) {
        "ongoing" -> "En curso"
        "on going" -> "En curso"
        "completed" -> "Finalizado"
        "end" -> "Finalizado"
        "hiatus" -> "En pausa"
        "on hold" -> "En pausa"
        "canceled", "cancelled" -> "Cancelado"
        "dropped" -> "Abandonado"
        else -> crudo
    }

    private fun texto(crudo: String?): String? {
        if (crudo == null) return null
        val plano = Regex("<[^>]+>").replace(crudo, " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&#8211;", "-").replace("&#8217;", "'")
        val res = plano.trim()
        return res.ifEmpty { null }
    }

    private fun bonito(slug: String): String =
        slug.replace('-', ' ').split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { palabra ->
                palabra.replaceFirstChar { c -> c.uppercaseChar() }
            }

    private companion object {
        const val BASE = "https://mhscans.com"
        const val RITMO_MS = 3000L
        const val RECOVERY = "https://mhscans.com"
        const val AJAX_POR_PAGINA = 96

        /** Nombres visibles de los slugs de genero de MHScans. */
        val NOMBRES_GENERO: Map<String, String> = mapOf(
            "accion" to "Acción",
            "artes-marciales" to "Artes marciales",
            "aventura" to "Aventura",
            "ciencia-ficcion" to "Ciencia ficción",
            "comedia" to "Comedia",
            "comic" to "Cómic",
            "cooks" to "Infidelidad",
            "deporte" to "Deporte",
            "drama_" to "Drama",
            "fantasia" to "Fantasía",
            "historia" to "Historia",
            "horror" to "Horror",
            "josei_" to "Josei",
            "live-action" to "Live action",
            "manga" to "Manga",
            "manhua" to "Manhua",
            "manhwa_" to "Manhwa",
            "mecha" to "Mecha",
            "misterio" to "Misterio",
            "one-shot" to "One shot",
            "psicologico" to "Psicológico",
            "recuerdos-de-la-vida" to "Recuerdos de la vida",
            "romance" to "Romance",
            "seinen" to "Seinen",
            "shoujo" to "Shoujo",
            "shounen" to "Shounen",
            "supernatural" to "Supernatural",
            "vida-escolar" to "Vida escolar"
        )

        /** Respaldo honesto si el sitemap cayera: los mismos
         *  generos oficiales del sitio como slugs. */
        val GENEROS_RESPALDO: List<Genero> = NOMBRES_GENERO.entries
            .map { (slug, nombre) -> Genero(slug, nombre) }
    }
}
