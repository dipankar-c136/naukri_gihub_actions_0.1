package com.automation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaukriBot {

    private static int snapCounter = 1;

    public static void main(String[] args) {
        // 1. SETUP DRIVER
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new"); // Run in background
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        try {
            log("🚀 Starting Naukri Bot...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
            takeScreenshot(driver, "homepage_loaded");

            // 2. ENTER CREDENTIALS
            log("Reading credentials...");
            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            if (username == null || password == null) throw new RuntimeException("Credentials missing!");

            log("Entering credentials...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField"))).sendKeys(username);
            driver.findElement(By.id("passwordField")).sendKeys(password);
            takeScreenshot(driver, "creds_entered");

            // 3. CLICK LOGIN
            log("Clicking Login button...");
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            log("✅ Login clicked. Waiting 5s for response...");
            Thread.sleep(5000);
            takeScreenshot(driver, "after_click");

            // 4. ERROR CHECK (Priority)
            if (checkForErrorText(driver)) {
                log("⛔ STOPPING: Critical error found on screen. No email will be sent.");
                return;
            }

            // 5. DIRECT SUCCESS CHECK (If no OTP asked)
            if (driver.getCurrentUrl().contains("mnjuser")) {
                log("🎉 SUCCESS: Direct Login! Landed on: " + driver.getCurrentUrl());
                takeScreenshot(driver, "dashboard_success");
                return;
            }

            // 6. OTP CHECK
            List<WebElement> otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));

            if (!otpInputs.isEmpty()) {
                log("🚨 OTP Input Boxes Detected: " + otpInputs.size());
                takeScreenshot(driver, "otp_inputs_visible");
                handleOtpLogic(driver, otpInputs);
            } else {
                log("⚠️ Unknown State: Not on dashboard, and no obvious error found.");
                log("Check screenshot '03_after_click.png'.");
            }

        } catch (Exception e) {
            log("❌ SYSTEM CRASH: " + e.getMessage());
            e.printStackTrace();
            takeScreenshot(driver, "CRASH_FAILURE");
            System.exit(1);
        } finally {
            driver.quit();
        }
    }

    // --- OTP LOGIC ---
    private static void handleOtpLogic(WebDriver driver, List<WebElement> otpInputs) throws Exception {
        String gmailUser = System.getenv("GMAIL_USERNAME");
        String gmailPass = System.getenv("GMAIL_APP_PASSWORD");

        if (gmailUser == null || gmailPass == null) throw new RuntimeException("Gmail secrets missing.");

        log("⏳ Waiting 20s for NEW email...");
        Thread.sleep(20000);

        String otp = getOtpFromGmail(gmailUser, gmailPass);

        if (otp != null) {
            log("✅ Extracted OTP: " + otp);

            // Re-find inputs to prevent StaleElementReferenceException
            otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));

            char[] digits = otp.toCharArray();

            // --- ENTER OTP LOGIC ---
            for (int i = 0; i < otpInputs.size() && i < digits.length; i++) {
                // This enters the digits one by one into the 6 boxes
                otpInputs.get(i).sendKeys(String.valueOf(digits[i]));
            }
            log("OTP Entered. Submitting...");

            // --- CLICK VERIFY LOGIC ---
            try {
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                boolean clicked = false;
                for (WebElement btn : buttons) {
                    // We look for any button that says "Verify"
                    if (btn.getText().toLowerCase().contains("verify")) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                        clicked = true;
                        break;
                    }
                }
                // Fallback: If no button says "Verify", click the generic submit button
                if (!clicked) {
                    WebElement submitBtn = driver.findElement(By.cssSelector("button[type='submit']"));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);
                }
            } catch (Exception e) {
                log("Verify click skipped (maybe auto-submitted).");
            }

            // --- WAIT FOR REDIRECT ---
            log("Waiting for redirection to Dashboard (mnjuser)...");
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(ExpectedConditions.urlContains("mnjuser"));
            } catch (Exception e) {
                log("Wait timed out, checking URL anyway...");
            }

            // --- FINAL VALIDATION ---
            if (driver.getCurrentUrl().contains("mnjuser")) {
                log("🎉 SUCCESS: OTP Accepted! Landed on Homepage: " + driver.getCurrentUrl());
                takeScreenshot(driver, "dashboard_success");
            } else {
                log("❌ FAILED: OTP Accepted but still on: " + driver.getCurrentUrl());
                takeScreenshot(driver, "otp_failed_redirect");
            }
        } else {
            throw new RuntimeException("❌ OTP Email not found.");
        }
    }

    // --- UTILS: ERROR CHECKER ---
    private static boolean checkForErrorText(WebDriver driver) {
        try {
            // Check Red Banners
            List<WebElement> errors = driver.findElements(By.cssSelector(".server-error, .err, .validation-error, .r-error-msg, .error-message"));
            for (WebElement err : errors) {
                if (err.isDisplayed() && !err.getText().trim().isEmpty()) {
                    System.err.println("❌ SCREEN ERROR DETECTED: " + err.getText());
                    return true;
                }
            }
            // Check Body Text
            String pageText = driver.findElement(By.tagName("body")).getText().toLowerCase();
            if (pageText.contains("max limit") || pageText.contains("incorrect password") || pageText.contains("require otp to login")) {
                System.err.println("❌ TEXT ERROR DETECTED: The page contains error text.");
                return true;
            }
        } catch (Exception e) {
            log("Error checking failed: " + e.getMessage());
        }
        return false;
    }

    // --- UTILS: LOGGING & SCREENSHOTS ---
    private static void log(String msg) {
        System.out.println("[NAUKRI-BOT] " + msg);
    }

    public static void takeScreenshot(WebDriver driver, String name) {
        try {
            String fileName = String.format("%02d_%s.png", snapCounter++, name);
            FileUtils.copyFile(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE), new File(fileName));
            log("📸 Screenshot saved: " + fileName);
        } catch (IOException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    // --- UTILS: GMAIL FETCHING ---
    public static String getOtpFromGmail(String email, String appPassword) throws Exception {
        log("📩 Connecting to Gmail...");
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", email, appPassword);
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];
            if (msg.getSubject() != null && msg.getSubject().contains("Your OTP for logging in Naukri account")) {
                log("🎯 Found Email: " + msg.getSubject());
                String content = getTextFromMessage(msg);
                Matcher m = Pattern.compile("\\b\\d{6}\\b").matcher(content);
                if (m.find()) return m.group(0);
            }
            if (i < messages.length - 5) break;
        }
        return null;
    }

    private static String getTextFromMessage(Part p) throws Exception {
        if (p.isMimeType("text/plain")) return (String) p.getContent();
        if (p.isMimeType("text/html")) return ((String) p.getContent()).replaceAll("<[^>]*>", " ");
        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) result.append(getTextFromMessage(mp.getBodyPart(i)));
            return result.toString();
        }
        return "";
    }
}