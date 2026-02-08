package com.automation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class NaukriBot {

    public static void main(String[] args) {
        // Setup WebDriverManager to handle the browser driver automatically
        WebDriverManager.chromedriver().setup();

        // --- STEALTH OPTIONS ---
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new"); // Run in background (CI mode)
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080"); // FIX: Crucial for avoiding blank screenshots
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);

        // Hide WebDriver flag
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        try {
            System.out.println("🚀 Starting Naukri Bot...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // --- FIX 1: Prevent Blank Screenshot ---
            // Wait for the body to be visible so we know the page rendered
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

            System.out.println("Page Title: " + driver.getTitle());
            takeScreenshot(driver, "01_homepage.png");

            // --- GET CREDENTIALS ---
            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            if (username == null || password == null) {
                throw new RuntimeException("❌ ERROR: Credentials not found in Environment Variables!");
            }

            // --- LOGIN LOGIC ---
            System.out.println("Typing credentials...");
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.sendKeys(username);

            WebElement passField = driver.findElement(By.id("passwordField"));
            passField.sendKeys(password);

            // --- CLICK LOGIN (NOT OTP) ---
            try {
                // Wait for the specific "Login" button (class="blue-btn")
                // We use CSS Selector because it's faster and cleaner for classes
                WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));

                System.out.println("Found Login Button. Clicking...");

                // Use JS Click to avoid overlays (like Recaptcha or cookie banners)
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            } catch (Exception e) {
                System.err.println("⚠️ Could not click login button normally: " + e.getMessage());
                takeScreenshot(driver, "ERROR_login_click_failed.png");
                throw e; // Re-throw to stop script if we can't click
            }

            System.out.println("✅ Login clicked. Waiting for transition...");

            // Wait a bit to see if we get redirected or hit an OTP wall
            Thread.sleep(8000);

            takeScreenshot(driver, "02_after_login.png");

            // Check if we are on the dashboard
            if (driver.getCurrentUrl().contains("mnjuser")) {
                System.out.println("🎉 SUCCESS: Login successful, reached dashboard!");
            } else {
                System.out.println("⚠️ NOTICE: Still on login page. Check '02_after_login.png' for OTP or errors.");
            }

        } catch (Exception e) {
            System.err.println("❌ Critical Error: " + e.getMessage());
            takeScreenshot(driver, "ERROR_failure.png");
            System.exit(1);
        } finally {
            driver.quit();
        }
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