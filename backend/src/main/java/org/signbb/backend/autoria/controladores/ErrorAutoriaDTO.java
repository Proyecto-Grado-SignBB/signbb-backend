package org.signbb.backend.autoria.controladores;

import java.util.List;

import org.signbb.backend.autoria.modelo.Aviso;

/**
 * Cuerpo de un error de autoría.
 *
 * @param mensaje texto para el médico
 * @param avisos  avisos que lo causan (vacía si no aplica)
 */
public record ErrorAutoriaDTO(String mensaje, List<Aviso> avisos) {
}
