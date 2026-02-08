package com.automation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class NaukriBot {

    public static void main(String[] args) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");
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

            System.out.println("Page Title: " + driver.getTitle());
            takeScreenshot(driver, "01_homepage.png");

            String username = System.getenv("NAUKRI_EMAIL");
            String password = System.getenv("NAUKRI_PASSWORD");

            if (username == null || password == null) {
                throw new RuntimeException("❌ ERROR: Credentials not found in Environment Variables!");
            }

            System.out.println("Typing credentials...");
            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.sendKeys(username);

            WebElement passField = driver.findElement(By.id("passwordField"));
            passField.sendKeys(password);

            // --- DEBUG: HIGHLIGHT AND CLICK ---
            try {
                // 1. Find the button
                WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.blue-btn")));

                // 2. HIGHLIGHT IT (Red Border + Yellow Background)
                highlightElement(driver, loginButton);
                System.out.println("Element Highlighted. Taking snapshot...");

                // 3. Take the visual proof
                takeScreenshot(driver, "02_target_highlighted.png");

                // 4. Click
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loginButton);
                System.out.println("Click action performed.");

            } catch (Exception e) {
                System.err.println("⚠️ Could not click login button normally: " + e.getMessage());
                takeScreenshot(driver, "ERROR_login_click_failed.png");
                throw e;
            }

            System.out.println("✅ Login clicked. Waiting for transition...");
            Thread.sleep(8000);

            takeScreenshot(driver, "03_result_after_click.png");

            if (driver.getCurrentUrl().contains("mnjuser")) {
                System.out.println("🎉 SUCCESS: Login successful, reached dashboard!");
            } else {
                System.out.println("⚠️ NOTICE: Check '03_result_after_click.png'. If it shows OTP, the click was correct but security blocked you.");
            }

        } catch (Exception e) {
            System.err.println("❌ Critical Error: " + e.getMessage());
            takeScreenshot(driver, "ERROR_failure.png");
            System.exit(1);
        } finally {
            driver.quit();
        }
    }

    // --- HELPER METHOD: Highlighting ---
    public static void highlightElement(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].setAttribute('style', 'border: 5px solid red; background: yellow; color: black;');", element);
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