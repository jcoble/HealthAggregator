using HealthAggregator.Data;
using HealthAggregator.Data.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Tests;

public sealed class FhirImportServiceTests
{
    [Fact]
    public async Task ImportBundleAsync_normalizes_lab_observations_and_keeps_raw_fhir_source()
    {
        await using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();

        var options = new DbContextOptionsBuilder<HealthAggregatorDbContext>()
            .UseSqlite(connection)
            .Options;

        await using var db = new HealthAggregatorDbContext(options);
        await db.Database.EnsureCreatedAsync();

        var importer = new FhirImportService(db);

        var summary = await importer.ImportBundleAsync(
            sourceSystem: "epic-sandbox",
            sourceName: "Epic Sandbox",
            payloadJson: SampleBundle,
            cancellationToken: CancellationToken.None);

        Assert.Equal(3, summary.SourceRecordsUpserted);
        Assert.Equal(1, summary.LabObservationsUpserted);

        var lab = await db.LabObservations.SingleAsync(CancellationToken.None);
        Assert.Equal("Observation/obs-a1c", lab.FhirReference);
        Assert.Equal("epic-sandbox", lab.SourceSystem);
        Assert.Equal("4548-4", lab.LoincCode);
        Assert.Equal("Hemoglobin A1c", lab.TestName);
        Assert.Equal(5.7m, lab.NumericValue);
        Assert.Equal("%", lab.Unit);
        Assert.Equal(4.0m, lab.ReferenceLow);
        Assert.Equal(5.6m, lab.ReferenceHigh);
        Assert.Equal("High", lab.Interpretation);
        Assert.Equal(new DateTimeOffset(2026, 4, 12, 14, 30, 0, TimeSpan.Zero), lab.EffectiveAt);

        var sourceRecord = await db.SourceRecords.SingleAsync(
            record => record.ResourceType == "Observation",
            CancellationToken.None);
        Assert.Contains("\"resourceType\": \"Observation\"", sourceRecord.RawJson);
        Assert.Equal("obs-a1c", sourceRecord.ResourceId);
    }

    [Fact]
    public async Task ImportBundleAsync_is_idempotent_for_the_same_source_resource()
    {
        await using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();

        var options = new DbContextOptionsBuilder<HealthAggregatorDbContext>()
            .UseSqlite(connection)
            .Options;

        await using var db = new HealthAggregatorDbContext(options);
        await db.Database.EnsureCreatedAsync();

        var importer = new FhirImportService(db);

        await importer.ImportBundleAsync("epic-sandbox", "Epic Sandbox", SampleBundle, CancellationToken.None);
        await importer.ImportBundleAsync("epic-sandbox", "Epic Sandbox", SampleBundle, CancellationToken.None);

        Assert.Equal(3, await db.SourceRecords.CountAsync(CancellationToken.None));
        Assert.Equal(1, await db.LabObservations.CountAsync(CancellationToken.None));
    }

    private const string SampleBundle = """
        {
          "resourceType": "Bundle",
          "type": "searchset",
          "entry": [
            {
              "resource": {
                "resourceType": "Patient",
                "id": "patient-1",
                "name": [{ "given": ["Alex"], "family": "Rivera" }],
                "birthDate": "1981-07-04"
              }
            },
            {
              "resource": {
                "resourceType": "DiagnosticReport",
                "id": "report-1",
                "status": "final",
                "code": { "text": "Diabetes monitoring" },
                "issued": "2026-04-12T15:00:00Z",
                "result": [{ "reference": "Observation/obs-a1c" }]
              }
            },
            {
              "resource": {
                "resourceType": "Observation",
                "id": "obs-a1c",
                "status": "final",
                "category": [{
                  "coding": [{
                    "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                    "code": "laboratory",
                    "display": "Laboratory"
                  }]
                }],
                "code": {
                  "coding": [{
                    "system": "http://loinc.org",
                    "code": "4548-4",
                    "display": "Hemoglobin A1c"
                  }],
                  "text": "Hemoglobin A1c"
                },
                "effectiveDateTime": "2026-04-12T14:30:00Z",
                "valueQuantity": {
                  "value": 5.7,
                  "unit": "%",
                  "system": "http://unitsofmeasure.org",
                  "code": "%"
                },
                "referenceRange": [{
                  "low": { "value": 4.0, "unit": "%" },
                  "high": { "value": 5.6, "unit": "%" },
                  "text": "4.0-5.6 %"
                }],
                "interpretation": [{
                  "coding": [{ "code": "H", "display": "High" }]
                }]
              }
            }
          ]
        }
        """;
}
