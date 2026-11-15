package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.Mechanic;
import com.fixmyride.servicerequest.repository.MechanicRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MechanicDirectory {
    private final MechanicRepository mechanicRepository;

    public MechanicDirectory(MechanicRepository mechanicRepository) {
        this.mechanicRepository = mechanicRepository;
    }

    public List<Mechanic> availableMechanics() {
        return mechanicRepository.findAllByOrderByNameAsc().stream()
                .filter(Mechanic::isAvailable)
                .toList();
    }

    public Map<Long, Mechanic> mechanicsById() {
        return mechanicRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(Mechanic::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
    }
}
