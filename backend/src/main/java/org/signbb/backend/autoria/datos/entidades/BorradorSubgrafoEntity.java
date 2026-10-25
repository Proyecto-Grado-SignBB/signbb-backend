package org.signbb.backend.autoria.datos.entidades;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.autoria.modelo.CopiaPublicada;
import org.signbb.backend.autoria.modelo.DocumentoLienzo;
import org.signbb.backend.autoria.modelo.EstadoBorrador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * Borrador de un subgrafo: lo que el médico edita en el lienzo. Es
 * independiente del {@code GrafoClinicoEntity} publicado, que solo se escribe
 * al publicar; así la cuidadora nunca ve un subgrafo a medio armar.
 */
@Entity
@Table(name = "borrador_subgrafo_entity", indexes = {
        @Index(name = "ix_borrador_subgrafo_estado", columnList = "estado"),
        @Index(name = "ix_borrador_subgrafo_autor", columnList = "autor_id"),
        @Index(name = "ix_borrador_subgrafo_revisor", columnList = "revisor_id") })
@Data
public class BorradorSubgrafoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id del subgrafo publicado ({@code grafo_<slug>}), único y sin {@code /}.
     * Se fija al crear el borrador y no cambia.
     */
    @Column(nullable = false, unique = true)
    private String grafoId;

    /** Nombre del subgrafo, el {@code label} del grafo publicado. */
    @Column(nullable = false)
    private String nombre;

    /** Pregunta puente del grafo raíz desde la que se entra al subgrafo. */
    @Column(nullable = false)
    private String puenteId;

    /** Categoría de la pregunta puente (la opción de la pregunta inicial). */
    @Column(nullable = false)
    private String categoria;

    /** Texto de la opción que lleva al subgrafo desde la pregunta puente. */
    @Column(nullable = false)
    private String etiquetaOpcion;

    /** Nombre del autor (para mostrar y para la metadata publicada); sale de su cuenta. */
    private String autor;

    /** Institución del autor; sale de su cuenta. */
    private String institucion;

    /**
     * Cuenta del médico que creó el borrador: solo él lo edita. Es {@code null}
     * en los borradores de antes del módulo de seguridad, que solo un
     * supervisor puede ver.
     */
    @Column(name = "autor_id")
    private Long autorId;

    /** Documento del lienzo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documento", nullable = false)
    private DocumentoLienzo documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoBorrador estado;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** Última publicación, o {@code null} si nunca se publicó o se despublicó. */
    private Instant publicadoEn;

    /**
     * Cómo estaba el borrador en su última publicación, o {@code null} si nunca
     * se publicó o se publicó antes de que existiera la copia.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "copia_publicada")
    private CopiaPublicada copiaPublicada;

    /** Cuándo se envió a revisión por última vez, o {@code null}. */
    private Instant enviadoRevisionEn;

    /** Supervisor que aprobó o rechazó la última revisión, o {@code null}. */
    @Column(name = "revisor_id")
    private Long revisorId;

    /** Cuándo se aprobó o rechazó la última revisión, o {@code null}. */
    private Instant revisadoEn;

    /**
     * Comentario del supervisor al rechazar, para que el autor sepa qué
     * corregir. Se borra al volver a enviarlo o al aprobarlo.
     */
    @Column(length = 2000)
    private String comentarioRevision;

    /** Bloqueo optimista: dos ediciones simultáneas no se pisan. */
    @Version
    @Column(name = "jpa_version")
    private Long version;
}
