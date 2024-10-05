package com.ui.apps.mail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MailProcessor {

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
			if (contentType.contains("text/plain")) {
				// Parte in testo semplice
				return part.getContent().toString();
			} else if (contentType.contains("text/html")) {
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

			if (contentType.contains("text/plain")) {
				plainText = (String) part.getContent();
			} else if (contentType.contains("text/html")) {
				htmlText = (String) part.getContent();
			}
		}

		/*if (plainText != null) {
			return plainText;
		} else*/ if (htmlText != null) {
			return tika_autoParser(htmlText);
			
		}
		return htmlText;
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