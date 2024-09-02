package com.hubbers.core.tools.mail;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.hubbers.core.tools.mail.PrintMailMessageSinker;

public class MailTests {
	
	@Test
	public void readOfficeMail() throws Exception {
		
		ReadEmailMicrosoft.builder().build().readingMail();
	}
	
	@Test
	@Disabled
	public void readGoogleMail() throws IOException, URISyntaxException, MessagingException {
		
		String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");

		ReadEmail
			.builder()
				.username(username)
				.password(password)
				.host("imap.gmail.com")
				.port("993")
				.folder("INBOX")
				.flagTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
			.build()
			.process(new PrintMailMessageSinker());
		
		
		/*
		String username = System.getenv("OFFICE_USERNAME");
        String password = System.getenv("OFFICE_PASSWORD");
		ReadEmail
			.builder()
				.username(username)
				.password(password)
				.host("outlook.office365.com")
				.port("993")
				.folder("INBOX")
				.flagTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
			.build()
			.process(new PrintMailMessageSinker());
		*/
		
		
	}

}
