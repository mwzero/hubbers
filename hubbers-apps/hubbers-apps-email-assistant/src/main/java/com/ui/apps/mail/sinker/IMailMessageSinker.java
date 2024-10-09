package com.ui.apps.mail.sinker;

import java.io.IOException;

import javax.mail.Message;
import javax.mail.MessagingException;

public interface IMailMessageSinker {
	
    public void process(Message message) throws MessagingException, IOException;
    	
}
