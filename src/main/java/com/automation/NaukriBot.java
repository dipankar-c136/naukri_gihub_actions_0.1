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
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);

        // Additional JS injection to hide WebDriver flag
        //((ChromeDriver) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        try {
            System.out.println("🚀 Starting Naukri Bot...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // --- DEBUG: Print Title ---
            System.out.println("Page Title: " + driver.getTitle());
            takeScreenshot(driver, "01_homepage.png");

            // --- GET CREDENTIALS FROM GITHUB SECRETS ---
            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            if (username == null || password == null) {
                throw new RuntimeException("❌ ERROR: Credentials not found in Environment Variables!");
            }

            // --- LOGIN LOGIC ---
            // Note: Update these locators if Naukri changes them
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.sendKeys(username);

            WebElement passField = driver.findElement(By.id("passwordField"));
            passField.sendKeys(password);

            /*WebElement loginBtn = driver.findElement(By.xpath("//button[.='Login']"));
            loginBtn.click();*/

            // LOCATOR STRATEGY:
            // We look for the button that contains the text 'Login' BUT DOES NOT contain 'Use OTP'
            // AND has the class 'blue-btn' (which is the styling for the main button)
            try {
                // 1. Wait for the specific "Login" button to be clickable
                WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button.blue-btn") // This class is unique to the Password Login button
                ));

                // 2. JavaScript Click (Safest way to bypass the Invisible Recaptcha overlay)
                // The <div class="g-recaptcha"> sits right next to the button and can sometimes block standard clicks.
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);

            } catch (Exception e) {
                System.out.println("Could not click login button: " + e.getMessage());
                takeScreenshot(driver, "ERROR_login_click_failed.png");
            }

            System.out.println("✅ Login clicked. Waiting for dashboard...");
            Thread.sleep(5000); // Simple wait for demo purposes

            takeScreenshot(driver, "02_after_login.png");
            System.out.println("🎉 Script Finished Successfully");

        } catch (Exception e) {
            System.err.println("❌ Critical Error: " + e.getMessage());
            takeScreenshot(driver, "ERROR_failure.png");
            // Fail the build if the script fails
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