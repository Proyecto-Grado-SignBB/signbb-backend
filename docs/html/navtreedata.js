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
      [ "Mapa de la suite", "pruebas_autoria.html#autotoc_md0", null ],
      [ "Fixture", "pruebas_autoria.html#autotoc_md1", null ],
      [ "Qué se prueba y por qué importa", "pruebas_autoria.html#autotoc_md2", [
        [ "Edición (<span class=\"tt\">EditorLienzoTest</span>)", "pruebas_autoria.html#autotoc_md3", null ],
        [ "Avisos (<span class=\"tt\">ValidadorLienzoTest</span>)", "pruebas_autoria.html#autotoc_md4", null ],
        [ "Compilar (<span class=\"tt\">CompiladorSubgrafoTest</span>)", "pruebas_autoria.html#autotoc_md5", null ],
        [ "Publicar (<span class=\"tt\">PublicadorSubgrafoTest</span>)", "pruebas_autoria.html#autotoc_md6", null ],
        [ "Servicio (<span class=\"tt\">ServicioAutoriaImplTest</span>)", "pruebas_autoria.html#autotoc_md7", null ],
        [ "Reglas: compilar, validar y publicar (<span class=\"tt\">CompiladorReglasTest</span>, <span class=\"tt\">ValidadorReglasTest</span>, <span class=\"tt\">PublicadorReglaTest</span>)", "pruebas_autoria.html#autotoc_md8", null ],
        [ "Reglas: CRUD y publicación (<span class=\"tt\">ServicioAutoriaReglasImplTest</span>, <span class=\"tt\">ServicioAutoriaReglasAbstractoTest</span>)", "pruebas_autoria.html#autotoc_md9", null ],
        [ "API (<span class=\"tt\">ControladorAutoriaTest</span>)", "pruebas_autoria.html#autotoc_md10", null ],
        [ "Punta a punta (<span class=\"tt\">AutoriaIntegracionTest</span>)", "pruebas_autoria.html#autotoc_md11", null ],
        [ "Versiones del contenido (republicar sin romper recorridos en curso)", "pruebas_autoria.html#autotoc_md12", null ],
        [ "Imágenes de las preguntas", "pruebas_autoria.html#autotoc_md13", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_autoria.html#autotoc_md14", null ]
    ] ],
    [ "Pruebas del módulo de navegación", "pruebas_navegacion.html", [
      [ "Mapa de la suite", "pruebas_navegacion.html#autotoc_md15", null ],
      [ "Fixtures", "pruebas_navegacion.html#autotoc_md16", null ],
      [ "Qué se prueba y por qué importa", "pruebas_navegacion.html#autotoc_md17", [
        [ "Alertas y validación (<span class=\"tt\">MotorNavegacionTest</span>)", "pruebas_navegacion.html#autotoc_md18", null ],
        [ "Retroceder (<span class=\"tt\">MotorNavegacionRetrocederTest</span>)", "pruebas_navegacion.html#autotoc_md19", null ],
        [ "Límite de preguntas (<span class=\"tt\">MotorNavegacionLimiteTest</span>)", "pruebas_navegacion.html#autotoc_md20", null ],
        [ "Cola de la selección múltiple (<span class=\"tt\">MotorNavegacionColaTest</span>, <span class=\"tt\">OrdenColaTest</span>)", "pruebas_navegacion.html#autotoc_md21", null ],
        [ "Servicio (<span class=\"tt\">ServicioNavegacionImplTest</span>)", "pruebas_navegacion.html#autotoc_md22", null ]
      ] ],
      [ "Qué se quitó en la reorganización", "pruebas_navegacion.html#autotoc_md23", null ],
      [ "Cómo ejecutarlas", "pruebas_navegacion.html#autotoc_md24", null ],
      [ "Resultados en GitHub Actions", "pruebas_navegacion.html#autotoc_md25", null ]
    ] ],
    [ "Pruebas del módulo de observaciones", "pruebas_observaciones.html", [
      [ "Mapa de la suite", "pruebas_observaciones.html#autotoc_md26", null ],
      [ "Qué se prueba y por qué importa", "pruebas_observaciones.html#autotoc_md27", [
        [ "Captura de la respuesta (<span class=\"tt\">ServicioCapturaImplTest</span>)", "pruebas_observaciones.html#autotoc_md28", null ],
        [ "Validación de la selección (<span class=\"tt\">SeleccionCuidadorTest</span>)", "pruebas_observaciones.html#autotoc_md29", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_observaciones.html#autotoc_md30", null ]
    ] ],
    [ "Pruebas del módulo de reglas", "pruebas_reglas.html", [
      [ "Mapa de la suite", "pruebas_reglas.html#autotoc_md31", null ],
      [ "Modelo de reglas (TAD)", "pruebas_reglas.html#pruebas_model", [
        [ "TAD <span class=\"tt\">ReglaClinica</span> (<span class=\"tt\">ReglaClinicaTest</span>)", "pruebas_reglas.html#autotoc_md32", null ],
        [ "TAD <span class=\"tt\">BaseReglas</span> (<span class=\"tt\">BaseReglasTest</span>)", "pruebas_reglas.html#autotoc_md33", null ],
        [ "Evaluación abstracta (<span class=\"tt\">EvaluacionReglasAbstractaTest</span>)", "pruebas_reglas.html#autotoc_md34", null ]
      ] ],
      [ "Servicio de inferencia y mapeo JSON", "pruebas_reglas.html#autotoc_md35", [
        [ "Servicio de inferencia (<span class=\"tt\">ServicioReglasImplTest</span>)", "pruebas_reglas.html#autotoc_md36", null ],
        [ "Mapeo JSON (<span class=\"tt\">BaseReglasMapperTest</span>)", "pruebas_reglas.html#autotoc_md37", null ]
      ] ],
      [ "Cómo ejecutarlas", "pruebas_reglas.html#autotoc_md38", null ]
    ] ],
    [ "Pruebas del sistema", "pruebas.html", [
      [ "Cómo ejecutarlas", "pruebas.html#autotoc_md39", null ],
      [ "Tipos de prueba", "pruebas.html#autotoc_md40", null ],
      [ "Suite por módulo", "pruebas.html#autotoc_md41", null ],
      [ "Pruebas de integración de la raíz", "pruebas.html#autotoc_md42", null ],
      [ "Resultados en CI", "pruebas.html#autotoc_md43", null ]
    ] ],
    [ "Pruebas del cache", "pruebas_cache.html", [
      [ "Ubicación", "pruebas_cache.html#autotoc_md44", null ],
      [ "ResolvedorContenidoClinicoTest — resolución cache-aside del contenido", "pruebas_cache.html#autotoc_md45", null ],
      [ "RepositorioSesionRedisTest — sesiones en Redis", "pruebas_cache.html#autotoc_md46", null ],
      [ "CacheRedisIntegracionTest — integración", "pruebas_cache.html#autotoc_md47", null ],
      [ "ColaSeleccionMultipleRedisIntegracionTest — cola de la selección múltiple", "pruebas_cache.html#autotoc_md48", null ],
      [ "Cómo ejecutarlas", "pruebas_cache.html#autotoc_md49", null ],
      [ "Notas", "pruebas_cache.html#autotoc_md50", null ]
    ] ],
    [ "Manual de contenedores", "md_contenedores_2MANUAL__CONTENEDORES.html", [
      [ "Levantar", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md59", null ],
      [ "Dar de baja", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md60", null ],
      [ "Eliminar los datos persistentes", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md61", null ],
      [ "Acceso rápido", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md62", null ],
      [ "Parámetros de conexión (perfil <span class=\"tt\">docker</span> del backend)", "md_contenedores_2MANUAL__CONTENEDORES.html#autotoc_md63", null ]
    ] ],
    [ "Grafo sistema", "md_grafo__sistema_2reporte__grafo.html", [
      [ "Indice", "md_grafo__sistema_2reporte__grafo.html#autotoc_md65", null ],
      [ "Grafo Raíz", "md_grafo__sistema_2reporte__grafo.html#autotoc_md66", null ],
      [ "Grafo Gastrointestinal", "md_grafo__sistema_2reporte__grafo.html#autotoc_md67", null ],
      [ "Grafo Respiración", "md_grafo__sistema_2reporte__grafo.html#autotoc_md68", null ],
      [ "Grafo Activo y color", "md_grafo__sistema_2reporte__grafo.html#autotoc_md69", null ],
      [ "Herramientas", "md_grafo__sistema_2reporte__grafo.html#autotoc_md70", [
        [ "Construir grafos visualmente", "md_grafo__sistema_2reporte__grafo.html#autotoc_md71", null ],
        [ "Representacion visual de base de reglas", "md_grafo__sistema_2reporte__grafo.html#autotoc_md72", null ],
        [ "Representacion visual de grafo", "md_grafo__sistema_2reporte__grafo.html#autotoc_md73", null ]
      ] ]
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
"classorg_1_1signbb_1_1backend_1_1autoria_1_1servicios_1_1ServicioAutoriaImpl.html#af4d95fca5828977091adf9f311102f70",
"classorg_1_1signbb_1_1backend_1_1navegacion_1_1modelo_1_1EstadoPaciente.html#a27335a5b97fc21155783f7a729231f71",
"classorg_1_1signbb_1_1backend_1_1reglas_1_1datos_1_1entidades_1_1BaseReglasEntity.html",
"interfaceorg_1_1signbb_1_1backend_1_1navegacion_1_1servicios_1_1ServicioNavegacion.html#a7075fd56cad7c7d69503cc97280e13a0"
];

const SYNCONMSG = 'pulsar para deshabilitar sincronización';
const SYNCOFFMSG = 'pulsar para habilitar sincronización';
const LISTOFALLMEMBERS = 'Lista de todos los miembros';