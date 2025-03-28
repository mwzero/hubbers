package com.hubbers.tasks.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

public class PlaywrightTask {
	
	public static void main(String[] args) {
		
        try (Playwright playwright = Playwright.create()) {
        	
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            page.navigate("http://playwright.dev");
            
            String markdown = convertToMarkdown(page.content());

            System.out.println(markdown);
            
            System.out.println(page.title());
        }
    }
	
	private static String convertToMarkdown(String html) {
        FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
        return converter.convert(html);
    }
	
	private static String convertToText(String html) {
        // Utilizzare una libreria come flexmark-java per la conversione
        // Aggiungi la dipendenza nel tuo build.gradle o pom.xml
        // implementation 'com.vladsch.flexmark:flexmark-all:0.62.2'
        return org.jsoup.Jsoup.parse(html).text();
    }
	
	

}
