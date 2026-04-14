using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/timeline")]
public sealed class TimelineController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetTimeline(
        [FromQuery] string? kind,
        [FromQuery] string? source,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] int? take,
        CancellationToken cancellationToken)
    {
        var items = new List<TimelineItem>();

        if (kind is null or "lab")
            items.AddRange(await db.LabObservations.AsNoTracking()
                .Select(lab => new TimelineItem("lab", lab.TestName, lab.EffectiveAt, lab.SourceSystem, lab.SourceName, lab.FhirReference, lab.NumericValue, lab.Unit))
                .ToListAsync(cancellationToken));

        if (kind is null or "report")
            items.AddRange(await db.DiagnosticReports.AsNoTracking()
                .Select(r => new TimelineItem("report", r.CodeText ?? "Diagnostic report", r.IssuedAt, r.SourceSystem, r.SourceName, r.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "condition")
            items.AddRange(await db.Conditions.AsNoTracking()
                .Select(c => new TimelineItem("condition", c.CodeText ?? "Condition", c.RecordedAt ?? c.OnsetAt, c.SourceSystem, c.SourceName, c.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "medication")
            items.AddRange(await db.Medications.AsNoTracking()
                .Select(m => new TimelineItem("medication", m.MedicationText ?? "Medication", m.AuthoredAt, m.SourceSystem, m.SourceName, m.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "allergy")
            items.AddRange(await db.Allergies.AsNoTracking()
                .Select(a => new TimelineItem("allergy", a.AllergyText ?? "Allergy", a.RecordedAt, a.SourceSystem, a.SourceName, a.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "encounter")
            items.AddRange(await db.Encounters.AsNoTracking()
                .Select(e => new TimelineItem("encounter", e.TypeText ?? "Encounter", e.StartedAt, e.SourceSystem, e.SourceName, e.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "document")
            items.AddRange(await db.Documents.AsNoTracking()
                .Select(d => new TimelineItem("document", d.TypeText ?? "Document", d.DocumentedAt, d.SourceSystem, d.SourceName, d.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        var filtered = items
            .Where(i => string.IsNullOrWhiteSpace(source) || i.SourceSystem == source)
            .Where(i => !from.HasValue || i.At == null || i.At >= from)
            .Where(i => !to.HasValue || i.At == null || i.At <= to)
            .OrderByDescending(i => i.At)
            .Take(take ?? 300)
            .ToList();

        return Ok(filtered);
    }
}

public sealed record TimelineItem(
    string Kind,
    string Title,
    DateTimeOffset? At,
    string SourceSystem,
    string SourceName,
    string FhirReference,
    decimal? NumericValue,
    string? Unit);
