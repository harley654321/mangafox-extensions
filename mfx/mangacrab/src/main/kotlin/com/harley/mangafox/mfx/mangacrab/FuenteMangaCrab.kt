package com.harley.mangafox.mfx.mangacrab

import com.harley.mangafox.data.parser.Seccion
import com.harley.mangafox.data.web.ClienteWeb
import com.harley.mangafox.debug.MangaFoxLog
import com.harley.mangafox.logic.detalle.Capitulo
import com.harley.mangafox.logic.detalle.ObraDetalle
import com.harley.mangafox.logic.directorio.Genero
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.logic.lector.CapituloBloqueadoException
import com.harley.mangafox.logic.lector.PaginasCapitulo
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * EXTENSION MFX mangacrab — MangaCrab (es.mangacrab.org).
 *
 * API JSON /api/mv/ autopsiada en vivo 21-oct-2026 (evidencia
 * keiyoushi + verificacion propia):
 *
 *   RECIENTES : ?feed=updated     (24/pag, 1162 series)
 *   POPULARES : ?feed=discover    (24/pag)
 *   RANKING   : ?feed=discover&ranking=weekly (636, lista
 *               DISTINTA de discover: 3 secciones reales)
 *   DIRECTORIO: ?feed=discover[&genres=<slug>] + ?search=
 *   FICHA     : /api/mv/mangas/by-slug/<slug> (estado label
 *               "En curso", generos con nombre)
 *   CAPITULOS : /api/mv/mangas/<id>/chapters?per_page=50 (925
 *               caps en la serie de prueba: v1 trae la primera
 *               pagina y declara capitulosIncompletos honesto)
 *   LECTOR     : /api/mv/mangas/<id>/chapter?chapter_slug=...
 *               -> content.pages[] con URLS FIRMADAS
 *               (expires+md5, cargan sin nada extra:
 *               verificado 200 image/webp). Capitulo VIP o
 *               bloqueado -> CapituloBloqueadoException.
 *
 * FIN honesto por pagination.has_next del propio JSON. El id
 * numerico del manga viaja en ObraDetalle.id (el lector lo
 * necesita para armar la ruta del capitulo).
 */
class FuenteMangaCrab(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "mangacrab"

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
        val ruta = when (seccion) {
            Seccion.RECIENTES ->
                "/api/mv/mangas?page=" + pagina +
                    "&per_page=" + POR_PAGINA + "&feed=updated"
            Seccion.POPULARES ->
                "/api/mv/mangas?page=" + pagina +
                    "&per_page=" + POR_PAGINA + "&feed=discover"
            Seccion.RANKING ->
                "/api/mv/mangas?page=" + pagina +
                    "&per_page=" + POR_PAGINA +
                    "&feed=discover&ranking=weekly"
        }
        val crudo = web.getHtml(ruta) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            parsearLista(crudo)
        }.also {
            MangaFoxLog.d(
                ETIQUETA, "seccion " + seccion.name + " pag=" + pagina +
                    ": " + it.size + " obras" +
                    (if (it.isEmpty()) " (FIN)" else "")
            )
        }
    }

    /** items[{id,title,cover,permalink,...}] 24/pag. has_next
     *  false => FIN (el motor lo confirma con pagina vacia). */
    private fun parsearLista(crudo: String): List<ObraHome> {
        val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            ?: return emptyList()
        val items = raiz.optJSONArray("items") ?: return emptyList()
        val res = mutableListOf<ObraHome>()
        for (i in 0 until items.length()) {
            val e = items.optJSONObject(i) ?: continue
            val mid = e.optLong("id", -1).takeIf { it > 0 } ?: continue
            val titulo = e.optString("title").trim()
            if (titulo.isEmpty()) continue
            val slug = slugDePermalink(e.optString("permalink"))
            if (slug.isEmpty()) continue
            val enlace = "/series/" + slug
            if (res.none { it.id == enlace }) {
                res.add(
                    ObraHome(
                        enlace, titulo, e.optString("cover").trim(),
                        enlace = enlace
                    )
                )
            }
        }
        return res
    }

    private fun slugDePermalink(permalink: String): String {
        val partes = permalink.trim('/').split('/')
        val i = partes.indexOf("series")
        return if (i >= 0 && i + 1 < partes.size) partes[i + 1] else ""
    }

    // ------------------------------------------------------------ generos

    /** Lista congelada de evidencia keiyoushi (la API no expone
     *  endpoint de generos, /api/mv/genres 404 verificado). */
    override suspend fun generos(): List<Genero> = GENEROS_MANGACRAB

    // ------------------------------------------------------------ directorio

    override fun tamanoPaginaPedido(generoSlug: String?, busqueda: Boolean) =
        POR_PAGINA

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        val obras = pedir(pagina, generoSlug, null)
        MangaFoxLog.d(
            ETIQUETA, "directorio pag=" + pagina +
                (if (!generoSlug.isNullOrEmpty()) " genero=" + generoSlug else "") +
                ": " + obras.size + " obras"
        )
        return obras
    }

    // ------------------------------------------------------------ busqueda

    /** ?search= es busqueda server-side del propio sitio. */
    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        val obras = pedir(pagina, null, q)
        MangaFoxLog.d(
            ETIQUETA, "busqueda '" + q + "' pag=" + pagina + ": " +
                obras.size + " obras"
        )
        return obras
    }

    private suspend fun pedir(
        pagina: Int,
        generoSlug: String?,
        busqueda: String?
    ): List<ObraHome> {
        var ruta = "/api/mv/mangas?page=" + pagina +
            "&per_page=" + POR_PAGINA + "&feed=discover"
        if (!generoSlug.isNullOrEmpty()) {
            ruta += "&genres=" + URLEncoder.encode(generoSlug, "UTF-8")
        }
        if (!busqueda.isNullOrEmpty()) {
            ruta += "&search=" + URLEncoder.encode(busqueda, "UTF-8")
        }
        val crudo = web.getHtml(ruta) ?: return emptyList()
        return withContext(Dispatchers.IO) { parsearLista(crudo) }
    }

    // -------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val slug = obra.enlace.removePrefix("/series/")
        if (slug.isEmpty()) return null
        val t0 = System.currentTimeMillis()
        val crudo = web.getHtml(
            "/api/mv/mangas/by-slug/" + URLEncoder.encode(slug, "UTF-8")
        )
        if (crudo == null) {
            MangaFoxLog.w(ETIQUETA, "ficha sin JSON: " + obra.enlace)
            return null
        }
        val d = runCatching { JSONObject(crudo) }.getOrNull()
        if (d == null || d.optLong("id", -1) <= 0) {
            MangaFoxLog.w(ETIQUETA, "ficha sin data: " + slug)
            return null
        }
        val generos = mutableListOf<String>()
        d.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val n = g.optString("name").trim()
                if (n.isNotEmpty()) generos.add(n)
            }
        }
        val (capitulos, incompletos) = cargarCapitulos(d.optLong("id"))
        val detalle = ObraDetalle(
            id = d.optLong("id").toString(),
            titulo = d.optString("title").ifBlank { obra.titulo },
            portadaUrl = d.optString("cover").ifBlank { obra.portadaUrl },
            sinopsis = d.optString("description").trim(),
            estado = d.optJSONObject("status")?.optString("label")?.trim()
                ?.ifBlank { null },
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

    /** Capitulos por la API: v1 trae la PRIMERA pagina (50 caps)
     *  y declara capitulosIncompletos si el total es mayor. */
    private suspend fun cargarCapitulos(
        mangaId: Long
    ): Pair<List<Capitulo>, Boolean> {
        val crudo = web.getHtml(
            "/api/mv/mangas/" + mangaId + "/chapters?per_page=" +
                CAPITULOS_POR_LOTE
        ) ?: return emptyList<Capitulo>() to true
        val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            ?: return emptyList<Capitulo>() to true
        val items = raiz.optJSONArray("items")
            ?: return emptyList<Capitulo>() to true
        val total = raiz.optJSONObject("pagination")
            ?.optInt("total_items", 0) ?: 0
        val lista = mutableListOf<Capitulo>()
        for (i in 0 until items.length()) {
            val c = items.optJSONObject(i) ?: continue
            val titulo = c.optString("title").trim()
            val cslug = c.optString("chapter_slug").trim()
            if (titulo.isEmpty() || cslug.isEmpty()) continue
            lista.add(
                Capitulo(
                    numero = titulo.removePrefix("Capitulo ").trim()
                        .ifBlank { i.toString() },
                    titulo = titulo,
                    fecha = fecha(c.optString("date")),
                    enlace = "/series/#" + cslug
                )
            )
        }
        return lista to (total > lista.size)
    }

    private fun fecha(crudo: String): String? {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(crudo) ?: return null
        return m.groupValues[3] + "/" + m.groupValues[2] + "/" +
            m.groupValues[1]
    }

    // -------------------------------------------------------------- lector

    /** /api/mv/mangas/<id>/chapter?chapter_slug=<slug>: pages con
     *  URLS firmadas listas para usar. VIP/bloqueado = la
     *  verdad del sitio: CapituloBloqueadoException. */
    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        val mangaId = detalle.id.toLongOrNull()
        val cslug = capitulo.enlace.substringAfter("#", "")
        if (mangaId == null || mangaId <= 0 || cslug.isEmpty()) {
            MangaFoxLog.w(ETIQUETA, "lector sin id/slug: " + capitulo.titulo)
            return PaginasCapitulo(emptyList())
        }
        val t0 = System.currentTimeMillis()
        val crudo = web.getHtml(
            "/api/mv/mangas/" + mangaId + "/chapter?chapter_slug=" +
                URLEncoder.encode(cslug, "UTF-8")
        )
        if (crudo == null) {
            MangaFoxLog.w(ETIQUETA, "lector sin JSON: " + cslug)
            return PaginasCapitulo(emptyList())
        }
        val d = runCatching { JSONObject(crudo) }.getOrNull()
        if (d == null) {
            return PaginasCapitulo(emptyList())
        }
        if (d.optBoolean("is_locked", false) ||
            d.optBoolean("is_vip_chapter", false)
        ) {
            MangaFoxLog.w(ETIQUETA, "capitulo VIP/bloqueado: " + cslug)
            throw CapituloBloqueadoException(
                "Este capítulo es VIP o está bloqueado en MangaCrab"
            )
        }
        val paginas = d.optJSONObject("content")?.optJSONArray("pages")
        val urls = mutableListOf<String>()
        if (paginas != null) {
            for (i in 0 until paginas.length()) {
                val u = paginas.optString(i).trim()
                if (u.startsWith("http") && !u.contains("imagen-final")) {
                    urls.add(u)
                }
            }
        }
        MangaFoxLog.d(
            ETIQUETA, "capitulo '" + capitulo.titulo + "': " + urls.size +
                " paginas (" + (System.currentTimeMillis() - t0) + " ms)"
        )
        return PaginasCapitulo(
            urls,
            referer = BASE.trimEnd('/') + "/"
        )
    }

    companion object {
        const val ETIQUETA = "Mfx.Crab"
        const val BASE = "https://es.mangacrab.org"
        /** mangacrab.org es el hermano sin subdominio (las
         *  portadas viven ahi): puerta de recovery. */
        const val RECOVERY = "https://mangacrab.org"
        const val RITMO_MS = 2000L
        const val POR_PAGINA = 24
        const val CAPITULOS_POR_LOTE = 5000
    }
}
