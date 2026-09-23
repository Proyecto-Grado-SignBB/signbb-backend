package org.signbb.backend.reglas.datos.entidades;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.signbb.backend.reglas.datos.serializacion.BaseReglasJson;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Data;

@Entity
@Data
public class BaseReglasEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Documento JSON (formato de intercambio) de la base de reglas. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estructura", nullable = false)
    private BaseReglasJson estructura;

    /** Utilizado para identificar si hay versiones antiguas utilizadas */
    @Version
    @Column(name = "jpa_version")
    private Long version;
}
