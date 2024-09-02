package com.hubbers.recipes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hubbers.core.Agent;
import com.hubbers.core.Task;
import com.hubbers.core.model.AgentResponse;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class ScrapeAndSummarizeRecipe {
	
	String url;

	public void execute() throws Exception {

		//
		log.info("Download url[{}]", url);
		String body = getTextFromUrl(url, "c:\\temp\\file.html");
		
		Agent researcher = Agent.builder()
				.role("Principal Researcher")
				.goal("Do amazing research and summaries based on the content you are working with")
				.backstory("You're a Principal Researcher at a big company and you need to do research about a given topic.")
				.verbose(true)
				.memory(true)
				.build();

		Task task = Task.builder()
					.agent(researcher)
					.description("Analyze and summarize the content below, make sure to include the most relevant information in the summary, return only the summary nothing else.\\n\\nCONTENT\\n----------\\n{{it}}'")
					.expectedOutput("A summary")
					.build();
		
		Map<String, String> inputs = new HashMap<String, String>();
		inputs.put("{it}", body);
		AgentResponse output = task.execute(inputs);
		System.out.println(output.getResponse());
		
		
	}
	
	
	private String getTextFromUrl(String url, String file) throws IOException {
		
		readHTML(url, file);
		
		Document doc = Jsoup.parse(new File(file));
		String text = doc.body().text(); 
		return text;
    }
	
	private String readHTML(String urlString, String saveHtmlFilePath) throws IOException {

        //Create a URL object
        URL url = new URL(urlString);

        //Connect and open the URL
        URLConnection connection = url.openConnection();

        // Save the URL as an HTML file
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveHtmlFilePath), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line);
            writer.newLine();
        }

        reader.close();
        writer.close();

        // Return the file path of the HTML file
        return saveHtmlFilePath;
    }

}