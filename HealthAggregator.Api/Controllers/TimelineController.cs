using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/timeline")]
public sealed class TimelineController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetTimeline(CancellationToken cancellationToken)
    {
        var labs = await db.LabObservations.AsNoTracking()
            .Select(lab => new TimelineItem("lab", lab.TestName, lab.EffectiveAt, lab.SourceName, lab.FhirReference, lab.NumericValue, lab.Unit))
            .ToListAsync(cancellationToken);

        var reports = await db.DiagnosticReports.AsNoTracking()
            .Select(report => new TimelineItem("diagnostic-report", report.CodeText ?? "Diagnostic report", report.IssuedAt, report.SourceName, report.FhirReference, null, null))
            .ToListAsync(cancellationToken);

        var conditions = await db.Conditions.AsNoTracking()
            .Select(condition => new TimelineItem("condition", condition.CodeText ?? "Condition", condition.RecordedAt ?? condition.OnsetAt, condition.SourceName, condition.FhirReference, null, null))
            .ToListAsync(cancellationToken);

        var medications = await db.Medications.AsNoTracking()
            .Select(medication => new TimelineItem("medication", medication.MedicationText ?? "Medication", medication.AuthoredAt, medication.SourceName, medication.FhirReference, null, null))
            .ToListAsync(cancellationToken);

        var documents = await db.Documents.AsNoTracking()
            .Select(document => new TimelineItem("document", document.TypeText ?? "Document", document.DocumentedAt, document.SourceName, document.FhirReference, null, null))
            .ToListAsync(cancellationToken);

        return Ok(labs
            .Concat(reports)
            .Concat(conditions)
            .Concat(medications)
            .Concat(documents)
            .OrderByDescending(item => item.At)
            .Take(300));
    }
}

public sealed record TimelineItem(
    string Kind,
    string Title,
    DateTimeOffset? At,
    string SourceName,
    string FhirReference,
    decimal? NumericValue,
    string? Unit);
