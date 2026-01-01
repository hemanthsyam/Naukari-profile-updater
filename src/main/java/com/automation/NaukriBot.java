package com.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

public class NaukriBot {

    public static void main(String[] args) {
        String username = System.getenv("NAUKRI_USER");
        String password = System.getenv("NAUKRI_PASS");
        String apiKey = System.getenv("GEMINI_KEY");

        String newHeadline = generateHeadline(apiKey);
        updateProfile(username, password, newHeadline);
    }

    public static String generateHeadline(String apiKey) {
        String prompt = "Write a resume headline for an SDET with 3 years experience. " +
                "Keywords to include: Java, Selenium, Rest Assured, TestNG, Jenkins, API Automation. " +
                "Limit to 200 characters. No intro, just the headline text.";

        String requestBody = "{ \"contents\": [{ \"parts\": [{ \"text\": \"" + prompt + "\" }] }] }";

        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .queryParam("key", apiKey)
                .body(requestBody)
                .post("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent");

        String responseString = response.getBody().asString();
        JsonObject json = JsonParser.parseString(responseString).getAsJsonObject();

        String generatedText = json.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();

        return generatedText.replace("\n", " ").trim();
    }

    public static void updateProfile(String username, String password, String headline) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            page.navigate("https://www.naukri.com/nlogin/login");
            page.getByPlaceholder("Enter your active Email ID / Username").fill(username);
            page.getByPlaceholder("Enter your password").fill(password);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
            page.waitForURL("**/mnjuser/homepage");

            page.navigate("https://www.naukri.com/mnjuser/profile");

            page.locator(".resume-headline-container .edit.icon").click();

            Locator textarea = page.locator("textarea#resumeHeadlineTxt");
            textarea.clear();
            textarea.fill(headline);

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();

            System.out.println("Profile updated with: " + headline);

            browser.close();
        }
    }
}