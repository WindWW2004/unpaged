package com.fixmyride.servicerequest.domain;

import java.time.Duration;

public interface Schedulable {
    Duration estimateDuration();
}
