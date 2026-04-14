using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/allergies")]
public sealed class AllergiesController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetAllergies(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Allergies.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(a => a.AllergyText != null && a.AllergyText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(a => a.ClinicalStatus == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(a => a.SourceSystem == source);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(a => a.RecordedAt)
            .Take(500)
            .ToList();

        return Ok(rows);
    }
}
