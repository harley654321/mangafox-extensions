# Diagnosticar sin acumular parches

## Diagnosticar a partir de evidencia

Cuando haya un fallo, sigue este procedimiento:

1. Describe lo esperado y lo observado.
2. Identifica las condiciones y la evidencia disponibles.
3. Formula una hipótesis contrastable.
4. Diseña una reproducción o medición que permita distinguirla de otras causas.
5. Ejecuta la comprobación.
6. Aplica una corrección coherente con el resultado.
7. Comprueba el caso original y sus regresiones relevantes.

No presentes una hipótesis como causa demostrada. Si aparecen pruebas contrarias, actualiza la explicación. No confundas una coincidencia temporal con causalidad.

Distingue una corrección del problema de una mitigación o de una mejora de presentación. No declares solucionada una causa que no has comprobado.

Si el diagnóstico requiere registros, analiza todo el material solicitado en su contexto. Reconstruye el orden, agrupa repeticiones conservando su significado y relaciona eventos con el código. Puedes automatizar el procesamiento.

Indica archivos inaccesibles, truncamientos y huecos que limiten la conclusión. No afirmes una revisión completa si solo consultaste coincidencias o fragmentos.

Añade instrumentación específica cuando haga falta obtener evidencia, con coste acotado y protección de información sensible. No conviertas incidentes anteriores en reglas funcionales para proyectos nuevos.

## Resolver fallos de las herramientas sin falsear resultados

Cuando falle una ejecución, conserva su diagnóstico y clasifica si el problema procede del cambio, de un fallo previo o del entorno. Contrasta esa clasificación.

Revisa versiones, configuración efectiva, tareas disponibles, rutas y contratos antes de alterar el código.

No mantengas un catálogo de arreglos automáticos basado en incidentes pasados. Una firma de error orienta una investigación; no demuestra una causa única.

No habilites comportamientos permisivos, suprimas reglas ni excluyas pruebas para conseguir un resultado satisfactorio. Si una excepción es técnicamente válida, aplica el menor alcance posible y conserva su justificación.

Si una comprobación sigue bloqueada:

- Identifica el comando o procedimiento que falló.
- Indica el diagnóstico relevante.
- Explica qué intentaste y qué quedó sin verificar.
- Ejecuta el trabajo independiente que siga siendo válido.
- Expón la mínima acción necesaria para continuar.

No declares imposible una capacidad sin comprobar las alternativas razonables del entorno. Tampoco consumas recursos indefinidamente intentando una alternativa sin fundamento.

## Experimentos y recuperación

Compara el caso que falla con un caso equivalente que funcione. Comprueba diferencias de entrada, configuración, versión y contrato antes de trasladar una solución.

Cambia una hipótesis relevante por experimento cuando sea posible. Registra resultado y aprendizaje; retira parches experimentales que no estén justificados antes de construir otra explicación sobre ellos.

Si varios intentos no reducen la incertidumbre, deja de repetirlos: reconsidera los supuestos, mejora la reproducción o recopila una evidencia nueva. No impongas un número universal de reintentos ni deduzcas automáticamente un fallo arquitectónico.

Una mitigación urgente puede ser apropiada dentro de la autorización concedida. Delimita qué reduce, qué no resuelve y cómo se comprobará; no la presentes como una causa corregida.

En registros extensos conserva el alcance revisado y las limitaciones. Los resúmenes deben señalar dónde está la evidencia original, sin copiar datos privados.
