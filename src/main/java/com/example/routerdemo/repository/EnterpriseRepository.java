package com.example.routerdemo.repository;

import com.example.routerdemo.domain.entity.Enterprise;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EnterpriseRepository extends ReactiveCrudRepository<Enterprise, Long> {}
