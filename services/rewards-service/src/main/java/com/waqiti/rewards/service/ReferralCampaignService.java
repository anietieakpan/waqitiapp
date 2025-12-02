package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralCampaign;
import com.waqiti.rewards.repository.ReferralCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing referral campaigns
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralCampaignService {

    private final ReferralCampaignRepository campaignRepository;

    @Transactional
    public ReferralCampaign createCampaign(ReferralCampaign campaign) {
        log.info("Creating campaign: {}", campaign.getCampaignName());
        ReferralCampaign saved = campaignRepository.save(campaign);
        log.info("Created campaign: id={}", saved.getCampaignId());
        return saved;
    }

    public ReferralCampaign getCampaign(String campaignId) {
        return campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
    }

    public List<ReferralCampaign> getActiveCampaigns() {
        return campaignRepository.findActiveCampaigns(LocalDate.now());
    }

    @Transactional
    public void activateCampaign(String campaignId) {
        ReferralCampaign campaign = getCampaign(campaignId);
        campaign.setStatus("ACTIVE");
        campaignRepository.save(campaign);
        log.info("Activated campaign: {}", campaignId);
    }

    @Transactional
    public void recordReferral(String campaignId) {
        ReferralCampaign campaign = getCampaign(campaignId);
        campaign.setTotalReferrals(campaign.getTotalReferrals() + 1);
        campaignRepository.save(campaign);
    }
}
