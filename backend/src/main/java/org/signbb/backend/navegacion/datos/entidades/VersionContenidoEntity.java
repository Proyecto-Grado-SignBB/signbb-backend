package org.signbb.backend.navegacion.datos.entidades;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una versión del contenido clínico: la foto del grafo completo (raíz y todos
 * los subgrafos ya compuestos) tal como quedó en una publicación.
 *
 * <p>
 * Es inmutable: una vez guardada no se modifica ni se borra. Un recorrido de
 * la cuidadora se ata al {@code id} de la versión con la que empezó y la
 * termina con ella aunque el médico publique cambios mientras tanto. La
 * versión vigente para los recorridos nuevos es la de mayor {@code id}.
 *
 * <p>
 * En esta primera entrega solo se guarda el grafo; las reglas siguen siendo
 * las vigentes.
 */
@Entity
@Table(name = "version_contenido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersionContenidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Grafo clínico compuesto (sin referencias) de esta versión. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grafo", nullable = false, updatable = false)
    private GrafoClinicoJson grafo;

    /** Cuándo se guardó la versión. */
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    /** Qué la originó, para quien revise la tabla (por ejemplo, la publicación de un subgrafo). */
    @Column(name = "origen", nullable = false, updatable = false, length = 255)
    private String origen;

    /** Crea la versión con el grafo compuesto y el motivo; la fecha es la actual. */
    public VersionContenidoEntity(GrafoClinicoJson grafo, String origen) {
        this.grafo = grafo;
        this.origen = origen;
        this.creadaEn = Instant.now();
    }
}
