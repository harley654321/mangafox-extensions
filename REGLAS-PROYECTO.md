# REGLAS-PROYECTO.md — Estructura y mantenibilidad

## 1. Alcance

Estas reglas definen propiedades del código, sus límites y su organización. AGENTS.md define el método de trabajo del agente; no se repiten aquí sus pasos de investigación, ejecución, verificación o entrega.

Son una base reutilizable para distintos lenguajes y tipos de proyecto. No añaden funciones al producto ni obligan a utilizar un framework, patrón, arquitectura distribuida o tecnología determinados.

Las secciones sobre componentes que el proyecto no tenga no aplican. Las convenciones específicas complementan esta base sin convertir detalles de otro producto en requisitos.

## 2. Máximo de 500 líneas por archivo mantenido manualmente

Ningún archivo de código mantenido manualmente que se incorpore o modifique debe superar 500 líneas físicas, contadas con su formato legible habitual. Se cuentan importaciones, declaraciones, comentarios y líneas vacías.

El límite es una política de este proyecto, no una cifra universal que garantice buena arquitectura. Un archivo de menos de 500 líneas también es incorrecto si mezcla responsabilidades.

Queda prohibido cumplir el límite mediante:

- Compactar sentencias, funciones o declaraciones en una misma línea.
- Quitar espacios, comentarios útiles o separaciones para reducir el contador.
- Usar cadenas enormes, código incrustado o archivos auxiliares opacos para esconder la misma complejidad.
- Mover código a otro archivo que mantenga la misma mezcla de responsabilidades.
- Partir mecánicamente por rangos de líneas, números o sufijos sin significado.
- Marcar como generado un archivo editado manualmente.

La solución es extraer responsabilidades a archivos nuevos con nombres, contratos y propietarios claros. No hay que llenar 500 líneas antes de crear otro archivo.

Al acercarse a 300 líneas se debe reconsiderar la cohesión y el crecimiento previsto. Es una alerta de diseño, no una obligación de fragmentar un componente cohesivo ni un nuevo límite.

El límite también alcanza archivos de UI, estilos, configuración ejecutable y pruebas mantenidos manualmente. Una clase, función o declaración grande no está exenta: debe descomponerse mediante los mecanismos reales del lenguaje o framework.

Los archivos generados automáticamente, lockfiles, código externo sin modificar y datos o recursos no ejecutables pueden quedar fuera del contador. Su procedencia y exclusión deben ser explícitas y acotadas. Sus generadores y fuentes editables sí están sujetos a estas reglas. No se excluye una carpeta amplia para ocultar código propio.

El control de tamaño debe usar conteo físico reproducible y el formato normal del proyecto. Cumplirlo no sustituye cumplir las reglas de responsabilidad y dependencias.

## 3. Responsabilidad única y cohesión

Cada archivo, clase, módulo o componente debe representar una responsabilidad reconocible y un motivo coherente de cambio.

No se reúnen operaciones distintas solo porque usan los mismos datos, pertenecen a una misma pantalla o resultan cómodas de localizar juntas.

Los elementos que cambian por el mismo motivo deben permanecer próximos. Los que cambian por motivos independientes deben poder evolucionar sin modificar innecesariamente los demás.

Quedan excluidos los God Objects, God Files y componentes que absorben reglas, estado, acceso externo, presentación y coordinación de todo el sistema.

Tampoco se permite repartir un God Object entre varios archivos manteniendo su dependencia circular o su estado compartido oculto.

## 4. Módulos y límites funcionales

La organización debe mostrar las capacidades y límites reales del proyecto. Los paquetes, carpetas y nombres deben permitir identificar qué pertenece a cada módulo y qué expone.

Un módulo publica una superficie pequeña y explícita. Sus consumidores no acceden a sus detalles internos, almacenamiento particular ni estructuras accidentales del framework.

Las dependencias entre módulos deben formar una dirección comprensible, sin ciclos. La separación lógica puede realizarse dentro de un único proceso o artefacto; no exige microservicios ni múltiples despliegues.

Las carpetas genéricas como utils, helpers, common o shared no son destinos por defecto. Un elemento compartido necesita un significado concreto, consumidores reales y una responsabilidad estable.

## 5. Dependencias explícitas y acoplamiento controlado

Los componentes se conocen mediante contratos necesarios; no se pretende que todos sean ajenos a la existencia de los demás.

La lógica central no debe depender de detalles de presentación, proveedores externos o decisiones accidentales de almacenamiento. Cuando corresponda, dichos detalles implementan los contratos que necesita el núcleo.

No se exige una interfaz por cada clase o función. Una abstracción debe proteger un límite, una variación real o un contrato útil; no existir solo para añadir una capa.

Se deben evitar dependencias transitivas innecesarias, importaciones de internos, localizadores globales de servicios y acceso implícito a dependencias.

La construcción y selección de dependencias corresponde a puntos de composición identificables. Los componentes deben recibir lo que necesitan, con una vida útil clara.

## 6. Coordinadores, orquestadores y adaptadores

Un coordinador expresa la secuencia de una operación y delega sus responsabilidades a colaboradores con contratos claros. No concentra la implementación de todas las operaciones que coordina.

Un adaptador traduce un contrato externo al contrato interno o a la inversa. No acumula reglas de negocio que pertenecen al dominio.

Los conectores encapsulan detalles de integración, sin obligar al resto del proyecto a conocer protocolos o modelos específicos del proveedor.

No se crean coordinadores, conectores ni capas para cada acción trivial. Su incorporación debe reducir mezcla de responsabilidades o proteger una frontera real.

La delegación descrita aquí es entre componentes del código; no obliga a crear subagentes ni tiene relación con la distribución de tareas del agente.

## 7. Presentación, lógica e infraestructura separadas

Cuando exista presentación —visual, programática o de línea de comandos— su responsabilidad es interpretar entradas y representar resultados mediante contratos definidos.

Las reglas funcionales no deben residir dentro de elementos visuales, plantillas, estilos, animaciones o detalles del transporte de una API.

La presentación no ejecuta directamente acceso a persistencia o proveedores externos saltándose los límites de aplicación que correspondan.

El dominio no conoce colores, dimensiones, widgets, rutas visuales ni decisiones de animación. Las transformaciones de formato y estado puramente visual pertenecen a la presentación.

Los estilos, recursos y componentes visuales se organizan por su propia cohesión. No es obligatorio separar cada propiedad en otro archivo.

Cambiar apariencia debe conservar el contrato funcional salvo cambio explícito de ese contrato. Cambiar reglas funcionales no debe exigir alterar recursos de apariencia que no estén afectados.

La separación reduce el alcance de los cambios; no promete que jamás haya cambios legítimos en más de una capa.

## 8. Contratos y encapsulación

Los contratos deben definir entradas, salidas, errores relevantes y efectos observables. No deben depender de detalles internos que sus consumidores no necesitan.

Los tipos y modelos deben expresar restricciones útiles del dominio. Los estados imposibles no deben representarse mediante combinaciones arbitrarias de indicadores independientes.

Las funciones públicas no deben exigir conocer el orden secreto de inicializaciones o mutaciones ajenas para producir un resultado válido.

Los modelos del proveedor, transporte, persistencia y dominio pueden diferir cuando sus contratos lo requieran. Las traducciones pertenecen a fronteras identificadas; no se duplican modelos sin motivo.

Una modificación interna que conserva el contrato no debe obligar a cambiar consumidores. Los cambios incompatibles de contratos públicos requieren una evolución explícita.

## 9. Estado con propietario definido

Cada dato mutable debe tener un propietario, un alcance y una vida útil reconocibles. Los demás componentes lo consultan o modifican mediante el contrato de ese propietario.

Las representaciones derivadas no deben convertirse en fuentes de verdad rivales. Las cachés no son autoridades funcionales salvo que el contrato lo establezca.

No se permite estado global mutable utilizado para comunicar módulos sin un contrato explícito.

Cuando el componente sea concurrente, su modelo de consistencia, orden y sincronización debe estar definido. Los invariantes no deben depender de coincidencias de ejecución.

## 10. Efectos externos y recursos

Los efectos externos deben estar encapsulados en componentes identificables. El código central no debe adquirir dependencias externas de forma oculta.

Los componentes deben poder recibir dependencias controladas cuando sean necesarias para su uso y prueba, sin exigir infraestructura ajena a su responsabilidad.

Cada recurso debe tener un propietario responsable de su vida útil. Su acceso y liberación deben respetar los contratos de la plataforma y de la biblioteca correspondiente.

Las operaciones costosas no pertenecen a rutas que deban responder dentro de un presupuesto menor. El presupuesto y la estrategia dependen del producto, no de valores universales fijados en esta base.

## 11. Datos, errores y consistencia

Las restricciones esenciales de los datos pertenecen a su dominio o frontera autorizada, no únicamente a una representación visual.

Las modificaciones que deban ser indivisibles deben mantener esa propiedad mediante mecanismos adecuados. Un estado parcialmente actualizado no puede presentarse como resultado completo.

Los errores deben conservar significado para el consumidor. No se sustituyen indiscriminadamente por valores vacíos, éxito aparente o excepciones genéricas sin causa.

Una operación que falla no debe corromper el estado válido anterior ni dejar efectos incompatibles con su contrato. Los mecanismos de recuperación forman parte del diseño cuando sean necesarios.

Los datos privados y secretos no deben formar parte de código, recursos públicos o salidas diagnósticas sin una política explícita de protección.

## 12. Nombres, legibilidad y estructura interna

Los nombres deben expresar intención y pertenencia. No se permite llamar manager, service o helper a una mezcla de operaciones sin responsabilidad definida.

Las funciones deben ser comprensibles y mantener un nivel de abstracción coherente. La extracción de funciones y componentes debe aclarar el comportamiento, no esconderlo detrás de saltos innecesarios.

Los comentarios explican decisiones, restricciones o motivos que el código no expresa. No sustituyen nombres claros ni legitiman responsabilidades mezcladas.

No se conservan implementaciones obsoletas comentadas, alternativas muertas ni código provisional como parte de la solución normal.

El formato es consistente con las convenciones del lenguaje y del proyecto. El tamaño se evalúa con ese formato, nunca con una versión comprimida artificialmente.

## 13. Reutilización sin sobrearquitectura

Se comparte una regla o comportamiento común cuando existe una misma responsabilidad semántica. La similitud de unas líneas no demuestra que deban pertenecer a una abstracción común.

No se duplican reglas funcionales para evitar acceder al componente que las posee. Tampoco se obliga a compartir componentes que deben evolucionar independientemente.

No se introducen capas, contenedores, motores genéricos, sistemas de eventos o distribución física para necesidades hipotéticas.

El objetivo es que una modificación local requiera comprender la menor parte razonable del sistema, sin fragmentarlo en archivos triviales que dificulten seguir el comportamiento.

## 14. Configuración y composición reproducibles

La configuración del producto debe estar separada de valores particulares de una máquina. No se incrustan rutas privadas o credenciales en contratos del código.

Las dependencias y la construcción deben tener una definición reproducible conforme al ecosistema. Un cambio local de laboratorio no debe convertirse accidentalmente en un requisito del producto.

Las opciones de configuración deben tener significado, propietario y valor efectivo determinables. No se acumulan opciones duplicadas o contradictorias en distintos niveles.

## 15. Documentación de arquitectura

La documentación necesaria debe permitir identificar módulos, responsabilidades, dependencias permitidas y contratos públicos relevantes.

Las decisiones con consecuencias duraderas deben registrar su contexto y compromiso. No es necesario documentar cada clase ni duplicar el contenido del código.

Las exclusiones del límite de tamaño deben identificar su procedencia y alcance. Ninguna excepción puede convertir código propio mantenido manualmente en una zona sin reglas.

## 16. Qué constituye incumplimiento

Incumple estas reglas un archivo menor de 500 líneas que mezcla responsabilidades, un orquestador que implementa todo, una división que conserva acoplamiento oculto o una separación visual que deja reglas funcionales dentro de la presentación.

También incumple compactar código para aprobar un contador, ocultar una dependencia tras acceso global o introducir una abstracción sin propósito para aparentar modularidad.

Una división válida conserva comportamiento y contratos, reduce responsabilidades por unidad y deja dependencias explícitas. El número de archivos por sí solo no acredita una mejora.

## Referencias y alcance de sus aportaciones

Esta es una política redactada para reutilizarse; no una transcripción de las fuentes. El máximo de 500 líneas y la alerta de 300 son decisiones de organización aquí establecidas, no estándares atribuidos a los autores.

- [Principios arquitectónicos, Microsoft](https://learn.microsoft.com/en-us/dotnet/architecture/modern-web-apps-azure/architectural-principles): separación de responsabilidades, encapsulación y dependencias.
- [Presentation Domain Separation, Martin Fowler](https://martinfowler.com/bliki/PresentationDomainSeparation.html): límites entre presentación y funcionalidad.
- [Refactoring Module Dependencies, Martin Fowler](https://martinfowler.com/articles/refactoring-dependencies.html): modularidad y composición de dependencias.
