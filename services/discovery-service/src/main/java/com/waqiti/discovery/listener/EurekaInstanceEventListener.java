package com.waqiti.discovery.listener;

import com.netflix.appinfo.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Eureka event listener for monitoring service registrations and de-registrations
 */
@Component
@Slf4j
public class EurekaInstanceEventListener {

    @EventListener
    public void handleInstanceRegistered(EurekaInstanceRegisteredEvent event) {
        InstanceInfo instanceInfo = event.getInstanceInfo();
        log.info("Service registered: {} - {} [{}:{}]", 
            instanceInfo.getAppName(), 
            instanceInfo.getInstanceId(),
            instanceInfo.getIPAddr(),
            instanceInfo.getPort());
    }

    @EventListener
    public void handleInstanceCanceled(EurekaInstanceCanceledEvent event) {
        log.info("Service canceled: {} - {}", 
            event.getAppName(), 
            event.getServerId());
    }

    @EventListener
    public void handleInstanceRenewed(EurekaInstanceRenewedEvent event) {
        log.debug("Service renewed: {} - {}", 
            event.getAppName(), 
            event.getServerId());
    }

    @EventListener
    public void handleRegistrySync(EurekaPeerReplicationEvent event) {
        log.debug("Registry sync event: {}", event.getPeerNodeName());
    }

    @EventListener
    public void handleServerStart(EurekaServerStartedEvent event) {
        log.info("Eureka Server started successfully");
    }
}