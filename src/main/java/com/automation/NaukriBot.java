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

    // Counter to keep screenshots ordered (01, 02, 03...)
    private static int snapCounter = 1;

    public static void main(String[] args) {
        // Setup Driver
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
        // Hide WebDriver flag
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        try {
            log("🚀 Starting Naukri Bot...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

            log("Page Loaded. Title: " + driver.getTitle());
            takeScreenshot(driver, "homepage_loaded");

            // --- 1. ENTER CREDENTIALS ---
            log("Reading credentials from environment...");
            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            if (username == null || password == null) throw new RuntimeException("Credentials missing!");

            log("Typing credentials...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField"))).sendKeys(username);
            driver.findElement(By.id("passwordField")).sendKeys(password);

            takeScreenshot(driver, "creds_entered");

            // --- 2. CLICK LOGIN ---
            log("Locating Login button...");
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));

            log("Clicking Login button...");
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            log("✅ Login clicked. Waiting 5s for page transition...");
            Thread.sleep(5000);

            // Snapshot immediately after click to see what happened
            takeScreenshot(driver, "after_login_click");

            // --- 3. CHECK STATUS (DASHBOARD vs OTP vs ERROR) ---
            String currentUrl = driver.getCurrentUrl();
            log("Current URL after click: " + currentUrl);

            // CASE A: Direct Success
            if (currentUrl.contains("mnjuser")) {
                log("🎉 SUCCESS: Redirected directly to Dashboard!");
                return; // Exit successfully
            }

            // CASE B: OTP Screen Detection
            // We check for input boxes or specific text
            List<WebElement> otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));
            boolean isOtpPage = !otpInputs.isEmpty() || driver.getPageSource().contains("OTP");

            if (isOtpPage) {
                log("🚨 OTP Screen Detected! (Found " + otpInputs.size() + " input boxes)");
                takeScreenshot(driver, "otp_screen_detected");

                // --- HANDLE OTP ---
                handleOtpLogic(driver, otpInputs);
            }
            else {
                log("⚠️ No OTP inputs found, but not on dashboard either.");
                captureErrorText(driver); // Look for error messages
            }

            // --- 4. FINAL VERIFICATION ---
            log("Performing final check...");
            takeScreenshot(driver, "final_state");

            if (driver.getCurrentUrl().contains("mnjuser")) {
                log("🎉 SUCCESS: We are inside the dashboard.");
            } else {
                log("❌ FAILED: Script finished but URL is still: " + driver.getCurrentUrl());
                captureErrorText(driver);
                throw new RuntimeException("Login failed - See screenshots");
            }

        } catch (Exception e) {
            log("❌ CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
            takeScreenshot(driver, "CRASH_FAILURE");
            System.exit(1); // Force fail the GitHub Action
        } finally {
            log("Shutting down driver...");
            driver.quit();
        }
    }

    // --- LOGIC: OTP HANDLING ---
    private static void handleOtpLogic(WebDriver driver, List<WebElement> otpInputs) throws Exception {
        String gmailUser = System.getenv("GMAIL_USERNAME");
        String gmailPass = System.getenv("GMAIL_APP_PASSWORD");

        if (gmailUser == null || gmailPass == null) {
            throw new RuntimeException("OTP required but Gmail secrets are missing.");
        }

        log("⏳ Waiting 20s for NEW email to arrive...");
        Thread.sleep(20000);

        String otp = getOtpFromGmail(gmailUser, gmailPass);

        if (otp != null) {
            log("✅ Extracted OTP: " + otp);

            // Re-find inputs in case page refreshed
            if(otpInputs.isEmpty()) {
                otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));
            }

            char[] digits = otp.toCharArray();
            for (int i = 0; i < otpInputs.size() && i < digits.length; i++) {
                otpInputs.get(i).sendKeys(String.valueOf(digits[i]));
            }
            log("OTP Typed into " + otpInputs.size() + " boxes.");
            takeScreenshot(driver, "otp_entered");

            // Click Verify
            try {
                log("Looking for Verify button...");
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                boolean clicked = false;
                for (WebElement btn : buttons) {
                    if (btn.getText().toLowerCase().contains("verify")) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                        log("Clicked 'Verify' button.");
                        clicked = true;
                        break;
                    }
                }
                if (!clicked) log("⚠️ Warning: Could not find explicit 'Verify' button (might contain icon).");
            } catch (Exception e) {
                log("Verify click error: " + e.getMessage());
            }

            log("Waiting 5s after verification...");
            Thread.sleep(5000);
        } else {
            throw new RuntimeException("❌ Failed to find OTP email in Gmail.");
        }
    }

    // --- HELPER: LOGGING ---
    private static void log(String message) {
        System.out.println("[NAUKRI-BOT] " + message);
    }

    // --- HELPER: CAPTURE ERROR TEXT ---
    private static void captureErrorText(WebDriver driver) {
        try {
            // Look for common error classes
            List<WebElement> errors = driver.findElements(By.cssSelector(".error, .err, .server-error, .validation-error"));
            if (!errors.isEmpty()) {
                for (WebElement err : errors) {
                    if (err.isDisplayed()) {
                        System.err.println("❌ SCREEN ERROR FOUND: " + err.getText());
                    }
                }
            } else {
                // If no specific class, print body text snippet
                String bodyText = driver.findElement(By.tagName("body")).getText();
                String snippet = bodyText.length() > 200 ? bodyText.substring(0, 200) : bodyText;
                System.err.println("❌ PAGE TEXT SNIPPET: " + snippet.replace("\n", " "));
            }
        } catch (Exception e) {
            System.err.println("Could not extract error text.");
        }
    }

    // --- HELPER: SCREENSHOT ---
    public static void takeScreenshot(WebDriver driver, String name) {
        try {
            String fileName = String.format("%02d_%s.png", snapCounter++, name);
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(scrFile, new File(fileName));
            log("📸 Screenshot saved: " + fileName);
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }

    // --- HELPER: EMAIL PARSER ---
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
        log("🔍 Found " + messages.length + " unread emails.");

        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];
            String subject = msg.getSubject();

            if (subject != null && subject.contains("Your OTP for logging in Naukri account")) {
                log("🎯 Found Target Email: " + subject);
                String content = getTextFromMessage(msg);
                Pattern p = Pattern.compile("\\b\\d{6}\\b");
                Matcher m = p.matcher(content);
                if (m.find()) return m.group(0);
            }
            if (i < messages.length - 5) break;
        }
        return null;
    }

    private static String getTextFromMessage(Part p) throws Exception {
        if (p.isMimeType("text/plain")) {
            return (String) p.getContent();
        }
        else if (p.isMimeType("text/html")) {
            return ((String) p.getContent()).replaceAll("<[^>]*>", " ");
        }
        else if (p.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) p.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < mimeMultipart.getCount(); i++) {
                result.append(getTextFromMessage(mimeMultipart.getBodyPart(i)));
            }
            return result.toString();
        }
        return "";
    }
}