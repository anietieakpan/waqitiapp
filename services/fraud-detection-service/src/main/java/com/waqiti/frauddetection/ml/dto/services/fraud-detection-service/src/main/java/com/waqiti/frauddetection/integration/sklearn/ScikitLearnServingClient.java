package com.waqiti.frauddetection.integration.sklearn;

import com.waqiti.frauddetection.ml.dto.*;
import org.springframework.stereotype.Service;

@Service
public class ScikitLearnServingClient {
    public ScikitLearnPredictionResponse predict(FeatureVector features) {
        return ScikitLearnPredictionResponse.builder().build();
    }
}
