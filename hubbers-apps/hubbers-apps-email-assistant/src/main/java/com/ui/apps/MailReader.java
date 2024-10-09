package com.ui.apps;

import javax.mail.Flags;
import javax.mail.search.FlagTerm;

import com.st.DataFrame;
import com.ui.apps.components.MailExtractorSinker;
import com.ui.apps.mail.GoogleEmailReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailReader {
	
	MailExtractorSinker sinker = new MailExtractorSinker();
	
	public void process(String rootFolder, String username, String password) throws Exception {
    	
        log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);

        sinker.setRootDirectory(rootFolder);
		
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
	public DataFrame getDF() {
		
		return new DataFrame(sinker.getDs());
	}

	public static void main(String[] args) throws Exception {
    	
		//parameters
		String rootFolder = "C:\\temp\\mail-assistant";
    	String username = System.getenv("GMAIL_USERNAME");
        String password = System.getenv("GMAIL_PASSWORD");
        log.info("Reading mail for [{}] writing to [{}]", username, rootFolder);

        MailReader reader = new MailReader();
        reader.process(rootFolder, username, password);
	}
	
}
