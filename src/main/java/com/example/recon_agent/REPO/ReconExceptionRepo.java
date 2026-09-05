package com.example.recon_agent.REPO;

import com.example.recon_agent.ENUM.ExceptionCategory;
import com.example.recon_agent.MODEL.ReconException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReconExceptionRepo extends JpaRepository<ReconException,Long>{

    // every exception in a given cateogry- powers the dashboard
    // filter by cateogry
    List<ReconException> findByCategory(ExceptionCategory category);

    // open exceptions only
    List<ReconException> findByStatus(String status);

}
