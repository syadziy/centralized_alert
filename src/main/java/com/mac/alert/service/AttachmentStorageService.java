package com.mac.alert.service;

import com.mac.alert.entities.model.AlertAttachment;

public interface AttachmentStorageService {

    byte[] load(AlertAttachment attachment);
}
