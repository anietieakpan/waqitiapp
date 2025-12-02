package com.waqiti.tax.repository;

import com.waqiti.tax.domain.TaxForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxFormRepository extends JpaRepository<TaxForm, UUID> {
    
    /**
     * Find all forms for a tax return
     */
    List<TaxForm> findByTaxReturnIdOrderByCreatedAtDesc(UUID taxReturnId);
    
    /**
     * Find forms by tax return and type
     */
    List<TaxForm> findByTaxReturnIdAndFormType(UUID taxReturnId, TaxForm.FormType formType);
    
    /**
     * Find current (final) version of a form
     */
    Optional<TaxForm> findByTaxReturnIdAndFormTypeAndIsFinalTrue(UUID taxReturnId, 
                                                               TaxForm.FormType formType);
    
    /**
     * Find forms by status
     */
    List<TaxForm> findByStatus(TaxForm.FormStatus status);
    
    /**
     * Find forms by tax year
     */
    List<TaxForm> findByTaxYear(Integer taxYear);
    
    /**
     * Find forms with validation errors
     */
    @Query("SELECT tf FROM TaxForm tf WHERE tf.validationErrors IS NOT NULL AND tf.validationErrors <> ''")
    List<TaxForm> findWithValidationErrors();
    
    /**
     * Find submitted forms
     */
    List<TaxForm> findBySubmittedAtIsNotNull();
    
    /**
     * Find forms by form number
     */
    List<TaxForm> findByFormNumber(String formNumber);
    
    /**
     * Find latest version of forms for a tax return
     */
    @Query("SELECT tf FROM TaxForm tf WHERE tf.taxReturn.id = :taxReturnId AND tf.isFinal = true")
    List<TaxForm> findFinalVersions(@Param("taxReturnId") UUID taxReturnId);
    
    /**
     * Count forms by type
     */
    @Query("SELECT tf.formType, COUNT(tf) FROM TaxForm tf GROUP BY tf.formType")
    List<Object[]> countByFormType();
    
    /**
     * Find forms pending validation
     */
    @Query("SELECT tf FROM TaxForm tf WHERE tf.status = 'COMPLETED' AND (tf.validationErrors IS NULL OR tf.validationErrors = '')")
    List<TaxForm> findPendingValidation();
    
    /**
     * Find all form versions for a specific type and tax return
     */
    @Query("SELECT tf FROM TaxForm tf WHERE tf.taxReturn.id = :taxReturnId AND tf.formType = :formType ORDER BY tf.sequenceNumber DESC")
    List<TaxForm> findAllVersions(@Param("taxReturnId") UUID taxReturnId, 
                                 @Param("formType") TaxForm.FormType formType);
    
    /**
     * Check if form exists for tax return
     */
    boolean existsByTaxReturnIdAndFormType(UUID taxReturnId, TaxForm.FormType formType);
    
    /**
     * Find forms needing PDF generation
     */
    @Query("SELECT tf FROM TaxForm tf WHERE tf.status = 'VALIDATED' AND tf.pdfPath IS NULL")
    List<TaxForm> findNeedingPdfGeneration();
    
    /**
     * Get form statistics for a tax return
     */
    @Query("SELECT " +
           "COUNT(tf) as totalForms, " +
           "COUNT(CASE WHEN tf.status = 'COMPLETED' THEN 1 END) as completedForms, " +
           "COUNT(CASE WHEN tf.isFinal = true THEN 1 END) as finalForms, " +
           "COUNT(CASE WHEN tf.validationErrors IS NOT NULL AND tf.validationErrors <> '' THEN 1 END) as formsWithErrors " +
           "FROM TaxForm tf WHERE tf.taxReturn.id = :taxReturnId")
    Object[] getFormStatistics(@Param("taxReturnId") UUID taxReturnId);
}