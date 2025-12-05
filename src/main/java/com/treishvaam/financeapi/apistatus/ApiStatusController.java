package com.treishvaam.financeapi.apistatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/status")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ApiStatusController {

    @Autowired
    private ApiFetchStatusRepository apiFetchStatusRepository;

    @GetMapping
    public ResponseEntity<List<ApiFetchStatus>> getLatestApiStatuses() {
        return ResponseEntity.ok(apiFetchStatusRepository.findLatestStatusForEachApi());
    }

    @GetMapping("/history")
    public ResponseEntity<List<ApiFetchStatus>> getFullApiStatusHistory() {
        return ResponseEntity.ok(apiFetchStatusRepository.findAllByOrderByLastFetchTimeDesc());
    }
}