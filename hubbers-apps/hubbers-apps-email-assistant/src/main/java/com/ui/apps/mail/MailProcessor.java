package com.ui.apps.mail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MailProcessor {

	public static boolean isHtml (String contentType) {
		
		if (contentType.toUpperCase().contains("TEXT/HTML")) return true;
		
		return false;

	}
	
	public static boolean isText (String contentType) {
		
		if (contentType.toUpperCase().contains("TEXT/PLAIN")) return true;
		
		return false;

	}
	
	public static String mailContentCleaner(
			File fileEmail, 
			boolean preProcess,
			int maxSentences) throws IOException {
		
		String email = new String(Files.readAllBytes(Paths.get(fileEmail.getAbsolutePath() + "/content.txt")));
		if ( preProcess) {
			
			// Rimuovi firme, saluti, risposte citate e footer
	        email = email.replaceAll("--.*$", "");  // Rimuove firme
	        email = email.replaceAll("^Ciao .*?\n", "");  // Rimuove saluti
	        email = email.replaceAll(">.*\n", "");  // Rimuove risposte citate
	        email = email.replaceAll("Questa email .*?\n", "");  // Rimuove avvisi tipici
	        
	        // Converti a minuscolo
	        email = email.toLowerCase();
	        
	        // Rimuovi link e caratteri speciali
	        email = email.replaceAll("http\\S+", "");  // Rimuove URL
	        email = email.replaceAll("[^\\p{L}\\p{N}\\s]", "");  // Rimuove caratteri non alfanumerici
	        email = email.replaceAll("\\s+", " ");  // Rimuove spazi multipli
	        
	        // Unisci elenchi e limita la lunghezza del testo
	        email = email.replaceAll("\\n\\s*[-*•]\\s*", " ");  // Unisce gli elenchi
	        String[] frasi = email.split("\\.");
	        StringBuilder risultato = new StringBuilder();
	        for (int i = 0; i < Math.min(frasi.length, maxSentences); i++) {
	            risultato.append(frasi[i]).append(".");
	        }
	        email = risultato.toString();

		}
		return email.trim();
	}

	public static String processMessage(Message message) throws IOException, MessagingException {
		
		if (message.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) message.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				Part part = multipart.getBodyPart(i);
				return processPart(part);
			}
		} else {
			// Per i messaggi non multipart
			return processPart(message);
		}
		return null;
	}

	private static String processPart(Part part) throws MessagingException, IOException {
		
		if (part.isMimeType("multipart/alternative")) {
			return processMultipartAlternative((Multipart) part.getContent());
		} else if (part.isMimeType("multipart/*")) {
			Multipart multipart = (Multipart) part.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				processPart(multipart.getBodyPart(i));
			}
		} else {
			String contentType = part.getContentType().toLowerCase();
			if ( isText(contentType) ) {
				// Parte in testo semplice
				return part.getContent().toString();
			} else if ( isHtml(contentType) ) {
				// Parte in HTML
				return part.getContent().toString();
			} else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
				// Parte con allegato
				System.out.println("Allegato trovato: " + part.getFileName());
			} else {
				// Altri tipi di contenuto
				System.out.println("Altro tipo di contenuto: " + contentType);
			}
		}
		return null;
	}

	private static String processMultipartAlternative(Multipart multipart) throws MessagingException, IOException {
		
		String plainText = null;
		String htmlText = null;

		for (int i = 0; i < multipart.getCount(); i++) {
			
			Part part = multipart.getBodyPart(i);
			String contentType = part.getContentType().toLowerCase();

			if ( isText(contentType ))
				plainText = (String) part.getContent();
			else if ( isHtml(contentType) ) 
				htmlText = (String) part.getContent();
			
		}

		/*if (plainText != null) {
			return plainText;
		} else*/ if (htmlText != null) {
			return tika_autoParser(htmlText);
			
		}
		return plainText;
	}
	
	public static String tika_autoParser(String htmlContent) {
		String content = null;
		Document document = Jsoup.parse(htmlContent);
		content = document.text();
		/*
		Tika tika = new Tika();

    	InputStream stream = new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8));
        
		try {
			content = tika.parseToString(stream);
		} catch (IOException | TikaException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
        return content;
	}
}