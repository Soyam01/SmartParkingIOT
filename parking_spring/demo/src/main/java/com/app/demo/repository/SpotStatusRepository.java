package com.app.demo.repository;

import com.app.demo.model.SpotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpotStatusRepository extends JpaRepository<SpotStatus, String> {
    Optional<SpotStatus> findBySpotNumber(String s);
    List<SpotStatus> findAllByOrderBySpotNumberAsc();
}
