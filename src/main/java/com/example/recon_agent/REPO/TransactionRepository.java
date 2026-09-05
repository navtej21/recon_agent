package com.example.recon_agent.REPO;

import com.example.recon_agent.ENUM.SourceSystem;
import com.example.recon_agent.MODEL.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction,Long> {


    // All Transactions from a single source -used by the matching engine
    // to build its per-source hashmaps for the exact-match pass.
    List<Transaction> findBySourceSystem(SourceSystem sourceSystem);


    //search the transaction based on the externalRef
    List<Transaction> findByExternalRef(String externalRef);


    // narrowing our search with source system and teh dups
    List<Transaction> findBySourceSystemAndExternalRef(SourceSystem sourceSystem,String externalRef);

    // not yet claimed by a match result
    List<Transaction> findByConsumedFalse();
}
