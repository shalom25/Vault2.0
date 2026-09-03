package com.example.vault.loans;

import java.util.UUID;

public class Loan {
    private final UUID borrower;
    private double principal;
    private double remaining;
    private double installmentAmount;
    private long intervalMs;
    private long nextChargeAtMs;
    private int installmentsLeft;
    private int missedPayments;
    private long createdAtMs;
    private LoanStatus status;

    public Loan(UUID borrower) {
        this.borrower = borrower;
        this.status = LoanStatus.ACTIVE;
        this.createdAtMs = System.currentTimeMillis();
    }

    public UUID getBorrower() {
        return borrower;
    }

    public double getPrincipal() {
        return principal;
    }

    public void setPrincipal(double principal) {
        this.principal = principal;
    }

    public double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public double getInstallmentAmount() {
        return installmentAmount;
    }

    public void setInstallmentAmount(double installmentAmount) {
        this.installmentAmount = installmentAmount;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getNextChargeAtMs() {
        return nextChargeAtMs;
    }

    public void setNextChargeAtMs(long nextChargeAtMs) {
        this.nextChargeAtMs = nextChargeAtMs;
    }

    public int getInstallmentsLeft() {
        return installmentsLeft;
    }

    public void setInstallmentsLeft(int installmentsLeft) {
        this.installmentsLeft = installmentsLeft;
    }

    public int getMissedPayments() {
        return missedPayments;
    }

    public void setMissedPayments(int missedPayments) {
        this.missedPayments = missedPayments;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }
}

