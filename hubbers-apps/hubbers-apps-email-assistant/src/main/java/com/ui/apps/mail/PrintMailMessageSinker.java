package com.ui.apps.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrintMailMessageSinker implements IMailMessageSinker  {
	
    public void process(Message message) throws MessagingException, IOException {
    	
    	List<String> files = new ArrayList<>(); 
    	
    	Address[] fromAddress = message.getFrom();
		String subject = message.getSubject();
		String sentDate = message.getSentDate().toString();
		String contentType = message.getContentType();
		
		String messageContent  = MailProcessor.processMessage(message);

		log.debug("From: [{}] Subject [{}] [{}] [{}] [{}]",  
    			InternetAddress.toString(fromAddress), 
    			subject,
    			sentDate,
    			contentType, 
    			String.join(",",files));
    }
}
