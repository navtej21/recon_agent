package com.example.recon_agent.INGESTION;


import com.example.recon_agent.MODEL.Transaction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

// i will provide a csv path . this will provide me the normalized transaction objs. that all 3 source-specific parsers implement
public interface SourceParser {
    List<Transaction> parse(Path csvFile) throws IOException;
}
