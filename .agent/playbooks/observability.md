# Diagnóstico de desarrollo y cobertura de observabilidad

## Obligación en cada cambio

Antes de implementar una función o corrección, revisa qué evidencia permitiría reconstruir su ejecución real. Comprueba los registros existentes en las responsabilidades y fronteras afectadas.

Completa la cobertura que falte como parte del cambio. No esperes a que el usuario encuentre un fallo para instrumentar una operación importante. Si el diagnóstico actual deja un punto ciego relevante, corrígelo dentro del alcance; si requiere una decisión nueva, identifica la carencia y su consecuencia.

Esta obligación se aplica durante todo el proyecto, no solo cuando se solicita expresamente «añadir logs». No exige escribir un mensaje en cada archivo o función: exige que las operaciones relevantes sean reconstruibles sin huecos inexplicados.

## Qué debe poder reconstruirse

Deriva los eventos del flujo real, sin inventar componentes que el proyecto no tenga:

- Entrada de una acción o solicitud relevante y decisión de aceptarla o rechazarla.
- Inicio, avance significativo y terminación de la operación.
- Resultado: éxito, fallo, cancelación, descarte o tiempo de espera.
- Transiciones de estado que expliquen cambios de comportamiento.
- Cruces entre componentes y dependencias externas.
- Escrituras y cambios persistentes importantes y su resultado.
- Creación, uso y liberación de recursos cuyo ciclo afecte la corrección.
- Recuperación de errores y decisiones de reintento.
- Tiempos y métricas necesarios para explicar demoras o consumo anómalos.

Instrumenta tanto los recorridos normales como sus salidas alternativas relevantes. «El botón se pulsó» o «hubo un error» sin identificar la operación y el resultado deja el diagnóstico incompleto.

Para cada flujo modificado, comprueba si podrías explicar dónde empezó, qué decidió, dónde terminó y qué recursos o efectos produjo. Un inicio sin final debe poder distinguirse de una operación todavía activa.

## Contrato de los eventos

Reutiliza una fachada de diagnóstico del proyecto y una etiqueta o categoría estable. Si no existe y hace falta, crea el mínimo soporte compartido en lugar de dispersar formatos incompatibles.

Los eventos deben permitir identificar componente, acción, resultado y metadatos técnicos. Usa identificadores de sesión y operación cuando sean necesarios para correlacionarlos. El orden debe poder reconstruirse incluso con concurrencia.

Un formato posible es «componente | acción | resultado | metadatos». Conserva el contrato existente si ya cumple el objetivo; no impongas un formato nuevo por preferencia.

Incluye duración, hilo o ejecutor, cantidades, códigos y clasificación del fallo cuando aporten información. Para un error imprevisto, conserva la causa y la traza técnica depurada que permita localizarlo.

No registres como éxito el simple inicio de una solicitud. Diferencia intención, ejecución, resultado confirmado y presentación del resultado cuando sean fases distintas.

## Canal y funcionamiento

El diagnóstico detallado debe estar habilitado en desarrollo y debug para que el usuario pueda entregar registros de una sesión real.

Usa el canal propio del entorno: por ejemplo, Logcat en Android y logging estructurado o salida diagnóstica equivalente en otros proyectos. Instrumentar no significa crear una pantalla de logs en el producto ni añadir mensajes visibles al usuario.

Comprueba niveles, filtros y activación efectiva. Una llamada presente en el código pero filtrada, desactivada o enviada a un canal que el usuario no puede obtener no cubre el flujo.

Mantén el orden y las claves necesarias para unir eventos entre componentes. No dependas exclusivamente de la configuración del entorno de pruebas para obtener información que faltaría durante el uso normal en debug.

## Privacidad, coste y producción

Registra metadatos suficientes para diagnosticar sin incluir secretos, credenciales, contenido del usuario, rutas privadas ni payloads completos. Los identificadores técnicos no deben revelar esos datos.

No conviertas el diagnóstico en una captura indiscriminada de red, archivos o datos personales. Describe la operación, su resultado y cantidades o categorías seguras.

Evita eventos repetidos por cada actualización frecuente si no aportan información. Utiliza cambios de estado, agregación o muestreo para eventos de alta frecuencia, conservando contadores y errores relevantes.

El logger no debe bloquear rutas sensibles, adquirir recursos pesados por evento ni generar mensajes costosos cuando esté desactivado.

La instrumentación forense de desarrollo debe estar excluida o desactivada en producción conforme a su configuración de entrega. No debe construir payloads diagnósticos ni emitirlos por descuido. La observabilidad operativa de producción, si el producto la requiere, tiene una política explícita y mínima.

No cambies la semántica del programa para facilitar el logging. Un problema del logger no debe introducir efectos secundarios que alteren el resultado de la operación.

## Verificación y diagnóstico posterior

Cuando puedas ejecutar el flujo, captura sus registros reales. Verifica correlación, eventos finales, rutas alternativas pertinentes, información suficiente y ausencia de datos sensibles.

Si añades o cambias el mecanismo de activación, comprueba desarrollo y producción. Una condición en el código fuente no demuestra por sí sola que el artefacto respete la política.

Las pruebas pueden comprobar eventos y activación, sin acoplar toda la suite a textos exactos que no sean un contrato público.

Si no puedes ejecutar el flujo en el destino, verifica lo que sí permita el entorno y declara pendiente la comprobación real. No digas que los eventos ya aparecen en Logcat por haber escrito sus llamadas.

Al recibir registros, reconstruye el recorrido completo solicitado y busca también huecos, operaciones sin término y recursos no liberados. Si descubres que una anomalía queda fuera de la cobertura, mejora el diagnóstico para la siguiente ejecución.

El registro facilita detectar anomalías; no garantiza detectar todos los defectos ni sustituye pruebas y mediciones.
