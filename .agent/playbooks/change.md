# Comprender e implementar el cambio

## Comprender la petición y recuperar contexto

Antes de editar:

- Identifica qué pidió el usuario, qué resultado espera y qué restricciones estableció.
- Distingue hechos confirmados, propuestas, suposiciones y pendientes.
- Consulta las reglas y decisiones documentadas del proyecto.
- Identifica el repositorio, su rama y el estado de los cambios locales.
- Conserva el trabajo ajeno y las modificaciones existentes.
- Recupera el estado actual desde fuentes verificables; el recuerdo de una conversación puede estar incompleto.
- Localiza las fuentes conectadas que puedan resolver dudas sobre la tarea.

Formula criterios de aceptación observables: qué debe producir el cambio y cómo se comprobará. No añadas funcionalidades para completar una idea que el usuario no pidió.

Pregunta cuando falte una decisión necesaria que no pueda obtenerse de las fuentes disponibles. Resuelve elecciones rutinarias dentro del alcance autorizado sin pedir confirmación repetida. Si adoptas una suposición que afecte el resultado, hazla explícita.

Si una propuesta técnica es incorrecta, explica la razón, la evidencia disponible, una alternativa y su consecuencia. No des la razón automáticamente.

## Inspeccionar el código y establecer una referencia

Antes de modificar un componente:

- Localiza su implementación, consumidores y pruebas.
- Lee completos los archivos relevantes; si son extensos, revisa sus secciones de forma ordenada.
- Sigue los datos, contratos, dependencias y efectos implicados.
- Identifica dónde se decide el comportamiento y quién es responsable de su estado.
- Revisa las convenciones y los puntos de extensión existentes.
- Inspecciona la construcción y distribución cuando el cambio pueda afectarlas.

Utiliza búsqueda rápida de archivos y referencias, preferentemente rg o su equivalente. Combina búsquedas con lectura contextual; una coincidencia aislada no explica el comportamiento.

Establece una referencia inicial adecuada: una reproducción, ejecución, prueba existente o comprobación del estado previo. Registra fallos anteriores para diferenciarlos de los introducidos.

Si no puedes ejecutar una referencia, explica el impedimento y utiliza la evidencia disponible sin inventar una comparación.

Amplía la inspección cuando exista una causa compartida o un contrato afectado. Evita convertir cada tarea en una reescritura del repositorio.

## Planificar e investigar

Para trabajos complejos, mantén un plan breve con:

- Resultado esperado y criterios de aceptación.
- Componentes afectados.
- Decisiones pendientes y riesgos relevantes.
- Cambios previstos.
- Comprobaciones y evidencia de cierre.

Para un cambio simple, una definición breve puede ser suficiente. No prepares planes extensos que no guíen la ejecución.

Investiga cuando exista incertidumbre técnica, dependencia de una versión o información susceptible de cambiar. Prioriza declaraciones del proyecto, documentación oficial, código fuente, notas de versión e incidencias oficiales.

Confirma los contratos aplicables a las versiones utilizadas. Una solución válida para otra versión puede ser incompatible con esta.

Distingue lo documentado de lo inferido. Conserva referencias cuando sustenten una decisión importante. No sustituyas una prueba disponible por una búsqueda de opiniones.

## Implementar con criterio

Aplica las reglas del proyecto actual sin duplicar aquí sus normas de arquitectura, tamaño o estilo. En este kit están en REGLAS-PROYECTO.md.

Traduce los criterios de aceptación a incrementos pequeños y comprobables. Después de cada incremento significativo, ejecuta una comprobación rápida pertinente. No acumules modificaciones extensas sin feedback.

Mantén los cambios dentro del alcance autorizado. Si aparece una necesidad de diseño que afecta a ese alcance, explica la decisión y actualiza el plan cuando corresponda.

No resuelvas un fallo de verificación debilitando el contrato solicitado o escondiendo el diagnóstico. El criterio de implementación es satisfacer el requisito y las reglas vigentes con evidencia.

## Trazabilidad y decisiones

Mantén una relación entre cada requisito y su implementación y comprobación. Para cambios extensos usa identificadores; para uno pequeño basta una lista breve. No necesitas generar documentos por cada acción.

Descompón el trabajo por entregables comprobables. Define las dependencias entre ellos y sus contratos de entrada y salida. No paralelices tareas que compitan por el mismo estado.

Antes de elegir una solución con coste significativo, compara alternativas razonables según requisitos, evidencia y mantenimiento. Registra solo las decisiones que ayuden a entender o continuar el trabajo.

Si la investigación invalida el plan, actualízalo. No modifiques el requisito para que coincida con lo que ya escribiste. Una mejora fuera del alcance se propone por separado.


## Cobertura de diagnóstico

Antes de implementar, aplica [el procedimiento de observabilidad](observability.md) al flujo afectado. La instrumentación faltante forma parte del cambio; no dejes operaciones importantes sin evidencia de ejecución y resultado.
