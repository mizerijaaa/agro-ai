package com.uiktp.agro.repo;

import com.uiktp.agro.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByParcelUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("DELETE FROM Recommendation r WHERE r.parcel.id = :parcelId")
    void deleteAllByParcelId(@Param("parcelId") Long parcelId);
}
