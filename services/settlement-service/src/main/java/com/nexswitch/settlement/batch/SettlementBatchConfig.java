package com.nexswitch.settlement.batch;

import com.nexswitch.domain.model.Transaction;
import com.nexswitch.domain.model.TransactionStatus;
import com.nexswitch.domain.port.outbound.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

// LEARN: SpringBatch — Job → Step; the JobRepository stores execution state in the DB so
//        restarts skip already-processed items. Tasklet is the simplest step type — executes
//        once and returns FINISHED; use Chunk-oriented steps when reading large datasets.
@Configuration
public class SettlementBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchConfig.class);

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               TransactionRepository transactionRepository) {
        return new StepBuilder("settlementStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    List<Transaction> pending = transactionRepository.findByStatus(TransactionStatus.CAPTURED);
                    log.info("settlement.batch.start captured_count={}", pending.size());
                    for (Transaction txn : pending) {
                        Transaction settled = txn.withStatus(TransactionStatus.SETTLEMENT_PENDING);
                        transactionRepository.save(settled);
                        log.info("settlement.batch.queued transactionId={} merchantId={}",
                                txn.id(), txn.merchantId().value());
                    }
                    log.info("settlement.batch.done queued={}", pending.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
