package org.signbb.backend.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.signbb.backend.config.RecursoNoEncontradoException;

/** Tests de {@link ServicioImagenes}: formato, tamaño y nombre, con un almacén en memoria. */
class ServicioImagenesTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 };
    private static final byte[] GIF = { 'G', 'I', 'F', '8', '9', 'a', 0, 0 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P' };

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
        };
        servicio = new ServicioImagenes(almacen, 1024);
    }

    @Test
    void guardaLosCuatroFormatosConNombreUuidYSuExtension() {
        assertTrue(servicio.subir(PNG).endsWith(".png"));
        assertTrue(servicio.subir(JPEG).endsWith(".jpg"));
        assertTrue(servicio.subir(GIF).endsWith(".gif"));
        String webp = servicio.subir(WEBP);
        assertTrue(webp.endsWith(".webp"));
        assertTrue(FormatoImagen.nombreValido(webp));
        assertEquals("image/webp", guardadas.get(webp).tipo());
        assertEquals(4, guardadas.size());
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
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        assertThrows(ImagenInvalidaException.class, () -> servicio.subir(grande));
        assertTrue(guardadas.isEmpty());
    }

    @Test
    void obtenerDevuelveLoGuardadoYRechazaNombresRaros() {
        String nombre = servicio.subir(PNG);

        assertArrayEquals(PNG, servicio.obtener(nombre).datos());
        assertEquals("image/png", servicio.obtener(nombre).tipo());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener("../etc/passwd"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.obtener("11111111-1111-1111-1111-111111111111.png"));
    }
}
