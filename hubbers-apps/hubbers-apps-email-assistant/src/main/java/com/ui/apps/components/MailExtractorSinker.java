package com.ui.apps.components;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ui.apps.mail.IMailMessageSinker;
import com.ui.apps.mail.MailProcessor;
import com.ui.apps.utils.FileNameHashGenerator;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class MailExtractorSinker implements IMailMessageSinker  {
	
	String rootDirectory;
	
	@Override
    public void process(Message message) throws MessagingException, IOException {
		
		//String uuid = UUID.randomUUID().toString();
		String uuid = FileNameHashGenerator.generateSafeFileName(InternetAddress.toString(message.getFrom()) + message.getSubject());
		String workFolder = "%s/%s".formatted(rootDirectory, uuid);
		File file = new File(workFolder);
		if ( file.exists()) return ;
		file.mkdirs();
		
    	log.debug("Processing message[{}] to folder[{}]", 
    			message.getSubject(), workFolder);
    	Map<String, String> files = new HashMap<>(); 
    	
    	Address[] fromAddress = message.getFrom();
		String subject = message.getSubject();
		String sentDate = message.getSentDate().toString();
		String contentType = message.getContentType();
		
		String messageContent = null;

		if (contentType.contains("multipart")) {
			
			Multipart multiPart = (Multipart) message.getContent();
			int numberOfParts = multiPart.getCount();
			
			for (int partCount = 0; partCount < numberOfParts; partCount++) {
				MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
				
				if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
					
					String fileName = part.getFileName();
					String name = fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					part.saveFile(workFolder + File.separator + name);
					files.put(name, workFolder + File.separator + name);
					
					
				} 
			}

		} 
		
		messageContent  = MailProcessor.processMessage(message);
	
		log.debug("From: [{}] Subject [{}] Date[{}] Content[{}] Files [{}]",  
    			InternetAddress.toString(fromAddress), 
    			subject,
    			sentDate,
    			contentType, 
    			String.join(",",files.keySet()));
		
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("From", InternetAddress.toString(fromAddress));
		metadata.put("Subject", subject);
		metadata.put("sentDate", sentDate);
		metadata.put("contentType", contentType);
		
		try (Writer writer = new FileWriter(workFolder + File.separator + "metadata.json")) {
		    Gson gson = new GsonBuilder().create();
		    gson.toJson(metadata, writer);
		}
		
		
		String fileContent = workFolder + File.separator + "content.txt";
		
		 
		
		try (Writer out = new FileWriter(fileContent)) {
			out.write(messageContent);
			
		}

    }
	
	
    	
}
