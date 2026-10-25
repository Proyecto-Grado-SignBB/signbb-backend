package org.signbb.backend.media;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.signbb.backend.config.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;

/**
 * Reglas de las imágenes de las preguntas: formato, tamaño y nombre.
 *
 * <p>
 * Antes de guardar, la imagen se decodifica, se aplica la orientación EXIF y
 * se vuelve a codificar sin metadatos. Así el archivo que queda en Silo no
 * conserva GPS, cámara, fechas, miniaturas ni comentarios. JPEG y PNG se
 * mantienen; WebP se convierte a PNG si tiene transparencia o a JPEG si no, y
 * los GIF estáticos se convierten a PNG. Los GIF animados se rechazan para no
 * perder la animación.
 */
@Service
public class ServicioImagenes {

    /** Máximo de píxeles que se decodifican, para evitar bombas de descompresión. */
    static final long MAX_PIXELES = 25_000_000L;

    static {
        // Hace disponibles los lectores de WebP de TwelveMonkeys en el contenedor.
        ImageIO.scanForPlugins();
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServicioImagenes.class);

    private final AlmacenImagenes almacen;
    private final ReferenciasImagenes referencias;
    private final long maxBytes;

    /** Inyecta el almacén, las referencias y el tope de tamaño ({@code signbb.media.max-bytes}). */
    public ServicioImagenes(AlmacenImagenes almacen, ReferenciasImagenes referencias,
            @Value("${signbb.media.max-bytes:1048576}") long maxBytes) {
        this.almacen = almacen;
        this.referencias = referencias;
        this.maxBytes = maxBytes;
    }

    /**
     * Valida, normaliza y guarda una imagen.
     *
     * @return el nombre con el que quedó guardada (uuid y extensión)
     * @throws ImagenInvalidaException      si está vacía, pesa de más, no es png, jpeg, webp ni gif,
     *                                      es animada o no se puede decodificar
     * @throws AlmacenNoDisponibleException si Silo no responde
     */
    public String subir(byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw new ImagenInvalidaException("El archivo está vacío.");
        }
        if (datos.length > maxBytes) {
            throw new ImagenInvalidaException("La imagen pesa " + megas(datos.length)
                    + " y el máximo es " + megas(maxBytes) + ". Elige una más liviana.");
        }
        FormatoImagen formato = FormatoImagen.detectar(datos).orElseThrow(() -> new ImagenInvalidaException(
                "Solo se aceptan imágenes png, jpeg, webp o gif."));
        Normalizada normalizada = normalizar(datos, formato);
        if (normalizada.datos().length > maxBytes) {
            throw new ImagenInvalidaException("La imagen queda demasiado pesada al quitarle los metadatos. "
                    + "Elige una más liviana.");
        }
        String nombre = UUID.randomUUID() + "." + normalizada.formato().extension();
        almacen.guardar(nombre, normalizada.datos(), normalizada.formato().tipo());
        return nombre;
    }

    /**
     * Lee una imagen guardada.
     *
     * @throws RecursoNoEncontradoException si el nombre no es válido o no existe
     * @throws AlmacenNoDisponibleException si Silo no responde
     */
    public AlmacenImagenes.Imagen obtener(String nombre) {
        if (!FormatoImagen.nombreValido(nombre)) {
            throw new RecursoNoEncontradoException("no hay una imagen '" + nombre + "'");
        }
        return almacen.leer(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("no hay una imagen '" + nombre + "'"));
    }

    /** True si la imagen está referenciada por contenido publicado o una versión conservada. */
    public boolean esPublicada(String nombre) {
        return referencias.esPublicada(nombre);
    }

    /** Invalida la lista de imágenes publicadas tras publicar o despublicar. */
    public void invalidarCachePublicadas() {
        referencias.invalidar();
    }

    /**
     * Borra de Silo las imágenes retiradas que ya no referencia nadie. Si Silo
     * falla, se registra y la limpieza diaria las recogerá después.
     */
    public void eliminarSiHuerfanas(Set<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return;
        }
        Set<String> enUso = referencias.enUso();
        for (String nombre : nombres) {
            if (!FormatoImagen.nombreValido(nombre) || enUso.contains(nombre)) {
                continue;
            }
            try {
                almacen.eliminar(nombre);
            } catch (AlmacenNoDisponibleException e) {
                LOG.warn("No se pudo borrar la imagen retirada '{}': {}", nombre, e.getMessage());
            }
        }
    }

    /** Imagen normalizada que se guarda. */
    private record Normalizada(byte[] datos, FormatoImagen formato) {
    }

    /** Decodifica, aplica la orientación y vuelve a codificar sin metadatos. */
    private Normalizada normalizar(byte[] datos, FormatoImagen entrada) {
        int orientacion = orientacion(datos);
        BufferedImage original = leer(datos);
        BufferedImage imagen = orientar(original, orientacion);
        FormatoImagen salida = formatoSalida(entrada, imagen);
        byte[] limpios = codificar(imagen, salida);
        return new Normalizada(limpios, salida);
    }

    /** Orientación EXIF, o 1 si no tiene o no se puede leer. */
    private static int orientacion(byte[] datos) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(datos));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
                return exif.getInt(ExifDirectoryBase.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | IOException | MetadataException | RuntimeException e) {
            // La orientación es opcional: si no se puede leer, se deja la imagen como está.
        }
        return 1;
    }

    /** Decodifica la primera imagen y rechaza animaciones y dimensiones excesivas. */
    private static BufferedImage leer(byte[] datos) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            if (entrada == null) {
                throw new ImagenInvalidaException("No se pudo leer la imagen.");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new ImagenInvalidaException("Solo se aceptan imágenes png, jpeg, webp o gif.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, false, true);
                if (lector.getNumImages(true) > 1) {
                    throw new ImagenInvalidaException("No se aceptan imágenes animadas.");
                }
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if ((long) ancho * alto > MAX_PIXELES) {
                    throw new ImagenInvalidaException("La imagen tiene demasiados píxeles.");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new ImagenInvalidaException("No se pudo leer la imagen.");
        }
    }

    /** Aplica la orientación EXIF a la imagen. */
    private static BufferedImage orientar(BufferedImage original, int orientacion) {
        if (orientacion <= 1 || orientacion > 8) {
            return original;
        }
        int ancho = original.getWidth();
        int alto = original.getHeight();
        boolean intercambia = orientacion >= 5;
        int nuevoAncho = intercambia ? alto : ancho;
        int nuevoAlto = intercambia ? ancho : alto;
        int tipo = original.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage salida = new BufferedImage(nuevoAncho, nuevoAlto, tipo);
        Graphics2D g = salida.createGraphics();
        try {
            switch (orientacion) {
                case 2 -> {
                    g.translate(ancho, 0);
                    g.scale(-1, 1);
                }
                case 3 -> {
                    g.translate(ancho, alto);
                    g.rotate(Math.PI);
                }
                case 4 -> {
                    g.translate(0, alto);
                    g.scale(1, -1);
                }
                case 5 -> {
                    g.rotate(Math.PI / 2);
                    g.scale(1, -1);
                }
                case 6 -> {
                    g.translate(alto, 0);
                    g.rotate(Math.PI / 2);
                }
                case 7 -> {
                    g.translate(alto, ancho);
                    g.rotate(Math.PI / 2);
                    g.scale(-1, 1);
                }
                case 8 -> {
                    g.translate(0, ancho);
                    g.rotate(3 * Math.PI / 2);
                }
                default -> {
                }
            }
            g.drawImage(original, 0, 0, null);
        } finally {
            g.dispose();
        }
        return salida;
    }

    /**
     * Formato de salida: JPEG y PNG se mantienen; WebP sin transparencia pasa a
     * JPEG, y WebP con transparencia o GIF a PNG.
     */
    private static FormatoImagen formatoSalida(FormatoImagen entrada, BufferedImage imagen) {
        return switch (entrada.extension()) {
            case "jpg", "png" -> entrada;
            case "webp" -> imagen.getColorModel().hasAlpha()
                    ? new FormatoImagen("png", "image/png")
                    : new FormatoImagen("jpg", "image/jpeg");
            default -> new FormatoImagen("png", "image/png");
        };
    }

    /** Escribe la imagen sin metadatos. */
    private static byte[] codificar(BufferedImage imagen, FormatoImagen formato) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            if ("jpg".equals(formato.extension())) {
                escribirJpeg(imagen, salida);
            } else if (!ImageIO.write(imagen, "png", salida)) {
                throw new ImagenInvalidaException("No se pudo procesar la imagen.");
            }
            if (salida.size() == 0) {
                throw new ImagenInvalidaException("No se pudo procesar la imagen.");
            }
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ImagenInvalidaException("No se pudo procesar la imagen.");
        }
    }

    /** JPEG con calidad razonable y sin metadatos. */
    private static void escribirJpeg(BufferedImage imagen, ByteArrayOutputStream salida) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(0.9f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(ios);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }

    private static String megas(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
    }
}
