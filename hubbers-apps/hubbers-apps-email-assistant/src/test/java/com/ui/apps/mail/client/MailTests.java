package com.ui.apps.mail.client;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ui.apps.mail.GoogleEmailReader;
import com.ui.apps.mail.OfficeEmailReader;
import com.ui.apps.mail.sinker.PrintMailMessageSinker;

public class MailTests {
	
	@Test
	@Disabled
	public void readOfficeMail() throws Exception {
		
		String username = System.getenv("OFFICE_USERNAME");
        String password = System.getenv("OFFICE_PASSWORD");
        String host = System.getenv("OFFICE_HOST");
        
        OfficeEmailReader
        	.builder()
        		.username(username)
        		.password(password)
        		.host(host)
        	.build()
        	.readingMail();
        
	}
	
	@Test
	public void readGoogleMail() throws IOException, URISyntaxException, MessagingException {
		
		String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");

        GoogleEmailReader
			.builder()
				.username(username)
				.password(password)
				.host("imap.gmail.com")
				.port("993")
				.folder("INBOX")
				.flagTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
			.build()
			.process(new PrintMailMessageSinker());
		
	}

}
