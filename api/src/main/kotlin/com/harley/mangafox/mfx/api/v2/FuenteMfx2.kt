package com.harley.mangafox.mfx.api.v2

import android.content.Context
import com.harley.mangafox.logic.home.ObraHome
import com.harley.mangafox.mfx.api.ContextoMfx
import com.harley.mangafox.mfx.api.FuenteMfx

/**
 * CONTRATO MFX v2 — MODULO D DEL KERNEL (FuenteMfx2).
 *
 * Regla de oro (FuenteMfx v1): el paquete v1 esta CONGELADO;
 * todo lo nuevo vive aqui. La apk valida apiVersion del
 * manifest: 1 = contrato v1, 2 = esta superficie. Desajuste =
 * rechazo con log explicito, jamas NoSuchMethodError silencioso
 * (la leccion de Cloudstream documentada en DISENO-MFX.md).
 *
 * QUE APORTA v2 SOBRE v1:
 *  1. buscar(filtros, pagina): busqueda con FILTROS TIPEDOS
 *     (Filtro.kt) — el modelo Mihon que hace sus fuentes
 *     configurables sin tocar la UI de la apk.
 *  2. cabecerasImagen(): cabeceras HTTP que las IMAGENES de
 *     esta fuente exigen (leccion Ikigai/Keiyoushi 23-oct:
 *     el CDN de imagen huellea los Sec-Fetch/Referer y responde
 *     404 disfrazado; cada fuente declara las suyas).
 *  3. PuenteJs: la fuente ejecuta JavaScript en el Motor
 *     QuickJS del kernel (Modulo C) — tokens de sitios Qwik,
 *     parseo protegido, decodificadores (patron JavaScriptEngine
 *     de Mihon).
 */
const val API_MFX_VERSION_2: Int = 2

/**
 * Puente al Motor QuickJS del kernel (Modulo C). Lo implementa
 * el kernel sobre MotorJs (hilo unico del motor, timeouts y
 * leyes de registro incluidos); la fuente solo lo consume.
 * Devuelve String (primitivos JS) o null si fallo/undefined:
 * objetos con JSON.stringify dentro del propio script.
 */
interface PuenteJs {
    suspend fun evaluar(etiqueta: String, codigo: String): String?
}

/**
 * Regalo del kernel a las extensiones v2: TODO lo de v1
 * (clienteWeb, log, registro) mas el PuenteJs.
 */
class ContextoMfx2 internal constructor(
    contexto: Context,
    registrar: (FuenteMfx, String) -> Unit,
    /** Puente al Motor QuickJS (Modulo C). */
    val puenteJs: PuenteJs
) : ContextoMfx(contexto, registrar) {

    /** Ejecuta [codigo] en el Motor QuickJS del kernel. */
    suspend fun evaluarJs(
        etiqueta: String,
        codigo: String
    ): String? = puenteJs.evaluar(etiqueta, codigo)
}

/**
 * Fuente v2: es una FuenteMfx v1 COMPLETA (home, directorio,
 * ficha, lector y buscar v1 siguen funcionando) mas las tres
 * capacidades nuevas. El kernel la detecta por el apiVersion
 * del manifest y la cablea con ContextoMfx2.
 */
interface FuenteMfx2 : FuenteMfx {
    override val id: String

    /** Filtros que esta fuente ofrece a la busqueda. La UI
     *  generica de la apk los pinta; la fuente los interpreta
     *  en buscar(filtros, pagina). Default: sin filtros. */
    suspend fun filtros(): List<Filtro> = emptyList()

    /** Busqueda con filtros tipados y paginacion 1-based.
     *  Default: delega en la busqueda v1 con el primer Filtro
     *  de Texto si existe, si no lista vacia (estado honesto). */
    suspend fun buscar(
        filtros: List<Filtro>,
        pagina: Int
    ): List<ObraHome> {
        val texto = filtros.filterIsInstance<Filtro.Texto>()
            .firstOrNull()?.valor.orEmpty()
        return if (texto.isBlank()) emptyList() else buscar(texto, pagina)
    }

    /** Cabeceras HTTP que el CARGADOR DE IMAGENES del kernel
     *  agrega a toda descarga de este host (Sec-Fetch-*, Referer,
     *  cookies... leccion Ikigai: sin ellas el CDN responde 404
     *  disfrazado). Default: vacias. */
    fun cabecerasImagen(): Map<String, String> = emptyMap()

    /** Sufijo de host al que aplican cabecerasImagen()
     *  (ej: "ikigaimangas.cloud"). Vacia = no aplica. */
    fun hostImagenes(): String = ""
}

/**
 * Punto de entrada del .mfx v2 (el manifest lo nombra en
 * pluginClassName con apiVersion: 2). Igual que BasePluginMfx
 * pero activa con ContextoMfx2 (con PuenteJs).
 */
abstract class BasePluginMfx2 {
    /** Ruta absoluta del .mfx; la fija el cargador. */
    internal var archivoMfx: String = ""

    /** Coordinar registro de fuentes y recursos propios. */
    open fun activar(contexto: ContextoMfx2) {}

    /** Registra una fuente v2 ante el kernel. */
    protected fun registrarFuente(
        fuente: FuenteMfx2,
        contexto: ContextoMfx2
    ) {
        contexto.registrarFuente(fuente, archivoMfx)
    }
}
