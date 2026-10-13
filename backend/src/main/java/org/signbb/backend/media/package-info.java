/**
 * Imágenes de apoyo de las preguntas. El médico las sube desde el lienzo, se
 * guardan en Silo (almacén compatible con S3) y la cuidadora las ve en la
 * pregunta. En el lienzo y en el grafo solo se guarda el nombre del archivo;
 * el backend las entrega desde {@code /media/{nombre}}.
 */
package org.signbb.backend.media;
