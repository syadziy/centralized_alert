package com.mac.alert.utils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.mac.alert.entities.constant.AlertErrorCode;
import com.mac.alert.entities.model.DeliveryFailure;
import com.mac.alert.utils.exception.AlertDeliveryException;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;

import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    public DeliveryFailure classify(Throwable throwable) {
        List<Throwable> exceptions = flattenExceptions(
                throwable
        );

        /*
         * Exception yang sengaja dilempar aplikasi.
         * Diprioritaskan sebelum exception framework.
         */
        AlertDeliveryException deliveryException =
                findFirst(
                        exceptions,
                        AlertDeliveryException.class
                );

        if (deliveryException != null) {
            return createFailure(
                    deliveryException.getErrorCode(),
                    deliveryException,
                    null
            );
        }

        /*
         * Authentication.
         */
        Throwable authenticationException =
                findFirstAssignable(
                        exceptions,
                        MailAuthenticationException.class,
                        AuthenticationFailedException.class
                );

        if (authenticationException != null) {
            return createFailure(
                    AlertErrorCode.SMTP_AUTHENTICATION_FAILED,
                    authenticationException,
                    null
            );
        }

        /*
         * Gagal pada recipient SMTP.
         */
        SMTPAddressFailedException addressFailure =
                findFirst(
                        exceptions,
                        SMTPAddressFailedException.class
                );

        if (addressFailure != null) {
            int smtpCode = addressFailure.getReturnCode();

            if (isTemporarySmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.SMTP_4XX_TEMPORARY_FAILURE,
                        addressFailure,
                        smtpCode
                );
            }

            /*
             * SMTP 5xx pada perintah RCPT biasanya berarti
             * recipient ditolak secara permanen.
             */
            if (isPermanentSmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.INVALID_RECIPIENT,
                        addressFailure,
                        smtpCode
                );
            }
        }

        /*
         * Gagal pada sender/envelope-from.
         */
        SMTPSenderFailedException senderFailure =
                findFirst(
                        exceptions,
                        SMTPSenderFailedException.class
                );

        if (senderFailure != null) {
            int smtpCode = senderFailure.getReturnCode();

            if (isTemporarySmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.SMTP_4XX_TEMPORARY_FAILURE,
                        senderFailure,
                        smtpCode
                );
            }

            if (isPermanentSmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.INVALID_SENDER,
                        senderFailure,
                        smtpCode
                );
            }
        }

        /*
         * Generic SMTP command failure seperti MAIL,
         * DATA, atau end-of-data.
         */
        SMTPSendFailedException smtpSendFailure =
                findFirst(
                        exceptions,
                        SMTPSendFailedException.class
                );

        if (smtpSendFailure != null) {
            int smtpCode = smtpSendFailure.getReturnCode();

            if (isTemporarySmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.SMTP_4XX_TEMPORARY_FAILURE,
                        smtpSendFailure,
                        smtpCode
                );
            }

            if (isPermanentSmtpCode(smtpCode)) {
                return createFailure(
                        AlertErrorCode.SMTP_5XX_PERMANENT_FAILURE,
                        smtpSendFailure,
                        smtpCode
                );
            }
        }

        /*
         * Timeout ketika membuka koneksi SMTP.
         */
        MailConnectException mailConnectException =
                findFirst(
                        exceptions,
                        MailConnectException.class
                );

        SocketTimeoutException socketTimeoutException =
                findFirst(
                        exceptions,
                        SocketTimeoutException.class
                );

        if (mailConnectException != null
                && socketTimeoutException != null) {
            return createFailure(
                    AlertErrorCode.SMTP_CONNECTION_TIMEOUT,
                    socketTimeoutException,
                    null
            );
        }

        /*
         * SMTP host/port menolak koneksi.
         */
        ConnectException connectException =
                findFirst(
                        exceptions,
                        ConnectException.class
                );

        if (connectException != null) {
            return createFailure(
                    AlertErrorCode.SMTP_CONNECTION_REFUSED,
                    connectException,
                    null
            );
        }

        /*
         * Koneksi gagal, tetapi bukan timeout yang dapat
         * diidentifikasi secara khusus.
         */
        if (mailConnectException != null) {
            return createFailure(
                    AlertErrorCode.SMTP_CONNECTION_REFUSED,
                    mailConnectException,
                    null
            );
        }

        /*
         * Socket timeout setelah koneksi terbentuk
         * dianggap read timeout.
         */
        if (socketTimeoutException != null) {
            return createFailure(
                    AlertErrorCode.SMTP_READ_TIMEOUT,
                    socketTimeoutException,
                    null
            );
        }

        /*
         * Error database dari Spring JDBC atau JPA.
         */
        DataAccessException databaseException =
                findFirst(
                        exceptions,
                        DataAccessException.class
                );

        if (databaseException != null) {
            return createFailure(
                    AlertErrorCode.DATABASE_ERROR,
                    databaseException,
                    null
            );
        }

        /*
         * Error saat parsing atau menyiapkan MIME message.
         */
        Throwable emailBuildException =
                findFirstAssignable(
                        exceptions,
                        MailParseException.class,
                        MailPreparationException.class,
                        AddressException.class
                );

        if (emailBuildException != null) {
            return createFailure(
                    AlertErrorCode.EMAIL_BUILD_FAILED,
                    emailBuildException,
                    null
            );
        }

        /*
         * MessagingException generik yang tidak dapat
         * diklasifikasikan lebih spesifik.
         */
        MessagingException messagingException =
                findFirst(
                        exceptions,
                        MessagingException.class
                );

        if (messagingException != null) {
            return createFailure(
                    AlertErrorCode.EMAIL_BUILD_FAILED,
                    messagingException,
                    null
            );
        }

        return createFailure(
                AlertErrorCode.UNKNOWN_ERROR,
                getRootCause(throwable),
                null
        );
    }

    private DeliveryFailure createFailure(
            AlertErrorCode errorCode,
            Throwable throwable,
            Integer smtpResponseCode
    ) {
        return new DeliveryFailure(
                errorCode,
                getSafeMessage(throwable),
                throwable.getClass().getName(),
                smtpResponseCode == null
                        ? null
                        : smtpResponseCode.toString()
        );
    }

    /*
     * Mengambil seluruh exception:
     * - normal cause
     * - MessagingException.nextException
     * - MailSendException.messageExceptions
     */
    private List<Throwable> flattenExceptions(
            Throwable throwable
    ) {
        List<Throwable> result = new ArrayList<>();
        Deque<Throwable> queue = new ArrayDeque<>();

        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );

        queue.add(throwable);

        while (!queue.isEmpty()) {
            Throwable current = queue.removeFirst();

            if (current == null || !visited.add(current)) {
                continue;
            }

            result.add(current);

            if (current.getCause() != null) {
                queue.addLast(current.getCause());
            }

            if (current instanceof MessagingException messaging) {
                Exception nextException =
                        messaging.getNextException();

                if (nextException != null) {
                    queue.addLast(nextException);
                }
            }

            if (current instanceof MailSendException mailSend) {
                Arrays.stream(
                        mailSend.getMessageExceptions()
                ).forEach(queue::addLast);
            }
        }

        return result;
    }

    private <T extends Throwable> T findFirst(
            List<Throwable> exceptions,
            Class<T> expectedType
    ) {
        return exceptions.stream()
                .filter(expectedType::isInstance)
                .map(expectedType::cast)
                .findFirst()
                .orElse(null);
    }

    @SafeVarargs
    private final Throwable findFirstAssignable(
            List<Throwable> exceptions,
            Class<? extends Throwable>... expectedTypes
    ) {
        for (Throwable exception : exceptions) {
            for (Class<? extends Throwable> expectedType
                    : expectedTypes) {

                if (expectedType.isInstance(exception)) {
                    return exception;
                }
            }
        }

        return null;
    }

    private boolean isTemporarySmtpCode(int code) {
        return code >= 400 && code <= 499;
    }

    private boolean isPermanentSmtpCode(int code) {
        return code >= 500 && code <= 599;
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }

    private String getSafeMessage(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            message = throwable
                    .getClass()
                    .getSimpleName();
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            return message.substring(
                    0,
                    MAX_MESSAGE_LENGTH
            );
        }

        return message;
    }
}