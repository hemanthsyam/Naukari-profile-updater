package com.automation;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
        ChromeOptions options = new ChromeOptions();

        if (headlessMode) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }

        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--dns-prefetch-disable");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        logger.info("WebDriver initialized in " + (headlessMode ? "headless" : "normal") + " mode");
    }

    public boolean isPeakTime() {
        int currentHour = LocalDateTime.now().getHour();
        for (int hour : PEAK_HOURS) {
            if (currentHour == hour) {
                return true;
            }
        }
        return false;
    }

    public void login() throws InterruptedException {
        try {
            logger.info("Starting login process...");
            driver.get("https://www.naukri.com/nlogin/login");
            Thread.sleep(3000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            WebElement emailField = wait.until(ExpectedConditions.elementToBeClickable(By.id("usernameField")));
            emailField.clear();
            emailField.sendKeys(username);
            Thread.sleep(1000);

            WebElement passwordField = driver.findElement(By.id("passwordField"));
            passwordField.clear();
            passwordField.sendKeys(password);
            Thread.sleep(1000);

            WebElement loginButton = driver.findElement(By.xpath("//button[contains(text(),'Login')]"));
            loginButton.click();

            Thread.sleep(6000);

            if (driver.getCurrentUrl().contains("nlogin")) {
                throw new RuntimeException("Login failed - still on login page");
            }

            logger.info("Login successful");

        } catch (Exception e) {
            logger.severe("Login failed: " + e.getMessage());
            throw new RuntimeException("Login failed", e);
        }
    }

    public String getCurrentProfile() throws InterruptedException {
        try {
            driver.get("https://www.naukri.com/mnjuser/profile");
            Thread.sleep(4000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            WebElement headline = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[@class='widgetCont']/div/div")));

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
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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
            logger.info("Gemini API response code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                JsonObject jsonResponse = new Gson().fromJson(response.toString(), JsonObject.class);
                String generatedText = jsonResponse.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString().trim();

                generatedText = cleanAIResponse(generatedText);

                logger.info("AI Generated content: " + generatedText);
                return generatedText;
            } else {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(errorStream, "utf-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.warning("API Error: " + errorResponse.toString());
                }

                logger.warning("Gemini API failed with code: " + responseCode + ", using fallback");
                return createSmartVariation(currentContent);
            }

        } catch (Exception e) {
            logger.severe("Error calling Gemini API: " + e.getMessage());
            e.printStackTrace();
            return createSmartVariation(currentContent);
        }
    }

    private String buildPrompt(String currentContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert resume writer for Indian job portals like Naukri.com.\n\n");
        prompt.append("Current resume headline:\n");
        prompt.append(currentContent).append("\n\n");
        prompt.append("Task: Create a slightly different version of this headline that:\n");
        prompt.append("1. Keeps ALL the core experience, years, and role (SDET, QA, etc.) EXACTLY as mentioned\n");
        prompt.append("2. Rearranges 2-3 skills in different order to make it fresh\n");
        prompt.append("3. Replaces ONE technology with its modern alternative:\n");
        prompt.append("   - Selenium → Playwright or Cypress\n");
        prompt.append("   - RestAssured → Postman or GraphQL\n");
        prompt.append("   - Jenkins → GitHub Actions or GitLab CI\n");
        prompt.append("4. Adds ONE trending skill if space allows: K6, Grafana, Prometheus, ArgoCD, Terraform, GenAI Testing\n");
        prompt.append("5. Maintains the pipe (|) separator format\n");
        prompt.append("6. Stays under 240 characters total\n");
        prompt.append("7. Keeps it professional and recruiter-friendly\n\n");
        prompt.append("IMPORTANT: Return ONLY the new headline text, no explanations, no quotes, no markdown, no extra text.\n");
        prompt.append("Do NOT add phrases like 'Here is' or 'Optimized headline:' - just give me the headline directly.\n");

        return prompt.toString();
    }

    private String cleanAIResponse(String text) {
        text = text.replaceAll("```.*?```", "").trim();
        text = text.replaceAll("^['\"`]+|['\"`]+$", "").trim();
        text = text.replaceAll("(?i)^(here is|here's|optimized headline:|new headline:)\\s*", "").trim();
        text = text.replaceAll("\\n+", " ").trim();

        if (text.length() > 240) {
            String[] parts = text.split("\\|");
            while (text.length() > 240 && parts.length > 1) {
                parts = Arrays.copyOf(parts, parts.length - 1);
                text = String.join(" | ", parts).trim();
            }
        }

        return text;
    }

    private String createSmartVariation(String content) {
        logger.info("Creating smart local variation...");

        if (!content.contains("|")) {
            return content + " | Cloud Technologies";
        }

        String[] segments = content.split("\\|");
        List<String> segmentList = new ArrayList<>();
        for (String seg : segments) {
            segmentList.add(seg.trim());
        }

        String[][] replacements = {
                {"Selenium", "Playwright"},
                {"RestAssured", "Postman"},
                {"Jenkins", "GitHub Actions"},
                {"Manual Testing", "Exploratory Testing"},
                {"API Testing", "GraphQL Testing"}
        };

        Random random = new Random();
        for (String[] replacement : replacements) {
            for (int i = 0; i < segmentList.size(); i++) {
                if (segmentList.get(i).contains(replacement[0]) && random.nextBoolean()) {
                    segmentList.set(i, segmentList.get(i).replace(replacement[0], replacement[1]));
                    logger.info("Replaced: " + replacement[0] + " → " + replacement[1]);
                    break;
                }
            }
        }

        if (segmentList.size() > 3 && random.nextBoolean()) {
            int idx1 = 1 + random.nextInt(Math.min(3, segmentList.size() - 2));
            int idx2 = 1 + random.nextInt(Math.min(3, segmentList.size() - 2));
            if (idx1 != idx2) {
                Collections.swap(segmentList, idx1, idx2);
                logger.info("Swapped positions: " + idx1 + " ↔ " + idx2);
            }
        }

        String result = String.join(" | ", segmentList);

        if (result.length() > 240) {
            segmentList.remove(segmentList.size() - 1);
            result = String.join(" | ", segmentList);
        }

        logger.info("Smart variation created: " + result);
        return result;
    }

    public void updateProfile(String optimizedContent) throws InterruptedException {
        try {
            logger.info("Updating profile with new content...");

            driver.get("https://www.naukri.com/mnjuser/profile");
            Thread.sleep(4000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            WebElement editButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[@class='edit icon']")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", editButton);
            Thread.sleep(1000);
            editButton.click();
            Thread.sleep(2000);

            WebElement headlineField = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("resumeHeadlineTxt")));

            headlineField.clear();
            Thread.sleep(1000);
            headlineField.sendKeys(Keys.CONTROL + "a");
            headlineField.sendKeys(Keys.DELETE);
            Thread.sleep(500);
            headlineField.sendKeys(optimizedContent);
            Thread.sleep(1000);

            WebElement saveButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='submit' and contains(text(),'Save')]")));

            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", saveButton);
            Thread.sleep(4000);

            logger.info("✅ Profile updated successfully! Naukri will show 'Recently Updated'");

        } catch (Exception e) {
            logger.severe("Profile update failed: " + e.getMessage());
            e.printStackTrace();
            takeScreenshot("update_failed");
            throw new RuntimeException("Update failed", e);
        }
    }

    private void takeScreenshot(String filename) {
        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            File source = ts.getScreenshotAs(OutputType.FILE);
            File dest = new File(filename + "_" + System.currentTimeMillis() + ".png");
            org.apache.commons.io.FileUtils.copyFile(source, dest);
            logger.info("Screenshot saved: " + dest.getAbsolutePath());
        } catch (Exception e) {
            logger.warning("Failed to take screenshot: " + e.getMessage());
        }
    }

    public void performDailyUpdate() {
        try {
            logger.info("=== Starting Daily Naukri Profile Update ===");
            logger.info("Timestamp: " + LocalDateTime.now());
            logger.info("Peak time check: " + isPeakTime());

            initializeDriver();
            login();

            String currentProfile = getCurrentProfile();
            String optimizedContent = generateOptimizedContent(currentProfile);

            if (!currentProfile.equals(optimizedContent)) {
                updateProfile(optimizedContent);
                logger.info("✅ Update completed successfully!");
            } else {
                logger.warning("Generated content same as current, creating forced variation...");
                optimizedContent = createSmartVariation(currentProfile);
                updateProfile(optimizedContent);
                logger.info("✅ Forced update completed!");
            }

            logger.info("=== Daily Update Completed Successfully ===");

        } catch (Exception e) {
            logger.severe("❌ Daily update failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
                logger.info("WebDriver closed");
            }
        }
    }

    public static void main(String[] args) {
        String username = System.getenv("NAUKRI_USERNAME");
        String password = System.getenv("NAUKRI_PASSWORD");
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String headlessModeStr = System.getenv("HEADLESS_MODE");

        boolean headlessMode = headlessModeStr != null && headlessModeStr.equalsIgnoreCase("true");

        if (username == null || password == null || geminiKey == null) {
            System.err.println("Error: Environment variables not set!");
            System.err.println("Required: NAUKRI_USERNAME, NAUKRI_PASSWORD, GEMINI_API_KEY");
            System.exit(1);
        }

        NaukriProfileUpdater updater = new NaukriProfileUpdater(username, password, geminiKey, headlessMode);
        updater.performDailyUpdate();
    }
}