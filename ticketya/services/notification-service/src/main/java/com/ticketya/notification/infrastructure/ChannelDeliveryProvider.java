package com.ticketya.notification.infrastructure;

import com.ticketya.notification.application.NotificationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Routes EMAIL to SMTP and leaves the existing PUSH simulation intact. */
@Component
@Primary
public class ChannelDeliveryProvider implements DeliveryProvider {
    private final JdbcNotificationRepository repository;
    private final JavaMailSender mailSender;
    private final SimulatedDeliveryProvider push;
    private final NotificationProperties properties;

    public ChannelDeliveryProvider(JdbcNotificationRepository repository, JavaMailSender mailSender,
                                   SimulatedDeliveryProvider push, NotificationProperties properties) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.push = push;
        this.properties = properties;
    }

    @Override
    public void send(JdbcNotificationRepository.ClaimedNotification notification) {
        if (!"EMAIL".equals(notification.channel())) {
            push.send(notification);
            return;
        }
        String recipient = repository.emailForCustomer(notification.customerId())
                .orElseThrow(MissingContactException::new);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.smtp().from());
        message.setTo(recipient);
        message.setSubject(notification.subject());
        message.setText(notification.body());
        mailSender.send(message);
    }
}
