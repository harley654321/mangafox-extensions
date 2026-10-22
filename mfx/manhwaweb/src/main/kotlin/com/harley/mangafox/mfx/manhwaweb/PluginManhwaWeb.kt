package com.harley.mangafox.mfx.manhwaweb

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de ManhwaWeb (lo nombra el
 * manifest.json en pluginClassName). Unica responsabilidad:
 * despertar y registrar la fuente ante el kernel; todo el
 * parseo vive en FuenteManhwaWeb.
 */
class PluginManhwaWeb : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente manhwaweb")
        registrarFuente(FuenteManhwaWeb(contexto), contexto)
    }
}
