package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.repository.*;

import com.waqiti.common.security.awareness.domain.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhishingSimulationServiceExtensions {

    private final PhishingSimulationCampaignRepository campaignRepository;
    private final PhishingTestResultRepository resultRepository;

    @Transactional(readOnly = true)
    public PhishingCampaignReport getCampaignReport(UUID campaignId) {
        PhishingSimulationCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalStateException("Campaign not found"));

        double openRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalOpened() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double clickRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalClicked() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double submitRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalSubmittedData() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double reportRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalReported() * 100.0) / campaign.getTotalDelivered()
                : 0;

        return PhishingCampaignReport.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getCampaignName())
                .totalTargeted(campaign.getTotalTargeted())
                .totalDelivered(campaign.getTotalDelivered())
                .totalOpened(campaign.getTotalOpened())
                .totalClicked(campaign.getTotalClicked())
                .totalSubmitted(campaign.getTotalSubmittedData())
                .totalReported(campaign.getTotalReported())
                .openRate(openRate)
                .clickRate(clickRate)
                .submitRate(submitRate)
                .reportRate(reportRate)
                .build();
    }
}