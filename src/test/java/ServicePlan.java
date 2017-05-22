import java.util.ArrayList;

/**
 * Created by achurikov on 11-Apr-16.
 */
public class ServicePlan {
    // serviceplansJson
    private String name;
    private String shortDesc;
    private String longDesc;
    private String published;
    private String recFee;
    private ArrayList<String> planUpgrades;
    private ArrayList<ResourceRate> resourceRates;
    public ArrayList<SubscriptionPeriod> subscriptionPeriods;

    public ArrayList<ResourceRate> getResourceRates() {
        return resourceRates;
    }

    public void setResourceRates(ArrayList<ResourceRate> resourceRates) {
        this.resourceRates = resourceRates;
    }

    public ArrayList<String> getPlanUpgrades() {
        return planUpgrades;
    }

    public void setPlanUpgrades(ArrayList<String> planUpgrades) {
        this.planUpgrades = planUpgrades;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }

    public void setLongDesc(String longDesc) {
        this.longDesc = longDesc;
    }

    public void setPublished(String published) {
        this.published = published;
    }

    public void setRecFee(String recFee) {
        this.recFee = recFee;
    }

    public String getShortDesc() {
        return shortDesc;
    }

    public String getLongDesc() {
        return longDesc;
    }

    public String getPublished() {
        return published;
    }

    public String getRecFee() {
        return recFee;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ResourceRate resourceRateByName(String name) {
        if (resourceRates == null) return null;

        ResourceRate rate = null;
        int i;

        for (i=0;i<resourceRates.size();i++) {
            rate = resourceRates.get(i);
            if (rate.name.compareTo(name)==0) break;
        }

        if (i==resourceRates.size()) return null;
        else return rate;
    }
}

