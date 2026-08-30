package com.example.recon_agent.ENUM;

public enum ExceptionCategory {
    /*
    why a record became unmatch. rule based classification
    these in automatically; unexplained is the residual that gets sent to the llm
    for a plain - english explanation
     */


    AMOUNT_MISMATCH,  // changes due to the fee/tax/refund
    TIMING, //outside the settlement tolerance window
    MISSING, // present in 1 source absent in the others
    DUPLICATE, // same ref appears more than once in a source
    UNEXPLAINED // dosent fit in a clean rule - hence LLM reasoning is required
}


