package com.waqiti.frauddetection.integration.pytorch;

import com.waqiti.frauddetection.ml.dto.*;
import org.springframework.stereotype.Service;

@Service
public class PyTorchServingClient {
    public PyTorchPredictionResponse predict(FeatureVector features) {
        return PyTorchPredictionResponse.builder().build();
    }
}
