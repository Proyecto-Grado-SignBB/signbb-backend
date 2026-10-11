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
import jakarta.persistence.Version;
import lombok.Data;

/**
 * Borrador de un subgrafo: lo que el médico edita en el lienzo. Es
 * independiente del {@code GrafoClinicoEntity} publicado, que solo se escribe
 * al publicar; así la cuidadora nunca ve un subgrafo a medio armar.
 */
@Entity
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

    private String autor;

    private String institucion;

    /** Documento del lienzo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documento", nullable = false)
    private DocumentoLienzo documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoBorrador estado;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** Última publicación, o {@code null} si nunca se publicó. */
    private Instant publicadoEn;

    /**
     * Cómo estaba el borrador en su última publicación, o {@code null} si nunca
     * se publicó o se publicó antes de que existiera la copia.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "copia_publicada")
    private CopiaPublicada copiaPublicada;

    /** Bloqueo optimista: dos ediciones simultáneas no se pisan. */
    @Version
    @Column(name = "jpa_version")
    private Long version;
}
