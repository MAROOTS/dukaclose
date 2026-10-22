package com.dukaclose.api.business;

import com.dukaclose.api.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessMembershipRepository extends JpaRepository<BusinessMembership, UUID> {
    List<BusinessMembership> findByUserId(UUID userId);
    List<BusinessMembership> findByUserIdAndStatus(UUID userId, RecordStatus status);

}
