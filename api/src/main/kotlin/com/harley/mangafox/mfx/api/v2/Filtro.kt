package com.harley.mangafox.mfx.api.v2

/**
 * FILTROS TIPEADOS DE BUSQUEDA — API MFX v2 (Modulo D).
 *
 * Autopsia Mihon: su modelo de filtros (FilterList con Select,
 * Text, CheckBox, TriState, Sort, Group) es LA superficie que
 * hace sus 1000+ fuentes configurables sin tocar la UI. Aqui
 * el mismo concepto, reducido a lo que el kernel necesita:
 *
 *  - La EXTENSION declara sus filtros (filtros()) y la UI de
 *    la apk los pinta generica: la apk no sabe de sitios.
 *  - El ESTADO viaja dentro del filtro (data class inmutable):
 *    la UI devuelve la misma lista con estados nuevos.
 *  - La extension interpreta los estados en buscar(filtros).
 *
 * Congelado con el lanzamiento de la apk: romper una firma
 * exige apiVersion 3 y un paquete v3 (regla de oro, FuenteMfx).
 */
sealed class Filtro {

    /** Nombre visible del filtro (lo pinta la UI). */
    abstract val nombre: String

    /** Caja de texto libre (titulo a buscar, autor, etc.). */
    data class Texto(
        override val nombre: String,
        val valor: String = ""
    ) : Filtro()

    /** Lista cerrada de opciones (dropdown). */
    data class Seleccion(
        override val nombre: String,
        val opciones: List<String>,
        val indice: Int = 0
    ) : Filtro()

    /** Casilla booleana (CheckBox). */
    data class Casilla(
        override val nombre: String,
        val activada: Boolean = false
    ) : Filtro()

    /** Tres estados (incluir/excluir/ignorar) — TriState Mihon. */
    data class TriEstado(
        override val nombre: String,
        val estado: Estado = Estado.EXCLUIDO
    ) : Filtro() {
        enum class Estado { INCLUIDO, EXCLUIDO, IGNORADO }
    }

    /** Criterio de orden (Sort): opciones + indice activo. */
    data class Orden(
        override val nombre: String,
        val opciones: List<String>,
        val indice: Int = 0
    ) : Filtro()
}
