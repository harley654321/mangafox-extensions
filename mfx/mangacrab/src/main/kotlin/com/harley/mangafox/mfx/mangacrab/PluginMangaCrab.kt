package com.harley.mangafox.mfx.mangacrab

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de MangaCrab (lo nombra el
 * manifest.json en pluginClassName). Unica responsabilidad:
 * despertar y registrar la fuente ante el kernel; todo el
 * parseo vive en FuenteMangaCrab.
 */
class PluginMangaCrab : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente mangacrab")
        registrarFuente(FuenteMangaCrab(contexto), contexto)
    }
}
