package org.signbb.backend.mantenimiento;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas ({@link LimpiezaProgramada}). */
@Configuration
@EnableScheduling
public class ConfiguracionMantenimiento {
}
