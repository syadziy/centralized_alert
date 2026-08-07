package com.mac.alert.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

import com.mac.alert.config.properties.AlertAttachmentProperties;
import com.mac.alert.entities.constant.AlertBodyType;
import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.entities.constant.AttachmentDisposition;
import com.mac.alert.entities.model.AlertAttachment;
import com.mac.alert.entities.model.AlertMessage;
import com.mac.alert.entities.model.EmailSendResult;
import com.mac.alert.service.AttachmentStorageService;
import com.mac.alert.service.EmailService;
import com.mac.alert.service.EmailTemplateService;
import com.mac.alert.utils.exception.AlertDeliveryException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SmtpEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final AttachmentStorageService attachmentStorageService;
    private final Clock clock;
    private final EmailTemplateService emailTemplateService;
    private final AlertAttachmentProperties attachmentProperties;

    public SmtpEmailServiceImpl(
            JavaMailSender mailSender,
            AttachmentStorageService attachmentStorageService,
            EmailTemplateService emailTemplateService,
            AlertAttachmentProperties attachmentProperties,
            Clock clock
    ) {
        this.mailSender = mailSender;
        this.attachmentStorageService =
                attachmentStorageService;
        this.emailTemplateService =
                emailTemplateService;
        this.attachmentProperties =
                attachmentProperties;
        this.clock = clock;
    }

    @Override
    public EmailSendResult send(AlertMessage alertMessage) {
        validateSender(alertMessage.senderEmail());
        validateRecipients(alertMessage);

        try {
            MimeMessage mimeMessage =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true,
                            StandardCharsets.UTF_8.name()
                    );

            setSender(helper, alertMessage);

            helper.setTo(
                    alertMessage.to().toArray(String[]::new)
            );

            if (!alertMessage.cc().isEmpty()) {
                helper.setCc(
                        alertMessage.cc().toArray(String[]::new)
                );
            }

            if (!alertMessage.bcc().isEmpty()) {
                helper.setBcc(
                        alertMessage.bcc().toArray(String[]::new)
                );
            }

            if (StringUtils.hasText(
                    alertMessage.replyToEmail()
            )) {
                helper.setReplyTo(
                        alertMessage.replyToEmail()
                );
            }

            helper.setSubject(alertMessage.subject());

            String renderedBody =
                    emailTemplateService.render(
                            alertMessage.body(),
                            alertMessage.templateVariables()
                    );

            boolean html =
                    alertMessage.bodyType()
                            == AlertBodyType.HTML;

            helper.setText(
                    renderedBody,
                    html
            );

            addAttachments(
                    helper,
                    alertMessage
            );

            mimeMessage.setSentDate(
                    Date.from(clock.instant())
            );

            for (var attachment :
                    alertMessage.attachments()) {

                byte[] fileContent =
                        attachmentStorageService.load(
                                attachment
                        );

                ByteArrayResource resource =
                        new ByteArrayResource(fileContent);

                if (attachment.disposition()
                        == AttachmentDisposition.INLINE) {

                    if (!StringUtils.hasText(
                            attachment.contentId()
                    )) {
                        throw new IllegalArgumentException(
                                "contentId is required for inline attachment"
                        );
                    }

                    helper.addInline(
                            attachment.contentId(),
                            resource,
                            attachment.contentType()
                    );

                } else {
                    helper.addAttachment(
                            attachment.fileName(),
                            resource,
                            attachment.contentType()
                    );
                }
            }

            mimeMessage.saveChanges();

            String messageId =
                    mimeMessage.getMessageID();

            mailSender.send(mimeMessage);

            return new EmailSendResult(messageId);

        } catch (AlertDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AlertDeliveryException(
                    AlertErrorCode.EMAIL_BUILD_FAILED,
                    "Failed to create or send MIME email",
                    exception
            );
        }
    }

    private void addAttachments(
            MimeMessageHelper helper,
            AlertMessage alertMessage
    ) throws MessagingException {

        validateTotalAttachmentSize(alertMessage);

        for (AlertAttachment attachment
                : alertMessage.attachments()) {

            byte[] content =
                    attachmentStorageService.load(
                            attachment
                    );

            ByteArrayResource resource =
                    new ByteArrayResource(content);

            if (attachment.disposition()
                    == AttachmentDisposition.INLINE) {

                if (attachment.contentId() == null
                        || attachment.contentId().isBlank()) {
                    throw new AlertDeliveryException(
                            AlertErrorCode.EMAIL_BUILD_FAILED,
                            "contentId is required for inline attachments: "
                                    + attachment.fileName()
                    );
                }

                helper.addInline(
                        attachment.contentId(),
                        resource,
                        attachment.contentType()
                );

            } else {
                helper.addAttachment(
                        attachment.fileName(),
                        resource,
                        attachment.contentType()
                );
            }
        }
    }

    private void validateSender(String sender) {
        validateAddress(
                sender,
                AlertErrorCode.INVALID_SENDER,
                "Sender email tidak valid"
        );
    }

    private void validateRecipients(
            AlertMessage alertMessage
    ) {
        alertMessage.to().forEach(this::validateRecipient);
        alertMessage.cc().forEach(this::validateRecipient);
        alertMessage.bcc().forEach(this::validateRecipient);
    }

    private void validateRecipient(String recipient) {
        validateAddress(
                recipient,
                AlertErrorCode.INVALID_RECIPIENT,
                "Recipient email tidak valid"
        );
    }

    private void validateAddress(
            String email,
            AlertErrorCode errorCode,
            String message
    ) {
        try {
            InternetAddress internetAddress =
                    new InternetAddress(email, true);

            internetAddress.validate();

        } catch (AddressException exception) {
            throw new AlertDeliveryException(
                    errorCode,
                    message + ": " + email,
                    exception
            );
        }
    }

    private void validateTotalAttachmentSize(
            AlertMessage alertMessage
    ) {
        long totalSize = alertMessage.attachments()
                .stream()
                .mapToLong(AlertAttachment::fileSizeBytes)
                .sum();

        long maximumTotalSize =
                attachmentProperties
                        .maxTotalSize()
                        .toBytes();

        if (totalSize > maximumTotalSize) {
            throw new AlertDeliveryException(
                    AlertErrorCode.ATTACHMENT_TOO_LARGE,
                    "The total size of the attachments exceeds the limit. "
                            + "totalSize="
                            + totalSize
                            + ", maximum="
                            + maximumTotalSize
            );
        }
    }

    private void setSender(
            MimeMessageHelper helper,
            AlertMessage alertMessage
    ) throws Exception {

        if (StringUtils.hasText(
                alertMessage.senderName()
        )) {
            helper.setFrom(
                    alertMessage.senderEmail(),
                    alertMessage.senderName()
            );
        } else {
            helper.setFrom(
                    alertMessage.senderEmail()
            );
        }
    }
}
