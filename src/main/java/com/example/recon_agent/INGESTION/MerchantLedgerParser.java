package com.example.recon_agent.INGESTION;

import com.example.recon_agent.ENUM.SourceSystem;
import com.example.recon_agent.MODEL.Transaction;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class MerchantLedgerParser implements SourceParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public List<Transaction> parse(Path csvFile) throws IOException {
        List<Transaction> transactions = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(csvFile.toFile()))) {
            String[] row;
            boolean header = true;
            while ((row = reader.readNext()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                // row: [order_id, txn_ref, amount, order_date, status]
                String rawRef = row[1];
                BigDecimal amount = new BigDecimal(row[2]);
                LocalDate date = LocalDate.parse(row[3], DATE_FORMAT);

                Transaction txn = Transaction.builder()
                        .sourceSystem(SourceSystem.MERCHANT_LEDGER)
                        .externalRef(normalize(rawRef))
                        .amount(amount)
                        .transactionDate(date)
                        .rawRecord(String.join(",", row))
                        .consumed(false)
                        .build();

                transactions.add(txn);
            }
        } catch (CsvValidationException e) {
            throw new IOException("Malformed CSV row in " + csvFile, e);
        }
        return transactions;
    }

    private String normalize(String ref) {
        return ref == null ? null : ref.trim().toUpperCase();
    }
}