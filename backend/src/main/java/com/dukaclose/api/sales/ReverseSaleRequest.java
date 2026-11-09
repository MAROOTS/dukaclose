// ReverseSaleRequest.java
package com.dukaclose.api.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseSaleRequest(@NotBlank @Size(max = 200) String reason) {}