using HealthAggregator.Api.Controllers;
using HealthAggregator.Data.Entities;
using Microsoft.AspNetCore.Mvc;

namespace HealthAggregator.Tests;

public sealed class NewControllerTests
{
    // ===== MedicationsController =====
    [Fact]
    public async Task Medications_returns_rows_ordered_by_AuthoredAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "cleveland-clinic", SourceName = "Cleveland", ResourceId = "1", FhirReference = "MedicationRequest/1", MedicationText = "Metformin", Status = "active", AuthoredAt = new DateTimeOffset(2024, 3, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "summa-health", SourceName = "Summa", ResourceId = "2", FhirReference = "MedicationRequest/2", MedicationText = "Atorvastatin", Status = "active", AuthoredAt = new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new MedicationsController(db).GetMedications(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<MedicationRecord>>(result!.Value);
        Assert.Equal(2, rows.Count);
        Assert.Equal("Atorvastatin", rows[0].MedicationText);
    }

    [Fact]
    public async Task Medications_filters_by_source()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "cleveland-clinic", SourceName = "", ResourceId = "1", FhirReference = "a/1", MedicationText = "A", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "summa-health", SourceName = "", ResourceId = "2", FhirReference = "b/2", MedicationText = "B", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new MedicationsController(db).GetMedications(null, null, null, null, "cleveland-clinic", CancellationToken.None) as OkObjectResult;
        Assert.Single(Assert.IsAssignableFrom<List<MedicationRecord>>(result!.Value));
    }

    // ===== ConditionsController =====
    [Fact]
    public async Task Conditions_orders_by_OnsetAt_then_RecordedAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Conditions.AddRange(
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", CodeText = "Asthma", ClinicalStatus = "active", OnsetAt = new DateTimeOffset(2020, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", CodeText = "Diabetes", ClinicalStatus = "active", OnsetAt = null, RecordedAt = new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new ConditionsController(db).GetConditions(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<ConditionRecord>>(result!.Value);
        Assert.Equal("Diabetes", rows[0].CodeText);
    }

    // ===== AllergiesController =====
    [Fact]
    public async Task Allergies_returns_rows_ordered_by_RecordedAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Allergies.AddRange(
            new AllergyRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", AllergyText = "Peanuts", ClinicalStatus = "active", RecordedAt = new DateTimeOffset(2020, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new AllergyRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", AllergyText = "Penicillin", ClinicalStatus = "active", RecordedAt = new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new AllergiesController(db).GetAllergies(null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<AllergyRecord>>(result!.Value);
        Assert.Equal("Penicillin", rows[0].AllergyText);
    }

    // ===== EncountersController =====
    [Fact]
    public async Task Encounters_returns_rows_ordered_by_StartedAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Encounters.AddRange(
            new EncounterRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", TypeText = "Primary care", Status = "finished", StartedAt = new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new EncounterRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", TypeText = "Specialist", Status = "finished", StartedAt = new DateTimeOffset(2025, 6, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new EncountersController(db).GetEncounters(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<EncounterRecord>>(result!.Value);
        Assert.Equal("Specialist", rows[0].TypeText);
    }

    // ===== DocumentsController =====
    [Fact]
    public async Task Documents_returns_rows_ordered_by_DocumentedAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Documents.AddRange(
            new DocumentRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", TypeText = "Progress note", Status = "final", DocumentedAt = new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new DocumentRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", TypeText = "Discharge summary", Status = "final", DocumentedAt = new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new DocumentsController(db).GetDocuments(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<DocumentRecord>>(result!.Value);
        Assert.Equal("Discharge summary", rows[0].TypeText);
    }

    // ===== LabsController abnormal predicate =====
    [Fact]
    public async Task Labs_abnormal_filter_excludes_Normal_interpretation_includes_H_L_and_out_of_range()
    {
        using var db = TestDb.CreateInMemory();
        db.LabObservations.AddRange(
            new LabObservation { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", TestName = "Normal", Interpretation = "Normal", NumericValue = 5.5m, ReferenceLow = 4m, ReferenceHigh = 6m, EffectiveAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new LabObservation { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", TestName = "High", Interpretation = "H", NumericValue = 7m, EffectiveAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new LabObservation { SourceSystem = "a", SourceName = "", ResourceId = "3", FhirReference = "a/3", TestName = "Low", Interpretation = "L", NumericValue = 3m, EffectiveAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new LabObservation { SourceSystem = "a", SourceName = "", ResourceId = "4", FhirReference = "a/4", TestName = "OutOfRange", NumericValue = 12m, ReferenceLow = 4m, ReferenceHigh = 6m, EffectiveAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var result = await new LabsController(db).GetLabs(null, null, null, null, null, true, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<LabObservation>>(result!.Value);
        Assert.Equal(3, rows.Count);
        Assert.DoesNotContain(rows, r => r.TestName == "Normal");
    }

    // ===== TimelineController =====
    [Fact]
    public async Task Timeline_includes_allergies_and_encounters_and_filters_by_kind()
    {
        using var db = TestDb.CreateInMemory();
        db.Allergies.Add(new AllergyRecord { SourceSystem = "a", SourceName = "Org", ResourceId = "1", FhirReference = "A/1", AllergyText = "Peanuts", RecordedAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow });
        db.Encounters.Add(new EncounterRecord { SourceSystem = "a", SourceName = "Org", ResourceId = "2", FhirReference = "E/2", TypeText = "Annual", StartedAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow });
        db.LabObservations.Add(new LabObservation { SourceSystem = "a", SourceName = "Org", ResourceId = "3", FhirReference = "O/3", TestName = "HbA1c", EffectiveAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow });
        await db.SaveChangesAsync();

        var all = await new TimelineController(db).GetTimeline(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var allItems = Assert.IsAssignableFrom<List<TimelineItem>>(all!.Value);
        Assert.Equal(3, allItems.Count);

        var allergyOnly = await new TimelineController(db).GetTimeline("allergy", null, null, null, null, CancellationToken.None) as OkObjectResult;
        var allergyItems = Assert.IsAssignableFrom<List<TimelineItem>>(allergyOnly!.Value);
        Assert.Single(allergyItems);
        Assert.Equal("allergy", allergyItems[0].Kind);
    }

    // ===== EpicIntegrationsController Disconnect =====
    [Fact]
    public async Task Disconnect_removes_connection_but_keeps_ingested_data()
    {
        using var db = TestDb.CreateInMemory();
        db.EpicConnections.Add(new EpicConnection {
            OrganizationId = "cleveland-clinic",
            OrganizationName = "Cleveland Clinic",
            FhirBaseUrl = "https://...",
            AccessToken = "token",
            ConnectedAt = DateTimeOffset.UtcNow
        });
        db.LabObservations.Add(new LabObservation {
            SourceSystem = "cleveland-clinic",
            SourceName = "Cleveland Clinic",
            ResourceId = "1",
            FhirReference = "Observation/1",
            TestName = "HbA1c"
        });
        await db.SaveChangesAsync();
        var connectionId = db.EpicConnections.Single().Id;

        var controller = new EpicIntegrationsController(null!, db, null!);
        var result = await controller.Disconnect(connectionId, CancellationToken.None) as OkObjectResult;

        Assert.NotNull(result);
        Assert.Equal(0, db.EpicConnections.Count());
        Assert.Equal(1, db.LabObservations.Count());
    }

    [Fact]
    public async Task Disconnect_returns_NotFound_for_unknown_id()
    {
        using var db = TestDb.CreateInMemory();
        var controller = new EpicIntegrationsController(null!, db, null!);
        var result = await controller.Disconnect(999, CancellationToken.None);
        Assert.IsType<NotFoundResult>(result);
    }
}
