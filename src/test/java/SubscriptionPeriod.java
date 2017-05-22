/**
 * Created by achurikov on 3/2/2017.
 */
public class SubscriptionPeriod {
    String name;
    String unit; // 1 - week, 2 - month, 3 - year
    String length;
    String active;
    String trial;
    Fee setupFee;
    Fee recFee;
    Fee renewalFee;
    Fee transferFee;
    Fee depositFee;
    String cancelFeeType;
    Fee cancelFee;
    String autorenewalPeriod; // can be set only by editing existing period
    String nonRefundableAmount;
    String fullRefuntPeriod;
    String afterRefundPeriod;
    String notificationSchedule;
    String planFeesDescription; // Period Fees Description in prework
    String sortNumber;
}
