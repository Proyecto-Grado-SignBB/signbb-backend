package org.signbb.backend.media;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Almacén de imágenes en Silo (API compatible con S3).
 *
 * <p>
 * El cliente y el bucket se preparan la primera vez que se usan, no al
 * arrancar: así el backend (y las pruebas, y el CI) arrancan aunque Silo no
 * esté levantado, y solo falla subir o ver imágenes.
 */
@Component
public class AlmacenImagenesS3 implements AlmacenImagenes {

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;

    private S3Client cliente;
    private boolean bucketListo;

    /** Lee la configuración {@code signbb.media.*}. */
    public AlmacenImagenesS3(
            @Value("${signbb.media.endpoint:http://localhost:9000}") String endpoint,
            @Value("${signbb.media.access-key:silo-admin}") String accessKey,
            @Value("${signbb.media.secret-key:ejemplo123}") String secretKey,
            @Value("${signbb.media.bucket:signbb-imagenes}") String bucket) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    @Override
    public void guardar(String nombre, byte[] datos, String tipo) {
        try {
            S3Client s3 = listo();
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(nombre).contentType(tipo).build(),
                    RequestBody.fromBytes(datos));
        } catch (RuntimeException ex) {
            throw new AlmacenNoDisponibleException("No se pudo guardar la imagen en Silo", ex);
        }
    }

    @Override
    public Optional<Imagen> leer(String nombre) {
        try {
            S3Client s3 = listo();
            ResponseBytes<GetObjectResponse> respuesta = s3
                    .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(nombre).build());
            return Optional.of(new Imagen(respuesta.asByteArray(), FormatoImagen.tipoDe(nombre)));
        } catch (NoSuchKeyException | NoSuchBucketException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            throw new AlmacenNoDisponibleException("No se pudo leer la imagen de Silo", ex);
        }
    }

    @Override
    public List<Guardada> listar() {
        try {
            S3Client s3 = listo();
            List<Guardada> guardadas = new ArrayList<>();
            s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).build()).contents()
                    .forEach(o -> guardadas.add(new Guardada(o.key(), o.lastModified())));
            return guardadas;
        } catch (RuntimeException ex) {
            throw new AlmacenNoDisponibleException("No se pudo listar las imágenes de Silo", ex);
        }
    }

    @Override
    public void eliminar(String nombre) {
        try {
            S3Client s3 = listo();
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(nombre).build());
        } catch (RuntimeException ex) {
            throw new AlmacenNoDisponibleException("No se pudo borrar la imagen de Silo", ex);
        }
    }

    /** Cliente con el bucket ya creado; se prepara en el primer uso. */
    private synchronized S3Client listo() {
        if (cliente == null) {
            cliente = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .forcePathStyle(true)
                    // Silo/MinIO no necesita los checksums extra que el SDK nuevo agrega por defecto
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                    .build();
        }
        if (!bucketListo) {
            try {
                cliente.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException ex) {
                cliente.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    cliente.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                } else {
                    throw ex;
                }
            }
            bucketListo = true;
        }
        return cliente;
    }
}
