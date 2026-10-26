package com.harley.mangafox.mfx.mantrazscan

import com.harley.mangafox.mfx.api.v2.BasePluginMfx2
import com.harley.mangafox.mfx.api.v2.ContextoMfx2
import kotlinx.coroutines.runBlocking

/**
 * Punto de entrada del .mfx de Mantraz Scan (lo nombra el
 * manifest.json en pluginClassName). Extension v2: filtros
 * tipados en el directorio. Canario JS al activar siguiendo
 * el patron vivo de ikigaimangas: si el puente al Motor
 * QuickJS esta roto se ve AQUI y no en una fuente que lo
 * necesite.
 */
class PluginMantrazScan : BasePluginMfx2() {
    override fun activar(contexto: ContextoMfx2) {
        contexto.log("activando fuente mantrazscan (v2)")
        runBlocking {
            val r = contexto.evaluarJs("mantraz-canario", "6 * 7")
            contexto.log(
                "canario JS (Motor C): " + (r ?: "NULL — puente roto")
            )
        }
        registrarFuente(FuenteMantrazScan(contexto), contexto)
    }
}
