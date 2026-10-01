package com.insurance.risk.service;

import com.insurance.risk.dto.request.RiskFactorConfigRequest;
import com.insurance.risk.entity.RiskFactorConfig;
import com.insurance.risk.repository.RiskFactorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskFactorConfigService {

    private final RiskFactorConfigRepository repository;

    public List<RiskFactorConfig> listForInsuranceType(String insuranceType) {
        return repository.findByInsuranceType(insuranceType.toUpperCase());
    }

    @Transactional
    public RiskFactorConfig create(RiskFactorConfigRequest req) {
        RiskFactorConfig f = RiskFactorConfig.builder()
                .insuranceType(req.getInsuranceType().toUpperCase())
                .factorType(req.getFactorType())
                .minValue(req.getMinValue())
                .maxValue(req.getMaxValue())
                .conditionValue(req.getConditionValue())
                .label(req.getLabel())
                .weight(req.getWeight())
                .active(req.isActive())
                .build();
        return repository.save(f);
    }

    @Transactional
    public RiskFactorConfig update(UUID id, RiskFactorConfigRequest req) {
        RiskFactorConfig f = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk factor not found: " + id));
        f.setInsuranceType(req.getInsuranceType().toUpperCase());
        f.setFactorType(req.getFactorType());
        f.setMinValue(req.getMinValue());
        f.setMaxValue(req.getMaxValue());
        f.setConditionValue(req.getConditionValue());
        f.setLabel(req.getLabel());
        f.setWeight(req.getWeight());
        f.setActive(req.isActive());
        return repository.save(f);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk factor not found: " + id);
        }
        repository.deleteById(id);
    }
}
