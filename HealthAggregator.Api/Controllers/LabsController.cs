using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/labs")]
public sealed class LabsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetLabs([FromQuery] string? search, [FromQuery] DateTimeOffset? from, [FromQuery] DateTimeOffset? to, CancellationToken cancellationToken)
    {
        var query = db.LabObservations.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
        {
            query = query.Where(lab => lab.TestName.Contains(search) || (lab.LoincCode != null && lab.LoincCode.Contains(search)));
        }

        var labs = (await query.ToListAsync(cancellationToken))
            .Where(lab => !from.HasValue || lab.EffectiveAt == null || lab.EffectiveAt >= from)
            .Where(lab => !to.HasValue || lab.EffectiveAt == null || lab.EffectiveAt <= to)
            .OrderByDescending(lab => lab.EffectiveAt)
            .Take(500)
            .ToList();

        return Ok(labs);
    }

    [HttpGet("series")]
    public async Task<IActionResult> GetSeries([FromQuery] string? loinc, [FromQuery] string? name, CancellationToken cancellationToken)
    {
        var query = db.LabObservations.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(loinc))
        {
            query = query.Where(lab => lab.LoincCode == loinc);
        }
        else if (!string.IsNullOrWhiteSpace(name))
        {
            query = query.Where(lab => lab.TestName.Contains(name));
        }

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
                    lab.SourceName,
                    lab.FhirReference
                })
            });

        return Ok(series);
    }
}
