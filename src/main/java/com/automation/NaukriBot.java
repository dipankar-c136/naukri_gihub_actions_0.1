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

            if (username == null || password == null) throw new RuntimeException("Credentials missing!");

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField"))).sendKeys(username);
            driver.findElement(By.id("passwordField")).sendKeys(password);

            // --- 2. CLICK LOGIN ---
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            System.out.println("✅ Login clicked. Checking for OTP wall...");
            Thread.sleep(5000);

            // --- 3. OTP HANDLING ---
            List<WebElement> otpInputs = driver.findElements(By.cssSelector("input[type='tel']"));

            if (!otpInputs.isEmpty()) {
                System.out.println("🚨 OTP Screen Detected! (Found " + otpInputs.size() + " input boxes)");
                takeScreenshot(driver, "03_otp_required.png");

                String gmailUser = System.getenv("GMAIL_USERNAME");
                String gmailPass = System.getenv("GMAIL_APP_PASSWORD");

                // Wait for the FRESH email to arrive (20 seconds)
                System.out.println("⏳ Waiting 20s for NEW email to arrive...");
                Thread.sleep(20000);

                String otp = getOtpFromGmail(gmailUser, gmailPass);

                if (otp != null) {
                    System.out.println("✅ Extracted OTP: " + otp);

                    char[] digits = otp.toCharArray();
                    for (int i = 0; i < otpInputs.size() && i < digits.length; i++) {
                        otpInputs.get(i).sendKeys(String.valueOf(digits[i]));
                    }

                    System.out.println("Typing complete. Submitting...");

                    // Try to find Verify button (sometimes it auto-submits)
                    try {
                        List<WebElement> buttons = driver.findElements(By.tagName("button"));
                        for (WebElement btn : buttons) {
                            if (btn.getText().toLowerCase().contains("verify")) {
                                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Verify click skipped: " + e.getMessage());
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
            takeScreenshot(driver, "ERROR_crash.png");
            System.exit(1);
        } finally {
            driver.quit();
        }
    }

    // --- ROBUST EMAIL PARSER (UPDATED FOR YOUR .EML FILE) ---
    public static String getOtpFromGmail(String email, String appPassword) throws Exception {
        System.out.println("📩 Connecting to Gmail...");
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");

        Session session = Session.getDefaultInstance(props, null);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", email, appPassword);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        // Fetch messages
        Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        System.out.println("🔍 Found " + messages.length + " unread emails.");

        // Check the last 5 emails (Newest first)
        for (int i = messages.length - 1; i >= 0; i--) {
            Message msg = messages[i];
            String subject = msg.getSubject();

            // Based on your uploaded file, this is the EXACT subject to look for:
            if (subject != null && subject.contains("Your OTP for logging in Naukri account")) {
                System.out.println("🎯 Found Target Email: " + subject);

                // Get CLEAN text (strip HTML)
                String content = getTextFromMessage(msg);

                // Look for 6 digits (e.g., 123456)
                Pattern p = Pattern.compile("\\b\\d{6}\\b");
                Matcher m = p.matcher(content);

                if (m.find()) {
                    return m.group(0);
                } else {
                    System.out.println("⚠️ Found email but Regex failed. Content preview: " +
                            (content.length() > 50 ? content.substring(0, 50) : content));
                }
            }
            if (i < messages.length - 5) break;
        }
        return null;
    }

    // --- RECURSIVE CONTENT EXTRACTOR ---
    private static String getTextFromMessage(Part p) throws Exception {
        if (p.isMimeType("text/plain")) {
            return (String) p.getContent();
        }
        else if (p.isMimeType("text/html")) {
            String html = (String) p.getContent();
            // Convert HTML tags to spaces so numbers don't get stuck together
            return html.replaceAll("<[^>]*>", " ");
        }
        else if (p.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) p.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < mimeMultipart.getCount(); i++) {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                result.append(getTextFromMessage(bodyPart));
            }
            return result.toString();
        }
        return "";
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