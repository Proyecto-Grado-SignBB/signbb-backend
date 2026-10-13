package org.signbb.backend.navegacion.datos.entidades;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;

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
 * La foto incluye el grafo compuesto y la base de reglas compuesta: así un
 * recorrido en curso conserva tanto el grafo como las reglas con las que
 * empezó. En versiones creadas antes de versionar las reglas, {@code reglas}
 * es {@code null} y se usan las vigentes.
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

    /**
     * Base de reglas compuesta de esta versión, o {@code null} en versiones
     * creadas antes de versionar las reglas.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reglas", updatable = false)
    private BaseReglasJson reglas;

    /** Cuándo se guardó la versión. */
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    /** Qué la originó, para quien revise la tabla (por ejemplo, la publicación de un subgrafo). */
    @Column(name = "origen", nullable = false, updatable = false, length = 255)
    private String origen;

    /** Crea la versión con el grafo y las reglas compuestas, y el motivo; la fecha es la actual. */
    public VersionContenidoEntity(GrafoClinicoJson grafo, BaseReglasJson reglas, String origen) {
        this.grafo = grafo;
        this.reglas = reglas;
        this.origen = origen;
        this.creadaEn = Instant.now();
    }
}
