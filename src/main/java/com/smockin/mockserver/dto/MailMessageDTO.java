package com.smockin.mockserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class MailMessageDTO {

    private String sender;
    private Date dateReceived;
    private String subject;
    private String plainTextBody;
    private String htmlBody;

}
