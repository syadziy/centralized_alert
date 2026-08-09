package com.mac.alert;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.alert.config.properties.*;
import com.mac.alert.controller.AlertController;
import com.mac.alert.entities.constant.*;
import com.mac.alert.entities.dto.*;
import com.mac.alert.entities.mapper.AlertMapper;
import com.mac.alert.entities.model.*;
import com.mac.alert.job.AlertPickupScheduler;
import com.mac.alert.repository.AlertRepository;
import com.mac.alert.service.*;
import com.mac.alert.service.impl.*;
import com.mac.alert.subscriber.AlertKafkaConsumer;
import com.mac.alert.utils.*;
import com.mac.alert.utils.exception.AlertDeliveryException;
import com.mac.alert.utils.handler.AsyncExceptionHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Validation;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.unit.DataSize;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class CoreServiceCoverageTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void mapperNormalizesDefaultsAndOptionalFields() {
        AlertMapper mapper = new AlertMapper(new AlertCreateProperties(4, 3));
        CreateAlert command = mapper.toCommand(request(null, null, null), AlertCreatedSource.API, NOW);
        assertEquals("SOURCE", command.sourceSystem());
        assertEquals("sender@example.com", command.senderEmail());
        assertEquals(3, command.priority());
        assertEquals(NOW, command.scheduledAt());
        assertNull(command.correlationId());
        assertTrue(command.attachments().isEmpty());

        CreateAlert withAttachment = mapper.toCommand(request(7, NOW.plusSeconds(1), List.of(
                new AlertAttachmentRequest(" file.txt ", " text/plain ", 2, StorageType.LOCAL,
                        " file.txt ", " ", AttachmentDisposition.INLINE, " cid "))),
                AlertCreatedSource.KAFKA, NOW);
        assertEquals(7, withAttachment.priority());
        assertEquals("file.txt", withAttachment.attachments().getFirst().fileName());
        assertEquals("cid", withAttachment.attachments().getFirst().contentId());

        CreateAlert withoutRecipients = mapper.toCommand(
                new CreateAlertRequest("SOURCE", "key", null, "sender@example.com", null,
                        null, "subject", "body", AlertBodyType.TEXT, Map.of(), null, null,
                        null, List.of()),
                AlertCreatedSource.API, NOW);
        assertTrue(withoutRecipients.recipients().isEmpty());
    }

    @Test
    void createServiceHandlesCreationIdempotencyAndValidation() {
        AlertRepository repository = mock(AlertRepository.class);
        RecipientConfigurationService recipientConfigurations = mock(RecipientConfigurationService.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        when(recipientConfigurations.resolve(anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        AlertCreateServiceImpl service = new AlertCreateServiceImpl(
                repository, recipientConfigurations, Clock.fixed(NOW, ZoneOffset.UTC), eventPublisher);
        CreateAlert valid = command(List.of(new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)), List.of());
        when(repository.insertAlertRequest(any(), eq(valid), eq(NOW))).thenReturn(true);
        AlertCreateResult created = service.create(valid);
        assertTrue(created.created());
        verify(repository).insertRecipients(created.alertId(), valid.recipients(), NOW);
        verify(repository).insertAttachments(created.alertId(), valid.attachments(), NOW);
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof AlertWebNotification notification
                && notification.alertId().equals(created.alertId())
                && notification.subject().equals(valid.subject())));
        verify(recipientConfigurations).resolve("SOURCE", valid.recipients());

        ExistingAlert existing = new ExistingAlert(UUID.randomUUID(), "SENT", NOW.minusSeconds(10));
        when(repository.insertAlertRequest(any(), eq(valid), eq(NOW))).thenReturn(false);
        when(repository.findExistingAlert("SOURCE", "key")).thenReturn(existing);
        assertEquals(existing.alertId(), service.create(valid).alertId());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(command(List.of(new CreateAlert.Recipient(RecipientType.CC, "cc@example.com", null)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(List.of(
                new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null),
                new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(
                List.of(new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)),
                List.of(new CreateAlert.Attachment("inline", "text/plain", 1, StorageType.LOCAL,
                        "inline", null, AttachmentDisposition.INLINE, null)))));
    }

    @Test
    void controllerCoversAcceptedConflictCreatedAndIdempotentResponses() {
        AlertDispatchService dispatch = mock(AlertDispatchService.class);
        AlertCreateService create = mock(AlertCreateService.class);
        AlertMapper mapper = mock(AlertMapper.class);
        AlertController controller = new AlertController(dispatch, create, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        when(dispatch.dispatchAlertById(id, TriggerSource.API)).thenReturn(false, true);
        assertEquals(HttpStatus.CONFLICT, controller.dispatch(id).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, controller.dispatch(id).getStatusCode());

        CreateAlertRequest request = request(null, null, null);
        CreateAlert command = command(List.of(new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)), List.of());
        when(mapper.toCommand(request, AlertCreatedSource.API, NOW)).thenReturn(command);
        when(create.create(command)).thenReturn(
                new AlertCreateResult(id, "PENDING", true, NOW),
                new AlertCreateResult(id, "PENDING", false, NOW));
        var created = controller.createAlert(request);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("/api/v1/alerts/" + id, created.getHeaders().getLocation().toString());
        assertEquals(HttpStatus.OK, controller.createAlert(request).getStatusCode());
    }

    @Test
    void templateServiceRendersAndWrapsFailures() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        EmailTemplateServiceImpl service = new EmailTemplateServiceImpl(engine);
        assertEquals("Hello Ada", service.render("Hello [[${name}]]", Map.of("name", "Ada")));
        assertEquals("Hello", service.render("Hello", Map.of()));
        assertThrows(AlertDeliveryException.class, () -> service.render(" ", Map.of()));
        assertThrows(AlertDeliveryException.class,
                () -> service.render("[[${#strings.substring('a', 99)}]]", null));
    }

    @Test
    void localAttachmentStorageValidatesAllImportantBoundaries(@TempDir Path directory) throws Exception {
        byte[] content = "hello".getBytes();
        Files.write(directory.resolve("ok.txt"), content);
        AlertAttachmentProperties props = attachmentProperties(directory, 10, 20);
        LocalAttachmentStorageServiceImpl storage = new LocalAttachmentStorageServiceImpl(props);
        AlertAttachment valid = attachment("ok.txt", content.length, sha256(content), AttachmentDisposition.ATTACHMENT, null);
        assertArrayEquals(content, storage.load(valid));
        assertArrayEquals(content, storage.load(attachment("ok.txt", content.length, null,
                AttachmentDisposition.ATTACHMENT, null)));
        assertEquals(AlertErrorCode.ATTACHMENT_DOWNLOAD_FAILED,
                failure(() -> storage.load(new AlertAttachment(UUID.randomUUID(), "x", "text/plain", 1,
                        StorageType.S3, "x", null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_NOT_FOUND,
                failure(() -> storage.load(attachment("../secret", 1, null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_NOT_FOUND,
                failure(() -> storage.load(attachment("missing", 1, null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_TOO_LARGE,
                failure(() -> storage.load(attachment("ok.txt", 11, null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_CHECKSUM_MISMATCH,
                failure(() -> storage.load(attachment("ok.txt", 4, null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_CHECKSUM_MISMATCH,
                failure(() -> storage.load(attachment("ok.txt", 5, "00", AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
        Files.write(directory.resolve("large.txt"), new byte[11]);
        assertEquals(AlertErrorCode.ATTACHMENT_TOO_LARGE,
                failure(() -> storage.load(attachment("large.txt", 10, null, AttachmentDisposition.ATTACHMENT, null))).getErrorCode());
    }

    @Test
    void smtpServiceBuildsMimeMessageOnceAndValidatesAddressesAndLimits() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        AttachmentStorageService storage = mock(AttachmentStorageService.class);
        EmailTemplateService templates = mock(EmailTemplateService.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        when(templates.render(anyString(), anyMap())).thenReturn("rendered");
        when(storage.load(any())).thenReturn("file".getBytes());
        SmtpEmailServiceImpl service = new SmtpEmailServiceImpl(sender, storage, templates,
                attachmentProperties(Path.of("."), 100, 100), Clock.fixed(NOW, ZoneOffset.UTC));
        AlertAttachment file = attachment("file.txt", 4, null, AttachmentDisposition.ATTACHMENT, null);
        AlertAttachment inline = attachment("inline.png", 4, null, AttachmentDisposition.INLINE, "logo");
        AlertMessage message = message("sender@example.com", List.of("to@example.com"), List.of(file, inline));
        assertNotNull(service.send(message).messageId());
        verify(storage, times(2)).load(any());
        verify(sender).send(mime);

        assertEquals(AlertErrorCode.INVALID_SENDER,
                failure(() -> service.send(message("bad", List.of("to@example.com"), List.of()))).getErrorCode());
        assertEquals(AlertErrorCode.INVALID_RECIPIENT,
                failure(() -> service.send(message("sender@example.com", List.of("bad"), List.of()))).getErrorCode());
        assertEquals(AlertErrorCode.ATTACHMENT_TOO_LARGE,
                failure(() -> service.send(message("sender@example.com", List.of("to@example.com"),
                        List.of(attachment("large", 101, null, AttachmentDisposition.ATTACHMENT, null))))).getErrorCode());
        assertEquals(AlertErrorCode.EMAIL_BUILD_FAILED,
                failure(() -> service.send(message("sender@example.com", List.of("to@example.com"),
                        List.of(attachment("inline", 1, null, AttachmentDisposition.INLINE, null))))).getErrorCode());
    }

    @Test
    void retryCalculatorAndFailureClassifierCoverRetrySemantics() throws Exception {
        RetryDelayCalculator calculator = new RetryDelayCalculator(processingProperties());
        assertEquals(NOW.plusSeconds(1), calculator.calculate(0, NOW));
        assertEquals(NOW.plusSeconds(4), calculator.calculate(3, NOW));
        assertEquals(NOW.plusSeconds(8), calculator.calculate(99, NOW));

        FailureClassifier classifier = new FailureClassifier();
        assertCode(classifier, new AlertDeliveryException(AlertErrorCode.ATTACHMENT_NOT_FOUND, "missing"),
                AlertErrorCode.ATTACHMENT_NOT_FOUND);
        assertCode(classifier, new MailAuthenticationException("auth"), AlertErrorCode.SMTP_AUTHENTICATION_FAILED);
        assertCode(classifier, new ConnectException("refused"), AlertErrorCode.SMTP_CONNECTION_REFUSED);
        assertCode(classifier, new SocketTimeoutException("timeout"), AlertErrorCode.SMTP_READ_TIMEOUT);
        assertCode(classifier, new DataAccessResourceFailureException("db"), AlertErrorCode.DATABASE_ERROR);
        assertCode(classifier, new MailParseException("parse"), AlertErrorCode.EMAIL_BUILD_FAILED);
        assertCode(classifier, new MessagingException("mail"), AlertErrorCode.EMAIL_BUILD_FAILED);
        assertCode(classifier, new RuntimeException("outer", new IllegalStateException("root")), AlertErrorCode.UNKNOWN_ERROR);
        DeliveryFailure blank = classifier.classify(new RuntimeException());
        assertEquals("RuntimeException", blank.errorMessage());
        DeliveryFailure longMessage = classifier.classify(new RuntimeException("x".repeat(2100)));
        assertEquals(2000, longMessage.errorMessage().length());
    }

    @Test
    void kafkaSchedulerAndAsyncBoundariesDelegateAndValidate() {
        AlertDispatchService dispatch = mock(AlertDispatchService.class);
        AlertCreateService create = mock(AlertCreateService.class);
        AlertMapper mapper = mock(AlertMapper.class);
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        AlertKafkaConsumer consumer = new AlertKafkaConsumer(dispatch, create, mapper, validator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();
        when(dispatch.dispatchAlertById(id, TriggerSource.KAFKA)).thenReturn(false, true);
        consumer.consume(new AlertEventRequested(id));
        consumer.consume(new AlertEventRequested(id));

        CreateAlertRequest request = validRequest();
        CreateAlert command = command(List.of(new CreateAlert.Recipient(RecipientType.TO, "to@example.com", null)), List.of());
        when(mapper.toCommand(request, AlertCreatedSource.KAFKA, NOW)).thenReturn(command);
        when(create.create(command)).thenReturn(new AlertCreateResult(id, "PENDING", true, NOW));
        consumer.consume(new CreateAlertEvent("event", NOW, request), "key");
        consumer.consume(new CreateAlertEvent("event", NOW, request), null);
        assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> consumer.consume(new CreateAlertEvent("", NOW, request), null));

        AsyncExceptionHandler handler = mock(AsyncExceptionHandler.class);
        AlertPickupScheduler scheduler = new AlertPickupScheduler(dispatch, handler);
        when(dispatch.dispatchPendingAlerts(TriggerSource.SCHEDULER)).thenReturn(2).thenThrow(new IllegalStateException("db"));
        scheduler.pickupPendingAlerts();
        scheduler.pickupPendingAlerts();
        verify(handler).handle(isNull(), eq("centralized-alert.scheduler"), eq("scheduler"),
                eq("pickupPendingAlerts"), anyMap(), any(Throwable.class));
        new AsyncExceptionHandler().handle(null, "dataset", "test", "run", null,
                new IllegalStateException("boom"));
    }

    private static CreateAlertRequest request(Integer priority, Instant scheduledAt,
            List<AlertAttachmentRequest> attachments) {
        return new CreateAlertRequest(" SOURCE ", " key ", " ", " Sender@Example.com ", " Sender ",
                " Reply@Example.com ", " Subject ", "Hello", AlertBodyType.HTML, null, priority,
                scheduledAt, List.of(new AlertRecipientRequest(RecipientType.TO, " To@Example.com ", " User ")),
                attachments);
    }

    private static CreateAlertRequest validRequest() {
        return new CreateAlertRequest("SOURCE", "key", null, "sender@example.com", "Sender",
                "reply@example.com", "Subject", "Hello", AlertBodyType.HTML, Map.of(), null,
                null, List.of(new AlertRecipientRequest(RecipientType.TO, "to@example.com", "User")),
                List.of());
    }

    private static CreateAlert command(List<CreateAlert.Recipient> recipients, List<CreateAlert.Attachment> attachments) {
        return new CreateAlert("SOURCE", "key", null, AlertCreatedSource.API, "sender@example.com", null,
                null, "subject", "body", AlertBodyType.TEXT, Map.of(), 3, NOW, 2, recipients, attachments);
    }

    private static AlertMessage message(String sender, List<String> to, List<AlertAttachment> attachments) {
        return new AlertMessage(UUID.randomUUID(), sender, "Sender", "reply@example.com", "Subject", "Body",
                AlertBodyType.HTML, Map.of("name", "Ada"), to, List.of("cc@example.com"),
                List.of("bcc@example.com"), attachments);
    }

    private static AlertAttachment attachment(String key, long size, String checksum,
            AttachmentDisposition disposition, String contentId) {
        return new AlertAttachment(UUID.randomUUID(), key, "text/plain", size, StorageType.LOCAL,
                key, checksum, disposition, contentId);
    }

    private static AlertAttachmentProperties attachmentProperties(Path path, long maxFile, long maxTotal) {
        return new AlertAttachmentProperties(DataSize.ofBytes(maxFile), DataSize.ofBytes(maxTotal),
                new AlertAttachmentProperties.Local(path.toString()));
    }

    private static AlertProcessingProperties processingProperties() {
        return new AlertProcessingProperties(Duration.ofMinutes(1), Duration.ofSeconds(1),
                Duration.ofSeconds(8), 2);
    }

    private static AlertDeliveryException failure(Runnable operation) {
        return assertThrows(AlertDeliveryException.class, operation::run);
    }

    private static void assertCode(FailureClassifier classifier, Throwable throwable, AlertErrorCode expected) {
        assertEquals(expected, classifier.classify(throwable).errorCode());
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
