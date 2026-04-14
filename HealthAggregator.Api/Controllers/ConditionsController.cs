using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/conditions")]
public sealed class ConditionsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetConditions(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Conditions.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(c => c.CodeText != null && c.CodeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(c => c.ClinicalStatus == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(c => c.SourceSystem == source);

        var rows = await query.ToListAsync(cancellationToken);
        var filtered = rows
            .Where(c => !from.HasValue || (c.OnsetAt ?? c.RecordedAt) >= from)
            .Where(c => !to.HasValue || (c.OnsetAt ?? c.RecordedAt) <= to)
            .OrderByDescending(c => c.OnsetAt ?? c.RecordedAt)
            .Take(500)
            .ToList();

        return Ok(filtered);
    }
}
