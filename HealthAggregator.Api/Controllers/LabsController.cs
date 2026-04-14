using HealthAggregator.Data;
using HealthAggregator.Data.Entities;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/labs")]
public sealed class LabsController(HealthAggregatorDbContext db) : ControllerBase
{
    private static readonly string[] AbnormalPrefixes = ["HH", "LL", "H", "L", "A"];

    [HttpGet]
    public async Task<IActionResult> GetLabs(
        [FromQuery] string? search,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? loinc,
        [FromQuery] string? source,
        [FromQuery] bool? abnormal,
        CancellationToken cancellationToken)
    {
        var query = db.LabObservations.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(lab => lab.TestName.Contains(search) || (lab.LoincCode != null && lab.LoincCode.Contains(search)));
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(lab => lab.SourceSystem == source);
        if (!string.IsNullOrWhiteSpace(loinc))
            query = query.Where(lab => lab.LoincCode == loinc);

        var list = await query.ToListAsync(cancellationToken);

        if (abnormal == true)
            list = list.Where(IsAbnormal).ToList();

        var labs = list
            .Where(lab => !from.HasValue || lab.EffectiveAt == null || lab.EffectiveAt >= from)
            .Where(lab => !to.HasValue || lab.EffectiveAt == null || lab.EffectiveAt <= to)
            .OrderByDescending(lab => lab.EffectiveAt)
            .Take(500)
            .ToList();

        return Ok(labs);
    }

    [HttpGet("series")]
    public async Task<IActionResult> GetSeries(
        [FromQuery] string? loinc,
        [FromQuery] string? name,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.LabObservations.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(loinc))
            query = query.Where(lab => lab.LoincCode == loinc);
        else if (!string.IsNullOrWhiteSpace(name))
            query = query.Where(lab => lab.TestName.Contains(name));

        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(lab => lab.SourceSystem == source);

        var labs = (await query.ToListAsync(cancellationToken))
            .OrderBy(lab => lab.EffectiveAt)
            .ToList();

        var series = labs
            .GroupBy(lab => lab.LoincCode ?? lab.TestName)
            .Select(group => new
            {
                Key = group.Key,
                Name = group.First().TestName,
                LoincCode = group.First().LoincCode,
                Points = group.Select(lab => new
                {
                    lab.EffectiveAt,
                    lab.NumericValue,
                    lab.TextValue,
                    lab.Unit,
                    lab.ReferenceLow,
                    lab.ReferenceHigh,
                    lab.Interpretation,
                    lab.SourceSystem,
                    lab.SourceName,
                    lab.FhirReference
                })
            });

        return Ok(series);
    }

    internal static bool IsAbnormal(LabObservation lab)
    {
        if (!string.IsNullOrWhiteSpace(lab.Interpretation))
        {
            foreach (var prefix in AbnormalPrefixes)
            {
                if (lab.Interpretation.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }
        }
        if (lab.NumericValue.HasValue)
        {
            if (lab.ReferenceLow.HasValue && lab.NumericValue < lab.ReferenceLow) return true;
            if (lab.ReferenceHigh.HasValue && lab.NumericValue > lab.ReferenceHigh) return true;
        }
        return false;
    }
}
