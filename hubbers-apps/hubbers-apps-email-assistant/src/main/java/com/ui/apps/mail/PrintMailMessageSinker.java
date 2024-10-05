package com.ui.apps.mail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrintMailMessageSinker implements IMailMessageSinker  {
	
    public void process(Message message) throws MessagingException, IOException {
    	
    	List<String> files = new ArrayList<>(); 
    	
    	Address[] fromAddress = message.getFrom();
		String subject = message.getSubject();
		String sentDate = message.getSentDate().toString();
		String contentType = message.getContentType();
		
		
		
		String messageContent = "";

		if (contentType.contains("multipart")) {
			
			Multipart multiPart = (Multipart) message.getContent();
			int numberOfParts = multiPart.getCount();
			
			for (int partCount = 0; partCount < numberOfParts; partCount++) {
				MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
				if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
					// this part is attachment
					String fileName = part.getFileName();
					String name = fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					files.add(name);
					
				} else {
					// this part may be the message content
					messageContent = part.getContent().toString();
				}
			}

		} else if (contentType.contains("text/plain") || contentType.contains("text/html")) {
			Object content = message.getContent();
			if (content != null) {
				messageContent = content.toString();
			}
		}

		log.debug("From: [{}] Subject [{}] [{}] [{}] [{}]",  
    			InternetAddress.toString(fromAddress), 
    			subject,
    			sentDate,
    			contentType, 
    			String.join(",",files));
    }
}
