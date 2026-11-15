package com.fixmyride.servicerequest.domain;

import java.math.BigDecimal;

public interface Billable {
    BigDecimal calculateCost();
}
