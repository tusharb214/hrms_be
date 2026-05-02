package com.sitegenius.hrms.service;

import com.sitegenius.hrms.entity.LeaveTypeConfig;
import com.sitegenius.hrms.exception.BadRequestException;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.repository.LeaveTypeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeConfigService {

    private final LeaveTypeConfigRepository repo;

    public List<LeaveTypeConfig> getAll()    { return repo.findAll(); }
    public List<LeaveTypeConfig> getActive() { return repo.findByActiveTrue(); }

    public LeaveTypeConfig getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + id));
    }

    public LeaveTypeConfig create(LeaveTypeConfig req) {
        if (repo.existsByTypeCode(req.getTypeCode().toUpperCase()))
            throw new BadRequestException("Leave type code already exists: " + req.getTypeCode());
        req.setTypeCode(req.getTypeCode().toUpperCase());
        return repo.save(req);
    }

    public LeaveTypeConfig update(Long id, LeaveTypeConfig req) {
        LeaveTypeConfig existing = getById(id);
        existing.setTypeName(req.getTypeName());
        existing.setAllowedPerYear(req.getAllowedPerYear());
        existing.setHalfDayAllowed(req.isHalfDayAllowed());
        existing.setActive(req.isActive());
        existing.setDescription(req.getDescription());
        existing.setRequiresDocument(req.isRequiresDocument());
        return repo.save(existing);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}