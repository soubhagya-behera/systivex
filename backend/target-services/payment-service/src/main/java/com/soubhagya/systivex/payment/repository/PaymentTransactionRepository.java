package com.soubhagya.systivex.payment.repository;

import com.soubhagya.systivex.payment.model.PaymentTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {}
