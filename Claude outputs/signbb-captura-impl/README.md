# Implementación: Módulo de Captura y Registro de Observaciones

Corresponde a `Diseno_Modulo_Captura_Registro_Observaciones.md`. Todos los paths son relativos a `backend/src/main/java/` (o `src/test/java/` para las pruebas) en el repo de `signbb-backend`.

## Archivos NUEVOS (crear tal cual)

- `org/signbb/backend/crobservaciones/modelo/TipoInteraccion.java`
- `org/signbb/backend/crobservaciones/modelo/SeleccionCuidador.java`
- `org/signbb/backend/crobservaciones/modelo/package-info.java`
- `org/signbb/backend/crobservaciones/servicios/ServicioCaptura.java`
- `org/signbb/backend/crobservaciones/servicios/ServicioCapturaImpl.java`
- `org/signbb/backend/crobservaciones/servicios/package-info.java`
- `org/signbb/backend/crobservaciones/repositorios/ObservacionEntity.java`
- `org/signbb/backend/crobservaciones/repositorios/RepositorioObservaciones.java`
- `org/signbb/backend/crobservaciones/repositorios/package-info.java`
- Pruebas: `crobservaciones/modelo/SeleccionCuidadorTest.java`, `crobservaciones/servicios/ServicioCapturaImplTest.java`, `navegacion/motor/MotorNavegacionSeleccionMultipleTest.java`

## Archivos MODIFICADOS (reemplazan a los existentes)

- `org/signbb/backend/crobservaciones/controladores/RespuestaRequest.java` — se agregan `opciones` y `tipo`, ambos opcionales; el campo `respuesta` no cambia.
- `org/signbb/backend/crobservaciones/controladores/ControladorConsulta.java` — el endpoint `responder` ahora inyecta y llama a `ServicioCaptura` en vez de `ServicioNavegacion` directamente. `iniciar` y `consultar` quedan idénticos.
- `org/signbb/backend/navegacion/servicios/ServicioNavegacion.java` — se agrega la sobrecarga `responder(String, List<String>)`. La firma existente no cambia.
- `org/signbb/backend/navegacion/servicios/ServicioNavegacionImpl.java` — implementa la sobrecarga nueva; el resto del archivo es idéntico al que ya tenías.
- `org/signbb/backend/navegacion/motor/MotorNavegacion.java` — se agrega la sobrecarga `paso(..., List<String>)` con el desempate por `peso`; el `paso(..., String)` existente no se toca.

## Qué falta después de copiar esto

1. **Nada de configuración adicional para la auditoría**: `ObservacionEntity` es una entidad JPA más, igual que la `Nodo` que ya existe en `navegacion.servicios` — debería quedar cubierta por el mismo mecanismo de `DatabaseInit`/`ddl-auto` que ya use el proyecto. Si el proyecto usa migraciones explícitas (Flyway/Liquibase), falta agregar el script para la tabla `observacion_entity` y `observacion_opciones`.
2. **Correr las pruebas** (`mvn test`): las de `SeleccionCuidador` y `ServicioCapturaImpl` no dependen de nada externo. La de `MotorNavegacion` sí asume las firmas reales de `GrafoClinico`/`BaseReglas`/`JsonParser` vistas en el código compartido — si algo no compila ahí, es la señal de que alguna de esas clases expone algo distinto a lo inferido; el resto del módulo no depende de eso.
3. **Pendiente, fuera de este alcance** (documentado en la sección 11 del diseño): el campo `tipoInteraccion` en la metadata del grafo/`Nodo`/`PreguntaDTO` sigue sin existir — hoy `ControladorConsulta` lo infiere o toma una pista opcional del cliente (`RespuestaRequest.tipo`), pero no es un dato del grafo. Imágenes del cuidador y sensores (sección 8 del diseño) siguen fuera de alcance.
