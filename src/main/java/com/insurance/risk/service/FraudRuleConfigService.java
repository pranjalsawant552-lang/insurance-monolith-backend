package com.insurance.risk.service;

import com.insurance.risk.dto.request.FraudRuleConfigRequest;
import com.insurance.risk.entity.FraudRuleConfig;
import com.insurance.risk.repository.FraudRuleConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FraudRuleConfigService {

    private final FraudRuleConfigRepository repository;

    public List<FraudRuleConfig> listAll() {
        return repository.findAll();
    }

    @Transactional
    public FraudRuleConfig update(UUID id, FraudRuleConfigRequest req) {
        FraudRuleConfig rule = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fraud rule not found: " + id));
        rule.setThresholdDays(req.getThresholdDays());
        rule.setWeight(req.getWeight());
        rule.setDescription(req.getDescription());
        rule.setActive(req.isActive());
        rule.setUpdatedAt(ZonedDateTime.now());
        return repository.save(rule);
    }
}
