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
import java.util.stream.Collectors;

public class NaukriProfileUpdater {

    private static final Logger logger = Logger.getLogger(NaukriProfileUpdater.class.getName());
    private WebDriver driver;
    private WebDriverWait wait;
    private String username;
    private String password;
    private String geminiApiKey;
    private boolean headlessMode;

    // CRITICAL SKILLS LIST - These will NEVER be removed
    private static final List<String> REQUIRED_KEYWORDS = Arrays.asList(
            "Selenium", "Playwright", "RestAssured", "Postman", "Java"
    );

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

            WebElement headline = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@class='widgetCont']//span[contains(@class, 'resume-headline')] | //div[@class='widgetCont']/div/div")));

            String currentHeadline = headline.getText().trim();
            logger.info("Current headline: " + currentHeadline);
            return currentHeadline;

        } catch (Exception e) {
            logger.warning("Could not extract profile: " + e.getMessage());
            // Fallback default if extraction fails
            return "SDET | Java | Selenium | Playwright | RestAssured | Postman | Automation Engineer";
        }
    }

    public String generateOptimizedContent(String currentContent) {
        try {
            logger.info("Calling Gemini AI for content optimization...");

            // NOTE: Using gemini-1.5-flash. Ensure your API Key supports this model.
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + geminiApiKey);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String prompt = buildPrompt(currentContent);
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

                String cleanText = cleanAIResponse(generatedText);

                // SAFETY CHECK: If AI removed a critical skill, discard AI result and use Shuffle
                if (!containsAllCriticalSkills(cleanText)) {
                    logger.warning("AI response missing critical skills. Discarding and using Shuffle.");
                    return createSmartVariation(currentContent);
                }

                return cleanText;

            } else {
                logger.warning("Gemini API failed with code: " + responseCode + ", using Smart Shuffle Fallback");
                return createSmartVariation(currentContent);
            }

        } catch (Exception e) {
            logger.severe("Error calling Gemini API: " + e.getMessage());
            return createSmartVariation(currentContent);
        }
    }

    public void updateProfile(String optimizedContent) {
        try {
            logger.info("Updating profile...");

            if (optimizedContent == null || optimizedContent.length() < 10) {
                logger.warning("Generated content too short. Skipping update.");
                return;
            }

            // FINAL SAFETY CHECK
            if (!containsAllCriticalSkills(optimizedContent)) {
                logger.severe("ABORTING UPDATE: Optimized content is missing critical skills!");
                return;
            }

            driver.get("https://www.naukri.com/mnjuser/profile");

            WebElement editButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//span[contains(@class,'edit icon')]")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", editButton);
            wait.until(ExpectedConditions.elementToBeClickable(editButton));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editButton);

            WebElement headlineField = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.id("resumeHeadlineTxt")));

            headlineField.clear();
            headlineField.sendKeys(Keys.CONTROL + "a");
            headlineField.sendKeys(Keys.DELETE);
            headlineField.sendKeys(optimizedContent);

            WebElement saveButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='submit' and contains(text(),'Save')]")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", saveButton);
            wait.until(ExpectedConditions.invisibilityOf(saveButton));

            logger.info("✅ Profile updated successfully!");

        } catch (Exception e) {
            logger.severe("Profile update failed: " + e.getMessage());
            takeScreenshot("update_failed");
            throw new RuntimeException("Update failed", e);
        }
    }

    private String buildPrompt(String currentContent) {
        return "Rewrite this Naukri headline slightly to change the word order, but KEEP THESE KEYWORDS EXACTLY: " +
                "Selenium, Playwright, RestAssured, Postman, Java. \n" +
                "Current: " + currentContent + "\n" +
                "Rules: Keep under 250 chars. Use '|' separator. No markdown. RETURN ONLY THE TEXT.";
    }

    private String cleanAIResponse(String text) {
        return text.replaceAll("```.*?```", "").replaceAll("^['\"`]+|['\"`]+$", "").trim();
    }

    /**
     * Checks if all critical keywords are present (Case Insensitive)
     */
    private boolean containsAllCriticalSkills(String content) {
        String lowerContent = content.toLowerCase();
        for (String skill : REQUIRED_KEYWORDS) {
            if (!lowerContent.contains(skill.toLowerCase())) {
                logger.warning("Missing critical skill: " + skill);
                return false;
            }
        }
        return true;
    }

    /**
     * Fallback Logic: Just SHUFFLE the order.
     * NEVER REPLACE skills.
     */
    private String createSmartVariation(String content) {
        logger.info("Generating Smart Variation (Shuffle Strategy)...");

        List<String> parts = new ArrayList<>(Arrays.asList(content.split("\\|")));
        parts = parts.stream().map(String::trim).collect(Collectors.toList());

        // Pure Shuffle - No replacements
        Collections.shuffle(parts);

        String result = String.join(" | ", parts);

        // Verify we didn't accidentally lose anything (unlikely in shuffle, but good to be safe)
        if (!containsAllCriticalSkills(result)) {
            logger.warning("Shuffle validation failed. Returning original.");
            return content;
        }

        logger.info("New Variation: " + result);
        return result;
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

        if (username == null || password == null) {
            System.err.println("❌ Error: Missing Environment Variables");
            System.exit(1);
        }

        boolean headless = headlessModeStr != null && headlessModeStr.equalsIgnoreCase("true");
        new NaukriProfileUpdater(username, password, geminiKey, headless).performDailyUpdate();
    }
}
