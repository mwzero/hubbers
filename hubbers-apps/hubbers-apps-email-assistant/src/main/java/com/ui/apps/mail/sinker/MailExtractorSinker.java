package com.ui.apps.mail.sinker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.st.DataSet;
import com.ui.apps.mail.utils.MailFileHelper;
import com.ui.apps.mail.utils.MailProcessor;
import com.ui.apps.utils.FileNameHashGenerator;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Extracts mail information and metadata.
 * 
 * all data are persisted over rootDirectory attribute in a folder for each message
 * 
 * body.txt: contains mail body
 * metadata.json contains mail attributes such ad : senders, title, content-type, etc..
 * attches: attachments are persisted in the same folder
 * @return
 */

public class MailExtractorSinker implements IMailMessageSinker  {
	
	@Setter
	String rootDirectory;
	
	@Getter
	DataSet ds;
	
	public MailExtractorSinker() {
		
		log.debug("Initialize DataFrame");
		
		String[] headers = new String[] {"Sender","Date","Subject"};
		List<String[]> data = new ArrayList<>();
		
		ds = new DataSet(headers, data);
	}
	
	
	@Override
    public void process(Message message) throws MessagingException, IOException {
		
		//String uuid = UUID.randomUUID().toString();
		String uuid = FileNameHashGenerator.generateSafeFileName(InternetAddress.toString(message.getFrom()) + message.getSubject());
		String workFolder = "%s/%s".formatted(rootDirectory, uuid);
		File file = new File(workFolder);
		if ( file.exists()) return ;
		file.mkdirs();
		
    	log.debug("Processing message[{}] to folder[{}]", message.getSubject(), workFolder);
    	
    	Map<String, String> files = new HashMap<>(); 
    	Address[] fromAddress = message.getFrom();
		String subject = message.getSubject();
		String sentDate = message.getSentDate().toString();
		String contentType = message.getContentType();
		
		ds.getData().add(new String[] {
				InternetAddress.toString(fromAddress), 
				sentDate,
				subject});
		
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
		
		Map<String, String> metadata = new HashMap<>();
		metadata.put("from", InternetAddress.toString(fromAddress));
		metadata.put("subject", subject);
		metadata.put("sentDate", sentDate);
		metadata.put("contentType", contentType);
		
		MailFileHelper.setMetaDataFile(workFolder, metadata);
		MailFileHelper.setContentFile(workFolder, messageContent);
		
    }
}
