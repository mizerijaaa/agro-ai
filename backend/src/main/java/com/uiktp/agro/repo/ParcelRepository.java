package com.uiktp.agro.repo;

import com.uiktp.agro.model.AppUser;
import com.uiktp.agro.model.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    List<Parcel> findByUser(AppUser user);

    /** Последно внесена парцела = највисок id (auto-increment). */
    Optional<Parcel> findTopByUserOrderByIdDesc(AppUser user);
}
