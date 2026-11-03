# Preparar el entorno a partir del proyecto

## Primero el proyecto, después las herramientas

Identifica el ecosistema leyendo sus archivos reales de declaración. No deduzcas sus versiones a partir de herramientas instaladas o de una conversación anterior.

Consulta, cuando existan:

- Documentación de instalación, desarrollo, pruebas y distribución.
- Manifiestos de paquetes, módulos y dependencias.
- Lockfiles y catálogos de versiones.
- Wrappers, archivos de selección de versiones y scripts de construcción.
- Configuración del compilador, runtime, SDK y destinos.
- Configuración de pruebas, análisis y cobertura.
- Makefiles, contenedores y otros mecanismos reproducibles.
- Workflows de integración continua.

Obtén una lista concreta de versiones y comandos necesarios. Identifica qué está fijado, qué se hereda y qué debe resolverse según una matriz de compatibilidad.

Si discrepan documentación, scripts y declaraciones, investiga qué utiliza realmente el proyecto. No cambies versiones para hacerlas coincidir sin entender el motivo de la discrepancia.

Descubre las tareas y opciones disponibles mediante la herramienta correspondiente. No inventes nombres de tareas, variantes o paquetes a partir de su apariencia.

## Comprobar el entorno disponible

Comprueba las capacidades necesarias para esta tarea. Reutiliza un inventario reciente si sigue siendo válido; no repitas una auditoría completa sin motivo.

Verifica:

- Sistema, arquitectura y permisos efectivos.
- Rutas de trabajo y almacenamiento persistente.
- Herramientas instaladas, sus versiones y su procedencia.
- Límites efectivos de recursos cuando condicionen el trabajo.
- Acceso a los servicios y repositorios requeridos.
- Posibilidad real de ejecutar el producto y las pruebas previstas.

Distingue lo anunciado por el host de lo asignado al proceso. Si el rendimiento importa, contrasta límites del entorno y una medición representativa. No extrapoles una cifra aislada a todas las tareas.

Comprueba primero las instalaciones y cachés existentes. Usa versiones compatibles y rutas explícitas cuando puedan coexistir varias instalaciones. No dependas de un PATH accidental.

Un inventario declara lo instalado; una operación ejecutada demuestra una capacidad. No presentes una herramienta como operativa únicamente porque existe su directorio o porque un comando informa su versión.

## Preparar las herramientas necesarias

La preparación local de herramientas de desarrollo necesarias forma parte del trabajo, dentro de las autorizaciones y límites del entorno. No te detengas en «no está instalado» si puedes resolverlo de forma razonable.

Para cada componente faltante:

1. Determina por qué es necesario para el proyecto o su verificación.
2. Confirma el nombre real del paquete y la versión compatible.
3. Prefiere el wrapper, gestor o mecanismo reproducible del proyecto.
4. Obtén el componente de una fuente oficial o verificable.
5. Verifica integridad o autenticidad con los mecanismos disponibles.
6. Instálalo de forma aislada, conservando otras versiones necesarias.
7. Ejecuta una comprobación funcional pequeña.
8. Conserva los pasos suficientes para reproducir la preparación.

Instala lo necesario para ejecutar más verificaciones válidas. No instales un catálogo de herramientas por costumbre, ni confundas su cantidad con calidad.

Para seleccionar herramientas adicionales, define primero qué incertidumbre resolverán, qué entrada analizarán y qué evidencia producirán. Descarta las que dupliquen comprobaciones sin aportar valor.

Distingue herramientas locales de desarrollo, dependencias de prueba y dependencias que se distribuirán con el producto. Evalúa compatibilidad, mantenimiento, coste y efectos de cada incorporación. No cambies la cadena del proyecto para acomodar una instalación preexistente.

No eludas políticas de red o de seguridad. Diagnostica el acceso real y usa transportes y repositorios permitidos. La posibilidad técnica de instalar no autoriza costes, servicios externos ni operaciones sobre producción.

## Adaptar la configuración local sin alterar el proyecto

Revisa la configuración que depende de la máquina: rutas, binarios, memoria, paralelismo, cachés, credenciales y servicios de desarrollo.

Distingue:

- Configuración que expresa requisitos del proyecto.
- Configuración particular del equipo del usuario.
- Configuración necesaria solo para el entorno de trabajo actual.

Conserva las dos primeras salvo cambio autorizado o corrección justificada. Aplica los ajustes locales mediante mecanismos soportados: parámetros, variables, perfiles, archivos ignorados o configuración externa.

Verifica la precedencia efectiva de esos mecanismos. Una opción declarada puede estar siendo anulada por otra fuente.

Si no existe un mecanismo limpio y necesitas una modificación temporal para verificar, hazla en una copia o checkout aislado. Conserva el original, registra el parche y evita incorporarlo al cambio entregado. Comprueba que la adaptación no esté encubriendo una incompatibilidad del producto.

No impongas valores universales de memoria, concurrencia, caché o compatibilidad. Ajústalos según la herramienta, sus requisitos y los límites medidos. No desactives controles solo porque dificultan el build.

Una ejecución satisfactoria demuestra que esa configuración permitió esa ejecución; no demuestra por sí sola equivalencia con todos los entornos del proyecto.

## Perfil y conservación

Si el proyecto tiene un perfil local de entorno, úsalo como inventario declarado y contrástalo. Sus versiones no sustituyen los requisitos del proyecto.

Separa herramientas persistentes de datos temporales. Conserva manifiestos de preparación y cachés útiles, con versiones y rutas. Revalida después de restauraciones o cambios de imagen; persistencia declarada no garantiza integridad.

Compara las tareas locales con las de CI para detectar diferencias relevantes. Un laboratorio vacío que compila demuestra preparación de herramientas, no verificación del repositorio objetivo.

Aísla los procesos de prueba y limpia solo lo que hayas creado. No dejes procesos, puertos o servicios de desarrollo ejecutándose sin necesidad.
