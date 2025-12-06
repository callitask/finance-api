package com.treishvaam.financeapi.apistatus;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiFetchStatusRepository extends JpaRepository<ApiFetchStatus, Long> {
  @Query(
      "SELECT s FROM ApiFetchStatus s WHERE s.id IN (SELECT MAX(s2.id) FROM ApiFetchStatus s2 GROUP BY s2.apiName)")
  List<ApiFetchStatus> findLatestStatusForEachApi();

  List<ApiFetchStatus> findAllByOrderByLastFetchTimeDesc();

  List<ApiFetchStatus> findByApiNameInOrderByLastFetchTimeDesc(List<String> apiNames);
}
