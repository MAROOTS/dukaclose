package com.dukaclose.api.closing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Close on this count. The note is required whenever the drawer is short or over. */
public record CloseRequest(@NotNull UUID cashCountId, @Size(max = 200) String note) {}