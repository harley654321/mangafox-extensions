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

    /** Slug CANONICO de obra: sin prefijos 'manga/' NI
     *  'manhwa/', sin barras. EVIDENCIA (23-oct 06:16, log
     *  dueno 22c9e72fd + navegador REAL en el API): el API
     *  espera /manhwa/see/<slug pelado>; las listas traen DOS
     *  convenciones — recientes 'id_rel' = 'manga/<slug>' y
     *  top 'link' = '/manhwa/<slug>' — y llamar con cualquiera
     *  de los dos prefijos da HTTP 404 ('ficha sin JSON').
     *  Verificado en vivo: /manhwa/see/glory_hole_1679782381480
     *  responde la ficha completa. */
    private fun slugLimpio(crudo: String): String {
        var s = crudo.trim().trim('/')
        if (s.startsWith("manga/")) s = s.removePrefix("manga/")
        if (s.startsWith("manhwa/")) s = s.removePrefix("manhwa/")
        return s.trim('/')
    }

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

    /** /latest/new-manhwa (ronda v5, investigacion del dueno
     *  23-oct 07:47 verificada en vivo contra el API): el
     *  payload trae TRES secciones que el sitio muestra juntas
     *  en Ult. Capitulos: manhwas_esp (principal), _manhwas
     *  (+18) y manhwas_raw (raw). La v4 solo unia 2 y ponia la
     *  +18 PRIMERO sin orden temporal => el panel abria mostrando
     *  puro +18 y la seccion principal quedaba al fondo.
     *  RECIENTES correcto = las 3 unidas, orden por 'create'
     *  (epoch ms) DESCENDENTE y dedupe por id_manhwa DESPUES de
     *  ordenar: la entrada mas nueva gana siempre ('Negocios
     *  Peligrosos' llega con cap 69 de hoy y cap 41 viejo en
     *  raw; la extension oficial de Keiyoushi se queda con la
     *  vieja). 103 unicas verificadas en vivo (34+35+35).
     *  'chapter' es DECIMAL en el API (33.2, 1.1, 73.5): jamas
     *  truncar. Ventana rodante sin paginacion (page=2 se
     *  ignora): pagina 2+ = vacio honesto (FIN tras agotar
     *  la base completa, fix kernel bfdc405). */
    private suspend fun cargarRecientesApi(): List<ObraHome> {
        val crudo = web.getHtml("/latest/new-manhwa") ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val manhwas = runCatching { JSONObject(crudo) }
                .getOrNull()?.optJSONObject("manhwas") ?: return@withContext emptyList()
            val entradas = mutableListOf<Pair<Long, JSONObject>>()
            for (clave in listOf("manhwas_esp", "_manhwas", "manhwas_raw")) {
                val arr = manhwas.optJSONArray(clave) ?: continue
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    entradas.add(Pair(e.optLong("create", 0L), e))
                }
            }
            entradas.sortWith(compareByDescending { it.first })
            val vistas = mutableSetOf<String>()
            val res = mutableListOf<ObraHome>()
            for ((_, e) in entradas) {
                val idManhwa = e.optString("id_manhwa").trim()
                val titulo = e.optString("name_manhwa").trim()
                if (idManhwa.isEmpty() || titulo.isEmpty()) continue
                if (!vistas.add(idManhwa)) continue
                // Detalle via id_rel: es la llave REAL del endpoint
                // see (verificado en vivo 23-oct: 'nuevacara...
                // adsa' responde 200 y el id 'limpio' da 404).
                // id_manhwa queda como llave de dedupe y respaldo.
                val slug = slugLimpio(e.optString("id_rel"))
                if (slug.isEmpty()) continue
                val cap = e.optDouble("chapter", -1.0)
                val etiqueta = if (cap >= 0) "Cap. " + fmtCap(cap) else null
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
                val slug = slugLimpio(e.optString("link"))
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
                val slug = slugLimpio(e.optString("real_id"))
                // Dto oficial: el library devuelve 'the_real_name'
                // (name_esp es de la FICHA, no del library).
                val titulo = e.optString("the_real_name")
                    .ifBlank { e.optString("name_esp") }.trim()
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
        var slug = obra.enlace.removePrefix("/manhwa/see/")
        if (slug.isEmpty()) return null
        val t0 = System.currentTimeMillis()
        // FIX DEFINITIVO (23-oct 05:51, evidencia triple: browser
        // real en el sitio + Dto oficial Keiyoushi ManhwaWeb +
        // log dueno): el API api.manhwaweb.com SI recibe
        // /manhwa/see/<slug> pero el slug de las listas trae el
        // prefijo 'manga/' => 404 SIEMPRE. El %2F anterior era
        // sintoma; la causa era el prefijo. slugLimpio lo pela.
        slug = slugLimpio(slug)
        var crudo = web.getHtml("/manhwa/see/" + slug)
        if (crudo == null) {
            // RESCATE (ronda v5, hallazgo del dueno 23-oct): ~1%
            // de las entradas de /latest trae id_rel roto de
            // fabrica (truncado o corrupto) => 404. La biblioteca
            // SI tiene la llave buena (real_id): busqueda por
            // titulo EXACTO y un unico reintento. Verificado en
            // vivo: 'Manual de instrucciones del Reencarnado'
            // tenia id_rel E id_manhwa rotos y real_id abrio con
            // 220 caps.
            val realId = rescatarRealId(obra.titulo)
            if (realId != null) {
                MangaFoxLog.d(ETIQUETA, "rescate ficha 404: '" +
                    obra.titulo + "' -> real_id " + realId)
                crudo = web.getHtml("/manhwa/see/" + realId)
            }
        }
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
        // Dto oficial: _categoris = [{"<id>": "<nombre>"}, ...]
        // (el NOMBRE viaja dentro; no hay que traducir ids).
        d.optJSONArray("_categoris")?.let { arr ->
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val claves = g.keys()
                while (claves.hasNext()) {
                    val nombre = g.optString(claves.next()).trim()
                    if (nombre.isNotEmpty()) generos.add(nombre)
                }
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
        // CANON (log e8864799c): el API see/ entrega chapters[]
        // ASCENDENTE (cap 1 arriba); las demas fuentes ya
        // pintan descendente. sortedByDescending estable:
        // decimales ok, no-parseable al fondo, versions juntas.
        return lista.sortedByDescending {
            it.numero.toFloatOrNull() ?: -1f
        }
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

    /** Rescate de llave para el detalle: busca el titulo en
     *  /manhwa/library y devuelve el real_id SOLO si el nombre
     *  coincide exacto (normalizado) => jamas abre la ficha de
     *  otra obra. */
    private suspend fun rescatarRealId(titulo: String): String? {
        if (titulo.isBlank()) return null
        val ruta = "/manhwa/library?buscar=" +
            URLEncoder.encode(titulo, "UTF-8") + "&page=0"
        val crudo = web.getHtml(ruta) ?: return null
        return withContext(Dispatchers.IO) {
            val raiz = runCatching { JSONObject(crudo) }.getOrNull()
                ?: return@withContext null
            val items = raiz.optJSONArray("data") ?: return@withContext null
            val busco = titulo.trim().lowercase()
            for (i in 0 until items.length()) {
                val e = items.optJSONObject(i) ?: continue
                val nombre = e.optString("the_real_name")
                    .ifBlank { e.optString("name_esp") }.trim()
                if (nombre.lowercase() == busco) {
                    return@withContext e.optString("real_id").trim()
                        .ifBlank { null }
                }
            }
            null
        }
    }

    private fun fmtNum(n: Int): String = n.toString()

    /** Capitulo DECIMAL del API (33.2, 1.1, 73.5): sin ceros de
     *  cola y sin truncar los decimales reales. */
    private fun fmtCap(n: Double): String =
        if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()

    companion object {
        const val ETIQUETA = "Mfx.MhwWeb"
        const val BASE = "https://api.manhwaweb.com"
        const val RECOVERY = "https://manhwaweb.com"
        const val RITMO_MS = 2000L
        const val POR_PAGINA = 30
    }
}
