package com.automation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.mail.*;
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

    public static void main(String[] args) {
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
            System.out.println("🚀 Starting Naukri Bot...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

            // --- 1. ENTER CREDENTIALS ---
            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField"))).sendKeys(username);
            driver.findElement(By.id("passwordField")).sendKeys(password);

            // --- 2. CLICK LOGIN ---
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            System.out.println("✅ Login clicked. Checking for OTP wall...");
            Thread.sleep(5000);

            // --- 3. OTP HANDLING ---
            // We look for input fields that accept numbers (Naukri uses type='tel' or class='otp')
            List<WebElement> otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));

            if (!otpInputs.isEmpty()) {
                System.out.println("🚨 OTP Screen Detected! (Found " + otpInputs.size() + " input boxes)");
                takeScreenshot(driver, "03_otp_required.png");

                // Get credentials for Gmail
                String gmailUser = System.getenv("GMAIL_USERNAME");
                String gmailPass = System.getenv("GMAIL_APP_PASSWORD");

                if(gmailUser == null || gmailPass == null) {
                    throw new RuntimeException("OTP required but GMAIL_USERNAME/PASSWORD secrets are missing!");
                }

                System.out.println("⏳ Waiting 15s for email to arrive...");
                Thread.sleep(15000); // Give email time to arrive

                String otp = getOtpFromGmail(gmailUser, gmailPass);

                if (otp != null) {
                    System.out.println("✅ Extracted OTP: " + otp);

                    // Enter OTP char-by-char into the separate boxes
                    char[] digits = otp.toCharArray();
                    for (int i = 0; i < otpInputs.size() && i < digits.length; i++) {
                        otpInputs.get(i).sendKeys(String.valueOf(digits[i]));
                    }

                    System.out.println("Typing complete. Submitting...");

                    // The verify button usually appears after typing, or we force click it
                    try {
                        WebElement verifyBtn = driver.findElement(By.xpath("//button[contains(text(),'Verify')]"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", verifyBtn);
                    } catch (Exception e) {
                        System.out.println("Could not click verify (maybe auto-submitted?): " + e.getMessage());
                    }

                    Thread.sleep(5000);
                } else {
                    throw new RuntimeException("❌ Failed to find OTP email.");
                }
            }

            // --- 4. VERIFY SUCCESS ---
            takeScreenshot(driver, "04_final_page.png");

            if (driver.getCurrentUrl().contains("mnjuser")) {
                System.out.println("🎉 SUCCESS: Login successful! We are inside.");
            } else {
                System.err.println("❌ FAILED: Still not on dashboard. Check screenshot 04.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            takeScreenshot(driver, "ERROR_crash.png");
            System.exit(1);
        } finally {
            driver.quit();
        }
    }

    // --- GMAIL OTP READER ---
    public static String getOtpFromGmail(String email, String appPassword) throws Exception {
        System.out.println("📩 Connecting to Gmail...");
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");

        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", email, appPassword);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        // Look for unread emails first
        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        // Scan last 5 messages
        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];
            String subject = msg.getSubject();

            // Filter for Naukri OTP emails
            if (subject != null && subject.toLowerCase().contains("otp") && msg.getFrom()[0].toString().toLowerCase().contains("naukri")) {
                System.out.println("Found OTP Email: " + subject);

                // Extract Body
                String content = "";
                if (msg.getContent() instanceof String) {
                    content = (String) msg.getContent();
                } else if (msg.getContent() instanceof Multipart) {
                    Multipart mp = (Multipart) msg.getContent();
                    BodyPart bp = mp.getBodyPart(0);
                    content = bp.getContent().toString();
                }

                // Find 6 digit number
                Pattern p = Pattern.compile("\\b\\d{6}\\b");
                Matcher m = p.matcher(content);

                if (m.find()) {
                    return m.group(0);
                }
            }
            if (i < messages.length - 5) break;
        }
        return null;
    }

    public static void takeScreenshot(WebDriver driver, String fileName) {
        try {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(scrFile, new File(fileName));
            System.out.println("📸 Screenshot saved: " + fileName);
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }
}