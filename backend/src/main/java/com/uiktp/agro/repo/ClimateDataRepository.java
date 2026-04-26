package com.uiktp.agro.repo;

import com.uiktp.agro.model.ClimateData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClimateDataRepository extends JpaRepository<ClimateData, Long> {

    @Query("SELECT c FROM ClimateData c WHERE c.parcel.id = :parcelId AND c.createdAt >= :dayStart AND c.createdAt < :dayEnd ORDER BY c.createdAt DESC")
    List<ClimateData> findTodaysEntriesForParcel(
            @Param("parcelId") Long parcelId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            Pageable pageable
    );

    Optional<ClimateData> findFirstByParcelIdOrderByCreatedAtDesc(Long parcelId);

    @Modifying
    @Query("DELETE FROM ClimateData c WHERE c.parcel.id = :parcelId")
    void deleteAllByParcelId(@Param("parcelId") Long parcelId);
}
