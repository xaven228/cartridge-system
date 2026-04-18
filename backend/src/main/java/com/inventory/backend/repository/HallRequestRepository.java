package com.inventory.backend.repository;

import com.inventory.backend.entity.HallRequest;
import com.inventory.backend.entity.HallRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HallRequestRepository extends JpaRepository<HallRequest, Long> {
    List<HallRequest> findAllByOrderByRequestedAtDesc();

    List<HallRequest> findByRoomIdOrderByRequestedAtDesc(Long roomId);

    List<HallRequest> findByStatusOrderByRequestedAtDesc(HallRequestStatus status);

    List<HallRequest> findByStatusInOrderByRequestedAtDesc(Collection<HallRequestStatus> statuses);
}
