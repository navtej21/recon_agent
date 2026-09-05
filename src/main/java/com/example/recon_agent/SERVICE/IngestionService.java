package com.example.recon_agent.SERVICE;


import com.example.recon_agent.INGESTION.BankStatementParser;
import com.example.recon_agent.INGESTION.MerchantLedgerParser;
import com.example.recon_agent.INGESTION.RazorpaySettlementParser;
import com.example.recon_agent.MODEL.Transaction;
import com.example.recon_agent.REPO.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

// calling all the 3 parsers seperately in order for easy testing in case of commandlinerun
@Service
@AllArgsConstructor
@Slf4j
public class IngestionService {


    private final RazorpaySettlementParser razorParser;
    private final BankStatementParser bankParser;
    private final MerchantLedgerParser ledgerParser;
    private final TransactionRepository transactionRepository;




    public int ingestAll(Path sourceDirectory) throws IOException{

        List<Transaction> razorpayTxns = razorParser.parse(sourceDirectory.resolve("razorpay_settlement.csv"));
        List<Transaction> bankTxns = bankParser.parse(sourceDirectory.resolve("bank_statement.csv"));
        List<Transaction> ledgerTxns = ledgerParser.parse(sourceDirectory.resolve("merchant_ledger.csv"));

        log.info("Parsed {} Razorpay, {} bank, {} ledger transactions",
                razorpayTxns.size(), bankTxns.size(), ledgerTxns.size());

        transactionRepository.saveAll(razorpayTxns);
        transactionRepository.saveAll(bankTxns);
        transactionRepository.saveAll(ledgerTxns);

        int total = razorpayTxns.size() + bankTxns.size() + ledgerTxns.size();
        log.info("Persisted {} total transaction rows", total);
        return total;

    }
}
