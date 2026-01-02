package com.automation;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;

public class NaukriProfileUpdater {

    private static final Logger logger = Logger.getLogger(NaukriProfileUpdater.class.getName());
    private WebDriver driver;
    private WebDriverWait wait;
    private String username;
    private String password;
    private String geminiApiKey;
    private boolean headlessMode;

    private static final int[] PEAK_HOURS = {4, 8, 17, 20};

    public NaukriProfileUpdater(String username, String password, String geminiApiKey, boolean headlessMode) {
        this.username = username;
        this.password = password;
        this.geminiApiKey = geminiApiKey;
        this.headlessMode = headlessMode;
        setupLogger();
    }

    private void setupLogger() {
        try {
            FileHandler fh = new FileHandler("naukri_automation_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to setup logger: " + e.getMessage());
        }
    }

    public void initializeDriver() {
        // Use WebDriverManager to ensure the correct driver is always available
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        if (headlessMode) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }

        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--disable-notifications");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        // Initialize WebDriverWait globally
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        logger.info("WebDriver initialized in " + (headlessMode ? "headless" : "normal") + " mode");
    }

    public void login() {
        try {
            logger.info("Starting login process...");
            driver.get("https://www.naukri.com/nlogin/login");

            WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usernameField")));
            emailField.clear();
            emailField.sendKeys(username);

            WebElement passwordField = driver.findElement(By.id("passwordField"));
            passwordField.clear();
            passwordField.sendKeys(password);

            WebElement loginButton = driver.findElement(By.xpath("//button[contains(text(),'Login')]"));
            loginButton.click();

            // Wait until URL contains 'mnjuser' (dashboard) or 'homepage'
            // This replaces Thread.sleep(6000)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("mnjuser"),
                    ExpectedConditions.urlContains("homepage")
            ));

            logger.info("Login successful");

        } catch (Exception e) {
            logger.severe("Login failed: " + e.getMessage());
            throw new RuntimeException("Login failed", e);
        }
    }

    public String getCurrentProfile() {
        try {
            driver.get("https://www.naukri.com/mnjuser/profile");

            // Wait for the specific widget container to be visible
            WebElement headline = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@class='widgetCont']//span[contains(@class, 'resume-headline')] | //div[@class='widgetCont']/div/div")));

            String currentHeadline = headline.getText().trim();
            logger.info("Current headline: " + currentHeadline);

            return currentHeadline;

        } catch (Exception e) {
            logger.warning("Could not extract profile: " + e.getMessage());
            return "SDET | Software Quality Assurance | Automation Specialist";
        }
    }

    public String generateOptimizedContent(String currentContent) {
        try {
            logger.info("Calling Gemini AI for content optimization...");

            String prompt = buildPrompt(currentContent);
            // CHANGED: Using gemini-pro instead of flash to avoid 404
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + geminiApiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JsonObject requestBody = new JsonObject();
            com.google.gson.JsonArray contentsArray = new com.google.gson.JsonArray();
            JsonObject contents = new JsonObject();
            com.google.gson.JsonArray partsArray = new com.google.gson.JsonArray();
            JsonObject parts = new JsonObject();

            parts.addProperty("text", prompt);
            partsArray.add(parts);
            contents.add("parts", partsArray);
            contentsArray.add(contents);
            requestBody.add("contents", contentsArray);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = new Gson().toJson(requestBody).getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }

                JsonObject jsonResponse = new Gson().fromJson(response.toString(), JsonObject.class);
                String generatedText = jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString().trim();

                return cleanAIResponse(generatedText);
            } else {
                logger.warning("Gemini API failed with code: " + responseCode + ", using fallback");
                return createSmartVariation(currentContent);
            }

        } catch (Exception e) {
            logger.severe("Error calling Gemini API: " + e.getMessage());
            return createSmartVariation(currentContent);
        }
    }

    public void updateProfile(String optimizedContent) {
        try {
            logger.info("Updating profile with new content...");

            driver.get("https://www.naukri.com/mnjuser/profile");

            // 1. Wait for edit icon and scroll it to CENTER
            WebElement editButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//span[contains(@class,'edit icon')]")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", editButton);

            // 2. Click using JS to avoid ElementClickInterceptedException
            wait.until(ExpectedConditions.elementToBeClickable(editButton));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editButton);

            // 3. Wait for the text area (modal animation)
            WebElement headlineField = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.id("resumeHeadlineTxt")));

            // Clear and Type
            headlineField.clear();
            // A small delay is sometimes needed for JS 'clear' events to register on complex forms
            headlineField.sendKeys(Keys.CONTROL + "a");
            headlineField.sendKeys(Keys.DELETE);
            headlineField.sendKeys(optimizedContent);

            // 4. Click Save
            WebElement saveButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='submit' and contains(text(),'Save')]")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", saveButton);

            // 5. Wait for the success message OR the modal to disappear
            // This confirms the save actually happened
            wait.until(ExpectedConditions.invisibilityOf(saveButton));

            logger.info("✅ Profile updated successfully!");

        } catch (Exception e) {
            logger.severe("Profile update failed: " + e.getMessage());
            takeScreenshot("update_failed");
            throw new RuntimeException("Update failed", e);
        }
    }

    // --- Helper Methods (Prompt, Cleaning, Variation, Screenshot, Main) ---

    private String buildPrompt(String currentContent) {
        return "You are an expert resume optimizer. Rewrite this Naukri headline slightly to make it fresh but keep the same meaning. \n" +
                "Current: " + currentContent + "\n" +
                "Rules: Keep under 250 chars. Use '|' separator. Don't use markdown. return ONLY the headline.";
    }

    private String cleanAIResponse(String text) {
        return text.replaceAll("```.*?```", "").replaceAll("^['\"`]+|['\"`]+$", "").trim();
    }

    private String createSmartVariation(String content) {
        return content.replace("Selenium", "Playwright").replace("Jenkins", "GitHub Actions");
    }

    private void takeScreenshot(String filename) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File dest = new File(filename + "_" + System.currentTimeMillis() + ".png");
            org.apache.commons.io.FileUtils.copyFile(src, dest);
        } catch (IOException e) {
            logger.warning("Screenshot failed");
        }
    }

    public void performDailyUpdate() {
        try {
            initializeDriver();
            login();
            String current = getCurrentProfile();
            String optimized = generateOptimizedContent(current);

            if (!current.equals(optimized)) {
                updateProfile(optimized);
            } else {
                updateProfile(createSmartVariation(current));
            }
        } catch (Exception e) {
            logger.severe("Process failed: " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }
    }

    public static void main(String[] args) {
        String username = System.getenv("NAUKRI_USERNAME");
        String password = System.getenv("NAUKRI_PASSWORD");
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String headlessModeStr = System.getenv("HEADLESS_MODE");

        // Simple check
        if (username == null || password == null) {
            System.err.println("❌ Error: Missing Environment Variables");
            System.exit(1);
        }

        boolean headless = headlessModeStr != null && headlessModeStr.equalsIgnoreCase("true");
        new NaukriProfileUpdater(username, password, geminiKey, headless).performDailyUpdate();
    }
}