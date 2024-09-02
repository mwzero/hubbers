package com.hubbers;

import com.hubbers.recipes.ScrapeAndSummarizeRecipe;

public class Application  {

	public static void main(String... args) throws Exception {
		
		ConfifurationEnvironment.settingProxy();

		ScrapeAndSummarizeRecipe
			.builder()
			.url("https://jsoup.org/cookbook/extracting-data/attributes-text-html")
			.build()
			.execute();
	}
	
}