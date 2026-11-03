# Revisar y entregar

## Revisar el cambio completo

Antes de entregar, realiza una revisión de autor:

1. Lee el diff completo y el contexto de los archivos modificados.
2. Comprueba que todos los cambios corresponden al objetivo.
3. Revisa los contratos, consumidores y configuraciones afectados.
4. Verifica que la implementación satisface los criterios de aceptación.
5. Revisa los riesgos pertinentes y las rutas de fallo.
6. Comprueba los resultados de pruebas y su alcance real.
7. Busca supresiones, código temporal, duplicación y dependencias innecesarias.
8. Comprueba que no se incluyan secretos ni datos privados.
9. Revisa el artefacto y sus configuraciones relevantes cuando corresponda.
10. Actualiza la documentación que el cambio haya dejado incorrecta.
11. Comprueba que las adaptaciones locales no se filtren a la entrega.
12. Identifica todo pendiente relevante antes de afirmar que terminaste.

Después de una modificación nueva, repite las comprobaciones que pueda invalidar. Si los controles pertinentes ya pasaron y no existe una preocupación nueva, continúa hacia la entrega sin repetir trabajo por ceremonia.

No cierres con fallos bloqueantes conocidos como si todo estuviera listo. No confundas ausencia de errores observados con ausencia absoluta de defectos.

## Git, publicación y continuidad

Inspecciona el estado antes de preparar cambios. Incluye solo el trabajo correspondiente y respeta las convenciones del repositorio.

Commitea y pushea cuando esté autorizado. Comprueba el resultado y el identificador remoto cuando publiques. Si falla la publicación, diferencia claramente el estado local del remoto.

No sobrescribas cambios ajenos ni uses operaciones destructivas o reescritura de historia para facilitar tareas rutinarias.

Commit, push, distribución y despliegue son operaciones distintas. No infieras autorización para una a partir de otra. Evita enviar comunicaciones externas o modificar servicios fuera del alcance concedido.

Mantén reproducibles la preparación y la verificación. Documenta comandos y decisiones duraderas en el lugar apropiado, sin generar archivos de seguimiento innecesarios en cada tarea.

Si se interrumpe la sesión, conserva un resumen operativo: estado actual, cambios, evidencia obtenida, impedimentos y siguiente acción. Usa almacenamiento realmente persistente o el repositorio según corresponda. Excluye secretos.

## Comunicación y entrega

Comunica avances breves en trabajos prolongados: hallazgo, incertidumbre y siguiente comprobación. Evita narrar cada comando o presentar intenciones como ejecuciones.

Mantén evidencia proporcional y reproducible: procedimiento o comando, entorno relevante, resultado y límites. No hace falta pegar toda la salida, pero debe poder identificarse qué se ejecutó.

Distingue con precisión: revisado, construido, ejecutado, medido, inferido y pendiente. No uses etiquetas de actividad como prueba de una ejecución.

Entrega estos cuatro puntos:

1. Qué se analizó: componentes y fuentes relevantes; ubicaciones precisas cuando ayuden.
2. Qué se cambió: resultado y motivo.
3. Cómo se verificó: comandos o procedimientos, resultados y alcance de ejecución.
4. Qué falta: impedimentos, límites y comprobaciones pendientes. Incluye el estado del commit o artefacto cuando corresponda.

Si no modificaste código, dilo. Si no hubo una causa demostrada, no la inventes. Si no ejecutaste el producto, no digas que funciona en él.

El criterio de cierre es que el trabajo autorizado esté realizado, revisado y respaldado por las comprobaciones pertinentes, con sus límites comunicados. La extensión del informe y la cantidad de herramientas no sustituyen esa evidencia.

## Dos pasadas con objetivos diferentes

Primero contrasta la implementación contra la intención actual. Clasifica cualquier brecha como ausente, parcial, contradictoria o fuera de alcance, con evidencia concreta. Las casillas marcadas y las afirmaciones del ejecutor no prueban cumplimiento.

Después examina calidad e integración: contratos, riesgos relevantes, pruebas, complejidad, mantenimiento y distribución. No inventes defectos ni severidad para llenar un informe.

Cada hallazgo debe indicar ubicación, condición que lo activa, consecuencia, evidencia y prioridad. Distingue fallo demostrado, riesgo razonado y preferencia de estilo. Las sugerencias no deben ampliar silenciosamente el alcance.

Si hay revisión independiente disponible y autorizada, proporciona contexto mínimo suficiente, base y estado a revisar, requisitos y comandos de comprobación. El revisor inspecciona sin modificar el checkout compartido. Verifica sus hallazgos; no aceptes ni rechaces feedback por autoridad.

Si revisas tu propio trabajo, declara esa circunstancia cuando importe. No llames independiente a una segunda lectura tuya ni a un informe generado sin acceso al cambio.

No cierres una brecha cambiando el requisito o debilitando su prueba. Corrige el cambio o presenta el pendiente. Una revisión sin hallazgos solo cubre el alcance inspeccionado.


## Control de puntos ciegos

Contrasta el cambio con [el procedimiento de observabilidad](observability.md). Comprueba la cobertura de los flujos afectados y diferencia instrumentación escrita de registros realmente capturados.
