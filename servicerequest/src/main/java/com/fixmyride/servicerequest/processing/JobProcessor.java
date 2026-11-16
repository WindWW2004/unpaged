package com.fixmyride.servicerequest.processing;

import com.fixmyride.servicerequest.domain.ServiceJob;

import java.util.Optional;

public interface JobProcessor {
    Optional<ServiceJob> processNext();
    String modeDescription();
}
