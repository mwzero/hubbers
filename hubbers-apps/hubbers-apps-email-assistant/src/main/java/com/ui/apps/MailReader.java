package com.ui.apps;

import javax.mail.Flags;
import javax.mail.search.FlagTerm;

import com.ui.apps.components.MailExtractorSinker;
import com.ui.apps.mail.GoogleEmailReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailReader {
	
	public static void main(String[] args) throws Exception {
    	
    	//parameters
		String rootFolder = "C:\\temp\\mail-assistant";
    	String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");
        log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);

        MailExtractorSinker sinker = 
				MailExtractorSinker.builder().rootDirectory(rootFolder).build();
		
		GoogleEmailReader
			.builder()
			.username(username)
			.password(password)
			.host("imap.gmail.com")
			.port("993")
			.folder("INBOX")
			.flagTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
		.build()
		.process(sinker);
		
	}
	
}
