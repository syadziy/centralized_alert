package com.mac.alert.service;

import com.mac.alert.entities.model.AlertMessage;
import com.mac.alert.entities.model.EmailSendResult;

public interface EmailService {

    EmailSendResult send(AlertMessage alertMessage);
}
