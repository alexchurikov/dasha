import java.util.ArrayList;

/**
 * Created by achurikov on 13-Apr-16.
 */
public class ResourceRate {
    String name;
    String showInStore;
    String measureable;
    String showInCP;

    Fee setupFee;
    String setupChargePerUnit;
    String chargeForUpgrade;

    Fee recFee;
    String recChargePerUnit;

    Fee overuseFee;
    String limitNotification;
    String cancellationDescription;

    String includedUnits;
    String maxUnits;
    String minUnits;

    String controlledBy;
    String maxUnitsResource;
    String minUnitsResource;

    String storeDescription;
    String storePriceText;
    String sortOrder;

    ArrayList<ResourceRatePeriod> periods;
}
