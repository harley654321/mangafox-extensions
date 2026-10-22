package com.harley.mangafox.mfx.olympusscanlation

import com.harley.mangafox.mfx.api.BasePluginMfx
import com.harley.mangafox.mfx.api.ContextoMfx

/**
 * Punto de entrada del .mfx de Olympus Scanlation (lo nombra el
 * manifest.json en pluginClassName). Unica responsabilidad:
 * despertar y registrar la fuente ante el kernel; todo el
 * parseo vive en FuenteOlympusScanlation.
 */
class PluginOlympusScanlation : BasePluginMfx() {
    override fun activar(contexto: ContextoMfx) {
        contexto.log("activando fuente olympusscanlation")
        registrarFuente(FuenteOlympusScanlation(contexto), contexto)
    }
}
