package com.individual.messenger.controller;
import com.individual.messenger.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final OperationsService operations;private final ElasticSearchService search;
    public OperationsController(OperationsService operations,ElasticSearchService search){this.operations=operations;this.search=search;}
    @GetMapping("/audit") public Map<String,Object> audit(){return operations.audit();}
    @GetMapping("/search") public Map<String,Object> search(){return search.status();}
    @PostMapping("/search/reindex") @ResponseStatus(HttpStatus.ACCEPTED)
    public void reindex(){search.requestReindex();}
}
