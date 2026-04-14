namespace HealthAggregator.Data.Entities;

public sealed class EpicConnection
{
    public int Id { get; set; }
    public string OrganizationId { get; set; } = "";
    public string OrganizationName { get; set; } = "";
    public string FhirBaseUrl { get; set; } = "";
    public string? PatientId { get; set; }
    public string? AccessToken { get; set; }
    public string? RefreshToken { get; set; }
    public string Scope { get; set; } = "";
    public DateTimeOffset ConnectedAt { get; set; }
    public DateTimeOffset? LastSyncedAt { get; set; }
}

public sealed class EpicAuthorizationState
{
    public int Id { get; set; }
    public string State { get; set; } = "";
    public string OrganizationId { get; set; } = "";
    public string CodeVerifier { get; set; } = "";
    public string RedirectUri { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset ExpiresAt { get; set; }
    public bool Consumed { get; set; }
}

public sealed class SyncJob
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string Status { get; set; } = "running";
    public DateTimeOffset StartedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }
    public int SourceRecordsUpserted { get; set; }
    public int LabObservationsUpserted { get; set; }
    public string? Error { get; set; }
}

public sealed class SourceRecord
{
    public int Id { get; set; }
    public int? SyncJobId { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string ResourceType { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string RawJson { get; set; } = "";
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class PatientRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string FhirId { get; set; } = "";
    public string? DisplayName { get; set; }
    public DateOnly? BirthDate { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}

public sealed class LabObservation
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? DiagnosticReportReference { get; set; }
    public string? LoincCode { get; set; }
    public string TestName { get; set; } = "";
    public decimal? NumericValue { get; set; }
    public string? TextValue { get; set; }
    public string? Unit { get; set; }
    public decimal? ReferenceLow { get; set; }
    public decimal? ReferenceHigh { get; set; }
    public string? ReferenceText { get; set; }
    public string? Interpretation { get; set; }
    public DateTimeOffset? EffectiveAt { get; set; }
    public string Status { get; set; } = "";
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class DiagnosticReportRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? CodeText { get; set; }
    public string? Status { get; set; }
    public DateTimeOffset? IssuedAt { get; set; }
    public string? ResultReferences { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class ConditionRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? CodeText { get; set; }
    public string? ClinicalStatus { get; set; }
    public DateTimeOffset? OnsetAt { get; set; }
    public DateTimeOffset? RecordedAt { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class MedicationRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? MedicationText { get; set; }
    public string? Status { get; set; }
    public DateTimeOffset? AuthoredAt { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class AllergyRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? AllergyText { get; set; }
    public string? ClinicalStatus { get; set; }
    public DateTimeOffset? RecordedAt { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class EncounterRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? TypeText { get; set; }
    public string? Status { get; set; }
    public DateTimeOffset? StartedAt { get; set; }
    public DateTimeOffset? EndedAt { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class DocumentRecord
{
    public int Id { get; set; }
    public string SourceSystem { get; set; } = "";
    public string SourceName { get; set; } = "";
    public string FhirReference { get; set; } = "";
    public string ResourceId { get; set; } = "";
    public string? PatientFhirId { get; set; }
    public string? TypeText { get; set; }
    public string? Status { get; set; }
    public DateTimeOffset? DocumentedAt { get; set; }
    public string? ContentUrl { get; set; }
    public DateTimeOffset ImportedAt { get; set; }
}

public sealed class AiThread
{
    public int Id { get; set; }
    public string Title { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
}

public sealed class AiMessage
{
    public int Id { get; set; }
    public int AiThreadId { get; set; }
    public string Role { get; set; } = "";
    public string Content { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
}

public sealed class AuditLog
{
    public int Id { get; set; }
    public string Action { get; set; } = "";
    public string? Detail { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}
