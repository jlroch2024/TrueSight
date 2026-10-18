package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertHoldingRepository extends JpaRepository<AlertHolding, AlertHoldingId> {

    /** The holdings one alert reaches. */
    List<AlertHolding> findByAlertId(Long alertId);
}
