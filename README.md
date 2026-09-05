# Reconciliation Agent

**Razorpay AI Buildathon — Track 4: AI Finance Controller**

An AI-assisted agent that reconciles payment transactions across three independent sources — Razorpay settlement reports, bank statements, and merchant ledgers — matching confidently where possible, and honestly explaining what it can't resolve.

---

## The Problem

When a customer pays a merchant through Razorpay, that one payment leaves a trail in three separate systems:

1. **Razorpay's settlement report** — what Razorpay processed and paid out
2. **The bank statement** — what actually landed in the merchant's account
3. **The merchant's internal order ledger** — what the merchant's own system recorded as paid

These three records rarely agree perfectly. Fees get deducted, settlements lag by a day or two, syncs fail, webhooks fire twice. Today, finance teams reconcile this by hand — cross-checking spreadsheets row by row. It's slow, error-prone, and doesn't scale.

This project automates that cross-check — and just as importantly, is honest about what it *can't* resolve, instead of hiding failures in an unexplained pile.

---

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Razorpay   │     │    Bank     │     │   Merchant  │
│  Settlement │     │  Statement  │     │Order Ledger │
│    (CSV)    │     │    (CSV)    │     │    (CSV)    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       └───────────────────┼───────────────────┘
                            ▼
                 ┌─────────────────────┐
                 │  Ingestion Layer    │  → normalizes 3 different
                 │  (3 source parsers) │     schemas into one shape
                 └──────────┬──────────┘
                            ▼
                 ┌─────────────────────────────────┐
                 │       Matching Engine            │
                 │  1. Duplicate detection          │
                 │  2. Exact match                  │
                 │  3. Fuzzy match (amount + date)  │
                 │  4. Residual classification      │
                 └──────────┬──────────────────────┘
                            ▼
              ┌─────────────┴─────────────┐
              ▼                           ▼
    ┌──────────────────┐        ┌──────────────────┐
    │  Matched Records   │       │  Exceptions        │
    │  (EXACT/FUZZY)     │       │  (DUPLICATE/       │
    └──────────────────┘        │   MISSING/          │
                                  │   UNEXPLAINED)      │
                                  └─────────┬──────────┘
                                            ▼
                                 ┌─────────────────────┐
                                 │  Claude API           │  → only for
                                 │  (exception reasoning)│    UNEXPLAINED
                                 └──────────┬───────────┘
                                            ▼
                                 ┌─────────────────────┐
                                 │   Audit Trail Log     │  → every decision,
                                 │   (append-only)        │    always written
                                 └──────────┬───────────┘
                                            ▼
                                 ┌─────────────────────┐
                                 │   REST API             │
                                 └──────────┬───────────┘
                                            ▼
                                 ┌─────────────────────┐
                                 │   Dashboard (HTML)    │
                                 └─────────────────────┘
```

---

## Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Data JPA |
| Database | MySQL (H2 supported for zero-setup local dev) |
| CSV Parsing | OpenCSV |
| AI Layer | Anthropic Claude API (Messages endpoint) |
| Dashboard | Single-file HTML/JS, no build step |
| Data | Synthetic CSVs with a known ground-truth answer key |

---

## The Matching Engine — How It Works

The engine runs **4 ordered passes**. Order matters: each pass narrows the pool for the next.

### 1. Duplicate Detection
Groups all unconsumed transactions by reference across **all 3 sources**. If any single source has more than one row for a reference, the **entire cross-source group** is pulled out together and flagged — not just the duplicated rows — so the clean rows from other sources don't get misclassified downstream.

### 2. Exact Match
A hashmap lookup on the transaction reference. Requires exactly one candidate per source and **identical amounts** across all three. No ambiguity, no scoring — a deterministic fact. (Amount agreement is the bar for "exact," not date — a payment settling a day or two late is normal behavior, not a discrepancy.)

### 3. Fuzzy Match
For what's left, scores amount closeness (weighted 0.7) and date closeness (weighted 0.3) against a 3-day tolerance window. Takes the **weaker of the two source comparisons** as the overall confidence — a "weakest link" principle that avoids overstating certainty. Tiered into `FUZZY_HIGH` (≥0.85) and `FUZZY_LOW` (≥0.60).

### 4. Residual Classification
Whatever remains is grouped by reference and classified:
- **MISSING** — Razorpay has a record, but Bank or Ledger doesn't (sync failure)
- **UNEXPLAINED** — all three sources have a record, but disagree beyond fuzzy tolerance, with no clean rule explaining why → **this is the only category that calls Claude**, for a plain-English root-cause explanation and recommendation

**Design tradeoff, stated honestly:** the fuzzy pass uses greedy assignment — it takes the best available candidate at each step rather than solving a globally optimal bipartite match across all pairs. For this data volume, greedy is fast and correct; at much larger scale, an optimal assignment algorithm would be the next investment.

---

## Verified Results

On a 180-transaction synthetic batch (60 base transactions × 3 sources), with known, seeded ground truth:

| Metric | Value |
|---|---|
| Total transactions processed | 180 |
| Resolved with confidence (EXACT + FUZZY) | 54 (90%) |
| Exact matches | 48 |
| Fuzzy matches | 6 |
| Duplicate exceptions | 3 |
| Missing exceptions | 3 |
| Unexplained (unresolved after all rules) | 0 in seeded data — verified working via injected test case |
| Unconsumed transactions after full pipeline | 0 |

Every transaction is accounted for — no silent drops, no double-counting.

---

## The AI Layer, Demonstrated

Claude is called **only** for the `UNEXPLAINED` category — never for `MISSING` or `DUPLICATE`, which already have a complete rule-based explanation. This keeps API usage minimal and reserves the LLM for genuinely ambiguous cases that need reasoning, not pattern-matching.

**Example — a real transaction, a real API call:**

> Razorpay settlement: ₹5,000.00 on 2026-08-15. Bank credit: ₹3,200.00 on 2026-08-28 (13 days later). Ledger: ₹5,000.00, matching Razorpay.

**Claude's response:**
> *"The bank statement shows a reduced amount of 3200.00 compared to the original settlement amount of 5000.00, with a 13-day delay in crediting. This indicates that Razorpay deducted fees, chargebacks, refunds, or adjustments totaling 1800.00 (36% of the settlement) before the funds reached the bank account... Recommendation: Review the Razorpay dashboard for this settlement to identify all deductions..."*

This is the actual differentiator over rules-only reconciliation tools: instead of an unexplained line item, the finance user gets a specific, actionable diagnosis.

---

## API Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/reconciliation/summary` | Match rate, counts by type and category |
| `GET` | `/reconciliation/exceptions?category=` | Filterable exception list with full transaction detail |
| `GET` | `/reconciliation/audit-trail/{ref}` | Complete decision history for one transaction reference |
| `POST` | `/reconciliation/run` | Manually trigger the pipeline on unconsumed data |

---

## Running It Locally

```bash
# 1. Set your Anthropic API key
export ANTHROPIC_API_KEY=your_key_here

# 2. Generate synthetic test data
mvn compile exec:java -Dexec.mainClass=com.reconagent.datagen.SyntheticDataGenerator

# 3. Run the application (auto-ingests + runs the pipeline on first boot)
mvn spring-boot:run

# 4. Open the dashboard
open dashboard/index.html
```

---

## Project Structure

```
recon-agent/
├── src/main/java/com/reconagent/
│   ├── model/          # JPA entities: Transaction, MatchResult, ReconException, AuditLogEntry
│   ├── repository/     # Spring Data JPA repositories
│   ├── ingestion/       # CSV parsers + orchestration
│   ├── matching/        # The 4-pass matching engine
│   ├── llm/             # Claude API client
│   ├── api/              # REST controllers + DTOs
│   └── datagen/          # Synthetic data generator
├── dashboard/
│   └── index.html        # Single-file dashboard, no build step
└── data/synthetic/       # Generated CSVs + ground truth answer key
```

---

## Honest Limitations & What's Next

- **Greedy, not optimal, fuzzy matching.** Fine at this scale; a real production system handling batched settlements (one-to-many matches) would need a proper assignment algorithm.
- **Read-only today.** The system detects and explains, but doesn't act. The natural next step is closing the loop — auto-routing exceptions to the right team, or drafting the follow-up query to Razorpay support directly.
- **Small, controlled dataset.** Built on 180 synthetic transactions with known ground truth specifically to measure real accuracy rather than demonstrate one convincing example — the next step is validating against real (anonymized) settlement data at larger scale.
- **Minimal UI, by design.** Given the timeline, engineering effort went into the reconciliation logic and its correctness, not visual polish. The dashboard is a thin, functional layer over a real, working API.
