package com.mustafabulu.billing.paymentservice.application;

import com.mustafabulu.billing.common.idempotency.IdempotencyKeyResolver;
import com.mustafabulu.billing.common.tenant.TenantContextHolder;
import com.mustafabulu.billing.paymentservice.api.dto.ProcessPaymentRequest;
import com.mustafabulu.billing.paymentservice.domain.PaymentResult;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordDocument;
import com.mustafabulu.billing.paymentservice.persistence.PaymentRecordRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PaymentApplicationService {

    private static final String OPERATION_CODE = "PAYMENT_PROCESS";

    private final BankSoapGatewayMock bankSoapGatewayMock;
    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentApplicationService(BankSoapGatewayMock bankSoapGatewayMock,
                                     PaymentRecordRepository paymentRecordRepository) {
        this.bankSoapGatewayMock = bankSoapGatewayMock;
        this.paymentRecordRepository = paymentRecordRepository;
    }

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
        String status = providerReference.startsWith("APPROVED") ? "SUCCESS" : "FAILED";

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
