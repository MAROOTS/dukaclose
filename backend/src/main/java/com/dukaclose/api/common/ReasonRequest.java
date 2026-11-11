// ReasonRequest.java
package com.dukaclose.api.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {}