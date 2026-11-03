# Verificar el comportamiento y su evidencia

## Diseñar la verificación antes de cerrar la implementación

Relaciona cada criterio de aceptación con una comprobación adecuada. Elige la profundidad según el riesgo, las herramientas y el alcance real.

Distingue estas capas:

| Capa | Evidencia que aporta |
| --- | --- |
| Análisis estático | Propiedades verificables sin ejecutar el comportamiento del producto |
| Construcción real | El proyecto genera los resultados de las tareas ejecutadas |
| Pruebas unitarias | Comportamientos concretos de unidades ejecutadas |
| Integración | Interacción entre componentes y dependencias seleccionadas |
| Ejecución con dobles o simulación | Comportamiento bajo las sustituciones declaradas |
| Ejecución del sistema real | Comportamiento en el entorno efectivamente utilizado |
| Inspección del artefacto | Propiedades del paquete o resultado construido |

Ninguna capa sustituye automáticamente a las demás. Un build puede incluir más que compilación; describe las tareas que corrieron. Un test compilado todavía no es un test ejecutado. Un doble no valida el componente que sustituye.

Un resultado satisfactorio solo acredita el alcance comprobado. No extrapoles una prueba pequeña a todo el producto.

## Ejecutar pruebas reales y ampliar la evidencia

Para cambios de código o configuración ejecutable:

1. Ejecuta las comprobaciones estáticas pertinentes.
2. Construye el proyecto o los módulos afectados con su cadena real cuando exista un paso de construcción.
3. Ejecuta las pruebas relacionadas.
4. Ejecuta el comportamiento modificado mediante el mecanismo más directo y válido disponible.
5. Comprueba integración y regresiones según el riesgo.
6. Inspecciona el resultado distribuible si el cambio afecta su generación o contenido.

En proyectos interpretados, utiliza sus comprobaciones y ejecuciones reales; no inventes una compilación que el ecosistema no requiere. Para cambios exclusivamente documentales, aplica validaciones documentales pertinentes.

Aprovecha un entorno capaz: si puedes preparar las herramientas y ejecutar una comprobación necesaria, hazlo. No delegues al usuario pruebas que puedes realizar de manera válida.

Si falta infraestructura de pruebas, añade la mínima que aporte evidencia significativa sin alterar innecesariamente el producto. Hazla reproducible e intégrala cuando sea útil mantenerla. No sustituyas el proyecto por un programa de ejemplo para afirmar que verificaste sus cambios.

Ejecuta las clases, módulos o componentes reales modificados. Los fixtures y dobles deben servirles de soporte; no reimplementes la solución dentro del test para demostrar que funciona.

Cuando uses simulación, identifica sus sustituciones y límites. Confirma compatibilidad antes de incorporar herramientas. Adaptar un test a otro entorno exige revisar sus supuestos, no solo cambiar su ejecutor.

Ejercita casos normales, límites y fallos relevantes derivados del contrato actual. Añade una regresión para un defecto significativo cuando sea viable automatizarlo de manera estable.

No fuerces pruebas nuevas para cambios triviales ni persigas cobertura numérica sin valor. Si utilizas cobertura, úsala para detectar rutas importantes sin verificar. No reduzcas el ámbito del informe para esconder un fallo.

## Medir cuando se afirme una mejora

Si la tarea incluye rendimiento, consumo o escalabilidad, establece una referencia y compara con una metodología coherente.

Identifica carga, entorno, versiones, calentamiento, repeticiones y variabilidad relevantes. Distingue medición directa, estimación e inferencia.

No atribuyas una mejora a un cambio sin evidencia suficiente. No modifiques la corrección del resultado para mejorar una cifra sin evaluar y comunicar el compromiso.

Usa herramientas de perfilado, análisis o carga cuando resuelvan una pregunta concreta. Comprueba que su instrumentación no distorsione sustancialmente lo medido.

El alcance de una mejora medida termina donde termina su evidencia. No generalices resultados de un entorno a todos los destinos.

## Comprobar que la prueba detecta el defecto

Para una regresión importante, comprueba cuando sea viable que la prueba falla por el defecto y pasa con la corrección. Un fallo de preparación, importación o sintaxis no demuestra ese poder de detección.

Si necesitas comparar contra la versión anterior o introducir una mutación controlada, utiliza una copia aislada. No reviertas trabajo ajeno ni alteres el checkout activo para producir una demostración.

Una aserción debe distinguir un resultado correcto de uno incorrecto. Revisa su valor esperado y lo que no cubre. La existencia de archivos o una coincidencia de texto no demuestra por sí sola la corrección del contenido.

## Vigencia de los resultados

Antes de afirmar éxito:

1. Identifica la comprobación que respalda la afirmación.
2. Confirma que corresponde al estado actual.
3. Ejecuta el comando completo con el alcance declarado.
4. Revisa salida, finalización, código de salida y pruebas omitidas.
5. Explica lo demostrado y sus límites.

Lee el resultado completo relevante. Si la herramienta trunca la salida, conserva y examina un archivo o informe completo. Si utilizas tuberías, evita que el código de salida de un filtro o tee oculte el del comando verificado.

Después de cambios que puedan invalidar un resultado, repite sus comprobaciones. No necesitas repetir verificaciones independientes sin motivo.

Una ejecución con cero pruebas, todas omitidas o solo recogida de casos no acredita comportamiento probado. Comprueba el resumen real.

Ejecuta los controles exigidos por el repositorio y la integración pertinente. Si limitas una suite grande, justifica el alcance; no declares verde la parte no ejecutada. Informa los fallos previos por separado, sin atribuirlos al cambio ni ocultarlos.
