package com.example.recon_agent;


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

    @Override
    public void run(String... args) throws Exception {
        int rows=ingestionService.ingestAll(Path.of("data/synthetic"));
        Map<String,Integer> result=matchingService.runFullPipeline();
        System.out.println(result);
    }
}