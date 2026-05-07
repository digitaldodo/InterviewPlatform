package com.interview.platform.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

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
        send(to, "Verify your InterviewPrep account", verificationTemplate(otp));
    }

    public void sendPasswordReset(String to, String token) {
        String resetUrl = frontendUrl.replaceAll("/$", "") + "/index.html?resetToken=" + token;
        send(to, "Reset your InterviewPrep password", resetTemplate(resetUrl));
    }

    public void sendBookingConfirmation(String to, String title, String startTime, String meetingLink) {
        send(to, "Your mock interview is booked", bookingTemplate(title, startTime, meetingLink));
    }

    private void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("SendGrid not configured. Email '{}' for {} skipped.", subject, to);
            return;
        }
        Mail mail = new Mail(new Email(fromEmail), subject, new Email(to), new Content("text/html", html));
        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            if (response.getStatusCode() >= 400) {
                log.warn("SendGrid email failed with status {} and body {}", response.getStatusCode(), response.getBody());
            }
        } catch (IOException ex) {
            log.warn("SendGrid email failed", ex);
        }
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

    private String resetTemplate(String resetUrl) {
        return """
                <div style="font-family:Inter,Arial,sans-serif;background:#0f1117;color:#e8eaf0;padding:28px">
                  <div style="max-width:560px;margin:auto;background:#171a25;border:1px solid #2e3350;border-radius:12px;padding:28px">
                    <h1 style="margin:0 0 12px">Reset your password</h1>
                    <p style="color:#a5adbd">This secure link expires in 30 minutes.</p>
                    <p><a href="%s" style="display:inline-block;background:#6c63ff;color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none">Reset password</a></p>
                  </div>
                </div>
                """.formatted(resetUrl);
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
}
