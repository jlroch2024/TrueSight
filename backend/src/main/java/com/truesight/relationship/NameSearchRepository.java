package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and saves when each company was last searched for by name. The id is the company's id. */
public interface NameSearchRepository extends JpaRepository<NameSearch, Long> {
}
