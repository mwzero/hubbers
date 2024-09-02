package com.hubbers.core.agents;

import java.time.LocalDate;
import java.util.List;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import lombok.Builder;

import static java.util.Arrays.asList;

@Builder
public class Recipe {
	
	static ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            .format("json")
            .build();
	
	static class RecipeProcessor {

        @Description("short title, 3 words maximum")
        private String title;

        @Description("short description, 2 sentences maximum")
        private String description;

        @Description("each step should be described in 4 words, steps should rhyme")
        private List<String> steps;

    }
	
	@StructuredPrompt("Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}")
    static class CreateRecipePrompt {

        private String dish;
        private List<String> ingredients;
    }

    interface Chef {

        Recipe createRecipeFrom(String... ingredients);

        Recipe createRecipe(CreateRecipePrompt prompt);
    }

    public static void main(String[] args) {

        Chef chef = AiServices.create(Chef.class, chatLanguageModel);

        Recipe recipe = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

        System.out.println(recipe);
        // Recipe {
        //     title = "Greek Salad",
        //     description = "A refreshing mix of veggies and feta cheese in a zesty dressing.",
        //     steps = [
        //         "Chop cucumber and tomato",
        //         "Add onion and olives",
        //         "Crumble feta on top",
        //         "Drizzle with dressing and enjoy!"
        //     ],
        //     preparationTimeMinutes = 10
        // }


        CreateRecipePrompt prompt = new CreateRecipePrompt();
        prompt.dish = "salad";
        prompt.ingredients = asList("cucumber", "tomato", "feta", "onion", "olives");

        Recipe anotherRecipe = chef.createRecipe(prompt);
        System.out.println(anotherRecipe);
        // Recipe ...
    }

}
