package com.interview.platform.service;

import com.interview.platform.exception.EmailDeliveryException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter MEETING_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);

    private final String apiKey;
    private final String fromEmail;
    private final String frontendUrl;

    public EmailService(
            @Value("${app.sendgrid.api-key}") String apiKey,
            @Value("${app.sendgrid.from-email}") String fromEmail,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.frontendUrl = frontendUrl;
    }

    public void sendVerificationOtp(String to, String otp) {
        log.info("Preparing verification OTP email for {}", maskEmail(to));
        send(to, "Verify your InterviewPrep account", verificationTemplate(otp));
    }

    public void sendPasswordReset(String to, String token) {
        sendPasswordResetOtp(to, token);
    }

    public void sendPasswordResetOtp(String to, String otp) {
        log.info("Preparing password reset OTP email for {}", maskEmail(to));
        send(to, "Reset your InterviewPrep password", resetOtpTemplate(otp));
    }

    public void sendBookingConfirmation(String to, String title, String startTime, String meetingLink) {
        sendBookingConfirmation(to, title, startTime, meetingLink, null);
    }

    public void sendBookingConfirmation(String to, String title, String startTime, String meetingLink, String recipientTimeZone) {
        log.info("Preparing booking confirmation email for {}", maskEmail(to));
        send(to, "Your mock interview is booked", bookingTemplate(title, localMeetingTime(startTime, recipientTimeZone), meetingLink));
    }

    public void sendSessionInvite(String to, String recipientName, String counterpartName, String title,
                                  String startTime, String meetingProvider, String meetingLink) {
        sendSessionInvite(to, recipientName, counterpartName, title, startTime, meetingProvider, meetingLink, null);
    }

    public void sendSessionInvite(String to, String recipientName, String counterpartName, String title,
                                  String startTime, String meetingProvider, String meetingLink, String recipientTimeZone) {
        sendSessionInvite(to, recipientName, counterpartName, title, startTime, meetingProvider, meetingLink, recipientTimeZone, null);
    }

    public void sendSessionInvite(String to, String recipientName, String counterpartName, String title,
                                  String startTime, String meetingProvider, String meetingLink, String recipientTimeZone,
                                  CalendarInviteService.CalendarInvite calendarInvite) {
        log.info("Preparing session invite email for {}", maskEmail(to));
        send(to, "Your interview session is scheduled",
                meetingInviteTemplate(recipientName, counterpartName, title, localMeetingTime(startTime, recipientTimeZone), meetingProvider, meetingLink),
                calendarInvite);
    }

    public void sendMeetingReminder(String to, String title, String startTime, String meetingLink, String counterpartName) {
        sendMeetingReminder(to, title, startTime, meetingLink, counterpartName, null);
    }

    public void sendMeetingReminder(String to, String title, String startTime, String meetingLink, String counterpartName, String recipientTimeZone) {
        log.info("Preparing meeting reminder email for {}", maskEmail(to));
        send(to, "Your interview room is live",
                meetingReminderTemplate(title, localMeetingTime(startTime, recipientTimeZone), meetingLink, counterpartName));
    }

    public void sendPreInterviewReminder(String to, String recipientName, String interviewerName, String intervieweeName,
                                         List<String> topics, String scheduledDate, String scheduledTime,
                                         String timezone, String meetingLink, Integer durationMinutes) {
        sendPreInterviewReminder(to, recipientName, interviewerName, intervieweeName, topics, scheduledDate,
                scheduledTime, timezone, meetingLink, durationMinutes, null);
    }

    public void sendPreInterviewReminder(String to, String recipientName, String interviewerName, String intervieweeName,
                                         List<String> topics, String scheduledDate, String scheduledTime,
                                         String timezone, String meetingLink, Integer durationMinutes,
                                         CalendarInviteService.CalendarInvite calendarInvite) {
        log.info("Preparing pre-interview reminder email for {}", maskEmail(to));
        send(to, "Your mock interview starts in 30 minutes",
                preInterviewReminderTemplate(recipientName, interviewerName, intervieweeName, topics, scheduledDate,
                        scheduledTime, timezone, meetingLink, durationMinutes),
                calendarInvite);
    }

    public void sendSessionCancellation(String to, String title, String startTime, String counterpartName) {
        sendSessionCancellation(to, title, startTime, counterpartName, null);
    }

    public void sendSessionCancellation(String to, String title, String startTime, String counterpartName, String recipientTimeZone) {
        sendSessionCancellation(to, title, startTime, counterpartName, recipientTimeZone, null);
    }

    public void sendSessionCancellation(String to, String title, String startTime, String counterpartName, String recipientTimeZone,
                                        CalendarInviteService.CalendarInvite calendarInvite) {
        log.info("Preparing session cancellation email for {}", maskEmail(to));
        send(to, "Interview session cancelled",
                cancellationTemplate(title, localMeetingTime(startTime, recipientTimeZone), counterpartName),
                calendarInvite);
    }

    public void sendSessionStatusUpdate(String to, String title, String status, String startTime, String launchUrl) {
        sendSessionStatusUpdate(to, title, status, startTime, launchUrl, null);
    }

    public void sendSessionStatusUpdate(String to, String title, String status, String startTime, String launchUrl, String recipientTimeZone) {
        sendSessionStatusUpdate(to, title, status, startTime, launchUrl, recipientTimeZone, null);
    }

    public void sendSessionStatusUpdate(String to, String title, String status, String startTime, String launchUrl, String recipientTimeZone,
                                        CalendarInviteService.CalendarInvite calendarInvite) {
        log.info("Preparing session status email for {}", maskEmail(to));
        send(to, "Interview session update",
                sessionStatusTemplate(title, status, localMeetingTime(startTime, recipientTimeZone), launchUrl),
                calendarInvite);
    }

    private void send(String to, String subject, String html) {
        send(to, subject, html, null);
    }

    private void send(String to, String subject, String html, CalendarInviteService.CalendarInvite calendarInvite) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("SendGrid API key is not configured. Email '{}' for {} was not sent.", subject, maskEmail(to));
            throw new EmailDeliveryException("Email delivery is not configured. Please contact support.");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.error("SendGrid from email is not configured. Email '{}' for {} was not sent.", subject, maskEmail(to));
            throw new EmailDeliveryException("Email sender is not configured. Please contact support.");
        }
        Mail mail = new Mail(new Email(fromEmail), subject, new Email(to), new Content("text/html", html));
        addCalendarAttachment(mail, calendarInvite);
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            log.info("Sending SendGrid email '{}' to {} from {}", subject, maskEmail(to), fromEmail);
            Response response = sg.api(request);
            log.info("SendGrid response for '{}' to {}: status={}", subject, maskEmail(to), response.getStatusCode());
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid email failed for '{}' to {} with status {} and body {}",
                        subject, maskEmail(to), response.getStatusCode(), response.getBody());
                throw new EmailDeliveryException("Email delivery failed. Please verify the sender configuration.");
            }
        } catch (IOException ex) {
            log.error("SendGrid email request failed for '{}' to {}", subject, maskEmail(to), ex);
            throw new EmailDeliveryException("Email delivery failed. Please try again later.", ex);
        } catch (RuntimeException ex) {
            log.error("SendGrid email request failed unexpectedly for '{}' to {}", subject, maskEmail(to), ex);
            throw new EmailDeliveryException("Email delivery failed. Please try again later.", ex);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "unknown";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String masked = name.length() <= 2 ? name.charAt(0) + "*" : name.substring(0, 2) + "***";
        return masked + "@" + parts[1];
    }

    private String verificationTemplate(String otp) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Verify your email</h1>
                    <p style="color:#a5adbd">Use this one-time code to activate your InterviewPrep account.</p>
                    <div style="font-size:32px;letter-spacing:8px;font-weight:700;color:#f4c95d;margin:24px 0">%s</div>
                    <p style="color:#a5adbd">This code expires in 10 minutes.</p>
                  </div>
                </div>
                """.formatted(otp);
    }

    private String resetOtpTemplate(String otp) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Reset your password</h1>
                    <p style="color:#a5adbd">Use this one-time code inside InterviewPrep to set a new password.</p>
                    <div style="font-size:32px;letter-spacing:8px;font-weight:700;color:#f4c95d;margin:24px 0">%s</div>
                    <p style="color:#a5adbd">This code expires in 10 minutes. If you did not request it, you can ignore this email.</p>
                  </div>
                </div>
                """.formatted(otp);
    }

    private String bookingTemplate(String title, String startTime, String meetingLink) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Session scheduled</h1>
                    <p style="color:#a5adbd">%s is scheduled for %s.</p>
                    <p><a href="%s" style="display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none">Join meeting</a></p>
                  </div>
                </div>
                """.formatted(title, startTime, meetingLink);
    }

    private String meetingInviteTemplate(String recipientName, String counterpartName, String title, String startTime,
                                         String meetingProvider, String meetingLink) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Interview session confirmed</h1>
                    <p style="color:#a5adbd">Hi %s, your %s session with %s is booked for %s.</p>
                    <p style="color:#a5adbd">Meeting provider: %s</p>
                    <p><a href="%s" style="display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none">Open meeting</a></p>
                    <p style="color:#a5adbd">An .ics calendar invite is attached. Open it to add this session to Google Calendar, Outlook, Apple Calendar, or your mobile calendar app.</p>
                    <p style="color:#a5adbd">You can also join from your InterviewPrep dashboard.</p>
                  </div>
                </div>
                """.formatted(safe(recipientName), safe(title), safe(counterpartName), safe(startTime), safe(meetingProvider), safe(meetingLink));
    }

    private String meetingReminderTemplate(String title, String startTime, String meetingLink, String counterpartName) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Your meeting is live</h1>
                    <p style="color:#a5adbd">%s with %s is ready to begin. Scheduled time: %s.</p>
                    <p><a href="%s" style="display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none">Join now</a></p>
                  </div>
                </div>
                """.formatted(safe(title), safe(counterpartName), safe(startTime), safe(meetingLink));
    }

    private String cancellationTemplate(String title, String startTime, String counterpartName) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Session cancelled</h1>
                    <p style="color:#a5adbd">%s scheduled for %s with %s has been cancelled.</p>
                    <p style="color:#a5adbd">Open the attached .ics cancellation file to update supporting calendar apps.</p>
                  </div>
                </div>
                """.formatted(safe(title), safe(startTime), safe(counterpartName));
    }

    private String sessionStatusTemplate(String title, String status, String startTime, String launchUrl) {
        String button = (launchUrl == null || launchUrl.isBlank()) ? "" :
                "<p><a href=\"%s\" style=\"display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none\">Open session</a></p>"
                        .formatted(safe(launchUrl));
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Session update</h1>
                    <p style="color:#a5adbd">%s is now %s. Scheduled time: %s.</p>
                    %s
                    <p style="color:#a5adbd">Use the attached .ics file to update this event in your calendar.</p>
                  </div>
                </div>
                """.formatted(safe(title), safe(status), safe(startTime), button);
    }

    private String localMeetingTime(String startTime, String timeZone) {
        if (startTime == null || startTime.isBlank()) {
            return "the scheduled time";
        }
        try {
            ZoneId zone = timeZone == null || timeZone.isBlank() ? null : ZoneId.of(timeZone.trim());
            ZonedDateTime zoned = parseMeetingInstant(startTime).atZone(zone == null ? ZoneId.of("UTC") : zone);
            return MEETING_TIME_FORMATTER.format(zoned);
        } catch (DateTimeParseException | IllegalArgumentException ignored) {
            return startTime;
        }
    }

    private void addCalendarAttachment(Mail mail, CalendarInviteService.CalendarInvite calendarInvite) {
        if (mail == null || calendarInvite == null || calendarInvite.content() == null || calendarInvite.content().isBlank()) {
            return;
        }
        Attachments attachment = new Attachments();
        attachment.setFilename(calendarInvite.filename());
        attachment.setType("text/calendar; charset=UTF-8; method=" + calendarInvite.method());
        attachment.setDisposition("attachment");
        attachment.setContent(Base64.getEncoder().encodeToString(calendarInvite.content().getBytes(StandardCharsets.UTF_8)));
        mail.addAttachments(attachment);
    }

    private Instant parseMeetingInstant(String startTime) {
        try {
            return OffsetDateTime.parse(startTime).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.parse(startTime);
        }
    }

    private String preInterviewReminderTemplate(String recipientName, String interviewerName, String intervieweeName,
                                                List<String> topics, String scheduledDate, String scheduledTime,
                                                String timezone, String meetingLink, Integer durationMinutes) {
        String topicText = topics == null || topics.isEmpty() ? "Mock interview" : String.join(", ", topics);
        String durationText = (durationMinutes == null || durationMinutes <= 0 ? 45 : durationMinutes) + " minutes";
        String timezoneText = timezone == null || timezone.isBlank() ? "" : " (" + escape(timezone) + ")";
        String button = meetingLink == null || meetingLink.isBlank() ? "" :
                "<p><a href=\"%s\" style=\"display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none\">Join interview</a></p>"
                        .formatted(escape(meetingLink));
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Your mock interview starts soon.</h1>
                    <p style="color:#a5adbd">Hi %s, this is a reminder for your upcoming interview session.</p>
                    <div style="background:#11131c;border:1px solid #2e3350;border-radius:10px;padding:16px;margin:20px 0">
                      <p style="margin:0 0 8px"><strong>Interviewer:</strong> %s</p>
                      <p style="margin:0 0 8px"><strong>Interviewee:</strong> %s</p>
                      <p style="margin:0 0 8px"><strong>Topics:</strong> %s</p>
                      <p style="margin:0 0 8px"><strong>Date:</strong> %s</p>
                      <p style="margin:0 0 8px"><strong>Time:</strong> %s%s</p>
                      <p style="margin:0"><strong>Duration:</strong> %s</p>
                    </div>
                    %s
                    <p style="color:#a5adbd">The attached .ics file can add or refresh this session in your calendar app.</p>
                    <p style="color:#a5adbd">You can also join from your InterviewPrep dashboard.</p>
                  </div>
                </div>
                """.formatted(
                escape(recipientName),
                escape(interviewerName),
                escape(intervieweeName),
                escape(topicText),
                escape(scheduledDate),
                escape(scheduledTime),
                timezoneText,
                escape(durationText),
                button
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
