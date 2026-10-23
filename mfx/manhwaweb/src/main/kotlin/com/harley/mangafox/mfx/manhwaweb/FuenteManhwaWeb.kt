package com.harley.mangafox.mfx.manhwaweb

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
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * EXTENSION MFX manhwaweb — ManhwaWeb (manhwaweb.com).
 *
 * API JSON api.manhwaweb.com autopsiada en vivo 21-oct-2026
 * (evidencia keiyoushi + verificacion propia; el endpoint de
 * novedades de keiyoushi /manhwa/latest/new-manhwa esta MUERTO:
 * el bundle JS actual dice que se movio a /latest/new-manhwa):
 *
 *   RECIENTES : /latest/new-manhwa -> manhwas._manhwas +
 *               manhwas.manhwas_esp (grupos y capitulos con
 *               fecha: los "Nuevos Capitulos" de la home real)
 *   POPULARES : /manhwa/nuevos -> top.manhwas_esp ("Lo mas
 *               Leido" SEMANAL de la home real)
 *   RANKING   : /manhwa/nuevos -> top.manhwas_raw (pestaña
 *               TOTAL: lista DISTINTA de la semanal)
 *   DIRECTORIO: /manhwa/library?buscar=&page=<0!>&generes=3a2
 *               (30/pag, page BASE CERO: se traduce aqui)
 *   FICHA     : /manhwa/see/<real_id> (_sinopsis, _status,
 *               _categoris ids -> nombres, chapters con
 *               versions[] por grupo)
 *   LECTOR     : /chapters/see/<slug-del-capitulo> ->
 *               chapter.img[] (img1mw.xyz, URLs directas)
 *
 * FIN honesto por el campo "next" del JSON de library. Las
 * secciones de home son listas cortas del sitio (10 c/u):
 * pagina 2+ = vacio honesto, no pagina doble.
 */
class FuenteManhwaWeb(contexto: ContextoMfx) : FuenteMfx {

    override val id: String = "manhwaweb"

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
        if (pagina > 1) {
            // Listas cortas de la home real: una sola pagina.
            MangaFoxLog.d(
                ETIQUETA, "seccion " + seccion.name + " pag=" + pagina +
                    ": 0 obras (FIN: lista unica del sitio)"
            )
            return emptyList()
        }
        val res = when (seccion) {
            Seccion.RECIENTES -> cargarRecientesApi()
            else -> cargarMasLeidas(seccion)
        }
        MangaFoxLog.d(
            ETIQUETA, "seccion " + seccion.name + " pag=" + pagina + ": " +
                res.size + " obras"
        )
        return res
    }

    /** /latest/new-manhwa: capitulos recientes con grupo y
     *  numero (las tarjetas "Nuevos Capitulos" de la home). */
    private suspend fun cargarRecientesApi(): List<ObraHome> {
        val crudo = web.getHtml("/latest/new-manhwa") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val manhwas = runCatching { JSONObject(crudo) }
                .getOrNull()?.optJSONObject("manhwas") ?: return@withContext emptyList()
            val vistas = mutableSetOf<String>()
            val res = mutableListOf<ObraHome>()
            for (clave in listOf("_manhwas", "manhwas_esp")) {
                val arr = manhwas.optJSONArray(clave) ?: continue
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val slug = e.optString("id_rel").trim()
                    val titulo = e.optString("name_manhwa").trim()
                    if (slug.isEmpty() || titulo.isEmpty()) continue
                    if (!vistas.add(slug)) continue
                    val cap = e.optInt("chapter", -1)
                    val etiqueta = if (cap >= 0) "Cap. " + fmtNum(cap) else null
                    val enlace = "/manhwa/see/" + slug
                    if (res.none { it.id == enlace }) {
                        res.add(
                            ObraHome(
                                enlace, titulo,
                                e.optString("img").trim(),
                                etiqueta, enlace
                            )
                        )
                    }
                }
            }
            res
        }
    }

    /** /manhwa/nuevos: "Lo mas leido" de la home. POPULARES =
     *  pestaña Semanal (manhwas_esp), RANKING = pestaña Total
     *  (manhwas_raw): DOS listas distintas del MISMO endpoint,
     *  etiquetadas con su puesto (#1, #2...). */
    private suspend fun cargarMasLeidas(seccion: Seccion): List<ObraHome> {
        val crudo = web.getHtml("/manhwa/nuevos") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val top = runCatching { JSONObject(crudo) }
                .getOrNull()?.optJSONObject("top") ?: return@withContext emptyList()
            val clave = if (seccion == Seccion.POPULARES) {
                "manhwas_esp"
            } else {
                "manhwas_raw"
            }
            val arr = top.optJSONArray(clave) ?: return@withContext emptyList()
            val res = mutableListOf<ObraHome>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val slug = e.optString("link").trim()
                    .removePrefix("/manhwa/").trim('/')
                val titulo = e.optString("name").trim()
                if (slug.isEmpty() || titulo.isEmpty()) continue
                val enlace = "/manhwa/see/" + slug
                if (res.none { it.id == enlace }) {
                    res.add(
                        ObraHome(
                            enlace, titulo,
                            e.optString("imagen").trim(),
                            "#" + (i + 1), enlace
                        )
                    )
                }
            }
            res
        }
    }

    // ------------------------------------------------------------ generos

    /** Generos del filtro del directorio (ids enteros, se
     *  pasan como generes=3a29 unidos por 'a'). Evidencia:
     *  lista del mantenedor de keiyoushi; la API no expone
     *  catalogo de generos. */
    override suspend fun generos(): List<Genero> = GENEROS_MANHWAWEB

    // ------------------------------------------------------------ directorio

    override fun tamanoPaginaPedido(generoSlug: String?, busqueda: Boolean) =
        POR_PAGINA

    override suspend fun cargarDirectorio(
        pagina: Int,
        generoSlug: String?
    ): List<ObraHome> {
        val res = pedirLibrary(pagina, null, generoSlug)
        MangaFoxLog.d(
            ETIQUETA, "directorio pag=" + pagina +
                (if (!generoSlug.isNullOrEmpty()) " genero=" + generoSlug else "") +
                ": " + res.size + " obras"
        )
        return res
    }

    // ------------------------------------------------------------ busqueda

    override suspend fun buscar(texto: String, pagina: Int): List<ObraHome> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        val res = pedirLibrary(pagina, q, null)
        MangaFoxLog.d(
            ETIQUETA, "busqueda '" + q + "' pag=" + pagina + ": " +
                res.size + " obras"
        )
        return res
    }

    /** /manhwa/library: page BASE CERO (pagina 1 de la app =
     *  page=0 de la API). 30 items/pag, FIN por campo next. */
    private suspend fun pedirLibrary(
        pagina: Int,
        buscar: String?,
        generoSlug: String?
    ): List<ObraHome> {
        if (pagina < 1) return emptyList()
        var ruta = "/manhwa/library?buscar=" +
            URLEncoder.encode(buscar ?: "", "UTF-8") +
            "&page=" + (pagina - 1)
        if (!generoSlug.isNullOrEmpty()) {
            ruta += "&generes=" + URLEncoder.encode(generoSlug, "UTF-8")
        }
        val crudo = web.getHtml(ruta) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val raiz = runCatching { JSONObject(crudo) }.getOrNull()
                ?: return@withContext emptyList()
            if (!raiz.optBoolean("next", false) && pagina > 1) {
                return@withContext emptyList()
            }
            val items = raiz.optJSONArray("data")
                ?: return@withContext emptyList()
            val res = mutableListOf<ObraHome>()
            for (i in 0 until items.length()) {
                val e = items.optJSONObject(i) ?: continue
                val slug = e.optString("real_id").trim()
                val titulo = e.optString("name_esp").trim()
                if (slug.isEmpty() || titulo.isEmpty()) continue
                val enlace = "/manhwa/see/" + slug
                if (res.none { it.id == enlace }) {
                    res.add(
                        ObraHome(
                            enlace, titulo,
                            e.optString("_imagen").trim(),
                            enlace = enlace
                        )
                    )
                }
            }
            res
        }
    }

    // -------------------------------------------------------------- ficha

    override suspend fun cargarDetalle(obra: ObraHome): ObraDetalle? {
        val slug = obra.enlace.removePrefix("/manhwa/see/")
        if (slug.isEmpty()) return null
        val t0 = System.currentTimeMillis()
        // FIX (log dueno 23-oct 05:16): URLEncoder.encode sobre
        // TODO el slug convertia el slash legitimo ('manga/<id>')
        // en %2F => el sitio buscaba un segmento unico inexistente
        // y la ficha moria en 404 SIEMPRE ('ficha sin JSON').
        // El slug viene del PROPIO JSON del sitio: se usa tal cual.
        val crudo = web.getHtml("/manhwa/see/" + slug)
        if (crudo == null) {
            MangaFoxLog.w(ETIQUETA, "ficha sin JSON: " + obra.enlace)
            return null
        }
        val d = runCatching { JSONObject(crudo) }.getOrNull()
        if (d == null || d.optString("_id").isBlank()) {
            MangaFoxLog.w(ETIQUETA, "ficha sin data: " + slug)
            return null
        }
        val generos = mutableListOf<String>()
        d.optJSONArray("_categoris")?.let { arr ->
            for (i in 0 until arr.length()) {
                val gid = arr.optInt(i, -1)
                NOMBRES_GENEROS[gid]?.let { generos.add(it) }
            }
        }
        val capitulos = extraerCapitulos(d.optJSONArray("chapters"))
        val detalle = ObraDetalle(
            id = d.optString("_id").ifBlank { slug },
            titulo = d.optString("name_esp").ifBlank { obra.titulo },
            portadaUrl = d.optString("_imagen").ifBlank { obra.portadaUrl },
            sinopsis = d.optString("_sinopsis").trim(),
            estado = traducirEstado(d.optString("_status")),
            generos = generos,
            capitulos = capitulos
        )
        MangaFoxLog.d(
            ETIQUETA, "ficha '" + detalle.titulo + "': " + capitulos.size +
                " caps (" + (System.currentTimeMillis() - t0) + " ms)"
        )
        return detalle
    }

    /** chapters[] trae versions[] (una por grupo/scan): se
     *  aplana a capitulos con el grupo en el titulo, igual que
     *  hace keiyoushi. La lista de la API es COMPLETA (no
     *  pagina): capitulosIncompletos=false honesto. */
    private fun extraerCapitulos(
        chapters: JSONArray?
    ): List<Capitulo> {
        if (chapters == null) return emptyList()
        val lista = mutableListOf<Capitulo>()
        for (i in 0 until chapters.length()) {
            val ch = chapters.optJSONObject(i) ?: continue
            val numero = ch.opt("chapter")?.toString()?.trim() ?: continue
            val versions = ch.optJSONArray("versions") ?: continue
            for (v in 0 until versions.length()) {
                val ver = versions.optJSONObject(v) ?: continue
                val link = ver.optString("link").trim()
                if (link.isEmpty()) continue
                val cslug = link.substringAfterLast('/')
                val grupo = ver.optJSONArray("joint")?.let { j ->
                    (0 until j.length())
                        .mapNotNull { k ->
                            j.optJSONObject(k)?.optString("name")
                        }
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                }.orEmpty()
                val titulo = "Capítulo " + numero +
                    (if (grupo.isNotBlank()) " ($grupo)" else "")
                lista.add(
                    Capitulo(
                        numero = numero,
                        titulo = titulo,
                        fecha = fechaEpoch(ver.optLong("create", 0L)),
                        enlace = "/chapters/see/" + cslug
                    )
                )
            }
        }
        return lista
    }

    private fun fechaEpoch(ms: Long): String? {
        if (ms <= 0L) return null
        return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
    }

    private fun traducirEstado(crudo: String): String? {
        val t = crudo.trim().lowercase()
        return when {
            t.isEmpty() -> null
            t.contains("publicandose") -> "Publicándose"
            t.contains("finalizado") || t.contains("finalized") -> "Finalizado"
            t.contains("abandon") -> "Abandonado"
            t.contains("pausado") || t.contains("hiatus") -> "Pausado"
            else -> crudo.trim().replaceFirstChar { it.uppercase() }
        }
    }

    // -------------------------------------------------------------- lector

    /** /chapters/see/<slug>: chapter.img[] URLs directas. */
    override suspend fun cargarPaginasCapitulo(
        detalle: ObraDetalle,
        capitulo: Capitulo
    ): PaginasCapitulo? {
        if (capitulo.enlace.isEmpty()) return PaginasCapitulo(emptyList())
        val t0 = System.currentTimeMillis()
        val crudo = web.getHtml(capitulo.enlace)
        if (crudo == null) {
            MangaFoxLog.w(ETIQUETA, "lector sin JSON: " + capitulo.enlace)
            return PaginasCapitulo(emptyList())
        }
        val urls = withContext(Dispatchers.IO) {
            val d = runCatching { JSONObject(crudo) }.getOrNull()
            val imgs = d?.optJSONObject("chapter")?.optJSONArray("img")
            val res = mutableListOf<String>()
            if (imgs != null) {
                for (i in 0 until imgs.length()) {
                    val u = imgs.optString(i).trim()
                    if (u.startsWith("http")) res.add(u)
                }
            }
            res
        }
        MangaFoxLog.d(
            ETIQUETA, "capitulo '" + capitulo.titulo + "': " + urls.size +
                " paginas (" + (System.currentTimeMillis() - t0) + " ms)"
        )
        return PaginasCapitulo(
            urls,
            referer = "https://manhwaweb.com/"
        )
    }

    private fun fmtNum(n: Int): String = n.toString()

    companion object {
        const val ETIQUETA = "Mfx.MhwWeb"
        const val BASE = "https://api.manhwaweb.com"
        const val RECOVERY = "https://manhwaweb.com"
        const val RITMO_MS = 2000L
        const val POR_PAGINA = 30
    }
}
