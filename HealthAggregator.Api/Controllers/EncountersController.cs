using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/encounters")]
public sealed class EncountersController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetEncounters(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Encounters.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(e => e.TypeText != null && e.TypeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(e => e.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(e => e.SourceSystem == source);
        if (from.HasValue) query = query.Where(e => e.StartedAt >= from);
        if (to.HasValue)   query = query.Where(e => e.StartedAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(e => e.StartedAt)
            .Take(500)
            .ToList();

        return Ok(rows);
    }
}
