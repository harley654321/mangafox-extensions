package com.harley.mangafox.mfx.ikigaimangas

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de Ikigai Mangas (lo nombra el
 * manifest.json en pluginClassName). Unica responsabilidad:
 * despertar y registrar la fuente ante el kernel; todo el
 * parseo vive en FuenteIkigaiMangas.
 */
class PluginIkigaiMangas : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente ikigaimangas")
        registrarFuente(FuenteIkigaiMangas(contexto), contexto)
    }
}
