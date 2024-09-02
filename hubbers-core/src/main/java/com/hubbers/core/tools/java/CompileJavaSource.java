package com.hubbers.core.tools.java;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.DiagnosticCollector;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class CompileJavaSource {
	
    public static void process(String response) {
    	
    	String sourceCode = JavaCodeExtractor.extract(response).getResponse();
        

        // 2. Nome del file sorgente
        String fileName = "HttpInvoker.java";

        // 3. Creazione del file sorgente
        File sourceFile = new File(fileName);
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(sourceCode);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 4. Ottenimento del compilatore
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // 5. Preparazione del diagnostico
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // 6. Compilazione del file sorgente
        int compilationResult = compiler.run(null, null, null, fileName);

        if (compilationResult == 0) {
            System.out.println("Compilazione riuscita!");
        } else {
            System.out.println("Compilazione fallita!");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.out.println(diagnostic.getMessage(null));
            }
        }

        // 7. Cancellazione del file sorgente
        if (sourceFile.exists() && sourceFile.delete()) {
            System.out.println("File sorgente eliminato.");
        } else {
            System.out.println("Impossibile eliminare il file sorgente.");
        }
    }
}
