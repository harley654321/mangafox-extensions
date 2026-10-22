package com.harley.mangafox.mfx.olympusscanlation

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * EXTENSION MFX olympusscanlation — Olympus Scanlation.
 *
 * API JSON propia, autopsiada en vivo 21-oct-2026 (evidencia
 * keiyoushi + navegacion real del dueno pide verificar): el
 * dominio SALTA constantemente (olympusscanlation.com muerto ->
 * olympusbiblioteca.com -> 301 -> olympusxyz.com vigente). El
 * landing olympus.pages.dev anuncia el dominio vigente en
 * og:url: es la recoveryUrl. Toda la data es JSON:
 *
 *   RECIENTES : /api/new-chapters?page=N         (paginado 15)
 *   POPULARES : /api/rankings?page=N&period=monthly_ranking (15)
 *   RANKING   : /api/rankings?page=N&period=total_ranking (15)
 *               (validacion Zod viva: SOLO esos 2 periodos)
 *   DIRECTORIO: /api/series?page=N[&genres=<id>] (15/pag, 885)
 *   GENEROS   : /api/genres-statuses              (id+nombre)
 *   BUSQUEDA  : /api/series/list (885) filtrada local, 20/pag
 *   FICHA     : /api/series/<slug>?type=comic
 *   CAPITULOS : /api/series/<slug>/chapters?page=1 (40/pag)
 *   LECTOR    : /series/comic-<slug>/capitulo/<num> SSR con
 *               main.strip div.panel > img; OJO: con UA de
 *               navegador entrega las URLs reales de
 *               media.imagesolymp.xyz (verificado), con UA raro
 *               entrega placeholders /cp/ (verificado): el
 *               ClienteWeb de la apk ya manda UA Chrome.
 *
 * TRES listas distintas (monthly vs total vs new-chapters):
 * cero paginas dobles. FIN honesto por paginador JSON
 * (current_page/last_page): pagina > last_page = vacio.
 */
class FuenteOlympusScanlation(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "olympusscanlation"

    private val web: ClienteWeb =
        contexto.clienteWeb(BASE, RITMO_MS, RECOVERY)

    /** Cache local de /api/series/list para la busqueda (una
     *  peticion por sesion de busqueda, igual que keiyoushi). */
    private var catalogoCache: MutableList<Pair<String, String>>? = null

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
            Seccion.RECIENTES -> "/api/new-chapters?page=" + pagina
            Seccion.POPULARES ->
                "/api/rankings?page=" + pagina + "&period=monthly_ranking"
            Seccion.RANKING ->
                "/api/rankings?page=" + pagina + "&period=total_ranking"
        }
        val crudo = web.getHtml(ruta) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            parsearPagina(crudo)
        }.also {
            MangaFoxLog.d(
                ETIQUETA, "seccion " + seccion.name + " pag=" + pagina +
                    ": " + it.size + " obras" +
                    (if (it.isEmpty()) " (FIN)" else "")
            )
        }
    }

    /** Item de ranking/novedades: id, name, slug, cover, type.
     *  Solo comics; el paginador dice si hay FIN. */
    private fun parsearPagina(crudo: String): List<ObraHome> {
        val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            ?: return emptyList()
        val datos = raiz.optJSONArray("data") ?: return emptyList()
        val res = mutableListOf<ObraHome>()
        for (i in 0 until datos.length()) {
            val e = datos.optJSONObject(i) ?: continue
            if (e.optString("type", "comic") != "comic") continue
            val id = e.optInt("id", -1).takeIf { it > 0 } ?: continue
            val titulo = e.optString("name").trim()
            if (titulo.isEmpty()) continue
            val slug = e.optString("slug").trim()
            if (slug.isEmpty()) continue
            val portada = e.optString("cover").trim()
            val enlace = "/series/comic-" + slug
            if (res.none { it.id == enlace }) {
                res.add(ObraHome(enlace, titulo, portada, enlace = enlace))
            }
        }
        return res
    }

    // ------------------------------------------------------------ generos

    private var generosCache: List<Genero>? = null

    /** /api/genres-statuses: generos con id entero (el filtro
     *  del directorio usa ?genres=<id>). Verificado en vivo. */
    override suspend fun generos(): List<Genero> {
        generosCache?.let { return it }
        val crudo = web.getHtml("/api/genres-statuses") ?: return emptyList()
        val res = withContext(Dispatchers.IO) {
            val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            val lista = mutableListOf<Genero>()
            val gens = raiz?.optJSONArray("genres")
            if (gens != null) {
                for (i in 0 until gens.length()) {
                    val g = gens.optJSONObject(i) ?: continue
                    val gid = g.optInt("id", -1).takeIf { it > 0 } ?: continue
                    val nombre = g.optString("name").trim()
                    if (nombre.isNotEmpty()) {
                        lista.add(Genero(gid.toString(), nombre))
                    }
                }
            }
            lista
        }
        generosCache = res
        MangaFoxLog.d(ETIQUETA, "generos: " + res.size)
        return res
    }

    // ------------------------------------------------------------ directorio

    /** 15 obras/pag en el directorio JSON (885 comics, 59 pags
     *  el 21-oct). El genero filtra por id (?genres=N,
     *  verificado: accion -> 749). */
    override fun tamanoPaginaPedido(generoSlug: String?, busqueda: Boolean) =
        if (busqueda) 20 else 15

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        var ruta = "/api/series?page=" + pagina
        if (!generoSlug.isNullOrEmpty()) {
            ruta += "&genres=" + URLEncoder.encode(generoSlug, "UTF-8")
        }
        val crudo = web.getHtml(ruta) ?: return emptyList()
        val res = withContext(Dispatchers.IO) {
            val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            val datos = raiz?.optJSONObject("data")?.optJSONObject("series")
                ?.optJSONArray("data")
            val lista = mutableListOf<ObraHome>()
            if (datos != null) {
                for (i in 0 until datos.length()) {
                    val e = datos.optJSONObject(i) ?: continue
                    if (e.optString("type", "comic") != "comic") continue
                    val slug = e.optString("slug").trim()
                    val titulo = e.optString("name").trim()
                    if (slug.isEmpty() || titulo.isEmpty()) continue
                    val enlace = "/series/comic-" + slug
                    if (lista.none { it.id == enlace }) {
                        lista.add(
                            ObraHome(
                                enlace, titulo,
                                e.optString("cover").trim(),
                                enlace = enlace
                            )
                        )
                    }
                }
            }
            lista
        }
        MangaFoxLog.d(
            ETIQUETA, "directorio pag=" + pagina +
                (if (!generoSlug.isNullOrEmpty()) " genero=" + generoSlug else "") +
                ": " + res.size + " obras"
        )
        return res
    }

    // ------------------------------------------------------------ busqueda

    /** /api/series/list trae TODO el catalogo (885 comics) en un
     *  JSON unico: filtro local por nombre, 20/pag. Pagina 1
     *  completa la busqueda; el resto son vacias (FIN). */
    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        val catalogo = catalogo() ?: return emptyList()
        val aciertos = catalogo.filter {
            it.second.contains(q, ignoreCase = true)
        }
        val res = aciertos.drop((pagina - 1) * POR_PAGINA_BUSQUEDA)
            .take(POR_PAGINA_BUSQUEDA)
            .map { ObraHome(it.first, it.second, "", enlace = it.first) }
        MangaFoxLog.d(
            ETIQUETA, "busqueda '" + q + "' pag=" + pagina +
                ": " + res.size + " de " + aciertos.size
        )
        return res
    }

    private suspend fun catalogo(): MutableList<Pair<String, String>>? {
        catalogoCache?.let { return it }
        val crudo = web.getHtml("/api/series/list") ?: return null
        val res = withContext(Dispatchers.IO) {
            val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            val datos = raiz?.optJSONArray("data")
            val lista = mutableListOf<Pair<String, String>>()
            if (datos != null) {
                for (i in 0 until datos.length()) {
                    val e = datos.optJSONObject(i) ?: continue
                    if (e.optString("type", "comic") != "comic") continue
                    val slug = e.optString("slug").trim()
                    val nombre = e.optString("name").trim()
                    if (slug.isNotEmpty() && nombre.isNotEmpty()) {
                        lista.add("/series/comic-" + slug to nombre)
                    }
                }
            }
            lista
        }
        MangaFoxLog.d(ETIQUETA, "catalogo para busqueda: " + res.size)
        catalogoCache = res
        return res
    }

    // -------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val slug = slugDe(obra.enlace) ?: return null
        val t0 = System.currentTimeMillis()
        val crudo = web.getHtml(
            "/api/series/" + URLEncoder.encode(slug, "UTF-8") + "?type=comic"
        )
        if (crudo == null) {
            MangaFoxLog.w(ETIQUETA, "ficha sin JSON: " + obra.enlace)
            return null
        }
        val d = runCatching { JSONObject(crudo).optJSONObject("data") }
            .getOrNull()
        if (d == null) {
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
        val nota = d.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat()
        val (capitulos, incompletos) = cargarCapitulos(slug)
        val detalle = ObraDetalle(
            id = d.optInt("id", -1).toString(),
            titulo = d.optString("name").ifBlank { obra.titulo },
            portadaUrl = d.optString("cover").ifBlank { obra.portadaUrl },
            sinopsis = d.optString("summary").trim(),
            estado = d.optJSONObject("status")?.optString("name")?.trim()
                ?.ifBlank { null },
            nota = nota,
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

    /** Capitulos por /api/series/<slug>/chapters (40/pag, desc):
     *  v1 carga la PRIMERA pagina y lo declara honesto
     *  (capitulosIncompletos=true si meta.total > 40). */
    private suspend fun cargarCapitulos(
        slug: String
    ): Pair<List<Capitulo>, Boolean> {
        val crudo = web.getHtml(
            "/api/series/" + URLEncoder.encode(slug, "UTF-8") +
                "/chapters?page=1&direction=desc&type=comic"
        ) ?: return emptyList<Capitulo>() to true
        val raiz = runCatching { JSONObject(crudo) }.getOrNull()
            ?: return emptyList<Capitulo>() to true
        val datos = raiz.optJSONArray("data")
            ?: return emptyList<Capitulo>() to true
        val total = raiz.optJSONObject("meta")?.optInt("total", 0) ?: 0
        val lista = mutableListOf<Capitulo>()
        for (i in 0 until datos.length()) {
            val c = datos.optJSONObject(i) ?: continue
            val numero = c.optString("name").trim()
            if (numero.isEmpty()) continue
            lista.add(
                Capitulo(
                    numero = numero,
                    titulo = "Capítulo " + numero,
                    fecha = fechaIso(c.optString("published_at")),
                    enlace = "/series/comic-" + slug + "/capitulo/" +
                        URLEncoder.encode(numero, "UTF-8")
                )
            )
        }
        return lista to (total > lista.size)
    }

    private fun slugDe(enlace: String): String? {
        val m = Regex("comic-([a-z0-9-]+)").find(enlace) ?: return null
        return m.groupValues[1]
    }

    private fun fechaIso(iso: String): String? {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return null
        return m.groupValues[3] + "/" + m.groupValues[2] + "/" +
            m.groupValues[1]
    }

    // -------------------------------------------------------------- lector

    /** Lector SSR: main.strip > div.panel > img. Con UA de
     *  navegador (el de la apk) el HTML trae las URLs reales de
     *  media.imagesolymp.xyz; los placeholders /cp/ se
     *  descartan por si las cosas cambian. */
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
        Regex("<div class=\"panel\"[^>]*>\\s*<img[^>]*>")
            .findAll(html).forEach { m ->
                val src = Regex("src=\"([^\"]+)\"").find(m.value)
                    ?.groupValues?.get(1)?.trim() ?: return@forEach
                if (src.startsWith("http") && !src.contains("/cp/")) {
                    if (!urls.contains(src)) urls.add(src)
                }
            }
        return urls
    }

    companion object {
        const val ETIQUETA = "Mfx.Olympus"
        /** Dominio vigente 21-oct (olympus.pages.dev anuncia
         *  el cambio en og:url: es la puerta de recovery). */
        const val BASE = "https://olympusxyz.com"
        const val RECOVERY = "https://olympus.pages.dev"
        const val RITMO_MS = 1000L
        const val POR_PAGINA_BUSQUEDA = 20
    }
}
