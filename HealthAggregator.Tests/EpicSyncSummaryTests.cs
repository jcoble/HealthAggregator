using HealthAggregator.Core.Models;

namespace HealthAggregator.Tests;

public sealed class EpicSyncSummaryTests
{
    [Fact]
    public void FromResources_keeps_per_resource_diagnostics_and_aggregates_totals()
    {
        var resources = new[]
        {
            new FhirResourceSyncSummary("Patient", "Patient/patient-1", new ImportSummary(1, 0, 0, 1, 0, 0, 0, 0, 0)),
            new FhirResourceSyncSummary("Observation", "Observation?patient=patient-1&category=laboratory&_count=100", new ImportSummary(3, 2, 0, 0, 0, 0, 0, 0, 0)),
            new FhirResourceSyncSummary("DiagnosticReport", "DiagnosticReport?patient=patient-1&_count=100", new ImportSummary(4, 0, 4, 0, 0, 0, 0, 0, 0))
        };

        var summary = EpicSyncSummary.FromResources(resources);

        Assert.Equal(8, summary.Total.SourceRecordsUpserted);
        Assert.Equal(2, summary.Total.LabObservationsUpserted);
        Assert.Equal(4, summary.Total.DiagnosticReportsUpserted);
        Assert.Equal("Observation", summary.Resources[1].ResourceType);
        Assert.Equal(2, summary.Resources[1].Summary.LabObservationsUpserted);
    }
}
