package bisq.core.payment;

public interface SameCountryRestrictedBankAccount extends BankAccount {
    String getCountryCode();
}
