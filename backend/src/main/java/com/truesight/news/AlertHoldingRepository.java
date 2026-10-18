package com.truesight.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AlertHoldingRepository extends JpaRepository<AlertHolding, AlertHoldingId> {

    /** The holdings one alert reaches. */
    List<AlertHolding> findByAlertId(Long alertId);

    /** The holdings reached by any of these alerts, loaded together for the Alerts page. */
    List<AlertHolding> findByAlertIdIn(Collection<Long> alertIds);
}
