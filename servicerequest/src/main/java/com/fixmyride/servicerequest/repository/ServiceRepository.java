package com.fixmyride.servicerequest.repository;

import com.fixmyride.servicerequest.domain.ServiceJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface ServiceRepository<T extends ServiceJob> extends JpaRepository<T, Long> {
}
