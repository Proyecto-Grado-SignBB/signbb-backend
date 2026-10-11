package org.signbb.backend.autoria.datos.entidades;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.autoria.modelo.DocumentoBaseRegla;
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
 * Borrador de una base de reglas: lo que el médico edita en el panel de reglas.
 * Es independiente de la {@code BaseReglasEntity} publicada, que solo se escribe
 * al publicar; así la cuidadora nunca ve reglas a medio armar.
 *
 * <p>
 * Se identifica por {@code grafoId}: el grafo publicado al que pertenecen las
 * reglas ({@code grafo_raiz} o un subgrafo). Las reglas de un subgrafo usan los
 * ids locales de sus nodos y el compositor los prefija al publicar.
 */
@Entity
@Data
public class BorradorBaseReglaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id del grafo publicado al que pertenecen las reglas ({@code grafo_<slug>}
     * o {@code grafo_raiz}), único y sin {@code /}. Se fija al crear el borrador
     * y no cambia.
     */
    @Column(nullable = false, unique = true)
    private String grafoId;

    /** Nombre de la base de reglas, para el tablero; no va al JSON publicado. */
    @Column(nullable = false)
    private String nombre;

    /** Descripción de la base de reglas, para el tablero; no va al JSON publicado. */
    private String descripcion;

    private String autor;

    private String institucion;

    /** Documento con las reglas del borrador. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documento", nullable = false)
    private DocumentoBaseRegla documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoBorrador estado;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** Última publicación, o {@code null} si nunca se publicó. */
    private Instant publicadoEn;

    /** Bloqueo optimista: dos ediciones simultáneas no se pisan. */
    @Version
    @Column(name = "jpa_version")
    private Long version;
}
