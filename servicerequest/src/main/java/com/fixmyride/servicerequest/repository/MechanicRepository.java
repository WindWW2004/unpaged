package com.fixmyride.servicerequest.repository;

import com.fixmyride.servicerequest.domain.Mechanic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MechanicRepository extends JpaRepository<Mechanic, Long> {
    List<Mechanic> findAllByOrderByNameAsc();
}
