package com.neueda.app.events;

import java.util.UUID;

/** A public pie has a version its followers may need to pick up: it was just published or its slices changed. */
public record PieVersionSaved(UUID pieId, int version) {}
