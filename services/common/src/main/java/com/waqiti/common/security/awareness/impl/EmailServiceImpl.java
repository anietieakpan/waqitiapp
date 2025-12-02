package com.waqiti.common.security.awareness.impl;

import com.waqiti.common.security.awareness.EmailService;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import com.waqiti.common.security.awareness.domain.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;


    @Override
    public void sendPhishingSimulationEmail(String recipientEmail, PhishingSimulationCampaign campaign) {

    }

    @Override
    public void sendEmail(String to, String subject, String body) {

    }

    @Override
    public void trackEmailOpen(UUID campaignId, UUID employeeId) {

    }
}