using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/medications")]
public sealed class MedicationsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetMedications(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Medications.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(m => m.MedicationText != null && m.MedicationText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(m => m.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(m => m.SourceSystem == source);
        if (from.HasValue)
            query = query.Where(m => m.AuthoredAt >= from);
        if (to.HasValue)
            query = query.Where(m => m.AuthoredAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(m => m.AuthoredAt)
            .Take(500)
            .ToList();

        return Ok(rows);
    }
}
