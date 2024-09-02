

import com.hubbers.ConfifurationEnvironment;
import com.hubbers.recipes.ScrapeAndSummarizeRecipe;

public class ScrapeAndSummarizeRecipeRunner  {

	public static void main(String... args) throws Exception {
		
		ConfifurationEnvironment.settingProxy();

		ScrapeAndSummarizeRecipe
			.builder()
			.url("https://jsoup.org/cookbook/extracting-data/attributes-text-html")
			.build()
			.execute();
	}
	
}