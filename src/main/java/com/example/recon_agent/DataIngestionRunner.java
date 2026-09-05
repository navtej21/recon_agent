package com.example.recon_agent;


import com.example.recon_agent.AI_LAYER.ClaudeApiClient;
import com.example.recon_agent.REPO.TransactionRepository;
import com.example.recon_agent.SERVICE.IngestionService;
import com.example.recon_agent.SERVICE.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.Map;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataIngestionRunner implements CommandLineRunner{

    private final IngestionService ingestionService;
    private final MatchingService matchingService;
    private  final ClaudeApiClient claudeApiClient;
    private final TransactionRepository transactionRepository;

    @Override
    public void run(String... args) throws Exception {
        if (transactionRepository.count() > 0) {
            log.info("Transactions already present ({} rows) - skipping auto-ingestion", transactionRepository.count());
            return;
        }

        int totalIngested = ingestionService.ingestAll(Path.of("data/synthetic"));
        log.info("Startup ingestion complete: {} transactions loaded", totalIngested);

        Map<String, Integer> matchResults = matchingService.runFullPipeline();
        log.info("Matching pipeline results: {}", matchResults);
    }
}