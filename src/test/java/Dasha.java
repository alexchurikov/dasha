/**
 * Created by achurikov on 3/2/2017.
 */
import com.google.gson.*;
import com.sun.org.apache.regexp.internal.RE;
import com.sun.rowset.internal.Row;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

import javax.swing.text.html.HTMLDocument;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jxl.Cell;
import jxl.CellType;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

public class Dasha {
    private WebDriver driver;
    private Map<String,String> settings;

    @Test
    public void main() throws Exception {
        settings=getSettingsFromFile("resources\\settings.json"); // get data for the installation
        ServicePlan plan = getPlanData();
        //configureBA(plan);
    }

    public void configureBA(ServicePlan plan) {
        int i = init();
        //int i = quickInit();
        //int i = existingInit();

        if (i != 0) {
            output("Something went wrong during Firefox initialization, exiting...");
            return;
        }

        try {
            plan.setName("TestPlan1");
            int result = modifyPlan(plan);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private int existingInit() {
        output("Using existing Firefox window...");
        WebDriver driver = null;

        try
        {
            //URL uri = new URL(settings.get("CP_URL"));
            //URL uri = new URL("https://127.0.0.1:8354");
            URL uri = new URL("https://127.0.0.1:8355");
            driver = new RemoteWebDriver(uri, DesiredCapabilities.firefox());
            output("Executed on remote driver");

        }
        catch (Exception e) {
            return 1;
        }

        return 0;
    }

    private int init() {
        output("Opening Firefox...");
        driver = new FirefoxDriver();
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);

        output("Logging in...");
        logintoCP(settings.get("CP_URL"), settings.get("login"), settings.get("password"), settings.get("OSA_version"));
        switchtoBilling();

        return 0;
    }

    private int quickInit() {
        output("Opening Firefox...");
        driver = new FirefoxDriver();
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        output("Opening...");
        driver.get(settings.get("CP_URL"));

        return 0;
    }

    private void logintoCP(String cpUrl, String cpLogin, String cpPassword, String oaVersion) {
        driver.get(cpUrl);
        if (oaVersion.equals("6")) toFrame("loginFrame");
        else if (oaVersion.equals("7")==false) {
            output("Error: OA version invalid, use 6 or 7");
            return;
        }
        WebElement loginField = driver.findElement(By.id("inp_user"));
        loginField.sendKeys(cpLogin);
        WebElement passField = driver.findElement(By.id("inp_password"));
        passField.sendKeys(cpPassword);
        driver.findElement(By.id("login")).click();
    }
    private void switchtoBilling() {
        try {
            sleep(5000);
            toFrame("topFrame");
            clickID("to_bm");
        }
        catch (Exception e) {
            output("Could not find element by id: to_bm (\"Billing\").");
            exit(3);
        }
    }
    private void switchtoReseller(String resellerName) {
        try {
            toFrame("leftFrame");
            clickID("click_resellers");

            // search for reseller
            toFrame("mainFrame");
            clickID("header_id_1_field_id_0");
            input("header_id_1_field_id_0", resellerName);
            clickID("list_search");
        }
        catch (Exception e) {
            output("Could not find reseller name");
        }
        // Find the line and click the login button
        WebElement rName = driver.findElement(By.linkText(resellerName));
        //WebElement line = rName.findElement(By.xpath(".."));
        WebElement line = rName.findElement(By.xpath("parent::*"));
        output("Line is:" + line.toString());
        //WebElement loginButton = line.findElement(By.cssSelector("a[id='0_link']"));
        //or
        //WebElement loginButton = line.findElement(By.cssSelector("a#0_link"));
        //or
        WebElement loginButton = line.findElement(By.cssSelector("#0_link"));
        loginButton.click();
    }

    private Map<String, String> getSettingsFromFile(String file) {
        try {
            settings = new HashMap<String, String>();

            BufferedReader reader = new BufferedReader(new FileReader(file));
            JsonElement jelement = new JsonParser().parse(reader);
            JsonArray plansArray = jelement.getAsJsonArray();

            String fieldName;
            String fieldValue;
            Iterator iter = plansArray.iterator();
            // for each license

            output("Using settings:");

            while (iter.hasNext()) {
                JsonObject curLicense = (JsonObject) iter.next();
                fieldName = curLicense.getAsJsonPrimitive("name").toString();
                fieldName = fieldName.substring(1, fieldName.length() - 1); // removing quotes
                fieldValue = curLicense.getAsJsonPrimitive("value").toString();
                fieldValue = fieldValue.substring(1, fieldValue.length() - 1); // removing quotes
                settings.put(fieldName, fieldValue);
                output(fieldName + " = " + fieldValue);
            }

            return settings;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ServicePlan getPlanData() {
        ServicePlan plan = new ServicePlan();

        int i = getResourceRatesFromExcel(1, 41, plan);
        i = getSubscriptionPeriodsFromExcel(1, i+1, plan);
        i = getResourceRatePeriodsFromExcel(1, i+1, plan);

        return plan;
    }

    private int getResourceRatesFromExcel(int start_col, int start_row, ServicePlan plan) {
        File inputWorkbook = new File("resources\\" + settings.get("source_xls"));
        Workbook w;
        Cell[] row;
        int r = start_row;
        int c = start_col;
        int i=0;

        try {
            w = Workbook.getWorkbook(inputWorkbook);
            // Get the first sheet
            Sheet sheet = w.getSheet(settings.get("source_sheet"));

            // find where the data starts
            for (row = null; row == null; r++) {
                row = sheet.getRow(r);
            }

            if (row[c].getContents().compareTo("Resource Rates") != 0) {
                output("Wrong prework, expected \"Resource Rates\" at [" + c + "][" + r + "]");
                exit (1);
            }

            r = r+2; // step over header
            ArrayList<ResourceRate> resourceRates = new ArrayList<ResourceRate>();

            row = sheet.getRow(r);
            for (i=0; row[start_col].getContents().compareTo("") != 0; ) {
                c = start_col;
                String name;
                ResourceRate rate = new ResourceRate();
                rate.name = row[c].getContents();
                output("Found rate: " + rate.name);
                resourceRates.add(rate);
                c++;

                rate.showInStore = row[c].getContents();
                c++;
                rate.measureable = row[c].getContents();
                c++;
                rate.showInCP = row[c].getContents();
                c++;

                rate.setupFee = readFee(row, c);
                c+=3;
                rate.setupChargePerUnit = row[c].getContents();
                c++;
                rate.chargeForUpgrade = row[c].getContents();
                c++;

                rate.recFee = readFee(row, c);
                c+=3;
                rate.recChargePerUnit = row[c].getContents();
                c++;

                rate.overuseFee = readFee(row, c);
                c+=3;
                rate.limitNotification = row[c].getContents();
                c++;

                rate.cancellationDescription = row[c].getContents();
                c++;
                c++;// skip resource rate name

                rate.includedUnits = row[c].getContents();
                c++;
                rate.maxUnits = row[c].getContents();
                c++;
                rate.minUnits = row[c].getContents();
                c++;
                rate.controlledBy = row[c].getContents();
                c++;
                rate.maxUnitsResource = row[c].getContents();
                c++;
                rate.minUnitsResource = row[c].getContents();
                c++;

                rate.storeDescription = row[c].getContents();
                c++;
                rate.storePriceText = row[c].getContents();
                c++;
                rate.sortOrder = row[c].getContents();

                i++;
                row = sheet.getRow(r+i);
            }

            plan.setResourceRates(resourceRates);
        } catch (BiffException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return r+i;
    }

    private int getSubscriptionPeriodsFromExcel(int start_col, int start_row, ServicePlan plan) {
        File inputWorkbook = new File("resources\\" + settings.get("source_xls"));
        Workbook w;
        Cell[] row;
        int r = start_row;
        int c = start_col;
        int i = 0;

        try {
            w = Workbook.getWorkbook(inputWorkbook);
            // Get the needed sheet
            Sheet sheet = w.getSheet(settings.get("source_sheet"));

            // find where the data starts
            do {
                r++;
                row = sheet.getRow(r);
            } while (row[c].getContents().compareTo("") == 0);

            if (row[c].getContents().compareTo("Subscription Periods") != 0) {
                output("Wrong prework, expected \"Subscription Periods\" at [" + c + "][" + r + "]");
                exit (1);
            }

            r++; // step over header
            ArrayList<SubscriptionPeriod> periods = new ArrayList<SubscriptionPeriod>();

            i=0;
            while (true) {
                row = sheet.getRow(r+i);
                if (row==null) break;
                if (row.length == 0) break;
                if (row[start_col].getContents().compareTo("") == 0) break;

                SubscriptionPeriod period = new SubscriptionPeriod();
                periods.add(period);

                c = start_col;

                period.name = row[c].getContents();
                c++;
                period.active = row[c].getContents();
                c++;
                period.trial = row[c].getContents();
                c++;

                String[] name = row[c].getContents().split(" ");
                period.length = name[0];
                period.unit = name[1];
                output("Found period: " + period.length + " " + period.unit);
                c++;


                period.setupFee = readFee(row, c);
                c+=3;
                period.recFee = readFee(row, c);
                c+=3;
                period.renewalFee = readFee(row, c);
                c+=3;
                period.transferFee = readFee(row, c);
                c+=3;
                period.depositFee = readFee(row, c);
                c+=3;

                period.cancelFeeType = row[c].getContents();
                c++;
                period.cancelFee = readFee(row, c);
                c+=3;

                period.autorenewalPeriod = row[c].getContents();
                c++;
                period.nonRefundableAmount = row[c].getContents();
                c++;
                period.fullRefuntPeriod = row[c].getContents();
                c++;
                period.afterRefundPeriod = row[c].getContents();
                c++;
                period.notificationSchedule = row[c].getContents();
                c++;
                period.planFeesDescription = row[c].getContents();
                c++;
                period.sortNumber = row[c].getContents();

                i++;
            }

            plan.subscriptionPeriods = periods;
        } catch (BiffException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return r+i;
    }

    private int getResourceRatePeriodsFromExcel(int start_col, int start_row, ServicePlan plan) {
        File inputWorkbook = new File("resources\\" + settings.get("source_xls"));
        Workbook w;
        Cell[] row;
        int r = start_row;
        int c = start_col;
        int i = 0;

        output("============================= Reading Resource Rate Periods =============================");

        try {
            w = Workbook.getWorkbook(inputWorkbook);
            // Get the needed sheet
            Sheet sheet = w.getSheet(settings.get("source_sheet"));

            // find where the data starts
            do {
                row = sheet.getRow(r);
                r++;
            } while (row[c].getContents().compareTo("") == 0);

            if (row[c].getContents().compareTo("Resource Rate Periods") != 0) {
                output("Wrong prework, expected \"Resource Rate Periods\" at [" + c + "][" + r + "], but found \"" + row[c].getContents() + "\"");
                exit (1);
            }

            //r++; // step over header

            i=0;
            while (true) {
                row = sheet.getRow(r+i);
                if (row==null) break;
                if (row.length == 0) break;
                if (row[start_col].getContents().compareTo("") == 0) break;

                ResourceRatePeriod period = new ResourceRatePeriod();

                c = start_col;

                String[] fullName = row[c].getContents().split("-");
                String[] name = fullName[1].split(" ");
                period.duration = name[1];
                period.unit = name[2];
                if (name[2].endsWith("s")) {
                    period.unit = name[2].substring(0,name[2].length()-1);
                }
                c++;

                String rrName = row[c].getContents();
                c++;

                output("Found period: " + period.duration + " " + period.unit + "(s) for rate " + rrName);

                period.recFee = readFee(row, c);
                c+=3;
                c++; // set over empty column

                period.overFee = readFee(row, c);
                c+=3;

                plan.resourceRateByName(rrName).periods.add(period);

                i++;
            }
        } catch (BiffException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return r+i;
    }

    private Fee readFee(Cell[] row, int start) {
        int c = start;
        Fee fee = new Fee();

        fee.value = row[c].getContents();
        c++;
        fee.description = row[c].getContents();
        c++;
        fee.showZeroPrice = row[c].getContents();

        return fee;
    }

    private int createPlan(Map<String, String> data, ServicePlan plan) throws Exception {
        toFrame("leftFrame");
        clickID("click_service_plans");
        toFrame("mainFrame");

        String prefixedName = data.get("prefix").concat(plan.getName());
        prefixedName = truncatePlanName(prefixedName);
        //---------------------------------------
        // verify that plan does not exist
        output("Checking if it already exists...");
        if (findInSearch(prefixedName) != null) { // need to check exact match in that function!!!
            output("Yes, it does. Skipping...");
            return 1;
        }

        //---------------------------------------

        clickID("input___add");
        //choose Plan type
        clickID("WizardPlanTypeplanTypeID_5");
/*
//      TOIMPROVE
        WebElement planType=driver.findElement(By.xpath("//*[.,'Generic Service Plan']"));
//        WebElement planType=driver.findElement(By.id("WizardPlanTypeplanTypeID_5"));
        if (planType.getText() != "Generic Service Plan") {
            output("TTText is: " + planType.getText());
            throw new Exception("Generic Plan is on different ID in this BA version! Change your code!");
        }
*/
        clickID("input___SelectWizardTypeWin_Next");

        /* screen 1 */
        // - input name, etc
        WebElement nameField = driver.findElement(By.id("input___name"));

        // setting maxlength to 120
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("document.getElementById('input___name').setAttribute('maxlength', '120')");

        input("input___name", prefixedName);
        input("input___ServiceTemplateserviceTemplateID", data.get("st_id"));

        // Plan Category (drop-down list)
        WebElement planCategory = driver.findElement(By.id("input___PlanCategoryplanCategoryID"));
        // check that it's a select
        //output("Tag name: " + planCategory.getTagName());
        if (planCategory.getTagName().equals("select")) {
            Select select = new Select(planCategory);
            select.selectByVisibleText(data.get("planCategory"));
        }
        else if (planCategory.getTagName().equals("input")) {
            WebElement parent = planCategory.findElement(By.xpath(".."));
            WebElement text = parent.findElement(By.id("text-input___PlanCategoryplanCategoryID"));
            //    output("text: " + text.getText());
            if (text.getText().equals("Office 365") == false) {
                output("\"Office 365\" plan category could not be found, I see this instead: " + text.getText());
                return 2;
            }
        }
        else {
            output("Plan category is of an unknown element type!");
            return 3;
        }

        // Service Terms
        Select select = new Select(driver.findElement(By.id("input___ServiceTermsServTermID")));
        select.selectByVisibleText(data.get("ServiceTerms"));

        // Description, published
        input("input___shortDescription", plan.getShortDesc());
        input("input___longDescription", plan.getLongDesc());
        if (data.get("published").equals("yes")) clickID("input___vPublished");

        //autorenewal
        select = new Select(driver.findElement(By.id("input___AutoRenewType")));
        select.selectByVisibleText(data.get("autorenewalType"));
        input("input___RenewOrderInterval", data.get("autorenewalDays"));

        clickID("input___SaveStep1");

        /* screen 2 */
        // // one year by default
        clickID("vec_t1_4");

        // set recurr fee
        driver.findElement(By.id("input___dSubscriptionFee-3")).clear();
        input("input___dSubscriptionFee-3", plan.getRecFee());

        clickID("input_____NextStep");

        /* screen 3 */
        // click "setup plan rates"
        //clickID("input_____NextStep");

        // click "Finish"
        clickID("input___SP_ViewPlan");

        return 0;
    }

    private int modifyPlan(ServicePlan plan) {
        try {
            sleep(5000);
            output("Opening leftFrame...");
            if (toFrame("leftFrame") != 0) return 1;

            output("Opening service plans...");
            clickID("click_service_plans");
            clickID("click_service_plans");
        }
        catch (Exception e) {
            output("Could not find element by id \"click_service_plans\".");
        }

        output("Opening mainFrame...");
        toFrame("mainFrame");

        String prefixedName = settings.get("prefix").concat(plan.getName());
        prefixedName = truncatePlanName(prefixedName);
        output("Searching for plan...");

        WebElement planElement = findInSearch(prefixedName);
        if (planElement == null) {
            output("Something went wrong. Plan " + settings.get("prefix").concat(plan.getName()) + " not found");
            return 1;
        }
        planElement.click();

        /*addResourceRates(plan.getResourceRates());

        ArrayList<ResourceRate> rates = plan.getResourceRates();
        for(int i=0; i < rates.size(); i++) {
            modifyResourceRate(rates.get(i));
        }*/

        addSubscriptionPeriods(plan.subscriptionPeriods);

        return 0;
    }

    private void addSubscriptionPeriods(ArrayList<SubscriptionPeriod> periods) {
        output("Filling subscription periods...");
        try {
            clickID("webgate__tab_2"); // remove if you also create resource rates
            clickID("webgate__tab_2"); // Subscription periods
        }
        catch (Exception e) {
            output("Could not find element by id: webgate__tab_2 (\"Resource Rates\").");
        }

        // remove existing periods first
        try {
            WebElement checkBox = driver.findElement(By.xpath("//input[contains(@name, 't1_checkall')]"));
            checkBox.click();
            clickID("input___MoveToArc");
        }
        catch (Exception e) {
            output("ERROR: Could not remove all periods");
            return;
        }

        int num = periods.size();
        for (int i = 0; i<num; i++) {
            addSubscriptionPeriod(periods.get(i)); // check check-boxes
            modifySubscriptionPeriod(periods.get(i));
        }
    }

    private void setFee(String feeName, Fee fee) {
        input("input___" + feeName + "Fee", fee.value);
        input("input___" + feeName + "FeeText", fee.description);
        setCheckBox("input___ShowZero" + feeName + "Fee", fee.showZeroPrice);
    }

    private void setDropdown(String where, String what) {
        if (what.compareTo("<default>") == 0) return; // if value is <default>, don't do anything
        if (what.compareTo("<empty>") == 0) {
            output("Error in prework: Drop-down cannot be empty.");
        }

        WebElement dropdown = driver.findElement(By.id(where));
        // check that it's a select
        if (dropdown.getTagName().equals("select")) {
            Select select = new Select(dropdown);
            select.selectByVisibleText(what);
        } else {
            output("Error: could not find \"select\" element. Found" + dropdown.getTagName());
        }
    }

    private void addSubscriptionPeriod(SubscriptionPeriod period) {
        output("Adding period: " + period.length + " " + period.unit);
        clickID("input_____addHosting"); // add new
        input("input___Period", period.length);

        // unit (drop-down list)
        setDropdown("input___PeriodType", period.unit);

        clickID("input___SP_ViewPlanPeriod"); // Finish
    }

    private void modifySubscriptionPeriod(SubscriptionPeriod period) {
        // Find period by value in "Subscription Period" column
        WebElement periodNameElement = driver.findElement(By.xpath("//td[contains(text(),'" + period.name + "')]"));
        WebElement periodLine = periodNameElement.findElement(By.xpath("parent::*"));
        WebElement clickableElement = periodLine.findElement(By.xpath(".//a")); // clickable descendant
        clickableElement.click();

        clickID("input___Correct"); // Edit

        setCheckBox("input___Enabled", period.active);
        setCheckBox("input___Trial", period.trial);

        setFee("Setup", period.setupFee);
        setFee("Subscription", period.setupFee);
        setFee("Renewal", period.setupFee);
        setFee("Deposit", period.setupFee);

        input("input___FeeText", period.planFeesDescription);

        ///////////////////////////////////////////////
        setDropdown("input___CancellationFeeType", period.cancelFeeType);

        if(period.cancelFeeType.compareTo("Plan and Resource Rates until Expiration Date") == 0) {
            input("input___CancellationFeeText", period.cancelFee.description);
            setCheckBox("input___ShowZeroCancelFee", period.cancelFee.showZeroPrice);
        }
        else if(period.cancelFeeType.compareTo("Custom") == 0) {
            input("input___CancellationFeeText", period.cancelFee.description);
            setCheckBox("input___ShowZeroCancelFee", period.cancelFee.showZeroPrice);
            input("input___CancellationFeeFormula", period.cancelFee.value);
        }
        else if(period.cancelFeeType.compareTo("None") != 0) {
            output("Error: Could not parse value of Cancellation Fee Type: " + period.cancelFeeType);
        }
        ///////////////////////////////////////////////

        input("input___NonRefundableAmt", period.nonRefundableAmount);
        input("input___RefundPeriod", period.fullRefuntPeriod);
        setDropdown("input___RefundPolicy", period.afterRefundPeriod);

        selectInPopup("input___refNotifSchedule", period.notificationSchedule);

        input("input___FeeText", period.planFeesDescription);
        input("input___SortNumber", period.sortNumber);

        clickID("input___Save"); // Finish

        clickID("Planname");
        clickID("webgate__tab_2"); // Subscription periods
    }

    public void addResourceRates(ArrayList<ResourceRate> rates) {
        output("Filling resource rates...");
        try {
            clickID("webgate__tab_2"); // Resource Rates
        }
        catch (Exception e) {
            output("Could not find element by id: webgate__tab_2 (\"Resource Rates\").");
        }

        try {
            clickID("input___add"); // add new
        }
        catch (Exception e) {
            output("Could not find element id \"input___add\".");
        }
        int numRes = rates.size();
        for (int i = 0; i<numRes; i++) {
            selectResourceRate(rates.get(i)); // check check-boxes
        }

        try {
            clickID("input___NextButton");
            clickID("input___SP_PlanRateList"); // finish
        }
        catch (Exception e) {
            output("Could not find element id \"input___NextButton\" or \"input___SP_PlanRateList\".");
        }
    }

    private void selectResourceRate(ResourceRate rate) {
        String rrName = rate.name;
        rrName = truncateRateName(rrName);

        String xpathStr = "//td[contains(text(),'" + rrName + "')]";
        WebElement rrNameElement;
        try {
            rrNameElement = driver.findElement(By.xpath(xpathStr));
        }
        catch (Exception e) {
            output("ERROR: The following resource could not be found: " + rate.name);
            output("Create it and add to plan manually!");
            return;
        }
        WebElement rrLine = rrNameElement.findElement(By.xpath(".."));

        WebElement checkBox = rrLine.findElement(By.tagName("input"));
        checkBox.click();

        try {
            //    Select select = new Select(rrLine.findElement(By.tagName("select")));
            //    select.selectByVisibleText(get("resourceCategory"));
        }
        catch(Exception ex) {
            // most probably Select element was not found, it means that resource already is present in resource category
            // just do nothing
        }
    }

    private String mapCheckBoxValue(String value) {
        if (value.compareTo("Yes") == 0) {
            return "1";
        }
        else if (value.compareTo("No") == 0) {
            return "0";
        }
        else {
            System.out.println("Error: Could not map check-box value: " + value);
            return null;
        }
    }

    private void setCheckBox(String elementName, String value) {
        if (value.compareTo("<default>") == 0) return; // if value is <default>, don't do anything
        if (value.compareTo("<To be configured on resource rate period level. See configuration details below>") == 0) return; // same
        WebElement element = driver.findElement(By.id(elementName));
        if (element.getAttribute("value").compareTo(mapCheckBoxValue(value))!=0) {
            element.click();
        }
    }

    private void setControlledBy(String elementName, String value) {
        if (value.compareTo("<default>") == 0) return; // if value is <default>, don't do anything
        WebElement element = null;

        if (value.compareTo("External System") == 0) {
            element = driver.findElement(By.id("row__MaxControl_0"));
        }
        else if (value.compareTo("Billing System") == 0) {
            element = driver.findElement(By.id("row__MaxControl_1"));
        }
        if (element == null) {
            output("Error: Could not parse input for Controlled By!");
            exit(1);
        }
        element.click();
    }

    private void selectInPopup(String where, String what) {
        if(what.compareTo("default>") == 0) {
            return;
        }
        if(what.compareTo("<empty>") == 0) {
            clickID("reset___refNotifSchedule");
            return;
        }

        WebElement element = null;

        try {
            clickID(where);

            String parentWindowHandler = driver.getWindowHandle(); // Store your parent window
            String subWindowHandler = null;

            Set<String> handles = driver.getWindowHandles(); // get all window handles
            Iterator<String> iterator = handles.iterator();
            while (iterator.hasNext()){
                subWindowHandler = iterator.next();
            }
            driver.switchTo().window(subWindowHandler); // switch to popup window

            String xpath = "//td[contains(text(),'" + what + "')]"; // May not be an exact match, a cycle would be better.
            element = driver.findElement(By.xpath(xpath));
            if (element.getText().compareTo(what) != 0) {
                output("We were trying to select " + what + " in " + where + ", but found " + element.getText());
                output("Error: Found some value, but it's not an exact match, pls set the correct one manually and report to developer!");
            }
            else {
                element.click();
            }

            driver.switchTo().window(parentWindowHandler);  // switch back to parent window
            toFrame("mainFrame");
        }
        catch (Exception e){
            output("We were trying to select " + what + " in " + where + ", but found " + element.getText());
            output("Error: Found some value, but it's not an exact match, pls set the correct one manually and report to developer!");
            e.printStackTrace();
            exit(1);
        }
    }

    private void modifyResourceRate(ResourceRate rate) { // designed to be run right after addResourceRates()
        String rrName = rate.name;
        rrName = truncateRateName(rrName);

        //toFrame("mainFrame");

        /*result = planLine.findElement(By.xpath(".//a[contains(text(),'" + text + "')]"));
        //output("Text: " + text);
        //output("result.text: " + result.getText());
        if (result.getText().equals(text)) {
            //output("Exact match found, creation skipped.");
            return result;
        }*/

        try {

            WebElement rrNameElement = driver.findElement(By.linkText(rrName));
            rrNameElement.click();
            clickID("input___Edit");

            // Set all properties:
            setCheckBox("input___IsMain", rate.showInStore);
            setCheckBox("input___measurable", rate.measureable);
            setCheckBox("input___IsVisible", rate.showInCP);

            input("input___setupFee", rate.setupFee.value);
            input("input___SetupFeeDescr", rate.setupFee.description);
            setCheckBox("input___SFIncludeIfZero", rate.setupFee.showZeroPrice);
            setCheckBox("input___IsSFperUnit", rate.setupChargePerUnit);
            setCheckBox("input___IsSFforUpgrade", rate.chargeForUpgrade);

            input("input___recurringFee", rate.recFee.value);
            input("input___RecurrFeeDescr", rate.recFee.description);
            setCheckBox("input___RFIncludeIfZero", rate.recFee.showZeroPrice);
            setCheckBox("input___IsRFperUnit", rate.recChargePerUnit);

            // Overuse Fee
            input("input___costForAdditional", rate.overuseFee.value);
            input("input___OveruseFeeDescr", rate.overuseFee.description);
            setCheckBox("input___OFIncludeIfZero", rate.overuseFee.showZeroPrice);
            selectInPopup("input___refLimitNotification", rate.limitNotification);

            input("input___CancellationFeeDescr", rate.cancellationDescription);

            input("input___includedValue", rate.includedUnits);
            input("input___maxValue", rate.maxUnits);
            input("input___minValue", rate.minUnits);

            setControlledBy("tr___MaxControl", rate.controlledBy);
//            input("input___setupFee", rate.maxUnitsResource); // TODO: implement!!!
//            input("input___setupFee", rate.minUnitsResource); // TODO: implement!!!

            input("input___StoreText", rate.storeDescription);
            input("input___StorePriceText", rate.storePriceText);
            input("input___StoreSortOrder", rate.sortOrder);

            clickID("input___Save");

            // get back to list of resource rates
            clickID("Planname");

            WebElement el = driver.findElement(By.linkText("Resource Rates"));
            el.click();

            //.//*[@id='multiLineTabs']/ul[2]
            //clickID("webgate__tab_2"); // Resource Rates

        }
        catch (Exception e) {
            e.printStackTrace();
        }

/*
        String xpathStr = "//td[contains(text(),'" + rrName + "')]";
        WebElement rrNameElement;
        try {
            rrNameElement = driver.findElement(By.xpath(xpathStr));
        }
        catch (Exception e) {
            return;
        }*/

    }

    private WebElement findInSearch(String text) { // searches for text using search and returns link to element with given text
        try {
            WebElement hideSearch = driver.findElement(By.linkText("Hide Search"));
        }
        catch (Exception e) {
            try{
                WebElement showSearch = driver.findElement(By.linkText("Show Search"));
                showSearch.click();
            }
            catch(Exception e2) {
                // search not found. Proceed with caution.
                //return null;
            }
        }

        String pText = prepareForBASearch(text); // quote parentheses

        try{
            input("filter_name", pText);
        }
        catch(Exception e) {
            output("There are no plans at all. Creating...");
            return null;
        }

        try {
            clickID("_browse_search");
        }
        catch(Exception e) {
            output("Could not click search button");
        }

        WebElement planLine = null;
        WebElement result = null;
/*        // just searching for the plan name:
        try {
            //String xpathStr = "//a[text() = '" + text + "']";
            String xpathStr = "a[text() = '" + text + "']";
            output("Xpath: " + xpathStr);
            result = driver.findElement(By.xpath(xpathStr));
            //output("Text: " + text);
            //output("result.text: " + result.getText());
            return result;
        }
        catch (Exception ex) {
            return null;
        }
*/

        for (int i=1;; i++) { // checking each found element for total match
            //output("i = " + i);
            try {
                planLine = driver.findElement(By.id("vel_t1_"+i));
            }
            catch (Exception ex) {
                if (i==1) {
                    output("No, it doesn't. Creating it...");
                    return null;
                } // nothing found
                else {
                    output("Found something, but not exact match, creating...");
                    return null;
                }
            }
            try {
                //WebElement result = planLine.findElement(By.linkText(text));
                //result = planLine.findElement(By.xpath("./a[contains(text(),'" + text +"')]"));
                //result = planLine.findElement(By.xpath("//a[text() = '" + text + "']"));
                //result = planLine.findElement(By.xpath("//a[contains(text(),'" + text + "')]")); // works - but here we have an error, it searches absolutely and always finds the first line!
                result = planLine.findElement(By.xpath(".//a[contains(text(),'" + text + "')]"));
                //output("Text: " + text);
                //output("result.text: " + result.getText());
                if (result.getText().equals(text)) {
                    //output("Exact match found, creation skipped.");
                    return result;
                }
            }
            catch (Exception ex) {
                output("Something went wrong. Will skip the current plan. Report this exception to developer:");
                output(ex.toString());
                if (planLine==null) {exit(1);}
                else {return planLine;}
            }
        }

    }
    private String prepareForBASearch(String input) {
        String output = input;
        //output("INPUT: " + input);
        output = output.replace("(", "\\(");
        output = output.replace(")", "\\)");

        //output("OUTPUT: " + output);
        return output;
    }

    private int toFrame(String name) {
        try {
            driver.switchTo().defaultContent();
            driver.switchTo().frame(name);
            return 0;
        }
        catch(Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private void clickID(String id) {
        WebElement el;

        try {
            el = driver.findElement(By.id(id));
            el.click();
        }
        catch (Exception e) {
            output("Error: Could not find element by ID:" + id);
            e.printStackTrace();
            exit(1);
        }
    }

    private void input(String where, String what)  { // maybe need to add "throws something"
        if (what.compareTo("<default>") == 0) return; // if value is <default>, don't do anything
        if (what.compareTo("<To be configured on resource rate period level. See configuration details below>") == 0) return; // same
        WebElement wE = driver.findElement(By.id(where));
        wE.clear();
        if (what.compareTo("<empty>") == 0) return; // if value is <empty>, just clear the field
        wE.sendKeys(what);
    }

    private String truncateRateName(String rrName) {
        if (settings.get("ResourceRateNamesTruncatedByOA").equals("yes")) {
            if(rrName.length()> 64) {
                return rrName.substring(0,63);
            }
        }

        return rrName;
    }

    private String truncatePlanName(String planName) {
        if (settings.get("PlanNamesTruncatedByBA").equals("yes")) {
            if (planName.length() > 60) {
                planName = planName.substring(0,59);
            }
        }

        return planName;
    }

    private void output(String string) {System.out.println(string);}

    // Not used:
    private int getDatafromServicePlansJSON(String file) {
        try {
            output("------------------- Reading Serviceplans JSON -------------------");

            BufferedReader reader = new BufferedReader(new FileReader(file));

            // array of plans
            JsonElement jelement = new JsonParser().parse(reader);
            JsonObject jPlans = jelement.getAsJsonObject();
            int numberOfPlans = 0;
            // for each plan
            for (Map.Entry<String, JsonElement> jPlanMap : jPlans.entrySet())
            {
                JsonObject jPlan = jPlanMap.getValue().getAsJsonObject();
                //output("Processing: " + jPlan);
                ServicePlan plan = new ServicePlan();

                // populate plan fields
                String planName=jPlan.getAsJsonPrimitive("Name").toString();
                planName=planName.substring(1,planName.length()-1); // removing quotes
                plan.setName(planName);
                output("------------------- Processing Plan: " + plan.getName());

                plan.setShortDesc(planName);
                plan.setRecFee(jPlan.getAsJsonPrimitive("RecurringFee").toString());
                output("RecurringFee: " + jPlan.getAsJsonPrimitive("RecurringFee").toString());

                // populate upgrades array
                JsonArray jUpgrades = jPlan.getAsJsonArray("UpgradeTo");
                //output("jUpgrades = " + jUpgrades);
                ArrayList<String> upgrades = new ArrayList<String>();
                Iterator iter = jUpgrades.iterator();
                while(iter.hasNext()) {
                    upgrades.add(iter.next().toString());
                    //output("jUpgrades = " + jUpgrades);
                }
                output("Upgrades: " + upgrades);
                plan.setPlanUpgrades(upgrades);

                // populate resourceRates array
                output("Rates:");
                ArrayList<ResourceRate> resourceRates = new ArrayList<ResourceRate>();

                JsonObject jRates = jPlan.getAsJsonObject("Resources");
                //output("jRates = " + jRates);
                // for each rate
                for (Map.Entry<String, JsonElement> jRateMap : jRates.entrySet())
                {
                    JsonObject jRate = jRateMap.getValue().getAsJsonObject();

                    ResourceRate rate = new ResourceRate();
                    String rateName = jRate.getAsJsonPrimitive("Name").toString();
                    rateName=rateName.substring(1,rateName.length()-1); // removing quotes
                    rate.name = rateName;
                    rate.includedUnits = jRate.getAsJsonPrimitive("Included").toString();
                    rate.maxUnits = jRate.getAsJsonPrimitive("Maximum").toString();
                    rate.recFee.value = jRate.getAsJsonPrimitive("RecurringFee").toString();
                    resourceRates.add(rate);
                    output("Name: " + rate.name + ", Included: " + rate.includedUnits + ", Max: " + rate.maxUnits + ", Recurring Fee: " + rate.recFee.value);
                }
                plan.setResourceRates(resourceRates);

                // put plan name to our map
                numberOfPlans++;
                //if (plan != null) output("FINAL NAME: " + planName);
            }
            output("Number of plans read from file: " + numberOfPlans);
            return numberOfPlans;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    private void fillResourceRate(ResourceRate rate) { // not needed
        /*WebElement includedAmount = rrLine.findElement(By.xpath("//input[contains(@id,'input___includedValue-0')]"));
        includedAmount.clear();
        includedAmount.sendKeys(rate.getIncluded());
*/  // edits only only one rate, need to use findElements and arrays
        String rrName = rate.name;
        rrName = truncateRateName(rrName);

        String xpathStr = "//td[contains(text(),'" + rrName + "')]";
        WebElement rrNameElement;
        try {
            rrNameElement = driver.findElement(By.xpath(xpathStr));
        }
        catch (Exception e) {
            return;
        }

        WebElement rrLine = rrNameElement.findElement(By.xpath("..")); // get parent

        WebElement field = rrLine.findElement(By.xpath(".//input[contains(@id, 'input___includedValue')]"));
        field.clear();
        field.sendKeys(rate.includedUnits);

        field = rrLine.findElement(By.xpath(".//input[contains(@id, 'input___maxValue')]"));
        field.clear();
        field.sendKeys(rate.maxUnits);

        field = rrLine.findElement(By.xpath(".//input[contains(@id, 'input___recurringFee')]"));
        field.clear();
        field.sendKeys(rate.recFee.value);
    }
}
