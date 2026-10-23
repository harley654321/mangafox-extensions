package com.harley.mangafox.mfx.ikigaimangas

import com.harley.mangafox.mfx.api.v2.BasePluginMfx2
import com.harley.mangafox.mfx.api.v2.ContextoMfx2
import kotlinx.coroutines.runBlocking

/**
 * Punto de entrada del .mfx de Ikigai Mangas (lo nombra el
 * manifest.json en pluginClassName). PRIMERA EXTENSION v2:
 * contexto v2 con puente al Motor QuickJS (Modulo C).
 */
class PluginIkigaiMangas : BasePluginMfx2() {
    override fun activar(contexto: ContextoMfx2) {
        contexto.log("activando fuente ikigaimangas (v2)")
        // CANARIO JS: primera prueba VIVA del puente extension ->
        // PuenteJsKernel -> MotorJs en produccion. Si el puente
        // esta roto se ve AQUI, no en una fuente que lo necesite.
        runBlocking {
            val r = contexto.evaluarJs("ikigai-canario", "6 * 7")
            contexto.log(
                "canario JS (Motor C): " + (r ?: "NULL — puente roto")
            )
        }
        registrarFuente(FuenteIkigaiMangas(contexto), contexto)
    }
}
