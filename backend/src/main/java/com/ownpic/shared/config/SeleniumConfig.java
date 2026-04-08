package com.ownpic.shared.config;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SeleniumConfig {

    @Bean(destroyMethod = "quit")
    public ChromeDriver chromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--disable-blink-features=AutomationControlled",
                "--window-position=-9999,-9999"
        );

        ChromeDriver driver = new ChromeDriver(options);
        driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                Map.of("source", "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });")
        );
        return driver;
    }
}