package com.marketplace.repository;

import com.marketplace.model.Operation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    // Desvincula el producto de todas sus operaciones antes de eliminarlo,
    // para no violar la restricción de clave foránea y preservar el historial.
    @Modifying
    @Query("UPDATE Operation o SET o.product = null WHERE o.product.id = :productId")
    void clearProductFromOperations(@Param("productId") Long productId);
    List<Operation> findByUploadId(Long uploadId);

    @Query("SELECT o FROM Operation o WHERE o.upload.id = :uploadId ORDER BY o.invoiceDate ASC")
    List<Operation> findByUploadIdOrdered(@Param("uploadId") Long uploadId);

    @Query("SELECT o FROM Operation o JOIN FETCH o.product ORDER BY o.invoiceDate DESC")
    List<Operation> findAllWithProduct();

    List<Operation> findByProductId(Long productId);

    // Elimina todas las operaciones asociadas a un upload (al borrar un ZIP del historial).
    @Modifying
    @Query("DELETE FROM Operation o WHERE o.upload.id = :uploadId")
    void deleteByUploadId(@Param("uploadId") Long uploadId);

    /**
     * Agrega la cantidad vendida por producto y por upload en una sola query, para
     * construir la serie de demanda histórica del predictor sin N+1.
     * Resultado: [sku (String), uploadId (Long), sumQuantity (Long)]
     */
    @Query("SELECT o.product.sku, o.upload.id, SUM(o.quantity) " +
           "FROM Operation o " +
           "WHERE o.product IS NOT NULL AND o.upload IS NOT NULL AND o.quantity IS NOT NULL " +
           "GROUP BY o.product.sku, o.upload.id")
    List<Object[]> sumQuantityBySkuAndUpload();

    // Busca operaciones donde el SKU original (alias) ya fue vinculado manualmente a un
    // producto. Se usa para resolver automáticamente el mismo SKU en cargas futuras.
    List<Operation> findByPendingSkuAndProductIsNotNull(String pendingSku);

    // Busca operaciones huérfanas (sin producto) con un pendingSku dado, en cualquier upload.
    // Se usa para reparar retroactivamente ops de cargas anteriores cuando se configura el mismo SKU.
    List<Operation> findByPendingSkuAndProductIsNull(String pendingSku);

}
