package com.example.recon_agent.MODEL;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExceptionExplanation {
    private String likelyCause;
    private String recommendation;
}
