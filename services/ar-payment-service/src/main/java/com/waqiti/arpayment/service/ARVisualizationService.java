package com.waqiti.arpayment.service;

import com.waqiti.arpayment.domain.ARSession;
import com.waqiti.arpayment.domain.ARPaymentExperience;
import com.waqiti.arpayment.dto.*;
import com.waqiti.arpayment.repository.ARSessionRepository;
import com.waqiti.arpayment.repository.ARPaymentExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ARVisualizationService {
    
    private final ARSessionRepository sessionRepository;
    private final ARPaymentExperienceRepository experienceRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Transactional(readOnly = true)
    public ARWalletVisualizationResponse visualizeWallet(String sessionToken, String visualizationType) {
        log.info("Creating wallet visualization for session: {} type: {}", sessionToken, visualizationType);
        
        try {
            Optional<ARSession> sessionOpt = sessionRepository.findBySessionToken(sessionToken);
            if (sessionOpt.isEmpty()) {
                return ARWalletVisualizationResponse.error("Session not found");
            }
            
            ARSession session = sessionOpt.get();
            
            // Determine visualization type
            String vizType = visualizationType != null ? visualizationType : "HOLOGRAPHIC_CARDS";
            
            // Create visualization data based on type
            Map<String, Object> visualizationData = createWalletVisualization(session.getUserId(), vizType);
            
            // Add overlay to session
            ARSession.AROverlay walletOverlay = ARSession.AROverlay.builder()
                    .id(UUID.randomUUID().toString())
                    .type("WALLET_" + vizType)
                    .content("wallet_visualization")
                    .position(createCenterPosition())
                    .style(createVisualizationStyle(vizType))
                    .isInteractive(true)
                    .action("WALLET_INTERACTION")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            addOverlayToSession(sessionToken, walletOverlay);
            
            return ARWalletVisualizationResponse.builder()
                    .success(true)
                    .visualizationType(vizType)
                    .visualizationData(visualizationData)
                    .interactionPoints(createWalletInteractionPoints(vizType))
                    .animations(createWalletAnimations(vizType))
                    .message("Wallet visualization created")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error creating wallet visualization", e);
            return ARWalletVisualizationResponse.error("Failed to create wallet visualization");
        }
    }
    
    public Map<String, Object> createPaymentVisualization(ARPaymentExperience experience) {
        Map<String, Object> visualization = new HashMap<>();
        
        visualization.put("experienceId", experience.getExperienceId());
        visualization.put("type", experience.getExperienceType().name());
        
        // Create visualization based on experience type
        switch (experience.getExperienceType()) {
            case QR_SCAN_TO_PAY:
                visualization.putAll(createQRScanVisualization(experience));
                break;
            case SPATIAL_DROP:
                visualization.putAll(createSpatialDropVisualization(experience));
                break;
            case GESTURE_PAYMENT:
                visualization.putAll(createGesturePaymentVisualization(experience));
                break;
            case VIRTUAL_SHOPPING:
                visualization.putAll(createShoppingVisualization(experience));
                break;
            case HOLOGRAPHIC_WALLET:
                visualization.putAll(createHolographicVisualization(experience));
                break;
            default:
                visualization.putAll(createDefaultVisualization(experience));
        }
        
        // Add common visualization elements
        addCommonVisualizationElements(visualization, experience);
        
        return visualization;
    }
    
    public List<ARPaymentExperience.VisualizationEffect> createPaymentEffects(
            String paymentType, BigDecimal amount) {
        
        List<ARPaymentExperience.VisualizationEffect> effects = new ArrayList<>();
        
        // Amount-based effects
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            effects.add(createPremiumEffect());
        }
        
        // Payment type effects
        switch (paymentType) {
            case "INSTANT":
                effects.add(createLightningEffect());
                break;
            case "CRYPTO":
                effects.add(createCryptoEffect());
                break;
            case "GIFT":
                effects.add(createGiftEffect());
                break;
        }
        
        // Always add confirmation effect
        effects.add(createConfirmationEffect());
        
        return effects;
    }
    
    public Map<String, Object> create3DPaymentModel(BigDecimal amount, String currency) {
        Map<String, Object> model = new HashMap<>();
        
        model.put("type", "3D_CURRENCY");
        model.put("amount", amount);
        model.put("currency", currency);
        
        // Create 3D model parameters
        Map<String, Object> modelParams = new HashMap<>();
        modelParams.put("mesh", selectCurrencyMesh(currency));
        modelParams.put("texture", selectCurrencyTexture(currency));
        modelParams.put("scale", calculateModelScale(amount));
        modelParams.put("rotation", createInitialRotation());
        modelParams.put("position", new double[]{0, 0, 0});
        
        model.put("modelParameters", modelParams);
        
        // Add physics properties
        Map<String, Object> physics = new HashMap<>();
        physics.put("gravity", true);
        physics.put("bounce", 0.3);
        physics.put("friction", 0.5);
        model.put("physics", physics);
        
        return model;
    }
    
    public void addPaymentPathVisualization(String sessionToken, UUID senderId, UUID recipientId) {
        try {
            Map<String, Object> pathData = new HashMap<>();
            pathData.put("type", "PAYMENT_PATH");
            pathData.put("startPoint", "sender_location");
            pathData.put("endPoint", "recipient_location");
            pathData.put("animation", "FLOWING_PARTICLES");
            pathData.put("color", "#00FF00");
            pathData.put("duration", 2000);
            
            ARSession.AROverlay pathOverlay = ARSession.AROverlay.builder()
                    .id(UUID.randomUUID().toString())
                    .type("PAYMENT_PATH")
                    .content("payment_flow_visualization")
                    .position(pathData)
                    .style(createPathStyle())
                    .isInteractive(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            addOverlayToSession(sessionToken, pathOverlay);
            
        } catch (Exception e) {
            log.error("Error adding payment path visualization", e);
        }
    }
    
    // Helper methods
    
    private Map<String, Object> createWalletVisualization(UUID userId, String vizType) {
        Map<String, Object> visualization = new HashMap<>();
        
        visualization.put("userId", userId);
        visualization.put("type", vizType);
        
        switch (vizType) {
            case "HOLOGRAPHIC_CARDS":
                visualization.put("cards", createHolographicCards());
                visualization.put("layout", "CAROUSEL");
                break;
            case "3D_SPHERE":
                visualization.put("sphere", create3DSphere());
                visualization.put("layout", "ORBITAL");
                break;
            case "FLOATING_TILES":
                visualization.put("tiles", createFloatingTiles());
                visualization.put("layout", "GRID");
                break;
            case "CRYPTO_CONSTELLATION":
                visualization.put("nodes", createCryptoNodes());
                visualization.put("layout", "NETWORK");
                break;
        }
        
        return visualization;
    }
    
    private List<Map<String, Object>> createHolographicCards() {
        List<Map<String, Object>> cards = new ArrayList<>();
        
        // Create sample cards
        String[] cardTypes = {"DEBIT", "CREDIT", "CRYPTO", "REWARDS"};
        for (int i = 0; i < cardTypes.length; i++) {
            Map<String, Object> card = new HashMap<>();
            card.put("id", UUID.randomUUID().toString());
            card.put("type", cardTypes[i]);
            card.put("position", new double[]{i * 0.15 - 0.225, 0, 0});
            card.put("rotation", new double[]{0, i * 15, 0});
            card.put("hologramEffect", true);
            card.put("interactable", true);
            cards.add(card);
        }
        
        return cards;
    }
    
    private Map<String, Object> create3DSphere() {
        Map<String, Object> sphere = new HashMap<>();
        sphere.put("radius", 0.5);
        sphere.put("segments", 32);
        sphere.put("wireframe", true);
        sphere.put("rotation", new double[]{0, 0, 0});
        sphere.put("rotationSpeed", 0.01);
        return sphere;
    }
    
    private List<Map<String, Object>> createFloatingTiles() {
        List<Map<String, Object>> tiles = new ArrayList<>();
        
        for (int i = 0; i < 6; i++) {
            Map<String, Object> tile = new HashMap<>();
            tile.put("id", UUID.randomUUID().toString());
            tile.put("position", new double[]{
                (i % 3) * 0.2 - 0.2,
                (i / 3) * 0.2,
                0
            });
            tile.put("size", new double[]{0.15, 0.15, 0.02});
            tile.put("floatAnimation", true);
            tile.put("glowEffect", true);
            tiles.add(tile);
        }
        
        return tiles;
    }
    
    private List<Map<String, Object>> createCryptoNodes() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        
        String[] cryptos = {"BTC", "ETH", "USDC", "SOL"};
        for (String crypto : cryptos) {
            Map<String, Object> node = new HashMap<>();
            node.put("symbol", crypto);
            node.put("position", randomPosition());
            node.put("connections", new ArrayList<>());
            node.put("particleEffect", true);
            nodes.add(node);
        }
        
        return nodes;
    }
    
    private Map<String, Object> createCenterPosition() {
        Map<String, Object> position = new HashMap<>();
        position.put("x", 0.0);
        position.put("y", 0.0);
        position.put("z", -0.5);
        return position;
    }
    
    private Map<String, Object> createVisualizationStyle(String vizType) {
        Map<String, Object> style = new HashMap<>();
        
        style.put("opacity", 0.9);
        style.put("glowIntensity", 0.5);
        style.put("primaryColor", "#00D4FF");
        style.put("secondaryColor", "#FF00D4");
        
        switch (vizType) {
            case "HOLOGRAPHIC_CARDS":
                style.put("hologramShader", true);
                style.put("scanlineEffect", true);
                break;
            case "3D_SPHERE":
                style.put("wireframeColor", "#00FF00");
                style.put("pulseEffect", true);
                break;
            case "CRYPTO_CONSTELLATION":
                style.put("particleColor", "#FFD700");
                style.put("connectionColor", "#FFFFFF");
                break;
        }
        
        return style;
    }
    
    private List<Map<String, Object>> createWalletInteractionPoints(String vizType) {
        List<Map<String, Object>> points = new ArrayList<>();
        
        Map<String, Object> mainPoint = new HashMap<>();
        mainPoint.put("id", "main_interaction");
        mainPoint.put("position", createCenterPosition());
        mainPoint.put("radius", 0.1);
        mainPoint.put("action", "OPEN_WALLET");
        points.add(mainPoint);
        
        if (vizType.equals("HOLOGRAPHIC_CARDS")) {
            for (int i = 0; i < 4; i++) {
                Map<String, Object> cardPoint = new HashMap<>();
                cardPoint.put("id", "card_" + i);
                cardPoint.put("position", new double[]{i * 0.15 - 0.225, 0, 0});
                cardPoint.put("radius", 0.08);
                cardPoint.put("action", "SELECT_CARD");
                points.add(cardPoint);
            }
        }
        
        return points;
    }
    
    private Map<String, Object> createWalletAnimations(String vizType) {
        Map<String, Object> animations = new HashMap<>();
        
        animations.put("entrance", createEntranceAnimation());
        animations.put("idle", createIdleAnimation(vizType));
        animations.put("interaction", createInteractionAnimation());
        animations.put("exit", createExitAnimation());
        
        return animations;
    }
    
    private Map<String, Object> createEntranceAnimation() {
        Map<String, Object> animation = new HashMap<>();
        animation.put("type", "SCALE_IN");
        animation.put("duration", 1000);
        animation.put("easing", "easeOutElastic");
        animation.put("from", new double[]{0, 0, 0});
        animation.put("to", new double[]{1, 1, 1});
        return animation;
    }
    
    private Map<String, Object> createIdleAnimation(String vizType) {
        Map<String, Object> animation = new HashMap<>();
        
        switch (vizType) {
            case "HOLOGRAPHIC_CARDS":
                animation.put("type", "FLOAT");
                animation.put("amplitude", 0.02);
                animation.put("frequency", 0.5);
                break;
            case "3D_SPHERE":
                animation.put("type", "ROTATE");
                animation.put("axis", "y");
                animation.put("speed", 0.01);
                break;
            default:
                animation.put("type", "PULSE");
                animation.put("scale", 1.05);
                animation.put("duration", 2000);
        }
        
        return animation;
    }
    
    private Map<String, Object> createInteractionAnimation() {
        Map<String, Object> animation = new HashMap<>();
        animation.put("type", "RIPPLE");
        animation.put("duration", 500);
        animation.put("color", "#FFFFFF");
        animation.put("radius", 0.2);
        return animation;
    }
    
    private Map<String, Object> createExitAnimation() {
        Map<String, Object> animation = new HashMap<>();
        animation.put("type", "FADE_OUT");
        animation.put("duration", 500);
        animation.put("easing", "easeInQuad");
        return animation;
    }
    
    private Map<String, Object> createQRScanVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("scanFrame", createScanFrame());
        viz.put("scanLine", createScanLine());
        viz.put("targetIndicator", createTargetIndicator());
        return viz;
    }
    
    private Map<String, Object> createSpatialDropVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("dropEffect", createDropEffect(experience.getAmount()));
        viz.put("landingMarker", createLandingMarker());
        viz.put("pickupRadius", createPickupRadius());
        return viz;
    }
    
    private Map<String, Object> createGesturePaymentVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("gestureTrail", createGestureTrail());
        viz.put("handTracking", createHandTrackingVisual());
        viz.put("confirmationGesture", createConfirmationGesture());
        return viz;
    }
    
    private Map<String, Object> createShoppingVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("productModels", createProductModels());
        viz.put("shoppingCart", createARShoppingCart());
        viz.put("priceLabels", createPriceLabels());
        return viz;
    }
    
    private Map<String, Object> createHolographicVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("hologram", createHologram());
        viz.put("dataStreams", createDataStreams());
        viz.put("interfaceElements", createHolographicInterface());
        return viz;
    }
    
    private Map<String, Object> createDefaultVisualization(ARPaymentExperience experience) {
        Map<String, Object> viz = new HashMap<>();
        viz.put("paymentIndicator", createPaymentIndicator());
        viz.put("amountDisplay", createAmountDisplay(experience.getAmount()));
        viz.put("statusIndicator", createStatusIndicator(experience.getStatus()));
        return viz;
    }
    
    private void addCommonVisualizationElements(Map<String, Object> visualization, 
                                               ARPaymentExperience experience) {
        visualization.put("securityIndicator", createSecurityIndicator(experience.getSecurityScore()));
        visualization.put("progressBar", createProgressBar(experience.getStatus()));
        
        if (experience.getPointsEarned() > 0) {
            visualization.put("pointsAnimation", createPointsAnimation(experience.getPointsEarned()));
        }
        
        if (experience.getAchievementUnlocked() != null) {
            visualization.put("achievementBadge", createAchievementBadge(experience.getAchievementUnlocked()));
        }
    }
    
    private ARPaymentExperience.VisualizationEffect createPremiumEffect() {
        return ARPaymentExperience.VisualizationEffect.builder()
                .effectType("PARTICLE")
                .effectName("PREMIUM_SPARKLE")
                .parameters(Map.of(
                    "particleCount", 50,
                    "color", "#FFD700",
                    "lifetime", 2000
                ))
                .duration(3000)
                .triggerEvent("PAYMENT_CONFIRMED")
                .build();
    }
    
    private ARPaymentExperience.VisualizationEffect createLightningEffect() {
        return ARPaymentExperience.VisualizationEffect.builder()
                .effectType("SHADER")
                .effectName("LIGHTNING_BOLT")
                .parameters(Map.of(
                    "color", "#00FFFF",
                    "intensity", 1.0,
                    "branches", 3
                ))
                .duration(1000)
                .triggerEvent("INSTANT_PAYMENT")
                .build();
    }
    
    private ARPaymentExperience.VisualizationEffect createCryptoEffect() {
        return ARPaymentExperience.VisualizationEffect.builder()
                .effectType("3D_MODEL")
                .effectName("CRYPTO_COIN_SPIN")
                .parameters(Map.of(
                    "model", "bitcoin_coin",
                    "rotationSpeed", 2.0,
                    "glowColor", "#FFA500"
                ))
                .duration(2000)
                .triggerEvent("CRYPTO_PAYMENT")
                .build();
    }
    
    private ARPaymentExperience.VisualizationEffect createGiftEffect() {
        return ARPaymentExperience.VisualizationEffect.builder()
                .effectType("ANIMATION")
                .effectName("GIFT_BOX_OPEN")
                .parameters(Map.of(
                    "boxColor", "#FF69B4",
                    "ribbonColor", "#FFD700",
                    "confetti", true
                ))
                .duration(2500)
                .triggerEvent("GIFT_PAYMENT")
                .build();
    }
    
    private ARPaymentExperience.VisualizationEffect createConfirmationEffect() {
        return ARPaymentExperience.VisualizationEffect.builder()
                .effectType("UI")
                .effectName("SUCCESS_CHECKMARK")
                .parameters(Map.of(
                    "color", "#00FF00",
                    "size", 0.1,
                    "pulseEffect", true
                ))
                .duration(1500)
                .triggerEvent("PAYMENT_COMPLETED")
                .build();
    }
    
    private void addOverlayToSession(String sessionToken, ARSession.AROverlay overlay) {
        Optional<ARSession> sessionOpt = sessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isPresent()) {
            ARSession session = sessionOpt.get();
            session.addOverlay(overlay);
            sessionRepository.save(session);
        }
    }
    
    private Map<String, Object> createPathStyle() {
        Map<String, Object> style = new HashMap<>();
        style.put("lineWidth", 0.02);
        style.put("glowEffect", true);
        style.put("animated", true);
        style.put("particleDensity", 20);
        return style;
    }
    
    private double[] randomPosition() {
        return new double[]{
            secureRandom.nextDouble() * 0.6 - 0.3,
            secureRandom.nextDouble() * 0.6 - 0.3,
            secureRandom.nextDouble() * 0.2 - 0.1
        };
    }
    
    private String selectCurrencyMesh(String currency) {
        switch (currency) {
            case "USD": return "dollar_bill_mesh";
            case "EUR": return "euro_coin_mesh";
            case "BTC": return "bitcoin_coin_mesh";
            case "ETH": return "ethereum_coin_mesh";
            default: return "generic_coin_mesh";
        }
    }
    
    private String selectCurrencyTexture(String currency) {
        switch (currency) {
            case "USD": return "dollar_texture";
            case "EUR": return "euro_texture";
            case "BTC": return "bitcoin_texture";
            case "ETH": return "ethereum_texture";
            default: return "generic_currency_texture";
        }
    }
    
    private double calculateModelScale(BigDecimal amount) {
        double baseScale = 0.1;
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            baseScale = 0.15;
        }
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            baseScale = 0.2;
        }
        return baseScale;
    }
    
    private double[] createInitialRotation() {
        return new double[]{0, 0, 0};
    }
    
    // Additional helper methods for visualization elements
    
    private Map<String, Object> createScanFrame() {
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "ANIMATED_FRAME");
        frame.put("color", "#00FF00");
        frame.put("cornerRadius", 0.02);
        frame.put("pulseAnimation", true);
        return frame;
    }
    
    private Map<String, Object> createScanLine() {
        Map<String, Object> line = new HashMap<>();
        line.put("type", "SCANNING_LINE");
        line.put("color", "#00FF00");
        line.put("speed", 1.0);
        line.put("direction", "vertical");
        return line;
    }
    
    private Map<String, Object> createTargetIndicator() {
        Map<String, Object> indicator = new HashMap<>();
        indicator.put("type", "CROSSHAIR");
        indicator.put("color", "#FFFF00");
        indicator.put("size", 0.05);
        indicator.put("animated", true);
        return indicator;
    }
    
    private Map<String, Object> createDropEffect(BigDecimal amount) {
        Map<String, Object> effect = new HashMap<>();
        effect.put("type", "MONEY_DROP");
        effect.put("model", create3DPaymentModel(amount, "USD"));
        effect.put("particleTrail", true);
        effect.put("impactEffect", "RIPPLE");
        return effect;
    }
    
    private Map<String, Object> createLandingMarker() {
        Map<String, Object> marker = new HashMap<>();
        marker.put("type", "LANDING_ZONE");
        marker.put("shape", "CIRCLE");
        marker.put("radius", 0.3);
        marker.put("color", "#00D4FF");
        marker.put("animated", true);
        return marker;
    }
    
    private Map<String, Object> createPickupRadius() {
        Map<String, Object> radius = new HashMap<>();
        radius.put("type", "PROXIMITY_SPHERE");
        radius.put("radius", 2.0);
        radius.put("opacity", 0.2);
        radius.put("color", "#00FF00");
        return radius;
    }
    
    private Map<String, Object> createGestureTrail() {
        Map<String, Object> trail = new HashMap<>();
        trail.put("type", "PARTICLE_TRAIL");
        trail.put("color", "#FF00FF");
        trail.put("lifetime", 1000);
        trail.put("width", 0.01);
        return trail;
    }
    
    private Map<String, Object> createHandTrackingVisual() {
        Map<String, Object> tracking = new HashMap<>();
        tracking.put("type", "SKELETAL_HAND");
        tracking.put("jointColor", "#00FF00");
        tracking.put("boneColor", "#FFFFFF");
        tracking.put("showConfidence", true);
        return tracking;
    }
    
    private Map<String, Object> createConfirmationGesture() {
        Map<String, Object> gesture = new HashMap<>();
        gesture.put("type", "GESTURE_GUIDE");
        gesture.put("gestureType", "THUMBS_UP");
        gesture.put("guideColor", "#00FF00");
        gesture.put("animated", true);
        return gesture;
    }
    
    private List<Map<String, Object>> createProductModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        // Placeholder for product 3D models
        return models;
    }
    
    private Map<String, Object> createARShoppingCart() {
        Map<String, Object> cart = new HashMap<>();
        cart.put("type", "FLOATING_CART");
        cart.put("position", new double[]{0.3, 0, -0.2});
        cart.put("itemCount", 0);
        cart.put("animated", true);
        return cart;
    }
    
    private List<Map<String, Object>> createPriceLabels() {
        List<Map<String, Object>> labels = new ArrayList<>();
        // Placeholder for price labels
        return labels;
    }
    
    private Map<String, Object> createHologram() {
        Map<String, Object> hologram = new HashMap<>();
        hologram.put("type", "HOLOGRAPHIC_DISPLAY");
        hologram.put("shader", "HOLOGRAM_SHADER");
        hologram.put("scanlineEffect", true);
        hologram.put("glitchEffect", 0.1);
        return hologram;
    }
    
    private List<Map<String, Object>> createDataStreams() {
        List<Map<String, Object>> streams = new ArrayList<>();
        // Placeholder for data visualization streams
        return streams;
    }
    
    private Map<String, Object> createHolographicInterface() {
        Map<String, Object> ui = new HashMap<>();
        ui.put("type", "HOLOGRAPHIC_UI");
        ui.put("elements", new ArrayList<>());
        ui.put("interactionMode", "GESTURE");
        return ui;
    }
    
    private Map<String, Object> createPaymentIndicator() {
        Map<String, Object> indicator = new HashMap<>();
        indicator.put("type", "PAYMENT_STATUS");
        indicator.put("icon", "MONEY_TRANSFER");
        indicator.put("animated", true);
        return indicator;
    }
    
    private Map<String, Object> createAmountDisplay(BigDecimal amount) {
        Map<String, Object> display = new HashMap<>();
        display.put("type", "3D_TEXT");
        display.put("text", "$" + amount.toString());
        display.put("fontSize", 0.1);
        display.put("color", "#00FF00");
        return display;
    }
    
    private Map<String, Object> createStatusIndicator(ARPaymentExperience.ExperienceStatus status) {
        Map<String, Object> indicator = new HashMap<>();
        indicator.put("type", "STATUS_LIGHT");
        indicator.put("status", status.name());
        indicator.put("color", getStatusColor(status));
        return indicator;
    }
    
    private Map<String, Object> createSecurityIndicator(Double securityScore) {
        Map<String, Object> indicator = new HashMap<>();
        indicator.put("type", "SECURITY_SHIELD");
        indicator.put("score", securityScore);
        indicator.put("color", securityScore > 0.8 ? "#00FF00" : "#FFFF00");
        return indicator;
    }
    
    private Map<String, Object> createProgressBar(ARPaymentExperience.ExperienceStatus status) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("type", "CIRCULAR_PROGRESS");
        progress.put("progress", getStatusProgress(status));
        progress.put("animated", true);
        return progress;
    }
    
    private Map<String, Object> createPointsAnimation(Integer points) {
        Map<String, Object> animation = new HashMap<>();
        animation.put("type", "POINTS_POPUP");
        animation.put("points", "+" + points);
        animation.put("color", "#FFD700");
        animation.put("floatUp", true);
        return animation;
    }
    
    private Map<String, Object> createAchievementBadge(String achievement) {
        Map<String, Object> badge = new HashMap<>();
        badge.put("type", "ACHIEVEMENT_UNLOCK");
        badge.put("name", achievement);
        badge.put("icon", "TROPHY");
        badge.put("celebration", true);
        return badge;
    }
    
    private String getStatusColor(ARPaymentExperience.ExperienceStatus status) {
        switch (status) {
            case COMPLETED: return "#00FF00";
            case FAILED: return "#FF0000";
            case PROCESSING: return "#FFFF00";
            default: return "#FFFFFF";
        }
    }
    
    private double getStatusProgress(ARPaymentExperience.ExperienceStatus status) {
        switch (status) {
            case INITIATED: return 0.2;
            case SCANNING: return 0.4;
            case PROCESSING: return 0.6;
            case CONFIRMING: return 0.8;
            case COMPLETED: return 1.0;
            default: return 0.0;
        }
    }
}