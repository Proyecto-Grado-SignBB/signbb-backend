package org.signbb.backend.navegacion.datos.entidades;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.navegacion.datos.serializacion.GrafoClinicoJson;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Data;

/** Representa los datos persistidos de un grafo clinico usando JPA Entities */
@Entity
@Data
public class GrafoClinicoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Documento JSON (formato de intercambio) del grafo clinico. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estructura", nullable = false)
    private GrafoClinicoJson estructura;

    /** Utilizado para identificar si hay versiones antiguas utilizadas */
    @Version
    @Column(name = "jpa_version")
    private Long version;
}
