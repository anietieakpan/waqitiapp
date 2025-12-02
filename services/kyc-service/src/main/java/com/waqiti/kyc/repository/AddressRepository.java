package com.waqiti.kyc.repository;

import com.waqiti.kyc.model.Address;
import com.waqiti.kyc.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByUserId(String userId);
    
    Optional<Address> findByUserIdAndAddressType(String userId, String addressType);
    
    Optional<Address> findByUserIdAndAddressLine1AndCityAndCountry(String userId, String addressLine1, 
                                                                  String city, String country);
    
    List<Address> findByUserIdAndVerificationStatus(String userId, VerificationStatus status);
    
    List<Address> findByCountry(String country);
    
    List<Address> findByVerificationStatus(VerificationStatus status);
    
    @Query("SELECT a FROM Address a WHERE a.userId = :userId AND a.addressType = 'PRIMARY'")
    Optional<Address> findPrimaryAddressByUserId(@Param("userId") String userId);
    
    @Query("SELECT a FROM Address a WHERE a.verificationScore >= :minScore")
    List<Address> findByVerificationScoreGreaterThanEqual(@Param("minScore") Integer minScore);
    
    @Query("SELECT COUNT(a) FROM Address a WHERE a.verificationStatus = :status")
    Long countByVerificationStatus(@Param("status") VerificationStatus status);
}