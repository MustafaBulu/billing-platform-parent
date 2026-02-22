package com.mustafabulu.billing.paymentservice.application;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyResolver;
import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordDocument;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApplicationService {

    private static final String OPERATION_CODE = "PAYMENT_PROCESS";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_COMPENSATED = "COMPENSATED";

    private final BankSoapGatewayMock bankSoapGatewayMock;
    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentApplicationService(BankSoapGatewayMock bankSoapGatewayMock,
                                     PaymentRecordRepository paymentRecordRepository) {
        this.bankSoapGatewayMock = bankSoapGatewayMock;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    @Transactional
    public PaymentResult process(ProcessPaymentRequest request) {
        String effectiveTenantId = TenantContextHolder.getTenantId().orElse(request.tenantId());
        String effectiveKey = IdempotencyKeyResolver.resolveCompositeKey(OPERATION_CODE)
                .orElse(effectiveTenantId + ":" + OPERATION_CODE + ":" + request.idempotencyKey());

        PaymentRecordDocument existing = paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                effectiveTenantId, OPERATION_CODE, effectiveKey).orElse(null);
        if (existing != null) {
            return toDomain(existing);
        }

        String providerReference = bankSoapGatewayMock.authorize(request.amount(), request.currency(), request.invoiceId());
        String status = providerReference.startsWith("APPROVED") ? STATUS_SUCCESS : STATUS_FAILED;

        PaymentRecordDocument document = new PaymentRecordDocument();
        document.setTenantId(effectiveTenantId);
        document.setOperationCode(OPERATION_CODE);
        document.setIdempotencyKey(effectiveKey);
        document.setTransactionId(UUID.randomUUID().toString());
        document.setInvoiceId(request.invoiceId());
        document.setAmount(request.amount());
        document.setCurrency(request.currency());
        document.setStatus(status);
        document.setProviderReference(providerReference);
        document.setProcessedAt(Instant.now());

        try {
            return toDomain(paymentRecordRepository.save(document));
        } catch (DuplicateKeyException duplicateKeyException) {
            return paymentRecordRepository.findByTenantIdAndOperationCodeAndIdempotencyKey(
                            effectiveTenantId, OPERATION_CODE, effectiveKey)
                    .map(this::toDomain)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    @Transactional
    public PaymentResult compensate(String tenantId, String idempotencyKey, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return new PaymentResult(
                    null, null, BigDecimal.ZERO, "", STATUS_FAILED, "MISSING_TRANSACTION_ID", Instant.now());
        }
        PaymentRecordDocument existing = paymentRecordRepository.findByTenantIdAndTransactionId(tenantId, transactionId)
                .orElse(null);
        if (existing == null) {
            return new PaymentResult(
                    transactionId, null, BigDecimal.ZERO, "", STATUS_FAILED, "PAYMENT_NOT_FOUND", Instant.now());
        }
        if (STATUS_COMPENSATED.equalsIgnoreCase(existing.getStatus())) {
            return toDomain(existing);
        }

        existing.setStatus(STATUS_COMPENSATED);
        existing.setProviderReference(STATUS_COMPENSATED + "-" + idempotencyKey);
        existing.setProcessedAt(Instant.now());
        return toDomain(paymentRecordRepository.save(existing));
    }

    private PaymentResult toDomain(PaymentRecordDocument document) {
        return new PaymentResult(
                document.getTransactionId(),
                document.getInvoiceId(),
                document.getAmount(),
                document.getCurrency(),
                document.getStatus(),
                document.getProviderReference(),
                document.getProcessedAt()
        );
    }
}
