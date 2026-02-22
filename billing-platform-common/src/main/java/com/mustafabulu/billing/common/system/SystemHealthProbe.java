package com.mustafabulu.billing.common.system;

import java.util.Map;

public interface SystemHealthProbe {
    Map<String, Object> probe();
}
