namespace HealthAggregator.Core.Models;

public sealed record EpicOrganization(
    string Id,
    string Name,
    string FhirBaseUrl,
    bool IsSandbox);
