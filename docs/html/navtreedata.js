/*
 @licstart  The following is the entire license notice for the JavaScript code in this file.

 The MIT License (MIT)

 Copyright (C) 1997-2020 by Dimitri van Heesch

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 and associated documentation files (the "Software"), to deal in the Software without restriction,
 including without limitation the rights to use, copy, modify, merge, publish, distribute,
 sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or
 substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 @licend  The above is the entire license notice for the JavaScript code in this file
*/
var NAVTREE =
[
  [ "SignBB", "index.html", [
    [ "SignBB — Backend", "index.html", "index" ],
    [ "Pruebas del módulo de autoría", "pruebas_autoria.html", [
      [ "Enfoque", "pruebas_autoria.html#autotoc_md0", null ],
      [ "Mapa de la suite", "pruebas_autoria.html#autotoc_md1", null ],
      [ "Fixture", "pruebas_autoria.html#autotoc_md2", null ],
      [ "Qué se prueba y por qué importa", "pruebas_autoria.html#autotoc_md3", [
        [ "CU02: crear grafo (<span class=\"tt\">EditorLienzoTest</span>, <span class=\"tt\">ValidadorLienzoTest</span>, <span class=\"tt\">CompiladorSubgrafoTest</span>)", "pruebas_autoria.html#autotoc_md4", null ],
        [ "CU07: editar grafo (<span class=\"tt\">EditorLienzoTest</span>, <span class=\"tt\">ServicioAutoriaImplTest</span>)", "pruebas_autoria.html#autotoc_md5", null ],
        [ "CU06: definir reglas (<span class=\"tt\">EditorLienzoTest</span>, <span class=\"tt\">CompiladorReglasTest</span>, <span class=\"tt\">ValidadorReglasTest</span>, <span class=\"tt\">ServicioAutoriaReglasImplTest</span>, <span class=\"tt\">PublicadorReglaTest</span>)", "pruebas_autoria.html#autotoc_md6", null ],
        [ "CU08/CU12: publicar y revisar (<span class=\"tt\">PublicadorSubgrafoTest</span>, <span class=\"tt\">ServicioRevisionImplTest</span>, <span class=\"tt\">GuardiaAutoriaTest</span>, controladores)", "pruebas_autoria.html#autotoc_md7", null ],
        [ "API (<span class=\"tt\">ControladorAutoriaTest</span>, <span class=\"tt\">ControladorRevisionTest</span>)", "pruebas_autoria.html#autotoc_md8", null ]
      ] ],
      [ "Qué se quitó en la reorganización", "pruebas_autoria.html#autotoc_md9", null ],
      [ "Cómo ejecutarlas", "pruebas_autoria.html#autotoc_md10", null ],
      [ "Resultados", "pruebas_autoria.html#autotoc_md11", null ]
    ] ],
    [ "Pruebas del módulo de navegación", "pruebas_navegacion.html", [
      [ "Enfoque", "pruebas_navegacion.html#autotoc_md12", null ],
      [ "Mapa de la suite", "pruebas_navegacion.html#autotoc_md13", null ],
      [ "Fixtures", "pruebas_navegacion.html#autotoc_md14", null ],
      [ "Qué se prueba y por qué importa", "pruebas_navegacion.html#autotoc_md15", [
        [ "CU01/CU03: recorrido y resultado (<span class=\"tt\">MotorNavegacionTest</span>)", "pruebas_navegacion.html#autotoc_md16", null ],
        [ "RF21: retroceder (<span class=\"tt\">MotorNavegacionRetrocederTest</span>)", "pruebas_navegacion.html#autotoc_md17", null ],
        [ "Límite de preguntas (<span class=\"tt\">MotorNavegacionLimiteTest</span>)", "pruebas_navegacion.html#autotoc_md18", null ],
        [ "Cola de selección múltiple (<span class=\"tt\">MotorNavegacionColaTest</span>, <span class=\"tt\">OrdenColaTest</span>)", "pruebas_navegacion.html#autotoc_md19", null ],
        [ "Servicio (<span class=\"tt\">ServicioNavegacionImplTest</span>, <span class=\"tt\">ServicioNavegacionVersionesTest</span>)", "pruebas_navegacion.html#autotoc_md20", null ]
      ] ],
      [ "Qué se quitó en la reorganización", "pruebas_navegacion.html#autotoc_md21", null ],
      [ "Cómo ejecutarlas", "pruebas_navegacion.html#autotoc_md22", null ],
      [ "Resultados", "pruebas_navegacion.html#autotoc_md23", null ]
    ] ],
    [ "Pruebas del módulo de observaciones", "pruebas_observaciones.html", [
      [ "Mapa de la suite", "pruebas_observaciones.html#autotoc_md24", null ],
      [ "Qué se prueba y por qué importa", "pruebas_observaciones.html#autotoc_md25", [
        [ "Captura de la respuesta (<span class=\"tt\">ServicioCapturaImplTest</span>)", "pruebas_observaciones.html#autotoc_md26", null ],
        [ "Dueño del recorrido (<span class=\"tt\">ServicioRecorridosImplTest</span>)", "pruebas_observaciones.html#autotoc_md27", null ],
        [ "Validación de la selección (<span class=\"tt\">SeleccionCuidadorTest</span>)", "pruebas_observaciones.html#autotoc_md28", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_observaciones.html#autotoc_md29", null ]
    ] ],
    [ "Pruebas del módulo de reglas", "pruebas_reglas.html", [
      [ "Mapa de la suite", "pruebas_reglas.html#autotoc_md30", null ],
      [ "Modelo de reglas (TAD)", "pruebas_reglas.html#pruebas_model", [
        [ "TAD <span class=\"tt\">ReglaClinica</span> (<span class=\"tt\">ReglaClinicaTest</span>)", "pruebas_reglas.html#autotoc_md31", null ],
        [ "TAD <span class=\"tt\">BaseReglas</span> (<span class=\"tt\">BaseReglasTest</span>)", "pruebas_reglas.html#autotoc_md32", null ],
        [ "Evaluación abstracta (<span class=\"tt\">EvaluacionReglasAbstractaTest</span>)", "pruebas_reglas.html#autotoc_md33", null ]
      ] ],
      [ "Servicio de inferencia y mapeo JSON", "pruebas_reglas.html#autotoc_md34", [
        [ "Servicio de inferencia (<span class=\"tt\">ServicioReglasImplTest</span>)", "pruebas_reglas.html#autotoc_md35", null ],
        [ "Mapeo JSON (<span class=\"tt\">BaseReglasMapperTest</span>)", "pruebas_reglas.html#autotoc_md36", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_reglas.html#autotoc_md37", null ]
    ] ],
    [ "Pruebas del módulo de seguridad", "pruebas_seguridad.html", [
      [ "Mapa de la suite", "pruebas_seguridad.html#autotoc_md38", null ],
      [ "Ayudas de prueba", "pruebas_seguridad.html#autotoc_md39", null ],
      [ "Qué se prueba y por qué importa", "pruebas_seguridad.html#autotoc_md40", [
        [ "Cuentas (<span class=\"tt\">ServicioCuentasImplTest</span>)", "pruebas_seguridad.html#autotoc_md41", null ],
        [ "Tokens (<span class=\"tt\">ServicioTokensTest</span>)", "pruebas_seguridad.html#autotoc_md42", null ],
        [ "Supervisor inicial (<span class=\"tt\">InicializadorSeguridadTest</span>)", "pruebas_seguridad.html#autotoc_md43", null ],
        [ "Secretos de producción (<span class=\"tt\">VerificadorSecretosProduccionTest</span>)", "pruebas_seguridad.html#autotoc_md44", null ],
        [ "Reglas de acceso (<span class=\"tt\">ReglasAccesoIntegracionTest</span>)", "pruebas_seguridad.html#autotoc_md45", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_seguridad.html#autotoc_md46", null ]
    ] ],
    [ "Pruebas del sistema", "pruebas.html", [
      [ "Cómo ejecutarlas", "pruebas.html#autotoc_md47", null ],
      [ "Tipos de prueba", "pruebas.html#autotoc_md48", null ],
      [ "Enfoque", "pruebas.html#autotoc_md49", null ],
      [ "Trazabilidad con los casos de uso", "pruebas.html#autotoc_md50", null ],
      [ "Pruebas unitarias", "pruebas.html#autotoc_md51", null ],
      [ "Pruebas de integración", "pruebas.html#autotoc_md52", null ],
      [ "Totales", "pruebas.html#autotoc_md53", null ],
      [ "Resultados", "pruebas.html#autotoc_md54", null ],
      [ "Cobertura", "pruebas.html#autotoc_md55", null ]
    ] ],
    [ "Pruebas del cache", "pruebas_cache.html", [
      [ "Ubicación", "pruebas_cache.html#autotoc_md56", null ],
      [ "ResolvedorContenidoClinicoTest — resolución cache-aside del contenido", "pruebas_cache.html#autotoc_md57", null ],
      [ "RepositorioSesionRedisTest — sesiones en Redis", "pruebas_cache.html#autotoc_md58", null ],
      [ "CandadoRecorridoRedisTest — candado por recorrido", "pruebas_cache.html#autotoc_md59", null ],
      [ "CacheRedisIntegracionTest — integración", "pruebas_cache.html#autotoc_md60", null ],
      [ "ColaSeleccionMultipleRedisIntegracionTest — cola de la selección múltiple", "pruebas_cache.html#autotoc_md61", null ],
      [ "Cómo ejecutarlas", "pruebas_cache.html#autotoc_md62", null ],
      [ "Notas", "pruebas_cache.html#autotoc_md63", null ],
      [ "Limpieza programada (<span class=\"tt\">mantenimiento</span>)", "pruebas_cache.html#autotoc_md64", null ]
    ] ],
    [ "Manual de contenedores", "md_contenedores_2MANUAL__CONTENEDORES.html", [
      [ "Levantar", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md76", [
        [ "Frontend PWA (Caddy)", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md77", null ],
        [ "Variables de entorno", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md78", null ]
      ] ],
      [ "Dar de baja", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md79", null ],
      [ "Eliminar los datos persistentes", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md80", null ],
      [ "Acceso rápido", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md81", null ],
      [ "Parámetros de conexión (perfil <span class=\"tt\">docker</span> del backend en el host)", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md82", null ]
    ] ],
    [ "SignBB · Autoría de subgrafos clínicos: qué se hizo y qué falta", "md_docs_2autoria-subgrafos.html", [
      [ "1. Qué es esta parte", "md_docs_2autoria-subgrafos.html#autotoc_md84", null ],
      [ "2. Ramas y cómo están", "md_docs_2autoria-subgrafos.html#autotoc_md85", null ],
      [ "3. Pasos que seguimos", "md_docs_2autoria-subgrafos.html#autotoc_md86", [
        [ "Back (<span class=\"tt\">signbb-backend</span>, rama <span class=\"tt\">autoria</span>)", "md_docs_2autoria-subgrafos.html#autotoc_md87", null ],
        [ "Front (<span class=\"tt\">signbb-frontend</span>, rama <span class=\"tt\">autoria</span>, proyecto <span class=\"tt\">ui-autoria</span>)", "md_docs_2autoria-subgrafos.html#autotoc_md88", null ]
      ] ],
      [ "4. API (<span class=\"tt\">/api/autoria</span>)", "md_docs_2autoria-subgrafos.html#autotoc_md89", null ],
      [ "5. Cómo probarlo en local", "md_docs_2autoria-subgrafos.html#autotoc_md90", [
        [ "Mac (Terminal, zsh)", "md_docs_2autoria-subgrafos.html#autotoc_md91", null ],
        [ "Windows (PowerShell)", "md_docs_2autoria-subgrafos.html#autotoc_md92", null ],
        [ "Verificar que el back responde (Mac y Windows)", "md_docs_2autoria-subgrafos.html#autotoc_md93", null ],
        [ "Prueba sugerida: «Ictericia en la piel»", "md_docs_2autoria-subgrafos.html#autotoc_md94", null ],
        [ "Probar la limpieza a mano", "md_docs_2autoria-subgrafos.html#autotoc_md95", null ],
        [ "Errores a verificar", "md_docs_2autoria-subgrafos.html#autotoc_md96", null ]
      ] ],
      [ "6. Pendientes para tener completa la parte de subgrafos clínicos", "md_docs_2autoria-subgrafos.html#autotoc_md97", [
        [ "Imprescindibles", "md_docs_2autoria-subgrafos.html#autotoc_md98", null ],
        [ "Importantes", "md_docs_2autoria-subgrafos.html#autotoc_md99", null ],
        [ "Detalles menores", "md_docs_2autoria-subgrafos.html#autotoc_md100", null ]
      ] ],
      [ "7. Cosas a tener en cuenta", "md_docs_2autoria-subgrafos.html#autotoc_md101", null ]
    ] ],
    [ "Entorno de pruebas y demo", "entorno_pruebas.html", [
      [ "Usuarios creados", "entorno_pruebas.html#autotoc_md102", null ],
      [ "Cómo se ejecuta el seed", "entorno_pruebas.html#autotoc_md103", [
        [ "Backend local", "entorno_pruebas.html#autotoc_md104", null ],
        [ "Docker", "entorno_pruebas.html#autotoc_md105", null ]
      ] ],
      [ "Grafos", "entorno_pruebas.html#autotoc_md106", [
        [ "Índice de nodos", "entorno_pruebas.html#autotoc_md107", null ],
        [ "Grafo raíz", "entorno_pruebas.html#autotoc_md108", null ],
        [ "Respiración y nariz", "entorno_pruebas.html#autotoc_md109", null ],
        [ "Gastrointestinal", "entorno_pruebas.html#autotoc_md110", null ],
        [ "Actividad y color", "entorno_pruebas.html#autotoc_md111", null ],
        [ "Cabeza, ojos y oídos", "entorno_pruebas.html#autotoc_md112", null ]
      ] ],
      [ "Bases de reglas", "entorno_pruebas.html#autotoc_md113", null ],
      [ "Scripts de ayuda", "entorno_pruebas.html#autotoc_md114", [
        [ "<span class=\"tt\">json_a_dot.py</span>", "entorno_pruebas.html#autotoc_md115", null ],
        [ "<span class=\"tt\">reglas_a_markdown.py</span>", "entorno_pruebas.html#autotoc_md116", null ],
        [ "<span class=\"tt\">regenerar_grafos.sh</span>", "entorno_pruebas.html#autotoc_md117", null ]
      ] ],
      [ "Validación del entorno", "entorno_pruebas.html#autotoc_md118", [
        [ "Pruebas automáticas", "entorno_pruebas.html#autotoc_md119", null ],
        [ "Comprobaciones del seed", "entorno_pruebas.html#autotoc_md120", null ]
      ] ],
      [ "Notas", "entorno_pruebas.html#autotoc_md121", null ]
    ] ],
    [ "base_reglas", "md_docs_2entorno-pruebas_2reglas_2base__reglas.html", null ],
    [ "Base de reglas: grafo_activo_color", "md_docs_2entorno-pruebas_2reglas_2reglas__activo__color.html", [
      [ "Resumen por nivel", "md_docs_2entorno-pruebas_2reglas_2reglas__activo__color.html#autotoc_md124", null ]
    ] ],
    [ "Base de reglas: grafo_cabeza", "md_docs_2entorno-pruebas_2reglas_2reglas__cabeza.html", [
      [ "Resumen por nivel", "md_docs_2entorno-pruebas_2reglas_2reglas__cabeza.html#autotoc_md126", null ]
    ] ],
    [ "Base de reglas: grafo_gastro", "md_docs_2entorno-pruebas_2reglas_2reglas__gastro.html", [
      [ "Resumen por nivel", "md_docs_2entorno-pruebas_2reglas_2reglas__gastro.html#autotoc_md128", null ]
    ] ],
    [ "Base de reglas: grafo_respiracion", "md_docs_2entorno-pruebas_2reglas_2reglas__respiracion.html", [
      [ "Resumen por nivel", "md_docs_2entorno-pruebas_2reglas_2reglas__respiracion.html#autotoc_md130", null ]
    ] ],
    [ "Paquetes", "namespaces.html", [
      [ "Lista de paquetes", "namespaces.html", "namespaces_dup" ],
      [ "Miembros de paquete", "namespacemembers.html", [
        [ "Todos", "namespacemembers.html", null ],
        [ "Funciones", "namespacemembers_func.html", null ]
      ] ]
    ] ],
    [ "Clases", "annotated.html", [
      [ "Lista de clases", "annotated.html", "annotated_dup" ],
      [ "Índice de clases", "classes.html", null ],
      [ "Jerarquía de clases", "hierarchy.html", "hierarchy" ],
      [ "Miembros de clases", "functions.html", [
        [ "Todos", "functions.html", "functions_dup" ],
        [ "Funciones", "functions_func.html", "functions_func" ],
        [ "Variables", "functions_vars.html", null ]
      ] ]
    ] ]
  ] ]
];

var NAVTREEINDEX =
[
"annotated.html",
"classorg_1_1signbb_1_1backend_1_1autoria_1_1servicios_1_1PublicadorRegla.html",
"classorg_1_1signbb_1_1backend_1_1mantenimiento_1_1LimpiadorLimites.html#a4e875a0c6139e860117e303ac81e6b15",
"classorg_1_1signbb_1_1backend_1_1navegacion_1_1modelo_1_1GrafoClinico.html#a612bdd6d0d822ea5be85c367bb50edcf",
"classorg_1_1signbb_1_1backend_1_1observaciones_1_1servicios_1_1ServicioRecorridosImplTest.html",
"classorg_1_1signbb_1_1backend_1_1seguridad_1_1servicios_1_1LimitadorIntentos.html#afaf92ba151425855315a12e730096b8b",
"interfaceorg_1_1signbb_1_1backend_1_1autoria_1_1servicios_1_1ServicioAutoriaReglas.html#a4e9d048018b247773dacf280573bff9c",
"namespaceorg_1_1signbb_1_1backend_1_1observaciones_1_1controladores.html#ae74b565ebb1b3c310c943c6178db09ea"
];

const SYNCONMSG = 'pulsar para deshabilitar sincronización';
const SYNCOFFMSG = 'pulsar para habilitar sincronización';
const LISTOFALLMEMBERS = 'Lista de todos los miembros';