package com.cloud.gateway.api.facade.admin.definition;

/** Latest sourceRevision of the touched Definition after a save; the input token for the next CAS write. */
public record DefinitionSaveApiOutput(String sourceRevision) { }
