/**
 * Módulo de seguridad: cuentas, roles y sesión con JWT. Hay tres roles
 * ({@link org.signbb.backend.seguridad.modelo.Rol}): el padre o acudiente que
 * hace los recorridos, el médico que arma subgrafos y reglas, y el médico
 * supervisor que revisa lo de los demás antes de publicarlo y administra las
 * cuentas clínicas.
 *
 * <p>
 * El token viaja en {@code Authorization: Bearer} y lo valida el resource
 * server de Spring Security ({@link
 * org.signbb.backend.seguridad.ConfiguracionSeguridad}). Los controladores
 * reciben el usuario como {@link
 * org.signbb.backend.seguridad.modelo.UsuarioActual}, así los servicios no
 * dependen de Spring Security y se prueban con Mockito.
 */
package org.signbb.backend.seguridad;
