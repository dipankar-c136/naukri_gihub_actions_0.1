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
        options.addArguments("--headless=new");
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

            // 4. ERROR CHECK
            if (checkForErrorText(driver)) {
                log("⛔ STOPPING: Critical error found on screen. No email will be sent.");
                return;
            }

            // 5. DIRECT SUCCESS CHECK
            if (driver.getCurrentUrl().contains("mnjuser")) {
                handleLoginSuccess(driver);
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

    // --- SUCCESS HANDLER ---
    private static void handleLoginSuccess(WebDriver driver) throws InterruptedException {
        log("🎉 SUCCESS: Login Validated! Landed on: " + driver.getCurrentUrl());
        log("⏳ Waiting 5s for dashboard to load fully...");
        Thread.sleep(5000);
        takeScreenshot(driver, "dashboard_success");
        performPostLoginActions(driver);
    }

    private static void performPostLoginActions(WebDriver driver) {
        log("👉 [PLACEHOLDER] Ready for next steps (Update Resume, etc.)...");
        // Add your update logic here later
    }

    // --- OTP LOGIC (Now uses Polling) ---
    private static void handleOtpLogic(WebDriver driver, List<WebElement> otpInputs) throws Exception {
        String gmailUser = System.getenv("GMAIL_USERNAME");
        String gmailPass = System.getenv("GMAIL_APP_PASSWORD");

        if (gmailUser == null || gmailPass == null) throw new RuntimeException("Gmail secrets missing.");

        // NOTE: We removed the blind Thread.sleep(20000). The getOtpFromGmail method now handles the waiting.
        String otp = getOtpFromGmail(gmailUser, gmailPass);

        if (otp != null) {
            log("✅ Extracted OTP: " + otp);

            otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));
            char[] digits = otp.toCharArray();

            for (int i = 0; i < otpInputs.size() && i < digits.length; i++) {
                otpInputs.get(i).sendKeys(String.valueOf(digits[i]));
            }
            log("OTP Entered. Submitting...");

            try {
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                boolean clicked = false;
                for (WebElement btn : buttons) {
                    if (btn.getText().toLowerCase().contains("verify")) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                        clicked = true;
                        break;
                    }
                }
                if (!clicked) {
                    WebElement submitBtn = driver.findElement(By.cssSelector("button[type='submit']"));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);
                }
            } catch (Exception e) {
                log("Verify click skipped (maybe auto-submitted).");
            }

            log("Waiting for redirection to Dashboard (mnjuser)...");
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(ExpectedConditions.urlContains("mnjuser"));
            } catch (Exception e) {
                log("Wait timed out, checking URL anyway...");
            }

            if (driver.getCurrentUrl().contains("mnjuser")) {
                handleLoginSuccess(driver);
            } else {
                log("❌ FAILED: OTP Accepted but still on: " + driver.getCurrentUrl());
                takeScreenshot(driver, "otp_failed_redirect");
            }
        } else {
            throw new RuntimeException("❌ OTP Email not found.");
        }
    }

    // --- SMART EMAIL POLLING ---
    public static String getOtpFromGmail(String email, String appPassword) throws Exception {
        log("📩 Connecting to Gmail...");
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", email, appPassword);

        Folder inbox = store.getFolder("INBOX");

        long startTime = System.currentTimeMillis();
        // Poll for 60 seconds
        while (System.currentTimeMillis() - startTime < 60000) {
            log("🔄 Polling inbox for OTP (Checking last 5 emails)...");
            inbox.open(Folder.READ_ONLY);

            int messageCount = inbox.getMessageCount();
            int start = Math.max(1, messageCount - 5);
            Message[] messages = inbox.getMessages(start, messageCount);

            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];
                // Check Subject
                if (msg.getSubject() != null && msg.getSubject().contains("Your OTP for logging in Naukri account")) {
                    // Check Time (Must be within last 3 minutes to avoid old OTPs)
                    if (System.currentTimeMillis() - msg.getReceivedDate().getTime() < 180000) {
                        log("🎯 Found NEW Email: " + msg.getSubject());
                        String content = getTextFromMessage(msg);
                        Matcher m = Pattern.compile("\\b\\d{6}\\b").matcher(content);
                        if (m.find()) {
                            String otp = m.group(0);
                            inbox.close(false);
                            store.close();
                            return otp;
                        }
                    }
                }
            }
            inbox.close(false);
            Thread.sleep(5000); // Wait 5s before next check
        }
        store.close();
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

    // --- UTILS ---
    private static boolean checkForErrorText(WebDriver driver) {
        try {
            List<WebElement> errors = driver.findElements(By.cssSelector(".server-error, .err, .validation-error, .r-error-msg, .error-message"));
            for (WebElement err : errors) {
                if (err.isDisplayed() && !err.getText().trim().isEmpty()) {
                    System.err.println("❌ SCREEN ERROR DETECTED: " + err.getText());
                    return true;
                }
            }
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
}