package org.signbb.backend.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;

/** Tests de {@link ServicioImagenes}: formato, tamaño, nombre, EXIF y orientación. */
class ServicioImagenesTest {

    private final Map<String, AlmacenImagenes.Imagen> guardadas = new HashMap<>();
    private ServicioImagenes servicio;

    @BeforeEach
    void preparar() {
        AlmacenImagenes almacen = new AlmacenImagenes() {
            @Override
            public void guardar(String nombre, byte[] datos, String tipo) {
                guardadas.put(nombre, new Imagen(datos, tipo));
            }

            @Override
            public Optional<Imagen> leer(String nombre) {
                return Optional.ofNullable(guardadas.get(nombre));
            }

            @Override
            public List<Guardada> listar() {
                return guardadas.keySet().stream().map(n -> new Guardada(n, Instant.EPOCH)).toList();
            }

            @Override
            public void eliminar(String nombre) {
                guardadas.remove(nombre);
            }
        };
        servicio = new ServicioImagenes(almacen, mock(ReferenciasImagenes.class), 1024);
    }

    @Test
    void guardaLosCuatroFormatosSinMetadatos() throws IOException {
        assertTrue(servicio.subir(recurso("foto.jpg")).endsWith(".jpg"));
        assertTrue(servicio.subir(recurso("foto.png")).endsWith(".png"));
        assertTrue(servicio.subir(recurso("foto.gif")).endsWith(".png"));
        assertTrue(servicio.subir(recurso("foto.webp")).endsWith(".jpg"));
        assertTrue(servicio.subir(recurso("foto-alpha.webp")).endsWith(".png"));
        assertEquals(5, guardadas.size());
    }

    private void assertOrientacion(String archivo, Color arriba, Color abajo) throws IOException {
        String nombre = servicio.subir(recurso(archivo));
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(guardadas.get(nombre).datos()));

        assertEquals(2, imagen.getWidth());
        assertEquals(4, imagen.getHeight());
        assertColor(arriba, new Color(imagen.getRGB(1, 0)), archivo + " arriba");
        assertColor(abajo, new Color(imagen.getRGB(1, 3)), archivo + " abajo");
    }

    private static void assertColor(Color esperado, Color real, String donde) {
        assertTrue(Math.abs(esperado.getRed() - real.getRed()) < 60
                && Math.abs(esperado.getGreen() - real.getGreen()) < 60
                && Math.abs(esperado.getBlue() - real.getBlue()) < 60,
                donde + ": esperaba " + esperado + " y fue " + real);
    }

    @Test
    void rechazaLoQueNoEsUnaImagenPermitida() {
        assertThrows(ImagenInvalidaException.class, () -> servicio.subir("<svg></svg>".getBytes()));
        assertThrows(ImagenInvalidaException.class, () -> servicio.subir(new byte[] { 'B', 'M', 0, 0 }));
        assertThrows(ImagenInvalidaException.class, () -> servicio.subir(new byte[0]));
        assertThrows(ImagenInvalidaException.class, () -> servicio.subir(null));
        assertTrue(guardadas.isEmpty());
    }

    @Test
    void rechazaLoQuePesaMasDelTope() {
        byte[] grande = new byte[1025];
        System.arraycopy(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }, 0, grande, 0, 8);

        assertThrows(ImagenInvalidaException.class, () -> servicio.subir(grande));
        assertTrue(guardadas.isEmpty());
    }

    @Test
    void obtenerDevuelveLoGuardadoYRechazaNombresRaros() throws IOException {
        String nombre = servicio.subir(recurso("foto.png"));

        assertNotNull(servicio.obtener(nombre));
        assertEquals("image/png", servicio.obtener(nombre).tipo());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener("../etc/passwd"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtener("11111111-1111-1111-1111-111111111111.png"));
    }

    @Test
    void nombresEnEncuentraLasImagenesDentroDeUnTextoSinImportarDondeEsten() {
        String a = "11111111-2222-3333-4444-555555555555.png";
        String b = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.webp";
        String json = "{\"imagenes\":[\"" + a + "\"],\"src\":[\"/api/imagenes/" + b + "\"],\"otro\":\"foto.png\"}";

        assertEquals(java.util.Set.of(a, b), FormatoImagen.nombresEn(json));
        assertTrue(FormatoImagen.nombresEn("sin imágenes").isEmpty());
        assertTrue(FormatoImagen.nombresEn(null).isEmpty());
    }

    private static byte[] recurso(String nombre) throws IOException {
        try (InputStream entrada = ServicioImagenesTest.class.getResourceAsStream("/imagenes/" + nombre)) {
            if (entrada == null) {
                throw new IllegalStateException("falta el recurso " + nombre);
            }
            return entrada.readAllBytes();
        }
    }
}
